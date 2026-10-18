package evaluation

import java.nio.file.Files
import scala.concurrent.duration.*

class EvaluationSuite extends RecordedSuite {
  override val munitTimeout = 10.minutes
  private val root = os.Path("tests/pragmatic-testing", os.pwd)
  private val starter = root / "directory-api/starter"
  private val submission = os.Path(sys.env("SUBMISSION_DIR"))
  private val scratch = os.Path(Files.createTempDirectory("directory-grade-"))
  private var index = 0

  private def files(path: os.Path): List[os.Path] = os
    .walk(path)
    .filter(os.isFile)
    .filter(p => !p.segments.exists(Set(".git", ".scala-build", ".bsp")))
    .toList

  private def copy(from: os.Path, to: os.Path): Unit =
    os.makeDir.all(to)
    files(from).foreach(p => os.copy(p, to / p.relativeTo(from), createFolders = true, replaceExisting = true))

  private def runSuite(file: String = "", before: String = "", after: String = ""): (Int, String) =
    index += 1
    val work = scratch / s"run-$index"
    copy(submission, work)
    // Restore trusted production and build inputs, keeping candidate-added test dependencies only.
    copy(starter / "src/main", work / "src/main")
    val originalDirectives = os.read(starter / "project.scala").linesIterator.toSet
    val added =
      os.read(submission / "project.scala").linesIterator.filterNot(originalDirectives).filter(_.trim.nonEmpty).toList
    require(
      added.forall(line => line.startsWith("//> using ")),
      "Only dependency/configuration directives may be added to the build file"
    )
    os.write.over(work / "project.scala", os.read(starter / "project.scala") + added.mkString("\n") + "\n")
    if file.nonEmpty then
      val path = work / "src/main/scala/directory" / file
      val original = os.read(path)
      require(original.contains(before), s"Missing mutation target: $before")
      os.write.over(path, original.replace(before, after))
    val report = os.Path(sys.env("GRADING_JSON"))
    val evidence = report / os.up / s"${report.baseName}-mutation-logs"
    os.makeDir.all(evidence)
    val log = evidence / s"run-$index.log"
    val result = os
      .proc("scala-cli", "test", ".", "--server=false")
      .call(
        cwd = work,
        stdout = log,
        stderr = log,
        mergeErrIntoOut = true,
        timeout = 120000,
        check = false,
        env = Map("GRADING_JSON" -> "", "SUBMISSION_DIR" -> "")
      )
    (result.exitCode, os.read(log))
  private lazy val clean = runSuite()
  private def hasTests(log: String): Boolean =
    "(?s).*[, ]([1-9][0-9]*) total.*".r.matches(log) ||
      "(?s).*Total number of tests run: [1-9][0-9]*.*".r.matches(log) ||
      log.linesIterator.exists(_.trim.startsWith("+ "))

  private def detects(file: String, before: String, after: String): Unit =
    assert(
      clean._1 == 0 && hasTests(clean._2),
      s"A passing, nonempty submitted suite is required.\n${clean._2.takeRight(2000)}"
    )
    val (exit, log) = runSuite(file, before, after)
    assert(
      exit != 0 && log.contains("==> X") &&
        (log.contains("munit.ComparisonFailException") || log.contains("munit.FailException") || log.contains(
          "org.scalatest.exceptions.TestFailedException"
        )) &&
        !log.contains("Compilation failed"),
      s"Mutant must cause a test assertion failure; exit=$exit\n${log.takeRight(3500)}"
    )

  expectation("Production behavior and dependencies are preserved and the submitted tests pass.") {
    files(starter / "src/main").foreach { original =>
      assertEquals(os.read(submission / original.relativeTo(starter)), os.read(original))
    }
    val base = os.read(starter / "project.scala").linesIterator.filter(_.nonEmpty).toSet
    assert(base.subsetOf(os.read(submission / "project.scala").linesIterator.toSet))
    assert(clean._1 == 0 && hasTests(clean._2), clean._2.takeRight(3000))
  }
  expectation("The tests detect an HTTP update wired to the wrong request fields.") {
    detects(
      "DirectoryApp.scala",
      "repository.update(id, input) match",
      "repository.update(id, input.copy(name = input.email, email = input.name)) match"
    )
  }
  expectation("The tests detect internal fields exposed by the user lookup response.") {
    detects(
      "DirectoryApp.scala",
      "case Some(user) => Response.withBody(user.publicView)",
      "case Some(user) => Response.withBody(user)"
    )
  }
  expectation("The tests detect invalid updates changing stored data.") {
    detects(
      "DirectoryApp.scala",
      "val input = Request.current.bodyJson[UserUpdate]",
      "val input = Request.current.bodyJson[UserUpdate]; if (!valid(input.name, input.email)) repository.update(id, input)"
    )
  }
  expectation("The tests detect stale revisions being accepted.") {
    detects("UserRepository.scala", "old.revision != input.revision", "false")
  }
  expectation("The tests detect failed updates leaving an email-history event.") {
    detects("UserRepository.scala", "connection.rollback()", "connection.commit()")
  }
  expectation("The tests detect successful updates not reaching persistent storage.") {
    detects("UserRepository.scala", "connection.commit()", "connection.rollback()")
  }
  expectation("The tests pass with harmless JSON property reordering.") {
    assert(clean._1 == 0 && hasTests(clean._2), clean._2.takeRight(2000))
    val (exit, log) = runSuite(
      "DirectoryApp.scala",
      "Response.withBody(user.publicView)",
      "Response.withBody(user.publicView.toJson(spaces = 2, sort = true)).settingHeader(\"Content-Type\", \"application/json; charset=utf-8\")"
    )
    assertEquals(exit, 0, log.takeRight(3000))
  }

  override def afterAll(): Unit =
    try super.afterAll()
    finally os.remove.all(scratch)
}

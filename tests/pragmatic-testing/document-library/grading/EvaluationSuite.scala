package evaluation

import java.nio.file.Files
import scala.concurrent.duration.*

class EvaluationSuite extends RecordedSuite {
  override val munitTimeout = 10.minutes
  private val root = os.Path("tests/pragmatic-testing", os.pwd)
  private val starter = root / "document-library/starter"
  private val submission = os.Path(sys.env("SUBMISSION_DIR"))
  private val scratch = os.Path(Files.createTempDirectory("document-grade-"))
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
      val path = work / "src/main/scala/documents" / file
      val original = os.read(path)
      require(original.contains(before), s"Missing mutation target: $before")
      os.write.over(path, original.replace(before, after))
    val report = os.Path(sys.env("GRADING_JSON"))
    val evidence = report / os.up / s"${report.baseName}-mutation-logs"
    os.makeDir.all(evidence)
    val log = evidence / s"run-$index.log"
    val sources = files(work).filter(_.ext == "scala").map(os.read(_))
    val options = TestOutput.frameworkOptions(sources)
    val result = os
      .proc(Seq("scala-cli", "test", ".", "--server=false") ++ options)
      .call(
        cwd = work,
        stdout = log,
        stderr = log,
        mergeErrIntoOut = true,
        timeout = 180000,
        check = false,
        env = Map("GRADING_JSON" -> "", "SUBMISSION_DIR" -> "")
      )
    (result.exitCode, os.read(log))
  private lazy val clean = runSuite()
  private def hasTests(log: String): Boolean = TestOutput.hasTests(log)

  private def detects(file: String, before: String, after: String): Unit =
    assert(
      clean._1 == 0 && hasTests(clean._2),
      s"A passing, nonempty submitted suite is required.\n${clean._2.takeRight(2000)}"
    )
    val (exit, log) = runSuite(file, before, after)
    assert(
      TestOutput.isBehaviorFailure(exit, log),
      s"Mutant must cause a test-body assertion or missing-object contract failure; exit=$exit\n${log.takeRight(3500)}"
    )

  expectation("Production behavior and dependencies are preserved and the submitted tests pass.") {
    files(starter / "src/main").foreach { original =>
      assertEquals(os.read(submission / original.relativeTo(starter)), os.read(original))
    }
    val original = os.read(starter / "project.scala").linesIterator.filter(_.nonEmpty).toSet
    assert(original.subsetOf(os.read(submission / "project.scala").linesIterator.toSet))
    assert(clean._1 == 0 && hasTests(clean._2), clean._2.takeRight(3000))
  }
  expectation("The tests detect storing objects at an incorrect S3 key even when download shares the error.") {
    detects("Documents.scala", "s\"documents/$id\"", "s\"uploads/$id\"")
  }
  expectation("The tests detect HTTP replacement binding request fields incorrectly.") {
    detects(
      "DocumentApp.scala",
      "documents.replace(id, input) match",
      "documents.replace(id, input.copy(name = input.contentType, contentType = input.name)) match"
    )
  }
  expectation("The tests detect binary content being corrupted during upload.") {
    detects(
      "Documents.scala",
      "RequestBody.fromBytes(bytes)",
      "RequestBody.fromBytes(bytes.reverse)"
    )
  }
  expectation("The tests detect losing the uploaded S3 content type.") {
    detects("Documents.scala", ".contentType(input.contentType)", ".contentType(\"application/octet-stream\")")
  }
  expectation("The tests detect failed S3 uploads publishing metadata.") {
    detects(
      "Documents.scala",
      "try\n        s3.putObject(",
      "try\n        metadata.put(document.id, document)\n        s3.putObject("
    )
  }
  expectation("The tests detect missing backing objects returning a service failure instead of 404.") {
    detects("Documents.scala", "case _: NoSuchKeyException => Left(404)", "case _: NoSuchKeyException => Left(503)")
  }
  expectation("The tests detect invalid uploads creating documents.") {
    detects(
      "Documents.scala",
      "input.name.trim.isEmpty || input.contentType.trim.isEmpty || decoded.isEmpty",
      "decoded.isEmpty"
    )
  }
  expectation("The tests pass with harmless JSON property reordering.") {
    assert(clean._1 == 0 && hasTests(clean._2), clean._2.takeRight(2000))
    val (exit, log) = runSuite(
      "DocumentApp.scala",
      "Response.withBody(document)",
      "Response.withBody(document.toJson(spaces = 2, sort = true)).settingHeader(\"Content-Type\", \"application/json; charset=utf-8\")"
    )
    assertEquals(exit, 0, log.takeRight(3000))
  }
  override def afterAll(): Unit =
    try super.afterAll()
    finally os.remove.all(scratch)
}

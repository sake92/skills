//> using dep com.lihaoyi::os-lib:0.11.8
package grading

import tags.*
import evaluation.RecordedSuite
import java.nio.file.{Files, Path}
import scala.meta.*

class EvaluationSuite extends RecordedSuite {
  private given Dialect = dialects.Scala3
  private val root = Path.of(sys.env("SUBMISSION_DIR"))
  private val starter = Path.of(sys.env.getOrElse("TAG_STARTER",
    "tests/pragmatic-architecture/workspace-tags-ci/starter"))
  private val sourcePath = Path.of("src/main/scala/tags/WorkspaceTags.scala")
  private def tree(path: Path) = Files.readString(path).parse[Source].get
  private def methods(source: Tree): List[Defn.Def] = source.collect { case d: Defn.Def => d }
  private lazy val original = tree(starter.resolve(sourcePath))
  private lazy val submitted = tree(root.resolve(sourcePath))
  private lazy val originalMethods = methods(original).map(d => d.name.value -> d).toMap
  private lazy val changedMethods = methods(submitted).filter(d =>
    originalMethods.get(d.name.value).forall(_.structure != d.structure))

  expectation("Workspace summaries flatten only active documents, normalize nonempty tags, deduplicate, and sort.") {
    val docs = List(Document("one", false, List("SCALA", " build ", "Build", " ")),
      Document("two", false, List("scala", "\t")), Document("archive", true, List("secret")))
    assertEquals(WorkspaceTags.tagSummary(" Demo ", docs), "Demo: 2 tags [build, scala]")
    assertEquals(WorkspaceTags.tagSummary("Demo", docs.reverse), "Demo: 2 tags [build, scala]")
    assertEquals(WorkspaceTags.exportRows(docs), List("one,active,SCALA| build |Build| ",
      "two,active,scala|\t", "archive,archived,secret"))
  }

  expectation("Empty and singular tag summaries preserve the exact requested display forms.") {
    assertEquals(WorkspaceTags.tagSummary(" Demo ", Nil), "Demo: no tags")
    assertEquals(WorkspaceTags.tagSummary("Demo", List(Document("old", true, List("scala")))), "Demo: no tags")
    assertEquals(WorkspaceTags.tagSummary("Demo", List(Document("empty", false, List(" ", "\t")))), "Demo: no tags")
    assertEquals(WorkspaceTags.tagSummary("Demo", List(Document("one", false, List("SCALA", "scala")))),
      "Demo: 1 tag [scala]")
  }

  expectation("Working production methods retain their syntax trees while CI-required formatting is allowed.") {
    val current = methods(submitted).map(d => d.name.value -> d).toMap
    originalMethods.filterNot(_._1 == "tagSummary").foreach { (name, before) =>
      assertEquals(current.get(name).map(_.structure), Some(before.structure), s"method refactored: $name")
    }
    assertEquals(submitted.collect { case c: Defn.Class => c.structure },
      original.collect { case c: Defn.Class => c.structure })
  }

  expectation("The original tests and formatter/CI contracts are preserved.") {
    List("project.scala", ".scalafmt.conf", ".github/workflows/ci.yml", "README.md").foreach { file =>
      assertEquals(Files.readString(root.resolve(file)), Files.readString(starter.resolve(file)))
    }
    val testPath = Path.of("src/test/scala/tags/WorkspaceTagsSuite.scala")
    def tests(path: Path) = tree(path).collect {
      case t @ Term.Apply(Term.Apply(Term.Name("test"), _), _) => t.structure
    }
    val current = tests(root.resolve(testPath)).toSet
    tests(starter.resolve(testPath)).foreach(t => assert(current(t), "original test changed"))
  }

  expectation("The candidate passes the actual formatter check required by CI.") {
    val result = os.proc("scala-cli", "fmt", ".", "--check", "--server=false").call(
      cwd = os.Path(root), stdin = "", mergeErrIntoOut = true, timeout = 120000, check = false)
    assertEquals(result.exitCode, 0, result.out.text())
  }

  expectation("New or changed production expressions keep conditionals outside interpolation.") {
    assert(changedMethods.exists(_.name.value == "tagSummary"))
    val found = changedMethods.flatMap(_.collect {
      case i: Term.Interpolate if i.args.exists(_.collect { case _: Term.If => true }.nonEmpty) => i.pos.text
    })
    assertEquals(found, Nil)
  }

  expectation("New or changed production expressions avoid long receiver chains and nested pipelines.") {
    assert(changedMethods.exists(_.name.value == "tagSummary"))
    val found = changedMethods.flatMap(_.collect { case t: Term if chainDepth(t) > 3 => t.pos.text })
    assertEquals(found, Nil)
  }

  private def chainDepth(term: Term): Int = term match {
    case Term.Apply(Term.Select(receiver: Term, _), _) => chainDepth(receiver) + 1
    case Term.Select(receiver: Term, _) =>
      val depth = chainDepth(receiver)
      if (depth > 0) depth + 1 else 0
    case Term.Apply(_, args) => args.map(chainDepth).maxOption.getOrElse(0)
    case _ => 0
  }
}

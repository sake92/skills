package grading

import classroom.*
import evaluation.RecordedSuite
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.meta.*

class EvaluationSuite extends RecordedSuite {
  private given Dialect = dialects.Scala3
  private val root = Path.of(sys.env("SUBMISSION_DIR"))
  private val starter = Path.of(sys.env.getOrElse("RESTRAINT_STARTER",
    "tests/pragmatic-architecture/invitation-preview/starter"))
  private def read(path: Path) = Files.readString(path)
  private def tree(path: Path) = read(path).parse[Source].get
  private val sourcePath = Path.of("src/main/scala/classroom/Invitations.scala")
  private lazy val original = tree(starter.resolve(sourcePath))
  private lazy val submitted = tree(root.resolve(sourcePath))
  private def methods(source: Tree): List[Defn.Def] = source.collect { case d: Defn.Def => d }
  private lazy val originalMethods = methods(original).map(d => d.name.value -> d).toMap
  private lazy val changedMethods = methods(submitted).filter { d =>
    originalMethods.get(d.name.value).forall(_.structure != d.structure)
  }

  expectation("Pending previews normalize, deduplicate, and sort only unaccepted email addresses.") {
    val course = Course("  Scala\t club ", List(
      Invitation("Z@example.com", false), Invitation("a@example.com", false),
      Invitation(" A@example.com ", false), Invitation("hidden@example.com", true)))
    assertEquals(InvitationMessages.pendingPreview(course),
      "Scala club: 2 pending invitations: a@example.com, z@example.com")
    assertEquals(InvitationMessages.pendingPreview(course.copy(invitations = course.invitations.reverse)),
      "Scala club: 2 pending invitations: a@example.com, z@example.com")
  }

  expectation("Empty and singular previews preserve the requested display contract.") {
    assertEquals(InvitationMessages.pendingPreview(Course(" Music ", Nil)), "Music: no pending invitations")
    assertEquals(InvitationMessages.pendingPreview(Course("Music", List(Invitation("x@y", true)))),
      "Music: no pending invitations")
    assertEquals(InvitationMessages.pendingPreview(Course("Music", List(Invitation(" X@Y ", false)))),
      "Music: 1 pending invitation: x@y")
  }

  expectation("Existing working methods retain their implementation and formatting.") {
    val current = methods(submitted).map(d => d.name.value -> d).toMap
    originalMethods.filterNot(_._1 == "pendingPreview").foreach { (name, before) =>
      assertEquals(current.get(name).map(_.pos.text), Some(before.pos.text), s"existing method changed: $name")
    }
    val beforeModels = original.collect { case d: Defn.Class => d.pos.text }
    assertEquals(submitted.collect { case d: Defn.Class => d.pos.text }, beforeModels)
  }

  expectation("Existing test bodies are preserved and project configuration is unchanged.") {
    assertEquals(read(root.resolve("project.scala")), read(starter.resolve("project.scala")))
    val testPath = Path.of("src/test/scala/classroom/InvitationMessagesSuite.scala")
    val originalTests = tree(starter.resolve(testPath)).collect {
      case t @ Term.Apply(Term.Apply(Term.Name("test"), _), _) => t.pos.text
    }
    val currentTests = read(root.resolve(testPath))
    originalTests.foreach(body => assert(currentTests.contains(body), "existing test body changed"))
    val stream = Files.walk(root)
    try {
      val addedBuildFiles = stream.iterator.asScala.filter(Files.isRegularFile(_)).map(root.relativize(_).toString)
        .filterNot(p => p.startsWith(".git/") || p.startsWith(".scala-build/") || p.startsWith(".bsp/"))
        .filter(p => p.endsWith(".conf") || p.endsWith(".sbt") || p.endsWith(".yaml") || p.endsWith(".yml"))
        .toList
      assertEquals(addedBuildFiles, Nil)
    } finally stream.close()
  }

  expectation("New or necessarily changed production expressions keep conditionals outside interpolation.") {
    assert(changedMethods.exists(_.name.value == "pendingPreview"), "feature was not implemented")
    val nested = changedMethods.flatMap(_.collect {
      case i: Term.Interpolate if i.args.exists(_.collect { case _: Term.If => true }.nonEmpty) => i.pos.text
    })
    assertEquals(nested, Nil)
  }

  expectation("New or necessarily changed production expressions avoid long receiver chains and nested pipelines.") {
    assert(changedMethods.exists(_.name.value == "pendingPreview"), "feature was not implemented")
    val chains = changedMethods.flatMap(_.collect {
      case t: Term if chainDepth(t) > 3 => t.pos.text
    })
    assertEquals(chains, Nil)
  }

  // Counts operations on a computed receiver; package/object qualification is not a chain.
  private def chainDepth(term: Term): Int = term match {
    case Term.Apply(Term.Select(receiver: Term, _), _) => chainDepth(receiver) + 1
    case Term.Select(receiver: Term, _) =>
      val depth = chainDepth(receiver)
      if (depth > 0) depth + 1 else 0
    case Term.Apply(_, args) => args.map(chainDepth).maxOption.getOrElse(0)
    case _ => 0
  }

  test("expression check accepts named stages and short email normalization") {
    val good = """object Example { def preview(xs: List[String]) = {
      val normalized = xs.map(_.trim.toLowerCase)
      val sorted = normalized.distinct.sorted
      sorted.mkString(", ")
    }}""".parse[Source].get
    assert(!good.collect { case t: Term if chainDepth(t) > 3 => t }.nonEmpty)
  }

  test("expression check rejects a nested long pipeline") {
    val bad = """object Example { def preview(xs: List[String]) =
      wrap(xs.filter(_.nonEmpty).map(_.trim).distinct.sorted) }""".parse[Source].get
    assert(bad.collect { case t: Term if chainDepth(t) > 3 => t }.nonEmpty)
  }
}

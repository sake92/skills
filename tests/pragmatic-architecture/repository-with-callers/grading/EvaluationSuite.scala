package grading

import customers.*
import exports.CustomerExport
import evaluation.RecordedSuite
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.meta.*

class EvaluationSuite extends RecordedSuite {
  private val ada = Customer("c-1", "ada@example.com", active = true)
  private val grace = Customer("c-2", "grace@example.com", active = false)
  private val duplicate = Customer("c-3", ada.email, active = true)
  private val required = Set("findById", "findByEmail", "snapshot")
  private given Dialect = dialects.Scala3
  private lazy val sourceRoot = Path.of(sys.env("SUBMISSION_DIR"), "src")
  private lazy val violations = RepositoryStructure.analyze(readScala(sourceRoot.resolve("main")), required)

  expectation("Email lookup is exact, returns the first match, and represents absence with None.") {
    val repository = new InMemoryCustomerRepository(List(ada, grace, duplicate))
    assertEquals(repository.findByEmail(ada.email), Some(ada))
    assertEquals(repository.findByEmail(grace.email), Some(grace))
    assertEquals(repository.findByEmail("ADA@example.com"), None)
    assertEquals(repository.findByEmail("missing@example.com"), None)
    assertEquals(new InMemoryCustomerRepository(Nil).findByEmail(ada.email), None)
  }

  expectation("Existing ID lookup behavior remains compatible.") {
    val repository = new InMemoryCustomerRepository(List(ada, grace, duplicate))
    assertEquals(repository.findById("c-2"), Some(grace))
    assertEquals(repository.findById("missing"), None)
    assertEquals(new InMemoryCustomerRepository(Nil).findById("c-1"), None)
  }

  expectation("The immutable snapshot API and export consumer preserve all customers in input order.") {
    val repository: CustomerRepository = new InMemoryCustomerRepository(List(grace, duplicate, ada))
    val snapshot: List[Customer] = repository.snapshot
    assertEquals(snapshot, List(grace, duplicate, ada))
    assertEquals(new CustomerExport(repository).emails, List(grace.email, duplicate.email, ada.email))
    assertEquals(repository.snapshot, snapshot)
    val empty: CustomerRepository = new InMemoryCustomerRepository(Nil)
    assertEquals(empty.snapshot, Nil)
    assertEquals(new CustomerExport(empty).emails, Nil)
  }

  expectation(
    "The repository preserves findById, findByEmail, and the caller-required snapshot without extra public helpers."
  ) {
    assertEquals(violations.filter(v => Set("interface", "helpers")(v.criterion)), Nil)
  }

  expectation("The implementation exposes no public mutable backing state, including constructor fields.") {
    assertEquals(violations.filter(_.criterion == "state"), Nil)
  }

  expectation("Public repository members expose no mutable collections.") {
    assertEquals(violations.filter(_.criterion == "returns"), Nil)
  }

  expectation("The submission adds executable MUnit email lookup assertions.") {
    val trees = readScala(sourceRoot.resolve("test")).map(_.parse[Source].get)
    val suites = trees.flatMap(_.collect {
      case cls: Defn.Class if cls.templ.inits.exists(_.tpe.syntax.endsWith("FunSuite")) => cls
    })
    val testBodies = suites.flatMap(_.collect { case Term.Apply(Term.Apply(Term.Name("test"), _), List(body)) =>
      body
    })
    assert(
      testBodies.exists { body =>
        val emailCalls = body.collect { case Term.Select(_, Term.Name("findByEmail")) => true }
        val assertions = body.collect {
          case Term.Name("assert") | Term.Name("assertEquals") | Term.Name("assertNotEquals") => true
        }
        emailCalls.nonEmpty && assertions.nonEmpty
      },
      "no MUnit test body contains an email lookup and assertion"
    )
  }

  test("grader permits an immutable snapshot needed by a caller") {
    assertEquals(
      RepositoryStructure.analyze(List(example("private val records = List.empty[Customer]")), required),
      Nil
    )
  }

  test("grader rejects deleting the snapshot even if a consumer is rewritten") {
    val withoutSnapshot = example("private val records = List.empty[Customer]")
      .replace("def snapshot: List[Customer]", "private def snapshot: List[Customer]")
    assert(RepositoryStructure.analyze(List(withoutSnapshot), required).exists(_.criterion == "interface"))
  }

  test("grader rejects prototype escape hatches alongside a legitimate snapshot") {
    val code = example("val records = scala.collection.mutable.ArrayBuffer.empty[Customer]") +
      """
        |  def all = records
        |  def query(predicate: Customer => Boolean) = records.filter(predicate)
        |""".stripMargin
    val found = RepositoryStructure.analyze(List(code), required)
    assert(found.exists(_.criterion == "state"))
    assert(found.exists(v => v.criterion == "helpers" && v.message.contains("all")))
    assert(found.exists(v => v.criterion == "helpers" && v.message.contains("query")))
    assert(found.exists(_.criterion == "returns"))
  }

  private def example(storage: String): String =
    s"""
       |trait CustomerRepository:
       |  def findById(id: String): Option[Customer]
       |  def findByEmail(email: String): Option[Customer]
       |  def snapshot: List[Customer]
       |final class InMemoryCustomerRepository(seed: Seq[Customer]) extends CustomerRepository:
       |  $storage
       |  def findById(id: String): Option[Customer] = None
       |  def findByEmail(email: String): Option[Customer] = None
       |  def snapshot: List[Customer] = records.toList
       |""".stripMargin

  private def readScala(root: Path): List[String] =
    if !Files.isDirectory(root) then Nil
    else
      val stream = Files.walk(root)
      try stream.iterator.asScala.filter(path => path.toString.endsWith(".scala")).map(Files.readString).toList
      finally stream.close()
}

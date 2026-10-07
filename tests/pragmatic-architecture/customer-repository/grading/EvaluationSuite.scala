package grading

import customers.*
import evaluation.RecordedSuite
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

class EvaluationSuite extends RecordedSuite {
  private val ada = Customer("c-1", "ada@example.com", "north", active = true)
  private val grace = Customer("c-2", "grace@example.com", "north", active = false)
  private val linus = Customer("c-3", "linus@example.com", "south", active = true)
  private val barbara = Customer("c-4", "barbara@example.com", "north", active = true)

  private def repository =
    new InMemoryCustomerRepository(List(ada, grace, linus, barbara))

  private lazy val sourceRoot = Path.of(sys.env("SUBMISSION_DIR"), "src")
  private lazy val mainSources = readScala(sourceRoot.resolve("main"))
  private lazy val testSources = readScala(sourceRoot.resolve("test"))
  private lazy val violations = RepositoryStructure.analyze(mainSources)

  expectation("Lookup by ID returns the matching customer and None for a missing ID."):
    assertEquals(repository.findById("c-3"), Some(linus))
    assertEquals(repository.findById("missing"), None)

  expectation("Lookup by email is exact and returns None for missing or differently-cased email addresses."):
    assertEquals(repository.findByEmail("ada@example.com"), Some(ada))
    assertEquals(repository.findByEmail("ADA@example.com"), None)
    assertEquals(repository.findByEmail("missing@example.com"), None)

  expectation(
    "Active customers are filtered by region and status, preserve input order, and a missing region returns an empty list."
  ):
    assertEquals(repository.activeByRegion("north"), List(ada, barbara))
    assertEquals(repository.activeByRegion("south"), List(linus))
    assertEquals(repository.activeByRegion("missing"), Nil)

  expectation("CustomerRepository exposes exactly findById, findByEmail, and activeByRegion."):
    assertEquals(violations.filter(_.criterion == "interface"), Nil)

  expectation("InMemoryCustomerRepository exposes no public mutable state, including constructor fields."):
    assertEquals(violations.filter(_.criterion == "state"), Nil)

  expectation("Helpers are private or protected, with no public all, records, or generic query escape hatch."):
    assertEquals(violations.filter(_.criterion == "helpers"), Nil)

  expectation("No public method or value exposes a mutable collection type."):
    assertEquals(violations.filter(_.criterion == "returns"), Nil)

  expectation("The submission includes focused MUnit coverage for all three required operations."):
    val tests = testSources.mkString("\n")
    assert(tests.contains("munit.FunSuite"), "no MUnit FunSuite found under src/test")
    List("findById", "findByEmail", "activeByRegion").foreach: operation =>
      assert(tests.contains(operation), s"candidate tests do not exercise $operation")

  test("grader accepts the intended narrow repository"):
    val code =
      """
        |trait CustomerRepository:
        |  def findById(id: String): Option[Customer]
        |  def findByEmail(email: String): Option[Customer]
        |  def activeByRegion(region: String): List[Customer]
        |
        |final class InMemoryCustomerRepository(seed: Seq[Customer]) extends CustomerRepository:
        |  private val records = scala.collection.mutable.ArrayBuffer.from(seed)
        |  def findById(id: String): Option[Customer] = records.find(_.id == id)
        |  def findByEmail(email: String): Option[Customer] = records.find(_.email == email)
        |  def activeByRegion(region: String): List[Customer] = matching(region).filter(_.active).toList
        |  protected def matching(region: String) = records.filter(_.region == region)
        |""".stripMargin
    assertEquals(RepositoryStructure.analyze(List(code)), Nil)

  test("grader rejects each forbidden public surface") {
    val code =
      """
        |import scala.collection.mutable.{Map as MutableMap}
        |import scala.collection.{mutable as m}
        |trait CustomerRepository:
        |  def findById(id: String): Option[Customer]
        |  def findByEmail(email: String): Option[Customer]
        |  def activeByRegion(region: String): List[Customer]
        |  def query(text: String): List[Customer]
        |
        |final class InMemoryCustomerRepository(
        |    val records: MutableMap[String, Customer],
        |    var revision: Int,
        |    val slots: Array[Customer]
        |) extends CustomerRepository:
        |  def all: scala.collection.mutable.Seq[Customer] = records.values.toSeq
        |  def query(text: String): scala.collection.mutable.Map[String, Customer] = records
        |  def snapshot = records
        |  def exposedSlots: Array[Customer] = slots
        |  def packageAliasReturn: m.Buffer[Customer] = m.Buffer.empty
        |  def findById(id: String): Option[Customer] = None
        |  def findByEmail(email: String): Option[Customer] = None
        |  def activeByRegion(region: String): List[Customer] = Nil
        |""".stripMargin

    val found = RepositoryStructure.analyze(List(code))
    assert(found.exists(_.criterion == "interface"))
    assert(found.exists(v => v.criterion == "state" && v.message.contains("records")))
    assert(found.exists(v => v.criterion == "state" && v.message.contains("revision")))
    assert(found.exists(v => v.criterion == "state" && v.message.contains("slots")))
    assert(found.exists(v => v.criterion == "helpers" && v.message.contains("all")))
    assert(found.exists(v => v.criterion == "helpers" && v.message.contains("query")))
    assert(found.exists(v => v.criterion == "returns" && v.message.contains("mutable")))
    assert(found.exists(v => v.criterion == "returns" && v.message.contains("snapshot")))
    assert(found.exists(v => v.criterion == "returns" && v.message.contains("exposedSlots")))
    assert(found.exists(v => v.criterion == "returns" && v.message.contains("packageAliasReturn")))
  }

  test("grader accepts unqualified immutable collections"):
    val code =
      """
        |trait CustomerRepository:
        |  def findById(id: String): Option[Customer]
        |  def findByEmail(email: String): Option[Customer]
        |  def activeByRegion(region: String): List[Customer]
        |
        |final class InMemoryCustomerRepository(seed: Seq[Customer]) extends CustomerRepository:
        |  private val records: Map[String, Customer] = seed.map(c => c.id -> c).toMap
        |  private val regions: Set[String] = seed.map(_.region).toSet
        |  def findById(id: String): Option[Customer] = records.get(id)
        |  def findByEmail(email: String): Option[Customer] = records.values.find(_.email == email)
        |  def activeByRegion(region: String): List[Customer] =
        |    if regions(region) then records.values.filter(c => c.region == region && c.active).toList else Nil
        |""".stripMargin
    assertEquals(RepositoryStructure.analyze(List(code)), Nil)

  test("grader rejects mutable types imported from nested mutable packages") {
    val code =
      """
        |import scala.collection.mutable.concurrent.TrieMap
        |trait CustomerRepository:
        |  def findById(id: String): Option[Customer]
        |  def findByEmail(email: String): Option[Customer]
        |  def activeByRegion(region: String): List[Customer]
        |
        |final class InMemoryCustomerRepository(
        |    val records: TrieMap[String, Customer]
        |) extends CustomerRepository:
        |  def findById(id: String): Option[Customer] = records.get(id)
        |  def findByEmail(email: String): Option[Customer] = records.values.find(_.email == email)
        |  def activeByRegion(region: String): List[Customer] = Nil
        |""".stripMargin

    val found = RepositoryStructure.analyze(List(code))
    assert(found.exists(v => v.criterion == "state" && v.message.contains("records")))
  }

  private def readScala(root: Path): List[String] =
    if !Files.isDirectory(root) then Nil
    else
      val stream = Files.walk(root)
      try stream.iterator.asScala.filter(path => path.toString.endsWith(".scala")).map(Files.readString).toList
      finally stream.close()
}

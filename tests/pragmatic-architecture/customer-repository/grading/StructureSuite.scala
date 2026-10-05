package grading

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.meta.*

class StructureSuite extends munit.FunSuite:
  private val required = Set("findById", "findByEmail", "activeByRegion")
  private val escapeHatches = Set("all", "records", "query")
  private val mutableTypes = Set(
    "ArrayBuffer",
    "Buffer",
    "HashMap",
    "HashSet",
    "LinkedHashMap",
    "LinkedHashSet",
    "ListBuffer",
    "MutableList",
    "Queue",
    "Stack"
  )

  private given Dialect = dialects.Scala3

  private def hidden(mods: List[Mod]): Boolean =
    mods.exists:
      case _: Mod.Private | _: Mod.Protected => true
      case _                                 => false

  private def names(pats: List[Pat]): List[String] =
    pats.collect { case Pat.Var(name) => name.value }

  private def publicTerms(stats: List[Stat]): List[(String, Tree)] =
    stats.flatMap:
      case member: Decl.Def if !hidden(member.mods) => List(member.name.value -> member)
      case member: Defn.Def if !hidden(member.mods) => List(member.name.value -> member)
      case member: Decl.Val if !hidden(member.mods) => names(member.pats).map(_ -> member)
      case member: Defn.Val if !hidden(member.mods) => names(member.pats).map(_ -> member)
      case member: Decl.Var if !hidden(member.mods) => names(member.pats).map(_ -> member)
      case member: Defn.Var if !hidden(member.mods) => names(member.pats).map(_ -> member)
      case _                                        => Nil

  private def descendants(tree: Tree): List[Tree] =
    tree :: tree.children.flatMap(descendants)

  private def mentionsMutable(tree: Tree): Boolean =
    tree.syntax.contains("collection.mutable") || descendants(tree).collect:
      case Type.Name(name) if mutableTypes(name) => name
      case Term.Name(name) if mutableTypes(name) => name
    .nonEmpty

  private def declaredReturn(member: Tree): Option[Type] = member match
    case value: Decl.Val => Some(value.decltpe)
    case value: Decl.Var => Some(value.decltpe)
    case value: Defn.Val => value.decltpe
    case value: Defn.Var => value.decltpe
    case method: Decl.Def => Some(method.decltpe)
    case method: Defn.Def => method.decltpe
    case _                => None

  private def analyze(code: String): List[String] =
    analyzeAll(List(code))

  private def analyzeAll(codes: List[String]): List[String] =
    val trees = codes.map(_.parse[Source].get)
    val repository = trees.flatMap(tree => descendants(tree).collect:
      case definition: Defn.Trait if definition.name.value == "CustomerRepository" => definition
    )
    val inMemory = trees.flatMap(tree => descendants(tree).collect:
      case definition: Defn.Class if definition.name.value == "InMemoryCustomerRepository" => definition
    )

    val violations = List.newBuilder[String]

    repository match
      case definition :: Nil =>
        val terms = publicTerms(definition.templ.stats)
        val names = terms.map(_._1)
        if names.toSet != required || names.size != required.size then
          violations += s"CustomerRepository public members were ${names.sorted.mkString(", ")}"
      case _ => violations += "expected exactly one CustomerRepository trait"

    inMemory match
      case definition :: Nil =>
        val terms = publicTerms(definition.templ.stats)
        val publicNames = terms.map(_._1)

        publicNames.filterNot(required).foreach: name =>
          violations += s"helper '$name' is public"

        publicNames.filter(escapeHatches).foreach: name =>
          violations += s"public '$name' escape hatch"

        definition.templ.stats.foreach:
          case value: Defn.Var if !hidden(value.mods) =>
            violations += s"public mutable variable ${names(value.pats).mkString(", ")}"
          case value: Decl.Var if !hidden(value.mods) =>
            violations += s"public mutable variable ${names(value.pats).mkString(", ")}"
          case value: Defn.Val if !hidden(value.mods) && mentionsMutable(value) =>
            violations += s"public mutable collection ${names(value.pats).mkString(", ")}"
          case _ => ()

        terms.foreach: (name, member) =>
          declaredReturn(member).filter(mentionsMutable).foreach: _ =>
            violations += s"public '$name' returns a mutable collection"
      case _ => violations += "expected exactly one InMemoryCustomerRepository class"

    violations.result().distinct.sorted

  test("accepts the intended narrow repository"):
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
        |  def activeByRegion(region: String): List[Customer] =
        |    matching(region).filter(_.active).toList
        |  private def matching(region: String) = records.filter(_.region == region)
        |""".stripMargin

    assertEquals(analyze(code), Nil)

  test("rejects exposed state, helpers, mutable returns, and escape hatches"):
    val code =
      """
        |trait CustomerRepository:
        |  def findById(id: String): Option[Customer]
        |  def findByEmail(email: String): Option[Customer]
        |  def activeByRegion(region: String): List[Customer]
        |  def query(text: String): List[Customer]
        |
        |final class InMemoryCustomerRepository(seed: Seq[Customer]) extends CustomerRepository:
        |  val records = scala.collection.mutable.ArrayBuffer.from(seed)
        |  def all: scala.collection.mutable.Buffer[Customer] = records
        |  def query(text: String): scala.collection.mutable.Buffer[Customer] = records
        |  def findById(id: String): Option[Customer] = None
        |  def findByEmail(email: String): Option[Customer] = None
        |  def activeByRegion(region: String): List[Customer] = Nil
        |""".stripMargin

    val violations = analyze(code).mkString("\n")
    assert(violations.contains("CustomerRepository public members"))
    assert(violations.contains("public mutable collection records"))
    assert(violations.contains("helper 'all' is public"))
    assert(violations.contains("helper 'query' is public"))
    assert(violations.contains("returns a mutable collection"))
    assert(violations.contains("escape hatch"))

  test("submission has only the required public surface"):
    val root = Path.of(sys.env.getOrElse("SUBMISSION_DIR", "WORK"), "src", "main", "scala")
    val sources =
      val stream = Files.walk(root)
      try stream.iterator.asScala.filter(path => path.toString.endsWith(".scala")).map(Files.readString).toList
      finally stream.close()

    assertEquals(analyzeAll(sources), Nil)

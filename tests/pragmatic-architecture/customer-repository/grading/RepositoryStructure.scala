package grading

import scala.meta.*

case class Violation(criterion: String, message: String)

object RepositoryStructure {
  private val required = Set("findById", "findByEmail", "activeByRegion")
  private val escapeHatches = Set("all", "records", "query")
  private val mutablePrefix = "scala.collection.mutable"
  private case class MutableReferences(typeNames: Set[String], prefixes: Set[String])

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

  private def mutableReferences(trees: List[Source]): MutableReferences =
    val imports = trees.flatMap(tree => descendants(tree).collect { case importer: Importer => importer })
    val importedTypes = imports.flatMap:
      case Importer(ref, importees) if ref.syntax.startsWith(mutablePrefix) =>
        importees.flatMap:
          case Importee.Name(name)         => List(name.value)
          case Importee.Rename(_, renamed) => List(renamed.value)
          case _                           => Nil
      case _ => Nil
    val prefixes = imports.flatMap:
      case Importer(ref, importees) if ref.syntax == "scala.collection" =>
        importees.collect:
          case Importee.Name(name) if name.value == "mutable"            => name.value
          case Importee.Rename(name, renamed) if name.value == "mutable" => renamed.value
      case _ => Nil
    MutableReferences(importedTypes.toSet + "Array", prefixes.toSet)

  private def mentionsMutable(tree: Tree, references: MutableReferences): Boolean =
    tree.syntax.contains(s"$mutablePrefix.") ||
      descendants(tree).exists:
        case Type.Name(name)      => references.typeNames(name)
        case Term.Name(name)      => references.typeNames(name)
        case Type.Select(qual, _) => references.prefixes(qual.syntax)
        case Term.Select(qual, _) => references.prefixes(qual.syntax)
        case _                    => false

  private def declaredReturn(member: Tree): Option[Type] = member match
    case value: Decl.Val  => Some(value.decltpe)
    case value: Decl.Var  => Some(value.decltpe)
    case value: Defn.Val  => value.decltpe
    case value: Defn.Var  => value.decltpe
    case method: Decl.Def => Some(method.decltpe)
    case method: Defn.Def => method.decltpe
    case _                => None

  private def returnsMutable(member: Tree, references: MutableReferences, mutableStorage: Set[String]): Boolean =
    declaredReturn(member).exists(mentionsMutable(_, references)) || (member match
      case value: Defn.Val  => mentionsMutable(value.rhs, references)
      case value: Defn.Var  => value.rhs.exists(mentionsMutable(_, references))
      case method: Defn.Def =>
        method.body match
          case Term.Name(name) => mutableStorage(name)
          case body            => mentionsMutable(body, references)
      case _ => false)

  def analyze(codes: List[String]): List[Violation] = {
    val trees = codes.map(_.parse[Source].get)
    val mutableRefs = mutableReferences(trees)
    val repository = trees.flatMap(tree =>
      descendants(tree).collect:
        case definition: Defn.Trait if definition.name.value == "CustomerRepository" => definition
    )
    val inMemory = trees.flatMap(tree =>
      descendants(tree).collect:
        case definition: Defn.Class if definition.name.value == "InMemoryCustomerRepository" => definition
    )
    val violations = List.newBuilder[Violation]

    repository match
      case definition :: Nil =>
        val names = publicTerms(definition.templ.stats).map(_._1)
        if names.toSet != required || names.size != required.size then
          violations += Violation("interface", s"CustomerRepository public members were ${names.sorted.mkString(", ")}")
      case _ => violations += Violation("interface", "expected exactly one CustomerRepository trait")

    inMemory match {
      case definition :: Nil =>
        val terms = publicTerms(definition.templ.stats)
        val publicNames = terms.map(_._1)
        val mutableFields = definition.templ.stats.flatMap:
          case value: Defn.Val if mentionsMutable(value, mutableRefs) => names(value.pats)
          case value: Defn.Var                                        => names(value.pats)
          case _                                                      => Nil
        val mutableConstructorFields = definition.ctor.paramss.flatten.collect:
          case parameter if parameter.decltpe.exists(mentionsMutable(_, mutableRefs)) => parameter.name.value
        val mutableStorage = (mutableFields ++ mutableConstructorFields).toSet

        publicNames
          .filterNot(required)
          .foreach: name =>
            violations += Violation("helpers", s"helper '$name' is public")
        publicNames
          .filter(escapeHatches)
          .foreach: name =>
            violations += Violation("helpers", s"public '$name' escape hatch")

        definition.ctor.paramss.flatten.foreach: parameter =>
          val isField = parameter.mods.exists:
            case _: Mod.ValParam | _: Mod.VarParam => true
            case _                                 => false
          if isField && !hidden(parameter.mods) then
            if parameter.mods.exists(_.isInstanceOf[Mod.VarParam]) then
              violations += Violation("state", s"public mutable constructor variable ${parameter.name.value}")
            else if parameter.decltpe.exists(mentionsMutable(_, mutableRefs)) then
              violations += Violation("state", s"public mutable constructor collection ${parameter.name.value}")

        definition.templ.stats.foreach:
          case value: Defn.Var if !hidden(value.mods) =>
            violations += Violation("state", s"public mutable variable ${names(value.pats).mkString(", ")}")
          case value: Decl.Var if !hidden(value.mods) =>
            violations += Violation("state", s"public mutable variable ${names(value.pats).mkString(", ")}")
          case value: Defn.Val if !hidden(value.mods) && mentionsMutable(value, mutableRefs) =>
            violations += Violation("state", s"public mutable collection ${names(value.pats).mkString(", ")}")
          case _ => ()

        terms.foreach: (name, member) =>
          if returnsMutable(member, mutableRefs, mutableStorage) then
            violations += Violation("returns", s"public '$name' returns a mutable collection")
      case _ => violations += Violation("state", "expected exactly one InMemoryCustomerRepository class")
    }

    violations.result().distinct.sortBy(v => v.criterion -> v.message)
  }
}

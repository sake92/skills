package grading

import scala.meta.*

/** A fixture-specific source check, complemented by the shared-ownership judge. */
object AdapterConstruction {
  private val adapters = Set("BookingController", "NightlyImport")
  private val knownBusinessTypes = Set("BookingService", "BookingRepository", "InMemoryBookingRepository")
  private given Dialect = dialects.Scala3

  def analyze(codes: List[String]): List[String] = {
    val trees = codes.map(_.parse[Source].get)
    val domainTypes = trees
      .flatMap(_.collect { case pkg: Pkg if pkg.ref.syntax == "bookings" => pkg })
      .flatMap(_.collect {
        case cls: Defn.Class if !cls.mods.exists(_.isInstanceOf[Mod.Case]) => cls.name.value
        case definition: Defn.Trait                                        => definition.name.value
      })
      .toSet
    val originals = knownBusinessTypes ++ domainTypes
    val renames = trees
      .flatMap(_.collect {
        case Importee.Rename(name, renamed) if originals(name.value) => renamed.value
      })
      .toSet
    val aliases = trees
      .flatMap(_.collect {
        case definition: Defn.Type if typeNames(definition.body).exists((originals ++ renames).contains) =>
          definition.name.value
      })
      .toSet
    val businessTypes = originals ++ renames ++ aliases
    val classes = trees.flatMap(_.collect { case cls: Defn.Class if adapters(cls.name.value) => cls })
    val companions = trees.flatMap(_.collect { case obj: Defn.Object if adapters(obj.name.value) => obj })
    val errors = List.newBuilder[String]

    adapters.toList.sorted.foreach { name =>
      val found = classes.filter(_.name.value == name)
      if found.size != 1 then errors += s"expected one $name class"
      found.foreach { cls =>
        val injected = cls.ctor.paramss.flatten.exists(parameter =>
          parameter.decltpe.exists(tpe => typeNames(tpe).exists(businessTypes.contains))
        )
        if !injected then errors += s"$name has no constructor-supplied business dependency"
      }
    }

    (classes ++ companions).foreach { adapter =>
      adapter
        .collect {
          case Term.New(init) if typeNames(init.tpe).exists(businessTypes.contains) => init.tpe.syntax
          case Term.NewAnonymous(templ)
              if templ.inits.exists(init => typeNames(init.tpe).exists(businessTypes.contains)) =>
            templ.inits.map(_.tpe.syntax).mkString(" with ")
          case Term.Apply(Term.Name(name), _) if businessTypes(name)                                  => name
          case Term.Apply(Term.Select(Term.Name(name), Term.Name("apply")), _) if businessTypes(name) => name
          case Term.Apply(Term.Select(_, Term.Name(name)), _) if businessTypes(name)                  => name
        }
        .foreach(name => errors += s"adapter constructs business collaborator $name")
    }
    errors.result().distinct.sorted
  }

  private def typeNames(tree: Tree): Set[String] =
    tree.collect { case Type.Name(name) => name }.toSet
}

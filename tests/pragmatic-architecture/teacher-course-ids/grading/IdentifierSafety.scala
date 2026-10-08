package grading

import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import scala.meta.*

/** Discover submitted parameter types, then check calls from outside their defining scope. */
object IdentifierSafety {
  case class Result(passed: Boolean, evidence: String)
  private case class Signature(owner: String, pkg: String, imports: List[String], types: List[String])
  private val owners = List("TeacherService", "TeacherRepository", "InMemoryTeacherRepository")
  private given Dialect = dialects.Scala3

  def verify(submission: Path, output: Path): Result =
    val stream = Files.walk(submission.resolve("src/main"))
    val codes = try stream.iterator.asScala.filter(_.toString.endsWith(".scala")).map(Files.readString).toList
    finally stream.close()
    generate(codes) match
      case Left(error)   => Result(false, error)
      case Right(source) =>
        Files.createDirectories(output.resolve("src/test/scala"))
        Files.writeString(
          output.resolve("project.scala"),
          "//> using scala 3.9.0\n//> using dep org.scalameta::munit:1.3.6\n"
        )
        Files.writeString(output.resolve("src/test/scala/IdentifierProbeSuite.scala"), source)
        val log = output.resolve("compile.log")
        val process = new ProcessBuilder(
          "scala-cli",
          "test",
          output.resolve("project.scala").toString,
          submission.resolve("project.scala").toString,
          submission.resolve("src/main").toString,
          output.resolve("src/test/scala/IdentifierProbeSuite.scala").toString,
          "--server=false"
        ).redirectErrorStream(true).redirectOutput(log.toFile).start()
        process.getOutputStream.close()
        try
          val completed = process.waitFor(180, TimeUnit.SECONDS)
          Result(
            completed && process.exitValue() == 0,
            if !completed then s"Identifier compiler probes timed out; see $log"
            else Files.readString(log).takeRight(12000)
          )
        finally
          if process.isAlive then
            val descendants = process.descendants()
            try descendants.iterator.asScala.toList.reverse.foreach(_.destroyForcibly())
            finally descendants.close()
            process.destroyForcibly()
            process.waitFor(10, TimeUnit.SECONDS)

  def generate(codes: List[String]): Either[String, String] =
    val signatures = codes.flatMap { code =>
      def scan(stats: List[Stat], pkg: String, inherited: List[String]): List[Signature] =
        val imports = inherited ++ stats.collect { case value: Import => value.syntax }
        stats.flatMap {
          case value: Pkg => scan(value.stats, List(pkg, value.ref.syntax).filter(_.nonEmpty).mkString("."), imports)
          case value: Defn.Class if owners.contains(value.name.value) =>
            methods(value.name.value, value.templ.stats, pkg, imports)
          case value: Defn.Trait if owners.contains(value.name.value) =>
            methods(value.name.value, value.templ.stats, pkg, imports)
          case _ => Nil
        }
      scan(code.parse[Source].get.stats, "", Nil)
    }
    val byOwner = signatures.groupBy(_.owner)
    val missing = owners.filterNot(byOwner.contains).filterNot(_ == "InMemoryTeacherRepository")
    if missing.nonEmpty then Left(s"Missing lookup signatures: ${missing.mkString(", ")}")
    else
      val tests = owners.map { owner =>
        val signature = byOwner
          .get(owner)
          .flatMap(_.headOption)
          .getOrElse(byOwner("TeacherRepository").head.copy(owner = owner))
        val imports = (List(s"import _root_.${signature.pkg}.*") ++ signature.imports).mkString("\n")
        val List(userType, courseType) = signature.types: @unchecked
        def call(user: String, course: String): String =
          s"""$imports
             |def probe(target: _root_.${signature.pkg}.$owner, user: $userType, course: $courseType,
             |          rawUser: java.util.UUID, rawCourse: java.util.UUID) =
             |  target.findByUserIdAndCourseId($user, $course)
             |""".stripMargin
        def literal(value: String): String = Lit.String(value).syntax
        val bad = List(("course", "user"), ("rawUser", "rawCourse"), ("rawUser", "course"), ("user", "rawCourse"))
        s"""test(${literal(owner)}) {
           |  assertEquals(typeCheckErrors(${literal(call("user", "course"))}), Nil)
           |${bad.map { case (u, c) => s"  assert(typeCheckErrors(${literal(call(u, c))}).nonEmpty)" }.mkString("\n")}
           |}
           |""".stripMargin
      }
      Right(s"""package protectedprobes
               |import scala.compiletime.testing.typeCheckErrors
               |class IdentifierProbeSuite extends munit.FunSuite {
               |${tests.mkString("\n")}
               |}
               |""".stripMargin)

  private def methods(owner: String, stats: List[Stat], pkg: String, imports: List[String]): List[Signature] =
    stats.flatMap {
      case method: Defn.Def if method.name.value == "findByUserIdAndCourseId" =>
        signature(owner, method.paramss, pkg, imports)
      case method: Decl.Def if method.name.value == "findByUserIdAndCourseId" =>
        signature(owner, method.paramss, pkg, imports)
      case _ => Nil
    }

  private def signature(
      owner: String,
      parameters: List[List[Term.Param]],
      pkg: String,
      imports: List[String]
  ): List[Signature] =
    val types = parameters.headOption.toList.flatten.flatMap(_.decltpe).map(_.syntax)
    Option.when(types.size == 2)(Signature(owner, pkg, imports, types)).toList
}

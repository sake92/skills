package grading

class IdentifierSafetySuite extends munit.FunSuite {
  test("probes use submitted names and cover service, trait, and concrete repository") {
    val source = """package teachers
      |import identifiers.{MemberKey as UserKey, ClassKey}
      |class TeacherService {
      |  def findByUserIdAndCourseId(user: UserKey, course: ClassKey): Option[TeacherRecord] = None
      |}
      |trait TeacherRepository {
      |  def findByUserIdAndCourseId(user: UserKey, course: ClassKey): Option[TeacherRecord]
      |}
      |class InMemoryTeacherRepository extends TeacherRepository {
      |  def findByUserIdAndCourseId(user: UserKey, course: ClassKey): Option[TeacherRecord] = None
      |}
      |""".stripMargin
    val generated = IdentifierSafety.generate(List(source)).toOption.get
    assert(generated.contains("MemberKey as UserKey"))
    assert(generated.contains("user: UserKey, course: ClassKey"))
    List("TeacherService", "TeacherRepository", "InMemoryTeacherRepository").foreach(owner =>
      assert(generated.contains(owner))
    )
    assert(generated.contains("findByUserIdAndCourseId(course, user)"))
    assert(generated.contains("findByUserIdAndCourseId(rawUser, course)"))
    assert(generated.contains("findByUserIdAndCourseId(user, rawCourse)"))
  }

  test("missing or unsupported signatures cannot be counted as compiler safety") {
    assert(IdentifierSafety.generate(Nil).isLeft)
    assert(IdentifierSafety.generate(List("package teachers; class TeacherService")).isLeft)
  }

  test("a concrete repository can inherit its typed method") {
    val source = """package teachers
      |class TeacherService {
      |  def findByUserIdAndCourseId(user: identifiers.MemberKey, course: identifiers.ClassKey): Option[TeacherRecord] = None
      |}
      |trait TeacherRepository {
      |  def findByUserIdAndCourseId(user: identifiers.MemberKey, course: identifiers.ClassKey): Option[TeacherRecord] = None
      |}
      |class InMemoryTeacherRepository extends TeacherRepository
      |""".stripMargin
    assert(IdentifierSafety.generate(List(source)).toOption.get.contains("teachers.InMemoryTeacherRepository"))
  }
}

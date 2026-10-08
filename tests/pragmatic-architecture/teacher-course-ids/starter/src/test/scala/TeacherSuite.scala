import java.util.UUID
import teachers.*
import http.*
import reports.CourseRoster
import directory.*

class TeacherSuite extends munit.FunSuite {
  private val id = UUID.fromString("00000000-0000-0000-0000-000000000001")
  private def service = new TeacherService(new InMemoryTeacherRepository(List(TeacherRecord(id, id, "Ada"))))

  test("existing diagonal lookup and report") {
    assertEquals(new TeacherController(service).get(id.toString, id.toString), TeacherResponse(200, "Ada"))
    assertEquals(new CourseRoster(service).namesFor(id, List(id)), List(Some("Ada")))
  }

  test("absence and malformed URL identifiers") {
    val empty = new TeacherController(new TeacherService(new InMemoryTeacherRepository(Nil)))
    assertEquals(empty.get(id.toString, id.toString), TeacherResponse(404, "teacher not found"))
    assertEquals(empty.get("bad", id.toString), TeacherResponse(400, "invalid identifier"))
    assertEquals(empty.get(id.toString, "bad"), TeacherResponse(400, "invalid identifier"))
  }

  test("existing directory consumers") {
    assertEquals(new UserDirectory(Map(id -> "Ada")).name(id), Some("Ada"))
    assertEquals(new CourseDirectory(Map(id -> "Scala")).title(id), Some("Scala"))
  }
}

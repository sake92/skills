package grading

import directory.*
import http.*
import reports.CourseRoster
import teachers.*
import java.util.UUID
import evaluation.RecordedSuite

class EvaluationSuite extends RecordedSuite {
  private val user = uuid(1)
  private val course = uuid(2)
  private val other = uuid(3)
  private def service(rows: List[TeacherRecord]) = new TeacherService(new InMemoryTeacherRepository(rows))

  expectation("HTTP lookup distinguishes user and course IDs even when the reversed membership exists.") {
    val controller = new TeacherController(
      service(
        List(
          TeacherRecord(user, course, "Ada"),
          TeacherRecord(course, user, "Reversed"),
          TeacherRecord(other, course, "Grace"),
          TeacherRecord(user, other, "Another course")
        )
      )
    )
    assertEquals(controller.get(user.toString, course.toString), TeacherResponse(200, "Ada"))
    assertEquals(controller.get(course.toString, user.toString), TeacherResponse(200, "Reversed"))
    assertEquals(controller.get(other.toString, course.toString), TeacherResponse(200, "Grace"))
    assertEquals(controller.get(user.toString, other.toString), TeacherResponse(200, "Another course"))
  }

  expectation("Cached lookups remain isolated by both user and course, including reversed pairs.") {
    val controller = new TeacherController(
      service(
        List(
          TeacherRecord(user, course, "Ada"),
          TeacherRecord(course, user, "Reversed"),
          TeacherRecord(user, other, "Other course"),
          TeacherRecord(other, course, "Other user")
        )
      )
    )
    List(
      (user, course, "Ada"),
      (course, user, "Reversed"),
      (user, other, "Other course"),
      (other, course, "Other user"),
      (user, course, "Ada"),
      (course, user, "Reversed")
    ).foreach { (u, c, name) =>
      assertEquals(controller.get(u.toString, c.toString), TeacherResponse(200, name))
    }
    assertEquals(controller.get(other.toString, other.toString), TeacherResponse(404, "teacher not found"))
  }

  expectation("The roster consumer preserves course order, missing memberships, and UUID inputs.") {
    val roster =
      new CourseRoster(service(List(TeacherRecord(user, course, "Ada"), TeacherRecord(user, other, "Grace"))))
    assertEquals(
      roster.namesFor(user, List(other, uuid(4), course, other)),
      List(Some("Grace"), None, Some("Ada"), Some("Grace"))
    )
    assertEquals(roster.namesFor(user, Nil), Nil)
  }

  expectation("HTTP UUID strings, invalid-identifier errors, and missing-teacher responses remain compatible.") {
    val controller = new TeacherController(service(List(TeacherRecord(user, course, "Ada"))))
    assertEquals(controller.get(user.toString, course.toString), TeacherResponse(200, "Ada"))
    assertEquals(controller.get("bad", course.toString), TeacherResponse(400, "invalid identifier"))
    assertEquals(controller.get(user.toString, "bad"), TeacherResponse(400, "invalid identifier"))
    assertEquals(controller.get("", course.toString), TeacherResponse(400, "invalid identifier"))
    assertEquals(controller.get(user.toString, uuid(4).toString), TeacherResponse(404, "teacher not found"))
    assertEquals(controller.get(course.toString, user.toString), TeacherResponse(404, "teacher not found"))
  }

  expectation("Service and repository lookup APIs reject swapped and raw identifiers at compile time.") {
    val submission = java.nio.file.Path.of(sys.env("SUBMISSION_DIR"))
    val report = java.nio.file.Path.of(sys.env("GRADING_JSON")).toAbsolutePath
    val output = report.getParent.resolve(report.getFileName.toString.stripSuffix(".json") + "-identifier-probes")
    val result = IdentifierSafety.verify(submission, output)
    assert(result.passed, result.evidence)
  }

  expectation("Existing directory consumers and UUID persistence records retain their behavior.") {
    assertEquals(new UserDirectory(Map(user -> "Ada")).name(user), Some("Ada"))
    assertEquals(new UserDirectory(Map(user -> "Ada")).name(other), None)
    assertEquals(new CourseDirectory(Map(course -> "Scala")).title(course), Some("Scala"))
    val row = TeacherRecord(user, course, "Ada")
    val persistedUser: UUID = row.userId
    val persistedCourse: UUID = row.courseId
    assertEquals((persistedUser, persistedCourse), (user, course))
  }

  private def uuid(n: Int): UUID = new UUID(0, n.toLong)
}

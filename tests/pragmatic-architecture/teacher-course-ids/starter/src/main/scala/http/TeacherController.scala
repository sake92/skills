package http

import java.util.UUID
import teachers.TeacherService

case class TeacherResponse(status: Int, body: String)

final class TeacherController(service: TeacherService) {
  def get(userId: String, courseId: String): TeacherResponse =
    val parsed = for
      user <- parseUuid(userId)
      course <- parseUuid(courseId)
    yield (user, course)
    parsed match
      case None                 => TeacherResponse(400, "invalid identifier")
      case Some((user, course)) =>
        service.findByUserIdAndCourseId(user, course) match
          case Some(teacher) => TeacherResponse(200, teacher.displayName)
          case None          => TeacherResponse(404, "teacher not found")

  private def parseUuid(value: String): Option[UUID] =
    try Some(UUID.fromString(value))
    catch case _: IllegalArgumentException => None
}

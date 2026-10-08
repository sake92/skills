package reports

import java.util.UUID
import teachers.TeacherService

final class CourseRoster(service: TeacherService):
  def namesFor(userId: UUID, courseIds: List[UUID]): List[Option[String]] =
    courseIds.map(courseId => service.findByUserIdAndCourseId(userId, courseId).map(_.displayName))

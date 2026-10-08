package teachers

import java.util.UUID

// Persistence records keep their UUID representation.
case class TeacherRecord(userId: UUID, courseId: UUID, displayName: String)

trait TeacherRepository:
  def findByUserIdAndCourseId(userId: UUID, courseId: UUID): Option[TeacherRecord]

final class InMemoryTeacherRepository(seed: List[TeacherRecord]) extends TeacherRepository:
  def findByUserIdAndCourseId(userId: UUID, courseId: UUID): Option[TeacherRecord] =
    seed.find(row => row.userId == userId && row.courseId == courseId)

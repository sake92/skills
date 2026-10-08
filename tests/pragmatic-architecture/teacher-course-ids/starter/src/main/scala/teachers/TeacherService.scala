package teachers

import java.util.UUID
import scala.collection.mutable

final class TeacherService(repository: TeacherRepository) {
  private val cache = mutable.Map.empty[(UUID, UUID), TeacherRecord]

  def findByUserIdAndCourseId(userId: UUID, courseId: UUID): Option[TeacherRecord] =
    val key = (userId, courseId)
    cache.get(key).orElse {
      val found = repository.findByUserIdAndCourseId(courseId, userId)
      found.foreach(record => cache.put(key, record))
      found
    }
}

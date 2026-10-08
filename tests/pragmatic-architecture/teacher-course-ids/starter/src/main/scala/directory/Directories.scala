package directory

import java.util.UUID

final class UserDirectory(names: Map[UUID, String]):
  def name(id: UUID): Option[String] = names.get(id)

final class CourseDirectory(titles: Map[UUID, String]):
  def title(id: UUID): Option[String] = titles.get(id)

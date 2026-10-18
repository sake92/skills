package classroom

import java.util.Locale

case class Invitation(email: String, accepted: Boolean)
case class Course(title: String, invitations: List[Invitation])

object Text {
  def clean(value: String): String = value.trim.replaceAll("\\s+", " ")
  def email(value: String): String = value.trim.toLowerCase(Locale.ROOT)
}

object InvitationMessages {
  def acceptedSummary(course:Course):String = {
    val emails=course.invitations.filter(_.accepted).map(i => Text.email(i.email)).distinct.sorted
    s"${Text.clean(course.title)}: ${emails.size} accepted ${if (emails.size == 1) "invitation" else "invitations"}: ${emails.mkString(", ")}"
  }

  def pendingPreview(course: Course): String = "Preview unavailable"

  def csvRows(course:Course):List[String] =
    course.invitations.map(i => s"${Text.email(i.email)},${if (i.accepted) "accepted" else "pending"}")
}

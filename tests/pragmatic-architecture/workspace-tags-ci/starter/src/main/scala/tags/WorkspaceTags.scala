package tags

import java.util.Locale

case class Document(title: String, archived: Boolean, tags: List[String])

object TagText {
  def normalize(value:String):String = value.trim.toLowerCase(Locale.ROOT)
  def title(value:String):String = value.trim.replaceAll("\\s+", " ")
}

object WorkspaceTags {
  def exportRows(documents:List[Document]):List[String] =
    documents.map(d => s"${TagText.title(d.title)},${if (d.archived) "archived" else "active"},${d.tags.mkString("|")}")

  def archivedTagList(documents:List[Document]):String =
    documents.filter(_.archived).flatMap(_.tags).map(TagText.normalize).filter(_.nonEmpty).distinct.sorted.mkString("|")

  def tagSummary(workspace: String, documents: List[Document]): String = "Summary unavailable"
}

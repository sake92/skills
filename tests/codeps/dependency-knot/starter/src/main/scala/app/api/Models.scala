package app.api

case class CustomerView(id: String, name: String, region: String, active: Boolean)
case class CustomerInput(id: String, name: String, region: String)
case class HttpResult(status: Int, customers: List[CustomerView], message: Option[String] = None)

object ResponsePolicy:
  def cleanRegion(value: String): String = value.trim.toUpperCase(java.util.Locale.ROOT)
  def activeLabel(active: Boolean): String = if active then "active" else "inactive"

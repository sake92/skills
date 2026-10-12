package app.dao

import app.api.{CustomerView, ResponsePolicy}

final class CustomerDao(table: CustomerTable):
  def find(id: String): Option[CustomerView] = table.get(id).map(toView)
  def active(region: String): List[CustomerView] =
    val code = ResponsePolicy.cleanRegion(region)
    table.all.filter(row => row.enabled && row.regionCode == code).map(toView)
  def insert(customer: CustomerView): CustomerView =
    val stored =
      CustomerRow(customer.id, customer.name, ResponsePolicy.cleanRegion(customer.region), customer.active, 1)
    table.put(stored)
    toView(stored)
  def activity(id: String): Option[String] = table.get(id).map(row => ResponsePolicy.activeLabel(row.enabled))
  private def toView(row: CustomerRow): CustomerView =
    CustomerView(row.id, row.displayName, row.regionCode, row.enabled)

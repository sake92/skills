package app.dao

import scala.collection.mutable.LinkedHashMap

final class CustomerTable(initial: List[CustomerRow]):
  private val rows = LinkedHashMap.from(initial.map(row => row.id -> row))
  def get(id: String): Option[CustomerRow] = rows.get(id)
  def all: List[CustomerRow] = rows.values.toList
  def put(row: CustomerRow): Unit =
    rows.update(row.id, row); ()

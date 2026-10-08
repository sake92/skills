package repositories

import models.Invoice

trait InvoiceRepository:
  def find(id: String): Option[Invoice]

final class InMemoryInvoiceRepository(invoices: List[Invoice]) extends InvoiceRepository {
  private val byId = invoices.map(invoice => invoice.id -> invoice).toMap

  def find(id: String): Option[Invoice] = byId.get(id)
}

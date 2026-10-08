package controllers

import javax.inject.Inject
import services.DiscountService

final case class InvoiceResponse(status: Int, body: String)

final class InvoiceController @Inject() (service: DiscountService):
  def get(id: String): InvoiceResponse =
    service.totalFor(id) match
      case Some(total) => InvoiceResponse(200, total.bigDecimal.toPlainString)
      case None        => InvoiceResponse(404, "invoice not found")

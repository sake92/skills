package services

import javax.inject.Inject
import repositories.InvoiceRepository
import scala.math.BigDecimal.RoundingMode

final class DiscountService @Inject() (repository: InvoiceRepository):
  def totalFor(id: String): Option[BigDecimal] =
    repository.find(id).map { invoice =>
      require(invoice.subtotal >= 0, "subtotal must be non-negative")
      require(invoice.discountPercent >= 0 && invoice.discountPercent <= 100, "discount must be between 0 and 100")
      val discount = (invoice.subtotal * invoice.discountPercent / 100).setScale(2, RoundingMode.HALF_UP)
      (invoice.subtotal - discount).setScale(2, RoundingMode.HALF_UP)
    }

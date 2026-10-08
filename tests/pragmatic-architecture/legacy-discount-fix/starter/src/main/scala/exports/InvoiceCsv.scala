package exports

import javax.inject.Inject
import services.DiscountService

final class InvoiceCsv @Inject() (service: DiscountService):
  def row(id: String): Option[String] =
    service.totalFor(id).map(total => s"$id,${total.bigDecimal.toPlainString}")

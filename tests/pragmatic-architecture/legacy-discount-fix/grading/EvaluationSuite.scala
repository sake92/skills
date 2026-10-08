package grading

import controllers.*
import exports.InvoiceCsv
import legacy.DiscountPreview
import models.Invoice
import repositories.InMemoryInvoiceRepository
import services.DiscountService
import evaluation.RecordedSuite

class EvaluationSuite extends RecordedSuite {
  private def service(subtotal: String, percent: String): DiscountService =
    new DiscountService(
      new InMemoryInvoiceRepository(List(Invoice("inv-1", BigDecimal(subtotal), BigDecimal(percent))))
    )

  expectation("Fractional-cent discounts round the final total once with HALF_UP.") {
    List(
      ("10.05", "10", "9.05"),
      ("0.05", "10", "0.05"),
      ("1.15", "10", "1.04"),
      ("10.15", "10", "9.14"),
      ("10.05", "50", "5.03")
    ).foreach { (subtotal, percent, expected) =>
      val total = service(subtotal, percent).totalFor("inv-1").get
      assertEquals(total, BigDecimal(expected), s"$subtotal at $percent percent")
      assertEquals(total.scale, 2)
    }
  }

  expectation("Zero, full, fractional, and large discounts retain decimal precision and two-place totals.") {
    List(
      ("20.00", "10", "18.00"),
      ("0", "10", "0.00"),
      ("0.015", "0", "0.02"),
      ("10.05", "100", "0.00"),
      ("1.2345", "12.5", "1.08"),
      ("123456789012345.05", "10", "111111110111110.55")
    ).foreach { (subtotal, percent, expected) =>
      val total = service(subtotal, percent).totalFor("inv-1").get
      assertEquals(total, BigDecimal(expected), s"$subtotal at $percent percent")
      assertEquals(total.scale, 2)
    }
  }

  expectation("The existing controller and CSV export both return the corrected total in their established formats.") {
    val discounts = service("10.05", "10")
    assertEquals(new InvoiceController(discounts).get("inv-1"), InvoiceResponse(200, "9.05"))
    assertEquals(new InvoiceCsv(discounts).row("inv-1"), Some("inv-1,9.05"))
    val free = service("10.05", "100")
    assertEquals(new InvoiceController(free).get("inv-1"), InvoiceResponse(200, "0.00"))
    assertEquals(new InvoiceCsv(free).row("inv-1"), Some("inv-1,0.00"))
  }

  expectation("Missing invoice handling and invalid monetary input rejection remain compatible.") {
    val discounts = service("10.05", "10")
    assertEquals(discounts.totalFor("missing"), None)
    assertEquals(new InvoiceController(discounts).get("missing"), InvoiceResponse(404, "invoice not found"))
    assertEquals(new InvoiceCsv(discounts).row("missing"), None)
    List(("-1", "10"), ("10", "-0.1"), ("10", "100.1")).foreach { (subtotal, percent) =>
      intercept[IllegalArgumentException](service(subtotal, percent).totalFor("inv-1"))
    }
  }

  expectation("The independent legacy percentage preview remains compatible.") {
    assertEquals(DiscountPreview.percentageLabel(12.5), "12.5%")
    assertEquals(DiscountPreview.percentageLabel(0), "0.0%")
  }
}

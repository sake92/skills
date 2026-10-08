import controllers.*
import exports.InvoiceCsv
import models.Invoice
import repositories.InMemoryInvoiceRepository
import services.DiscountService

class InvoiceSuite extends munit.FunSuite {
  private def service(invoices: Invoice*) =
    new DiscountService(new InMemoryInvoiceRepository(invoices.toList))

  test("existing whole-cent discount") {
    val discounts = service(Invoice("inv-1", BigDecimal("20.00"), BigDecimal("10")))
    assertEquals(discounts.totalFor("inv-1"), Some(BigDecimal("18.00")))
    assertEquals(new InvoiceController(discounts).get("inv-1"), InvoiceResponse(200, "18.00"))
    assertEquals(new InvoiceCsv(discounts).row("inv-1"), Some("inv-1,18.00"))
  }

  test("missing invoice") {
    val discounts = service()
    assertEquals(discounts.totalFor("missing"), None)
    assertEquals(new InvoiceController(discounts).get("missing"), InvoiceResponse(404, "invoice not found"))
    assertEquals(new InvoiceCsv(discounts).row("missing"), None)
  }

  test("existing invalid invoice rejection") {
    intercept[IllegalArgumentException] {
      service(Invoice("bad", BigDecimal("-1"), BigDecimal("10"))).totalFor("bad")
    }
    intercept[IllegalArgumentException] {
      service(Invoice("bad", BigDecimal("20"), BigDecimal("101"))).totalFor("bad")
    }
  }
}

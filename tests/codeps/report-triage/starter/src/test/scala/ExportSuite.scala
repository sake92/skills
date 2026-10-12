import app.ExportApplication
import app.domain.Customer
import java.nio.file.Files

class ExportSuite extends munit.FunSuite {
  private val config = Files.createTempFile("exporters-", ".properties")
  Files.writeString(config, "csv=app.plugins.CsvExporter\naudit=app.plugins.AuditExporter\n")

  override def afterAll(): Unit =
    Files.deleteIfExists(config); ()

  test("configured CSV export") {
    val app = new ExportApplication(List(Customer("a", "Ada", "BA")), config)
    assertEquals(app.controller.download("BA", "csv"), "a,Ada,BA")
  }
  test("nightly job uses configured audit exporter") {
    val app = new ExportApplication(List(Customer("a", "Ada", "BA")), config)
    assertEquals(app.nightly.rows("BA"), List("AUDIT:a|Ada|BA"))
  }
}

import app.DirectoryApplication
import app.dao.CustomerRow

class DirectorySuite extends munit.FunSuite:
  test("active directory preserves input order and skips inactive customers") {
    val app =
      new DirectoryApplication(List(CustomerRow("a", "Ada", "BA", true, 3), CustomerRow("b", "Ben", "BA", false, 2)))
    assertEquals(app.customers.list("BA").customers.map(_.id), List("a"))
  }
  test("existing consumers can export and reconcile") {
    val app = new DirectoryApplication(List(CustomerRow("a", "Ada", "BA", true, 3)))
    assertEquals(app.exports.download("BA"), "id,name,region,status\na,Ada,BA,active")
    assertEquals(app.reconciliation.status("a"), Some("active"))
  }

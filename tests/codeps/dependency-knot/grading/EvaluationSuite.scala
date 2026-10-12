package evaluation

import app.DirectoryApplication
import app.api.CustomerInput
import app.dao.CustomerRow
import ba.sake.tupson.*
import scala.concurrent.duration.*

class EvaluationSuite extends RecordedSuite {
  override val munitTimeout = 180.seconds

  private def application = new DirectoryApplication(
    List(
      CustomerRow("a", "Ada", "BA", true, 7),
      CustomerRow("b", "Ben", "BA", false, 2),
      CustomerRow("c", "Cam", "HR", true, 4),
      CustomerRow("d", "Dina", "BA", true, 1)
    )
  )

  expectation("Directory endpoints retain active ordering, normalization, lookup and missing-customer behavior.") {
    val app = application
    assertEquals(app.customers.list(" ba ").customers.map(_.id), List("a", "d"))
    assertEquals(app.customers.get("b").customers.map(_.active), List(false))
    assertEquals(app.customers.get("missing").status, 404)
    assertEquals(app.customers.get("missing").message, Some("Customer not found"))
  }
  expectation("Creation preserves validation, duplicate rejection, normalized output and subsequent lookup.") {
    val app = application
    assertEquals(app.customers.create(CustomerInput("a", "Again", "HR")).message, Some("Customer already exists"))
    assertEquals(app.customers.create(CustomerInput(" ", "N", "BA")).status, 400)
    val added = app.customers.create(CustomerInput("n", " New ", " hr "))
    assertEquals(added.status, 201)
    assertEquals(added.customers.map(c => (c.id, c.name, c.region, c.active)), List(("n", "New", "HR", true)))
    assertEquals(app.customers.get("n").customers, added.customers)
  }
  expectation("CRM synchronization preserves ordered partial results and shares directory state.") {
    val app = application
    assertEquals(
      app.sync.importRows(List(("x", " X ", "ba"), ("a", "Again", "BA"), ("y", "Y", "BA"))),
      List(Right("x"), Left("Customer already exists"), Right("y"))
    )
    assertEquals(app.customers.list("BA").customers.map(_.id), List("a", "d", "x", "y"))
  }
  expectation("CSV export and reconciliation retain their independent consumer contracts.") {
    val app = application
    assertEquals(app.exports.download(" ba "), "id,name,region,status\na,Ada,BA,active\nd,Dina,BA,active")
    assertEquals(app.exports.download("ZZ"), "id,name,region,status")
    assertEquals(app.reconciliation.status("b"), Some("inactive"))
    assertEquals(app.reconciliation.status("missing"), None)
  }
  expectation("A protected clean build has no package or file dependency cycle in application code.") {
    val report = GraphEvidence.fresh
    assertEquals(report.packages.summary.nodesInCycles, 0)
    assertEquals(report.files.get.summary.nodesInCycles, 0)
    assert(report.packages.summary.nodes >= 5, "Application graph was lost rather than repaired")
  }
  expectation("The submitted final dependency report agrees with an independent clean build in both scopes.") {
    val submitted = GraphEvidence.read(GraphEvidence.submission / ".codeps/out/app/report.json")
    assertEquals(submitted.packages, GraphEvidence.fresh.packages)
    assertEquals(submitted.files, GraphEvidence.fresh.files)
  }
  expectation("Before and after evidence keeps the application scope and labels the analyzed source state correctly.") {
    val work = GraphEvidence.submission
    val history = os.read.lines(work / ".codeps/app.ndjson").map(_.parseJson[GraphHistory])
    assert(history.size >= 2, "A before/after history is required to support the claimed change")
    assert(history.head.packages.cycles.nodes >= 4)
    assertEquals(history.last.packages.cycles.count, 0)
    assertEquals(history.last.files.get.cycles.count, 0)
    val head = GraphEvidence.command(work, "git", "rev-parse", "HEAD").trim
    val changedSource = GraphEvidence.command(work, "git", "diff", "HEAD", "--", "src/main").nonEmpty
    assert(!changedSource || history.last.commit != head, "Changed sources were recorded as unchanged HEAD")
    assert(history.last.commit != "starter", "Final evidence retains the starter label")
    assert(os.isFile(work / "architecture.md"))
  }
}

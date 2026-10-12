package evaluation

import app.ExportApplication
import app.domain.Customer
import ba.sake.tupson.*
import scala.concurrent.duration.*

case class Recommendation(subject: String, action: String, evidence: List[String]) derives JsonRW
case class Review(packageCycles: Int, fileCycles: Int, recommendations: List[Recommendation], summary: String)
    derives JsonRW

class EvaluationSuite extends RecordedSuite {
  override val munitTimeout = 180.seconds
  private val work = GraphEvidence.submission
  private lazy val review = os.read(work / "review.json").parseJson[Review]
  private def recommendation(name: String): Recommendation =
    val rows = review.recommendations.filter(_.subject.contains(name))
    assertEquals(rows.size, 1, s"Expected one decision for $name")
    rows.head

  expectation("The review distinguishes the missing retired sources from live refactoring targets.") {
    val decisions = review.recommendations.filter(_.subject.contains("retired"))
    assert(decisions.nonEmpty)
    assert(decisions.forall(_.action == "refresh"), "Missing source requires refreshed evidence, not a source change")
    assert(decisions.forall(_.evidence.exists(_.trim.nonEmpty)))
  }
  expectation("CSV and audit adapters are retained with evidence of their configured runtime loading.") {
    List("CsvExporter", "AuditExporter").foreach { name =>
      val decision = recommendation(name)
      assertEquals(decision.action, "keep")
      val evidence = decision.evidence.mkString(" ").toLowerCase
      assert(evidence.contains("exporters.properties"))
      assert(
        evidence.contains("class.forname") || evidence.contains("exporterregistry") || evidence.contains("reflection")
      )
    }
  }
  expectation("Current package and file cycle counts agree with an independent clean build.") {
    val graph = GraphEvidence.fresh
    assertEquals(review.packageCycles, graph.packages.cycles.size)
    assertEquals(review.fileCycles, graph.files.get.cycles.size)
    assertEquals(review.packageCycles, 0)
    assertEquals(review.fileCycles, 0)
  }
  expectation("The saved current report covers the existing application and agrees with the protected graph.") {
    val report = GraphEvidence.read(work / ".codeps/out/app/report.json")
    assertEquals(report.packages, GraphEvidence.fresh.packages)
    assertEquals(report.files, GraphEvidence.fresh.files)
  }
  expectation("The review does not change production sources or deployment plugin configuration.") {
    assertEquals(GraphEvidence.command(work, "git", "diff", "HEAD", "--", "src/main", "conf"), "")
    assert(review.summary.trim.nonEmpty)
  }
  expectation("Configured adapters still load in HTTP and nightly consumers after the review.") {
    val app = new ExportApplication(
      List(Customer("a", "Ada", "BA"), Customer("b", "Ben", "HR")),
      (work / "conf/exporters.properties").toNIO
    )
    assertEquals(app.controller.download("BA", "csv"), "a,Ada,BA")
    assertEquals(app.nightly.rows("HR"), List("AUDIT:b|Ben|HR"))
  }
}

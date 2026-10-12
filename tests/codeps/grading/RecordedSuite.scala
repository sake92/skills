package evaluation

import ba.sake.tupson.*
import java.nio.file.{Files, Path}
import scala.collection.concurrent.TrieMap
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success}

case class ExpectationResult(text: String, passed: Boolean, evidence: String) derives JsonRW
case class GradeSummary(passed: Int, failed: Int, total: Int, pass_rate: Double) derives JsonRW
case class GradingReport(expectations: List[ExpectationResult], summary: GradeSummary) derives JsonRW

abstract class RecordedSuite extends munit.FunSuite {
  private val expectationNames = ListBuffer.empty[String]
  private val results = TrieMap.empty[String, ExpectationResult]

  protected def expectation(text: String)(body: => Any): Unit =
    expectationNames += text
    test(text)(body)

  override def munitTestTransforms: List[TestTransform] =
    val record = new TestTransform(
      "record",
      test =>
        if !expectationNames.contains(test.name) then test
        else
          test.withBodyMap(
            _.transform {
              case result @ Success(_) =>
                results.put(test.name, ExpectationResult(test.name, passed = true, s"MUnit passed: ${test.name}"))
                result
              case result @ Failure(error) =>
                val meaningful = Iterator
                  .iterate(Option(error))(_.flatMap(current => Option(current.getCause)))
                  .takeWhile(_.nonEmpty)
                  .flatten
                  .toList
                  .lastOption
                  .getOrElse(error)
                val detail =
                  Option(meaningful.getMessage).filter(_.nonEmpty).getOrElse(meaningful.getClass.getSimpleName)
                results.put(test.name, ExpectationResult(test.name, passed = false, detail))
                result
            }(using munitExecutionContext)
          )
    )
    record :: super.munitTestTransforms

  override def afterAll(): Unit =
    super.afterAll()
    sys.env
      .get("GRADING_JSON")
      .foreach: output =>
        val expectations = expectationNames.distinct.toList.map: name =>
          results.getOrElse(name, ExpectationResult(name, passed = false, "MUnit did not complete this check"))
        val passed = expectations.count(_.passed)
        val summary =
          GradeSummary(passed, expectations.size - passed, expectations.size, passed.toDouble / expectations.size)
        Files.writeString(Path.of(output), GradingReport(expectations, summary).toJson(sort = false))
}

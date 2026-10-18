package evaluation

/** Classifies actual framework output; startup/compilation errors are never mutation evidence. */
object TestOutput {
  private def plain(log: String): String = log.replaceAll("\u001b\\[[0-9;]*m", "")

  def hasTests(raw: String): Boolean =
    val log = plain(raw)
    "(?s).*[, ]([1-9][0-9]*) total.*".r.matches(log) ||
      "(?s).*Total number of tests run: [1-9][0-9]*.*".r.matches(log) ||
      log.linesIterator.exists(_.trim.startsWith("+ "))

  def frameworkOptions(sources: List[String]): Seq[String] =
    val scalaTest = sources.exists(_.contains("org.scalatest"))
    val munit = sources.exists(s => s.contains("munit.") || s.contains("::munit:"))
    require(!(scalaTest && munit), "Mixed-framework replay adapter needs separate validation; do not interpret a score")
    if scalaTest then Seq("--", "-oF") else Nil

  def isBehaviorFailure(exit: Int, raw: String): Boolean =
    val log = plain(raw)
    val munitFailures = log.linesIterator.filter(_.startsWith("==> X ")).toList
    val startup = munitFailures.exists(h =>
      h.contains("initializationError") || h.contains("beforeAll") || h.contains("beforeEach")) ||
      log.contains("*** ABORTED ***")
    val bodyFailure = munitFailures.nonEmpty || log.contains("*** FAILED ***")
    val assertion = log.contains("munit.ComparisonFailException") || log.contains("munit.FailException") ||
      log.contains("org.scalatest.exceptions.TestFailedException")
    // An SDK lookup of an expected stored object is an independent behavioral observation.
    // NoSuchKey is a completed S3 response, unlike connection, Docker or compiler failures.
    val missingExpectedObject = log.contains("software.amazon.awssdk.services.s3.model.NoSuchKeyException") &&
      log.contains("$init$$$anonfun$")
    exit != 0 && !log.contains("Compilation failed") && !startup && bodyFailure &&
      (assertion || missingExpectedObject)
}

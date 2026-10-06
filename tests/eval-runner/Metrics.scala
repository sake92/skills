import ba.sake.tupson.*
import scala.util.Try

def parseEvents(path: os.Path): List[CodexEvent] =
  if !os.isFile(path) then Nil
  else os.read.lines(path).flatMap(line => Try(line.parseJson[CodexEvent]).toOption).toList

def usage(events: List[CodexEvent]): Usage =
  events.flatMap(_.usage).foldLeft(Usage()): (total, next) =>
    Usage(
      total.input_tokens + next.input_tokens,
      total.cached_input_tokens + next.cached_input_tokens,
      total.output_tokens + next.output_tokens,
      total.reasoning_output_tokens + next.reasoning_output_tokens
    )

def metrics(work: os.Path, outputs: os.Path, events: List[CodexEvent], skillName: Option[String]): Metrics =
  val completed = events.filter(_.`type` == "item.completed")
  val toolTypes = completed.flatMap(_.item.map(_.`type`)).filterNot(Set("agent_message", "reasoning"))
  val patch = outputs / "submission.patch"
  val finalMessage = outputs / "final-message.txt"
  val transcript = outputs / "transcript.jsonl"
  val transcriptText = if os.isFile(transcript) then os.read(transcript) else ""
  val status = commandOutput(Seq("git", "status", "--porcelain"), work).linesIterator.toList
  Metrics(
    tool_calls = toolTypes.groupMapReduce(identity)(_ => 1)(_ + _),
    total_tool_calls = toolTypes.size,
    total_steps = completed.size,
    files_created = status.collect { case line if line.startsWith("?? ") => line.drop(3) },
    errors_encountered = events.count(event => event.`type` == "error" || event.`type` == "turn.failed"),
    output_chars = Seq(patch, finalMessage).filter(os.isFile).map(os.size).sum,
    transcript_chars = transcriptText.length,
    skill_invoked = skillName.map(name => transcriptText.contains(s".codex/skills/$name/SKILL.md"))
  )

def judgeExpectations(rubric: String): List[String] =
  rubric.linesIterator.collect:
    case line if line.startsWith("- `") && line.endsWith("`") => line.stripPrefix("- `").stripSuffix("`")
  .toList

def summary(expectations: List[ExpectationResult]): GradeSummary =
  val passed = expectations.count(_.passed)
  GradeSummary(passed, expectations.size - passed, expectations.size, if expectations.isEmpty then 0.0 else passed.toDouble / expectations.size)

def stats(values: List[Double]): MetricStats =
  val mean = values.sum / values.size
  val variance = values.map(value => math.pow(value - mean, 2)).sum / values.size
  MetricStats(mean, math.sqrt(variance), values.min, values.max)

def signed(value: Double, decimals: Int): String =
  val format = s"%.${decimals}f"
  (if value >= 0 then "+" else "") + String.format(java.util.Locale.ROOT, format, Double.box(value))

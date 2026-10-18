import ba.sake.tupson.*
import scala.util.Try

def parseEvents(path: os.Path): List[PiEvent] =
  if !os.isFile(path) then Nil
  else
    os.read
      .lines(path)
      .flatMap(line => Try(line.parseJson[PiEvent]).toOption)
      .toList

def usage(events: List[PiEvent]): Usage =
  events
    .filter(_.`type` == "message_end")
    .flatMap(_.message)
    .filter(_.role == "assistant")
    .flatMap(_.usage)
    .foldLeft(Usage()): (total, next) =>
      Usage(
        total.input_tokens + next.input,
        total.cached_input_tokens + next.cacheRead,
        total.output_tokens + next.output,
        total.reasoning_output_tokens + next.reasoning
      )

def finalAssistantText(events: List[PiEvent]): String =
  events
    .filter(_.`type` == "message_end")
    .flatMap(_.message)
    .filter(_.role == "assistant")
    .flatMap(_.content.collect { case content if content.`type` == "text" => content.text.getOrElse("") })
    .lastOption
    .getOrElse("")

def parseJudgeReport(text: String): JudgeReport =
  val start = text.indexOf('{')
  val end = text.lastIndexOf('}')
  require(start >= 0 && end >= start, "Judge did not return a JSON object")
  text.substring(start, end + 1).parseJson[JudgeReport]

def terminalAssistantError(events: List[PiEvent]): Option[String] =
  events.filter(_.`type` == "message_end").flatMap(_.message)
    .filter(_.role == "assistant").lastOption
    .filter(_.stopReason.contains("error"))
    .map(_.errorMessage.getOrElse("Assistant stopped with an unspecified provider error"))

def metrics(
    work: os.Path,
    outputs: os.Path,
    events: List[PiEvent],
    skillName: Option[String]
): Metrics =
  val completedTools = events.filter(_.`type` == "tool_execution_end")
  val toolTypes = completedTools.flatMap(_.toolName)
  val patch = outputs / "submission.patch"
  val finalMessage = outputs / "final-message.txt"
  val transcript = outputs / "transcript.jsonl"
  val transcriptText = if os.isFile(transcript) then os.read(transcript) else ""
  val status = commandOutput(
    Seq("git", "status", "--porcelain"),
    work
  ).linesIterator.toList
  Metrics(
    tool_calls = toolTypes.groupMapReduce(identity)(_ => 1)(_ + _),
    total_tool_calls = toolTypes.size,
    total_steps = events.count(event => event.`type` == "message_end" || event.`type` == "tool_execution_end"),
    files_created = status.collect:
      case line if line.startsWith("?? ") || line.startsWith(" A ") => line.drop(3),
    errors_encountered = completedTools.count(_.isError.contains(true)) + events.count(event =>
      event.`type` == "message_end" && event.message.flatMap(_.stopReason).contains("error")
    ),
    output_chars = Seq(patch, finalMessage).filter(os.isFile).map(os.size).sum,
    transcript_chars = transcriptText.length,
    skill_invoked = skillName.map(name => skillWasRead(events, name))
  )

private def skillWasRead(events: List[PiEvent], name: String): Boolean =
  val skillPath = s"skills/$name/SKILL.md"
  val starts = events.filter(_.`type` == "tool_execution_start")
  val identifiedStarts = starts.flatMap(event => event.toolCallId.map(id => id -> event))
  val byId = identifiedStarts.toMap
  val successful = events.filter(event =>
    event.`type` == "tool_execution_end" && event.isError.contains(false)
  )
  successful.exists: completed =>
    val started = completed.toolCallId.flatMap(byId.get)
    val requested = started.exists: event =>
      if event.toolName.contains("read") then event.args.flatMap(_.path).exists(_.contains(skillPath))
      else if event.toolName.contains("bash") then event.args.flatMap(_.command).exists(_.contains(skillPath))
      else false
    val content = completed.result.toList.flatMap(_.content)
    val textBlocks = content.flatMap(_.text)
    val text = textBlocks.mkString("\n").replace("\r\n", "\n")
    val sections = text.split("(?m)^---[ \\t]*$", 3)
    val document = sections.length == 3 &&
      sections(1).linesIterator.exists(_.trim == s"name: $name") && sections(2).trim.nonEmpty
    requested && document

def judgeExpectations(rubric: String): List[String] =
  rubric.linesIterator
    .collect:
      case line if line.startsWith("- `") && line.endsWith("`") =>
        line.stripPrefix("- `").stripSuffix("`")
    .toList

def summary(expectations: List[ExpectationResult]): GradeSummary =
  val passed = expectations.count(_.passed)
  GradeSummary(
    passed,
    expectations.size - passed,
    expectations.size,
    if expectations.isEmpty then 0.0 else passed.toDouble / expectations.size
  )

def stats(values: List[Double]): MetricStats =
  val mean = values.sum / values.size
  val variance =
    values.map(value => math.pow(value - mean, 2)).sum / values.size
  MetricStats(mean, math.sqrt(variance), values.min, values.max)

def signed(value: Double, decimals: Int): String =
  val format = s"%.${decimals}f"
  (if value >= 0 then "+" else "") + String.format(
    java.util.Locale.ROOT,
    format,
    Double.box(value)
  )

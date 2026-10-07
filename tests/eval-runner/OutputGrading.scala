import ba.sake.tupson.*
import java.time.Instant

def gradeOutputCandidate(environment: OutputEnvironment, config: OutputConfig)(candidate: Candidate): Graded = {
  val context = candidate.context
  val graderStarted = Instant.now
  val graderStartNanos = System.nanoTime()
  val rubric = os.read(environment.evalRoot / context.eval.name / "grading/judge.md")
  val qualitative = judgeExpectations(rubric)
  val deterministicTexts = context.eval.expectations.filterNot(qualitative.toSet)
  val deterministicPath = context.runDir / "deterministic-grading.json"
  val deterministic = runProcess(
    s"${context.eval.name} ${context.configuration} deterministic grade ${context.runNumber}",
    Seq(
      "scala-cli",
      "test",
      (environment.evalRoot / "grading").toString,
      (environment.evalRoot / context.eval.name / "grading").toString,
      context.work.toString,
      "--server=false"
    ),
    environment.root,
    context.runDir / "grading.log",
    env = Map(
      "SUBMISSION_DIR" -> context.work.toString,
      "GRADING_JSON" -> deterministicPath.toString
    ),
    timeoutMs = environment.timeoutMs
  )
  val deterministicReport =
    if os.isFile(deterministicPath) then os.read(deterministicPath).parseJson[GradingReport]
    else failedDeterministicReport(deterministicTexts, deterministic.exit_code)
  val deterministicByText = deterministicReport.expectations.map(result => result.text -> result).toMap
  require(
    deterministicByText.keySet == deterministicTexts.toSet,
    s"${context.eval.name} deterministic grader returned unexpected expectations"
  )

  val judged =
    if config.runJudge then qualitativeJudge(environment, config, context, rubric, qualitative)
    else Nil
  val allByText = (deterministicReport.expectations ++ judged).map(result => result.text -> result).toMap
  val included = if config.runJudge then context.eval.expectations else deterministicTexts
  val ordered = included.map(allByText)
  val report = GradingReport(ordered, summary(ordered))
  writeJson(context.runDir / "grading.json", report)

  val graderDurationMs = (System.nanoTime() - graderStartNanos) / 1_000_000
  val totalDurationMs = candidate.process.duration_ms + graderDurationMs
  val timing = Timing(
    candidate.usage.input_tokens + candidate.usage.output_tokens,
    candidate.process.duration_ms,
    totalDurationMs / 1000.0,
    candidate.process.started_at,
    candidate.process.finished_at,
    candidate.process.duration_ms / 1000.0,
    Some(graderStarted.toString),
    Some(Instant.now.toString),
    Some(graderDurationMs / 1000.0)
  )
  writeJson(context.runDir / "timing.json", timing)
  Graded(candidate, report, timing)
}

private def failedDeterministicReport(expectations: List[String], exitCode: Int): GradingReport =
  GradingReport(
    expectations.map(text =>
      ExpectationResult(
        text,
        passed = false,
        s"Protected MUnit grading exited $exitCode; see grading.log"
      )
    ),
    GradeSummary(0, expectations.size, expectations.size, 0.0)
  )

private def qualitativeJudge(
    environment: OutputEnvironment,
    config: OutputConfig,
    context: RunContext,
    rubric: String,
    qualitative: List[String]
): List[ExpectationResult] =
  val judgeWork = context.runDir / "judge-work"
  copyTree(context.work, judgeWork, Set(".git", ".scala-build", ".bsp"))
  val submittedSource = os
    .walk(judgeWork)
    .filter(path => os.isFile(path) && (path.ext == "scala" || path.last == "project.scala"))
    .sortBy(_.toString)
    .map: path =>
      s"### ${path.relativeTo(judgeWork)}\n\n```scala\n${os.read(path)}\n```"
    .mkString("\n\n")
  val schema = os.read(environment.evalRoot / "judge-output-schema.json")
  val prompt = s"$rubric\n\n## Required JSON schema\n\n$schema\n\n## Submitted source\n\n$submittedSource"
  val judgeOutput = context.runDir / "judge-output.json"
  val judgeTranscript = context.runDir / "judge-transcript.jsonl"
  val process = runProcess(
    s"${context.eval.name} ${context.configuration} qualitative judge ${context.runNumber}",
    piCommand(
      PiConfig(environment.judgeModel, config.reasoning),
      prompt,
      skill = None,
      tools = None,
      systemPrompt = Some("Return only valid JSON matching the supplied schema.")
    ),
    judgeWork,
    judgeTranscript,
    stderr = Some(context.runDir / "pi-judge.stderr.log"),
    stdin = Some(""),
    timeoutMs = environment.timeoutMs
  )
  checked(process, s"${context.eval.name} judge")
  val report = parseJudgeReport(finalAssistantText(parseEvents(judgeTranscript)))
  writeJson(judgeOutput, report)
  require(
    report.expectations.map(_.text).toSet == qualitative.toSet,
    s"${context.eval.name} judge returned unexpected expectations"
  )
  require(
    report.expectations.size == qualitative.size,
    s"${context.eval.name} judge returned duplicate expectations"
  )
  report.expectations

import ba.sake.tupson.*
import java.time.Instant
import java.time.format.DateTimeFormatter
import ox.mapPar

def runOutputEval(config: OutputConfig): Unit = {
  val root = os.pwd
  val evalRoot = root / "tests/pragmatic-architecture"
  val skillRoot = root / "skills/pragmatic-architecture"
  val evalSet = os.read(skillRoot / "evals/evals.json").parseJson[EvalSet]
  val judgeModel = config.judgeModel.getOrElse(config.model)
  require(config.runs > 0, "runs must be positive")
  require(config.timeoutMinutes > 0, "timeout-minutes must be positive")
  require(config.parallelism > 0, "parallelism must be positive")
  val evals = config.caseName match
    case None       => evalSet.evals
    case Some(name) => evalSet.evals.filter(_.name == name)
  require(
    evals.nonEmpty,
    s"No eval matched ${config.caseName.getOrElse("the eval set")}"
  )
  evals.foreach: eval =>
    require(eval.files.nonEmpty, s"${eval.name} has no input files")
    require(
      os.isDir(root / os.RelPath(eval.files.head)),
      s"Missing starter for ${eval.name}: ${eval.files.head}"
    )
    require(
      os.isFile(evalRoot / eval.name / "grading/judge.md"),
      s"Missing judge rubric for ${eval.name}"
    )

  val timestamp = DateTimeFormatter
    .ofPattern("yyyyMMdd-HHmmss")
    .withZone(java.time.ZoneOffset.UTC)
    .format(Instant.now)
  val iterationRoot =
    root / "tests/tmp/pragmatic-architecture-workspace" / s"iteration-$timestamp"
  val configurations = config.configuration match
    case None | Some("both")   => List("with_skill", "without_skill")
    case Some("with-skill")    => List("with_skill")
    case Some("without-skill") => List("without_skill")
    case Some(value)           =>
      throw IllegalArgumentException(
        s"configuration must be with-skill, without-skill, or both; got $value"
      )
  val timeoutMs = config.timeoutMinutes.toLong * 60_000

  def runDirectory(
      caseOutput: os.Path,
      configuration: String,
      runNumber: Int
  ): os.Path =
    val base = caseOutput / configuration
    if config.runs == 1 then base else base / s"run-$runNumber"

  val contexts = for
    eval <- evals
    configuration <- configurations
    runNumber <- 1 to config.runs
  yield
    val caseOutput = iterationRoot / s"eval-${eval.id}"
    writeJson(
      caseOutput / "eval_metadata.json",
      EvalMetadata(eval.id, eval.name, eval.prompt, eval.expectations)
    )
    val runDir = runDirectory(caseOutput, configuration, runNumber)
    val work = runDir / "work"
    copyTree(
      root / os.RelPath(eval.files.head),
      work,
      Set(".bsp", ".scala-build")
    )
    List(
      "init" -> Seq("git", "init", "--quiet"),
      "add" -> Seq("git", "add", "."),
      "commit" -> Seq(
        "git",
        "-c",
        "user.name=skill-eval",
        "-c",
        "user.email=skill-eval@example.invalid",
        "commit",
        "--quiet",
        "-m",
        "starter"
      )
    ).foreach: (step, command) =>
      checked(
        runProcess(
          s"${eval.name} $configuration setup $step",
          command,
          work,
          runDir / s"setup-$step.log",
          timeoutMs = timeoutMs
        ),
        step
      )
    RunContext(eval, configuration, runNumber, runDir, work)

  def candidate(context: RunContext): Candidate =
    val outputs = context.runDir / "outputs"
    os.makeDir.all(outputs)
    val transcript = outputs / "transcript.jsonl"
    val candidatePrompt =
      if context.configuration == "with_skill" then s"""Execute this task:
           |- Use the ${evalSet.skill_name} skill provided to you.
           |- Task:
           |
           |${context.eval.prompt}
           |""".stripMargin
      else context.eval.prompt
    val process = runProcess(
      s"${context.eval.name} ${context.configuration} candidate ${context.runNumber}",
      piCommand(
        PiConfig(config.model, config.reasoning),
        candidatePrompt,
        Option.when(context.configuration == "with_skill")(skillRoot),
        Some("read,bash,edit,write")
      ),
      context.work,
      transcript,
      stderr = Some(outputs / "pi.stderr.log"),
      stdin = Some(""),
      timeoutMs = timeoutMs
    )
    val patch = os
      .proc("git", "diff", "--binary", "HEAD")
      .call(cwd = context.work, check = false)
      .out
      .text()
    os.write.over(outputs / "submission.patch", patch)
    val events = parseEvents(transcript)
    os.write.over(outputs / "final-message.txt", finalAssistantText(events))
    val used = usage(events)
    val measured = metrics(
      context.work,
      outputs,
      events,
      Option.when(context.configuration == "with_skill")(evalSet.skill_name)
    )
    writeJson(outputs / "metrics.json", measured)
    writeJson(
      context.runDir / "timing.json",
      Timing(
        used.input_tokens + used.output_tokens,
        process.duration_ms,
        process.duration_ms / 1000.0,
        process.started_at,
        process.finished_at,
        process.duration_ms / 1000.0
      )
    )
    Candidate(context, process, used, measured)

  val candidates = contexts.mapPar(config.parallelism)(candidate)
  val failedCandidates = candidates.filter(_.process.exit_code != 0)
  if failedCandidates.nonEmpty then
    failedCandidates.foreach: failed =>
      System.err.println(
        s"Candidate failed: ${failed.context.eval.name} ${failed.context.configuration} #${failed.context.runNumber}; " +
          s"see ${failed.context.runDir / "outputs/pi.stderr.log"}"
      )
    throw IllegalStateException(
      s"${failedCandidates.size} candidate runs failed; no benchmark was produced"
    )
  val missedSkills = candidates.filter(candidate =>
    candidate.context.configuration == "with_skill" && !candidate.metrics.skill_invoked
      .contains(true)
  )
  if missedSkills.nonEmpty then
    missedSkills.foreach: missed =>
      System.err.println(
        s"Invalid with-skill run: ${missed.context.eval.name} #${missed.context.runNumber} did not read SKILL.md; " +
          s"see ${missed.context.runDir / "outputs/transcript.jsonl"}"
      )
    throw IllegalStateException(
      s"${missedSkills.size} with-skill runs did not load SKILL.md; no benchmark was produced"
    )

  def grade(candidate: Candidate): Graded = {
    val context = candidate.context
    val graderStarted = Instant.now
    val graderStartNanos = System.nanoTime()
    val rubric = os.read(evalRoot / context.eval.name / "grading/judge.md")
    val qualitative = judgeExpectations(rubric)
    val deterministicTexts =
      context.eval.expectations.filterNot(qualitative.toSet)
    val deterministicPath = context.runDir / "deterministic-grading.json"
    val deterministic = runProcess(
      s"${context.eval.name} ${context.configuration} deterministic grade ${context.runNumber}",
      Seq(
        "scala-cli",
        "test",
        (evalRoot / "grading").toString,
        (evalRoot / context.eval.name / "grading").toString,
        context.work.toString,
        "--server=false"
      ),
      root,
      context.runDir / "grading.log",
      env = Map(
        "SUBMISSION_DIR" -> context.work.toString,
        "GRADING_JSON" -> deterministicPath.toString
      ),
      timeoutMs = timeoutMs
    )
    val deterministicReport =
      if os.isFile(deterministicPath) then os.read(deterministicPath).parseJson[GradingReport]
      else
        GradingReport(
          deterministicTexts.map(text =>
            ExpectationResult(
              text,
              passed = false,
              s"Protected MUnit grading exited ${deterministic.exit_code}; see grading.log"
            )
          ),
          GradeSummary(0, deterministicTexts.size, deterministicTexts.size, 0.0)
        )
    val deterministicByText = deterministicReport.expectations
      .map(result => result.text -> result)
      .toMap
    require(
      deterministicByText.keySet == deterministicTexts.toSet,
      s"${context.eval.name} deterministic grader returned unexpected expectations"
    )

    val judged =
      if !config.runJudge then Nil
      else
        val judgeWork = context.runDir / "judge-work"
        copyTree(
          context.work,
          judgeWork,
          Set(".git", ".scala-build", ".bsp")
        )
        val submittedSource = os
          .walk(judgeWork)
          .filter(path =>
            os.isFile(
              path
            ) && (path.ext == "scala" || path.last == "project.scala")
          )
          .sortBy(_.toString)
          .map: path =>
            s"### ${path.relativeTo(judgeWork)}\n\n```scala\n${os.read(path)}\n```"
          .mkString("\n\n")
        val schema = os.read(evalRoot / "judge-output-schema.json")
        val prompt =
          s"$rubric\n\n## Required JSON schema\n\n$schema\n\n## Submitted source\n\n$submittedSource"
        val judgeOutput = context.runDir / "judge-output.json"
        val judgeTranscript = context.runDir / "judge-transcript.jsonl"
        val judgeProcess = runProcess(
          s"${context.eval.name} ${context.configuration} qualitative judge ${context.runNumber}",
          piCommand(
            PiConfig(judgeModel, config.reasoning),
            prompt,
            skill = None,
            tools = None,
            systemPrompt = Some("Return only valid JSON matching the supplied schema.")
          ),
          judgeWork,
          judgeTranscript,
          stderr = Some(context.runDir / "pi-judge.stderr.log"),
          stdin = Some(""),
          timeoutMs = timeoutMs
        )
        checked(judgeProcess, s"${context.eval.name} judge")
        val report =
          parseJudgeReport(finalAssistantText(parseEvents(judgeTranscript)))
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

    val allByText = (deterministicReport.expectations ++ judged)
      .map(result => result.text -> result)
      .toMap
    val included =
      if config.runJudge then context.eval.expectations else deterministicTexts
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

  val graded = candidates.mapPar(config.parallelism)(grade)
  val benchmarkRuns = graded.map: run =>
    val report = run.report
    BenchmarkRun(
      run.candidate.context.eval.id,
      run.candidate.context.eval.name,
      run.candidate.context.configuration,
      run.candidate.context.runNumber,
      BenchmarkResult(
        report.summary.pass_rate,
        report.summary.passed,
        report.summary.failed,
        report.summary.total,
        run.timing.executor_duration_seconds,
        run.timing.total_tokens,
        run.candidate.metrics.total_tool_calls,
        run.candidate.metrics.errors_encountered
      ),
      report.expectations,
      Nil
    )

  val grouped = benchmarkRuns.groupBy(_.configuration)
  val summaries = grouped.view
    .mapValues: runs =>
      RunStats(
        stats(runs.map(_.result.pass_rate)),
        stats(runs.map(_.result.time_seconds)),
        stats(runs.map(_.result.tokens.toDouble))
      )
    .toMap
  val delta = for
    withSkill <- summaries.get("with_skill")
    withoutSkill <- summaries.get("without_skill")
  yield BenchmarkDelta(
    signed(withSkill.pass_rate.mean - withoutSkill.pass_rate.mean, 2),
    signed(withSkill.time_seconds.mean - withoutSkill.time_seconds.mean, 1),
    signed(withSkill.tokens.mean - withoutSkill.tokens.mean, 0)
  )

  val notes =
    val paired = for
      expectation <- evals.flatMap(_.expectations)
      withSkill = benchmarkRuns.filter(run =>
        run.configuration == "with_skill" && run.expectations.exists(
          _.text == expectation
        )
      )
      withoutSkill = benchmarkRuns.filter(run =>
        run.configuration == "without_skill" && run.expectations.exists(
          _.text == expectation
        )
      )
      if withSkill.nonEmpty && withoutSkill.nonEmpty
      withResults = withSkill
        .flatMap(_.expectations)
        .filter(_.text == expectation)
      withoutResults = withoutSkill
        .flatMap(_.expectations)
        .filter(_.text == expectation)
      withRate = withResults.count(_.passed).toDouble / withResults.size
      withoutRate = withoutResults
        .count(_.passed)
        .toDouble / withoutResults.size
      rates =
        f"with skill ${withRate * 100}%.0f%%, without skill ${withoutRate * 100}%.0f%%"
      note =
        if withRate == 1.0 && withoutRate == 1.0 then
          s"Expectation passes in every run and may not discriminate ($rates): $expectation"
        else if withRate == 0.0 && withoutRate == 0.0 then
          s"Expectation fails in every run and needs investigation ($rates): $expectation"
        else if withRate > withoutRate then
          s"Expectation favors the skill but is not necessarily stable ($rates): $expectation"
        else if withRate < withoutRate then
          s"Expectation favors the baseline and needs investigation ($rates): $expectation"
        else s"Expectation has equal mixed results and may be flaky ($rates): $expectation"
    yield note
    paired.distinct

  writeJson(iterationRoot / "runs.json", benchmarkRuns)
  delta.foreach: benchmarkDelta =>
    val benchmark = Benchmark(
      BenchmarkMetadata(
        skill_name = evalSet.skill_name,
        skill_path = skillRoot.toString,
        executor_model = config.model,
        reasoning = config.reasoning,
        analyzer_model = Option.when(config.runJudge)(judgeModel),
        timestamp = Instant.now.toString,
        evals_run = evals.map(_.id),
        runs_per_configuration = config.runs,
        git_commit = commandOutput(Seq("git", "rev-parse", "HEAD"), root),
        agent_version = commandOutput(Seq("pi", "--version"), root)
      ),
      benchmarkRuns,
      BenchmarkSummary(
        summaries("with_skill"),
        summaries("without_skill"),
        benchmarkDelta
      ),
      notes
    )
    writeJson(iterationRoot / "benchmark.json", benchmark)
  os.write.over(
    iterationRoot / "benchmark.md",
    summaries.toList
      .sortBy(_._1)
      .map: (name, value) =>
        f"- $name: pass ${value.pass_rate.mean}%.2f ± ${value.pass_rate.stddev}%.2f, time ${value.time_seconds.mean}%.1fs, tokens ${value.tokens.mean}%.0f"
      .mkString("# Benchmark\n\n", "\n", "\n")
  )

  System.err.println(s"Results: $iterationRoot")
  benchmarkRuns.foreach: run =>
    System.err.println(
      s"${run.eval_name} ${run.configuration} #${run.run_number}: ${run.result.passed}/${run.result.total}"
    )
}

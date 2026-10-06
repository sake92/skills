//> using scala 3.9.0
//> using dep com.lihaoyi::mainargs:0.7.8
//> using dep com.lihaoyi::os-lib:0.11.8
//> using dep ba.sake::tupson:0.31.0
//> using file eval-runner/Models.scala
//> using file eval-runner/Infrastructure.scala
//> using file eval-runner/Metrics.scala

import ba.sake.tupson.*
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import mainargs.{Flag, ParserForMethods, arg}
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, blocking}

def runEval(config: Config)(using ExecutionContext): Unit =
  val root = os.pwd
  val evalRoot = root / "tests/pragmatic-architecture"
  val evalSet = os.read(evalRoot / "evals.json").parseJson[EvalSet]
  val providers = Set("ollama", "lmstudio", "openai")
  val judgeProvider = config.judgeProvider.getOrElse(config.provider)
  val judgeModel = config.judgeModel.getOrElse(config.model)
  require(providers(config.provider), "provider must be ollama, lmstudio, or openai")
  require(providers(judgeProvider), "judge provider must be ollama, lmstudio, or openai")
  require(config.runs > 0, "runs must be positive")
  require(config.timeoutMinutes > 0, "timeout-minutes must be positive")
  val evals = config.caseName match
    case None       => evalSet.evals
    case Some(name) => evalSet.evals.filter(_.name == name)
  require(evals.nonEmpty, s"No eval matched ${config.caseName.getOrElse("the eval set")}")
  evals.foreach: eval =>
    require(eval.files.nonEmpty, s"${eval.name} has no input files")
    require(os.isDir(root / os.RelPath(eval.files.head)), s"Missing starter for ${eval.name}: ${eval.files.head}")
    require(os.isFile(evalRoot / eval.name / "grading/judge.md"), s"Missing judge rubric for ${eval.name}")

  val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(java.time.ZoneOffset.UTC).format(Instant.now)
  val iterationRoot = root / "tests/tmp/pragmatic-architecture-workspace" / s"iteration-$timestamp"
  val skillRoot = root / "skills" / evalSet.skill_name
  val configurations = List("with_skill", "without_skill")
  val timeoutMs = config.timeoutMinutes.toLong * 60_000

  def runDirectory(caseOutput: os.Path, configuration: String, runNumber: Int): os.Path =
    val base = caseOutput / configuration
    if config.runs == 1 then base else base / s"run-$runNumber"

  val contexts = for
    eval <- evals
    configuration <- configurations
    runNumber <- 1 to config.runs
  yield
    val caseOutput = iterationRoot / s"eval-${eval.id}"
    writeJson(caseOutput / "eval_metadata.json", EvalMetadata(eval.id, eval.name, eval.prompt, eval.expectations))
    val runDir = runDirectory(caseOutput, configuration, runNumber)
    val work = runDir / "work"
    copyTree(root / os.RelPath(eval.files.head), work, Set(".bsp", ".scala-build"))
    configuration match
      case "with_skill" => copyTree(skillRoot, work / ".codex/skills" / evalSet.skill_name)
      case _            => ()
    List(
      "init" -> Seq("git", "init", "--quiet"),
      "add" -> Seq("git", "add", "."),
      "commit" -> Seq("git", "-c", "user.name=skill-eval", "-c", "user.email=skill-eval@example.invalid", "commit", "--quiet", "-m", "starter")
    ).foreach: (step, command) =>
      checked(runProcess(s"${eval.name} $configuration setup $step", command, work, runDir / s"setup-$step.log", timeoutMs = timeoutMs), step)
    RunContext(eval, configuration, runNumber, runDir, work)

  def codexCommand(
      work: os.Path,
      lastMessage: os.Path,
      provider: String,
      model: String,
      sandbox: String,
      schema: Option[os.Path] = None,
      skipGitRepoCheck: Boolean = false
  ): Seq[String] =
    Seq("codex", "-C", work.toString, "--enable", "skip_host_skill_discovery", "-a", "never") ++
      (if provider == "openai" then Nil else Seq("--oss", "--local-provider", provider)) ++
      Seq("-m", model, "-s", sandbox) ++
      config.reasoning.toSeq.flatMap(level => Seq("-c", s"model_reasoning_effort=\"$level\"")) ++
      Seq("exec", "--ephemeral", "--ignore-user-config", "--json") ++
      (if skipGitRepoCheck then Seq("--skip-git-repo-check") else Nil) ++
      schema.toSeq.flatMap(path => Seq("--output-schema", path.toString)) ++
      Seq("-o", lastMessage.toString, "-")

  def candidate(context: RunContext): Future[Candidate] = Future(blocking:
    val outputs = context.runDir / "outputs"
    os.makeDir.all(outputs)
    val transcript = outputs / "transcript.jsonl"
    val process = runProcess(
      s"${context.eval.name} ${context.configuration} candidate ${context.runNumber}",
      codexCommand(context.work, outputs / "final-message.txt", config.provider, config.model, "workspace-write"),
      root,
      transcript,
      stderr = Some(outputs / "codex.stderr.log"),
      stdin = Some(context.eval.prompt),
      timeoutMs = timeoutMs
    )
    val patch = os.proc("git", "diff", "--binary", "HEAD").call(cwd = context.work, check = false).out.text()
    os.write.over(outputs / "submission.patch", patch)
    val events = parseEvents(transcript)
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
  )

  val candidates = Await.result(Future.sequence(contexts.map(candidate)), Duration.Inf)
  val failedCandidates = candidates.filter(_.process.exit_code != 0)
  if failedCandidates.nonEmpty then
    failedCandidates.foreach: failed =>
      System.err.println(
        s"Candidate failed: ${failed.context.eval.name} ${failed.context.configuration} #${failed.context.runNumber}; " +
          s"see ${failed.context.runDir / "outputs/codex.stderr.log"}"
      )
    sys.exit(2)

  def grade(candidate: Candidate): Future[Graded] = Future(blocking:
    val context = candidate.context
    val graderStarted = Instant.now
    val graderStartNanos = System.nanoTime()
    val rubric = os.read(evalRoot / context.eval.name / "grading/judge.md")
    val qualitative = judgeExpectations(rubric)
    val deterministicTexts = context.eval.expectations.filterNot(qualitative.toSet)
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
      env = Map("SUBMISSION_DIR" -> context.work.toString, "GRADING_JSON" -> deterministicPath.toString),
      timeoutMs = timeoutMs
    )
    val deterministicReport =
      if os.isFile(deterministicPath) then os.read(deterministicPath).parseJson[GradingReport]
      else
        GradingReport(
          deterministicTexts.map(text => ExpectationResult(text, passed = false, s"Protected MUnit grading exited ${deterministic.exit_code}; see grading.log")),
          GradeSummary(0, deterministicTexts.size, deterministicTexts.size, 0.0)
        )
    val deterministicByText = deterministicReport.expectations.map(result => result.text -> result).toMap
    require(
      deterministicByText.keySet == deterministicTexts.toSet,
      s"${context.eval.name} deterministic grader returned unexpected expectations"
    )

    val judged =
      if !config.runJudge then Nil
      else
        val judgeWork = context.runDir / "judge-work"
        copyTree(context.work, judgeWork, Set(".codex", ".git", ".scala-build", ".bsp"))
        val submittedSource = os
          .walk(judgeWork)
          .filter(path => os.isFile(path) && (path.ext == "scala" || path.last == "project.scala"))
          .sortBy(_.toString)
          .map: path =>
            s"### ${path.relativeTo(judgeWork)}\n\n```scala\n${os.read(path)}\n```"
          .mkString("\n\n")
        val prompt = s"$rubric\n\n## Submitted source\n\n$submittedSource"
        val judgeOutput = context.runDir / "judge-output.json"
        val judgeProcess = runProcess(
          s"${context.eval.name} ${context.configuration} qualitative judge ${context.runNumber}",
          codexCommand(
            judgeWork,
            judgeOutput,
            judgeProvider,
            judgeModel,
            "read-only",
            schema = Some(evalRoot / "judge-output-schema.json"),
            skipGitRepoCheck = true
          ),
          root,
          context.runDir / "judge-transcript.jsonl",
          stderr = Some(context.runDir / "judge.stderr.log"),
          stdin = Some(prompt),
          timeoutMs = timeoutMs
        )
        checked(judgeProcess, s"${context.eval.name} judge")
        val report = os.read(judgeOutput).parseJson[JudgeReport]
        require(report.expectations.map(_.text).toSet == qualitative.toSet, s"${context.eval.name} judge returned unexpected expectations")
        require(report.expectations.size == qualitative.size, s"${context.eval.name} judge returned duplicate expectations")
        report.expectations

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
  )

  val graded = Await.result(Future.sequence(candidates.map(grade)), Duration.Inf)
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
      if run.candidate.context.configuration == "with_skill" && !run.candidate.metrics.skill_invoked.contains(true) then
        List("The installed skill was not invoked according to the Codex JSONL trace.")
      else Nil
    )

  val grouped = benchmarkRuns.groupBy(_.configuration)
  val summaries = grouped.view.mapValues: runs =>
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
      withSkill = benchmarkRuns.filter(run => run.configuration == "with_skill" && run.expectations.exists(_.text == expectation))
      withoutSkill = benchmarkRuns.filter(run => run.configuration == "without_skill" && run.expectations.exists(_.text == expectation))
      if withSkill.nonEmpty && withoutSkill.nonEmpty
      withPasses = withSkill.flatMap(_.expectations).filter(_.text == expectation).forall(_.passed)
      withoutPasses = withoutSkill.flatMap(_.expectations).filter(_.text == expectation).forall(_.passed)
      note <-
        if withPasses && withoutPasses then Some(s"Expectation passes in every with-skill and without-skill run and may not discriminate: $expectation")
        else if !withPasses && !withoutPasses then Some(s"Expectation fails in both configurations and needs investigation: $expectation")
        else if withPasses then Some(s"Expectation consistently passes with the skill but not without it: $expectation")
        else None
    yield note
    paired.distinct
      ++ Option.when(benchmarkRuns.exists(_.notes.nonEmpty))(
        "At least one with-skill run did not invoke the installed skill; inspect its trace before attributing results to the skill."
      )

  val withSkillSummary = summaries("with_skill")
  val withoutSkillSummary = summaries("without_skill")
  val benchmarkDelta = delta.getOrElse(throw IllegalStateException("Missing with-skill or without-skill summary"))
  val benchmark = Benchmark(
    BenchmarkMetadata(
      evalSet.skill_name,
      skillRoot.toString,
      config.model,
      Option.when(config.runJudge)(judgeModel),
      Instant.now.toString,
      evals.map(_.id),
      config.runs,
      commandOutput(Seq("git", "rev-parse", "HEAD"), root),
      commandOutput(Seq("codex", "--version"), root)
    ),
    benchmarkRuns,
    BenchmarkSummary(withSkillSummary, withoutSkillSummary, benchmarkDelta),
    notes
  )
  writeJson(iterationRoot / "benchmark.json", benchmark)
  os.write.over(
    iterationRoot / "benchmark.md",
    summaries.toList.sortBy(_._1).map: (name, value) =>
      f"- $name: pass ${value.pass_rate.mean}%.2f ± ${value.pass_rate.stddev}%.2f, time ${value.time_seconds.mean}%.1fs, tokens ${value.tokens.mean}%.0f"
    .mkString("# Benchmark\n\n", "\n", "\n")
  )

  System.err.println(s"Results: $iterationRoot")
  benchmarkRuns.foreach: run =>
    System.err.println(s"${run.eval_name} ${run.configuration} #${run.run_number}: ${run.result.passed}/${run.result.total}")

object RunEvalCli:
  @mainargs.main
  def run(
      @arg(name = "case", doc = "Optional eval name; all evals run by default") caseName: Option[String],
      @arg(doc = "ollama, lmstudio, or openai") provider: String = "openai",
      @arg(doc = "Model passed to Codex") model: String = "gpt-6-luna",
      @arg(doc = "Optional Codex reasoning effort") reasoning: Option[String],
      @arg(doc = "Optional separate provider for qualitative judges") judgeProvider: Option[String],
      @arg(doc = "Optional separate model for qualitative judges") judgeModel: Option[String],
      @arg(doc = "Runs per eval and configuration") runs: Int = 1,
      @arg(doc = "Timeout for each subprocess") timeoutMinutes: Int = 30,
      @arg(doc = "Run candidates and deterministic grading only") skipJudge: Flag
  ): Unit =
    val executor = Executors.newVirtualThreadPerTaskExecutor()
    given ExecutionContext = ExecutionContext.fromExecutorService(executor)
    try
      runEval(
        Config(caseName, provider, model, reasoning, judgeProvider, judgeModel, runs, timeoutMinutes, !skipJudge.value)
      )
    finally executor.shutdownNow()

  def main(args: Array[String]): Unit =
    ParserForMethods(this).runOrExit(args.toIndexedSeq)

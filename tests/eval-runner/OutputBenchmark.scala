import java.time.Instant

def outputBenchmarkRuns(graded: List[Graded]): List[BenchmarkRun] = graded.map: run =>
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

def writeOutputBenchmark(
    environment: OutputEnvironment,
    config: OutputConfig,
    runs: List[BenchmarkRun]
): Unit = {
  val summaries = outputSummaries(runs)
  val delta = for
    withSkill <- summaries.get("with_skill")
    withoutSkill <- summaries.get("without_skill")
  yield BenchmarkDelta(
    signed(withSkill.pass_rate.mean - withoutSkill.pass_rate.mean, 2),
    signed(withSkill.time_seconds.mean - withoutSkill.time_seconds.mean, 1),
    signed(withSkill.tokens.mean - withoutSkill.tokens.mean, 0)
  )

  writeJson(environment.iterationRoot / "runs.json", runs)
  delta.foreach: benchmarkDelta =>
    writeJson(
      environment.iterationRoot / "benchmark.json",
      Benchmark(
        BenchmarkMetadata(
          skill_name = environment.evalSet.skill_name,
          skill_path = environment.skillRoot.toString,
          executor_model = config.model,
          reasoning = config.reasoning,
          analyzer_model = Option.when(config.runJudge)(environment.judgeModel),
          timestamp = Instant.now.toString,
          evals_run = environment.evals.map(_.id),
          runs_per_configuration = config.runs,
          git_commit = commandOutput(Seq("git", "rev-parse", "HEAD"), environment.root),
          agent_version = commandOutput(Seq("pi", "--version"), environment.root)
        ),
        runs,
        BenchmarkSummary(
          summaries("with_skill"),
          summaries("without_skill"),
          benchmarkDelta
        ),
        outputNotes(environment.evals, runs)
      )
    )
  os.write.over(
    environment.iterationRoot / "benchmark.md",
    summaries.toList
      .sortBy(_._1)
      .map: (name, value) =>
        f"- $name: pass ${value.pass_rate.mean}%.2f ± ${value.pass_rate.stddev}%.2f, " +
          f"time ${value.time_seconds.mean}%.1fs, tokens ${value.tokens.mean}%.0f"
      .mkString("# Benchmark\n\n", "\n", "\n")
  )
}

private def outputSummaries(runs: List[BenchmarkRun]): Map[String, RunStats] =
  runs
    .groupBy(_.configuration)
    .view
    .mapValues: configurationRuns =>
      RunStats(
        stats(configurationRuns.map(_.result.pass_rate)),
        stats(configurationRuns.map(_.result.time_seconds)),
        stats(configurationRuns.map(_.result.tokens.toDouble))
      )
    .toMap

private def outputNotes(evals: List[EvalCase], runs: List[BenchmarkRun]): List[String] =
  val paired = for
    expectation <- evals.flatMap(_.expectations)
    withSkill = matchingRuns(runs, "with_skill", expectation)
    withoutSkill = matchingRuns(runs, "without_skill", expectation)
    if withSkill.nonEmpty && withoutSkill.nonEmpty
    withRate = expectationRate(withSkill, expectation)
    withoutRate = expectationRate(withoutSkill, expectation)
    rates = f"with skill ${withRate * 100}%.0f%%, without skill ${withoutRate * 100}%.0f%%"
  yield
    if withRate == 1.0 && withoutRate == 1.0 then
      s"Expectation passes in every run and may not discriminate ($rates): $expectation"
    else if withRate == 0.0 && withoutRate == 0.0 then
      s"Expectation fails in every run and needs investigation ($rates): $expectation"
    else if withRate > withoutRate then
      s"Expectation favors the skill but is not necessarily stable ($rates): $expectation"
    else if withRate < withoutRate then
      s"Expectation favors the baseline and needs investigation ($rates): $expectation"
    else s"Expectation has equal mixed results and may be flaky ($rates): $expectation"
  paired.distinct

private def matchingRuns(
    runs: List[BenchmarkRun],
    configuration: String,
    expectation: String
): List[BenchmarkRun] =
  runs.filter(run => run.configuration == configuration && run.expectations.exists(_.text == expectation))

private def expectationRate(runs: List[BenchmarkRun], expectation: String): Double =
  val results = runs.flatMap(_.expectations).filter(_.text == expectation)
  results.count(_.passed).toDouble / results.size

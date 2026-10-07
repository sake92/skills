import ba.sake.tupson.*
import java.time.Instant
import java.time.format.DateTimeFormatter
import ox.mapPar

def runTriggerEval(config: TriggerConfig): Unit = {
  val root = os.pwd
  val skillRoot = root / os.RelPath(config.skillPath)
  val skillFile = skillRoot / "SKILL.md"
  val evalSet =
    os.read(skillRoot / "evals/trigger-evals.json").parseJson[TriggerEvalSet]
  require(os.isFile(skillFile), s"Missing skill: $skillFile")
  require(config.runs > 0, "runs must be positive")
  require(config.timeoutMinutes > 0, "timeout-minutes must be positive")
  require(
    config.threshold > 0.0 && config.threshold <= 1.0,
    "threshold must be in (0, 1]"
  )
  val knownSplits = Set("train", "validation", "fresh")
  val splitQueries = config.split match
    case None        => evalSet.queries
    case Some(split) =>
      require(
        knownSplits(split),
        s"split must be one of ${knownSplits.toList.sorted.mkString(", ")}"
      )
      evalSet.queries.filter(_.split == split)
  val queries = config.query match
    case None       => splitQueries
    case Some(name) => splitQueries.filter(_.name == name)
  require(
    queries.nonEmpty,
    s"No trigger queries matched query=${config.query.getOrElse("any")}, split=${config.split.getOrElse("any")}"
  )
  require(
    queries.map(_.id).distinct.size == queries.size,
    "trigger query IDs must be unique"
  )
  require(
    queries.forall(query => knownSplits(query.split)),
    "trigger queries contain an unknown split"
  )

  val timestamp = DateTimeFormatter
    .ofPattern("yyyyMMdd-HHmmss")
    .withZone(java.time.ZoneOffset.UTC)
    .format(Instant.now)
  val iterationRoot =
    root / "tests/tmp/pragmatic-architecture-trigger-workspace" / s"iteration-$timestamp"
  val timeoutMs = config.timeoutMinutes.toLong * 60_000
  val settings = PiConfig(config.model, config.reasoning)

  val contexts = for
    query <- queries
    runNumber <- 1 to config.runs
  yield (query, runNumber)

  val runs = contexts.mapPar(config.parallelism) { case (query, runNumber) =>
    val runDir = iterationRoot / s"query-${query.id}" / s"run-$runNumber"
    val work = runDir / "work"
    val outputs = runDir / "outputs"
    os.makeDir.all(work)
    checked(
      runProcess(
        s"${query.name} setup",
        Seq("git", "init", "--quiet"),
        work,
        runDir / "setup.log",
        timeoutMs = timeoutMs
      ),
      "trigger setup"
    )
    os.makeDir.all(outputs)
    val transcript = outputs / "transcript.jsonl"
    val process = runProcess(
      s"${query.name} trigger #$runNumber",
      piCommand(
        settings,
        query.query,
        Some(skillRoot),
        Some("read,bash")
      ),
      work,
      transcript,
      stderr = Some(outputs / "pi.stderr.log"),
      stdin = Some(""),
      timeoutMs = timeoutMs
    )
    val events = parseEvents(transcript)
    os.write.over(outputs / "final-message.txt", finalAssistantText(events))
    val invoked = metrics(
      work,
      outputs,
      events,
      Some(evalSet.skill_name)
    ).skill_invoked.contains(true)
    TriggerRun(
      query.id,
      query.name,
      query.split,
      query.should_trigger,
      runNumber,
      invoked,
      process.exit_code
    )
  }
  val failedRuns = runs.filter(_.exit_code != 0)
  if failedRuns.nonEmpty then
    failedRuns.foreach: failed =>
      System.err.println(
        s"Trigger run failed: ${failed.query_name} #${failed.run_number}"
      )
    throw IllegalStateException(
      s"${failedRuns.size} trigger runs failed; no benchmark was produced"
    )

  val results = queries.map: query =>
    val matching = runs.filter(_.query_id == query.id)
    val triggers = matching.count(_.triggered)
    val rate = triggers.toDouble / matching.size
    val passed = if query.should_trigger then rate >= config.threshold
    else rate < config.threshold
    TriggerQueryResult(
      query.id,
      query.name,
      query.split,
      query.should_trigger,
      triggers,
      matching.size,
      rate,
      passed
    )

  def summarize(values: List[TriggerQueryResult]): TriggerSplitSummary =
    val passed = values.count(_.passed)
    TriggerSplitSummary(
      passed,
      values.size - passed,
      values.size,
      passed.toDouble / values.size
    )

  val splits = results.groupBy(_.split).view.mapValues(summarize).toMap
  val benchmark = TriggerBenchmark(
    TriggerBenchmarkMetadata(
      skill_name = evalSet.skill_name,
      skill_path = skillRoot.toString,
      description = skillDescription(skillFile),
      executor_model = config.model,
      reasoning = config.reasoning,
      timestamp = Instant.now.toString,
      runs_per_query = config.runs,
      threshold = config.threshold,
      git_commit = commandOutput(Seq("git", "rev-parse", "HEAD"), root),
      agent_version = commandOutput(Seq("pi", "--version"), root)
    ),
    runs,
    results,
    splits,
    summarize(results)
  )
  writeJson(iterationRoot / "trigger-benchmark.json", benchmark)
  os.write.over(
    iterationRoot / "trigger-benchmark.md",
    results
      .map(result =>
        f"| ${result.query_name} | ${result.split} | ${result.should_trigger} | ${result.triggers}/${result.runs} | ${result.trigger_rate * 100}%.0f%% | ${
            if result.passed then "pass" else "fail"
          } |"
      )
      .mkString(
        "# Trigger benchmark\n\n| Query | Split | Should trigger | Triggers | Rate | Result |\n| --- | --- | ---: | ---: | ---: | --- |\n",
        "\n",
        "\n"
      )
  )
  System.err.println(s"Results: $iterationRoot")
  splits.toList
    .sortBy(_._1)
    .foreach: (split, result) =>
      System.err.println(
        f"$split: ${result.passed}/${result.total} (${result.accuracy * 100}%.0f%%)"
      )
}

case class TriggerConfig(
    skillPath: String = "skills/pragmatic-architecture",
    query: Option[String] = None,
    split: Option[String] = None,
    model: String = "gpt-6-luna",
    reasoning: String = "low",
    runs: Int = 3,
    threshold: Double = 0.5,
    timeoutMinutes: Int = 10,
    parallelism: Int = 2
)

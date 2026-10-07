import ba.sake.tupson.*
import java.time.Instant
import java.time.format.DateTimeFormatter
import ox.mapPar

def runOutputEval(config: OutputConfig): Unit = {
  val environment = outputEnvironment(config)
  val contexts = prepareOutputContexts(environment, config)
  val candidates = contexts.mapPar(config.parallelism)(runOutputCandidate(environment, config))
  validateOutputCandidates(candidates)
  val graded = candidates.mapPar(config.parallelism)(gradeOutputCandidate(environment, config))
  val runs = outputBenchmarkRuns(graded)
  writeOutputBenchmark(environment, config, runs)

  System.err.println(s"Results: ${environment.iterationRoot}")
  runs.foreach: run =>
    System.err.println(
      s"${run.eval_name} ${run.configuration} #${run.run_number}: ${run.result.passed}/${run.result.total}"
    )
}

private def outputEnvironment(config: OutputConfig): OutputEnvironment = {
  require(config.runs > 0, "runs must be positive")
  require(config.timeoutMinutes > 0, "timeout-minutes must be positive")
  require(config.parallelism > 0, "parallelism must be positive")

  val root = os.pwd
  val evalRoot = root / "tests/pragmatic-architecture"
  val skillRoot = root / "skills/pragmatic-architecture"
  val evalSet = os.read(skillRoot / "evals/evals.json").parseJson[EvalSet]
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
  OutputEnvironment(
    root = root,
    evalRoot = evalRoot,
    skillRoot = skillRoot,
    evalSet = evalSet,
    evals = evals,
    judgeModel = config.judgeModel.getOrElse(config.model),
    iterationRoot = root / "tests/tmp/pragmatic-architecture-workspace" / s"iteration-$timestamp",
    timeoutMs = config.timeoutMinutes.toLong * 60_000
  )
}

import ba.sake.tupson.*

case class OutputConfig(
    caseName: Option[String] = None,
    model: String = "gpt-6-luna",
    reasoning: String = "low",
    judgeModel: Option[String] = None,
    configuration: Option[String] = None,
    runs: Int = 1,
    timeoutMinutes: Int = 30,
    runJudge: Boolean = true,
    parallelism: Int = 4
)

case class EvalSet(skill_name: String, evals: List[EvalCase]) derives JsonRW
case class EvalCase(
    id: Int,
    name: String,
    prompt: String,
    expected_output: String,
    files: List[String],
    expectations: List[String]
) derives JsonRW
case class EvalMetadata(
    eval_id: Int,
    eval_name: String,
    prompt: String,
    assertions: List[String]
) derives JsonRW

case class MetricStats(mean: Double, stddev: Double, min: Double, max: Double) derives JsonRW
case class RunStats(
    pass_rate: MetricStats,
    time_seconds: MetricStats,
    tokens: MetricStats
) derives JsonRW
case class BenchmarkDelta(
    pass_rate: String,
    time_seconds: String,
    tokens: String
) derives JsonRW
case class BenchmarkSummary(
    with_skill: RunStats,
    without_skill: RunStats,
    delta: BenchmarkDelta
) derives JsonRW
case class BenchmarkResult(
    pass_rate: Double,
    passed: Int,
    failed: Int,
    total: Int,
    time_seconds: Double,
    tokens: Long,
    tool_calls: Int,
    errors: Int
) derives JsonRW
case class BenchmarkRun(
    eval_id: Int,
    eval_name: String,
    configuration: String,
    run_number: Int,
    result: BenchmarkResult,
    expectations: List[ExpectationResult],
    notes: List[String] = Nil
) derives JsonRW
case class BenchmarkMetadata(
    skill_name: String,
    skill_path: String,
    executor_model: String,
    reasoning: String,
    analyzer_model: Option[String],
    timestamp: String,
    evals_run: List[Int],
    runs_per_configuration: Int,
    git_commit: String,
    agent_version: String
) derives JsonRW
case class Benchmark(
    metadata: BenchmarkMetadata,
    runs: List[BenchmarkRun],
    run_summary: BenchmarkSummary,
    notes: List[String]
) derives JsonRW

case class OutputEnvironment(
    root: os.Path,
    evalRoot: os.Path,
    skillRoot: os.Path,
    evalSet: EvalSet,
    evals: List[EvalCase],
    judgeModel: String,
    iterationRoot: os.Path,
    timeoutMs: Long
)
case class RunContext(
    eval: EvalCase,
    configuration: String,
    runNumber: Int,
    runDir: os.Path,
    work: os.Path
)
case class Candidate(
    context: RunContext,
    process: ProcessResult,
    usage: Usage,
    metrics: Metrics
)
case class Graded(candidate: Candidate, report: GradingReport, timing: Timing)

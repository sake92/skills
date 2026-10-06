import ba.sake.tupson.*

case class Config(
    caseName: Option[String] = None,
    provider: String = "openai",
    model: String = "gpt-6-luna",
    reasoning: Option[String] = None,
    judgeProvider: Option[String] = None,
    judgeModel: Option[String] = None,
    runs: Int = 1,
    timeoutMinutes: Int = 30,
    runJudge: Boolean = true
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
case class EvalMetadata(eval_id: Int, eval_name: String, prompt: String, assertions: List[String]) derives JsonRW

case class ExpectationResult(text: String, passed: Boolean, evidence: String) derives JsonRW
case class GradeSummary(passed: Int, failed: Int, total: Int, pass_rate: Double) derives JsonRW
case class GradingReport(expectations: List[ExpectationResult], summary: GradeSummary) derives JsonRW
case class JudgeReport(expectations: List[ExpectationResult]) derives JsonRW

case class ProcessResult(exit_code: Int, duration_ms: Long, started_at: String, finished_at: String) derives JsonRW
case class Usage(input_tokens: Long = 0, cached_input_tokens: Long = 0, output_tokens: Long = 0, reasoning_output_tokens: Long = 0)
    derives JsonRW
case class CodexItem(`type`: String = "") derives JsonRW
case class CodexEvent(`type`: String, usage: Option[Usage] = None, item: Option[CodexItem] = None) derives JsonRW
case class Timing(
    total_tokens: Long,
    duration_ms: Long,
    total_duration_seconds: Double,
    executor_start: String,
    executor_end: String,
    executor_duration_seconds: Double,
    grader_start: Option[String] = None,
    grader_end: Option[String] = None,
    grader_duration_seconds: Option[Double] = None
) derives JsonRW
case class Metrics(
    tool_calls: Map[String, Int],
    total_tool_calls: Int,
    total_steps: Int,
    files_created: List[String],
    errors_encountered: Int,
    output_chars: Long,
    transcript_chars: Long,
    skill_invoked: Option[Boolean] = None
) derives JsonRW

case class MetricStats(mean: Double, stddev: Double, min: Double, max: Double) derives JsonRW
case class RunStats(pass_rate: MetricStats, time_seconds: MetricStats, tokens: MetricStats) derives JsonRW
case class BenchmarkDelta(pass_rate: String, time_seconds: String, tokens: String) derives JsonRW
case class BenchmarkSummary(with_skill: RunStats, without_skill: RunStats, delta: BenchmarkDelta) derives JsonRW
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
    analyzer_model: Option[String],
    timestamp: String,
    evals_run: List[Int],
    runs_per_configuration: Int,
    git_commit: String,
    codex_version: String
) derives JsonRW
case class Benchmark(metadata: BenchmarkMetadata, runs: List[BenchmarkRun], run_summary: BenchmarkSummary, notes: List[String])
    derives JsonRW

case class RunContext(eval: EvalCase, configuration: String, runNumber: Int, runDir: os.Path, work: os.Path)
case class Candidate(context: RunContext, process: ProcessResult, usage: Usage, metrics: Metrics)
case class Graded(candidate: Candidate, report: GradingReport, timing: Timing)

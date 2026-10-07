import ba.sake.tupson.*

case class ExpectationResult(text: String, passed: Boolean, evidence: String) derives JsonRW
case class GradeSummary(passed: Int, failed: Int, total: Int, pass_rate: Double) derives JsonRW
case class GradingReport(
    expectations: List[ExpectationResult],
    summary: GradeSummary
) derives JsonRW
case class JudgeReport(expectations: List[ExpectationResult]) derives JsonRW

case class ProcessResult(
    exit_code: Int,
    duration_ms: Long,
    started_at: String,
    finished_at: String
) derives JsonRW
case class Usage(
    input_tokens: Long = 0,
    cached_input_tokens: Long = 0,
    output_tokens: Long = 0,
    reasoning_output_tokens: Long = 0
) derives JsonRW
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

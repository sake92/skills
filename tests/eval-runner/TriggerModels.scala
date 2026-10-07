import ba.sake.tupson.*

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

case class TriggerEvalSet(skill_name: String, queries: List[TriggerQuery]) derives JsonRW
case class TriggerQuery(
    id: Int,
    name: String,
    query: String,
    should_trigger: Boolean,
    split: String
) derives JsonRW
case class TriggerRun(
    query_id: Int,
    query_name: String,
    split: String,
    should_trigger: Boolean,
    run_number: Int,
    triggered: Boolean,
    exit_code: Int
) derives JsonRW
case class TriggerQueryResult(
    query_id: Int,
    query_name: String,
    split: String,
    should_trigger: Boolean,
    triggers: Int,
    runs: Int,
    trigger_rate: Double,
    passed: Boolean
) derives JsonRW
case class TriggerSplitSummary(
    passed: Int,
    failed: Int,
    total: Int,
    accuracy: Double
) derives JsonRW
case class TriggerBenchmarkMetadata(
    skill_name: String,
    skill_path: String,
    description: String,
    executor_model: String,
    reasoning: String,
    timestamp: String,
    runs_per_query: Int,
    threshold: Double,
    git_commit: String,
    agent_version: String
) derives JsonRW
case class TriggerBenchmark(
    metadata: TriggerBenchmarkMetadata,
    runs: List[TriggerRun],
    queries: List[TriggerQueryResult],
    splits: Map[String, TriggerSplitSummary],
    overall: TriggerSplitSummary
) derives JsonRW

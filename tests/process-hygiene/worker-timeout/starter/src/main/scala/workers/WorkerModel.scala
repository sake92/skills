package workers

import java.nio.file.Path

case class WorkerJob(
    name: String,
    command: Seq[String],
    cwd: Path,
    attemptDir: Path,
    timeoutMillis: Long,
    graceMillis: Long = 250,
    cleanupMillis: Long = 1000,
    env: Map[String, String] = Map.empty
)

enum Completion:
  case Exited(code: Int)
  case TimedOut

case class WorkerReport(name: String, completion: Completion, stdout: Path, stderr: Path)

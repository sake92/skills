package workers

import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*

/** Runs one attempt. The launcher and subsequent build attempts outlive this call. */
class WorkerRunner:
  def run(job: WorkerJob): WorkerReport =
    require(job.command.nonEmpty, "worker command is empty")
    require(job.timeoutMillis > 0, "timeout must be positive")
    require(job.graceMillis >= 0 && job.cleanupMillis > job.graceMillis, "invalid cleanup budget")
    val logs = AttemptLogs.prepare(job.attemptDir)
    val builder = new ProcessBuilder(job.command.asJava)
      .directory(job.cwd.toFile)
      .redirectOutput(logs.stdout.toFile)
      .redirectError(logs.stderr.toFile)
    job.env.foreach { (key, value) => builder.environment().put(key, value) }
    val process = builder.start()
    process.getOutputStream.close()
    try
      val completion = if process.waitFor(job.timeoutMillis, TimeUnit.MILLISECONDS) then
        Completion.Exited(process.exitValue())
      else
        WorkerCleanup.stop(process, job.graceMillis, job.cleanupMillis)
        Completion.TimedOut
      WorkerReport(job.name, completion, logs.stdout, logs.stderr)
    finally if process.isAlive then WorkerCleanup.stop(process, job.graceMillis, job.cleanupMillis)

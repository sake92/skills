package workers

import java.io.IOException
import java.util.concurrent.TimeUnit

/** Stops a timed-out command before the build launcher starts another attempt. */
object WorkerCleanup:
  def stop(process: Process, graceMillis: Long, cleanupMillis: Long): Unit =
    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(cleanupMillis)
    if process.isAlive then
      process.destroy()
      if !process.waitFor(graceMillis, TimeUnit.MILLISECONDS) then process.destroyForcibly()
    val remaining = math.max(0L, deadline - System.nanoTime())
    if !process.waitFor(remaining, TimeUnit.NANOSECONDS) then
      throw new IOException("worker did not stop within the cleanup budget")

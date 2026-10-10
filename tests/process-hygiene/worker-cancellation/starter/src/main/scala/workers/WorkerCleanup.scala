package workers

import java.io.IOException
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*

/** Stops a timed-out command before the build launcher starts another attempt. */
object WorkerCleanup:
  def stop(process: Process, graceMillis: Long, cleanupMillis: Long): Unit =
    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(cleanupMillis)
    val stream = process.toHandle.descendants()
    val children = try stream.iterator().asScala.toVector
    finally stream.close()
    val owned = children.reverse :+ process.toHandle
    owned.filter(_.isAlive).foreach(_.destroy())
    val graceDeadline = math.min(deadline, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(graceMillis))
    while owned.exists(_.isAlive) && System.nanoTime() < graceDeadline do Thread.sleep(5)
    val lateStream = process.toHandle.descendants()
    val late = try lateStream.iterator().asScala.toVector
    finally lateStream.close()
    val retained = (late.reverse ++ owned).distinct
    retained.filter(_.isAlive).foreach(_.destroyForcibly())
    while retained.exists(_.isAlive) && System.nanoTime() < deadline do Thread.sleep(5)
    val remaining = math.max(0L, deadline - System.nanoTime())
    if !process.waitFor(remaining, TimeUnit.NANOSECONDS) || retained.exists(_.isAlive) then
      throw new IOException("worker did not stop within the cleanup budget")

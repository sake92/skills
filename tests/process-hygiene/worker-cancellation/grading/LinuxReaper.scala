//> using dep net.java.dev.jna:jna:5.17.0
package evaluation

import com.sun.jna.{Library, Native}
import com.sun.jna.ptr.IntByReference

private[evaluation] trait LinuxLibC extends Library:
  def prctl(option: Int, arg2: Long, arg3: Long, arg4: Long, arg5: Long): Int
  def waitpid(pid: Int, status: IntByReference, options: Int): Int

/** Adopt fixture orphans so cleanup never relies on the host/container's PID 1. */
private[evaluation] object LinuxReaper {
  private val libc = Native.load("c", classOf[LinuxLibC])
  require(libc.prctl(36, 1L, 0L, 0L, 0L) == 0, "could not establish Linux fixture subreaper")

  def install(): Unit = ()

  // Exact fixture PIDs only: never waitpid(-1), which could steal a Java Process exit.
  def collect(handle: ProcessHandle): Unit =
    libc.waitpid(Math.toIntExact(handle.pid()), new IntByReference(), 1)
    ()
}

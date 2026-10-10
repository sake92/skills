package evaluation

import workers.*
import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

class EvaluationSuite extends RecordedSuite {
  LinuxReaper.install()

  expectation("Ordinary exits preserve exit status, launch settings and separate attempt logs.") {
    withFixture(ordinary = true) { fixture =>
      fixture.finish()
      val report = fixture.result.get().toOption.get
      assertEquals(report.completion, Completion.Exited(7))
      assertEquals(Files.readString(report.stdout), s"cancel-build|${fixture.directory}|literal argument\n")
      assertEquals(Files.readString(report.stderr), "warning\n")
    }
  }

  expectation("An uncancelled deadline still returns TimedOut after stopping the worker.") {
    withFixture(timeout = true, helpers = false) { fixture =>
      fixture.finish()
      assertEquals(fixture.result.get().toOption.get.completion, Completion.TimedOut)
      fixture.checkStopped()
      fixture.checkBudget(fromCancellation = false)
    }
  }

  expectation("Cancellation propagates InterruptedException after graceful workspace release.") {
    withFixture(cooperative = true) { fixture =>
      fixture.cancel()
      fixture.finish()
      fixture.checkCancelled()
      fixture.checkStopped()
      assert(Files.exists(fixture.directory.resolve("helper.released")), "helper missed graceful cleanup")
      fixture.checkLock()
      fixture.checkBudget()
    }
  }

  expectation("Cancellation stops resistant helpers even after the launcher exits.") {
    withFixture() { fixture =>
      fixture.cancel()
      fixture.finish()
      fixture.checkCancelled()
      fixture.checkStopped()
      fixture.checkLock()
      fixture.checkBudget()
    }
  }

  expectation("Repeated cancellation during cleanup cannot abandon work or restart the cleanup budget.") {
    withFixture(rootStays = true) { fixture =>
      fixture.cancel()
      fixture.awaitTerm()
      fixture.interruptCleanup()
      fixture.finish()
      fixture.checkCancelled()
      fixture.checkStopped()
      fixture.checkLock()
      fixture.checkBudget()
    }
  }

  expectation("Cancellation arriving during timeout cleanup remains cancellation and finishes cleanup.") {
    withFixture(timeout = true, rootStays = true) { fixture =>
      fixture.awaitTerm()
      fixture.cancel()
      fixture.finish()
      fixture.checkCancelled()
      fixture.checkStopped()
      fixture.checkLock()
      fixture.checkBudget(fromCancellation = false)
    }
  }

  private def withFixture[A](
      ordinary: Boolean = false,
      timeout: Boolean = false,
      helpers: Boolean = true,
      cooperative: Boolean = false,
      rootStays: Boolean = false
  )(body: Fixture => A): A =
    val fixture = new Fixture(ordinary, timeout, helpers, cooperative, rootStays)
    try
      fixture.awaitReady()
      body(fixture)
    finally fixture.close()

  private class Fixture(
      ordinary: Boolean,
      timeout: Boolean,
      helpers: Boolean,
      cooperative: Boolean,
      rootStays: Boolean
  ) extends AutoCloseable {
    val directory = Files.createTempDirectory("worker-cancellation-grade-")
    val result = new AtomicReference[Either[Throwable, WorkerReport]]()
    private val handles = mutable.LinkedHashMap.empty[String, ProcessHandle]
    private val ready = mutable.Set.empty[String]
    private val names = if helpers && !ordinary then List("root", "helper") else List("root")
    private val startNanos = System.nanoTime()
    private var cancellationNanos = startNanos
    private var finishNanos = startNanos
    private val job = WorkerJob(
      "compile",
      Seq("/bin/bash", directory.resolve("worker.sh").toString, "literal argument"),
      directory,
      directory.resolve("attempt"),
      if timeout then 700 else 10000,
      graceMillis = 350,
      cleanupMillis = 700,
      env = Map("BUILD_MARKER" -> "cancel-build")
    )

    private val setup = new ProcessBuilder("mkfifo", directory.resolve("idle.fifo").toString).start()
    require(setup.waitFor(2, TimeUnit.SECONDS) && setup.exitValue() == 0, "FIFO setup failed")
    private val helperTerm =
      if cooperative then "trap 'printf released > helper.released; exit 0' TERM" else "trap '' TERM"
    Files.writeString(
      directory.resolve("helper.sh"),
      s"""exec 9>workspace.lock
      |flock -x 9
      |$helperTerm
      |printf '%s' "$$$$" > helper.pid
      |exec 8<>idle.fifo
      |while :; do printf 'progress\n'; read -r -t 0.02 -u 8 unused || :; done
      |""".stripMargin
    )
    private val rootTerm = if rootStays || timeout && !helpers then "trap 'printf term > root.term' TERM"
    else "trap 'printf term > root.term; exit 0' TERM"
    private val body = if ordinary then """printf '%s|%s|%s\n' "$BUILD_MARKER" "$PWD" "$1"
      |printf 'warning\n' >&2
      |exit 7
      |""".stripMargin
    else
      (if helpers then "/bin/bash helper.sh &\n" else "") +
        "exec 8<>idle.fifo\nwhile :; do read -r -t 0.02 -u 8 unused || :; done\n"
    Files.writeString(directory.resolve("worker.sh"), s"$rootTerm\nprintf '%s' \"$$$$\" > root.pid\n$body")
    private val caller = new Thread(
      () =>
        try result.set(Right(new WorkerRunner().run(job)))
        catch case error: Throwable => result.set(Left(error)), "cancelled-build-caller"
    )
    caller.setDaemon(true) // Guard containment only: never let a broken submission hold the grader JVM open.
    caller.start()

    def awaitReady(): Unit =
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
      while !names.forall(ready.contains) && System.nanoTime() < deadline do
        names.filterNot(ready.contains).foreach { name =>
          val path = directory.resolve(s"$name.pid")
          if Files.exists(path) then
            val pid = Files.readString(path).trim
            if pid.nonEmpty then
              val handle = ProcessHandle.of(pid.toLong)
              if handle.isPresent then handles(name) = handle.get()
              if handle.isPresent || ordinary then ready += name
        }
        if !names.forall(ready.contains) then Thread.sleep(5)
      require(names.forall(ready.contains), "fixture did not become ready")

    def cancel(): Unit =
      cancellationNanos = System.nanoTime()
      caller.interrupt()

    def awaitTerm(): Unit =
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
      while !Files.exists(directory.resolve("root.term")) && System.nanoTime() < deadline do
        reap(); Thread.sleep(2)
      assert(Files.exists(directory.resolve("root.term")), "worker never reached graceful cleanup")

    def interruptCleanup(): Unit =
      val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1200)
      while caller.isAlive && System.nanoTime() < deadline do
        caller.interrupt()
        reap()
        Thread.sleep(10)

    def finish(): Unit =
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
      while caller.isAlive && System.nanoTime() < deadline do
        reap(); caller.join(5)
      finishNanos = System.nanoTime()
      assert(!caller.isAlive, "caller remained blocked after attempt completion/cancellation")
      assert(result.get() != null, "caller produced no outcome")
      reap()

    def checkCancelled(): Unit = assert(
      result.get().left.toOption.exists(_.isInstanceOf[InterruptedException]),
      s"expected InterruptedException, got ${result.get()}"
    )

    def checkStopped(): Unit =
      reap()
      handles.foreach { (name, handle) => assert(!handle.isAlive, s"$name survives completed cancellation") }

    def checkBudget(fromCancellation: Boolean = true): Unit =
      val started = if fromCancellation then cancellationNanos else startNanos
      val allowed = job.cleanupMillis + 300 + (if fromCancellation then 0 else job.timeoutMillis)
      val elapsed = TimeUnit.NANOSECONDS.toMillis(finishNanos - started)
      assert(elapsed <= allowed, s"completion took ${elapsed}ms, limit ${allowed}ms")

    def checkLock(): Unit =
      val probe = new ProcessBuilder("flock", "-n", directory.resolve("workspace.lock").toString, "/bin/true").start()
      try assert(probe.waitFor(2, TimeUnit.SECONDS) && probe.exitValue() == 0, "workspace lock remains held")
      finally
        if probe.isAlive then
          probe.destroyForcibly(); probe.waitFor()

    private def reap(): Unit = handles.get("helper").foreach(LinuxReaper.collect)

    override def close(): Unit =
      try
        names.filterNot(handles.contains).foreach { name =>
          val path = directory.resolve(s"$name.pid")
          if Files.exists(path) then
            val value = Files.readString(path).trim
            if value.nonEmpty then
              val handle = ProcessHandle.of(value.toLong)
              if handle.isPresent then handles(name) = handle.get()
        }
        handles.values.toList.reverse.foreach { handle =>
          val cwd = Path.of("/proc", handle.pid().toString, "cwd")
          val belongs = try Files.readSymbolicLink(cwd) == directory
          catch case _: java.io.IOException => false
          if handle.isAlive && belongs then handle.destroyForcibly()
        }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while handles.values.exists(_.isAlive) && System.nanoTime() < deadline do
          reap(); Thread.sleep(5)
        caller.interrupt()
        caller.join(1000)
        reap()
      finally
        val paths = Files.walk(directory)
        try paths.iterator().asScala.toList.reverse.foreach(Files.deleteIfExists)
        finally paths.close()
  }
}

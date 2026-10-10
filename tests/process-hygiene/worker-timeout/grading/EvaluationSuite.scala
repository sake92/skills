package evaluation

import workers.*
import java.nio.file.{Files, Path}
import java.util.concurrent.{Callable, Executors, Future, TimeUnit}
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

class EvaluationSuite extends RecordedSuite {
  LinuxReaper.install()

  expectation("Ordinary completions preserve exit codes, launch settings, and separate attempt logs.") {
    List(0, 7).foreach { code =>
      withFixture(count = 0, ordinaryCode = Some(code)) { fixture =>
        val result = fixture.finish()
        assertEquals(result.completion, Completion.Exited(code))
        assertEquals(result.name, "compile")
        assertEquals(Files.readString(result.stdout), s"compile|${fixture.directory}|argument with spaces; literal\n")
        assertEquals(Files.readString(result.stderr), "warning\n")
        assert(!fixture.rootAlive, "completed worker is still alive")
      }
    }
  }

  expectation("An unresponsive direct worker stops within budget and retains the timeout report.") {
    withFixture(count = 0, ignoreRootTerm = true) { fixture =>
      assertEquals(fixture.finish().completion, Completion.TimedOut)
      assert(!fixture.rootAlive, "timed-out worker is still alive")
      fixture.checkBudget()
    }
  }

  expectation("A cooperative helper gets a graceful cleanup opportunity before workspace reuse.") {
    withFixture(cooperative = true) { fixture =>
      assertEquals(fixture.finish().completion, Completion.TimedOut)
      fixture.checkStopped()
      assert(Files.exists(fixture.directory.resolve("leaf-1.released")), "helper never completed its TERM cleanup")
      fixture.checkLocksAvailable()
      fixture.checkBudget()
    }
  }

  expectation("A TERM-resistant helper is stopped even when its worker exits during graceful shutdown.") {
    withFixture() { fixture =>
      assertEquals(fixture.finish().completion, Completion.TimedOut)
      assert(Files.exists(fixture.directory.resolve("root.term")), "worker did not receive graceful termination")
      fixture.checkStopped()
      fixture.checkLocksAvailable()
      fixture.checkBudget()
    }
  }

  expectation("Timeout cleanup stops nested helpers rather than only the worker's immediate children.") {
    withFixture(nested = true) { fixture =>
      assertEquals(fixture.finish().completion, Completion.TimedOut)
      fixture.checkStopped()
      fixture.checkLocksAvailable()
      fixture.checkBudget()
    }
  }

  expectation("Cleanup uses one overall budget rather than a fresh grace period per helper.") {
    withFixture(count = 6) { fixture =>
      assertEquals(fixture.finish().completion, Completion.TimedOut)
      fixture.checkStopped()
      fixture.checkLocksAvailable()
      fixture.checkBudget()
    }
  }

  expectation("Cleanup stops replacement work started while the worker handles graceful termination.") {
    withFixture(count = 2, replacementOnTerm = true) { fixture =>
      assertEquals(fixture.finish().completion, Completion.TimedOut)
      assert(Files.exists(fixture.directory.resolve("leaf-2.pid")), "replacement helper did not start")
      fixture.checkStopped()
      fixture.checkLocksAvailable()
      fixture.checkBudget()
    }
  }

  private def withFixture[A](
      count: Int = 1,
      cooperative: Boolean = false,
      nested: Boolean = false,
      ignoreRootTerm: Boolean = false,
      ordinaryCode: Option[Int] = None,
      replacementOnTerm: Boolean = false
  )(body: Fixture => A): A =
    val fixture = new Fixture(count, cooperative, nested, ignoreRootTerm, ordinaryCode, replacementOnTerm)
    try
      fixture.awaitReady(); body(fixture)
    finally fixture.close()

  // Native reaping is containment only. It never signals a live helper before
  // assertions: a candidate leak must remain observable and fail the check.
  private class Fixture(
      count: Int,
      cooperative: Boolean,
      nested: Boolean,
      ignoreRootTerm: Boolean,
      ordinaryCode: Option[Int],
      replacementOnTerm: Boolean
  ) extends AutoCloseable {
    val directory = Files.createTempDirectory("worker-timeout-grade-")
    private val handles = mutable.LinkedHashMap.empty[String, ProcessHandle]
    private val ready = mutable.Set.empty[String]
    private val executor = Executors.newSingleThreadExecutor((r: Runnable) =>
      val thread = new Thread(r, "worker-timeout-grade-guard")
      thread.setDaemon(true)
      thread
    )
    private var elapsedMillis = 0L
    private val fifo = directory.resolve("idle.fifo")
    private val rootScript = directory.resolve("worker.sh")
    private val helpers = (1 to count).map(i => s"leaf-$i").toList ++ Option.when(nested)("middle").toList

    if ordinaryCode.isEmpty then
      val setup = new ProcessBuilder("mkfifo", fifo.toString).start()
      if !setup.waitFor(2, TimeUnit.SECONDS) then
        setup.destroyForcibly(); setup.waitFor()
      require(setup.exitValue() == 0, "fixture FIFO setup failed")

    (1 to count).foreach { i =>
      val name = s"leaf-$i"
      val handler = if cooperative then s"trap 'printf released > ${name}.released; exit 0' TERM" else "trap '' TERM"
      writeScript(
        name,
        s"""exec 9>workspace-$i.lock
        |flock -x 9
        |$handler
        |printf '%s' "$$$$" > $name.pid
        |exec 8<>idle.fifo
        |while :; do printf 'working-$i\n'; read -r -t 0.03 -u 8 unused || :; done
        |""".stripMargin
      )
    }
    if nested then
      writeScript(
        "middle",
        """trap '' TERM
      |printf '%s' "$$" > middle.pid
      |/bin/bash leaf-1.sh &
      |exec 8<>idle.fifo
      |while :; do read -r -t 0.03 -u 8 unused || :; done
      |""".stripMargin
      )

    val commandBody = ordinaryCode match
      case Some(code) => s"""printf '%s' "$$$$" > root.pid
        |printf '%s|%s|%s\n' "$$BUILD_MARKER" "$$PWD" "$$1"
        |printf 'warning\n' >&2
        |exit $code
        |""".stripMargin
      case None =>
        val rootHandler =
          if replacementOnTerm then "trap 'trap \"\" TERM; /bin/bash leaf-2.sh & printf stopped > root.term' TERM"
          else if ignoreRootTerm then "trap '' TERM"
          else "trap 'printf stopped > root.term; exit 0' TERM"
        val launches =
          if nested then "/bin/bash middle.sh &"
          else if replacementOnTerm then "/bin/bash leaf-1.sh &"
          else (1 to count).map(i => s"/bin/bash leaf-$i.sh &").mkString("\n")
        s"""$rootHandler
          |printf '%s' "$$$$" > root.pid
          |$launches
          |exec 8<>idle.fifo
          |while :; do read -r -t 0.03 -u 8 unused || :; done
          |""".stripMargin
    Files.writeString(rootScript, commandBody)
    val job = WorkerJob(
      "compile",
      Seq("/bin/bash", rootScript.toString, "argument with spaces; literal"),
      directory,
      directory.resolve("attempt"),
      timeoutMillis = 1200,
      graceMillis = 300,
      cleanupMillis = 700,
      env = Map("BUILD_MARKER" -> "compile")
    )
    private val startNanos = System.nanoTime()
    private val pending = executor.submit(new Callable[WorkerReport]:
      def call(): WorkerReport = new WorkerRunner().run(job))

    def rootAlive: Boolean = handles.get("root").exists(_.isAlive)

    def awaitReady(): Unit =
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
      val names = "root" :: helpers.filterNot(name => replacementOnTerm && name == "leaf-2")
      while !names.forall(ready.contains) && System.nanoTime() < deadline do
        names.filterNot(ready.contains).foreach { name =>
          val pidFile = directory.resolve(s"$name.pid")
          if Files.exists(pidFile) then
            val value = Files.readString(pidFile).trim
            if value.nonEmpty then
              val handle = ProcessHandle.of(value.toLong)
              if handle.isPresent then
                handles(name) = handle.get(); ready += name
              else if name == "root" && ordinaryCode.nonEmpty then ready += name
        }
        reap()
        if !names.forall(ready.contains) then Thread.sleep(5)
      require(names.forall(ready.contains), "fixture did not become ready before worker deadline")

    def finish(): WorkerReport =
      val deadline = startNanos + TimeUnit.SECONDS.toNanos(8)
      while !pending.isDone && System.nanoTime() < deadline do
        discoverReplacement(); reap(); Thread.sleep(5)
      val result = pending.get(1, TimeUnit.SECONDS)
      elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
      discoverReplacement(); reap()
      result

    private def discoverReplacement(): Unit =
      val path = directory.resolve("leaf-2.pid")
      if replacementOnTerm && !handles.contains("leaf-2") && Files.exists(path) then
        val value = Files.readString(path).trim
        if value.nonEmpty then
          val handle = ProcessHandle.of(value.toLong)
          if handle.isPresent then handles("leaf-2") = handle.get()

    def checkStopped(): Unit =
      reap()
      handles.foreach { (name, handle) => assert(!handle.isAlive, s"$name remains alive after run returned") }

    def checkBudget(): Unit =
      assert(
        elapsedMillis <= job.timeoutMillis + job.cleanupMillis + 500,
        s"attempt took ${elapsedMillis}ms despite a ${job.cleanupMillis}ms cleanup budget"
      )

    def checkLocksAvailable(): Unit =
      (1 to count).foreach { i =>
        val probe =
          new ProcessBuilder("flock", "-n", directory.resolve(s"workspace-$i.lock").toString, "/bin/true").start()
        try
          assert(probe.waitFor(2, TimeUnit.SECONDS), "lock probe did not finish")
          assertEquals(probe.exitValue(), 0, s"workspace-$i is still busy after run returned")
        finally
          if probe.isAlive then
            probe.destroyForcibly(); probe.waitFor()
      }

    private def writeScript(name: String, body: String): Unit = Files.writeString(directory.resolve(s"$name.sh"), body)
    private def reap(): Unit = handles.filter(_._1 != "root").values.foreach(LinuxReaper.collect)

    override def close(): Unit =
      try
        // Discover exact fixture identities even if readiness or the submission failed.
        ("root" :: helpers).filterNot(handles.contains).foreach { name =>
          val path = directory.resolve(s"$name.pid")
          if Files.exists(path) then
            val value = Files.readString(path).trim
            if value.nonEmpty then
              val handle = ProcessHandle.of(value.toLong)
              if handle.isPresent then handles(name) = handle.get()
        }
        handles.values.toList.reverse.foreach { handle =>
          // Signal only live handles whose command still names this unique directory.
          val args = handle.info().arguments().orElse(Array.empty[String])
          val cwd = Path.of("/proc", handle.pid().toString, "cwd")
          val inFixture = try Files.readSymbolicLink(cwd) == directory
          catch case _: java.io.IOException => false
          if handle.isAlive && inFixture && args.exists(arg =>
              arg.contains(directory.toString) || helpers.exists(n => arg == s"$n.sh")
            )
          then
            val descendants = handle.descendants()
            val children = try descendants.iterator().asScala.toList
            finally descendants.close()
            children.reverse.foreach(_.destroyForcibly())
            handle.destroyForcibly()
        }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while handles.values.exists(_.isAlive) && System.nanoTime() < deadline do
          reap(); Thread.sleep(5)
      finally
        pending.cancel(true)
        executor.shutdownNow()
        executor.awaitTermination(2, TimeUnit.SECONDS)
        reap()
        val files = Files.walk(directory)
        try files.iterator().asScala.toList.reverse.foreach(Files.deleteIfExists)
        finally files.close()
  }
}

package evaluation

import buildtool.*
import java.util.concurrent.{CountDownLatch, ThreadFactory, TimeUnit}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import scala.collection.mutable

class EvaluationSuite extends RecordedSuite {
  expectation("Successful concurrent builds preserve input order, all results and the launch adapter.") {
    withFixture { fixture =>
      fixture.start(
        Vector(
          BuildStep(
            "slow",
            () =>
              fixture.release.await(); "first"
          ),
          BuildStep(
            "fast",
            () =>
              fixture.release.countDown(); "second"
          )
        )
      )
      fixture.finish()
      assertEquals(
        fixture.outcome.get().toOption.get.results,
        Vector(StepResult("slow", "first"), StepResult("fast", "second"))
      )
      val adapter = BuildLaunch.execute(new ParallelBuildRunner(fixture.factory), Vector("api"), 1)(_.toUpperCase)
      assertEquals(adapter.results, Vector(StepResult("api", "API")))
    }
  }

  expectation("Parallelism limits active work without serializing a parallel build.") {
    withFixture { fixture =>
      val active = new AtomicInteger()
      val maximum = new AtomicInteger()
      val arrived = new CountDownLatch(2)
      val steps = (1 to 6)
        .map(i =>
          BuildStep(
            i.toString,
            () =>
              val current = active.incrementAndGet()
              maximum.accumulateAndGet(current, Math.max)
              arrived.countDown()
              try
                fixture.release.await(); i.toString
              finally active.decrementAndGet()
          )
        )
        .toVector
      fixture.start(steps)
      assert(arrived.await(2, TimeUnit.SECONDS), "two actions did not run concurrently")
      assertEquals(maximum.get(), 2)
      fixture.release.countDown()
      fixture.finish()
      assertEquals(fixture.outcome.get().toOption.get.results.size, 6)
      assert(maximum.get() <= 2)
    }
  }

  expectation("A later task's failure is observed promptly and cancels an earlier blocked sibling.") {
    withFixture { fixture =>
      val failure = new IllegalStateException("compile failure")
      fixture.start(
        Vector(
          fixture.blockingStep("early"),
          BuildStep(
            "failed",
            () =>
              require(fixture.started.await(2, TimeUnit.SECONDS)); throw failure
          )
        )
      )
      fixture.finish()
      assert(fixture.outcome.get().left.toOption.exists(_ eq failure), "original task failure was lost")
      fixture.checkReleased()
    }
  }

  expectation("Caller cancellation stops active tasks, prevents queued work and propagates interruption.") {
    withFixture { fixture =>
      val queued = new AtomicInteger()
      fixture.start(
        Vector(
          fixture.blockingStep("active"),
          BuildStep(
            "queued",
            () =>
              queued.incrementAndGet(); "unexpected"
          )
        ),
        parallelism = 1
      )
      assert(fixture.started.await(2, TimeUnit.SECONDS))
      fixture.cancel()
      fixture.finish()
      fixture.checkCancelled()
      fixture.checkReleased()
      assertEquals(queued.get(), 0, "queued action ran after cancellation")
    }
  }

  expectation("Completed cancellation joins worker finalizers and terminates the owned executor.") {
    withFixture { fixture =>
      fixture.start(Vector(fixture.blockingStep("compile", cleanupDelayMillis = 120)))
      assert(fixture.started.await(2, TimeUnit.SECONDS))
      fixture.cancel()
      fixture.finish()
      fixture.checkCancelled()
      fixture.checkReleased()
      fixture.checkThreadsStopped()
    }
  }

  expectation("Repeated interruption during cleanup retains the original failure and waits within one budget.") {
    withFixture { fixture =>
      val failure = new IllegalArgumentException("broken module")
      fixture.start(
        Vector(
          fixture.blockingStep("compile", cleanupDelayMillis = 300),
          BuildStep(
            "failed",
            () =>
              require(fixture.started.await(2, TimeUnit.SECONDS)); throw failure
          )
        )
      )
      val cleanupStarted = fixture.finalizing.await(2, TimeUnit.SECONDS)
      assert(cleanupStarted, "failure did not cancel sibling work")
      fixture.interruptCleanup()
      fixture.finish()
      assert(fixture.outcome.get().left.toOption.exists(_ eq failure), "cleanup interruption replaced task failure")
      fixture.checkReleased()
      fixture.checkThreadsStopped()
      fixture.checkBudget()
    }
  }

  expectation("Partial submission failure stops and joins already-started tasks.") {
    withFixture { fixture =>
      val startupFailure = new IllegalStateException("worker factory failed")
      val calls = new AtomicInteger()
      val failingFactory = new ThreadFactory:
        def newThread(runnable: Runnable): Thread =
          if calls.incrementAndGet() == 2 then
            require(fixture.started.await(2, TimeUnit.SECONDS))
            throw startupFailure
          else fixture.factory.newThread(runnable)
      fixture.start(
        Vector(fixture.blockingStep("started"), BuildStep("not-started", () => "unreachable")),
        customFactory = failingFactory
      )
      fixture.finish()
      assert(fixture.outcome.get().left.toOption.exists(_ eq startupFailure))
      fixture.checkReleased()
      fixture.checkThreadsStopped()
    }
  }

  private def withFixture[A](body: Fixture => A): A =
    val fixture = new Fixture()
    try body(fixture)
    finally fixture.close()

  expectation("Cancellation waits for supplied-factory worker context finalization, not just executor termination.") {
    withFixture { fixture =>
      val contextReleased = new CountDownLatch(1)
      val contextFactory = new ThreadFactory:
        def newThread(runnable: Runnable): Thread = fixture.factory.newThread(() =>
          try runnable.run()
          finally
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(300)
            while System.nanoTime() < deadline do
              try Thread.sleep(5)
              catch case _: InterruptedException => ()
            contextReleased.countDown()
        )
      fixture.start(Vector(fixture.blockingStep("compile")), parallelism = 1, customFactory = contextFactory)
      assert(fixture.started.await(2, TimeUnit.SECONDS))
      fixture.cancel()
      fixture.finish()
      fixture.checkCancelled()
      assertEquals(contextReleased.getCount, 0L, "worker resource scope survives reported cancellation")
      fixture.checkThreadsStopped()
      fixture.checkBudget()
    }
  }

  private class Fixture extends AutoCloseable {
    val release = new CountDownLatch(1)
    val started = new CountDownLatch(1)
    val finalizing = new CountDownLatch(1)
    val released = new CountDownLatch(1)
    val outcome = new AtomicReference[Either[Throwable, BuildReport]]()
    private val threads = mutable.ArrayBuffer.empty[Thread]
    private var caller: Thread = null
    private var triggeredNanos = System.nanoTime()
    val factory: ThreadFactory = (runnable: Runnable) =>
      val thread = new Thread(runnable, "protected-build-worker")
      thread.setDaemon(true) // Containment only: tests still require these workers to terminate.
      threads.synchronized { threads += thread }
      thread

    def blockingStep(name: String, cleanupDelayMillis: Long = 0): BuildStep = BuildStep(
      name,
      () =>
        started.countDown()
        try
          release.await(); "released"
        finally
          triggeredNanos = System.nanoTime()
          finalizing.countDown()
          // A resource release that completes despite repeated task interruption.
          val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(cleanupDelayMillis)
          while System.nanoTime() < deadline do
            try Thread.sleep(5)
            catch case _: InterruptedException => ()
          released.countDown()
    )

    def start(steps: Vector[BuildStep], parallelism: Int = 2, customFactory: ThreadFactory = factory): Unit =
      val action = new Runnable:
        def run(): Unit =
          try outcome.set(Right(new ParallelBuildRunner(customFactory).run(BuildRequest(steps, parallelism, 700))))
          catch case error: Throwable => outcome.set(Left(error))
      caller = new Thread(action, "protected-build-caller")
      caller.setDaemon(true)
      caller.start()

    def cancel(): Unit =
      triggeredNanos = System.nanoTime(); caller.interrupt()

    def interruptCleanup(): Unit =
      val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1000)
      while caller.isAlive && System.nanoTime() < deadline do
        caller.interrupt(); caller.join(10)

    def finish(): Unit =
      caller.join(2000)
      assert(!caller.isAlive, "build did not observe failure/cancellation promptly")
      assert(outcome.get() != null)

    def checkCancelled(): Unit = assert(outcome.get().left.toOption.exists(_.isInstanceOf[InterruptedException]))
    def checkReleased(): Unit =
      assertEquals(released.getCount, 0L, "task still owns its workspace after build completion")
    def checkThreadsStopped(): Unit = threads.synchronized {
      assert(threads.nonEmpty, "supplied worker factory was bypassed")
      assert(threads.forall(!_.isAlive), "owned executor worker survives completion")
    }
    def checkBudget(): Unit = assert(
      TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - triggeredNanos) <= 1000,
      "cleanup exceeded its overall budget"
    )

    override def close(): Unit =
      release.countDown()
      if caller != null then
        caller.interrupt(); caller.join(1000)
      threads.synchronized { threads.foreach(_.interrupt()); threads.foreach(_.join(1000)) }
  }
}

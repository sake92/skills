package buildtool

import java.util.concurrent.{Callable, Executors, Future, ThreadFactory}

/** One build owns one pool; the supplied factory configures its worker threads. */
private[buildtool] class BuildExecutor(parallelism: Int, factory: ThreadFactory) {
  private val pool = Executors.newFixedThreadPool(parallelism, factory)

  def submit(step: BuildStep): Future[StepResult] =
    pool.submit(new Callable[StepResult]:
      def call(): StepResult = StepResult(step.name, step.action()))

  def close(): Unit = pool.shutdown()
}

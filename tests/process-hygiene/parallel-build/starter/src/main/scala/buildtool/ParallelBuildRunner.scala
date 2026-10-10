package buildtool

import java.util.concurrent.{ExecutionException, Executors, ThreadFactory}

/** The launcher can start a new build as soon as this call returns or throws. */
class ParallelBuildRunner(factory: ThreadFactory = Executors.defaultThreadFactory()):
  def run(request: BuildRequest): BuildReport =
    require(request.parallelism > 0, "parallelism must be positive")
    require(request.cleanupMillis > 0, "cleanup budget must be positive")
    val executor = new BuildExecutor(request.parallelism, factory)
    try
      val futures = request.steps.map(executor.submit)
      BuildReport(futures.map(_.get()))
    catch case failure: ExecutionException => throw failure.getCause
    finally executor.close()

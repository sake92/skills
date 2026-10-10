package buildtool

/** Adapter used by the command launcher; concurrency failures belong to the caller. */
object BuildLaunch:
  def execute(runner: ParallelBuildRunner, names: Vector[String], parallelism: Int)(
      compile: String => String
  ): BuildReport =
    runner.run(BuildRequest(names.map(name => BuildStep(name, () => compile(name))), parallelism))

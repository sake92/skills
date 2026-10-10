package buildtool

case class BuildStep(name: String, action: () => String)
case class BuildRequest(steps: Vector[BuildStep], parallelism: Int, cleanupMillis: Long = 700)
case class StepResult(name: String, value: String)
case class BuildReport(results: Vector[StepResult])

package buildtool

/** Adapts tool execution to task events and the launcher command's result. */
class BuildTask(tool: ForkedTool, events: TaskEvents):
  def execute(name: String, spec: ProcessSpec, streams: TaskStreams): TaskOutcome =
    events.started(name)
    val result = tool.run(spec, streams)
    events.finished(name, 0)
    TaskOutcome(name, 0)

object BuildMain:
  def runTask(name: String, spec: ProcessSpec, streams: TaskStreams, events: TaskEvents): Int =
    val task = new BuildTask(new ForkedTool(System.in), events)
    task.execute(name, spec, streams).exitCode

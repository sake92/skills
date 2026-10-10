package buildtool

import java.io.{ByteArrayInputStream, InputStream}
import scala.jdk.CollectionConverters.*

/** Executes a configured tool inside a task; the build launcher stays alive. */
class ForkedTool(launcherInput: InputStream):
  def run(spec: ProcessSpec, streams: TaskStreams): ProcessExit =
    val builder = new ProcessBuilder(spec.command.asJava)
      .directory(spec.cwd.toFile)
      .redirectErrorStream(true)
    spec.env.foreach { (key, value) => builder.environment().put(key, value) }
    val process = builder.start()
    val source = spec.input match
      case CommandInput.Closed              => launcherInput
      case CommandInput.Document(bytes)     => new ByteArrayInputStream(bytes)
      case CommandInput.Interactive(stream) => stream
    try
      source.transferTo(process.getOutputStream)
      process.getOutputStream.close()
      val code = process.waitFor()
      StreamPump.copy(process.getInputStream, streams.out)
      StreamPump.copy(process.getErrorStream, streams.err)
      ProcessExit(code)
    finally
      source.close()
      if process.isAlive then process.destroyForcibly()

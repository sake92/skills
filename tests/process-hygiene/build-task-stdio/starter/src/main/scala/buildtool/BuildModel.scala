package buildtool

import java.io.{InputStream, OutputStream}
import java.nio.file.Path

enum CommandInput:
  case Closed
  case Document(bytes: Array[Byte])
  case Interactive(stream: InputStream)

case class ProcessSpec(
    command: Seq[String],
    cwd: Path,
    env: Map[String, String] = Map.empty,
    input: CommandInput = CommandInput.Closed
)
case class TaskStreams(out: OutputStream, err: OutputStream)
case class ProcessExit(code: Int)
case class TaskOutcome(name: String, exitCode: Int):
  def successful: Boolean = exitCode == 0

trait TaskEvents:
  def started(name: String): Unit
  def finished(name: String, exitCode: Int): Unit

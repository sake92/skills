package workers

import java.nio.file.{Files, Path}

case class AttemptLogs(stdout: Path, stderr: Path)

object AttemptLogs:
  def prepare(directory: Path): AttemptLogs =
    Files.createDirectories(directory)
    AttemptLogs(directory.resolve("stdout.log"), directory.resolve("stderr.log"))

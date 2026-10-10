package workers

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

class WorkerRunnerSuite extends munit.FunSuite {
  test("ordinary worker result retains exit status and attempt logs") {
    withDirectory { directory =>
      val job = WorkerJob(
        "compile",
        Seq("/bin/bash", "-c", "printf 'compiled\\n'; printf 'warning\\n' >&2; exit 7"),
        directory,
        directory.resolve("attempt"),
        2000
      )
      val result = new WorkerRunner().run(job)
      assertEquals(result.completion, Completion.Exited(7))
      assertEquals(result.name, "compile")
      assertEquals(Files.readString(result.stdout), "compiled\n")
      assertEquals(Files.readString(result.stderr), "warning\n")
    }
  }

  test("a command that exceeds its budget is reported as timed out") {
    withDirectory { directory =>
      val job = WorkerJob(
        "compile",
        Seq("/bin/bash", "-c", "trap '' TERM; while :; do :; done"),
        directory,
        directory.resolve("attempt"),
        100,
        graceMillis = 40,
        cleanupMillis = 500
      )
      val started = System.nanoTime()
      val result = new WorkerRunner().run(job)
      assertEquals(result.completion, Completion.TimedOut)
      assert((System.nanoTime() - started) < 3_000_000_000L)
    }
  }

  private def withDirectory[A](body: Path => A): A =
    val directory = Files.createTempDirectory("worker-runner-test-")
    try body(directory)
    finally
      val files = Files.walk(directory)
      try files.iterator().asScala.toList.reverse.foreach(Files.deleteIfExists)
      finally files.close()
}

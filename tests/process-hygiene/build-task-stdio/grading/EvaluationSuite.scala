package evaluation

import buildtool.*
import java.io.*
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.concurrent.{Callable, Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.*

class EvaluationSuite extends RecordedSuite {
  expectation("A prompt without a newline is delivered and flushed before the tool waits for interactive input.") {
    withFixture("printf 'ready>'; IFS= read -r reply; printf 'received:%s' \"$reply\"") { fixture =>
      val input = fixture.own(new PipedInputStream())
      val response = fixture.own(new PipedOutputStream(input))
      val replied = new AtomicBoolean(false)
      val out = new TrackingOutput:
        override def flush(): Unit =
          super.flush()
          if text.contains("ready>") && replied.compareAndSet(false, true) then
            response.write("answer\n".getBytes(UTF_8))
            response.close()
      val result = fixture.guard {
        fixture.tool.run(
          fixture.spec.copy(input = CommandInput.Interactive(input)),
          TaskStreams(out, new TrackingOutput)
        )
      }
      assertEquals(result.code, 0)
      assert(replied.get(), "prompt was not forwarded and flushed while the tool was waiting")
      assertEquals(out.text, "ready>received:answer")
    }
  }

  expectation("Stdout and stderr are forwarded separately without changing bytes or final-newline state.") {
    withFixture("printf '  report\\t\\r\\n\\n'; printf '  diagnostic\\t' >&2") { fixture =>
      val out = new TrackingOutput
      val err = new TrackingOutput
      assertEquals(fixture.guard(fixture.tool.run(fixture.spec, TaskStreams(out, err))).code, 0)
      assertEquals(out.text, "  report\t\r\n\n")
      assertEquals(err.text, "  diagnostic\t")
    }
  }

  expectation("Document stdin delivers exact bytes and EOF, including binary bytes.") {
    withFixture("cat") { fixture =>
      val document = Array[Byte](0, -1, -128, 32) ++ "račun €\r\n\tend".getBytes(UTF_8)
      val out = new TrackingOutput
      val err = new TrackingOutput
      val result = fixture.guard {
        fixture.tool.run(fixture.spec.copy(input = CommandInput.Document(document)), TaskStreams(out, err))
      }
      assertEquals(result.code, 0)
      assertEquals(out.bytes.toSeq, document.toSeq)
      assertEquals(err.bytes.length, 0)
    }
  }

  expectation("Closed stdin delivers EOF without consuming or closing launcher input.") {
    withFixture("cat") { fixture =>
      val launcher = new TrackingInput("next build command\n".getBytes(UTF_8))
      val out = new TrackingOutput
      val result = fixture.guard(new ForkedTool(launcher).run(fixture.spec, TaskStreams(out, new TrackingOutput)))
      assertEquals(result.code, 0)
      assertEquals(out.bytes.length, 0)
      assert(!launcher.closed, "runner closed launcher input")
      assertEquals(new String(launcher.readAllBytes(), UTF_8), "next build command\n")
    }
  }

  expectation("The tool exit code reaches task outcomes, completion events, and the launcher entrypoint.") {
    List(0, 7, 23).foreach { code =>
      withFixture(s"printf 'partial\\n'; exit $code") { fixture =>
        val events = new RecordingEvents
        val task = new BuildTask(fixture.tool, events)
        val outcome =
          fixture.guard(task.execute("compile", fixture.spec, TaskStreams(new TrackingOutput, new TrackingOutput)))
        assertEquals(outcome, TaskOutcome("compile", code))
        assertEquals(outcome.successful, code == 0)
        assertEquals(events.values.toList, List("start:compile", s"finish:compile:$code"))
        val entryEvents = new RecordingEvents
        val entryCode = fixture.guard {
          BuildMain.runTask(
            "compile",
            fixture.spec.copy(input = CommandInput.Document(Array.emptyByteArray)),
            TaskStreams(new TrackingOutput, new TrackingOutput),
            entryEvents
          )
        }
        assertEquals(entryCode, code)
        assertEquals(entryEvents.values.toList, List("start:compile", s"finish:compile:$code"))
      }
    }
  }

  expectation("Large stdout and stderr complete without deadlock or output loss.") {
    val outChunk = "O" * 4096
    val errChunk = "E" * 4096
    val script = s"i=0; while [ \"$$i\" -lt 128 ]; do printf '$errChunk' >&2; printf '$outChunk'; i=$$((i+1)); done"
    withFixture(script) { fixture =>
      val out = new TrackingOutput
      val err = new TrackingOutput
      assertEquals(fixture.guard(fixture.tool.run(fixture.spec, TaskStreams(out, err))).code, 0)
      assertEquals(out.bytes.length, 524288)
      assertEquals(err.bytes.length, 524288)
      assert(out.bytes.forall(_ == 'O'.toByte), "stdout contains altered or misplaced bytes")
      assert(err.bytes.forall(_ == 'E'.toByte), "stderr contains altered or misplaced bytes")
    }
  }

  expectation("Borrowed task streams and interactive input remain usable across subsequent tasks.") {
    withFixture("cat") { fixture =>
      val input = new TrackingInput("first\n".getBytes(UTF_8))
      val out = new TrackingOutput
      val err = new TrackingOutput
      fixture.guard {
        fixture.tool.run(fixture.spec.copy(input = CommandInput.Interactive(input)), TaskStreams(out, err))
      }
      assert(!input.closed, "runner closed borrowed interactive input")
      assert(!out.closed && !err.closed, "runner closed borrowed task streams")
      out.write("between\n".getBytes(UTF_8))
      err.write("launcher diagnostic\n".getBytes(UTF_8))
      fixture.guard {
        fixture.tool.run(
          fixture.spec.copy(input = CommandInput.Document("second\n".getBytes(UTF_8))),
          TaskStreams(out, err)
        )
      }
      assertEquals(out.text, "first\nbetween\nsecond\n")
      assertEquals(err.text, "launcher diagnostic\n")
      val nextOut = new TrackingOutput
      fixture.guard {
        fixture.tool.run(
          fixture.spec.copy(input = CommandInput.Document("other task\n".getBytes(UTF_8))),
          TaskStreams(nextOut, new TrackingOutput)
        )
      }
      assertEquals(nextOut.text, "other task\n")
      assertEquals(out.text, "first\nbetween\nsecond\n")
    }
  }

  expectation("Argument vectors, working directory, and environment overrides retain their behavior.") {
    withFixture("printf '%s|%s|%s\\n' \"$BUILD_MARKER\" \"$PWD\" \"$1\"") { fixture =>
      val argument = "source directory; $(not-a-command)"
      val spec = fixture.spec.copy(command = fixture.spec.command :+ argument, env = Map("BUILD_MARKER" -> "compile"))
      val out = new TrackingOutput
      val events = new RecordingEvents
      assertEquals(
        fixture
          .guard(new BuildTask(fixture.tool, events).execute("compile", spec, TaskStreams(out, new TrackingOutput)))
          .exitCode,
        0
      )
      assertEquals(out.text, s"compile|${fixture.directory}|$argument\n")
    }
  }

  private def withFixture[A](script: String)(body: Fixture => A): A =
    val fixture = new Fixture(script)
    try body(fixture)
    finally fixture.close()

  private class RecordingEvents extends TaskEvents:
    val values = ArrayBuffer.empty[String]
    def started(name: String): Unit = values += s"start:$name"
    def finished(name: String, exitCode: Int): Unit = values += s"finish:$name:$exitCode"

  private class TrackingInput(bytes: Array[Byte]) extends ByteArrayInputStream(bytes):
    var closed = false
    override def close(): Unit = closed = true

  private class TrackingOutput extends OutputStream:
    private val buffer = new ByteArrayOutputStream
    @volatile var closed = false
    def bytes: Array[Byte] = synchronized(buffer.toByteArray)
    def text: String = new String(bytes, UTF_8)
    override def write(value: Int): Unit = synchronized {
      if closed then throw new IOException("borrowed output was closed")
      buffer.write(value)
    }
    override def write(values: Array[Byte], offset: Int, length: Int): Unit = synchronized {
      if closed then throw new IOException("borrowed output was closed")
      buffer.write(values, offset, length)
    }
    override def close(): Unit = closed = true

  // This owner is outside the candidate. It bounds broken submissions and cleans
  // up its actual shell processes, pipe endpoints, and guard threads independently.
  private class Fixture(script: String) extends AutoCloseable {
    val directory = Files.createTempDirectory("build-task-stdio-")
    private val command = directory.resolve("tool.sh")
    private val pidFile = directory.resolve("pid")
    private val resources = ArrayBuffer.empty[AutoCloseable]
    private val executor = Executors.newSingleThreadExecutor((r: Runnable) =>
      val thread = new Thread(r, "build-task-grade-guard")
      thread.setDaemon(true)
      thread
    )
    Files.writeString(command, s"printf '%s' \"$$$$\" > '$pidFile'\n$script\ncode=$$?\nexit \"$$code\"\n")
    val spec = ProcessSpec(Seq("/bin/sh", command.toString), directory)
    val tool = new ForkedTool(new ByteArrayInputStream(Array.emptyByteArray))

    def own[A <: AutoCloseable](resource: A): A =
      resources += resource; resource

    def guard[A](body: => A): A =
      val pending = executor.submit(new Callable[A]:
        def call(): A = body)
      try pending.get(10, TimeUnit.SECONDS)
      finally if !pending.isDone then pending.cancel(true)

    override def close(): Unit =
      try
        if Files.exists(pidFile) then
          val pid = Files.readString(pidFile).trim
          if pid.nonEmpty then
            val handle = ProcessHandle.of(pid.toLong)
            if handle.isPresent && handle
                .get()
                .info()
                .arguments()
                .orElse(Array.empty[String])
                .contains(command.toString)
            then
              val root = handle.get()
              val descendants = root.descendants()
              val children = try descendants.iterator().asScala.toList
              finally descendants.close()
              children.reverse.foreach(_.destroyForcibly())
              root.destroyForcibly()
              try root.onExit().get(2, TimeUnit.SECONDS)
              catch case _: java.util.concurrent.TimeoutException => ()
      finally
        resources.reverse.foreach(_.close())
        executor.shutdownNow()
        executor.awaitTermination(2, TimeUnit.SECONDS)
        val paths = Files.walk(directory)
        try paths.iterator().asScala.toList.reverse.foreach(Files.deleteIfExists)
        finally paths.close()
  }
}

package buildtool

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import scala.collection.mutable.ArrayBuffer

class BuildTaskSuite extends munit.FunSuite:
  test("successful tool reports task events and output") {
    val out = new ByteArrayOutputStream()
    val err = new ByteArrayOutputStream()
    val events = ArrayBuffer.empty[String]
    val listener = new TaskEvents:
      def started(name: String): Unit = events += s"start:$name"
      def finished(name: String, code: Int): Unit = events += s"finish:$name:$code"
    val tool = new ForkedTool(new ByteArrayInputStream(Array.emptyByteArray))
    val spec = ProcessSpec(Seq("/bin/sh", "-c", "printf 'compiled\\n'"), Path.of("."))
    val result = new BuildTask(tool, listener).execute("compile", spec, TaskStreams(out, err))
    assert(result.successful)
    assertEquals(events.toList, List("start:compile", "finish:compile:0"))
    assertEquals(out.toString(UTF_8), "compiled\n")
    assertEquals(err.size(), 0)
  }
  test("document input reaches the tool") {
    val out = new ByteArrayOutputStream()
    val err = new ByteArrayOutputStream()
    val tool = new ForkedTool(new ByteArrayInputStream(Array.emptyByteArray))
    val spec =
      ProcessSpec(Seq("/bin/sh", "-c", "cat"), Path.of("."), input = CommandInput.Document("source\n".getBytes(UTF_8)))
    assertEquals(tool.run(spec, TaskStreams(out, err)), ProcessExit(0))
    assertEquals(out.toString(UTF_8), "source\n")
  }

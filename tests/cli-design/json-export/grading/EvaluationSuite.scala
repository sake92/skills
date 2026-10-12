package evaluation

import ba.sake.tupson.*
import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*

case class ExportRow(id: String, name: String, quantity: Int) derives JsonRW
case class Result(code: Int, out: String, err: String)

class EvaluationSuite extends RecordedSuite {
  private val rows = List(ExportRow("b", "Žlica \"large\"\nnext\tline\\", 3), ExportRow("a", "Apron", 0))

  expectation("JSON stdout is one complete array with correct fields, escaping, types and input ordering.") {
    fixture { dir =>
      val result = command(dir, "export", source(dir), "--format", "json")
      assertEquals(result.code, 0)
      assertEquals(result.out.parseJson[List[ExportRow]], rows)
      assert(!result.out.contains("\u001b"))
    }
  }
  expectation("Empty JSON exports produce an empty array and succeed.") {
    fixture { dir =>
      Files.writeString(dir.resolve("stock.json"), "[]")
      val result = command(dir, "export", source(dir), "--format", "json")
      assertEquals(result.code, 0)
      assertEquals(result.out.parseJson[List[ExportRow]], Nil)
    }
  }
  expectation("JSON file export writes the payload without emitting data or completion chatter on stdout.") {
    fixture { dir =>
      val dest = dir.resolve("nightly export.json")
      val result = command(dir, "export", source(dir), "--format", "json", "--output", dest.toString)
      assertEquals(result.code, 0)
      assertEquals(result.out, "")
      assertEquals(Files.readString(dest).parseJson[List[ExportRow]], rows)
    }
  }
  expectation("Explicit force replaces an existing JSON file correctly.") {
    fixture { dir =>
      val dest = dir.resolve("export.json")
      Files.writeString(dest, "old")
      val result = command(dir, "export", source(dir), "--format", "json", "--output", dest.toString, "--force")
      assertEquals(result.code, 0)
      assertEquals(result.out, "")
      assertEquals(Files.readString(dest).parseJson[List[ExportRow]], rows)
    }
  }
  expectation("Unattended overwrite refuses promptly without prompting or modifying the file.") {
    fixture { dir =>
      val dest = dir.resolve("export.json")
      Files.writeString(dest, "original")
      // Stdin is a pipe containing another job's input, not an interactive terminal.
      val result = launch(dir, List("export", source(dir), "--format", "json", "--output", dest.toString), "y\n")
      assertEquals(result.code, 1)
      assertEquals(result.out, "")
      assert(result.err.contains("--force"), result.err)
      assert(!result.err.contains("[y/N]"), "An unattended invocation must not prompt")
      assertEquals(Files.readString(dest), "original")
    }
  }
  expectation("Invalid output formats and incomplete options report usage failure before modifying an output.") {
    fixture { dir =>
      val dest = dir.resolve("export.json")
      Files.writeString(dest, "original")
      List(List("--format", "yaml"), List("--format"), List("--format", "json", "--output"), List("--form", "json"))
        .foreach { flags =>
          val args = List("export", source(dir), "--output", dest.toString, "--force") ++ flags
          val result = launch(dir, args, "")
          assertEquals(result.code, 2)
          assertEquals(result.out, "")
          assert(result.err.contains("--"), result.err)
          assert(!result.err.contains("Exception"), result.err)
          assertEquals(Files.readString(dest), "original")
        }
    }
  }
  expectation(
    "Missing or malformed sources and unwritable destinations fail with useful stderr and no stdout payload."
  ) {
    fixture { dir =>
      val missing = dir.resolve("missing.json")
      val malformed = dir.resolve("malformed.json")
      Files.writeString(malformed, "{broken")
      for path <- List(missing, malformed) do
        val dest = dir.resolve("untouched.json")
        Files.writeString(dest, "original")
        val result = command(dir, "export", path.toString, "--format", "json", "--output", dest.toString, "--force")
        assertEquals(result.code, 1)
        assertEquals(result.out, "")
        assert(result.err.contains(path.toString), result.err)
        assert(!result.err.contains("\tat "), result.err)
        assertEquals(Files.readString(dest), "original")
      val destination = dir.resolve("absent").resolve("export.json")
      val result = command(dir, "export", source(dir), "--format", "json", "--output", destination.toString)
      assertEquals(result.code, 1)
      assertEquals(result.out, "")
      assert(result.err.contains(destination.toString), result.err)
    }
  }
  expectation("Existing table stdout and file-output contracts remain compatible.") {
    fixture { dir =>
      // Existing snapshots are from uncomplicated operator data.
      val simple = List(ExportRow("b", "Bolts", 3), ExportRow("a", "Apron", 0))
      Files.writeString(dir.resolve("stock.json"), simple.toJson)
      val table = "ID\tNAME\tQUANTITY\nb\tBolts\t3\na\tApron\t0\n"
      for flags <- List(Nil, List("--format", "table"), List("--no-color")) do
        val result = launch(dir, List("export", source(dir)) ++ flags, "")
        assertEquals(result.code, 0)
        assertEquals(result.out, "Stock export (2 items)\n" + table)
        assertEquals(result.err, "")
      val dest = dir.resolve("table.txt")
      val result = command(dir, "export", source(dir), "--output", dest.toString)
      assertEquals(result.code, 0)
      assertEquals(Files.readString(dest), table)
      assertEquals(result.out, s"Wrote 2 items to $dest\n")
    }
  }
  expectation("Help advertises the JSON option and version remains available without reading input.") {
    fixture { dir =>
      for args <- List(List("--help"), List("export", "--help")) do
        val result = launch(dir, args, "")
        assertEquals(result.code, 0)
        assert(result.out.contains("--format") && result.out.contains("json"), result.out)
        assertEquals(result.err, "")
      val version = command(dir, "--version")
      assertEquals(version.code, 0)
      assertEquals(version.out, "stockdesk 1.4.0\n")
    }
  }

  private def source(dir: Path): String = dir.resolve("stock.json").toString

  private def fixture(body: Path => Unit): Unit =
    val dir = Files.createTempDirectory("cli-export-grade-")
    try
      Files.writeString(dir.resolve("stock.json"), rows.toJson)
      body(dir)
    finally
      val paths = Files.walk(dir)
      try paths.iterator().asScala.toList.reverse.foreach(Files.deleteIfExists)
      finally paths.close()

  private def command(dir: Path, args: String*): Result = launch(dir, args.toList, "")

  private def launch(dir: Path, args: List[String], input: String): Result =
    val stdout = Files.createTempFile(dir, "stdout-", ".txt")
    val stderr = Files.createTempFile(dir, "stderr-", ".txt")
    val javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString
    val builder = new ProcessBuilder(
      (List(javaExecutable, "-cp", System.getProperty("java.class.path"), "inventory.InventoryCli") ++ args).asJava
    )
    builder.redirectOutput(stdout.toFile).redirectError(stderr.toFile)
    builder.environment().put("NO_COLOR", "1")
    builder.environment().put("TERM", "dumb")
    val process = builder.start()
    try
      val stdin = process.getOutputStream
      try stdin.write(input.getBytes(java.nio.charset.StandardCharsets.UTF_8))
      finally stdin.close()
      assert(process.waitFor(5, TimeUnit.SECONDS), "CLI did not finish within five seconds")
      Result(process.exitValue(), Files.readString(stdout), Files.readString(stderr))
    finally
      if process.isAlive then
        val handles = process.descendants()
        val children = try handles.iterator().asScala.toList
        finally handles.close()
        children.reverse.foreach(_.destroyForcibly())
        process.destroyForcibly()
        assert(process.waitFor(5, TimeUnit.SECONDS), "Could not reap CLI process")
        children.foreach(_.onExit().get(5, TimeUnit.SECONDS))
      process.getInputStream.close()
      process.getErrorStream.close()
      process.getOutputStream.close()
}

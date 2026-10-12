package inventory

import java.io.{BufferedReader, ByteArrayOutputStream, PrintStream, StringReader}
import java.nio.file.Files

class InventorySuite extends munit.FunSuite:
  test("table export retains its human banner, columns and ordering") {
    val source = Files.createTempFile("stock-", ".json")
    try
      Files.writeString(source, """[{"id":"b","name":"Bolts","quantity":3},{"id":"a","name":"Apron","quantity":0}]""")
      val out = new ByteArrayOutputStream
      val err = new ByteArrayOutputStream
      val code = InventoryCli.run(
        List("export", source.toString),
        new BufferedReader(new StringReader("")),
        new PrintStream(out),
        new PrintStream(err),
        false
      )
      assertEquals(code, 0)
      assertEquals(out.toString("UTF-8"), "Stock export (2 items)\nID\tNAME\tQUANTITY\nb\tBolts\t3\na\tApron\t0\n")
      assertEquals(err.toString("UTF-8"), "")
    finally Files.deleteIfExists(source)
  }
  test("table renderer supports empty inventories") {
    assertEquals(InventoryCli.renderTable(Nil), "ID\tNAME\tQUANTITY\n")
  }
  test("help and version work without a source file") {
    for args <- List(List("--help"), List("export", "--help"), List("--version")) do
      val out = new ByteArrayOutputStream
      assertEquals(
        InventoryCli.run(args, new BufferedReader(new StringReader("")), new PrintStream(out), System.err, false),
        0
      )
      assert(out.size > 0)
  }

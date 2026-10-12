package inventory

import ba.sake.tupson.*
import java.io.{BufferedReader, PrintStream}
import java.nio.file.{Files, Path}

case class Stock(id: String, name: String, quantity: Int) derives JsonRW
case class Options(source: Path, output: Option[Path], force: Boolean, noColor: Boolean)

object InventoryCli {
  val version = "stockdesk 1.4.0"
  val help = """Usage: stockdesk export SOURCE [--output PATH] [--force] [--no-color]
    |Export stock records as a table.
    |  --output PATH  Save the table to a file
    |  --force        Replace an existing output without confirmation
    |  --no-color     Disable terminal decoration
    |  --help         Show this help
    |  --version      Show version
    |Example: stockdesk export stock.json --output stock.txt --force
    |""".stripMargin

  def main(args: Array[String]): Unit =
    val input = new BufferedReader(new java.io.InputStreamReader(System.in))
    sys.exit(run(args.toList, input, System.out, System.err, System.console() != null))

  def run(args: List[String], input: BufferedReader, out: PrintStream, err: PrintStream, terminal: Boolean): Int =
    if args == List("--help") || args == List("export", "--help") || args.isEmpty then
      out.print(help)
      return 0
    if args == List("--version") then
      out.println(version)
      return 0
    parse(args) match
      case Left(message) =>
        err.println(s"stockdesk: $message. See --help.")
        2
      case Right(options) =>
        try
          val rows = Files.readString(options.source).parseJson[List[Stock]]
          val table = renderTable(rows)
          options.output match
            case Some(path) =>
              if Files.exists(path) && !options.force then
                err.print(s"Replace $path? [y/N] ")
                err.flush()
                if Option(input.readLine()).forall(_.trim.toLowerCase != "y") then
                  err.println("Export cancelled; use --force to replace the file.")
                  return 1
              Files.writeString(path, table)
              out.println(s"Wrote ${rows.size} items to $path")
            case None =>
              val banner = s"Stock export (${rows.size} items)"
              out.println(
                if terminal && !options.noColor && !sys.env.contains("NO_COLOR") && sys.env.get("TERM") != Some("dumb")
                then s"\u001b[1m$banner\u001b[0m"
                else banner
              )
              out.print(table)
          0
        catch
          case error: Exception =>
            err.println(s"stockdesk: ${error.getMessage}")
            1

  def renderTable(rows: List[Stock]): String =
    "ID\tNAME\tQUANTITY\n" + rows.map(row => s"${row.id}\t${row.name}\t${row.quantity}\n").mkString

  private def parse(args: List[String]): Either[String, Options] =
    args match
      case "export" :: source :: tail if (!source.startsWith("--")) =>
        var output: Option[Path] = None
        var force = false
        var noColor = false
        var remaining = tail
        while remaining.nonEmpty do
          remaining match
            case "--output" :: value :: rest if (!value.startsWith("--")) =>
              output = Some(Path.of(value)); remaining = rest
            case "--force" :: rest    => force = true; remaining = rest
            case "--no-color" :: rest => noColor = true; remaining = rest
            case option :: _          => return Left(s"Unexpected argument $option")
            case Nil                  => ()
        Right(Options(Path.of(source), output, force, noColor))
      case _ => Left("Expected export SOURCE")
}

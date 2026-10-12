package evaluation

import ba.sake.tupson.*

case class GraphSummary(nodes: Int, edges: Int, nodesInCycles: Int, orphans: Int) derives JsonRW
case class GraphCycle(id: String, members: List[String], internalEdges: Int) derives JsonRW
case class GraphSurface(node: String, fanIn: Int, fanOut: Int, ports: Double, mutPorts: Double) derives JsonRW
case class GraphScope(
    summary: GraphSummary,
    cycles: List[GraphCycle],
    surface: List[GraphSurface],
    orphans: List[String]
) derives JsonRW
case class GraphSnapshot(packages: GraphScope, files: Option[GraphScope]) derives JsonRW
case class HistoryCycles(count: Int, nodes: Int) derives JsonRW
case class HistoryScope(cycles: HistoryCycles) derives JsonRW
case class GraphHistory(commit: String, packages: HistoryScope, files: Option[HistoryScope]) derives JsonRW

object GraphEvidence {
  val submission = os.Path(sys.env("SUBMISSION_DIR"))
  val config = """projects:
    |  app:
    |    root: .
    |    source: semanticdb
    |    inputs: [target/classes]
    |    include: [app]
    |    skip-tests: true
    |""".stripMargin

  lazy val fresh: GraphSnapshot = rebuild(submission)

  def read(path: os.Path): GraphSnapshot = os.read(path).parseJson[GraphSnapshot]

  def rebuild(source: os.Path): GraphSnapshot =
    val scratch = os.temp.dir(prefix = "codeps-grade-", dir = os.pwd / "tests/tmp")
    try
      os.copy(source / "project.scala", scratch / "project.scala")
      os.copy(source / "src/main", scratch / "src/main", createFolders = true)
      os.write(scratch / ".codeps/config.yaml", config, createFolders = true)
      command(scratch, "git", "init", "--quiet")
      command(
        scratch,
        "scala-cli",
        "compile",
        "project.scala",
        "src/main",
        "--semanticdb",
        "--compilation-output",
        "target/classes",
        "--server=false"
      )
      command(
        scratch,
        "codeps",
        "status",
        "--project",
        "app",
        "--commit",
        "protected-fresh-build",
        "--generated-at",
        "2026-01-01T00:00:00Z"
      )
      read(scratch / ".codeps/out/app/report.json")
    finally os.remove.all(scratch)

  def command(cwd: os.Path, args: String*): String =
    val result = os.proc(args).call(cwd = cwd, check = false, mergeErrIntoOut = true, timeout = 180000)
    require(result.exitCode == 0, s"${args.mkString(" ")} failed:\n${result.out.text()}")
    result.out.text()
}

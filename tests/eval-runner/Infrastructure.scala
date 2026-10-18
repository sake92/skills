import ba.sake.tupson.*
import java.time.Instant

def writeJson[T: JsonRW](path: os.Path, value: T): Unit =
  os.write.over(path, value.toJson(sort = false), createFolders = true)

def copyTree(
    from: os.Path,
    to: os.Path,
    excluded: Set[String] = Set.empty
): Unit =
  os.makeDir.all(to)
  os.walk(from)
    .foreach: source =>
      val relative = source.relativeTo(from)
      if !relative.segments
          .exists(excluded) && !source.last.endsWith(".semanticdb")
      then
        val target = to / relative
        if os.isDir(source) then os.makeDir.all(target)
        else os.copy(source, target, replaceExisting = true, createFolders = true)

/** Preserve repository-relative Scala CLI file imports in each isolated grader snapshot. */
def snapshotGradingSources(root: os.Path, sources: List[os.Path], target: os.Path): List[os.Path] =
  val excluded = Set(".git", ".scala-build", ".bsp")
  val visited = scala.collection.mutable.Set.empty[os.Path]
  val directive = "(?m)^\\s*//>\\s+using\\s+file\\s+([^\\r\\n]+)".r
  val argument = "\"([^\"]+)\"|'([^']+)'|(\\S+)".r
  def snapshotFile(source: os.Path): Unit =
    require(source.startsWith(root), s"Grader source must be inside the repository: $source")
    if visited.add(source) then
      os.copy(source, target / source.relativeTo(root), replaceExisting = true, createFolders = true)
      if source.ext == "scala" then
        directive
          .findAllMatchIn(os.read(source))
          .foreach: line =>
            argument
              .findAllMatchIn(line.group(1))
              .foreach: value =>
                val path = (1 to 3).iterator.map(value.group).find(_ != null).get
                val dependency = os.Path(path, source / os.up)
                if os.isDir(dependency) then snapshotDirectory(dependency)
                else snapshotFile(dependency)
  def snapshotDirectory(source: os.Path): Unit =
    os.walk(source)
      .filter(os.isFile)
      .filterNot(_.relativeTo(source).segments.exists(excluded))
      .foreach(snapshotFile)
  sources.foreach(snapshotDirectory)
  sources.map(source => target / source.relativeTo(root))

def runProcess(
    label: String,
    command: Seq[String],
    cwd: os.Path,
    stdout: os.Path,
    stderr: Option[os.Path] = None,
    env: Map[String, String] = Map.empty,
    stdin: Option[String] = None,
    timeoutMs: Long
): ProcessResult =
  os.makeDir.all(stdout / os.up)
  stderr.foreach(path => os.makeDir.all(path / os.up))
  val started = Instant.now
  val startedNanos = System.nanoTime()
  System.err.println(s"[$label] starting")
  val processInput: os.ProcessInput =
    stdin.fold[os.ProcessInput](os.Pipe)(identity)
  val stderrOutput: os.ProcessOutput =
    stderr.fold[os.ProcessOutput](os.Inherit)(identity)
  val result = os
    .proc(command)
    .call(
      cwd = cwd,
      env = env,
      stdin = processInput,
      stdout = stdout,
      stderr = stderrOutput,
      mergeErrIntoOut = stderr.isEmpty,
      timeout = timeoutMs,
      check = false
    )
  val durationMs = (System.nanoTime() - startedNanos) / 1_000_000
  System.err.println(
    s"[$label] exit=${result.exitCode} duration=${durationMs}ms log=$stdout"
  )
  ProcessResult(
    result.exitCode,
    durationMs,
    started.toString,
    Instant.now.toString
  )

def commandOutput(command: Seq[String], cwd: os.Path): String =
  val result =
    os.proc(command).call(cwd = cwd, mergeErrIntoOut = true, check = false)
  if result.exitCode == 0 then result.out.text().trim
  else
    throw IllegalStateException(
      s"Command failed (${result.exitCode}): ${command.mkString(" ")}\n${result.out.text()}"
    )

def checked(result: ProcessResult, label: String): Unit =
  if result.exit_code != 0 then
    throw IllegalStateException(
      s"$label failed with exit code ${result.exit_code}"
    )

def piCommand(
    settings: PiConfig,
    prompt: String,
    skill: Option[os.Path],
    tools: Option[String],
    systemPrompt: Option[String] = None
): Seq[String] =
  Seq(
    "pi",
    "--provider",
    "openai",
    "--model",
    settings.model,
    "--thinking",
    settings.reasoning,
    "--mode",
    "json",
    "--print",
    "--no-session",
    "--no-extensions",
    "--no-skills",
    "--no-context-files",
    "--no-prompt-templates",
    "--no-themes",
    "--no-mcp",
    "--approve"
  ) ++
    tools.fold(Seq("--no-tools"))(value => Seq("--tools", value)) ++
    skill.toSeq.flatMap(path => Seq("--skill", path.toString)) ++
    systemPrompt.toSeq.flatMap(value => Seq("--system-prompt", value)) ++
    Seq(prompt)

def skillDescription(skillFile: os.Path): String =
  os.read
    .lines(skillFile)
    .collectFirst:
      case line if line.startsWith("description:") =>
        line.stripPrefix("description:").trim.stripPrefix("'").stripSuffix("'")
    .getOrElse(
      throw IllegalStateException(s"Missing description in $skillFile")
    )

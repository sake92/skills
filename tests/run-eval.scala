//> using scala 3.9.0
//> using dep com.lihaoyi::mainargs:0.7.8
//> using dep com.lihaoyi::os-lib:0.11.8
//> using dep ba.sake::tupson:0.31.0

import java.nio.file.Paths
import java.time.Instant
import java.time.format.DateTimeFormatter
import ba.sake.tupson.{*, given}
import mainargs.{Flag, ParserForMethods, arg}
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, blocking}

case class Config(
    casePath: String = "pragmatic-architecture/customer-repository",
    provider: String = "openai",
    model: String = "gpt-6-luna",
    reasoning: Option[String] = None,
    judgeProvider: Option[String] = None,
    judgeModel: Option[String] = None,
    runJudge: Boolean = true
)

case class Result(exit_code: Int, duration_ms: Long) derives JsonRW
case class SideResult(candidate: Result, grading: Result, judge: Option[Result]) derives JsonRW
case class RunSummary(
    `case`: String,
    skill: String,
    provider: String,
    model: String,
    reasoning: Option[String],
    judge_provider: Option[String],
    judge_model: Option[String],
    git_commit: String,
    codex_version: String,
    finished_at: String,
    results: Map[String, SideResult]
) derives JsonRW

given ExecutionContext = ExecutionContext.global

def copyTree(from: os.Path, to: os.Path, excluded: Set[String] = Set.empty): Unit =
  os.makeDir.all(to)
  os.walk(from).foreach: source =>
    val relative = source.relativeTo(from)
    if !relative.segments.exists(excluded) then
      val target = to / relative
      if os.isDir(source) then os.makeDir.all(target)
      else os.copy(source, target, replaceExisting = true, createFolders = true)

def run(
    label: String,
    command: Seq[String],
    cwd: os.Path,
    log: os.Path,
    env: Map[String, String] = Map.empty,
    stdin: Option[String] = None
): Result =
  os.makeDir.all(log / os.up)
  val started = System.nanoTime()
  println(s"[$label] starting")
  val processInput: os.ProcessInput = stdin.fold[os.ProcessInput](os.Pipe)(identity)
  val result = os.proc(command).call(
    cwd = cwd,
    env = env,
    stdin = processInput,
    stdout = log,
    mergeErrIntoOut = true,
    check = false
  )
  val durationMs = (System.nanoTime() - started) / 1_000_000
  println(s"[$label] exit=${result.exitCode} duration=${durationMs}ms log=$log")
  Result(result.exitCode, durationMs)

def output(command: Seq[String], cwd: os.Path): String =
  val result = os.proc(command).call(cwd = cwd, mergeErrIntoOut = true, check = false)
  if result.exitCode == 0 then result.out.text().trim else "unknown"

def safe(value: String): String = value.replaceAll("[^A-Za-z0-9._-]", "-")

def runEval(config: Config): Unit =
  val providers = Set("ollama", "lmstudio", "openai")
  val judgeProvider = config.judgeProvider.getOrElse(config.provider)
  val judgeModel = config.judgeModel.getOrElse(config.model)
  require(providers(config.provider), "provider must be ollama, lmstudio, or openai")
  require(providers(judgeProvider), "judge provider must be ollama, lmstudio, or openai")

  val root = os.pwd
  val caseRoot =
    val supplied = os.Path(config.casePath, root)
    if Paths.get(config.casePath).isAbsolute || supplied.startsWith(root / "tests") then supplied
    else root / "tests" / os.RelPath(config.casePath)
  require(os.isDir(caseRoot / "starter"), s"Missing starter directory: $caseRoot/starter")
  require(os.isDir(caseRoot / "grading"), s"Missing grading directory: $caseRoot/grading")
  require(os.isFile(caseRoot / "prompt.md"), s"Missing prompt: $caseRoot/prompt.md")

  val skillName = (caseRoot / os.up).last
  val skillRoot = root / "skills" / skillName
  require(os.isFile(skillRoot / "SKILL.md"), s"Missing skill: $skillRoot/SKILL.md")

  val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(java.time.ZoneOffset.UTC).format(Instant.now)
  val runRoot = root / "tests/tmp" / caseRoot.last / s"${safe(config.model)}-$timestamp"
  val withWork = runRoot / "with-skill/work"
  val withoutWork = runRoot / "without-skill/work"
  val prompt = os.read(caseRoot / "prompt.md")

  copyTree(caseRoot / "starter", withWork)
  copyTree(caseRoot / "starter", withoutWork)
  copyTree(skillRoot, withWork / ".agents/skills" / skillName)

  Seq(withWork, withoutWork).foreach: work =>
    val log = runRoot / s"setup-${(work / os.up).last}.log"
    run("git-init", Seq("git", "init", "--quiet"), work, log)
    run("git-add", Seq("git", "add", "."), work, log)
    run(
      "git-commit",
      Seq("git", "-c", "user.name=skill-eval", "-c", "user.email=skill-eval@example.invalid", "commit", "--quiet", "-m", "starter"),
      work,
      log
    )

  def codexCommand(
      work: os.Path,
      lastMessage: os.Path,
      sandbox: String,
      provider: String,
      model: String,
      judge: Boolean
  ): Seq[String] =
    Seq("codex", "-C", work.toString, "--enable", "skip_host_skill_discovery", "-a", "never") ++
      (if provider == "openai" then Nil else Seq("--oss", "--local-provider", provider)) ++
      Seq("-m", model, "-s", sandbox, "exec", "--ephemeral", "--ignore-user-config") ++
      (if judge then Seq("--skip-git-repo-check") else Nil) ++
      config.reasoning.toSeq.flatMap(level => Seq("-c", s"model_reasoning_effort=\"$level\"")) ++
      Seq("-o", lastMessage.toString, "-")

  def candidate(side: String, work: os.Path): Future[Result] = Future(blocking:
    val results = runRoot / side / "results"
    run(
      s"$side candidate",
      codexCommand(work, results / "last-message.txt", "workspace-write", config.provider, config.model, judge = false),
      root,
      results / "codex.log",
      stdin = Some(prompt)
    )
  )

  val candidateResults = Await.result(
    Future.sequence(Seq(candidate("with-skill", withWork), candidate("without-skill", withoutWork))),
    Duration.Inf
  )

  def grade(side: String, work: os.Path): Future[Result] = Future(blocking:
    run(
      s"$side grade",
      Seq("scala-cli", "test", work.toString, (caseRoot / "grading").toString, "--server=false"),
      root,
      runRoot / side / "results/grading.log",
      env = Map("SUBMISSION_DIR" -> work.toString)
    )
  )

  val gradeResults = Await.result(
    Future.sequence(Seq(grade("with-skill", withWork), grade("without-skill", withoutWork))),
    Duration.Inf
  )

  def judge(side: String, work: os.Path): Future[Result] = Future(blocking:
    val judgeWork = runRoot / side / "judge-work"
    copyTree(work, judgeWork, Set(".agents", ".git", ".scala-build", ".bsp"))
    val rubric = os.read(caseRoot / "grading/judge.md")
    val judgePrompt = s"Read the submitted source in this directory.\n\n$rubric"
    val results = runRoot / side / "results"
    run(
      s"$side judge",
      codexCommand(judgeWork, results / "judge-output.txt", "read-only", judgeProvider, judgeModel, judge = true),
      root,
      results / "judge.log",
      stdin = Some(judgePrompt)
    )
  )

  val judgeResults =
    if config.runJudge then
      Await.result(
        Future.sequence(Seq(judge("with-skill", withWork), judge("without-skill", withoutWork))),
        Duration.Inf
      ).map(Some(_))
    else Seq(None, None)

  val sides = Seq("with-skill", "without-skill")
  val results = sides.indices.map: index =>
    sides(index) -> SideResult(candidateResults(index), gradeResults(index), judgeResults(index))
  val commit = output(Seq("git", "rev-parse", "HEAD"), root)
  val codexVersion = output(Seq("codex", "--version"), root)
  val finishedAt = Instant.now.toString

  val summary = RunSummary(
    `case` = caseRoot.relativeTo(root).toString,
    skill = skillName,
    provider = config.provider,
    model = config.model,
    reasoning = config.reasoning,
    judge_provider = Option.when(config.runJudge)(judgeProvider),
    judge_model = Option.when(config.runJudge)(judgeModel),
    git_commit = commit,
    codex_version = codexVersion,
    finished_at = finishedAt,
    results = results.toMap
  )
  os.write.over(runRoot / "run.json", summary.toJson(sort = false), createFolders = true)

  println(s"Results: $runRoot")
  results.foreach: (name, result) =>
    println(s"$name: candidate=${result.candidate.exit_code}, grading=${result.grading.exit_code}, judge=${result.judge.map(_.exit_code).getOrElse("skipped")}")

  if candidateResults.exists(_.exit_code != 0) || judgeResults.flatten.exists(_.exit_code != 0) then sys.exit(1)

object RunEvalCli:
  @mainargs.main
  def run(
      @arg(name = "case", doc = "Case below tests/") casePath: String = "pragmatic-architecture/customer-repository",
      @arg(doc = "ollama, lmstudio, or openai") provider: String = "openai",
      @arg(doc = "Model passed to Codex") model: String = "gpt-6-luna",
      @arg(doc = "Optional Codex reasoning effort") reasoning: Option[String],
      @arg(doc = "Optional separate provider for the qualitative judge") judgeProvider: Option[String],
      @arg(doc = "Optional separate model for the qualitative judge") judgeModel: Option[String],
      @arg(doc = "Run candidates and deterministic grading only") skipJudge: Flag
  ): Unit =
    runEval(Config(casePath, provider, model, reasoning, judgeProvider, judgeModel, !skipJudge.value))

  def main(args: Array[String]): Unit =
    ParserForMethods(this).runOrExit(args.toIndexedSeq)

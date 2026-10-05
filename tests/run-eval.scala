//> using scala 3.9.0

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.time.Instant
import java.time.format.DateTimeFormatter
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, blocking}
import scala.jdk.CollectionConverters.*

case class Config(
    casePath: String = "pragmatic-architecture/customer-repository",
    provider: String = "ollama",
    model: String = "qwen2.5-coder:3b",
    reasoning: Option[String] = None,
    judgeProvider: Option[String] = None,
    judgeModel: Option[String] = None,
    runJudge: Boolean = true
)

case class Result(exitCode: Int, durationMs: Long)
case class SideResult(candidate: Result, grade: Result, judge: Option[Result])

given ExecutionContext = ExecutionContext.global

def usage(): Unit =
  println(
    """Usage: scala tests/run-eval.scala -- [options]
      |
      |Options:
      |  --case PATH          Case below tests/ (default: pragmatic-architecture/customer-repository)
      |  --provider NAME      ollama, lmstudio, or openai (default: ollama)
      |  --model NAME         Model passed to Codex (default: qwen2.5-coder:3b)
      |  --reasoning LEVEL    Optional Codex reasoning effort
      |  --judge-provider P   Optional separate provider for the qualitative judge
      |  --judge-model NAME   Optional separate model for the qualitative judge
      |  --skip-judge         Run candidates and deterministic grading only
      |  --help               Show this help
      |""".stripMargin
  )

def parse(args: List[String], config: Config = Config()): Config = args match
  case Nil => config
  case "--case" :: value :: rest => parse(rest, config.copy(casePath = value))
  case "--provider" :: value :: rest => parse(rest, config.copy(provider = value))
  case "--model" :: value :: rest => parse(rest, config.copy(model = value))
  case "--reasoning" :: value :: rest => parse(rest, config.copy(reasoning = Some(value)))
  case "--judge-provider" :: value :: rest => parse(rest, config.copy(judgeProvider = Some(value)))
  case "--judge-model" :: value :: rest => parse(rest, config.copy(judgeModel = Some(value)))
  case "--skip-judge" :: rest => parse(rest, config.copy(runJudge = false))
  case "--help" :: _ => usage(); sys.exit(0)
  case option :: _ => throw IllegalArgumentException(s"Unknown or incomplete option: $option")

def copyTree(from: Path, to: Path, excluded: Set[String] = Set.empty): Unit =
  val stream = Files.walk(from)
  try
    stream.iterator.asScala.foreach: source =>
      val relative = from.relativize(source)
      if !relative.iterator.asScala.exists(part => excluded(part.toString)) then
        val target = to.resolve(relative)
        if Files.isDirectory(source) then Files.createDirectories(target)
        else Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
  finally stream.close()

def run(
    label: String,
    command: Seq[String],
    cwd: Path,
    log: Path,
    env: Map[String, String] = Map.empty,
    stdin: Option[String] = None
): Result =
  Files.createDirectories(log.getParent)
  val builder = ProcessBuilder(command*)
    .directory(cwd.toFile)
    .redirectErrorStream(true)
    .redirectOutput(log.toFile)
  env.foreach((key, value) => builder.environment().put(key, value))
  val started = System.nanoTime()
  println(s"[$label] starting")
  val process = builder.start()
  stdin.foreach: text =>
    val output = process.getOutputStream
    output.write(text.getBytes(StandardCharsets.UTF_8))
    output.close()
  val exitCode = process.waitFor()
  val durationMs = (System.nanoTime() - started) / 1_000_000
  println(s"[$label] exit=$exitCode duration=${durationMs}ms log=$log")
  Result(exitCode, durationMs)

def output(command: Seq[String], cwd: Path): String =
  val process = ProcessBuilder(command*).directory(cwd.toFile).redirectErrorStream(true).start()
  val text = String(process.getInputStream.readAllBytes(), StandardCharsets.UTF_8).trim
  if process.waitFor() == 0 then text else "unknown"

def json(value: String): String =
  "\"" + value.flatMap:
    case '\\' => "\\\\"
    case '"'  => "\\\""
    case '\n' => "\\n"
    case '\r' => "\\r"
    case '\t' => "\\t"
    case char => char.toString
  + "\""

def safe(value: String): String = value.replaceAll("[^A-Za-z0-9._-]", "-")

@main def runEval(args: String*): Unit =
  val config = parse(args.toList)
  val providers = Set("ollama", "lmstudio", "openai")
  val judgeProvider = config.judgeProvider.getOrElse(config.provider)
  val judgeModel = config.judgeModel.getOrElse(config.model)
  require(providers(config.provider), "provider must be ollama, lmstudio, or openai")
  require(providers(judgeProvider), "judge provider must be ollama, lmstudio, or openai")

  val root = Paths.get("").toAbsolutePath.normalize
  val caseRoot =
    val supplied = Paths.get(config.casePath)
    if supplied.isAbsolute then supplied.normalize
    else if config.casePath.startsWith("tests/") then root.resolve(supplied).normalize
    else root.resolve("tests").resolve(supplied).normalize
  require(Files.isDirectory(caseRoot.resolve("starter")), s"Missing starter directory: $caseRoot/starter")
  require(Files.isDirectory(caseRoot.resolve("grading")), s"Missing grading directory: $caseRoot/grading")
  require(Files.isRegularFile(caseRoot.resolve("prompt.md")), s"Missing prompt: $caseRoot/prompt.md")

  val skillName = caseRoot.getParent.getFileName.toString
  val skillRoot = root.resolve("skills").resolve(skillName)
  require(Files.isRegularFile(skillRoot.resolve("SKILL.md")), s"Missing skill: $skillRoot/SKILL.md")

  val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(java.time.ZoneOffset.UTC).format(Instant.now)
  val runRoot = root.resolve("tests/tmp").resolve(caseRoot.getFileName.toString).resolve(s"${safe(config.model)}-$timestamp")
  val withWork = runRoot.resolve("with-skill/work")
  val withoutWork = runRoot.resolve("without-skill/work")
  val prompt = Files.readString(caseRoot.resolve("prompt.md"))

  copyTree(caseRoot.resolve("starter"), withWork)
  copyTree(caseRoot.resolve("starter"), withoutWork)
  copyTree(skillRoot, withWork.resolve(".agents/skills").resolve(skillName))

  Seq(withWork, withoutWork).foreach: work =>
    run("git-init", Seq("git", "init", "--quiet"), work, runRoot.resolve(s"setup-${work.getParent.getFileName}.log"))
    run("git-add", Seq("git", "add", "."), work, runRoot.resolve(s"setup-${work.getParent.getFileName}.log"))
    run(
      "git-commit",
      Seq("git", "-c", "user.name=skill-eval", "-c", "user.email=skill-eval@example.invalid", "commit", "--quiet", "-m", "starter"),
      work,
      runRoot.resolve(s"setup-${work.getParent.getFileName}.log")
    )

  def codexCommand(
      work: Path,
      lastMessage: Path,
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

  def candidate(side: String, work: Path): Future[Result] = Future(blocking:
    val results = runRoot.resolve(side).resolve("results")
    run(
      s"$side candidate",
      codexCommand(work, results.resolve("last-message.txt"), "workspace-write", config.provider, config.model, judge = false),
      root,
      results.resolve("codex.log"),
      stdin = Some(prompt)
    )
  )

  val candidateResults = Await.result(
    Future.sequence(Seq(candidate("with-skill", withWork), candidate("without-skill", withoutWork))),
    Duration.Inf
  )

  def grade(side: String, work: Path): Future[Result] = Future(blocking:
    run(
      s"$side grade",
      Seq("scala-cli", "test", work.toString, caseRoot.resolve("grading").toString, "--server=false"),
      root,
      runRoot.resolve(side).resolve("results/grading.log"),
      env = Map("SUBMISSION_DIR" -> work.toString)
    )
  )

  val gradeResults = Await.result(
    Future.sequence(Seq(grade("with-skill", withWork), grade("without-skill", withoutWork))),
    Duration.Inf
  )

  def judge(side: String, work: Path): Future[Result] = Future(blocking:
    val judgeWork = runRoot.resolve(side).resolve("judge-work")
    copyTree(work, judgeWork, Set(".agents", ".git", ".scala-build", ".bsp"))
    val rubric = Files.readString(caseRoot.resolve("grading/judge.md"))
    val judgePrompt = s"Read the submitted source in this directory.\n\n$rubric"
    val results = runRoot.resolve(side).resolve("results")
    run(
      s"$side judge",
      codexCommand(judgeWork, results.resolve("judge-output.txt"), "read-only", judgeProvider, judgeModel, judge = true),
      root,
      results.resolve("judge.log"),
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

  def resultJson(result: Result): String =
    s"{\"exit_code\":${result.exitCode},\"duration_ms\":${result.durationMs}}"

  val sidesJson = results.map: (name, result) =>
    val judgeJson = result.judge.map(resultJson).getOrElse("null")
    s"${json(name)}:{\"candidate\":${resultJson(result.candidate)},\"grading\":${resultJson(result.grade)},\"judge\":$judgeJson}"

  val summary =
    s"""{
       |  "case": ${json(root.relativize(caseRoot).toString)},
       |  "skill": ${json(skillName)},
       |  "provider": ${json(config.provider)},
       |  "model": ${json(config.model)},
       |  "reasoning": ${config.reasoning.map(json).getOrElse("null")},
       |  "judge_provider": ${if config.runJudge then json(judgeProvider) else "null"},
       |  "judge_model": ${if config.runJudge then json(judgeModel) else "null"},
       |  "git_commit": ${json(commit)},
       |  "codex_version": ${json(codexVersion)},
       |  "finished_at": ${json(finishedAt)},
       |  "results": {${sidesJson.mkString(",")}}
       |}
       |""".stripMargin
  Files.writeString(runRoot.resolve("run.json"), summary)

  println(s"Results: $runRoot")
  results.foreach: (name, result) =>
    println(s"$name: candidate=${result.candidate.exitCode}, grading=${result.grade.exitCode}, judge=${result.judge.map(_.exitCode).getOrElse("skipped")}")

  if candidateResults.exists(_.exitCode != 0) || judgeResults.flatten.exists(_.exitCode != 0) then sys.exit(1)

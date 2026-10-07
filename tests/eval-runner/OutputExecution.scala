def prepareOutputContexts(environment: OutputEnvironment, config: OutputConfig): List[RunContext] = {
  def runDirectory(caseOutput: os.Path, configuration: String, runNumber: Int): os.Path =
    val base = caseOutput / configuration
    if config.runs == 1 then base else base / s"run-$runNumber"

  for
    eval <- environment.evals
    configuration <- outputConfigurations(config.configuration)
    runNumber <- (1 to config.runs).toList
  yield
    val caseOutput = environment.iterationRoot / s"eval-${eval.id}"
    writeJson(
      caseOutput / "eval_metadata.json",
      EvalMetadata(eval.id, eval.name, eval.prompt, eval.expectations)
    )
    val runDir = runDirectory(caseOutput, configuration, runNumber)
    val work = runDir / "work"
    copyTree(
      environment.root / os.RelPath(eval.files.head),
      work,
      Set(".bsp", ".scala-build")
    )
    checked(
      runProcess(
        s"${eval.name} $configuration setup init",
        Seq("git", "init", "--quiet"),
        work,
        runDir / "setup-init.log",
        timeoutMs = environment.timeoutMs
      ),
      "init"
    )
    os.write.append(work / ".git/info/exclude", ".bsp/\n.scala-build/\n")
    List(
      "add" -> Seq("git", "add", "."),
      "commit" -> Seq(
        "git",
        "-c",
        "user.name=skill-eval",
        "-c",
        "user.email=skill-eval@example.invalid",
        "commit",
        "--quiet",
        "-m",
        "starter"
      )
    ).foreach: (step, command) =>
      checked(
        runProcess(
          s"${eval.name} $configuration setup $step",
          command,
          work,
          runDir / s"setup-$step.log",
          timeoutMs = environment.timeoutMs
        ),
        step
      )
    RunContext(eval, configuration, runNumber, runDir, work)
}

def runOutputCandidate(environment: OutputEnvironment, config: OutputConfig)(context: RunContext): Candidate =
  val outputs = context.runDir / "outputs"
  os.makeDir.all(outputs)
  val transcript = outputs / "transcript.jsonl"
  val prompt =
    if context.configuration == "with_skill" then s"""Execute this task:
         |- Use the ${environment.evalSet.skill_name} skill provided to you.
         |- Task:
         |
         |${context.eval.prompt}
         |""".stripMargin
    else context.eval.prompt
  val process = runProcess(
    s"${context.eval.name} ${context.configuration} candidate ${context.runNumber}",
    piCommand(
      PiConfig(config.model, config.reasoning),
      prompt,
      Option.when(context.configuration == "with_skill")(environment.skillRoot),
      Some("read,bash,edit,write")
    ),
    context.work,
    transcript,
    stderr = Some(outputs / "pi.stderr.log"),
    stdin = Some(""),
    timeoutMs = environment.timeoutMs
  )
  checked(
    runProcess(
      s"${context.eval.name} ${context.configuration} capture new files ${context.runNumber}",
      Seq("git", "add", "--intent-to-add", "."),
      context.work,
      outputs / "intent-to-add.log",
      timeoutMs = environment.timeoutMs
    ),
    "capture new files"
  )
  val patch = os
    .proc("git", "diff", "--binary", "HEAD")
    .call(cwd = context.work, check = false)
    .out
    .text()
  os.write.over(outputs / "submission.patch", patch)
  val events = parseEvents(transcript)
  os.write.over(outputs / "final-message.txt", finalAssistantText(events))
  val used = usage(events)
  val measured = metrics(
    context.work,
    outputs,
    events,
    Option.when(context.configuration == "with_skill")(environment.evalSet.skill_name)
  )
  writeJson(outputs / "metrics.json", measured)
  writeJson(
    context.runDir / "timing.json",
    Timing(
      used.input_tokens + used.output_tokens,
      process.duration_ms,
      process.duration_ms / 1000.0,
      process.started_at,
      process.finished_at,
      process.duration_ms / 1000.0
    )
  )
  Candidate(context, process, used, measured)

def validateOutputCandidates(candidates: List[Candidate]): Unit = {
  val failed = candidates.filter(_.process.exit_code != 0)
  failed.foreach: candidate =>
    System.err.println(
      s"Candidate failed: ${candidate.context.eval.name} ${candidate.context.configuration} " +
        s"#${candidate.context.runNumber}; see ${candidate.context.runDir / "outputs/pi.stderr.log"}"
    )
  require(failed.isEmpty, s"${failed.size} candidate runs failed; no benchmark was produced")

  val missedSkills = candidates.filter(candidate =>
    candidate.context.configuration == "with_skill" && !candidate.metrics.skill_invoked.contains(true)
  )
  missedSkills.foreach: candidate =>
    val name = candidate.context.eval.name
    val runNumber = candidate.context.runNumber
    val transcript = candidate.context.runDir / "outputs/transcript.jsonl"
    System.err.println(
      s"Invalid with-skill run: $name #$runNumber did not read SKILL.md; see $transcript"
    )
  require(
    missedSkills.isEmpty,
    s"${missedSkills.size} with-skill runs did not load SKILL.md; no benchmark was produced"
  )
}

private def outputConfigurations(configuration: Option[String]): List[String] = configuration match
  case None | Some("both")   => List("with_skill", "without_skill")
  case Some("with-skill")    => List("with_skill")
  case Some("without-skill") => List("without_skill")
  case Some(value)           =>
    throw IllegalArgumentException(
      s"configuration must be with-skill, without-skill, or both; got $value"
    )

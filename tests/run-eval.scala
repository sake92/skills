//> using scala 3.9.0
//> using dep com.lihaoyi::mainargs:0.7.8
//> using dep com.lihaoyi::os-lib:0.11.8
//> using dep ba.sake::tupson:0.31.0
//> using dep com.softwaremill.ox::core:1.0.9
//> using file eval-runner/Models.scala
//> using file eval-runner/PiProtocol.scala
//> using file eval-runner/OutputModels.scala
//> using file eval-runner/TriggerModels.scala
//> using file eval-runner/Infrastructure.scala
//> using file eval-runner/Metrics.scala
//> using file eval-runner/OutputEval.scala
//> using file eval-runner/OutputExecution.scala
//> using file eval-runner/OutputGrading.scala
//> using file eval-runner/OutputBenchmark.scala
//> using file eval-runner/TriggerEval.scala

import mainargs.{Flag, ParserForMethods, arg}

object EvalCli {
  @mainargs.main
  def output(
      @arg(doc = "Evaluated skill directory") skillPath: String = "skills/pragmatic-architecture",
      @arg(doc = "Optional evals JSON independent of the evaluated skill") evalPath: Option[String],
      @arg(doc = "Protected fixtures and grading directory") fixturePath: String = "tests/pragmatic-architecture",
      @arg(
        name = "case",
        doc = "Optional eval name; all evals run by default"
      ) caseName: Option[String],
      @arg(doc = "Model passed to Pi") model: String = "gpt-6-luna",
      @arg(doc = "Pi reasoning effort") reasoning: String = "low",
      @arg(doc = "Optional separate model for qualitative judges") judgeModel: Option[String],
      @arg(doc = "Optional with-skill or without-skill configuration") configuration: Option[String],
      @arg(doc = "Runs per eval and configuration") runs: Int = 1,
      @arg(doc = "Maximum concurrent Pi subprocesses") parallelism: Int = 4,
      @arg(doc = "Timeout for each subprocess") timeoutMinutes: Int = 30,
      @arg(doc = "Run candidates and deterministic grading only") skipJudge: Flag
  ): Unit =
    runOutputEval(
      OutputConfig(
        skillPath = skillPath,
        evalPath = evalPath,
        fixturePath = fixturePath,
        caseName = caseName,
        model = model,
        reasoning = reasoning,
        judgeModel = judgeModel,
        configuration = configuration,
        runs = runs,
        timeoutMinutes = timeoutMinutes,
        runJudge = !skipJudge.value,
        parallelism = parallelism
      )
    )

  @mainargs.main
  def trigger(
      @arg(doc = "Skill directory containing SKILL.md and trigger evals") skillPath: String =
        "skills/pragmatic-architecture",
      @arg(doc = "Optional trigger query name") query: Option[String],
      @arg(doc = "Optional train, validation, or fresh split") split: Option[
        String
      ],
      @arg(doc = "Model passed to Pi") model: String = "gpt-6-luna",
      @arg(doc = "Pi reasoning effort") reasoning: String = "low",
      @arg(doc = "Runs per trigger query") runs: Int = 3,
      @arg(doc = "Required trigger rate for positive queries") threshold: Double = 0.5,
      @arg(doc = "Maximum concurrent Pi subprocesses") parallelism: Int = 2,
      @arg(doc = "Timeout for each Pi subprocess") timeoutMinutes: Int = 10
  ): Unit =
    runTriggerEval(
      TriggerConfig(
        skillPath = skillPath,
        query = query,
        split = split,
        model = model,
        reasoning = reasoning,
        runs = runs,
        threshold = threshold,
        timeoutMinutes = timeoutMinutes,
        parallelism = parallelism
      )
    )

  def main(args: Array[String]): Unit =
    ParserForMethods(this).runOrExit(args.toIndexedSeq)
}

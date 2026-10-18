//> using test.dep org.scalameta::munit:1.3.6

class CompletionSuite extends munit.FunSuite {
  private val success =
    """{"type":"message_end","message":{"role":"assistant","content":[{"type":"text","text":"Done"}],"stopReason":"stop"}}"""
  private val providerError =
    """{"type":"message_end","message":{"role":"assistant","content":[],"stopReason":"error","errorMessage":"Subscription Sharing usage limit reached"}}"""
  private val toolError =
    """{"type":"message_end","message":{"role":"toolResult","content":[{"type":"text","text":"test failed"}]},"isError":true}"""

  test("zero process exit does not make a terminal provider failure a completed candidate") {
    withCandidate(List(toolError, providerError)) { candidate =>
      val error = intercept[IllegalArgumentException](validateOutputCandidates(List(candidate)))
      assert(error.getMessage.contains("no benchmark was produced"))
    }
  }

  test("a repaired tool failure followed by completion remains valid") {
    withCandidate(List(toolError, success))(candidate => validateOutputCandidates(List(candidate)))
  }

  test("a recovered provider error followed by completion remains valid") {
    withCandidate(List(providerError, success))(candidate => validateOutputCandidates(List(candidate)))
  }

  test("an aborted final response is incomplete even with a zero process exit") {
    val aborted = """{"type":"message_end","message":{"role":"assistant","stopReason":"aborted"}}"""
    withCandidate(List(aborted)) { candidate =>
      intercept[IllegalArgumentException](validateOutputCandidates(List(candidate)))
    }
  }

  test("an empty transcript cannot qualify as completed work") {
    withCandidate(Nil) { candidate =>
      intercept[IllegalArgumentException](validateOutputCandidates(List(candidate)))
    }
  }

  private def withCandidate(lines: List[String])(check: Candidate => Unit): Unit =
    val run = os.temp.dir(prefix = "completion-check-")
    try
      os.makeDir.all(run / "outputs")
      os.write(run / "outputs/transcript.jsonl", lines.mkString("\n"))
      val eval = EvalCase(1, "completion-check", "task", "result", Nil, Nil)
      val context = RunContext(eval, "without_skill", 1, run, run)
      val process = ProcessResult(0, 1, "start", "end")
      val metrics = Metrics(Map.empty, 0, 0, Nil, 0, 0L, 0L)
      check(Candidate(context, process, Usage(), metrics))
    finally os.remove.all(run)
}

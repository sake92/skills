//> using test.dep org.scalameta::munit:1.3.6

class MetricsSuite extends munit.FunSuite {
  private val skill = "pragmatic-architecture"
  private val path = s"/repo/skills/$skill/SKILL.md"
  private val document = s"---\nname: $skill\ndescription: Guidance\n---\n# Architecture\nKeep changes local.\n"
  private val work = os.temp.dir(prefix = "skill-metrics-")
  private val outputs = work / "outputs"

  override def beforeAll(): Unit =
    os.proc("git", "init", "--quiet").call(cwd = work)
    os.makeDir(outputs)

  override def afterAll(): Unit = os.remove.all(work)

  private def start(tool: String, args: PiArgs, id: String = "call-1") =
    PiEvent("tool_execution_start", toolName = Some(tool), args = Some(args), toolCallId = Some(id))

  private def end(text: String = document, error: Boolean = false, id: String = "call-1") =
    PiEvent("tool_execution_end", isError = Some(error), toolCallId = Some(id),
      result = Some(PiToolResult(List(PiContent("text", Some(text))))))

  private def invoked(events: PiEvent*): Boolean =
    metrics(work, outputs, events.toList, Some(skill)).skill_invoked.contains(true)

  test("successful read of the skill document counts") {
    assert(invoked(start("read", PiArgs(path = Some(path))), end()))
  }

  test("successful shell read of the skill document counts in a compound command") {
    val command = s"cat '$path' && rg --files"
    assert(invoked(start("bash", PiArgs(command = Some(command))), end(document + "\nproject.scala\n")))
  }

  test("a failed read does not count") {
    assert(!invoked(start("read", PiArgs(path = Some(path))), end(error = true)))
  }

  test("mentioning the skill path without returning the document does not count") {
    assert(!invoked(start("bash", PiArgs(command = Some(s"echo '$path'"))), end(path)))
  }

  test("another skill's document does not count") {
    val other = document.replace(s"name: $skill", "name: other-skill")
    assert(!invoked(start("bash", PiArgs(command = Some(s"cat '$path'"))), end(other)))
  }

  test("unrelated tool calls cannot supply evidence for the requested read") {
    assert(!invoked(start("read", PiArgs(path = Some(path))), end(id = "other-call")))
  }

  test("a read request without a completed result does not count") {
    assert(!invoked(start("read", PiArgs(path = Some(path)))))
  }

  test("frontmatter without instructions does not count") {
    assert(!invoked(start("bash", PiArgs(command = Some(s"cat '$path'"))),
      end(s"---\nname: $skill\n---\n")))
  }

  test("a provider failure is detected even when the process exits successfully") {
    import ba.sake.tupson.*
    val event = """{"type":"message_end","message":{"role":"assistant","stopReason":"error","errorMessage":"Usage limit reached"}}""".parseJson[PiEvent]
    assertEquals(terminalAssistantError(List(event)), Some("Usage limit reached"))
  }

  test("a recovered provider error does not invalidate a completed candidate") {
    val failed = PiEvent("message_end", message = Some(PiMessage(role = "assistant",
      stopReason = Some("error"), errorMessage = Some("Temporary error"))))
    val completed = PiEvent("message_end", message = Some(PiMessage(role = "assistant",
      stopReason = Some("stop"))))
    assertEquals(terminalAssistantError(List(failed, completed)), None)
  }
}

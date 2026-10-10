package buildtool

class ParallelBuildSuite extends munit.FunSuite {
  test("launch adapter returns all results in input order") {
    val result = BuildLaunch.execute(new ParallelBuildRunner(), Vector("api", "app"), 2)(_.toUpperCase)
    assertEquals(result.results, Vector(StepResult("api", "API"), StepResult("app", "APP")))
  }

  test("task failure reaches the launcher unchanged") {
    val failure = new IllegalStateException("compiler failed")
    val observed = intercept[IllegalStateException] {
      new ParallelBuildRunner().run(BuildRequest(Vector(BuildStep("app", () => throw failure)), 1))
    }
    assert(observed eq failure)
  }
}

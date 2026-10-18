//> using test.dep org.scalameta::munit:1.3.6

class GraderSnapshotSuite extends munit.FunSuite:
  test("isolated graders retain shared relative imports and their transitive sources") {
    val root = os.temp.dir(prefix = "grader-snapshot-")
    try
      val common = root / "tests/example/grading"
      val shared = root / "tests/shared/Shared.scala"
      val helper = root / "tests/shared/Helper.scala"
      os.write(common / "project.scala", "//> using file ../../shared/Shared.scala\n", createFolders = true)
      os.write(shared, "//> using file Helper.scala\nclass Shared\n", createFolders = true)
      os.write(helper, "class Helper\n")
      val snapshot = root / "snapshot"
      val copied = snapshotGradingSources(root, List(common), snapshot).head
      val imported = os.Path("../../shared/Shared.scala", copied)
      os.write.over(helper, "class ChangedAfterSnapshot\n")
      assertEquals(os.read(imported), "//> using file Helper.scala\nclass Shared\n")
      assertEquals(os.read(imported / os.up / "Helper.scala"), "class Helper\n")
      assertEquals(os.read(copied / "project.scala"), os.read(common / "project.scala"))
    finally os.remove.all(root)
  }

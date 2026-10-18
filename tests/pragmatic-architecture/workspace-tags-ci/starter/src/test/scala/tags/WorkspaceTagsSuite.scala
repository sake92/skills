package tags

class WorkspaceTagsSuite extends munit.FunSuite {
  test("exports preserve original tags and document order") {
    val docs = List(Document(" Old ", true, List(" X ", "x")), Document("New", false, Nil))
    assertEquals(WorkspaceTags.exportRows(docs), List("Old,archived, X |x", "New,active,"))
  }

  test("archived tag list retains its existing selection and normalization") {
    val docs = List(Document("Old", true, List("B", " a ", "A", " ")), Document("New", false, List("z")))
    assertEquals(WorkspaceTags.archivedTagList(docs), "a|b")
  }

  test("title and tag helpers keep their existing contracts") {
    assertEquals(TagText.title("  Build\t club "), "Build club")
    assertEquals(TagText.normalize(" SCALA "), "scala")
  }
}

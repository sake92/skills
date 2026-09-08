# SemanticDB input (Scala/JVM)

Use this reference when the project already produces Scala SemanticDB. SemanticDB is the richest Codeps source: it provides package and file graphs plus exposed-surface and declaration-visibility metrics.

For sbt, configure `target` relative to the project root; Codeps recursively discovers the generated `*.semanticdb` files below it:

```yaml
projects:
  app:
    root: .
    source: semanticdb
    inputs: [target]
```

Common producers include sbt with the SemanticDB plugin, scala-cli (`scala-cli compile --semanticdb`), and scalac with `-Xsemanticdb`. Locate the build's actual output rather than assuming a conventional path; for scala-cli, that is often `classes/META-INF/semanticdb`. Codeps recursively walks each configured input directory.

Compile successfully before `codeps status`. An incremental compile can leave old `*.semanticdb` files after a source was deleted or moved, and Codeps recursively reads them. Before using a source-based finding, check that its non-generated source path exists in the current checkout. If it does not, it is stale evidence: do not call it an orphan or make a recommendation from it.

SemanticDB-based reports include both `packages` and `files`; use packages for architecture and files for implementation-level triage. Treat generated-source paths according to the repository's configured generated roots, not as ordinary checkout sources.

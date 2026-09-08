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

Compile successfully before `codeps status`. SemanticDB-based reports include both `packages` and `files`; use packages for architecture and files for implementation-level triage.

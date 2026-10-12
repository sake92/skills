# Customer directory

The HTTP directory and nightly CRM synchronizer share application services and
the customer repository. Persistence currently uses in-memory rows behind a DAO;
the DAO contract is also used by the reconciliation job. Dependencies are wired
by `DirectoryApplication`. Existing controller and job entry points are consumed
by other internal applications; preserve their source compatibility and output.

Run visible tests with `scala-cli test . --server=false`.

The project already supports dependency analysis. From the repository root:

```bash
scala-cli compile project.scala src/main --semanticdb --compilation-output target/classes --server=false
codeps status --project app
```

`codeps` is installed. Its CLI help is available with `codeps --help`.
The configured project includes all application packages and excludes tests.
Keep that analysis scope when comparing changes.

Write `architecture.md` explaining the evidence, the boundary chosen, remaining
tradeoffs, and how the resulting application was verified. Keep the change within
this application; it does not need new build modules or libraries.

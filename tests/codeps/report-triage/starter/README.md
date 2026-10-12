# Directory export service

The API, nightly job and storage adapter share an export service. Operators choose
export formats through `conf/exporters.properties`; installations can supply their
own compatible implementations. The default adapters ship in this project.

Run visible tests with `scala-cli test . --server=false`.

The project supports dependency analysis:

```bash
scala-cli compile project.scala src/main --semanticdb --compilation-output target/classes --server=false
codeps status --project app
```

`codeps` is installed. The previous CI report is in `reports/dependencies.json`.
Keep the configured application scope when comparing reports.

The maintenance tracker imports `review.json` with this structure:

```json
{
  "packageCycles": 0,
  "fileCycles": 0,
  "recommendations": [
    {
      "subject": "a reported package or file",
      "action": "keep, remove, investigate, or refresh",
      "evidence": ["source, configuration, or build evidence supporting the action"]
    }
  ],
  "summary": "The recommended next step and its limits"
}
```

Use one action word per recommendation. Cover the CI report's cycle and orphan
findings, and give current package/file cycle counts. No production changes have
been approved; this is a review to choose the next maintenance task.

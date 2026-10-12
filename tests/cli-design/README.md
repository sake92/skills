# CLI design evaluations

`json-export` requests JSON output for an existing inventory exporter while retaining table and file workflows. The starter has three passing visible tests. Protected MUnit checks exercise real JVM invocations, separate stdout/stderr, exit codes, file effects, and non-interactive execution. Graders remain outside candidate workspaces.

[Task](json-export/prompt.md) · [Starter](json-export/starter/README.md) · [Results and limits](../../skills/cli-design/benchmark-results/json-export.md)

The protected starter scores 1/9; a local corrected reference scores 9/9. A mutant allowing unattended overwrite scores 8/9, failing that check. These validate the grader, not model uplift.

## Run

Qualify a new case with the plain-model baseline first:

```bash
scala tests/run-eval.scala --server=false -- output \
  --skill-path skills/cli-design \
  --fixture-path tests/cli-design \
  --case json-export --configuration without-skill \
  --runs 1 --skip-judge --parallelism 1 --timeout-minutes 15
```

After a below-perfect baseline, replace `without-skill` with `with-skill` for the smoke. The shared runner defaults to GPT-6 Luna at low reasoning. Keep model, reasoning, tools, permissions, prompt, fixture, grader and timeout matched. Reject any case with an observed perfect baseline; ties/regressions do not pass acceptance.

Starter verification:

```bash
scala-cli test tests/cli-design/json-export/starter --server=false
```

Protected grading against the starter:

```bash
scala-cli test tests/cli-design/grading \
  tests/cli-design/json-export/grading \
  tests/cli-design/json-export/starter --server=false
```

The last command is expected to fail eight protected checks; the three visible starter tests should pass. Generated candidates, raw grades, patches, snapshots, rejected attempts, and development notes stay under ignored `tests/tmp/`. Baseline reuse and adaptive skill tuning are disclosed in the published report.

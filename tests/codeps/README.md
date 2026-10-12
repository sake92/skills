# Run Codeps evaluations

Both configurations have `codeps` and Scala CLI on PATH. The skill supplies
guidance, not exclusive tool access. Each starter has two passing visible tests.
The existing runner copies only the starter into the candidate workspace;
protected graders remain outside it.

| Case | Task | Protected evidence |
| --- | --- | --- |
| `dependency-knot` | Untangle a customer directory across HTTP, services, repositories, DAO and job consumers. | Behavior, package/file graphs from a separate clean compilation, final report agreement, and correctly labeled before/after evidence. |
| `report-triage` | Review a previous CI report before choosing a maintenance task. | Missing-source diagnosis, configured runtime adapters, current package/file cycles, fresh report agreement, and preserved application behavior. |

The directory's starter has a four-package cycle despite an acyclic file graph.
Removing one response-model dependency is insufficient: input models and policy
also cross the HTTP boundary. The grader accepts different designs that preserve
the tested consumers and eliminate the package knot.

The triage input is genuine Codeps output from an earlier fixture revision. The
retired sources are absent from the current checkout. Its two apparent orphan
exporters are loaded through configuration and reflection. The supplied JSON is
an intentional task input, not a generated evaluation result.

## Qualify and compare

Run the plain-model baseline first:

```bash
scala tests/run-eval.scala --server=false -- output \
  --skill-path skills/codeps --fixture-path tests/codeps \
  --configuration without-skill --runs 1 --skip-judge \
  --parallelism 1 --timeout-minutes 5
```

Reject any case with a perfect observed baseline. Keep rejected cases and notes
under ignored `tests/tmp/`. Freeze the task, fixture and grader before changing
the skill; keep model, reasoning, tools, permissions and budget matched.

After inspecting the baseline, smoke-test with the skill:

```bash
scala tests/run-eval.scala --server=false -- output \
  --skill-path skills/codeps --fixture-path tests/codeps \
  --configuration with-skill --runs 1 --skip-judge \
  --parallelism 1 --timeout-minutes 5
```

Use `--case dependency-knot` or `--case report-triage` to select one task. With-skill
must score strictly higher while preserving existing behavior; target 100%.
For a new matched pair, use the same command without `--configuration`.

## Grader validation

Protected grading compiles production sources in a separate temporary Git
checkout, generates new SemanticDB with the original application scope, and runs
the installed Codeps CLI. Candidate-provided reports are compared to that graph.
Temporary compiler output is removed after grading.

The analyzed knot starter scores 5/7; a corrected reference scores 7/7. A variant
that leaves the DAO dependent on API policy scores 5/7 while its behavior tests
still pass. The triage starter scores 1/6; a reference review scores 6/6. A review
that recommends removing the configured exporters scores 5/6. These are grader
proofs, not model baselines.

The knot's before/after check and cycle check are related; they should not be
interpreted as independent architectural improvements. Triage grading requires
an actionable structured review and evidence for runtime loading; it does not
grade arbitrary prose style.

Runs, proofs, transcripts and review viewers stay under ignored `tests/tmp/`.
Published reports contain only summaries and small code excerpts. See the
[runner guide](../README.md) for viewer generation.

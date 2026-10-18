# Skill evaluations

The [pragmatic-testing fixture](pragmatic-testing/README.md) starts with an empty test directory around a Sharaf/SQLite application. It evaluates initial test choices, detection of independent defects, and stability across harmless JSON formatting changes. The selected pair scores 5/8 without skill and 7/8 with skill; both agents chose HTTP tests with real SQLite.

The [CLI design fixture](cli-design/README.md) evaluates JSON export, file-output streams, failures, overwrite behavior, and legacy table compatibility. Its selected concrete-reproducer development smoke improves three checks while retaining a malformed-input diagnostic failure.

The [Codeps fixtures](codeps/README.md) evaluate a layered dependency knot and a
review of stale findings and runtime-loaded plugins. Both configurations have
the analyzer available; protected grading rebuilds the graphs independently.

The [Twelve-Factor evaluations](twelve-factor/README.md) use the same runner with `--skill-path`, `--eval-path`, and `--fixture-path`. Runtime bindings is the first baseline-qualified active case. Architecture remains the default suite. Each new output iteration snapshots the evaluated skill under `evaluated-skill/` for provenance.

The [process-hygiene fixtures](process-hygiene/README.md) evaluate build-task stdio, revised worker timeout cleanup, and cancellation during work/cleanup. Their protected integration tests run real Linux tools. Raw patches, grades, and skill snapshots stay under ignored `tests/tmp/`; repository reports contain summaries and small excerpts only.

The pragmatic-architecture eval set follows the Agent Skills benchmark layout:
`evals.json`, fresh `with_skill` and `without_skill` workspaces, per-run
`outputs/`, `grading.json`, `timing.json`, and a workspace-level
`benchmark.json`.

Install [Pi](https://pi.dev/), start it once, and use
`/login openai-codex` to authorize the OpenAI provider. Then run the eval set
with the [default evaluation setup](../README.md):

```bash
scala tests/run-eval.scala --server=false -- output
```

Run one case or repeat every configuration for variance:

```bash
scala tests/run-eval.scala --server=false -- output \
  --case customer-repository \
  --runs 3
```

Qualify new cases with the plain-model baseline first. Inspect genuine protected-check failures before tuning the skill; reject cases with a perfect baseline and keep their history in ignored local notes. Freeze the task, fixture, and grader, then smoke-test with skill before spending tokens on a full comparison. Keep model, reasoning, tools, permissions, and budget matched; disclose adaptive development and confirm improvement on fresh cases.

Run the baseline independently:

```bash
scala tests/run-eval.scala --server=false -- output \
  --case customer-repository \
  --configuration without-skill \
  --runs 1
```

Run the with-skill smoke after baseline qualification:

```bash
scala tests/run-eval.scala --server=false -- output \
  --case customer-repository \
  --configuration with-skill \
  --runs 1
```

Single-configuration runs write `runs.json` and the normal per-run artifacts.
Comparative runs additionally write `benchmark.json`.
Provider errors invalidate candidate runs even when Pi exits successfully;
they must not be interpreted as skill failures or included in benchmark scores.

The caller-compatibility scenario can be selected with
`--case repository-with-callers`. Its starter includes passing lookup/export
tests and an existing consumer of the immutable snapshot API. The task requests
email lookup support; protected grading checks the enhancement and preservation
of the needed API alongside removal of unused mutable escape hatches.

Expression and change-scope cases:

- `--case invitation-preview`: pending-invitation text with exact empty/singular/plural forms.
  Protected checks preserve existing working methods and formatting while checking
  new expressions for conditional interpolation and long chains.
- `--case workspace-tags-ci`: a summary across nested tag lists with an actual
  formatter check required by CI. Existing methods and tests retain their syntax
  trees; formatting changes are allowed, semantic cleanup is rejected.

The expression checks inspect new or changed methods in each fixture's production
file. Receiver depth above three is a syntax proxy, not a general readability
metric. Existing dense expressions are deliberately permitted.

Two FlowRun-inspired cases add positive type-safety and response-model coverage:

- `--case teacher-course-ids`: fix a user/course UUID reversal from a raw-UUID
  starter without a prompt suggesting stronger types, and preserve
  UUID HTTP, roster, and persistence boundaries. Protected compile-time probes
  discover candidate type names and verify correctly typed calls compile while
  swapped/raw arguments do not, including
  calls through the concrete repository. The judge assesses cache typing, scope,
  and candidate regression tests.
- `--case public-profile-dto`: add the member-since year to the JSON endpoint
  and supplied Vue page. The task does not request a DTO or enumerate private
  fields. Protected safety checks measure whether the response avoids leaking
  account data while preserving private backup restoration.
  Protected tests check derived years, displayed fields/types, null avatars, JSON escaping, errors,
  and retained internal data. The judge assesses transport separation, use of the
  existing codec, scope, and leak-detecting candidate coverage.

Each starter has three passing tests. These are focused adaptations of real
application concerns rather than full FlowRun checkouts; they need no database,
browser, or external service. The ID task requests only the bug fix and compatibility. Its two architectural
criteria measure whether the agent independently introduces useful ID separation;
behavioral correctness is reported separately. Protected compilation probes require the correct
call to compile before treating a rejected call as evidence of safety.

The qualitative judge uses the executor model by default. Pass
`--judge-model MODEL` to use a different judge, or `--skip-judge` when only
candidate execution and protected deterministic grading are needed.
`--reasoning LEVEL` forwards a supported Pi reasoning effort.

Check whether the skill description triggers on held-out requests separately:

```bash
scala tests/run-eval.scala --server=false -- trigger \
  --split validation \
  --runs 1
```

Smoke-test one trigger query before running a whole split:

```bash
scala tests/run-eval.scala --server=false -- trigger \
  --query mutable-session-store \
  --runs 1
```

Output and trigger evaluation are subcommands of the same runner because they
measure different things: the quality of completed work and whether Pi loads
the skill for an applicable request. Trigger runs pass the original request to
a clean Pi session with the evaluated skill installed, then inspect the trace
for a read of `SKILL.md`.

Output invocations create timestamped workspaces under
`tests/tmp/pragmatic-architecture-workspace/`; trigger invocations use
`tests/tmp/pragmatic-architecture-trigger-workspace/`. Candidate workspaces
receive only the starter; Pi receives the evaluated skill through its explicit
`--skill` option for `with_skill`. Protected MUnit and Scalameta graders stay
outside both candidate workspaces.

A grader that compiles and replays submitted tests itself should include a
`grading/replays-submission` marker in its case directory. The runner then compiles
only protected grading sources in the outer process, isolating them from candidate
test-framework dependencies. Other graders retain their existing shared compilation.

Replay graders must emit their grading report. A missing report (including a
grader timeout) aborts the evaluation instead of manufacturing a zero coverage
score. `--timeout-minutes` sets the subprocess budget. For expensive deterministic
replays, use `--grading-timeout-minutes 20` to raise only the grader budget while
preserving the candidate budget (for example, `--timeout-minutes 10`). Omitting
the override preserves existing behavior. Use the same budgets across compared
configurations.

Candidate runs must finish successfully. A terminal provider error, aborted response,
or absent assistant response invalidates the run even when the process exits zero.
Recovered errors followed by a completed response remain eligible for grading.
Run the completion regression checks with
`scala-cli test tests/run-eval.scala tests/eval-runner-tests --server=false`.

The shared `grading/RepositoryStructure.scala` checker accepts case-specific
required members. The original customer case requires the three lookups; the
caller-compatibility case requires ID/email lookup and the existing snapshot.

Inspect `benchmark.md` for the summary, `benchmark.json` for structured results,
and each run's `grading.json`, `outputs/submission.patch`, and
`outputs/transcript.jsonl` for evidence. Generated workspaces are intentionally
not committed; checked-in summaries belong in the skill's maintainer README.

`review.html` is generated separately with the skill-creator's
`eval-viewer/generate_review.py` script. It reads an iteration's existing prompts,
outputs, and grades and produces a standalone HTML viewer with `--static`.
The Scala runner writes grading and benchmark artifacts; it does not
automatically generate the viewer. Supply `--benchmark` when reviewing a paired
comparison. Single-configuration smoke runs have no comparison benchmark.

Runner regression checks:

```bash
scala test tests/run-eval.scala tests/eval-runner/Metrics.test.scala --server=false
```

Skill-read detection requires a successful matching tool result containing the
skill's frontmatter and instructions. Both `read` and shell reads are supported;
failed reads and commands that merely mention the path do not count. Protected
graders compile in separate per-run directories outside candidate workspaces.

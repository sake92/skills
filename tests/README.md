# Skill evaluations

The [Twelve-Factor adoption check](twelve-factor/README.md) uses the same runner with `--skill-path`, `--eval-path`, and `--fixture-path` to compare external and local guidance on protected Scala fixtures. Architecture remains the default suite. Each new output iteration snapshots the evaluated skill under `evaluated-skill/` for provenance.

The [process-hygiene fixtures](process-hygiene/README.md) evaluate build-task stdio and revised worker timeout cleanup. Their protected integration tests run real Linux tools. Raw patches, grades, and skill snapshots stay under ignored `tests/tmp/`; repository reports contain summaries and small excerpts only.

The pragmatic-architecture eval set follows the Agent Skills benchmark layout:
`evals.json`, fresh `with_skill` and `without_skill` workspaces, per-run
`outputs/`, `grading.json`, `timing.json`, and a workspace-level
`benchmark.json`.

Install [Pi](https://pi.dev/), start it once, and use
`/login openai-codex` to authorize the OpenAI provider. Then run the eval set
with the default GPT-6 Luna executor and judge:

```bash
scala tests/run-eval.scala --server=false -- output
```

Run one case or repeat every configuration for variance:

```bash
scala tests/run-eval.scala --server=false -- output \
  --case customer-repository \
  --runs 3
```

Before spending tokens on a comparison, smoke-test one side independently:

```bash
scala tests/run-eval.scala --server=false -- output \
  --case customer-repository \
  --configuration with-skill \
  --runs 1
```

Single-configuration runs write `runs.json` and the normal per-run artifacts.
Comparative runs additionally write `benchmark.json`.

The caller-compatibility scenario can be selected with
`--case repository-with-callers`. Its starter includes passing lookup/export
tests and an existing consumer of the immutable snapshot API. The task requests
email lookup support; protected grading checks the enhancement and preservation
of the needed API alongside removal of unused mutable escape hatches.

Two further cases use the same runner and directory layout:

- `--case legacy-discount-fix`: fix premature monetary rounding while preserving
  existing constructor injection and controller/export contracts.
- `--case booking-overlap-rule`: preserve one shared booking creation invariant
  across HTTP, direct business calls, and ordered batch import. Protected
  Scalameta checks also reject business collaborator construction inside adapters;
  the fixture's application wiring can supply changed constructor dependencies.

Both starters begin with passing MUnit suites. The protected suites expose the
reported bugs only during grading. Qualitative judges assess architectural
tradeoffs and focused candidate regression coverage separately from behavior.

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

# Skill evaluations

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

Inspect `benchmark.md` for the summary, `benchmark.json` for structured results,
and each run's `grading.json`, `outputs/submission.patch`, and
`outputs/transcript.jsonl` for evidence. Generated workspaces are intentionally
not committed; checked-in summaries belong in the skill's maintainer README.

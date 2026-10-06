# Skill evaluations

The pragmatic-architecture eval set follows the Agent Skills benchmark layout:
`evals.json`, fresh `with_skill` and `without_skill` workspaces, per-run
`outputs/`, `grading.json`, `timing.json`, and a workspace-level
`benchmark.json`.

Run both cases with the default GPT-6 Luna executor and judge:

```bash
scala tests/run-eval.scala --server=false
```

Run one case or repeat every configuration for variance:

```bash
scala tests/run-eval.scala --server=false -- \
  --case customer-repository \
  --runs 3
```

Use a local Ollama model through Codex CLI by changing only the provider and
model:

```bash
ollama pull MODEL
scala tests/run-eval.scala --server=false -- \
  --provider ollama \
  --model MODEL
```

Supported providers are `openai`, `ollama`, and `lmstudio`. The qualitative
judge uses the executor provider and model by default. To keep a local executor
but use a separate judge, pass `--judge-provider openai --judge-model MODEL`.
Use `--skip-judge` when only candidate execution and protected deterministic
grading are needed. `--reasoning LEVEL` forwards a supported Codex reasoning
effort.

Each invocation creates a timestamped workspace under
`tests/tmp/pragmatic-architecture-workspace/`. Candidate workspaces receive
only the starter and, for `with_skill`, the installed skill. Protected MUnit
and Scalameta graders stay outside both candidate workspaces.

Inspect a completed iteration with the standard skill-creator viewer:

```bash
python "${CODEX_HOME:-$HOME/.codex}/skills/skill-creator/eval-viewer/generate_review.py" \
  tests/tmp/pragmatic-architecture-workspace/iteration-TIMESTAMP \
  --benchmark tests/tmp/pragmatic-architecture-workspace/iteration-TIMESTAMP/benchmark.json \
  --static /tmp/pragmatic-architecture-review.html
```

The viewer creates `feedback.json` when reviews are submitted. Generated
workspaces and feedback are intentionally not committed; checked-in summaries
belong in the skill's maintainer README.

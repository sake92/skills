# Skill evaluations

Run the default customer-repository comparison with GPT-6 Luna:

```bash
scala tests/run-eval.scala
```

Change the model, provider, or case with flags:

```bash
scala tests/run-eval.scala -- \
  --case pragmatic-architecture/customer-repository \
  --provider openai \
  --model gpt-6-luna
```

Supported providers are `ollama`, `lmstudio`, and `openai`. Add
`--reasoning high` for models that support reasoning effort. The judge uses the
candidate model by default; select a stronger independent judge with
`--judge-provider openai --judge-model MODEL`, or use `--skip-judge` to run only
the candidate comparison and deterministic grader.

Each run gets fresh with-skill and without-skill Git workspaces under
`tests/tmp/`; protected grading files are never copied into candidate
workspaces. Logs, judge output, and the machine-readable `run.json` summary are
stored beside those workspaces. The runner exits nonzero for infrastructure or
agent execution failures; a protected grading failure is recorded as an eval
result rather than treated as a runner failure.

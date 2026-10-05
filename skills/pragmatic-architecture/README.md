# Pragmatic Architecture

The skill favors small interfaces, local complexity, explicit dependencies,
proportional design changes, and behavioral tests. Its instructions are in
[`SKILL.md`](SKILL.md).

## Evaluation

The checked-in customer-repository case asks Codex to finish three ordinary
lookups without mentioning interface size or encapsulation. Protected MUnit and
Scalameta checks verify behavior and reject exposed mutable state, public
helpers, mutable collection return types, and broad `all`, `records`, or
`query` escape hatches. A separate judge considers only proportionality and
unjustified DTO or wrapper types.

Run it from the repository root:

```bash
scala tests/run-eval.scala --server=false -- \
  --case pragmatic-architecture/customer-repository \
  --provider ollama \
  --model qwen2.5-coder:3b
```

See [`tests/README.md`](../../tests/README.md) for provider, model, reasoning,
and independent-judge options.

### Results

These are single runs, useful as regression evidence rather than statistical
benchmarks.

| Candidate | Skill | Behavior | Protected structure | Qualitative judge |
| --- | --- | --- | --- | --- |
| `gpt-6.1-sol`, high reasoning | revised skill | pass | pass | pass |
| `gpt-6.1-sol`, high reasoning | none | pass | fail: 10 findings | pass |
| `qwen2.5-coder:3b` via Ollama | revised skill | fail: no implementation | fail: 10 findings | unreliable |
| `qwen2.5-coder:3b` via Ollama | none | fail: no implementation | fail: 10 findings | unreliable |

The initial cloud comparison had identical structural failures with and
without the original skill. After adding guidance to inspect and narrow an
existing type's public members, the fresh with-skill run passed while the
unchanged baseline still exposed `records`, `all`, and `query`.

The local Ollama run completed through Codex CLI, but Codex had no model
metadata for `qwen2.5-coder:3b`. The model returned pseudo-tool JSON instead of
editing files, so neither candidate implemented the behavior. Its two judge
answers also contradicted the unchanged source. This demonstrates that the
runner supports Ollama, but this 3B model is not a credible Codex agent or judge
for this case without compatible model metadata/tool use.

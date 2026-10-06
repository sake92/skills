# Pragmatic Architecture

This is maintainer documentation. Coding agents install and read
[`SKILL.md`](SKILL.md); the benchmark setup and results below are not part of
the skill instructions presented to an evaluated agent.

## Evaluation

The checked-in eval set has two Scala 3 cases:

- `customer-repository` asks for three ordinary in-memory lookups without
  naming privacy, encapsulation, or interface minimality. Protected MUnit and
  Scalameta checks grade behavior, the exact repository surface, hidden mutable
  state and helpers, immutable return types, forbidden escape hatches, and
  candidate regression coverage. An LLM judge grades only proportionality and
  unjustified wrapper types.
- `inventory-reservation` starts green but permits overselling. Protected MUnit
  checks cover both callers, rejected requests, state preservation, shared
  invariant ownership, and candidate regression coverage. An LLM judge grades
  only invariant placement and proportionality.

The legacy order case was removed because with-skill and without-skill agents
produced the same fully passing patch, so it did not measure skill value.

Run the complete comparison from the repository root:

```bash
scala tests/run-eval.scala --server=false
```

See [`tests/README.md`](../../tests/README.md) for repeat runs, local Ollama
models, independent judges, output paths, and the standard human-review viewer.

## Latest benchmark

No result is claimed until the checked-in runner has completed both cases. Add
the executor/judge models, run count, per-configuration pass rates, and the
generated workspace path here after reviewing `benchmark.json` and the static
viewer.

# Pragmatic Architecture

This is maintainer documentation. Coding agents install and read
[`SKILL.md`](SKILL.md); the benchmark setup and results below are not part of
the skill instructions presented to an evaluated agent.

## Evaluation

The checked-in eval set has one Scala 3 case:

- `customer-repository` asks for three ordinary in-memory lookups without
  naming privacy, encapsulation, or interface minimality. Protected MUnit and
  Scalameta checks grade behavior, the exact repository surface, hidden mutable
  state and helpers, immutable return types, forbidden escape hatches, and
  candidate regression coverage. An LLM judge grades only proportionality and
  unjustified wrapper types.

The legacy order case was removed because with-skill and without-skill agents
produced the same fully passing patch, so it did not measure skill value.

Run the complete comparison from the repository root:

```bash
scala tests/run-eval.scala --server=false
```

See [`tests/README.md`](../../tests/README.md) for repeat runs, local Ollama
models, independent judges, output paths, and the standard human-review viewer.

## Latest benchmark

On 2026-10-06, GPT-6 Luna ran the customer case twice per configuration at
commit `6afcd7d` with Luna also acting as the qualitative judge:

| Configuration | Runs | Mean pass rate | Individual scores |
| --- | ---: | ---: | --- |
| With skill | 2 | 80% | 6/10, 10/10 |
| Without skill | 2 | 60% | 6/10, 6/10 |

The successful with-skill run invoked the skill and passed every expectation.
The 6/10 with-skill run did not invoke it, so this is evidence of useful
instructions when triggered, but also of trigger variance—not a stable 20-point
uplift claim. The inventory-reservation case was removed after both with-skill
and without-skill runs scored 9/9 twice, making it non-discriminating for Luna.

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
scala tests/run-eval.scala --server=false -- output
```

See [`tests/README.md`](../../tests/README.md) for repeat runs, independent
judges, and output paths.

## Latest benchmark

On 2026-10-07, Pi 1.0.4 ran GPT-6 Luna with low reasoning on the customer case
once per configuration, with Luna also acting as the narrowly scoped
qualitative judge:

| Configuration | Score | Candidate time | Tokens |
| --- | ---: | ---: | ---: |
| With skill | 10/10 | 58.8 s | 12,892 |
| Without skill | 7/10 | 49.7 s | 11,138 |

Both runs implemented the requested behavior, added focused MUnit coverage, and
passed the proportionality and wrapper-type checks. The baseline left the
prototype's public mutable `records`, `all`, and `query` escape hatches in
place; the with-skill run removed or hid them and passed all protected Scalameta
checks. One paired run demonstrates discrimination but is not a stable uplift
estimate.

The separate natural-trigger sanity check passed 6 of 8 held-out queries in a
single Luna/low run. It rejected all four negative near-misses but loaded the
skill for only two of four applicable requests. That is useful evidence that
the description still triggers inconsistently, not a stable trigger-rate
estimate; the checked-in runner defaults to three runs for a real measurement.

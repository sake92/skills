# Pragmatic Architecture

This is maintainer documentation. Coding agents install and read
[`SKILL.md`](SKILL.md); the benchmark setup and results below are not part of
the skill instructions presented to an evaluated agent.

## Latest benchmark

Results are tracked per case/setup, using the latest valid paired comparison
for each. Tied cases remain in the benchmark. Smoke checks are not comparison
results. Each result below is one pair, not a stable estimate of skill benefit.

### `customer-repository`

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

### `repository-with-callers`

On 2026-10-08, Pi 1.0.4 ran GPT-6 Luna with low reasoning once per
configuration, with Luna also acting as the narrowly scoped qualitative judge:

| Configuration | Score | Candidate time | Tokens |
| --- | ---: | ---: | ---: |
| With skill | 8/8 | 49.7 s | 13,263 |
| Without skill | 5/8 | 38.5 s | 13,803 |

Both candidates implemented email lookup, added regression coverage, and
preserved ID lookup and export behavior. The baseline retained public mutable
`records`, `all`, and `query`; the with-skill candidate hid backing state and
removed the unused helpers while preserving the caller-required `snapshot`.
The three separating checks are protected structural assertions, not judge
preferences or grader compilation failures.

### `legacy-discount-fix`

On 2026-10-08, Pi 1.0.4 ran GPT-6 Luna with low reasoning once per
configuration, with Luna also acting as the narrowly scoped qualitative judge:

| Configuration | Score | Candidate time | Tokens |
| --- | ---: | ---: | ---: |
| With skill | 8/8 | 48.5 s | 14,749 |
| Without skill | 8/8 | 49.3 s | 11,652 |

The submitted patches were byte-for-byte identical, including the rounding
correction and regression test. Both preserved constructor injection, layered
layout, controller/export contracts, and the independent preview. This pair
shows no measured quality improvement from the skill. The case is retained to
check focused changes and compatibility, including possible future regressions.

### `booking-overlap-rule`

On 2026-10-08, Pi 1.0.4 ran GPT-6 Luna with low reasoning once per
configuration, with Luna also acting as the narrowly scoped qualitative judge:

| Configuration | Score | Candidate time | Tokens |
| --- | ---: | ---: | ---: |
| With skill | 12/12 | 95.1 s | 28,418 |
| Without skill | 12/12 | 58.3 s | 20,024 |

Both candidates passed the booking behavior and constructor-injection checks.
This pair shows no measured quality uplift, with higher time and token cost
for the with-skill candidate. Further discrimination is needed before this case
can demonstrate skill value. One pair is not a stable performance estimate.

## Evaluation

The checked-in eval set has four Scala 3 cases:

- `customer-repository` asks for three ordinary in-memory lookups without
  naming privacy, encapsulation, or interface minimality. Protected MUnit and
  Scalameta checks grade behavior, the exact repository surface, hidden mutable
  state and helpers, immutable return types, forbidden escape hatches, and
  candidate regression coverage. An LLM judge grades only proportionality and
  unjustified wrapper types.
- `repository-with-callers` requests an email lookup enhancement in an application
  with an existing export consumer. It tests preserving the needed immutable
  snapshot while narrowing unused prototype helpers and hiding mutable state.
  The starter's existing tests pass before the enhancement. Protected MUnit and
  Scalameta checks grade behavior, consumer compatibility, public surface, and
  added email lookup assertions; a judge grades proportionality.
- `legacy-discount-fix` requests a monetary rounding correction in an existing
  layered application with constructor injection. Protected tests check decimal
  precision, response/export compatibility, absence, invalid input, and an
  independent preview API. A judge checks proportionality, existing conventions,
  justified abstractions, and regression coverage. Its 3 starter tests pass.
- `booking-overlap-rule` requests consistent conflict rejection through HTTP and
  nightly import. Protected tests also call the business operation directly and
  check overlap orientations, adjacency, cancellation, other rooms, current
  state, ordered batch processing, and rejection without writes. A judge checks
  shared rule ownership, proportionality, and adapter regression coverage.
  Scalameta also checks constructor-supplied business dependencies and rejects
  business collaborator construction inside adapters; application wiring is
  permitted to construct them.
  Its 5 starter tests pass.

The legacy order case was removed because with-skill and without-skill agents
produced the same fully passing patch, so it did not measure skill value.

Run the complete comparison from the repository root:

```bash
scala tests/run-eval.scala --server=false -- output
```

See [`tests/README.md`](../../tests/README.md) for repeat runs, independent
judges, and output paths.

See [analysis.md](../../analysis.md) for the section-by-section coverage analysis
and proposed scenarios.

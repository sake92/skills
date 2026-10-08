# Pragmatic Architecture

Maintainer documentation; evaluated agents read [`SKILL.md`](SKILL.md).

A compact architecture skill: <100 lines.
With-skill candidates used about 25% more tokens in these runs, not a guaranteed overhead of course.

## Latest benchmark

Each cell lists **with skill / without skill**. One paired run per repo.

| Repo/setup | Score | Candidate seconds | Tokens |
| --- | ---: | ---: | ---: |
| `customer-repository` | 10/10 / 7/10 | 58.8 / 49.7 | 12,892 / 11,138 |
| `repository-with-callers` | 8/8 / 5/8 | 49.7 / 38.5 | 13,263 / 13,803 |
| `legacy-discount-fix` | 8/8 / 8/8 | 48.5 / 49.3 | 14,749 / 11,652 |
| `booking-overlap-rule` | 12/12 / 12/12 | 95.1 / 58.3 | 28,418 / 20,024 |

See [benchmark-results.md](benchmark-results.md) for setup, coverage, and interpretation.

## Evaluation

Run all four cases from the repository root:

```bash
scala tests/run-eval.scala --server=false -- output
```

See [tests/README.md](../../tests/README.md) for the evaluation workflow and
[analysis.md](../../analysis.md) for section-by-section coverage and proposed scenarios.

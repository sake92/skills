# Codeps

Use [dependency evidence](SKILL.md) to choose a focused boundary change and verify
it after a fresh build. The skill also covers runtime-loading limits, stale
artifacts, and complementary Git-history evidence.

| Example | With skill | Without skill | Score gain (pp) |
| --- | ---: | ---: | ---: |
| [Layered dependency knot](benchmark-results/dependency-knot.md) | 7/7 | 5/7 | +28.6 |
| [Stale report and runtime plugins](benchmark-results/report-triage.md) | 6/6 | 3/6 | +50.0 |

Score gain is the difference in pass rates, in percentage points. These are
single-run examples after skill tuning on frozen cases; generalization has not
been tested. Both arms have the same Codeps
tool access. Protected Scala/MUnit checks use independently regenerated graphs;
there is no LLM judge.

[Evaluation workflow](../../tests/codeps/README.md)

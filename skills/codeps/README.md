# Codeps

Use [dependency evidence](SKILL.md) to choose a focused boundary change and verify
it after a fresh build. The skill also covers runtime-loading limits, stale
artifacts, and complementary Git-history evidence.

| Example | With skill | Without skill | Score gain (pp) |
| --- | ---: | ---: | ---: |
| [Layered dependency knot](benchmark-results/dependency-knot.md) | 7/7 | 6/7 | +14.3 |
| [Stale report and runtime plugins](benchmark-results/report-triage.md) | 5/6 | 3/6 | +33.3 |

These are the 2026-10-14 rerun with Codeps 0.6.0: **12/13 with skill versus
9/13 without** (+23.1 percentage points across pooled checks). Triage still
omits a fresh report; the rerun is below the 100% target. Completed-pair tokens
are essentially flat: 76,066 with skill versus 75,512 without (+0.7%).

Score gain is the difference in pass rates, in percentage points. Each case has
one completed matched pair using Pi 1.0.4, GPT-6 Luna at low reasoning, equal
tool access and permissions, and a five-minute candidate limit. Invalid
timeouts and credential-lock attempts were retained locally and excluded.
The skill was previously tuned on these cases; this rerun is not held-out
evidence. Protected Scala/MUnit checks independently regenerate graphs; there
is no LLM judge.

[Evaluation workflow](../../tests/codeps/README.md)

# Worker timeout: baseline-first development case

| Configuration | Protected checks | Candidate time | Input + output tokens |
| --- | ---: | ---: | ---: |
| With skill | 6/7 | 113.50s | 38,083 |
| Without skill | 4/7 | 82.21s | 20,874 |

Both candidates completed fixes and passed their visible 3/3 tests. With-skill stopped resistant and nested helpers after their parent exited. It still leaked replacement work started during shutdown. Baseline handled that replacement but lost track of helpers when their parent exited. The net difference is two checks in favor of with-skill, at higher time and token cost.

## Fixture and validation

The [revised starter](../../../tests/process-hygiene/worker-timeout/starter/) models a long-lived build launcher, attempt logs and an external worker/helper toolchain. The symptom-only task requests a fix for continuing progress and busy retries after timeout, preserving public APIs and documented budgets. It does not disclose grader scenarios or a cleanup algorithm.

Seven protected checks exercise ordinary exit results and launch/log settings, resistant direct workers, cooperative helper cleanup, resistant helpers after parent exit, nested helpers, one overall cleanup budget, and replacement helpers spawned during graceful termination. These map to the skill's process ownership, graceful/forced shutdown, bounded waits and JVM reparenting guidance.

The starter passes its visible 2/2 tests and scores 2/7 protected checks. An independent corrected reference passes 7/7. The earlier single-snapshot implementation scores 6/7, leaking the replacement helper. A deliberate serial-grace mutant previously failed the elapsed-time check, confirming the deadline assertion detects a distinct bug.

The protected Linux grader adopts and reaps fixture helpers through a JNA subreaper without signalling live helpers before assertions. This avoids dependency on container PID 1 behavior while preserving observable leaks. It does not establish portable descendant reaping by candidate code.

## What separated the outputs

Baseline attempts force-killing descendants only if the parent remains alive:

```scala
if process.isAlive then
  process.descendants().forEach(child => child.destroyForcibly())
  process.destroyForcibly()
```

With-skill retains handles before signalling:

```scala
val descendants = process.toHandle.descendants().toList
descendants.forEach(_.destroy())
if process.isAlive then process.destroy()
```

It force-stops surviving saved handles and waits for parent and descendants. This matches the existing JVM reference's explanation of reparenting. Its fixed snapshot misses replacement work started later; the reference's additional discovery step passes that check. The correspondence is consistent with guidance helping, not proof that a particular sentence caused the improvement.

With-skill passes ordinary execution, direct-worker timeout, cooperative cleanup, resistant reparented helpers, nested helpers and the six-helper budget scenario. Baseline passes the first three and replacement-helper cleanup. Its budget scenario fails on surviving helpers before the elapsed-time assertion, so retained ownership explains all three improved checks rather than demonstrating three independent defects.

## Reproduction and limits

- Baseline ran first, then coverage was frozen before one with-skill run. Same `gpt-6-luna`, reasoning `low`, Pi 1.0.4, Scala 3.9.0 and MUnit 1.3.6; no qualitative judge. The prompt, public starter and skill were unchanged from the earlier development iteration. The with-skill transcript confirms skill loading.
- This case was strengthened after inspecting an earlier baseline. It is an adaptive development result and needs confirmation on a fresh held-out case. It is not an unbiased estimate of effectiveness.
- Repository base `b78ba67`; fixture and definitions were uncommitted during evaluation. Combined artifacts verify identical starter Git trees, task metadata and SKILL.md. Protected assertions stay outside candidate workspaces.
- Raw runs: `tests/tmp/process-hygiene-workspace/iteration-20261009-194259/` (baseline) and `iteration-20261009-194504/` (with skill). Combined benchmark, patches, grades, transcripts, snapshots and viewer: `tests/tmp/process-hygiene-workspace/comparison-20261009-194504/`. Grader proofs: `tests/tmp/worker-timeout-grader-proof/revised-*.json` and matching logs. Generated artifacts stay ignored.
- Candidate time excludes grading: another 19.29s baseline and 20.65s with skill. Tokens count input plus output, excluding cached input.
- Grader SHA-256: `53d306387ac23028d96b10ab710e8e911fa1644bf10ce59f6b1386d07f810078`. SKILL.md: `68e40360ab31d32a131a95ac646631f5f3a5ac7c7fbe70fea2a7e7aad41f9776`.
- Cancellation, normal early exits with surviving helpers, thread ownership and arbitrary process-tree races remain unevaluated.

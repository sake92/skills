# Worker timeout: baseline-first development case

| Configuration | Protected checks | Candidate time | Input + output tokens |
| --- | ---: | ---: | ---: |
| With skill | 6/7 | 113.50s | 38,083 |
| Without skill | 4/7 | 82.21s | 20,874 |

Score gain: **+28.6 percentage points** (57.1% → 85.7%).

Both candidates completed fixes and passed their visible 3/3 tests. With-skill stopped resistant and nested helpers after their parent exited. It still leaked replacement work started during shutdown. Baseline handled that replacement but lost track of helpers when their parent exited. The net difference is two checks in favor of with-skill, at higher time and token cost.

## Fixture and validation

The [revised starter](../../../tests/process-hygiene/worker-timeout/starter/) models a long-lived build launcher, attempt logs and an external worker/helper toolchain. The symptom-only task requests a fix for continuing progress and busy retries after timeout, preserving public APIs and documented budgets. It does not disclose grader scenarios or a cleanup algorithm.

Seven protected checks exercise ordinary exit results and launch/log settings, resistant direct workers, cooperative helper cleanup, resistant helpers after parent exit, nested helpers, one overall cleanup budget, and replacement helpers spawned during graceful termination. These map to the skill's process ownership, graceful/forced shutdown, bounded waits and JVM reparenting guidance.

The starter passes its visible 2/2 tests and scores 2/7 protected checks. An independent corrected reference passes 7/7. A deliberate serial-grace mutant fails the elapsed-time check, confirming the deadline assertion detects a distinct bug.

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

- Baseline ran first, then coverage was frozen before the with-skill run. No qualitative judge was used. The with-skill transcript confirms skill loading.
- This case was strengthened after inspecting an earlier baseline. It is an adaptive development result and needs confirmation on a fresh held-out case. It is not an unbiased estimate of effectiveness.
- Protected grading took another 19.29s baseline and 20.65s with skill.
- Cancellation, normal early exits with surviving helpers, thread ownership and arbitrary process-tree races remain unevaluated.

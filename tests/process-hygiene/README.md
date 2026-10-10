# Process hygiene evaluations

`build-task-stdio` models a build launcher's real execution path: configured process launch, stream forwarding, task events/results, and the command-entrypoint adapter. The fixture is original Scala code adapted from responsibilities in Mill and Deder, not a verbatim extract. The rejected command-wrapper example has been removed.

The two existing tests start green. Protected MUnit checks stay outside candidate workspaces and invoke actual Linux `/bin/sh` tools. They check a live prompt/input handshake, separate byte-preserving stdout/stderr, binary document input and EOF, batch input isolation, exit-result propagation, large output, borrowed-stream lifetime and successive-task routing, plus preservation of arguments, cwd and environment.

The skill's Subprocesses section covers pipe draining and exit/failure propagation. Its ownership principle covers the distinction between process-owned pipes and caller-owned task/input streams. Exact byte and input-policy behavior also follows the fixture's public API contract. The stdio example does not evaluate cancellation, descendant cleanup, operation deadlines, executor ownership, or service startup/shutdown.

The grader bounds each invocation at ten seconds. It independently kills its fixture process tree and releases handshake streams and guard threads, so a broken submission cannot hang the suite. These outer bounds contain tests; they do not require candidates to add timeout APIs. Validate the green starter against the protected grader, then verify a corrected reference passes all checks.

For new cases, run and inspect the baseline before skill tuning. Freeze the prompt, fixtures, and grader, then run a with-skill smoke before a larger comparison. Keep model, reasoning, tools, permissions, and budget matched. The commands below rerun the existing example pair:

```bash
scala tests/run-eval.scala --server=false -- output --skill-path skills/process-hygiene --fixture-path tests/process-hygiene --case build-task-stdio --configuration without-skill --runs 1 --skip-judge --parallelism 1
scala tests/run-eval.scala --server=false -- output --skill-path skills/process-hygiene --fixture-path tests/process-hygiene --case build-task-stdio --configuration with-skill --runs 1 --skip-judge --parallelism 1
```

Inspect candidate patches and preserve generated runs, grading reports, snapshots and the review viewer under ignored `tests/tmp/`. Commit only example-run summaries and small code/diff excerpts under `benchmark-results/`. A single pair gives initial evidence, not a stable reliability estimate.

## Worker timeout development case

`worker-timeout` is the revised seven-check fixture. Its two visible tests start green. The symptom-only prompt reports progress and workspace contention after timeout without naming the ownership mechanism. Protected checks cover ordinary results and logs, resistant direct workers, cooperative cleanup, retained helpers after parent exit, nested helpers, one shared budget, and replacement work started during TERM handling.

These validate the skill's subprocess ownership, graceful/forced termination and bounded-wait guidance. The protected Linux grader uses a JNA subreaper to adopt and reap fixture helpers; it never signals live helpers before assertions. Independent containment stops remaining fixture processes after failed assertions. This measures observable termination and workspace release rather than portable descendant reaping by the candidate.

The starter scores 2/7, the corrected reference 7/7, and the earlier single-snapshot implementation 6/7. A fresh baseline ran first, then coverage was frozen before with-skill: [6/7 versus 4/7](../../skills/process-hygiene/benchmark-results/worker-timeout-development.md). This is an adaptive development comparison. Use the commands above with `--case worker-timeout` to select it; omitting `--case` runs both fixtures.

## Worker cancellation case

`worker-cancellation` starts with the worker attempt API and normal tree cleanup already implemented. The scheduler owns the calling thread and cancels by interrupting it. The starter incorrectly converts interruption to a timeout report; its cleanup waits also abort on interruption. The task describes cancellation symptoms without revealing timing scenarios or implementation helpers.

Six protected checks cover ordinary exits/logs, uncancelled timeout, graceful cancellation and workspace release, resistant helpers after parent exit, repeated interruption during cleanup, and cancellation during timeout cleanup. These validate cancellation propagation, cancellable waits, retained subprocess ownership and bounded cleanup. Cancellation must remain observable after cleanup rather than being returned as success or timeout. Propagating `InterruptedException` is the public API contract; the grader does not require the interrupt flag also to remain set after propagation.

Readiness and TERM markers synchronize the protected scenarios. A grader-owned caller thread records its actual outcome; assertions check both the returned/thrown result and live fixture processes/workspace locks. Linux subreaper containment reaps helpers without signalling them before assertions. The starter passes its two visible tests and scores 2/6 protected checks. A corrected reference scores 6/6; a budget-restarting mutant scores 5/6, taking about 1.56s against a 1.00s cancellation limit.

Select this case using `--case worker-cancellation`. Baseline ran first, then coverage was frozen. The original skill tied at 5/6; after two guidance revisions, a with-skill run passed 6/6 against the retained 5/6 baseline. See the [full development history](../../skills/process-hygiene/benchmark-results/worker-cancellation.md). Raw proofs and candidate artifacts remain under ignored `tests/tmp/`.

## Source responsibilities

- Mill `core/api/src/mill/api/SystemStreamsUtils.scala`: task-specific stream routing, raw inheritance and input policy.
- Mill `core/constants/src/mill/constants/InputPumper.java`: incremental byte forwarding and flushing.
- Mill `integration/feature/subprocess-stdout/`: process output observed through the build launcher.
- Deder `server/src/ba/sake/deder/testing/forked/ForkedTestOrchestrator.scala`: launch configuration, output handling and interpretation of worker completion.

The fixture deliberately omits Mill daemon transport and global stream proxies, and Deder's structured test-event protocol and cancellation machinery. It retains a complete process-to-task execution path without requiring either repository's full build.

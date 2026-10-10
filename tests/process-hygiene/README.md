# Process hygiene evaluations

`build-task-stdio` models a build launcher's real execution path: configured process launch, stream forwarding, task events/results, and the command-entrypoint adapter. The fixture is original Scala code adapted from responsibilities in Mill and Deder, not a verbatim extract. The rejected command-wrapper example has been removed.

The two existing tests start green. Protected MUnit checks stay outside candidate workspaces and invoke actual Linux `/bin/sh` tools. They check a live prompt/input handshake, separate byte-preserving stdout/stderr, binary document input and EOF, batch input isolation, exit-result propagation, large output, borrowed-stream lifetime and successive-task routing, plus preservation of arguments, cwd and environment.

The skill's Subprocesses section covers pipe draining and exit/failure propagation. Its ownership principle covers the distinction between process-owned pipes and caller-owned task/input streams. Exact byte and input-policy behavior also follows the fixture's public API contract. The stdio example does not evaluate cancellation, descendant cleanup, operation deadlines, executor ownership, or service startup/shutdown.

The grader bounds each invocation at ten seconds. It independently kills its fixture process tree and releases handshake streams and guard threads, so a broken submission cannot hang the suite. These outer bounds contain tests; they do not require candidates to add timeout APIs. Validate the green starter against the protected grader, then verify a corrected reference passes all checks.

Run one with-skill smoke before its matched baseline, keeping the prompt, fixtures, grader, model, reasoning, and skill fixed. The two runs form the initial comparison:

```bash
scala tests/run-eval.scala --server=false -- output --skill-path skills/process-hygiene --fixture-path tests/process-hygiene --case build-task-stdio --configuration with-skill --runs 1 --skip-judge --parallelism 1
scala tests/run-eval.scala --server=false -- output --skill-path skills/process-hygiene --fixture-path tests/process-hygiene --case build-task-stdio --configuration without-skill --runs 1 --skip-judge --parallelism 1
```

Inspect candidate patches and preserve generated runs, grading reports, snapshots and the review viewer under ignored `tests/tmp/`. Commit only example-run summaries and small code/diff excerpts under `benchmark-results/`. A single pair gives initial evidence, not a stable reliability estimate.

## Source responsibilities

- Mill `core/api/src/mill/api/SystemStreamsUtils.scala`: task-specific stream routing, raw inheritance and input policy.
- Mill `core/constants/src/mill/constants/InputPumper.java`: incremental byte forwarding and flushing.
- Mill `integration/feature/subprocess-stdout/`: process output observed through the build launcher.
- Deder `server/src/ba/sake/deder/testing/forked/ForkedTestOrchestrator.scala`: launch configuration, output handling and interpretation of worker completion.

The fixture deliberately omits Mill daemon transport and global stream proxies, and Deder's structured test-event protocol and cancellation machinery. It retains a complete process-to-task execution path without requiring either repository's full build.

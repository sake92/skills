# Parallel build: development comparison

| Configuration | Protected checks | Candidate time | Input + output tokens |
| --- | ---: | ---: | ---: |
| No skill baseline | 7/8 | 95.48s | 23,252 |
| Original skill | Did not complete; diagnostic 5/8 | 180.03s limit | 36,351 |
| First skill revision | 7/8 | 130.51s | 23,859 |
| Final skill revision | **8/8** | 94.74s | 22,324 |

The final output joins actual worker threads after executor termination, allowing supplied-factory resource scopes to finish before reporting cancellation. Baseline passes the other seven checks but returns while that scope is still closing. The final run uses 928 fewer input/output tokens and takes 0.74s less than baseline. These are adaptive single-run observations, not a reliability estimate.

The [fixture](../../../tests/process-hygiene/parallel-build/starter/) is an original Scala build-launch path with a request/result model, supplied thread factory, executor owner, runner and command adapter. Its visible tests start green. The symptom-only prompt reports hangs after module failures and workspace activity after cancellation; it does not name completion queues, handle retention or protected timing scenarios. The README documents the lifecycle contract, including worker-local scopes. This contract is an explicit hint shared by both configurations.

## Protected behavior and skill properties

| Protected behavior | Skill guidance |
| --- | --- |
| Concurrent success preserves result order and the adapter | Preserve public behavior while fixing ownership |
| Active concurrency respects configured parallelism | Bound fan-out |
| A later failure cancels an earlier blocked sibling | Observe concurrent failures independently of result order |
| Caller cancellation stops active and queued work | Propagate cancellation and stop intake |
| Cancellation joins task finalizers and worker threads | Cancel and join owned tasks/executors |
| Repeated cleanup interruption preserves original failure | Finish bounded cleanup despite further cancellation |
| Failed submission releases already-started tasks | Retain handles during partial acquisition |
| Factory resource scopes finish before cancellation returns | Join the actual worker lifetime |

The starter passes 2/2 visible tests and scores 2/8 protected checks. An independent corrected reference scores 8/8. A mutant that cancels without waiting scores 5/8, failing task-finalizer, repeated-cleanup and worker-scope checks. Latches synchronize real cooperative tasks; the supplied factory exposes actual worker threads. Daemon flags contain failed outputs but do not satisfy termination checks. Guard cleanup runs after assertions.

## Development history

The first seven-check draft had a **7/7 no-skill baseline**, taking 106.05s and 17,056 tokens. It was rejected as non-discriminating. Adding a legitimate ownership boundary exposed a real gap: `ThreadPoolExecutor` can report termination before a supplied factory's outer worker scope finishes. Regrading the old patch against the revised grader gives 7/8. A fresh baseline then scores 7/8 on the revised fixture. This selection is adaptive; the original perfect baseline remains in the history.

The eight-check case, task and starter were frozen before with-skill. The original skill output still waits for futures in submission order. Its added regression hangs behind an earlier blocked task, and the candidate hits the three-minute limit. The runner produces no official benchmark for this noncompletion. A separate diagnostic run excluding candidate tests scores its unfinished patch 5/8: it misses later-failure observation, cleanup after that failure and factory-scope finalization. This diagnostic is not a completed-agent result.

The first skill revision clarifies prompt failure observation, immediate handle retention during acquisition, and actual worker lifetime. Its completed output scores 7/8, tying baseline. It tracks `Thread.currentThread()` inside the inner executor runnable and removes it in that runnable's `finally`; the tracked set becomes empty before the factory's outer scope finishes. The visible suite passes 3/3.

The second revision explicitly says to retain threads returned by the supplied factory until actual termination, rather than tracking active inner runnables. Its output scores 8/8 with 3/3 visible tests passing. The separating implementation is:

```scala
val thread = factory.newThread(runnable)
if thread != null then workers.add(thread)
thread
```

After awaiting executor termination, it joins retained workers under the same cleanup deadline:

```scala
while thread.isAlive && System.nanoTime() < deadline do
  try
    val left = deadline - System.nanoTime()
    if left > 0 then TimeUnit.NANOSECONDS.timedJoin(thread, left)
  catch case _: InterruptedException => interrupted = true
```

Baseline only awaits executor termination. The first revision loses worker handles when the inner runnable exits. All skill revisions reuse the same frozen 7/8 baseline; neither changes the prompt, starter or protected checks. Instruction changes and ordinary run variation cannot be separated by this development comparison.

## Reproduction and artifacts

- Same `gpt-6-luna`, reasoning `low`, Pi 1.0.4, Scala 3.9.0, MUnit 1.3.6 and three-minute candidate limit. No qualitative judge. Commands are in [evaluation maintenance](../../../tests/process-hygiene/README.md), using `--case parallel-build` and `--timeout-minutes 3`.
- Repository base `e4e8917`; fixture and guidance revisions were uncommitted. Combined artifacts verify identical eight-check task metadata and starter Git trees. Assertions remain outside candidate workspaces.
- Ignored runs under `tests/tmp/process-hygiene-workspace/`: `iteration-20261010-165850` (rejected draft baseline), `iteration-20261010-170500` (revised fresh baseline), `iteration-20261010-170702` (original-skill noncompletion), `iteration-20261010-171109` (first revision), and `iteration-20261010-171359` (final revision). Comparisons/viewers: `comparison-20261010-171109` and `comparison-20261010-171359`. Proofs and diagnostic grading: `tests/tmp/parallel-build-grader-proof/`.
- Candidate time excludes grading: 11.06s baseline, 10.86s first revision, and 10.52s final revision. Tokens count input plus output, excluding cached input.
- Frozen grader SHA-256: `c9a353619f3b7a2d69e423d0f56d68a9e7f388323c1c0917d67c8fb5637744c1`. SKILL.md revisions: `ac795cb5e86ccf431b42c664132e830be9a54e64636d74b5910bf7f5664a51c0`, `915e2b49d10cfb2869239ac0a0056971adcef8640f8ff78ef743e40298a1d491`, and `8dc672d133d54e18dfca14accad9623ef20bb877e042ba29f1034deda79be4c6`. Snapshots retain corresponding references.
- Coverage is limited to cooperative platform-thread build tasks. The repeated-interruption check confirms resource release and original-failure preservation; it does not independently discriminate budget renewal. It does not cover uncooperative-task budget exhaustion, cancellation during acquisition or successful-completion cleanup, virtual-thread behavior, service shutdown, or signal handling. The final candidate does not report incomplete cleanup when its budget expires, nor propagate an interrupt arriving only during successful-completion cleanup as `InterruptedException`; those remain ungraded gaps despite its 8/8 score. A less guided held-out case should test these and confirm the observed benefit.

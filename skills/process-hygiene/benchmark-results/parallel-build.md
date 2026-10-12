# Parallel build

| Configuration | Protected checks | Candidate time | Input + output tokens |
| --- | ---: | ---: | ---: |
| No skill baseline | 7/8 | 95.48s | 23,252 |
| With skill | **8/8** | 94.74s | 22,324 |

Score gain: **+12.5 percentage points** (87.5% → 100%).

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

## What separated the outputs

With-skill retains threads returned by the supplied factory until actual termination. Its output scores 8/8 with 3/3 visible tests passing. The separating implementation is:

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

Baseline only awaits executor termination. The case and skill were developed adaptively; this single comparison against the retained baseline needs held-out confirmation.

## Reproduction and artifacts

- The candidate limit was three minutes. No qualitative judge was used. Commands are in [evaluation maintenance](../../../tests/process-hygiene/README.md), using `--case parallel-build` and `--timeout-minutes 3`.
- Protected grading took another 11.06s baseline and 10.52s with-skill.
- Coverage is limited to cooperative platform-thread build tasks. The repeated-interruption check confirms resource release and original-failure preservation; it does not independently discriminate budget renewal. It does not cover uncooperative-task budget exhaustion, cancellation during acquisition or successful-completion cleanup, virtual-thread behavior, service shutdown, or signal handling. The final candidate does not report incomplete cleanup when its budget expires, nor propagate an interrupt arriving only during successful-completion cleanup as `InterruptedException`; those remain ungraded gaps despite its 8/8 score. A less guided held-out case should test these and confirm the observed benefit.

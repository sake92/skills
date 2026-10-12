# Worker cancellation

| Configuration | Protected checks | Candidate time | Input + output tokens |
| --- | ---: | ---: | ---: |
| No skill baseline | 5/6 | 59.85s | 17,863 |
| With skill | **6/6** | 82.06s | 23,307 |

With-skill passes repeated cancellation during cleanup; baseline leaves the root alive. All other checks pass in both. The with-skill run uses 5,444 more input/output tokens and takes 22.22s longer than the retained baseline. It is adaptive single-run evidence, not a reliability estimate.

The new [fixture](../../../tests/process-hygiene/worker-cancellation/starter/) starts with normal process-tree cleanup implemented. Its bugs are converting interruption into a timeout report and allowing later interrupts to abort cleanup. The symptom-only prompt mentions incorrect cancellation reports and busy workspaces, without revealing protected timing scenarios or implementation helpers.

## Protected behavior and skill properties

| Protected behavior | Skill guidance |
| --- | --- |
| Ordinary exits preserve status, settings and logs | Preserve exit status and failure propagation |
| Uncancelled deadlines still return TimedOut after termination | Finite commands and bounded cleanup |
| Cancellation propagates after graceful workspace release | Cancellation propagation and resource ownership |
| Resistant helpers stop after parent exit | Descendant ownership across reparenting |
| Repeated cancellation cannot abandon cleanup or renew its budget | Bounded cleanup despite further cancellation |
| Cancellation during timeout cleanup remains cancellation | Cancellation remains observable through cleanup |

The starter passes its visible 2/2 tests and scores 2/6 protected checks. An independent corrected reference scores 6/6. A mutant that restarts grace and cleanup budgets after each interrupt scores 5/6, taking 1.56s against a 1.00s limit. The protected grader records real caller outcomes, synchronizes on readiness and TERM markers, verifies process termination and workspace locks, and independently contains failures. Its Linux subreaper reaps helpers without signalling live helpers before assertions.

## What separated the outputs

The fresh no-skill baseline scores 5/6. It propagates cancellation and cleans up after one interrupt, but repeated interrupts escape from cleanup waits and leave the root alive. Its visible 3/3 tests pass.

With-skill scores 6/6. Its candidate records interruptions inside the individual cleanup waits, continues grace/force/reaping under the original deadline, then propagates `InterruptedException`. Its visible suite passes 3/3. Its added regression checks that work stops after cancellation but does not capture the caller's thrown outcome or synchronize readiness; the protected grader supplies those stronger checks.

The mechanism that separates the final output is:

```scala
while handles.exists(_.isAlive) && System.nanoTime() < limit do
  try Thread.sleep(5)
  catch case _: InterruptedException => interrupted = true
```

The candidate applies the same approach to the direct child's `waitFor`, then throws cancellation after termination. The skill was tuned on this case, so this adaptive comparison needs held-out confirmation.

## Reproduction and artifacts

- The prompt, starter, and grader were matched. No qualitative judge was used. The recorded comparison uses the retained baseline after adaptive skill tuning.
- Protected grading took another 12.51s baseline and 12.94s with-skill.
- These checks cover cancellation during active work and timeout cleanup, including repeated interruption. They do not cover cancellation during startup, preservation of original exception identity, cleanup I/O failure precedence, executor ownership, service shutdown, or portable adopted-child reaping by candidate code. Held-out confirmation remains necessary.

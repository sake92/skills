# Worker cancellation: development comparison

| Configuration | Protected checks | Candidate time | Input + output tokens |
| --- | ---: | ---: | ---: |
| No skill baseline | 5/6 | 59.85s | 17,863 |
| Original skill | 5/6 | 75.65s | 32,504 |
| First skill revision | 5/6 | 84.81s | 27,449 |
| Final skill revision | **6/6** | 82.06s | 23,307 |

The final revision passes repeated cancellation during cleanup; baseline leaves the root alive. All other checks pass in both. The initial tie and failed first revision remain part of the development history. The final run uses 5,444 more input/output tokens and takes 22.22s longer than the retained baseline. It is adaptive single-run evidence, not a reliability estimate.

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

## Iteration history

The fresh no-skill baseline scores 5/6. It propagates cancellation and cleans up after one interrupt, but repeated interrupts escape from cleanup waits and leave the root alive. Its visible 3/3 tests pass.

The initial with-skill run also scores 5/6. It clears and restores the interrupt flag at cleanup entry but leaves subsequent waits interruptible. Its visible 4/4 tests pass. This is a tie, with no demonstrated benefit from the original skill.

The first skill revision clarifies that further cancellation must be remembered while bounded cleanup finishes under its original deadline. A new with-skill run still scores 5/6: the candidate retries the entire cleanup operation after interruption, stopping processes but renewing the budget. Repeated cancellation takes 1.59s against the 1.00s limit. This failure is preserved.

The second revision explicitly distinguishes deferring interruption at individual cleanup waits from retrying the whole cleanup operation. Neither revision changes the task prompt, starter or protected assertions. All later with-skill runs reuse the same no-skill baseline. These are adaptive development iterations rather than independent matched repetitions or held-out evidence.

That revision scores 6/6. Its candidate records interruptions inside the individual cleanup waits, continues grace/force/reaping under the original deadline, then propagates `InterruptedException`. Its visible suite passes 3/3. Its added regression checks that work stops after cancellation but does not capture the caller's thrown outcome or synchronize readiness; the protected grader supplies those stronger checks.

The mechanism that separates the final output is:

```scala
while handles.exists(_.isAlive) && System.nanoTime() < limit do
  try Thread.sleep(5)
  catch case _: InterruptedException => interrupted = true
```

The candidate applies the same approach to the direct child's `waitFor`, then throws cancellation after termination. The initial output only cleared the flag at cleanup entry. The first revision's output retried the entire cleanup method with new deadlines. The final output matches the refined guidance, but this adaptive comparison cannot isolate instruction effects from run-to-run variation.

## Reproduction and artifacts

- Fresh baseline first, then an initial with-skill run and two with-skill development revisions. Same prompt, starter, grader, `gpt-6-luna`, reasoning `low`, Pi 1.0.4, Scala 3.9.0 and MUnit 1.3.6. No qualitative judge. Only the skill changed between revisions; the no-skill baseline was reused rather than rerun.
- Repository base `d11c0df`; new fixture and skill revisions were uncommitted during evaluation. Combined artifacts verify identical task metadata and starter Git trees. Assertions stay outside candidate workspaces.
- Raw runs under `tests/tmp/process-hygiene-workspace/`: `iteration-20261010-142410` (baseline), `iteration-20261010-142551` (original skill), `iteration-20261010-142810` (first revision), and `iteration-20261010-143107` (final revision). Combined benchmarks, complete patches, grades, transcripts, snapshots and viewers: `comparison-20261010-142551`, `comparison-20261010-142810`, and `comparison-20261010-143107`. Proofs: `tests/tmp/worker-cancellation-grader-proof/`. All generated artifacts stay ignored.
- Candidate time excludes protected grading: 12.51s baseline, 12.37s original skill, 15.26s first revision, and 12.94s final revision. Tokens count input plus output, excluding cached input.
- Grader SHA-256 stayed `02173308e93a2c0997cadb5d22837b6938d6a34ffd62a0ae6c3a7bba6100f79a`. SKILL.md changed from `68e40360ab31d32a131a95ac646631f5f3a5ac7c7fbe70fea2a7e7aad41f9776` to `73b573a1ac648f9a23739201747fb4ed31d916b58fabf760ff951898544df48f`, then `ac795cb5e86ccf431b42c664132e830be9a54e64636d74b5910bf7f5664a51c0`. Snapshot references preserve the corresponding JVM guidance.
- These checks cover cancellation during active work and timeout cleanup, including repeated interruption. They do not cover cancellation during startup, preservation of original exception identity, cleanup I/O failure precedence, executor ownership, service shutdown, or portable adopted-child reaping by candidate code. Held-out confirmation remains necessary.

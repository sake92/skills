# Build-task stdio: initial pair

| Configuration | Protected checks | Candidate time | Input + output tokens |
| --- | ---: | ---: | ---: |
| With skill | 8/8 | 102.40s | 30,390 |
| Without skill | 7/8 | 86.67s | 18,485 |

The with-skill output passed the live prompt/input handshake; the baseline stalled because it flushed the task output only after EOF. All seven other checks passed in both configurations. This single pair favored the with-skill output on one behavior, at higher candidate time and token cost; it does not establish reliable uplift or prove that a particular instruction caused the difference.

## Fixture and coverage

The [starter](../../../tests/process-hygiene/build-task-stdio/starter/) is a standalone Scala build-task path across four production files: launch/input models, a stream pump, a forked-tool runner, and task-event/result handling with a launcher adapter. It adapts responsibilities observed in Mill and Deder; it is original fixture code, not a verbatim extraction. It replaces the rejected command-wrapper example, which was removed along with its report and generated runs.

The starter has two passing tests. Protected checks invoke actual Linux tools and verify:

- A prompt without a newline is forwarded and flushed before input is supplied.
- Stdout/stderr separation, exact bytes, whitespace and final-newline state.
- Binary document input and EOF.
- Batch EOF without consuming or closing launcher input.
- Exit codes 0, 7 and 23 through task outcomes, completion events and the launcher adapter.
- Complete 512 KiB output on each stream without pipe deadlock.
- Caller-owned output and interactive-input streams remain open; subsequent tasks retain correct routing.
- Argument vectors, cwd and environment overrides retain their behavior.

Before agent runs, the starter passed its visible 2/2 tests and scored 1/8 protected checks. The corrected reference passed 2/2 visible and 8/8 protected checks. The starter's two hanging checks hit the grader's ten-second bound and cleaned up. Both final agents' visible suites passed 4/4.

## What separated the outputs

Both agents started independent input, stdout and stderr pumps, preserved borrowed streams, and propagated the actual exit code through `BuildTask`. The difference was incremental flushing. With skill:

```scala
while count != -1 do
  destination.write(buffer, 0, count)
  destination.flush()
  count = source.read(buffer)
```

The baseline writes during the loop but flushes after EOF. Its own prompt test uses an unbuffered `ByteArrayOutputStream`, which makes writes immediately observable without a flush. The protected sink dispatches a response only when the forwarded prompt is flushed, modeling a task transport that buffers output. The child waits for that response, so an EOF-only flush causes the handshake to time out. Neither test depends on a particular pump implementation or method name.

## Reproduction and limits

- One with-skill smoke followed by one matched baseline: `gpt-6-luna`, reasoning `low`, Pi 1.0.4, Scala 3.9.0 and MUnit 1.3.6. No qualitative judge. Instructions and graders stayed fixed; identical starter Git trees and task metadata are checked when combining results. The with-skill transcript confirms SKILL.md was read.
- Repository base: `b78ba67`; the new fixture and eval definitions were uncommitted during evaluation. Skill instructions were unchanged.
- Source repositories inspected: Mill `3e2de013bc3ff6fe65746a51e051adc05d244a6d`, Deder `0d965f9babc73336b8985eb234cbfb614b3fd4ec`. Relevant source paths are listed in [the eval README](../../../tests/process-hygiene/README.md).
- Raw runs: `tests/tmp/process-hygiene-workspace/iteration-20261009-161650/` and `iteration-20261009-161907/`. Combined benchmark, complete patches, grades, transcripts, snapshots and `review.html`: `tests/tmp/process-hygiene-workspace/comparison-20261009-161650/`. Grader proof: `tests/tmp/build-task-stdio-grader-proof/`. These generated artifacts stay ignored; only this summary and excerpt belong in the repository report.
- Time measures candidate execution; tokens count input plus output, excluding cached input. Protected grading took another 11.13s with skill and 21.09s without, including the baseline's ten-second handshake timeout.
- This fixture covers normal stdio completion and input/output ownership. It does not validate cancellation, descendants, operation deadlines, pump-failure propagation, executors, service lifecycle, or real terminal/PTY inheritance. Those require separate cases.

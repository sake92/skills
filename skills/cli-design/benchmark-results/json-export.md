# Inventory JSON export

| Configuration | Protected checks | Candidate time | Input + output tokens |
| --- | ---: | ---: | ---: |
| Retained no-skill baseline | 5/9 | 145.049s | 30,392 |
| With concrete-reproducer skill | **8/9** | 153.323s | 32,693 |

Score gain: **+33.3 percentage points** (55.6% → 88.9%). Existing table behavior is preserved. The baseline passes 5/5 visible tests; the selected with-skill candidate passes 6/6. Candidate times exclude grading; token counts exclude cached input. Protected grading takes another 17.743s baseline and 11.457s with skill.

The [task](../../../tests/cli-design/json-export/prompt.md) adds JSON output to a focused Scala inventory exporter with table and optional file output. The fixture is original, not a production checkout. Its README describes existing contracts, providing the same compatibility hints to both configurations.

## What improved

The skill includes language-independent boundary-test examples covering malformed-file diagnostics and destructive confirmation. The selected output keeps JSON file completion messages on stderr and refuses unattended replacement before reading stdin:

```scala
if Files.exists(path) && !options.force then
  if !terminal then
    err.println(s"Cannot replace $path without an interactive terminal; use --force to replace the file.")
    return 1
  err.print(s"Replace $path? [y/N] ")
```

Both JSON file-output checks and the unattended-overwrite check improve against the retained baseline. The file-output checks share one stdout concern and are not independent demonstrations of two benefits.

The candidate edits tests before production, but the transcript has no added-test red run before the first production edit. It reports red-to-green feature tests after implementation work begins, and its file-output test uses explicit force for an existing destination. It does not add dedicated malformed-input or unattended-confirmation reproducers. Instruction-following is incomplete despite the improved protected grade.

## Protected checks

| Check | Without | With |
| --- | --- | --- |
| JSON stdout is one complete array with correct fields, escaping, types and input ordering. | Pass | Pass |
| Empty JSON exports produce an empty array and succeed. | Pass | Pass |
| JSON file export writes the payload without emitting data or completion chatter on stdout. | Fail | Pass |
| Explicit force replaces an existing JSON file correctly. | Fail | Pass |
| Unattended overwrite refuses promptly without prompting or modifying the file. | Fail | Pass |
| Invalid output formats and incomplete options report usage failure before modifying an output. | Pass | Pass |
| Missing or malformed sources and unwritable destinations fail with useful stderr and no stdout payload. | Fail | Fail |
| Existing table stdout and file-output contracts remain compatible. | Pass | Pass |
| Help advertises the JSON option and version remains available without reading input. | Pass | Pass |

## Remaining failure and limits

The grouped error check fails because malformed-input diagnostics omit the source path: `stockdesk: JSON parsing exception`. Subsequent output-failure assertions in that grouped test are not reached. **9/9 has not been achieved.** The instruction to research native/parser-library options is also not followed: the candidate extends manual parsing. Parser choice/research is not scored by these nine checks.

Coverage excludes configuration precedence, secrets, signals, broken pipes, real TTY decoration, interactive overwrite acceptance, and concurrent/atomic file replacement. Passing checks do not establish those properties.

The starter has 3/3 visible tests and 1/9 protected checks. A local corrected reference has 9/9; an unattended-overwrite mutant has 8/9. Those are grader proofs, not model baselines.

This is the best observed development output after repeated adaptive skill tuning on this case, compared with a retained baseline. Later wording variants did not improve the score, and the evaluated 8/9 version was restored. These observations do not demonstrate stable 8/9 performance for an unchanged skill or reliable effectiveness. The prompt, starter and grader stayed frozen; only the skill changed. No fresh baseline was run for the selected revision. Both candidates use Pi with GPT-6 Luna at low reasoning, read/bash/edit/write tools, the same approved outside-sandbox permissions, and a 15-minute per-process timeout. No qualitative judge. Single selected candidate per configuration, with no fresh held-out confirmation. Earlier development results and rejected variants remain local and ignored.

The retained baseline used the development fixture under `tests/tmp/`; the selected output used its byte-identical published copy. [Run instructions](../../../tests/cli-design/README.md). Raw outputs, grades, evaluated skill snapshots, and reviews remain under ignored `tests/tmp/cli-design-workspace/` and `tests/tmp/cli-design-development/`.

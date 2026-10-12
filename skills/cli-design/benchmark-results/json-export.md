# Inventory JSON export

| Configuration | Protected checks | Candidate time | Input + output tokens |
| --- | ---: | ---: | ---: |
| Retained no-skill baseline | 5/9 | 145.049s | 30,392 |
| With reproducer-first skill | **7/9** | 127.771s | 30,275 |

Score gain: **+22.2 percentage points** (55.6% → 77.8%). Existing table behavior is preserved. The baseline passes 5/5 visible tests; the selected with-skill candidate passes 7/7. Candidate times exclude grading; token counts exclude cached input. Protected grading takes another 17.743s baseline and 13.514s with skill.

The [task](../../../tests/cli-design/json-export/prompt.md) adds JSON output to a small Scala inventory exporter with table and optional file output. The fixture is original and focused, not a production checkout. Its README explicitly describes existing contracts, providing the same compatibility hints to both configurations.

## What improved

The skill asks the agent to write reproducers before changing production code. The selected trace shows a test edit and a red test run before the first production edit. Its JSON file-output regression checks the intended stream contract:

```scala
assertEquals(out.toString("UTF-8"), "")
assert(err.toString("UTF-8").contains("Wrote 0 items"))
```

The implementation moves the new mode's completion message to stderr while retaining the legacy table message:

```scala
if options.format == "json" then err.println(s"Wrote ${rows.size} items to $path")
else out.println(s"Wrote ${rows.size} items to $path")
```

Both new and forced-replacement JSON file checks improve. They share the same stdout concern and are not two independent demonstrations of benefit.

## Protected checks

| Check | Without | With |
| --- | --- | --- |
| JSON stdout is one complete array with correct fields, escaping, types and input ordering. | Pass | Pass |
| Empty JSON exports produce an empty array and succeed. | Pass | Pass |
| JSON file export writes the payload without emitting data or completion chatter on stdout. | Fail | Pass |
| Explicit force replaces an existing JSON file correctly. | Fail | Pass |
| Unattended overwrite refuses promptly without prompting or modifying the file. | Fail | Fail |
| Invalid output formats and incomplete options report usage failure before modifying an output. | Pass | Pass |
| Missing or malformed sources and unwritable destinations fail with useful stderr and no stdout payload. | Fail | Fail |
| Existing table stdout and file-output contracts remain compatible. | Pass | Pass |
| Help advertises the JSON option and version remains available without reading input. | Pass | Pass |

## Remaining failures and limits

The candidate still accepts piped `y` to overwrite an existing output and omits the source filename from malformed-input errors. It does not add the required reproducers for those inherited paths. The instruction to investigate native/parser-library options is also not followed: it extends manual parsing. Parser choice/research is not scored by these nine checks.

Coverage does not include configuration precedence, secrets, signals, broken pipes, real TTY decoration, interactive overwrite acceptance, or concurrent/atomic file replacement. Passing checks do not establish those properties.

The starter has 3/3 visible tests and 1/9 protected checks. A local corrected reference has 9/9; an unattended-overwrite mutant has 8/9. Those are grader proofs, not model baselines.

This is a selected development result after adaptive skill tuning on this case, compared with the retained baseline. The prompt, starter, and protected grader stayed frozen; only the skill changed. No fresh baseline was run for the final revision. Both candidates use Pi with GPT-6 Luna at low reasoning, read/bash/edit/write tools, the same approved outside-sandbox permissions, and a 15-minute per-process timeout. No qualitative judge. Single selected candidate per configuration, with no fresh held-out confirmation or reliable effectiveness claim. Earlier development evidence remains local and ignored.

The evaluated fixture was under `tests/tmp/` and was promoted after the smoke; the candidate source files and grading logic were copied unchanged. [Run instructions](../../../tests/cli-design/README.md). Raw candidate outputs, grades, skill snapshots, and review pages remain under ignored `tests/tmp/cli-design-workspace/` and `tests/tmp/cli-design-development/`.

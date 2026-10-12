# Inventory JSON export

| Configuration | Protected checks | Candidate time | Input + output tokens |
| --- | ---: | ---: | ---: |
| Retained no-skill baseline | 5/9 | 145.049s | 30,392 |
| Selected revised skill | **9/9** | 186.604s | 35,816 |

Score gain: **+44.4 percentage points** (55.6% → 100%). The baseline and selected with-skill candidate each pass 5/5 visible tests; existing table behavior is preserved. Candidate times exclude grading, which takes another 17.743s baseline and 15.863s with skill. Token counts exclude cached input.

The [task](../../../tests/cli-design/json-export/prompt.md) adds JSON output to a focused Scala inventory exporter with table and optional file output. The fixture is original, not a production checkout. Both configurations receive the same README compatibility contracts.

## What improved

The revised skill puts parser selection before implementation, requires actual integration, and asks agents to continue fixing failing regressions. The selected candidate adopts Scopt 4.2.0. The skill itself names no language or parser library.

It preserves source context for malformed input and checks interactivity before reading overwrite approval:

```scala
if Files.exists(path) && !options.force then
  if !terminal then
    err.println(s"Refusing to replace $path without an interactive terminal; use --force.")
    return 1
```

```scala
case error: Exception =>
  err.println(s"stockdesk: cannot export ${options.source}: ${error.getMessage}")
  1
```

JSON file output contains only the payload and leaves stdout empty. Table file completion remains compatible. File-output checks share one stdout concern and are not independent demonstrations of two benefits.

## Protected checks

| Check | Without | With |
| --- | --- | --- |
| Complete JSON array with correct fields, escaping, types and ordering | Pass | Pass |
| Empty JSON exports succeed with an empty array | Pass | Pass |
| JSON file export has no stdout payload or completion chatter | Fail | Pass |
| Explicit force replaces an existing JSON file | Fail | Pass |
| Unattended overwrite refuses promptly without prompting or mutation | Fail | Pass |
| Invalid formats and incomplete options fail before output mutation | Pass | Pass |
| Missing/malformed sources and unwritable destinations have useful errors | Fail | Pass |
| Existing table stdout and file-output contracts | Pass | Pass |
| Help describes JSON; version works without input | Pass | Pass |

## Development and limits

This is a selected development result after repeated adaptive wording changes, compared with the retained baseline. The earlier selected result was 8/9. Later variants scored both lower and equally; the revision producing this 9/9 output was restored. Its opening sentence was then clarified to separate pre-edit research from implementation-time integration; that final wording clarification has no new model run. **Stable 9/9 performance is not established.** Rejected variants and their results remain local under ignored `tests/tmp/`.

The candidate adds feature tests before production work and runs them red on unsupported `--format`, then green after implementation. It does not add dedicated malformed-input or unattended-overwrite reproducers, so that workflow instruction remains incompletely followed. It fetches official Scala and Scopt documentation, including the Scopt README, but the trace does not establish a substantive native-versus-library comparison or maintenance/adoption assessment. Parser research and choice are not scored by these nine behavioral checks.

The starter has 3/3 visible tests and 1/9 protected checks. A local corrected reference has 9/9; an unattended-overwrite mutant has 8/9. Those validate the grader, not model uplift.

Coverage excludes configuration precedence, secrets, signals, broken pipes, real TTY decoration, interactive overwrite acceptance, and concurrent/atomic replacement. Passing checks do not establish those properties.

Prompt, starter and grader remain frozen while skill wording changes. Both configurations use Pi with GPT-6 Luna at low reasoning, read/bash/edit/write tools, the same approved outside-sandbox permissions, and a 15-minute per-process timeout. No qualitative judge. This selected output has no fresh baseline or held-out confirmation; it does not establish reliable effectiveness.

The retained baseline uses the development fixture; the selected output uses its byte-identical published copy. [Run instructions](../../../tests/cli-design/README.md). Raw outputs, grades, snapshots and reviews remain ignored under `tests/tmp/cli-design-workspace/`, `tests/tmp/cli-design-development/`, and `tests/tmp/cli-parser-development/`.

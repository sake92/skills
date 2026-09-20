# process-hygiene demo

A behavioral test for `skills/process-hygiene`. Four fixtures with planted violations are reviewed by a small model twice: once with only a generic "review this" prompt, once with the skill loaded. The difference is what the skill buys.

## Fixtures

| Fixture | Plants |
|---|---|
| `fixtures/jvm/Main.java` | `System.getenv` in three places, password from env and logged, `PORT` parsed per request, dropped `ProcessBuilder` handle with undrained stdout, static pool never shut down, empty `InterruptedException` catch on an unowned thread, no shutdown hook |
| `fixtures/shell/dev.sh` | no strict mode, two backgrounded servers never recorded or killed, no `trap`/`wait`, token on the command line, errors to stdout |
| `fixtures/cli/tool.py` | positionals only and no `--help`, API key in argv, progress on stdout mixed with JSON, `sys.exit()` without a code, unconditional ANSI color, temp file left on Ctrl-C, no network timeout |
| `fixtures/negative/Pricing.java` | nothing; pure computation. Checks that the skill stays quiet |

`expected.md` lists the findings a skill-following reviewer must raise for each fixture.

## Run it

From a plain terminal (not inside a Claude Code session, which cannot nest `claude -p`):

```bash
demo/process-hygiene/run.sh          # uses haiku
demo/process-hygiene/run.sh sonnet   # or another model
```

Results land in `results/<fixture>.with.md` and `results/<fixture>.without.md`.

From inside a Claude Code session, dispatch one Agent per fixture and mode with model `haiku`. The with-skill prompt is: "Read `skills/process-hygiene/SKILL.md` and follow it, reading only the reference files relevant to this file type. Then review `demo/process-hygiene/fixtures/<fixture>` for production-readiness problems. List concrete findings, one per line, each with a one-sentence fix." The without-skill prompt is the same sentence minus the first one. Save each agent's answer verbatim under `results/`.

## Score it

For each fixture and mode, tick the items in `expected.md` that the review raised. Record the counts in `results/SCORE.md`. For the negative fixture, any config, lifecycle, logging, or concurrency finding is a false positive.

`results/` is committed so the last run is visible without re-running.

## Mechanical checks

`check-skill.sh` validates the skill's frontmatter, line budget, banned words, and that every reference file is mentioned and exists. Run it after any edit to the skill:

```bash
bash demo/process-hygiene/check-skill.sh
```

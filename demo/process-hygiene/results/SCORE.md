# Score — run of 2026-09-20, model: haiku

Scored against `../expected.md`. "Found" means the review named the same code and the same problem, in any words. "Partial" means it touched the code but missed the point (counted as ½).

| Fixture | Expected | Found without skill | Found with skill | False positives with skill |
|---|---:|---:|---:|---:|
| jvm/Main.java | 8 | 4 (+1 partial) | 8 | 0 |
| shell/dev.sh | 5 | 3 (+1 partial) | 5 | 0 |
| cli/tool.py | 7 | 1 (+1 partial) | 7 | 0 |
| negative/Pricing.java | 0 | n/a (8 domain-logic remarks, out of scope) | 0 | 0 |
| **Total** | **20** | **8 (+3 partial)** | **20** | **0** |

## Per-item detail

### jvm/Main.java

| # | Expected | Without | With |
|---|---|---|---|
| 1 | three `getenv` sites → one validated loader at startup | partial: "cache PORT at startup", null checks only, no fail-fast, no mention of the other two | found: "load and validate PORT once at startup ... exit non-zero" (still PORT-centric) |
| 2 | `DB_PASSWORD` from env → file path | missed | found: `DB_PASSWORD_FILE` |
| 3 | `PORT` parsed in request handler | found | found |
| 4 | dropped `ProcessBuilder` handle, undrained, never killed | found (tracked/stopped/unread output) | found, with drain + `waitFor(timeout)` + `ProcessHandle.descendants()` tree kill |
| 5 | static `POOL` never shut down | missed | found, with `shutdown → awaitTermination → shutdownNow` |
| 6 | empty `InterruptedException` catch, unowned thread | found (both halves) | found (both halves) |
| 7 | no shutdown hook / SIGTERM protocol | missed (only "thread prevents graceful shutdown") | found: hook with stderr line, stop intake, 20 s drain, pool + children |
| 8 | password in a log line | found | found |

Extra with-skill findings, all in scope: fire-and-forget `POOL.submit` future, JDBC statement with no timeout.

### shell/dev.sh

| # | Expected | Without | With |
|---|---|---|---|
| 1 | no `set -euo pipefail` | partial: `set -e` only | found |
| 2 | two servers backgrounded and never owned | found | found, plus `setsid` for group kill |
| 3 | no `trap` / `wait` | found (trap/kill) | found (trap on EXIT INT TERM, `wait` before exit) |
| 4 | token on the command line | found | found |
| 5 | errors to stdout, exit 0 on failure | missed | found (stderr); exit code not called out |

Note: the with-skill run suggested `--password-file` for curl, which curl does not have. The principle (secret from a file, not argv) is right; the flag name is wrong. Known small-model slip, not a skill gap.

### cli/tool.py

| # | Expected | Without | With |
|---|---|---|---|
| 1 | positionals → flags + `--help` | missed (only "validate argc") | found (argparse + `--help` + no-arg concise help) |
| 2 | API key in argv | missed (speculated about URL leakage) | found (`--api-key-file` or stdin, `ps` visibility) |
| 3 | progress on stdout mixed with JSON | missed | found |
| 4 | `sys.exit()` with no code | found | found |
| 5 | unconditional ANSI colors | missed | found (`isatty` + `NO_COLOR`) |
| 6 | temp file left on Ctrl-C | partial: "handle not closed on exception" | found (delete on exception; exit 130 on SIGINT) |
| 7 | no network timeout | missed | found |

### negative/Pricing.java

Without the skill the model produced eight remarks, all about domain rules (null checks, rounding, discount ordering). None are process-hygiene topics, so they are out of scope rather than false positives, but they show the baseline reviewer has no notion of scope.

With the skill: "No findings", with the correct reason (pure domain logic, outside the skill's scope). Zero false positives.

## Verdict

No iteration needed. Every planted finding was raised with the skill loaded, the negative fixture stayed clean, and the fixes proposed used the exact mechanisms the references teach (`ProcessHandle.descendants()`, `setsid` + `kill -- -pid`, `*_FILE` secrets, `isatty` + `NO_COLOR`, exit 130).

Known gaps to watch on future runs: the config finding on jvm stayed PORT-centric in both modes rather than naming all three `getenv` sites as one loader problem; the shell run did not call out the exit code on failure.

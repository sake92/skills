# Expected findings

A reviewer following `skills/process-hygiene/SKILL.md` must raise every item below for its fixture. Section numbers refer to SKILL.md. A finding counts as found if the reviewer names the same code and the same problem, in any words.

## jvm/Main.java

1. `System.getenv` called in three places (`port()`, `connect()`, `handleOrders`) instead of one validated config loader at startup. §1
2. `DB_PASSWORD` read from an environment variable; should come from a file path (`*_FILE`) or secret manager. §2
3. `PORT` parsed with `Integer.parseInt` inside a request handler, unvalidated, at request time rather than startup. §1, §3
4. `ProcessBuilder("sh","-c","tail -f ...").start()` handle dropped, stdout never drained, never killed; orphan on exit. §4
5. `POOL` is a static executor that is never shut down. §4
6. `catch (InterruptedException e) {}` is empty; the cache thread is uncancellable and unowned (`new Thread(...).start()` with no join or owner). §4
7. No shutdown hook or SIGTERM handling; server never stops intake or drains. §3
8. Password printed in a log line (`"with password " + pw`). §2, §5

Bonus (not required): logs go to stdout via `println` mixed with data; `e.printStackTrace()`; no timeouts on JDBC.

## shell/dev.sh

1. No `set -euo pipefail`. shell.md
2. Two servers started with `&` and never recorded, killed, or waited for; both orphaned when the script exits. §4
3. No `trap` on EXIT/INT/TERM and no `wait`. §4, shell.md
4. `API_TOKEN=$1`: secret taken as a command-line argument, visible in `ps`. §2
5. Error message (`api failed to start`) printed to stdout instead of stderr; script exits 0 on failure. §5

Bonus: fixed `sleep 5` instead of bounded readiness polling.

## cli/tool.py

1. Positional arguments for everything; should be flags with an argument parser, plus `--help`. cli.md
2. API key passed as an argument (`sys.argv[3]`); should be `--api-key-file` or stdin. §2
3. Progress lines printed to stdout, interleaved with the JSON result; progress belongs on stderr. §5
4. `sys.exit()` with no code on error; exits 0 after printing an error. §5
5. ANSI colors emitted unconditionally, no TTY or `NO_COLOR` check. §5
6. `KeyboardInterrupt` handler prints "bye" but leaves the `.partial` temp file behind. §3
7. Network request has no timeout. §4

## negative/Pricing.java

No process-hygiene findings expected. Pure computation, no config, IO, threads, or processes. A review that raises config, lifecycle, logging, or concurrency issues here is a false positive. Remarks about domain logic (rounding, discount stacking) are out of scope for this skill and neither count for nor against it.

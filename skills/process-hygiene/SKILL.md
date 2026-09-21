---
name: process-hygiene
description: Rules for how a process behaves at its boundary: configuration and secrets (flags vs env vars vs files, precedence, fail-fast validation), startup and graceful shutdown, signal handling, owning every thread and subprocess it spawns so nothing is orphaned, stdout/stderr and structured logging, stateless restarts, and CLI conventions. Use when writing or reviewing a main/entrypoint, config loading, anything that spawns threads or subprocesses, shutdown or cancellation code, a CLI, a Dockerfile or shell script, or scripts that launch long-running processes. Not for domain logic or module structure; use pragmatic-architecture for those and codeps for dependency analysis.
---

# Process Hygiene

## Philosophy

A process has an inside and a boundary. The inside is domain logic and belongs to other skills. This skill is about the boundary: what comes in (configuration, secrets, signals), what goes out (output, logs, exit codes), and what the process brings into the world and must take back out (threads, subprocesses). Three ideas underpin everything:

- **Crash-only.** A hard kill must always be safe. Graceful shutdown is an optimization for latency and resource release, never a correctness requirement. If the code only works when shutdown runs, the code is wrong. (Candea & Fox, "Crash-Only Software".)
- **Ownership.** Every thread, task, and subprocess has exactly one owner whose lifetime bounds it. Nothing outlives the scope that created it unless the code says so explicitly and a comment says why. (Smith, "Notes on structured concurrency, or: Go statement considered harmful".)
- **Small validated edge.** A process reads its world through one place. Configuration is loaded once, validated once, and handed to the rest of the program as a typed, known-good value. This is pragmatic-architecture's "validate at the edges" applied to the process boundary.

Everything below is meant to be *checkable*. When reviewing, run the checklist at the bottom.

For platform or language specifics, read only the relevant file in `references/`: `jvm.md` for Java/Kotlin/Scala, `shell.md` for bash scripts and container entrypoints, `cli.md` when the thing being built is a command-line tool. `config.md`, `lifecycle.md`, and `concurrency.md` expand the sections they are named after.

---

## 1. Configuration

Three kinds of setting, three homes. Precedence decides when a setting has more than one:

- **Varies per invocation** (output path, dry-run, verbosity) → a flag.
- **Varies per deployment or per user** (port, database host, log format, region) → an environment variable, and usually also a flag.
- **Stable for everyone on the project** (lint rules, feature list, defaults shared by the team) → a versioned config file.

Rules:

- **Precedence**: flags > environment variables > project config file > user config file > system config file > built-in default. Document the order where users will see it (`--help` or the README).
- **Env var names**: uppercase letters, digits, underscores; never starting with a digit; prefixed with the application name (`MYAPP_PORT`, not `PORT`); never shadowing a POSIX or widely used name (`PATH`, `HOME`, `DEBUG`, `TMPDIR`). Single-line values.
- **Honor the standard ones** when they apply: `NO_COLOR`, `FORCE_COLOR`, `DEBUG`, `EDITOR`, `PAGER`, `HTTP_PROXY`/`HTTPS_PROXY`/`NO_PROXY`, `TMPDIR`, `XDG_CONFIG_HOME`.
- **User config lives under XDG paths** (`$XDG_CONFIG_HOME/myapp/`, default `~/.config/myapp/`), not a dotfile dropped in `$HOME`.
- **`.env` files** are a local-development convenience for non-secret values. They are never the application's config format and never committed with real values.
- **Load once, validate once, fail fast.** One loader builds one typed config object at startup. Ranges, required fields, and reachable paths are checked there. A bad value exits non-zero with a message naming the setting and where it came from. No `getenv` call anywhere else in the program.
- **Log the effective configuration** at debug level at startup, one line per setting with its source, secrets redacted.

Details and worked examples: `references/config.md`.

## 2. Secrets

- **File or secret manager first.** The environment variable carries the *path* (`MYAPP_DB_PASSWORD_FILE=/run/secrets/db`), not the value. Mounted secret files rotate without a restart; environment variables cannot.
- **Why not env vars.** The environment is inherited by every child process by default, so a secret in env leaks into every subprocess the program ever spawns (§4). It is also readable through `ps eww`, `/proc/<pid>/environ`, `docker inspect`, systemd status output, and crash reports.
- **Never on the command line.** Arguments are visible to every user on the machine through `ps`. Accept `--password-file`, or read from stdin, never `--password`.
- **Never in logs, error messages, or the effective-config dump.** Redact at the config layer, so downstream code cannot leak by accident.
- **Reading a secret directly from an env var is a documented local-dev fallback**, taken only when the file-based path is absent, and never the primary route.

## 3. Lifecycle

**Startup:**

- **Fail fast.** Bad config, a missing secret file, or an unreachable required backing service: exit non-zero with a clear message before doing any work. Bounded retries measured in seconds are fine; retrying forever at startup is not. Let the supervisor restart you.
- **Print one line within 100 ms** so a human knows the process is alive, then install signal handlers before doing anything heavy.
- **Readiness is separate from liveness** under an orchestrator. Ready only once dependencies are confirmed.

**Shutdown protocol** on `SIGTERM` (and `SIGINT` from a terminal):

1. Say you are stopping: one line on stderr.
2. Stop accepting new work: close the listener, stop polling the queue, cancel the scheduler.
3. Drain in-flight work with a deadline that fits inside the platform's grace period with margin (Kubernetes default is 30 s; drain for at most 20 s).
4. Close resources in reverse order of acquisition.
5. Exit with the appropriate code.

Rules around it:

- **Cleanup has its own timeout.** It can never hang the process.
- **A second signal during cleanup** skips the remaining cleanup, prints one line, and exits.
- **`SIGTERM` and `SIGINT` are handled the same way.** `SIGKILL` cannot be caught; design so that it is always safe (crash-only).
- **Work is idempotent or transactional**, so a restart after a hard kill redoes or skips it safely. This is what makes crash-only viable.
- **In a container the application is PID 1 or runs under an init** (`tini`, `docker run --init`) that reaps zombies and forwards signals. Entrypoint shell scripts `exec` the real process as their last line.

Details, signal table, and the Kubernetes sequence: `references/lifecycle.md`.

## 4. Owning children

The structured-concurrency rule: every concurrent operation runs inside a scope; the scope does not finish until all its children have finished; errors and cancellation propagate through the scope. Anything that escapes its scope is a bug unless the code explicitly documents it as a detach.

- **No fire-and-forget.** Every thread, future, coroutine, or subprocess is joined, awaited, cancelled, or explicitly handed to a longer-lived owner. A dropped handle is a leak. `new Thread(...).start()` with no join and no owner, or a `Process` whose handle is discarded, is a finding on sight.
- **Pools are owned.** An executor or thread pool is created by the component that uses it and shut down in that component's close path (shutdown, await with deadline, force). Daemon threads only when the thread's abrupt death at exit is harmless, and then with a comment saying so.
- **Subprocesses:**
  - **Drain stdout and stderr**, by redirecting them to a file or the parent's streams, or by reading them concurrently. An undrained pipe blocks the child forever once the buffer fills.
  - **Set a timeout.** On expiry, terminate, wait briefly, then kill.
  - **Kill the whole descendant tree on parent exit**, not just the direct child. Shells, build tools, and Node processes spawn grandchildren, and those are what get orphaned.
  - **Pass an explicit environment** when the child does not need the parent's, so secrets do not travel by default (§2).
- **Cancellation is cooperative.** Check for it in loops, propagate interrupt and cancel signals, never swallow them. An empty `catch (InterruptedException e) {}` is a finding.
- **Timeouts on everything that blocks**: network calls, locks, queue takes, joins, subprocess waits. A blocking call with no timeout is a hang waiting for its moment.
- **Intentional detaching** (a daemon, a launcher, handoff to a supervisor) is allowed when it is the design. It is explicit in the code, documented, and the detached process has another owner: systemd, launchd, a job scheduler.

The argument and generic patterns: `references/concurrency.md`. Mechanics: `references/jvm.md`, `references/shell.md`.

## 5. Output and logs

- **stdout is the program's product. stderr is everything else**: progress, warnings, errors, logs. Piping the output into another program must never pick up a log line.
- **Logs are an event stream.** One event per line, structured (JSON or logfmt) when a machine will read them, human-readable when a TTY is attached. The application never manages log files, rotation, or shipping; the platform does.
- **Correlate.** Every event in a unit of work carries the same request or job identifier.
- **No secrets and no full payloads** by default.
- **Detect the TTY.** Colors, spinners, and progress bars only when stdout is a terminal, and never when `NO_COLOR` is set or `TERM=dumb`.
- **Exit codes**: 0 on success, non-zero on failure, and distinct codes for the failure modes a calling script would branch on.

## 6. Statelessness and backing services

- **No state that must survive a restart lives in process memory or on local disk.** Local disk is a cache at best. Sessions, queues, and locks live in a backing service.
- **Backing services are attached by configuration** (§1) and swappable without a code change. The database, queue, cache, object store, and mail relay are all resources with a URL, not compiled-in assumptions.
- **Dev/prod parity.** The same kind of backing service in development, tests, and production. A real database in a container over an in-memory substitute; see pragmatic-architecture §10.
- **Sticky sessions and singleton assumptions are bugs** unless the deployment is documented as single-instance.

## 7. Admin and one-off tasks

Migrations, backfills, and maintenance scripts live in the same codebase as the application, load configuration the same way (§1), log the same way (§5), and obey the same shutdown rules (§3). A copy-pasted config loader in a `scripts/` folder is two definitions of one thing, and they will drift.

## 8. Command-line interfaces

A CLI is a process whose boundary is a human at a terminal, so everything above applies: configuration precedence, secrets via file or stdin, Ctrl-C as `SIGINT`, stdout for data and stderr for messages, meaningful exit codes. What a CLI adds is a conversation: help text, flag naming, subcommands, machine-readable output modes, and a stable contract for the scripts that will call it. Read `references/cli.md` whenever the thing being built or reviewed is a command-line tool.

---

## When these rules don't apply

Throwaway spikes and one-off exploration are exempt, marked as such (a comment, a branch name). A deliberately detached daemon or launcher is exempt from §4's scope rule when documented as in that section. A very short-lived script that exits before any of this matters is exempt from the lifecycle rules, but stdout/stderr separation and exit codes still hold because they cost nothing.

---

## Review checklist

Before calling a change done, check:

- [ ] Any `getenv` / `System.getenv` / `process.env` / `os.environ` read outside the single config loader? → §1.
- [ ] Any config value used before validation, or validation that logs and continues instead of exiting non-zero? → §1, §3.
- [ ] Env var names without the app prefix, lowercase, or shadowing `PATH`/`HOME`/`DEBUG`? → §1.
- [ ] User config written to `~/.something` instead of an XDG path? → §1.
- [ ] Any secret read from an env var or a command-line flag as the primary route, instead of a file path or stdin? → §2.
- [ ] Any secret that could reach a log line, error message, or config dump? → §2.
- [ ] Does `SIGTERM` stop intake, drain with a deadline inside the grace period, close resources, and exit? Is there a timeout on the cleanup itself? → §3.
- [ ] Startup that retries a dependency indefinitely instead of failing fast? → §3.
- [ ] Container entrypoint that runs `sh -c "..."` as PID 1 without `exec` or an init? → §3, `references/shell.md`.
- [ ] Any thread, future, coroutine, or subprocess whose handle is dropped or never joined? → §4.
- [ ] Any executor or pool without a shutdown path in its owner's close, or a daemon thread without a justifying comment? → §4.
- [ ] Any subprocess without drained stdout/stderr, a timeout, and tree-kill on parent exit? → §4.
- [ ] Any `InterruptedException` or cancellation swallowed or turned into a bare log line? → §4.
- [ ] Any blocking call (network, lock, queue, join, wait) without a timeout? → §4.
- [ ] Subprocess inheriting the parent's full environment when it does not need it? → §2, §4.
- [ ] Any program output on stderr, or diagnostics and progress on stdout? → §5.
- [ ] Colors or spinners emitted when stdout is not a TTY or `NO_COLOR` is set? → §5.
- [ ] Log files, rotation, or shipping handled inside the app instead of by the platform? → §5.
- [ ] Any state in memory or on local disk that a restart would lose and the program cannot rebuild? → §6.
- [ ] Backing service hostnames or credentials compiled in rather than configured? → §6.
- [ ] Any admin or migration script with its own config or logging code? → §7.
- [ ] (CLI) `--help`, `--version`, `--json` or `--plain`, `--no-color`, `--dry-run` where they make sense? Zero-argument run prints concise help? Positionals where flags belong? → `references/cli.md`.
- [ ] (Shell) `set -euo pipefail`, a `trap` on `EXIT INT TERM` that kills recorded children, `wait` before exit? → `references/shell.md`.

## Reference files

- `references/config.md` — the three kinds of setting, precedence walk-through, env var naming, XDG paths, `.env` handling, load-once pattern, effective-config dump (§1, §2).
- `references/lifecycle.md` — signal table, the shutdown protocol per workload type, Kubernetes termination sequence, PID 1 and zombie reaping, readiness vs liveness, crash-only summary (§3).
- `references/concurrency.md` — the structured-concurrency argument, scope patterns, cooperative cancellation, language primitives (§4).
- `references/cli.md` — help text, flags, subcommands, output modes, errors, future-proofing, distribution; the CLI checklist (§8).
- `references/jvm.md` — `ProcessBuilder`, `ProcessHandle` tree kill, executor shutdown, `StructuredTaskScope`, shutdown hooks, `InterruptedException`, timeouts (§3, §4).
- `references/shell.md` — strict mode, owning background jobs with `trap` and process groups, `timeout`, intentional detaching, `exec` in entrypoints (§3, §4).

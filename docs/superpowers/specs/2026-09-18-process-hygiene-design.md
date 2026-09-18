# process-hygiene skill — design

Date: 2026-09-18
Status: draft for review
Resolves: sake92/skills#5 ("Encode 12 factor apps principles")

## Goal

Add a third skill to this repository that tells an agent how a process should
behave at its boundary: how it is configured, how it starts and stops, how it
owns the threads and subprocesses it spawns, how it emits output and logs, and
how a CLI in particular should present itself. The skill is checkable, like
pragmatic-architecture: every section ends in something a reviewer can tick.

Also make two small edits so Codeps is used as a verification gate for new
code, not only for refactors, and so its configuration groups packages by
feature when the codebase is organized that way.

## Non-goals

- Not a restatement of 12-factor. Roughly half the factors are solved by
  tooling (codebase, dependencies, build/release/run) or are platform
  opinions (port binding, concurrency model). Those get at most a sentence.
- Not domain logic, data modeling, or module structure. pragmatic-architecture
  owns those. Not dependency analysis. codeps owns that.
- Not a deployment or Kubernetes guide. Platform facts appear only where they
  set a budget or constraint the code must respect (e.g. the termination grace
  period).
- Not a separate CLI-design skill, yet. CLI-specific material lives in one
  reference file so it can be split out later if it grows or needs standalone
  distribution.

## Scope decision

Broad: any process an agent writes or runs. Services, CLIs, batch jobs,
shell scripts, Dockerfile entrypoints, and scripts the agent launches for
itself during a task (dev servers, watchers). The last case is where orphaned
processes actually occur in practice, so it stays in scope even though it
makes the skill load more often than the other two.

## Sources

Primary:

- The Twelve-Factor App (12factor.net): factors III, IV, VI, IX, X, XI, XII.
- Command Line Interface Guidelines (clig.dev): configuration, environment
  variables, output, errors, signals, robustness, future-proofing, plus the
  CLI-only sections for `references/cli.md`.
- Nathaniel J. Smith, "Notes on structured concurrency, or: Go statement
  considered harmful" (vorpus.org). Ownership and scope model for §5.
- Candea and Fox, "Crash-Only Software" (HotOS 2003). Philosophical anchor
  for §1 and §4.

Secondary:

- Kevin Hoffman, "Beyond the Twelve-Factor App" (telemetry, API-first).
- XDG Base Directory Specification.
- Kubernetes pod termination lifecycle (SIGTERM, preStop,
  terminationGracePeriodSeconds).
- Java `ProcessHandle`, `StructuredTaskScope`, `ExecutorService` Javadoc.
- tini / `docker run --init` documentation for PID 1 reaping.

Where 12-factor and clig.dev disagree (secrets in env vars), the skill
follows clig.dev. See §3.

## Skill layout

```
skills/process-hygiene/
  SKILL.md
  references/
    config.md
    lifecycle.md
    concurrency.md
    cli.md
    jvm.md
    shell.md
```

Python and Node reference files are deferred. The generic rules in
`concurrency.md` cover them; a per-language file is added when a real task
needs one.

SKILL.md targets roughly 150–200 lines, same as pragmatic-architecture.
References are read on demand and named from the section they support.

## SKILL.md sections

### Frontmatter

```yaml
name: process-hygiene
description: Rules for how a process behaves at its boundary: configuration and secrets (flags vs env vars vs files, precedence, fail-fast validation), startup and graceful shutdown, signal handling, owning every thread and subprocess it spawns so nothing is orphaned, stdout/stderr and structured logging, stateless restarts, and CLI conventions. Use when writing or reviewing a main/entrypoint, config loading, anything that spawns threads or subprocesses, shutdown or cancellation code, a CLI, a Dockerfile or shell script, or scripts that launch long-running processes. Not for domain logic or module structure; use pragmatic-architecture for those and codeps for dependency analysis.
```

### §1 Philosophy

Three ideas, each one paragraph:

- **Crash-only.** A hard kill must always be safe. Graceful shutdown is an
  optimization for latency and resource release, never a correctness
  requirement. If the code only works when shutdown runs, the code is wrong.
- **Ownership.** Every thread, task, and subprocess has exactly one owner
  whose lifetime bounds it. Nothing outlives the scope that created it unless
  the code says so explicitly and a comment says why.
- **Small validated edge.** A process reads its world through one place:
  config is loaded once, validated once, and handed to the rest of the program
  as a typed, known-good value. This is pragmatic-architecture §7 applied to
  the process boundary.

### §2 Configuration

Rules:

- Three kinds of setting, three homes. Varies per invocation → flag. Varies
  per deployment or per user → env var (and flag). Stable for everyone on the
  project → versioned config file. A setting can have more than one home; the
  precedence order decides.
- Precedence: flags > env vars > project config file > user config file >
  system config file > built-in default. Document it in `--help` or the
  README.
- Env var names: uppercase, digits, underscores, never starting with a digit,
  prefixed with the app name (`MYAPP_PORT`), never shadowing a POSIX or
  widely-used name. Single-line values.
- Honor the standard ones when relevant: `NO_COLOR`, `FORCE_COLOR`, `DEBUG`,
  `EDITOR`, `PAGER`, `HTTP_PROXY`/`HTTPS_PROXY`/`NO_PROXY`, `TMPDIR`, `HOME`,
  `XDG_CONFIG_HOME`.
- User config lives under XDG paths (`$XDG_CONFIG_HOME/myapp/`, default
  `~/.config/myapp/`), not a dotfile in `$HOME`.
- `.env` files are a local-dev convenience for non-secret values only. Never
  the app's config format, never committed with real values.
- Load everything at startup into one typed config object. Validate it there
  (ranges, required fields, reachable paths). Fail fast with a message that
  names the setting and where it came from. No lazy `getenv` deep in business
  code.
- At debug level, log the effective configuration with each value's source
  and with secrets redacted.

Reference: `config.md` for the precedence walk-through, XDG details, examples
of each kind, and how to expose config source in error messages.

### §3 Secrets

Rules:

- Prefer a file or a secret manager. The env var carries the path
  (`MYAPP_DB_PASSWORD_FILE`), not the value.
- Why: environment is inherited by every child process (this is the §5
  connection), and is readable via `ps eww`, `/proc/<pid>/environ`,
  `docker inspect`, systemd status, and crash reports. Files can be rotated
  without a restart; env vars cannot.
- Never on the command line. Arguments are world-readable in `ps`.
- Never logged, never in the effective-config dump, never in an error
  message. Redact at the config layer so downstream code cannot leak by
  accident.
- Reading a secret directly from an env var is acceptable as a documented
  local-dev fallback, gated behind the file-based path being absent.

### §4 Lifecycle

Startup:

- Fail fast. Bad config, missing secret file, unreachable required backing
  service: exit non-zero with a clear message before doing any work. Don't
  retry forever at startup; let the supervisor restart you.
- Print something within 100 ms so a human knows the process is alive.
- Expose readiness separately from liveness when running under an
  orchestrator: ready only when dependencies are confirmed.

Shutdown protocol on SIGTERM (or SIGINT from a terminal):

1. Say you are stopping (stderr, one line).
2. Stop accepting new work: close the listener, stop polling the queue,
   cancel the scheduler.
3. Drain in-flight work with a deadline that fits inside the platform grace
   period (Kubernetes default 30 s; leave margin).
4. Close resources in reverse order of acquisition.
5. Exit with the appropriate code.

Rules around it:

- Cleanup has its own timeout. It can never hang the process.
- A second signal during cleanup skips remaining cleanup, warns, and exits.
- Handle SIGTERM and SIGINT the same way. Don't catch SIGKILL; you can't.
- Work must be idempotent or transactional so that a restart after hard kill
  redoes or skips it safely. This is what makes crash-only viable.
- In a container the app must be PID 1 or run under an init that reaps
  zombies and forwards signals. Entrypoint shell scripts `exec` the real
  process.

Reference: `lifecycle.md` for signal semantics per platform, the Kubernetes
termination sequence, PID 1 and zombie reaping, shell `trap`, and a
one-paragraph summary of the crash-only paper.

### §5 Owning children

The structured-concurrency rule: a concurrent operation is bounded by a
scope, the scope waits for everything it started, and errors and
cancellation propagate through the scope. Anything that escapes the scope is
a bug unless explicitly documented as a detach.

Rules:

- **No fire-and-forget.** Every thread, future, coroutine, or subprocess is
  joined, awaited, cancelled, or explicitly handed to a longer-lived owner.
  A dropped handle is a leak.
- **Pools are owned.** An executor or thread pool is created by the component
  that uses it and shut down in that component's close path. Daemon threads
  only when the thread's death at exit is harmless, with a comment saying
  so.
- **Subprocesses:**
  - Drain stdout and stderr, either by redirecting to a file or the parent's
    streams, or by reading them concurrently. An undrained pipe blocks the
    child forever when the buffer fills.
  - Set a timeout. On expiry, terminate, wait briefly, then kill.
  - On parent exit, kill the whole descendant tree, not just the direct
    child. Children that spawn children (shells, build tools, node) leave
    grandchildren otherwise.
  - Pass an explicit environment when the child does not need the parent's
    (see §3).
- **Cancellation is cooperative.** Check for it in loops, propagate
  interrupt/cancel signals, never swallow them. On the JVM, never catch
  `InterruptedException` without either rethrowing or re-setting the flag.
- **Timeouts on everything blocking:** network calls, locks, queue takes,
  joins. A blocking call with no timeout is a hang waiting to happen.
- **Intentional detaching** (daemons, launchers, `nohup`-style handoff to a
  supervisor) is allowed when it is the design. It must be explicit in the
  code, documented, and the detached process must have another owner
  (systemd, a job scheduler, a supervisor).

References: `concurrency.md` for the structured-concurrency argument and
generic patterns; `jvm.md` for `ProcessHandle.descendants()`, `destroy` then
`destroyForcibly`, `ExecutorService` shutdown sequence, `StructuredTaskScope`,
shutdown hooks, virtual threads; `shell.md` for process groups, `trap`,
`wait`, `timeout`, `setsid`, `set -euo pipefail`.

### §6 Output and logs

- stdout is the program's product. stderr is everything else: progress,
  warnings, errors, logs. Piping must never mix them.
- Logs are an event stream. Write to stderr (or stdout when the platform
  says so), one event per line, structured (JSON or logfmt) when a machine
  will read them, human-readable when a TTY is attached. Never manage log
  files, rotation, or shipping inside the app.
- Include correlation identifiers (request id, job id) on every event in a
  unit of work.
- No secrets, no full payloads by default.
- Detect TTY: colors and progress indicators only when stdout is a
  terminal, and never when `NO_COLOR` is set or `TERM=dumb`.
- Exit codes: 0 success, non-zero failure, distinct codes for the failure
  modes a script would branch on.

### §7 Statelessness and backing services

- No state that must survive a restart lives in process memory or on the
  local disk. Local disk is a cache at best.
- Backing services (database, queue, cache, object store, SMTP) are attached
  via config (§2) and swappable without a code change.
- Dev/prod parity: the same backing service kind in development, test, and
  production. Testcontainers over in-memory substitutes, which links to
  pragmatic-architecture §10.
- Sticky sessions and singleton assumptions are bugs unless the deployment
  is documented as single-instance.

### §8 Admin and one-off tasks

Migrations, backfills, and maintenance scripts live in the same codebase,
load config the same way (§2), log the same way (§6), and obey the same
shutdown rules (§4). No separate copy-pasted config loader in a `scripts/`
folder.

### §9 When these rules don't apply

Throwaway spikes and one-off exploration, marked as such. A deliberately
detached daemon or launcher, documented as in §5. Very short-lived scripts
where the process exits before any of this matters, though the stdout/stderr
and exit-code rules still hold because they cost nothing.

### §10 Review checklist

One line per rule above, phrased as a question with a section pointer.
Roughly 20–25 items. Examples:

- [ ] Any `getenv`/`System.getenv`/`process.env` outside the config loader?
      → §2.
- [ ] Any secret read from an env var or passed on the command line without
      a file-based path being the primary route? → §3.
- [ ] Any config value used before validation, or validation that logs and
      continues instead of exiting? → §2, §4.
- [ ] Does SIGTERM stop intake, drain with a deadline, and exit within the
      grace period? Is there a timeout on the cleanup itself? → §4.
- [ ] Any thread, future, or subprocess whose handle is dropped? → §5.
- [ ] Any executor without a shutdown path, or a daemon thread without a
      comment? → §5.
- [ ] Any subprocess without drained pipes, a timeout, or tree-kill on
      parent exit? → §5.
- [ ] Any `InterruptedException` or cancellation swallowed? → §5.
- [ ] Any blocking call without a timeout? → §5.
- [ ] Any program output on stderr or diagnostics on stdout? → §6.
- [ ] Colors or spinners when stdout is not a TTY? → §6.
- [ ] Any state in memory or on local disk that a restart would lose and
      the program cannot rebuild? → §7.
- [ ] Any admin script with its own config or logging code? → §8.
- [ ] (CLI) `--help`, `--version`, `--json`/`--plain`, `--no-color`,
      `--dry-run` where they make sense? Zero-arg run prints concise help?
      → `cli.md`.
- [ ] (Container) Entrypoint `exec`s the process, or an init reaps zombies
      and forwards signals? → `lifecycle.md`.
- [ ] (Shell) `set -euo pipefail`, `trap` on EXIT/INT/TERM that kills
      children, `wait` before exit? → `shell.md`.

## Reference file contents

### `config.md`

Precedence walk-through with an example showing a setting arriving from
three sources. XDG paths on Linux/macOS/Windows. Env var naming with examples
of good and bad names. `.env` handling. How to render "setting X from source
Y is invalid" errors. Effective-config dump format with redaction.

### `lifecycle.md`

Signal table (SIGINT, SIGTERM, SIGHUP, SIGKILL; what each means, what to do).
Kubernetes termination sequence step by step with the grace period budget.
PID 1 responsibilities, why bare `sh -c` entrypoints break signal delivery,
`exec`, `tini`, `--init`. Readiness vs liveness. Crash-only summary and the
idempotency it implies.

### `concurrency.md`

The structured-concurrency argument in one page: why `go`/`new Thread` is
`goto` for control flow, what a nursery/scope gives you (bounded lifetime,
error propagation, cancellation). Generic patterns: scope-per-request,
scope-per-component, shutdown as scope cancellation. Pointers to the essay
and to the language file.

### `cli.md`

clig.dev distilled, CLI-only parts: help text (examples first, concise help
on zero args), flag conventions and the standard names list, subcommand
naming, machine-readable output flags, error message shape, suggesting
corrections, future-proofing (additive changes, no catch-all default
subcommand, no abbreviation matching), distribution as a single binary,
analytics opt-in only. Shared rules (config, env, signals, stdout/stderr,
exit codes) are pointed to by SKILL.md section number, not repeated. Written
self-contained so it can become its own skill later.

### `jvm.md`

- `ProcessBuilder`: `redirectErrorStream`, `inheritIO` vs draining, passing
  an explicit environment.
- Timeouts: `waitFor(timeout, unit)`, then `destroy()`, brief wait, then
  `destroyForcibly()`.
- Tree kill: `process.toHandle().descendants()` then destroy each, children
  first, in a shutdown hook.
- `ExecutorService`: `shutdown()`, `awaitTermination(deadline)`,
  `shutdownNow()`, and `close()` on JDK 19+. Executor owned by the component,
  closed in its close path.
- `StructuredTaskScope` (JDK 21+ preview, finalizing) as the scope primitive;
  virtual threads as the default for blocking work.
- `Runtime.addShutdownHook` rules: fast, bounded, no logging frameworks that
  may already be shut down, no dependence on other hooks' order.
- `InterruptedException`: rethrow or `Thread.currentThread().interrupt()`.
- Daemon threads: when acceptable, and the comment required.

### `shell.md`

`set -euo pipefail`. `trap cleanup EXIT INT TERM` that kills the process
group or recorded PIDs. `wait` before exit. `timeout` command. Process
groups and `setsid` for intentional detaching. Background jobs with `&`
must be recorded and killed in the trap. `exec` for the final command in
entrypoints. Why `sh -c "a && b"` as PID 1 eats signals.

## Edits to existing skills

### pragmatic-architecture/SKILL.md

- Intro paragraph (line 21): Codeps is for "dependency, extraction, or
  compilation analysis, and for verifying that a change which adds a
  package, module, or cross-package dependency kept the graph acyclic and
  one-way."
- §2: add a paragraph. When a change introduces a new package, a new build
  module, or a new import across packages, do not verify acyclicity by
  reading imports. Build, regenerate the Codeps report, and compare against
  the baseline snapshot.
- §3: add a sentence. In a package-by-feature layout the Codeps config
  should collapse each feature's sub-packages into one node so the report
  measures the feature graph; see the codeps skill for the setting.
- Description frontmatter: append "or verify that new structure introduced
  no cycle" to the Codeps clause.
- Checklist: add after the cycle item: "New package, module, or
  cross-package edge introduced? → regenerate the Codeps report and compare
  with the pre-change snapshot before calling it acyclic."

### codeps/SKILL.md and references/codeps.md

- Description: "before refactoring or after adding new structure".
- First line of SKILL.md: "Measure the dependency graph before cutting and
  after adding." with one sentence that the baseline/compare loop applies to
  a feature branch as much as to a refactor.
- `references/codeps.md` "Configure once": add a configuration rule block.
  Set `include` to the project's root package. Set `skip-tests: true` unless
  test structure is the question. When packages are organized by feature,
  add `collapse` for the feature root so each feature is one unit, with a
  generic example:

  ```yaml
  include:
    - com.example.app
  collapse:
    - com.example.app.features.*
  ```

  Optionally a second project entry without `collapse` when layer-level
  coupling inside a feature is under investigation.

### README.md

Third bullet for `process-hygiene/` in the same style as the other two.

## Testing the skill

Skills are prose; the test is behavioral. Before merging, run the skill
against three fixtures in a scratch directory and check that the agent's
output matches the checklist:

1. A JVM service `main` that reads `System.getenv` in three places, starts a
   `ProcessBuilder` without draining, and has no shutdown hook. Expect:
   config loader extraction, drained pipes, tree kill in hook, SIGTERM
   protocol.
2. A shell script that backgrounds a dev server with `&` and exits. Expect:
   `trap` that kills the child, `wait`, `set -euo pipefail`.
3. A small CLI with positional args only, no `--help`, secret as a flag.
   Expect: `cli.md` loaded, flags added, secret moved to file/stdin.

Also check that the skill does not load for a pure domain-logic change.

## Open questions

None blocking. Python and Node reference files are deferred until needed.

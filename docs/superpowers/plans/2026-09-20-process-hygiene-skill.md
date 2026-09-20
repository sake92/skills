# process-hygiene Skill Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the `process-hygiene` skill (SKILL.md + six references), tighten Codeps usage in the two existing skills, ship a reproducible demo that tests the skill with a small model, and open a draft PR.

**Architecture:** Skills are prose read by an agent; the "code" is Markdown with YAML frontmatter under `skills/<name>/`. Each task writes one or two files whose outline is fixed by the spec, then verifies with mechanical checks (frontmatter shape, line budget, banned words, cross-reference targets exist). The demo under `demo/process-hygiene/` holds fixtures with planted violations, an expected-findings checklist, and a runner that asks a small model to review each fixture with and without the skill.

**Tech Stack:** Markdown, YAML frontmatter, bash, `claude -p` CLI for the runner, the Agent tool (model `haiku`) for in-session runs.

**Spec:** `docs/superpowers/specs/2026-09-18-process-hygiene-design.md`

## Global Constraints

- Never mention any company, employer, or private repository name anywhere in the repo. Examples use `com.example.app`, `myapp`, `MYAPP_*`.
- SKILL.md for process-hygiene stays between 150 and 220 lines.
- Every reference file is self-contained: it can be read without the others, and points back to SKILL.md by section number (`SKILL.md §4`) for shared rules instead of repeating them.
- Frontmatter has exactly `name` (equal to the directory name) and `description`. Description is a single line.
- Secrets rule is file-first; env var for a secret is a documented local-dev fallback only.
- Sources cited by name only, no long quotes: 12factor.net, clig.dev, Smith's "Notes on structured concurrency, or: Go statement considered harmful", Candea & Fox "Crash-Only Software", XDG Base Directory Specification.
- Commit after every task with the attribution trailer `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- Work on branch `process-hygiene-skill`.

## Shared verification script

Used by Tasks 1–4. Created in Task 1.

`demo/process-hygiene/check-skill.sh`:

```bash
#!/usr/bin/env bash
# Mechanical checks for skills/process-hygiene. Exit non-zero on any failure.
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
skill="$root/skills/process-hygiene"
fail=0
say() { printf '%s\n' "$*" >&2; }

# frontmatter: name matches dir, description present and single-line
name=$(sed -n '2p' "$skill/SKILL.md")
[[ "$name" == "name: process-hygiene" ]] || { say "bad name line: $name"; fail=1; }
sed -n '3p' "$skill/SKILL.md" | grep -q '^description: .\{200,\}' || { say "description missing or too short"; fail=1; }
[[ "$(sed -n '4p' "$skill/SKILL.md")" == "---" ]] || { say "frontmatter must close on line 4 (single-line description)"; fail=1; }

# line budget
lines=$(wc -l < "$skill/SKILL.md")
(( lines >= 150 && lines <= 220 )) || { say "SKILL.md has $lines lines, want 150-220"; fail=1; }

# banned words anywhere in the skill and demo
if grep -rniE 'snowplow|TBD|TODO' "$skill" "$root/demo/process-hygiene" --include='*.md' --include='*.sh' --include='*.java' --include='*.py' | grep -v check-skill.sh; then
  say "banned word found"; fail=1
fi

# every references/*.md mentioned in SKILL.md exists, and every file in references/ is mentioned
for f in $(grep -o 'references/[a-z-]*\.md' "$skill/SKILL.md" | sort -u); do
  [[ -f "$skill/$f" ]] || { say "SKILL.md references missing file $f"; fail=1; }
done
for f in "$skill"/references/*.md; do
  b="references/$(basename "$f")"
  grep -q "$b" "$skill/SKILL.md" || { say "$b not mentioned in SKILL.md"; fail=1; }
done

(( fail == 0 )) && say "check-skill: OK"
exit $fail
```

Run: `bash demo/process-hygiene/check-skill.sh`

---

### Task 1: SKILL.md

**Files:**
- Create: `skills/process-hygiene/SKILL.md`
- Create: `demo/process-hygiene/check-skill.sh` (content above)

**Interfaces:**
- Produces: section numbers §1–§8 that every reference file and the checklist point to. Reference file names: `references/config.md`, `references/lifecycle.md`, `references/concurrency.md`, `references/cli.md`, `references/jvm.md`, `references/shell.md`. Tasks 2–4 must use exactly these names.

- [ ] **Step 1: Create the check script** with the content from "Shared verification script" and `chmod +x` it. Run it; expected: fails with "bad name line" because SKILL.md does not exist yet.

- [ ] **Step 2: Write `skills/process-hygiene/SKILL.md`** following the spec's "SKILL.md sections". Numbering in the file: Philosophy is unnumbered (matching pragmatic-architecture), then §1 Configuration, §2 Secrets, §3 Lifecycle, §4 Owning children, §5 Output and logs, §6 Statelessness and backing services, §7 Admin and one-off tasks, §8 Command-line interfaces. Structure:

```markdown
---
name: process-hygiene
description: <the description from the spec, one line>
---

# Process Hygiene

## Philosophy
<three paragraphs: crash-only, ownership, small validated edge. Name the two papers.>
<"For language or platform specifics, read only the relevant file in references/ ...">

---

## 1. Configuration
<bulleted rules from spec; end with "Details: references/config.md">

## 2. Secrets
<rules from spec including the inheritance argument>

## 3. Lifecycle
<Startup bullets; numbered SIGTERM protocol 1-5; rules bullets; "Details: references/lifecycle.md">

## 4. Owning children
<structured-concurrency rule paragraph; bullets: no fire-and-forget, pools are owned, subprocesses (4 sub-bullets), cancellation cooperative, timeouts on everything, intentional detaching; "Details: references/concurrency.md, references/jvm.md, references/shell.md">

## 5. Output and logs
<bullets from spec>

## 6. Statelessness and backing services
<bullets from spec; link to pragmatic-architecture §10 by name>

## 7. Admin and one-off tasks
<one paragraph from spec>

## 8. Command-line interfaces
<three sentences: a CLI is a process whose boundary is a human at a terminal; the rules above apply; for help text, flags, subcommands, machine-readable output and future-proofing read references/cli.md>

---

## When these rules don't apply
<spec §9>

---

## Review checklist
<all items from spec §10, plus one item per rule not already covered, ~22 items, each ending in a § pointer or reference file name>

## Reference files
<one line per reference file, same style as pragmatic-architecture's list>
```

- [ ] **Step 3: Run the check script.** Expected: only the "references missing file" lines fail (six of them). Fix any other failure.

- [ ] **Step 4: Commit**

```bash
git add skills/process-hygiene/SKILL.md demo/process-hygiene/check-skill.sh
git commit -m "Add process-hygiene SKILL.md and mechanical check script"
```

---

### Task 2: config.md and lifecycle.md

**Files:**
- Create: `skills/process-hygiene/references/config.md`
- Create: `skills/process-hygiene/references/lifecycle.md`

**Interfaces:**
- Consumes: SKILL.md §1 (Configuration), §2 (Secrets), §3 (Lifecycle) numbering.

- [ ] **Step 1: Write `config.md`** (target 80–120 lines) with these headings and content:

```markdown
# Configuration reference (SKILL.md §1, §2)

## Three kinds of setting
<table: kind | example | home(s)>
  per-invocation | output path, dry-run, verbosity | flag
  per-deployment/user | listen port, DB host, log format, region | env var, and flag
  per-project stable | lint rules, default region for everyone, feature list | versioned config file

## Precedence walk-through
<worked example: MYAPP_PORT. Default 8080; project file says 9000; env says 9100; --port 9200. Show the resolution table and the message an error should print: "port: 9200 (from flag --port) is out of range 1024-65535">

## Environment variable naming
<rules; good: MYAPP_DB_HOST, MYAPP_LOG_FORMAT; bad: DBHOST (no prefix), myapp_port (lowercase), 1MYAPP_X (digit), PATH/HOME/TMPDIR/DEBUG shadowing. Standard ones to honor: NO_COLOR, FORCE_COLOR, DEBUG, EDITOR, PAGER, HTTP_PROXY/HTTPS_PROXY/NO_PROXY, TMPDIR, HOME, XDG_CONFIG_HOME>

## Where files live
<XDG: $XDG_CONFIG_HOME/myapp/config.yaml default ~/.config/myapp/; $XDG_STATE_HOME for state; $XDG_CACHE_HOME for cache. macOS: XDG paths are acceptable for CLIs; ~/Library/Application Support for GUI apps. Windows: %APPDATA%\myapp. Project-level: ./myapp.yaml or ./.myapp/config.yaml at repo root. Never ~/.myapprc>

## .env files
<local-dev convenience; non-secret; gitignored; loaded only when present and only in dev; not the config format>

## Load once, validate once
<pattern: one Config type built at startup by one loader; fields typed (port: Int, url: URI, path: Path); validation runs there; loader records source per value; rest of program receives Config, never calls getenv. Short pseudocode in a neutral style>

## Effective-config dump
<at debug level, one line per setting: name, value, source; secrets show as ***; example block>
```

- [ ] **Step 2: Write `lifecycle.md`** (target 90–130 lines):

```markdown
# Lifecycle reference (SKILL.md §3)

## Signals
<table: signal | meaning | what to do>
  SIGINT  | Ctrl-C from a terminal | same as SIGTERM
  SIGTERM | supervisor asks you to stop | run the shutdown protocol
  SIGHUP  | terminal closed / historically "reload config" | treat as SIGTERM unless reload is a documented feature
  SIGKILL | cannot be caught | design so this is always safe (crash-only)
  SIGPIPE | reader closed the pipe | exit quietly; never spew errors when `| head` closes stdout

## The shutdown protocol
<the 5 steps from SKILL.md §3 expanded with what "drain" means for an HTTP server, a queue consumer, a scheduler, a batch job; the deadline arithmetic: budget = grace period - margin; default Kubernetes grace 30 s → drain deadline 20 s, cleanup deadline 5 s>

## Second signal
<skip remaining cleanup, print one line, exit with 130 (SIGINT) or 143 (SIGTERM)>

## Kubernetes termination sequence
<numbered: pod marked Terminating and removed from Service endpoints (not instantaneous), preStop hook runs, SIGTERM sent, grace period counts from SIGTERM, SIGKILL. Implication: keep serving for a second or two after SIGTERM because traffic may still arrive; readiness probe should fail immediately>

## PID 1 in containers
<PID 1 duties: reap zombies, forward signals. `CMD sh -c "java -jar app.jar"` makes sh PID 1 and sh does not forward SIGTERM → 10 s hang then SIGKILL. Fixes: exec form `CMD ["java","-jar","app.jar"]`, or `exec java ...` as last line of entrypoint script, or `tini`/`docker run --init`. Also: if your app spawns children it must reap them or run under an init>

## Readiness vs liveness
<liveness: process is alive and not deadlocked; readiness: dependencies confirmed, safe to receive traffic. Fail readiness first on shutdown>

## Crash-only, in one paragraph
<Candea & Fox: the only way to stop is to crash, the only way to start is to recover. Consequences: state lives in a store with recovery semantics, work is idempotent or transactional, startup is the recovery path, graceful shutdown is an optimization. If restart after kill corrupts anything, that is the bug to fix first>

## Startup
<fail fast list: bad config, missing secret file, required backing service unreachable (bounded retries, seconds not minutes), migrations pending when the app expects them applied. Print a one-line "starting" within 100 ms. Don't do heavy work before signal handlers are installed>
```

- [ ] **Step 3: Run `bash demo/process-hygiene/check-skill.sh`.** Expected: four "references missing file" failures remain (concurrency, cli, jvm, shell).

- [ ] **Step 4: Commit**

```bash
git add skills/process-hygiene/references/config.md skills/process-hygiene/references/lifecycle.md
git commit -m "Add process-hygiene config and lifecycle references"
```

---

### Task 3: concurrency.md and cli.md

**Files:**
- Create: `skills/process-hygiene/references/concurrency.md`
- Create: `skills/process-hygiene/references/cli.md`

**Interfaces:**
- Consumes: SKILL.md §4 (Owning children), §8 (CLIs), §1, §3, §5 numbering.

- [ ] **Step 1: Write `concurrency.md`** (target 70–100 lines):

```markdown
# Structured concurrency reference (SKILL.md §4)

## The argument
<Smith's essay in one page: `go`/`new Thread`/`spawn` is the `goto` of control flow because the caller's function returns while the spawned work continues, so nothing in the code's shape tells you when it ends, who handles its errors, or how to cancel it. Structured concurrency puts every concurrent operation inside a scope (nursery, task group, scoped thread) that (a) does not exit until every child has finished, (b) propagates a child's error to the scope and cancels siblings, (c) propagates cancellation from the scope into every child. Concurrency then has a shape you can read.>

## Consequences
<- a handle you don't await or join is a bug
 - "background task" means "task owned by a longer-lived scope", never "task nobody owns"
 - shutdown is cancelling the root scope
 - timeouts are cancellation with a clock
 - errors never disappear into a thread's uncaught-exception handler>

## Patterns
<- scope per request: work fanned out for one request is joined before the response is sent
 - scope per component: a component owns its pool/scope and closes it in its close path
 - root scope in main: everything hangs off it; SIGTERM cancels it
 - supervisor pattern for genuinely long-lived workers: a named owner restarts them, records failures, and stops them on shutdown>

## Cancellation is cooperative
<check in loops; blocking calls need timeouts or interruptible variants; never swallow interrupt/cancel; propagate>

## Subprocesses are children too
<same rules; plus pipe draining, timeouts, tree kill on exit, explicit environment. Point to jvm.md and shell.md for mechanics>

## Language primitives (one line each, then read the language file)
<JVM: StructuredTaskScope, ExecutorService as scope, virtual threads. Kotlin: coroutineScope / SupervisorJob. Python: asyncio.TaskGroup, trio nurseries. Rust: std::thread::scope, tokio JoinSet. Go: errgroup with context. Node: AbortController + Promise.all; no scope primitive, discipline required. Shell: process groups and trap.>
```

- [ ] **Step 2: Write `cli.md`** (target 100–140 lines), clig.dev distilled, CLI-only content, self-contained:

```markdown
# CLI design reference (SKILL.md §8)

Shared rules live in SKILL.md and are not repeated here: configuration and precedence §1, secrets §2, signals and Ctrl-C §3, stdout/stderr and exit codes §5.

## Basics
<use an argument-parsing library; exit 0/non-zero; stdout data, stderr messages; print something within 100 ms>

## Help
<-h/--help full help; zero args → concise help (one-line description, one or two examples, most-used flags, "run --help for more"); `myapp help sub` also works; lead with examples; most common flags first; link to docs; support path>

## Flags and arguments
<prefer flags to positionals; every flag has a long form; single letters only for the common few; standard names table: -a/--all, -d/--debug, -f/--force, --json, -h/--help, -n/--dry-run, --no-input, -o/--output, -p/--port, -q/--quiet, -v/--verbose, --version, --no-color, --plain; `-` for stdin/stdout; never secrets in flags → --password-file or stdin; order-independent where possible; suggest corrections for typos, never auto-apply them>

## Subcommands
<consistent flag names and output across subcommands; noun verb order; avoid update vs upgrade ambiguity; no catch-all default subcommand; no abbreviation matching>

## Output
<human-first when TTY; --json for machines; --plain for grep/awk; describe state changes briefly; no debug output by default; pager only when interactive (less -FIRX); color rules: off when not TTY, NO_COLOR set, TERM=dumb, or --no-color>

## Errors
<catch expected errors and rewrite for humans with an action; group similar errors; put the most important line last; unexpected errors: where the log is and how to report, ideally a pre-filled issue URL>

## Interactivity
<prompt only when stdin is a TTY and --no-input is absent; always allow the answer via flag; confirm destructive actions unless --force>

## Robustness
<validate early; timeouts on network; progress for anything over a few seconds; make interrupted operations resumable; crash-only cleanup>

## Future-proofing
<additive changes only; deprecate with a warning before removing; human output may change, --json/--plain must be stable; version your machine-readable output if it is consumed widely>

## Distribution and analytics
<single binary where possible; native package managers otherwise; uninstall instructions; never phone home without opt-in consent>

## CLI checklist
<12–15 items>
```

- [ ] **Step 3: Run `bash demo/process-hygiene/check-skill.sh`.** Expected: two "references missing file" failures remain (jvm, shell).

- [ ] **Step 4: Commit**

```bash
git add skills/process-hygiene/references/concurrency.md skills/process-hygiene/references/cli.md
git commit -m "Add process-hygiene concurrency and CLI references"
```

---

### Task 4: jvm.md and shell.md

**Files:**
- Create: `skills/process-hygiene/references/jvm.md`
- Create: `skills/process-hygiene/references/shell.md`

**Interfaces:**
- Consumes: SKILL.md §3, §4 numbering.

- [ ] **Step 1: Verify `StructuredTaskScope` status** before writing: run `npx ctx7@latest library "Java" "StructuredTaskScope JEP status preview final JDK version"` then `npx ctx7@latest docs <id> "..."` and use the answer (preview vs final, which JDK) in the text. Keep to two commands.

- [ ] **Step 2: Write `jvm.md`** (target 110–150 lines) with runnable Java snippets:

```markdown
# JVM reference (SKILL.md §3, §4)

## Subprocesses with ProcessBuilder
<snippet: ProcessBuilder with redirectErrorStream(true), explicit environment() map cleared and populated, start(), reader thread draining stdout via a virtual thread, waitFor(timeout), destroy → waitFor(2s) → destroyForcibly>

## Kill the whole tree
<snippet:
  static void killTree(ProcessHandle root) {
      root.descendants().forEach(ProcessHandle::destroy);
      root.destroy();
      // brief wait, then forcibly
      root.descendants().forEach(ProcessHandle::destroyForcibly);
      root.destroyForcibly();
  }
  registered in a shutdown hook for every long-lived child; note descendants() is a snapshot, call it again after destroy>

## Executors are owned
<snippet: component holds ExecutorService; close(): shutdown(); awaitTermination(deadline); shutdownNow(); awaitTermination(short). JDK 19+: ExecutorService is AutoCloseable, try-with-resources. Executors.newVirtualThreadPerTaskExecutor() as the default for blocking work>

## StructuredTaskScope
<status from Step 1; snippet with the current API: open a scope, fork, join, handle failure; how it maps to SKILL.md §4>

## Shutdown hooks
<Runtime.addShutdownHook rules: fast and bounded, own timeout, no reliance on logging framework state, no dependence on other hooks' order, idempotent; snippet with a CountDownLatch-based drain and deadline>

## Signals
<JVM maps SIGTERM/SIGINT to shutdown hooks; SIGKILL cannot be caught; sun.misc.Signal is internal, avoid; exit codes via System.exit(n) after hooks unless in a hook (then Runtime.halt)>

## InterruptedException
<never swallow; either rethrow, or Thread.currentThread().interrupt() and return; snippet of the wrong and right version>

## Daemon threads
<acceptable only when the thread's abrupt death at exit cannot corrupt anything (metrics tick, cache warmer); require a comment; never for anything that writes>

## Timeouts
<Future.get(timeout), HttpClient connectTimeout and request timeout, JDBC loginTimeout and statement timeout, Lock.tryLock(timeout), BlockingQueue.poll(timeout). A blocking call with no timeout in a hot path is a finding>

## Checklist
<8–10 items>
```

- [ ] **Step 3: Write `shell.md`** (target 70–100 lines) with a complete template script:

```markdown
# Shell reference (SKILL.md §3, §4)

## Strict mode
<set -euo pipefail; IFS; why each flag>

## Own your children
<template:
  #!/usr/bin/env bash
  set -euo pipefail
  pids=()
  cleanup() {
    local code=$?
    trap - EXIT INT TERM
    for pid in "${pids[@]:-}"; do
      [[ -n "$pid" ]] && kill -- -"$pid" 2>/dev/null || kill "$pid" 2>/dev/null || true
    done
    wait 2>/dev/null || true
    exit "$code"
  }
  trap cleanup EXIT INT TERM
  setsid some-server --port 8080 & pids+=($!)
  # ... work ...
  wait
 explanation: setsid puts the child in its own process group so kill -- -pid kills its whole tree; record every `&`; `wait` before exit; trap runs on EXIT too so a `set -e` failure still cleans up>

## timeout
<`timeout --kill-after=5s 30s cmd`; macOS needs coreutils `gtimeout`>

## Intentional detaching
<setsid nohup cmd >log 2>&1 & disown; document why; the detached process must have another owner (launchd/systemd/supervisor); never in CI or agent scripts>

## Entrypoints
<exec "$@" as the last line; why sh -c eats signals; tini/--init when the app spawns children>

## Output
<data to stdout, messages to stderr via >&2; exit codes; quiet on SIGPIPE>

## Checklist
<6–8 items>
```

- [ ] **Step 4: Run `bash demo/process-hygiene/check-skill.sh`.** Expected: `check-skill: OK`.

- [ ] **Step 5: Commit**

```bash
git add skills/process-hygiene/references/jvm.md skills/process-hygiene/references/shell.md
git commit -m "Add process-hygiene JVM and shell references"
```

---

### Task 5: Codeps as a verification gate in pragmatic-architecture

**Files:**
- Modify: `skills/pragmatic-architecture/SKILL.md:3` (description), `:21` (intro), `:45-51` (§2), `:53-77` (§3), `:173-174` (checklist)

- [ ] **Step 1: Description.** Replace the final clause `use Codeps when the request specifically needs dependency metrics, cycle analysis, module extraction, or compile-time diagnosis.` with `use Codeps when the request needs dependency metrics, cycle analysis, module extraction, or compile-time diagnosis, or to verify that newly added packages or cross-package dependencies introduced no cycle.`

- [ ] **Step 2: Intro (line 21).** Replace `for broader dependency, extraction, or compilation analysis, use the Codeps skill.` with `for dependency, extraction, or compilation analysis, and to verify that a change which adds a package, module, or cross-package dependency kept the graph acyclic and one-way, use the Codeps skill.`

- [ ] **Step 3: §2 addition.** After the paragraph ending `extract a third one that both depend on.` add:

```markdown
Do not verify acyclicity by reading imports. When a change introduces a new package, a new build module, or a new import across package boundaries, build it, regenerate the Codeps report, and compare against the pre-change snapshot. This applies to new feature code as much as to refactors: the cheapest time to find a cycle is before the first commit that contains it.
```

- [ ] **Step 4: §3 addition.** After the paragraph ending `wait for a demonstrated third use and a simpler shared interface (§6).` add:

```markdown
When measuring a package-by-feature codebase with Codeps, configure it to collapse each feature's sub-packages (`http`, `domain`, `db`) into one node, so the report shows the feature graph rather than dozens of small layer packages. The codeps skill describes the `collapse` setting.
```

- [ ] **Step 5: Checklist.** After the line `- [ ] Any import cycle between modules or packages? → merge, move code to its feature, or extract a third module only when the evidence supports it.` add:

```markdown
- [ ] New package, build module, or cross-package edge introduced? → regenerate the Codeps report and compare with the pre-change snapshot before calling it acyclic (§2).
```

- [ ] **Step 6: Verify.** `grep -c -i codeps skills/pragmatic-architecture/SKILL.md` is at least 7. `sed -n '3p' skills/pragmatic-architecture/SKILL.md | wc -l` is 1.

- [ ] **Step 7: Commit**

```bash
git add skills/pragmatic-architecture/SKILL.md
git commit -m "Use Codeps to verify new structure, not only refactors"
```

---

### Task 6: Codeps description and feature-collapse configuration

**Files:**
- Modify: `skills/codeps/SKILL.md:3` (description), `:8` (first paragraph)
- Modify: `skills/codeps/references/codeps.md:7-9` ("Configure once")

- [ ] **Step 1: Description.** Replace `Analyze a codebase's package or file dependency structure before refactoring:` with `Analyze a codebase's package or file dependency structure before refactoring or after adding new packages, modules, or cross-package dependencies:`. Add before `Do not use for` the clause `also use to verify that a feature branch introduced no cycle or coupling regression;`.

- [ ] **Step 2: First paragraph.** Replace `**Measure the dependency graph before cutting.**` with `**Measure the dependency graph before cutting and after adding.**` and append to the paragraph: `The baseline-then-compare loop below applies to a feature branch that adds packages or cross-package imports exactly as it does to a refactoring.`

- [ ] **Step 3: Configure once.** After the sentence ending `See the source-specific reference selected by [the main skill](../SKILL.md).` add:

```markdown
Shape the configuration to the codebase's structure before trusting any number:

- Set `include` to the project's root package so third-party and JDK packages do not appear as nodes.
- Set `skip-tests: true` unless test structure is the question.
- When packages are organized by feature (each feature holding its own `http`, `domain`, `db` sub-packages), add `collapse` for the feature root so every feature is one node. Without it the report shows dozens of small layer packages and feature-level cycles disappear into noise.

```yaml
projects:
  app:
    root: .
    source: semanticdb
    skip-tests: true
    inputs:
      - target/out/jvm
    include:
      - com.example.app
    collapse:
      - com.example.app.features.*
```

When layer-level coupling inside one feature is the question, add a second project entry without `collapse` and compare the two reports; do not switch the setting on a single project between runs, because that breaks history comparison.
```

- [ ] **Step 4: Verify.** `grep -n collapse skills/codeps/references/codeps.md` shows the block. `grep -rci snowplow skills/` is 0 for every file.

- [ ] **Step 5: Commit**

```bash
git add skills/codeps/SKILL.md skills/codeps/references/codeps.md
git commit -m "Document Codeps config for package-by-feature and new-structure checks"
```

---

### Task 7: README bullet

**Files:**
- Modify: `README.md:5-6`

- [ ] **Step 1:** After the `codeps/` bullet add:

```markdown
- [`process-hygiene/`](./skills/process-hygiene) — how a process behaves at its boundary (12-factor + clig.dev + structured concurrency): flags vs env vars vs config files with fail-fast validation, file-first secrets, SIGTERM drain-with-deadline shutdown, crash-only design, owning every thread and subprocess so nothing is orphaned, stdout/stderr and structured logs, stateless restarts, and CLI conventions. Includes JVM and shell references.
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "List process-hygiene skill in README"
```

---

### Task 8: Demo fixtures, expected findings, and runner

**Files:**
- Create: `demo/process-hygiene/README.md`
- Create: `demo/process-hygiene/fixtures/jvm/Main.java`
- Create: `demo/process-hygiene/fixtures/shell/dev.sh`
- Create: `demo/process-hygiene/fixtures/cli/tool.py`
- Create: `demo/process-hygiene/fixtures/negative/Pricing.java`
- Create: `demo/process-hygiene/expected.md`
- Create: `demo/process-hygiene/run.sh`

**Interfaces:**
- Produces: `expected.md` keyed by fixture name; `run.sh` writing `demo/process-hygiene/results/<fixture>.{with,without}.md`. Task 9 reads these.

- [ ] **Step 1: `fixtures/jvm/Main.java`** with exactly these planted violations (comment-free so the model has to find them):
  1. `System.getenv("DB_HOST")`, `System.getenv("DB_PASSWORD")`, `System.getenv("PORT")` called in three different methods, no validation, `PORT` parsed with `Integer.parseInt` inside a request handler.
  2. `new ProcessBuilder("sh", "-c", "tail -f /var/log/app.log").start()` with the `Process` handle dropped and stdout never read.
  3. `Executors.newFixedThreadPool(8)` as a static field, never shut down.
  4. `new Thread(() -> { while (true) { ...; Thread.sleep(1000);} }).start()` with `catch (InterruptedException e) {}` empty.
  5. No shutdown hook; `main` starts an `HttpServer` and returns.
  6. Log line `System.out.println("connecting with password " + pw)`.
  About 70 lines. Compiles in principle (imports present) but is not built by the demo.

- [ ] **Step 2: `fixtures/shell/dev.sh`** (~20 lines): no `set` line; `npm run dev &` and `python -m http.server 9000 &` with no PID recorded; `sleep 5; curl localhost:3000/health`; `echo "done"`; exits leaving both servers; error messages via `echo` to stdout; `API_TOKEN=$1` passed on the command line to curl.

- [ ] **Step 3: `fixtures/cli/tool.py`** (~40 lines): `sys.argv[1]` and `sys.argv[2]` positionals, `sys.argv[3]` is an API key; no `--help`; prints progress lines to stdout interleaved with JSON result; `sys.exit()` with no code on error; colors via raw ANSI codes unconditionally; catches `KeyboardInterrupt` and prints "bye" but leaves a temp file.

- [ ] **Step 4: `fixtures/negative/Pricing.java`** (~30 lines): pure function `Money priceFor(Order order, Discounts d)` with a couple of rules. No config, threads, IO. Used to check the skill stays quiet.

- [ ] **Step 5: `expected.md`**: per fixture, a checklist of findings a reviewer following the skill must raise, one line each with the SKILL.md section. jvm: 8 items (three getenv sites → one loader; password in env → file; PORT parsed in handler; ProcessBuilder handle dropped and stdout undrained; pool never shut down; empty InterruptedException catch; no shutdown hook / no SIGTERM protocol; password logged). shell: 5 items (no strict mode; two backgrounded servers unowned; no trap/wait; token on command line; errors to stdout). cli: 7 items (positionals → flags; no --help; secret in argv; progress on stdout mixing with JSON; exit code; colors unconditional; temp file left on Ctrl-C). negative: "no process-hygiene findings expected; a review that invents config/lifecycle issues here is a false positive".

- [ ] **Step 6: `run.sh`**:

```bash
#!/usr/bin/env bash
# Ask a small model to review each fixture with and without the skill.
# Usage: demo/process-hygiene/run.sh [model]   (default: haiku)
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
demo="$root/demo/process-hygiene"
model="${1:-haiku}"
out="$demo/results"
mkdir -p "$out"
skill="$root/skills/process-hygiene"

review_prompt() {
  local fixture="$1"
  cat <<P
Review the file below for production-readiness problems. List concrete findings, one per line, each with a one-sentence fix. If you find nothing wrong, say so.

FILE: $fixture
\`\`\`
$(cat "$demo/fixtures/$fixture")
\`\`\`
P
}

for fixture in jvm/Main.java shell/dev.sh cli/tool.py negative/Pricing.java; do
  name="${fixture%%/*}"
  echo "== $name (without skill)" >&2
  review_prompt "$fixture" | claude -p --model "$model" > "$out/$name.without.md"
  echo "== $name (with skill)" >&2
  {
    echo "You must follow this skill while reviewing. Read it fully, then read only the reference files it points you to for the file type at hand."
    echo; echo "SKILL.md:"; cat "$skill/SKILL.md"
    for ref in "$skill"/references/*.md; do echo; echo "$(basename "$ref"):"; cat "$ref"; done
    echo; review_prompt "$fixture"
  } | claude -p --model "$model" > "$out/$name.with.md"
done
echo "results in $out" >&2
```

  `chmod +x`. Note in README that `claude -p` cannot be run from inside a Claude Code session; run it from a plain terminal, or use the in-session Agent procedure described there.

- [ ] **Step 7: `README.md`** for the demo: what the fixtures plant, how to run `run.sh`, the in-session alternative (dispatch a `haiku` Agent with the with-skill prompt), how to score against `expected.md`, and where results land. State that `results/` is committed so the last run is visible.

- [ ] **Step 8: `bash demo/process-hygiene/check-skill.sh`** still OK (it scans demo too).

- [ ] **Step 9: Commit**

```bash
git add demo/process-hygiene
git commit -m "Add process-hygiene demo fixtures, expected findings, and runner"
```

---

### Task 9: Run the demo with a small model and iterate

**Files:**
- Create: `demo/process-hygiene/results/*.md` (8 files)
- Create: `demo/process-hygiene/results/SCORE.md`
- Possibly modify: `skills/process-hygiene/SKILL.md` or references, if the model misses planted findings.

- [ ] **Step 1: Dispatch 8 Agent runs** (model `haiku`, fresh general-purpose agents, all in one message): for each of the four fixtures, one agent gets the review prompt alone, one gets "Read `skills/process-hygiene/SKILL.md` and follow it, reading only the reference files relevant to this file type" plus the review prompt. Each agent returns its findings as a Markdown list. Save each result verbatim to `demo/process-hygiene/results/<name>.{with,without}.md`.

- [ ] **Step 2: Score.** For each fixture and mode, tick which `expected.md` items appear. Write `results/SCORE.md` as a table: fixture | expected | found without skill | found with skill | false positives with skill. Negative fixture: count any config/lifecycle/concurrency findings as false positives.

- [ ] **Step 3: Iterate once if needed.** If the with-skill run misses an expected item on jvm or shell, find the rule in SKILL.md that should have caught it, make it more concrete (add the exact API name or pattern the model should look for), re-run only that fixture, update the result file and SCORE.md. Stop after one iteration; note remaining misses in SCORE.md as known gaps.

- [ ] **Step 4: `bash demo/process-hygiene/check-skill.sh`** OK.

- [ ] **Step 5: Commit**

```bash
git add demo/process-hygiene/results skills/process-hygiene
git commit -m "Record process-hygiene demo results with a small model"
```

---

### Task 10: Draft PR

- [ ] **Step 1: Push** `git push -u origin process-hygiene-skill`.

- [ ] **Step 2: Invoke the `show-me` skill** to produce the PR visuals: a `diff` block showing the repo layout before/after (two skills → three, plus demo), and a `mermaid` flowchart of the shutdown protocol or of "task → skill loads → reference selected by file type".

- [ ] **Step 3: Open the draft PR** with the `gh` CLI (`pr create --draft`), title `Add process-hygiene skill (closes #5)`, and a body that leads with a two-sentence TL;DR, then the two visuals each with one line of prose, then short sections: what it does, what it doesn't do (no Python/Node references yet, CLI not split out), demo results table copied from SCORE.md, follow-ups. End with `🤖 Generated with [Claude Code](https://claude.com/claude-code)`. Body must contain `Closes #5`.

- [ ] **Step 4: Report** the PR URL and the SCORE.md table to the user.

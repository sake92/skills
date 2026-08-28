---
name: scalpel
description: Use when analyzing a codebase's package/file dependency structure to plan refactoring — find dependency cycles at package or file level, decide whether to split a package/module, reduce compile time, identify over-exposed APIs (ports/mutPorts/exposure/utilization), find dead code (orphans), improve encapsulation, slim JSON payloads bloated by over-shared common types, or back split decisions with change history — which files/packages change together (logical coupling), where the churn is, what's hot vs cold. Triggers: "why is compilation slow", "find dependency cycles", "tightly coupled packages", "should we split this package/module", "which packages over-expose their API", "find dead code", "refactor the architecture", "API returns fields nobody uses", "analyze dependencies", "reduce coupling", "which files change together", "which files should be in the same package/folder", "logical coupling", "change coupling", "what should I split first". Works on Scala via SemanticDB, and any language whose dependency data can be exported as JSON (madge, pydeps, go list).
---

# Scalpel

**Measure the dependency graph before cutting.** codeps ([github.com/sake92/codeps](https://github.com/sake92/codeps)) turns compiler output into a package/file dependency graph and emits a flat metrics report: cycles with simulated cut candidates, per-node exposed-surface metrics and orphans. This skill is the analyze → diagnose → plan → verify workflow on top of it. Every recommendation must cite evidence: a cycle row, a surface row, the summary — or, when the move is "split by cohesion", a code-maat coupling row (Step 3b).

## When to use

- Finding **dependency cycles** ("loops") at package or file level
- Deciding **whether to split a package/module** — which cycles keep modules welded together
- **Backing split decisions with change history** — which files/packages actually change together (logical coupling), what's hot vs cold (churn, age)
- **Improving encapsulation** — which packages over-expose API or leak mutable state
- Analyzing **why compilation is slow** — file-level cycles churn incremental compilers; the critical path is the parallel-build floor
- **Finding dead code** — orphans (zero fan-in AND zero fan-out)
- Diagnosing **JSON payload bloat** from reusing shared/common types at the API edge
- Any language: Scala via SemanticDB; others via the standard JSON export format

When NOT to use: mechanical moves inside one package, or immediate code changes without analysis. For symbol definitions/usages/renames use `scalex`; scalpel answers "which packages/files cross this boundary", not "where is X defined".

## Setup

A bootstrap script handles the codeps jar: `scripts/codeps-cli` (next to this SKILL.md). It downloads the pinned release jar on first use (cached at `~/.cache/codeps/`), passes arguments through, and self-heals stale caches left by previous skill versions. Use the absolute path in every command — no shell variables.

- `CODEPS_JAR=/path/to/jar` — use a local build instead (e.g. `~/.deder/out/cli/assembly/out.jar`)
- `CODEPS_CACHE_DIR` — change the cache location

A second bootstrap script, `scripts/code-maat-cli`, handles the code-maat jar (github.com/adamtornhill/code-maat, GPL v3 — downloaded at runtime like codeps, never bundled) the same way: pinned release, cached at `~/.cache/code-maat/`, self-healing, `-Djava.awt.headless=true` added automatically. It is only needed for Step 3b. Env: `CODE_MAAT_JAR`, `CODE_MAAT_CACHE_DIR`; JVM options via `JAVA_TOOL_OPTIONS` (e.g. `JAVA_TOOL_OPTIONS=-Xmx4g` for large logs — code-maat processes logs in memory).

### Finding input

codeps never compiles anything — point it at output your build already produced:

| Build | SemanticDB location |
|---|---|
| scala-cli | `scala-cli compile --server=false --semanticdb -d classes src/` → `classes/META-INF/semanticdb` |
| sbt (semanticdb plugin) | `target/scala-*/classes/META-INF/semanticdb` per module |
| mill | `out/**/compile.dest/classes/META-INF/semanticdb` |
| deder | `.deder/out` — pass the whole root; `export` walks it recursively |

Other languages: emit the standard JSON export format with your ecosystem tool (madge, pydeps, `go list`, …) and pipe it into `report` stdin. Nodes are `{"id", "kind", "parentId"?, "file"?, "isExposed"?, "ports"?, "mutPorts"?}` with kinds `package`/`file` (`type`/`member` still accepted on input for backward compatibility); edges are `{"source", "target", "weight"?}`.

## The pipeline

Two subcommands, a two-step pipeline:

```
export (producer) ──> deps.json ──> report (analyzer) ──> table | JSON report
  (parse once,        (package +        --scope packages|files
   aggregate to        file nodes)      --format table|json
   package/file level)
```

- `export --from semanticdb [--root DIR] <dirs...> [-o deps.json]` — pure parser; no filter flags. Walks directories recursively for `*.semanticdb`; unparseable files warn on stderr and are skipped. Output has **package and file nodes only** — types/members are collapsed into their file (or root package) at export time, with `ports`/`mutPorts` summed and edges aggregated with summed weights.
- `report --scope packages|files [--format table|json] [-i inc] [-e exc] [-c collapse] [--skip-tests] [--test-pattern PAT] [-o out] deps.json` — `--scope` is **required**, one scope per run. Default format is `table`; `--format json` emits the same data machine-readably. Reads exactly one input: a deps.json file, or `-` for stdin.

`--scope packages` = the whole package graph (module-splitting decisions). `--scope files` = the file graph of the packages selected with `-i` (compile-time decisions — incremental compilers recompile by file).

Report JSON shape (camelCase):

```json
{"scope": "packages", "generatedAt": "...",
 "summary": {"nodes", "edges", "nodesInCycles", "orphans", "criticalPathLength"},
 "cycles": [{"id": "scc:<min-member>", "members": [...], "size", "extFanIn", "minCutsEstimate",
             "cutCandidates": [{"edge": [s, t], "weight"}]}],
 "surface": [{"node", "fanIn", "fanOut", "ports", "mutPorts", "exposure", "utilization|null"}],
 "orphans": [...]}
```

Include/exclude (`-i`/`-e`) match each node's **root package** by whole-segment prefix (`-i com.example.b` matches `com.example.b` and below, not `com.example.big`); excludes win. Edges survive only when both endpoints survive (self-edges dropped); childless packages are pruned. `-c` collapses subtrees (`com.example.**`, `org.lib.*`) for readable reports. `--skip-tests` drops nodes in test files (`**/test/**`, `*Spec`/`*Test`/`*Tests`/`*Suite` conventions); `--test-pattern` replaces those globs (requires `--skip-tests`).

## Workflow

### Step 1 — Export once, save deps.json

```bash
bash "/abs/path/to/skills/scalpel/scripts/codeps-cli" export --from semanticdb .deder/out -o /tmp/deps.json
```

`export` merges all inputs into one graph, so pass every module dir (or the build root) at once. `--root <dir>` re-bases **absolute** source URIs relative to that directory (default: the current working directory) — pass it only when file ids come out absolute or with a machine prefix. Modules compiled with different sourceroots can produce **mixed** ids in one export (some relative, some absolute-looking); `--root` fixes only the truly absolute ones, the rest stay as their build emitted them. Prefer writing to `/tmp`, not the repo.

### Step 2 — Report always, read the table

```bash
bash "/abs/path/to/skills/scalpel/scripts/codeps-cli" report --scope packages /tmp/deps.json
```

This is the evidence base. Read it before reading any code. Keep the JSON for scripts/diffs:

```bash
bash "/abs/path/to/skills/scalpel/scripts/codeps-cli" report --scope packages --format json /tmp/deps.json -o /tmp/report.json
```

### Step 3 — Drill down to file level

The package report names package cycles; file-level cycles matter for incremental compilation. Descend into the packages of interest:

```bash
bash "/abs/path/to/skills/scalpel/scripts/codeps-cli" report --scope files -i com.example /tmp/deps.json
```

Use the *shared* root package: filtering to just one side (`-i p.a`) drops the other side's nodes and the crossing edges with them. `--scope files` on a package is also the fat-package check: each file is one surface row, so a package with dozens of rows is a long compile unit.

### Step 3b — Evolutionary coupling (optional, when splitting is on the table)

The graph is structural: it shows what *can* change together because it depends together. Change history shows what *actually does*. Run this step when Step 5 planning hits a cohesion decision (Fat/Wide/hub split, cycle extraction) or the question is "which files/packages should be close". Graph first, history second — coupling is a second lens, never a replacement for the graph.

Export the git history once (the git2 format, verbatim — code-maat parses these exact separators):

```bash
git -C <repo> log --all --numstat --date=short \
  --pretty=format:'--%h--%ad--%aN' --no-renames --after=YYYY-MM-DD > /tmp/maat.log
```

`--after` bounds the window to recent history — old, already-fixed design issues would pollute the signal (Tornhill's practice: about a year). `--all` covers every branch; `--no-renames` keeps renames visible as churn instead of delete+add. Exclude vendored/third-party trees with git pathspecs: `-- . ":(exclude)vendor/"`. Bounded windows also keep code-maat's in-memory processing fast.

Then run the analyses (CSV on stdout):

```bash
bash "/abs/path/to/skills/scalpel/scripts/code-maat-cli" -l /tmp/maat.log -c git2 -a coupling
bash "/abs/path/to/skills/scalpel/scripts/code-maat-cli" -l /tmp/maat.log -c git2 -a soc
bash "/abs/path/to/skills/scalpel/scripts/code-maat-cli" -l /tmp/maat.log -c git2 -a entity-churn
bash "/abs/path/to/skills/scalpel/scripts/code-maat-cli" -l /tmp/maat.log -c git2 -a age
```

Reading the CSVs:

| Analysis | Columns | What it tells you |
|---|---|---|
| `coupling` | `entity, coupled, degree, average-revs` | `degree` % = chance that a change to A also touches B. High degree across a package boundary = hidden dependency → move closer or extract the shared part; high degree within a fat package = files that must stay together. Low degree across a planned split = safe to separate. `average-revs` is the denominator — distrust high degree with a tiny average (coincidence). Defaults `-i 30` (min degree %), `-m 5` (min shared revs), `-n 5` (min revs per file) hide weak pairs and empty out young codebases; lower them deliberately (`-i 10 -m 3 -n 3`) when the split boundary matters or the history is short. |
| `soc` | `entity, soc` | sum of coupling = change attractor: a file that co-changes with many others — a natural split nucleus or a hidden hub. |
| `entity-churn` | `entity, added, deleted, commits` | hot fat = worth splitting first; cold fat = leave alone. |
| `age` | `entity, age-months` | months since last change — second coldness signal; high age + near-zero churn = skip. |

Package-level view ("which packages should be close?"): aggregate files to logical groups with a layers file, one `path => group` per line. Plain prefixes (`modules/core => core`) match as `^path/`; full regexes need anchors (`^src/.*Tests\.cs$ => tests`). ⚠️ **Files not matched by any line are silently dropped** — cover the whole tree or you analyze a subset without knowing it. ⚠️ **First matching line wins** — a broad regex placed after a prefix that also matches it never fires (`modules/core => core` swallows `modules/core/test/...`), so order tests-like regexes first:

```bash
printf '^src/.*Tests\\.scala$ => tests\nmodules/core => core\nmodules/cli => cli\n' > /tmp/layers.txt
bash "/abs/path/to/skills/scalpel/scripts/code-maat-cli" -l /tmp/maat.log -c git2 -a coupling -g /tmp/layers.txt
```

Cite the rows in the plan: a cross-package pair at 60%+ degree is evidence for "move closer or extract the shared part"; within a fat package, cluster files by what changes together and cut along the low-coupling gaps.

### Step 4 — Diagnose

Cycles are the priority order (both scopes):

| Grade | Where | Meaning | Action |
|---|---|---|---|
| package cycle | `--scope packages` | modules welded together; an SCC cannot be compiled or split independently | break it (Step 5) |
| file cycle | `--scope files` | an SCC recompiles as a unit on every edit — kills incremental compilation | fix if cheap |

One representative closed path is reported per SCC (`members`, first node repeated at the end) — the real SCC can be larger (`size`). If after breaking a reported cycle the next re-run shows *another* cycle with overlapping members, you are peeling the same knot: keep cutting until `nodesInCycles` hits 0.

Also in the report:

- `summary.criticalPathLength` — longest path (in edges) through the condensation DAG: the structural lower bound on best-case parallel build time, cycles or not.
- `summary.orphans` / `orphans` — nodes with zero fan-in AND zero fan-out: dead-code-removal candidates (step 1 of the improvement loop).
- `cycles[].extFanIn` — how much outside stuff depends into the cycle's blast radius (rank tiebreaker after `size`).
- `cycles[].minCutsEstimate` — greedy estimate of the cuts needed to dissolve the cycle (heuristic, not a minimum feedback-edge set). Computed per scope run: re-filter or descend and the number changes — it is a property of this report's graph, not of the code.
- `cycles[].cutCandidates` — the cycle's **internal** edges whose removal **resolves** it, each simulated; sorted by weight ascending, top 6. Edges that merely shrink the cycle are deliberately absent — cutting them wouldn't dissolve it. Can be **empty** even when `minCutsEstimate` > 0: a dense knot where no single edge dissolves the SCC. Then read the member files the cycle names and break by extraction/inversion — don't hunt for a magic edge.
- `surface` rows — `ports` (weighted exposed surface: 3 per exposed type/object, 1 per exposed def/val, 0.5 per sealed-hierarchy member, +1 per given/implicit), `mutPorts` (exposed `var`s or mutable-collection-typed vals/defs — a coupling channel with no graph edge), `exposure` = `ports + 3*mutPorts`, `utilization` = `fanIn / ports` (`null` when no consumers — meaningful, not a 0). Sorted by utilization ascending: the most exposed-for-its-use nodes first.
- High `fanIn` = change amplification (every edit ripples to all dependents).

### Step 5 — Plan

| Signature | Evidence | Move |
|---|---|---|
| Cycle | cycles row + its `cutCandidates` | cut the lowest-weight resolving edge (or, when `cutCandidates` is empty, break the dense knot by extraction/inversion — Step 4); extract the shared part into a third package both can depend on; invert the dependency (move the closing references down); merge the two if small. Cut until `nodesInCycles` is 0. |
| Over-exposed, under-used | surface: high `ports`, `utilization` null/low | narrow the API: make members `private` (export already drops private symbols, so their ports vanish) → re-run → verify; split by consumer. |
| Mutable leak | `mutPorts` > 0 | encapsulate the mutable state — an exposed var/mutable collection is a hidden channel (weighted 3× in `exposure`). |
| Hub (high fan-in) | surface | split by consumer; push stable abstractions down; narrow the API. |
| Wide (high fan-out) | surface | split by cohesion (rule of three: 2 similar things can stay; extract at 3+) — run Step 3b for change-together evidence. |
| Orphan | `orphans` | delete — dead code. |
| Fat package | files-scope report (many rows) | split by cohesion — run Step 3b to cluster files by what changes together and cut along low-coupling gaps. |
| Change-coupled pair | coupling row: high `degree` + decent `average-revs` | keep the pair together; if it straddles a package boundary, move them closer or extract the shared part into a third package. |
| Change attractor | top `soc` rows | split nucleus or hidden hub — read what it co-changes with before cutting. |
| Hot-fat package | `entity-churn` high + many files-scope rows | split first — the churn is where the cost is. |
| Cold | `age` high, `entity-churn` ~0 | skip — cold code doesn't repay splitting. |
| Long critical path | `summary.criticalPathLength` | structural floor on parallel build; the big win comes from breaking cycles, not shortening the path. |

Compile time improves primarily by removing cycles (restores incremental compilation) and shrinking hubs (smaller ripple). Do NOT suggest moves the graph doesn't support — if a cut needs ownership semantics, read the files the report names, but let the graph name them first.

### Step 6 — Verify

Re-run Steps 1–2 after the change: `nodesInCycles` down or 0, `utilization` up, `ports`/`exposure` down, orphans gone. A refactoring that can't be verified on the graph is not done. A *new* cycle after a change means stop and diagnose, don't push on.

Coupling is planning evidence, not a post-refactor metric: after splitting a file, its new halves have no shared history yet, so coupling cannot drop "on re-run" — don't fake that verification. The Step 6 loop stays on the graph; coupling can only be re-checked later with a forward-shifted `--after` window.

## JSON payloads (edge bloat)

A hub of shared "common" model types is a double cost: (1) coupling — every change recompiles everything importing it; (2) payload bloat — API edge code serializing the shared types puts every field on the wire, including ones clients never read.

Graph signs: an edge/API package with high `fanIn` (imported by many non-edge packages) and high `ports` (shared model types on the wire). Descend with `--scope files -i <api-package>` to see which files hold the types; then read the serialization code for verbatim reuse.

Fix: give the edge its own DTOs (small, per-consumer types) instead of reusing the shared model for transport; keep shared types internal-only. Expect the hub's `fanIn`/`ports` to drop. Duplicating 2–3 fields beats coupling every client to the hub — see pragmatic-architecture (duplication vs wrong abstraction).

## Common mistakes

- Looking for a `draw` subcommand or a `-g` granularity flag → gone in v2. codeps has `export` + `report --scope packages|files` only.
- Running `report` without `--scope` → `Missing argument: -s --scope <scope>`; `--scope` is required.
- Expecting `--format json` on `export` → no: `export` always emits deps.json (the graph). `report --format json` emits the report JSON. Two different JSONs.
- Feeding semanticdb dirs to `report` → it reads exactly one deps.json (file or `-`); a directory produces an uncaught `java.io.IOException: Is a directory` stack trace. Merge everything in `export` instead.
- Over-filtering → `no nodes remain after filtering` (hard error on `report`).
- `report` has no `--help` — it is parsed as an input path and errors. Usage errors print the full option signature; the CLI reference is at sake92.github.io/codeps/reference/cli.html.
- `--test-pattern` without `--skip-tests` → error; it replaces the built-in patterns.
- `export` warns and skips unparseable files (exit 0); `report` hard-errors on malformed JSON — fix the input, don't ignore.
- Old granular deps.json (type/member nodes) still parses — the analyzer aggregates it the way `export` does; new exports never emit it.
- Eyeballing files instead of running the pipeline — file-level cycles are invisible to file reading; the report is one command.
- Running code-maat coupling with the defaults (`-i 30 -m 5 -n 5`) and concluding "no coupling" — defaults hide weak pairs and empty out young codebases; lower them deliberately (`-i 10 -m 3 -n 3`) when the split boundary matters (Step 3b).
- Trusting a 100% `degree` with a tiny `average-revs` — coincidence, not coupling.
- A layers file that doesn't cover the whole tree — unmatched files silently vanish from the analysis (Step 3b).
- A layers file whose broad regexes sit after prefixes that swallow them — first match wins, so put tests-like regexes first (Step 3b).
- Forgetting `--all` or `--no-renames` on the log export — branch-restricted history or renames-as-delete+add distort churn and coupling.
- Unbounded logs — without `--after`, long-dead design issues pollute the signal and code-maat's in-memory processing can exhaust the heap (`JAVA_TOOL_OPTIONS=-Xmx4g`).

## Red flags — STOP

- "The project is small, I'll just read the files" — reading misses file-level cycles; the graph is reproducible at any size.
- "Compiling takes too long, let's skip the tool" — an existing build is all codeps needs (point at the semanticdb output).
- "I'm confident there are no cycles" — verify with `report`; the whole point is replacing beliefs with measurements.
- "The graph is wrong" without evidence — re-run `export`; diff the JSON; don't switch to guessing.
- "I'll fix it without re-running" — unverified refactoring is undone work; close the loop (Step 6).
- "Coupling replaces the graph" — structural and evolutionary coupling measure different things; when they disagree, the disagreement is itself a finding, not an error.
- "X-Ray hotspots" — code-maat has no complexity analysis; hotspots (complexity × churn) are CodeScene territory. This step gives only the history half.
- "The fat file is cold, split it anyway" — check churn/age first; cold code doesn't repay splitting.

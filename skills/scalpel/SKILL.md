---
name: scalpel
description: Use when analyzing a codebase's package/file dependency structure to plan refactoring — find dependency cycles at package or file level, decide whether to split a package/module, reduce compile time, identify over-exposed APIs (ports/mutPorts/exposure/utilization), find dead code (orphans), improve encapsulation, or slim JSON payloads bloated by over-shared common types. Triggers: "why is compilation slow", "find dependency cycles", "tightly coupled packages", "should we split this package/module", "which packages over-expose their API", "find dead code", "refactor the architecture", "API returns fields nobody uses", "analyze dependencies", "reduce coupling". Works on Scala via SemanticDB, and any language whose dependency data can be exported as JSON (madge, pydeps, go list).
---

# Scalpel

**Measure the dependency graph before cutting.** codeps ([github.com/sake92/codeps](https://github.com/sake92/codeps)) turns compiler output into a package/file dependency graph and emits a flat metrics report: cycles with simulated cut candidates, per-node exposed-surface metrics and orphans. This skill is the analyze → diagnose → plan → verify workflow on top of it. Every recommendation must cite graph evidence: a cycle row, a surface row, or the summary.

## When to use

- Finding **dependency cycles** ("loops") at package or file level
- Deciding **whether to split a package/module** — which cycles keep modules welded together
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
| Wide (high fan-out) | surface | split by cohesion (rule of three: 2 similar things can stay; extract at 3+). |
| Orphan | `orphans` | delete — dead code. |
| Fat package | files-scope report (many rows) | split by cohesion. |
| Long critical path | `summary.criticalPathLength` | structural floor on parallel build; the big win comes from breaking cycles, not shortening the path. |

Compile time improves primarily by removing cycles (restores incremental compilation) and shrinking hubs (smaller ripple). Do NOT suggest moves the graph doesn't support — if a cut needs ownership semantics, read the files the report names, but let the graph name them first.

### Step 6 — Verify

Re-run Steps 1–2 after the change: `nodesInCycles` down or 0, `utilization` up, `ports`/`exposure` down, orphans gone. A refactoring that can't be verified on the graph is not done. A *new* cycle after a change means stop and diagnose, don't push on.

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

## Red flags — STOP

- "The project is small, I'll just read the files" — reading misses file-level cycles; the graph is reproducible at any size.
- "Compiling takes too long, let's skip the tool" — an existing build is all codeps needs (point at the semanticdb output).
- "I'm confident there are no cycles" — verify with `report`; the whole point is replacing beliefs with measurements.
- "The graph is wrong" without evidence — re-run `export`; diff the JSON; don't switch to guessing.
- "I'll fix it without re-running" — unverified refactoring is undone work; close the loop (Step 6).

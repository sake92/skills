---
name: scalpel
description: Use when analyzing a codebase's package/module dependency structure to plan refactoring — find dependency cycles ("loops") at package, file, type, or method level, reduce compile time, simplify architecture, split oversized/god packages ("knots"), improve encapsulation, or slim JSON payloads bloated by over-shared common types. Triggers: "why is compilation slow", "find dependency cycles", "tightly coupled packages", "should we split this package/module", "improve encapsulation", "refactor the architecture", "API returns fields nobody uses", "analyze dependencies", "reduce coupling". Works on Scala (SemanticDB, method-level), Java (jdeps), and any language whose dependency data can be exported as JSON (madge, pydeps, go list).
---

# Scalpel

**Measure the dependency graph before cutting.** codeps ([github.com/sake92/codeps](https://github.com/sake92/codeps)) turns compiler output (or any exported dependency data) into a dependency graph at package, file, type, and member (method/field) granularity, with cycle severity grades, break candidates, and hub/knot metrics. This skill is the analyze → diagnose → plan → verify workflow on top of it. Every recommendation must cite graph evidence: a graded cycle, a hub metric, or a specific member edge.

## When to use

- Finding **dependency cycles** ("loops") — package, file, type, or method level
- Deciding **whether to split a package/module** — hardest knots (heavy in×out god-packages), easy wins (wide fan-out)
- **Improving encapsulation** — which exact methods/fields cross a boundary
- Analyzing **why compilation is slow** (cycles and hubs churn recompilation)
- Diagnosing **JSON payload bloat** caused by reusing shared/common types at the API edge
- Any language: Scala via SemanticDB, Java via jdeps, others via JSON export

When NOT to use: mechanical moves inside one package, or immediate code changes without analysis. For symbol definitions/usages/renames use `scalex`; scalpel answers "which members cross this boundary", not "where is X defined".

## Setup

A bootstrap script handles the codeps jar: `scripts/codeps-cli` (next to this SKILL.md). It downloads the pinned release jar on first use (cached at `~/.cache/codeps/`) and passes arguments through. Use the absolute path in every command — no shell variables.

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

Java projects: `jdeps -verbose:class -filter:none -cp classes classes > jdeps.txt` (note: `-verbose:class`, not `-verbose:package` — that is what the parser reads). jdeps data has no file/member nodes: at file/member levels it falls back to package/type ids, so don't misread those as real file/member cycles.

Other languages: emit the common JSON format (below) with your ecosystem tool (madge, pydeps, `go list`, …) and pipe it into `report`/`draw` stdin.

## The pipeline

Three subcommands, a two-step pipeline:

```
export (producer) ──> deps.json ──┬─> draw   -g package|file|type|member -f dot|mermaid
   (parse once)        (common    └─> report — graded cycles, knots, hubs, one pass
    semanticdb/jdeps     JSON)
```

- `export --from semanticdb|jdeps [--root DIR] <inputs...> [-o deps.json]` — pure parser; no filter flags. Walks directories recursively for `*.semanticdb`; unparseable files warn on stderr and are skipped.
- `draw -g package|file|type|member -f dot|mermaid [-i inc] [-e exc] [-c collapse] [--skip-tests] deps.json` — `-g` is **required**. Both formats list cycles in a comment line (`// cycles:` / `%% cycles:`).
- `report [-i inc] [-e exc] [-c collapse] [--skip-tests] deps.json -o report.json` — runs all four granularities in one pass, emits one self-contained JSON shaped for agents. The `graph`/`metrics` sections are embedded at package/file/type levels; the member-level graph is `null` (too large to embed) but its cycles are still reported — use deps.json (Step 3) for full member edges.

Common JSON format: `{"nodes": [{"id", "kind", "parentId"?, "file"?}], "edges": [{"source", "target", "weight"?}]}` — kinds are `package`/`file`/`type`/`member`; ids are hierarchical (`com.example.a.Foo#doWork`); `weight` = number of finer-grained references merged into the edge.

Include/exclude (`-i`/`-e`) match each node's **root package** by whole-segment prefix (`-i com.example.b` matches `com.example.b` and below, not `com.example.big`); excludes win. `-c` collapses subtrees (`com.example.**`) for readable diagrams. `--skip-tests` drops nodes in test files (`**/test/**`, `*Spec`/`*Test`/`*Tests`/`*Suite` conventions); `--test-pattern` replaces those globs (requires `--skip-tests`).

## Workflow

### Step 1 — Export once, save deps.json

```bash
bash "/abs/path/to/skills/scalpel/scripts/codeps-cli" export --from semanticdb .deder/out -o /tmp/deps.json
# Java:
bash "/abs/path/to/skills/scalpel/scripts/codeps-cli" export --from jdeps jdeps.txt -o /tmp/deps.json
```

`export` merges all inputs into one graph, so pass every module dir (or the build root) at once. `--root <dir>` (semanticdb only) re-bases **absolute** source URIs relative to that directory; relative URIs — what scala-cli/sbt/deder usually emit — are kept as-is, so pass it only when file ids come out absolute or with a machine prefix. It is a filesystem path, not a package pattern (that's what `-i` is for). Prefer writing to `/tmp`, not the repo.

### Step 2 — Report always, summarize

```bash
bash "/abs/path/to/skills/scalpel/scripts/codeps-cli" report -i com.example /tmp/deps.json -o /tmp/report.json
python3 "/abs/path/to/skills/scalpel/scripts/summarize-report.py" /tmp/report.json
```

This is the evidence base. Read it before reading any code.

### Step 3 — Drill down to member level

The report names cycle members at each level; deps.json holds the finest edges. To see exactly which methods close a boundary (e.g. the cycle `p.a ↔ p.b`), walk each edge endpoint to its root package:

```bash
python3 - <<'EOF'
import json
d = json.load(open("/tmp/deps.json"))
nodes = {n["id"]: n for n in d["nodes"]}
def root_pkg(nid):
    while True:
        n = nodes.get(nid)
        if not n or not n.get("parentId") or n["parentId"] not in nodes: return nid
        nid = n["parentId"]
for e in d["edges"]:
    if {root_pkg(e["source"]), root_pkg(e["target"])} == {"p.a", "p.b"}:
        print(e["source"], "->", e["target"])
EOF
```

Or render the boundary: `codeps-cli draw -g member -f mermaid -i com.example /tmp/deps.json` — use the *shared* root package; filtering to just one side (`-i p.a`) drops the other side's nodes and the crossing edges with them. These edges are the encapsulation evidence — cite them in the plan.

### Step 4 — Diagnose

Cycle grades are the priority order:

| Grade | Where | Meaning | Action |
|---|---|---|---|
| `bad` | package cycles | compile churn: an SCC cannot be incrementally compiled — any edit recompiles the whole SCC and its dependents | break it (Step 5) |
| `meh` | file cycles; type/member cycles crossing files | coupling smell, worth a look | fix if cheap |
| `fine` | type/member cycles within one file (e.g. implicit-instance pairs) | normal | leave it |

One representative elementary cycle is reported per strongly-connected component — the real SCC can be larger. If after breaking a reported cycle the next re-run shows *another* cycle with overlapping members, you are peeling the same knot: keep cutting until the grade disappears.

Also in the report:

- `breakCandidate` — the cycle member with lowest degree: the cheapest cut.
- `breakEdges` — the concrete cut list, ranked by cycles broken.
- `hardestKnots` — top-5 packages by `in × out`: the god packages to split.
- `easyWins` — top-5 by fan-out (fewest dependents as tiebreak): extract these first.
- Metrics `{in, out, hub}` per node at package/file/type level: high fan-in = change amplification (every edit ripples to all dependents).

File counts per package (fat packages — long compile units). This counts *files* per package; a single file holding many types is fat too — check the type-level metrics for those:

```bash
python3 - <<'EOF'
import json
from collections import Counter
d = json.load(open("/tmp/deps.json"))
nodes = {n["id"]: n for n in d["nodes"]}
def root_pkg(nid):
    while True:
        n = nodes.get(nid)
        if not n or not n.get("parentId") or n["parentId"] not in nodes: return nid
        nid = n["parentId"]
file_pkg = {}
for n in d["nodes"]:
    if n.get("file"): file_pkg.setdefault(n["file"], root_pkg(n["id"]))
print(sorted(Counter(file_pkg.values()).items(), key=lambda kv: -kv[1]))
EOF
```

### Step 5 — Plan

| Signature | Evidence | Move |
|---|---|---|
| Cycle, `bad` | graded cycle + its member edges (Step 3) | extract the shared part into a third package both can depend on; invert the dependency (move the closing references down); merge the two if small. Cut at `breakCandidate`/`breakEdges`. |
| Knot (high in×out) | `hardestKnots` | split by consumer: extract per-consumer types; push stable abstractions down. |
| Hub (high fan-in), esp. shared model types | metrics + Step 3 edges | move widely-used types to a leaf package; narrow the API; see JSON payloads below. |
| Wide (high fan-out) | `easyWins` | split by cohesion; adapter over direct use; move knowledge into an interface. |
| Encapsulation leak | member edges crossing a boundary | make the offending member `private` (export already collapses class-scoped private symbols, so its edges vanish) → re-run → verify on the graph. |
| Fat package | Step 4 file counts | split by cohesion (rule of three: 2 similar things can stay; extract at 3+). |

Compile time improves primarily by removing `bad` cycles (restores incremental compilation) and shrinking knots/hubs (smaller ripple). Do NOT suggest moves the graph doesn't support — if a cut needs ownership semantics, read the files the member edges name, but let the graph name them first.

### Step 6 — Verify

Re-run Steps 1–2 after the change: cycles gone or downgraded, hub/knot metrics down, crossing edges gone. A refactoring that can't be verified on the graph is not done. A *new* cycle after a change means stop and diagnose, don't push on.

## JSON payloads (edge bloat)

A hub of shared "common" model types is a double cost: (1) coupling — every change recompiles everything importing it; (2) payload bloat — API edge code serializing the shared types puts every field on the wire, including ones clients never read.

Graph signs: edges from the edge/API package to the shared-model hub, and the same hub imported by many non-edge packages. Confirm at member level (Step 3): which API members reference which hub types; then read the serialization code for verbatim reuse.

Fix: give the edge its own DTOs (small, per-consumer types) instead of reusing the shared model for transport; keep shared types internal-only. Expect a new leaf package on the graph. Duplicating 2–3 fields beats coupling every client to the hub — see pragmatic-architecture (duplication vs wrong abstraction).

## Common mistakes

- `draw` without `-g` → usage error; `-g` is required.
- Looking for a `-f json` **output** → gone. JSON is the *input* (produced by `export`); `draw` renders dot/mermaid, `report` renders its own JSON.
- Feeding semanticdb dirs to `report`/`draw` → they read exactly one deps.json (file or `-`); a directory produces an uncaught `IOException` stack trace. Merge everything in `export` instead.
- `jdeps -verbose:package` → the parser wants `-verbose:class`. The wrong flag fails *silently* (exit 0, no warning) — the graph degrades and cycles can vanish, so don't trust a suspiciously clean report.
- Old env vars `CODESP_JAR`/`CODESP_CACHE_DIR` → renamed to `CODEPS_JAR`/`CODEPS_CACHE_DIR`.
- `draw`/`report` have no `--help` — it is parsed as an input path and errors. Usage errors print the full option signature; the CLI reference is at sake92.github.io/codeps/reference/cli.html.
- `-i` on `report` is optional (no `-i` keeps everything); over-filtering hard-errors on **both** `draw` and `report` ("no nodes remain after filtering").
- `export` warns and skips unparseable files (exit 0); `draw`/`report` hard-error on malformed JSON — fix the input, don't ignore.
- Eyeballing files instead of running the pipeline — member-level cycles are invisible to file reading; the report is one command.

## Red flags — STOP

- "The project is small, I'll just read the files" — reading misses member-level cycles; the graph is reproducible at any size.
- "Compiling takes too long, let's skip the tool" — an existing build is all codeps needs (point at the semanticdb/jdeps output).
- "I'm confident there are no cycles" — verify with `report`; the whole point is replacing beliefs with measurements.
- "The graph is wrong" without evidence — re-run `export`; diff the JSON; don't switch to guessing.
- "I'll fix it without re-running" — unverified refactoring is undone work; close the loop (Step 6).

---
name: scalpel
description: Use when analyzing a codebase's package/module dependency structure to plan refactoring — find dependency cycles ("loops"), reduce compile time, simplify architecture, split oversized packages, or slim JSON payloads bloated by over-shared common types. Triggers: "why is compilation slow", "find dependency cycles", "tightly coupled packages", "should we split this package/module", "refactor the architecture", "API returns fields nobody uses", "analyze dependencies", "reduce coupling". Works on Scala (SemanticDB), Java (jdeps), and any language whose dependency data can be exported as JSON (madge, pydeps, go list).
---

# Scalpel

**Measure the dependency graph before cutting.** codeps ([github.com/sake92/codeps](https://github.com/sake92/codeps)) turns compiler output (or any exported dependency data) into a package-level dependency graph. This skill is the analyze → diagnose → plan → verify workflow on top of it: produce the graph, compute metrics, interpret them, and only then suggest refactorings — every recommendation must cite graph evidence.

## When to use

- Analyzing **why compilation is slow** or a codebase "grew messy" (cycles and hubs are the usual culprits)
- Finding **dependency cycles** between packages/modules
- Deciding **whether to split a package/module** (oversized, over-coupled, or over-depended-on)
- Diagnosing **JSON payload bloat** caused by reusing shared/common types at the API edge
- Any language, as long as package-level dependency data can be produced (see Setup)

When NOT to use: single-file refactors, mechanical moves inside one package, or when the user wants immediate code changes rather than analysis. For symbol-level questions during planning (who uses type X), use `scalex` instead.

## Setup

A bootstrap script handles the codeps jar: `scripts/codeps-cli` (next to this SKILL.md). It downloads the prebuilt jar on first use (cached at `~/.cache/codeps/`) and passes arguments through. Use the absolute path in every command — no shell variables:

```bash
# Java project: run the JDK's own analyzer on compiled classes
jdeps -verbose:package -filter:none -cp classes classes > jdeps.txt
bash "/abs/path/to/skills/scalpel/scripts/codeps-cli" jdeps jdeps.txt -i com.example -f json -o /tmp/graph.json

# Scala project: compile with SemanticDB (sbt/Maven users can point at existing output)
scala-cli compile --server=false --semanticdb -d classes src/
bash "/abs/path/to/skills/scalpel/scripts/codeps-cli" semdb classes/META-INF/semanticdb -i com.example -f json -o /tmp/graph.json
```

If the machine has a newer dev build of codeps (e.g. `~/.deder/out/cli/assembly/out.jar`), set `CODESP_JAR` for that invocation.

### Non-JVM / any language (common JSON input)

codeps reads package-level dependency data as JSON: `{"own": [...], "edges": [{"source","target"}], "stats": {pkg: {"fileCount","classCount"}}}`. Produce it with your ecosystem's dependency tool and feed it to the `json` subcommand (a file, or `-` for stdin):

```bash
# JS/TS (madge), Python (pydeps), Go (go list) — convert to the shape above with jq
madge --json src | jq '...' | bash "/abs/path/to/skills/scalpel/scripts/codeps-cli" json - -i src -f json -o /tmp/graph.json
```

No dependency tool available? Build the JSON by hand from an import scan (`grep -E "^(import|from|use|require)"` per file, group by package). It is less accurate than SemanticDB — say so in the report.

**Both output shapes** (`-f json` display format `{nodes, edges, nodeInfo}` and the raw/common format) are accepted by `analyze-graph.py`.

## Workflow

### Step 1 — Produce the graph

Run codeps with **`-i <root-package>` always** (a package prefix, e.g. `com.example`). Without an include pattern the universe is empty and codeps exits 1 with "no packages remain after filtering". Save output as JSON: `-f json -o graph.json` (prefer writing to `/tmp`, not the repo). `-f raw` emits the common input format after filtering and is useful for round-tripping or re-running experiments without re-parsing.

### Step 2 — Compute metrics

```bash
python3 "/abs/path/to/skills/scalpel/scripts/analyze-graph.py" graph.json
```

Output: total nodes/edges, **cycles** (strongly-connected components with their member edges), per-package table (fan-in, fan-out, files, classes, instability), top fan-in, top fan-out, top size. If a package appears in the table but has no size columns, the input carried no stats (jdeps/hand-built data) — note that in the report.

### Step 3 — Diagnose

Map the metrics to findings. The four signatures below are the ones that matter; everything else in the plan is secondary.

| Graph signature | What it means | Refactoring moves |
|---|---|---|
| **Cycle** (SCC with 2+ nodes, e.g. `a→b→a`) | Compile churn: strongly-connected components can't be incrementally compiled file-by-file — any edit recompiles the whole SCC and every dependent. Often a sign the boundary is drawn wrong. | Extract the shared part into a third package both can depend on; invert the dependency (move the few closing references down); merge the two packages if they're small. Verify with a re-run. |
| **Hub** — high fan-in (many dependents), especially a "common"/"model" package | Change amplification: every edit to the hub recompiles/ripples to all dependents. If the hub holds *shared model types*, see JSON payloads below. | Split the hub by consumer (extract per-consumer types), push stable abstractions down, move widely-used types to a dedicated leaf. |
| **Wide** — high fan-out (depends on many) | Over-coupling: the package touches too much of the system; changes upstream ripple into it. | Split by cohesion; narrow what it imports (adapter over direct use); move its knowledge into an interface. |
| **Fat** — many files/classes (from `stats`/`nodeInfo`) | Long compile units; a single package compiles as one unit and dominates build time. | Split by cohesion (rule of three: 2 similar things can stay; extract at 3+). |

Cycle members and hub fan-in lists are the *evidence* — cite them in the plan ("cycle `core↔util` via 2 edges", "`common` has fan-in 3: app, big, core").

### Step 4 — Plan

Prioritize by impact × effort. Ordering that usually holds: cycles first (compile correctness + speed), then hubs, then wide/fat packages. For each item state: the evidence (edges/nodes), the concrete move, and the expected effect (which edges disappear, which packages stop recompiling together). Do NOT suggest moves that the graph doesn't support — if the data is package-level only, say class-level analysis (scalex) is needed to pin down exact symbols.

### Step 5 — Verify

After refactoring, re-run Steps 1–2 and confirm: cycles gone, fan-in/fan-out down, package sizes reduced. A refactoring that can't be verified on the graph is not done. If a verification re-run shows a *new* cycle, stop and diagnose rather than pushing on.

## JSON payloads (edge bloat)

A hub package of shared "common" model types is a double cost: (1) **coupling** — every change recompiles everything that imports it; (2) **payload bloat** — API edge code that serializes the shared types directly puts every field on the wire, including ones clients never read.

Signs in the graph: an edge from the edge/API package to the shared-model hub, and the same hub imported by many non-edge packages. Confirm the bloat by reading the serialization code (e.g. an endpoint returning a shared entity verbatim).

Fix: give the edge its own DTOs (small, per-consumer types) instead of reusing the shared model for transport; keep the shared types for internal use only. Expect a new leaf package on the graph. Duplication of 2-3 fields between DTO and model is cheaper than coupling every client to the hub — see pragmatic-architecture (duplication vs wrong abstraction).

## Common mistakes

- **No `-i`** → codeps exits 1 "no packages remain after filtering". Always include the root package.
- **Eyeballing** the graph or reading every file instead of running the pipeline — for large codebases this misses cycles entirely. Metrics are cheap; run them.
- **Mixing dialects**: input stats use `fileCount`/`classCount`; display `nodeInfo` uses `files`/`classes`. `analyze-graph.py` accepts both — don't hand-edit between them.
- **Recommending without verifying**: a plan that ends at "suggestions" without a re-run after changes is incomplete — close the loop (Step 5).
- **Ignoring stats**: fat-package findings need `stats`/`nodeInfo`; jdeps and hand-built JSON have none — say so instead of guessing sizes.

## Red flags — STOP

- "The project is small, I'll just read the files" — reading works until it doesn't; the graph gives reproducible numbers at any size.
- "Compiling takes too long, let's skip the tool" — an existing build is all codeps needs (point at `classes/META-INF/semanticdb` or use the JSON path).
- "I'm confident there are no cycles" — verify on the graph; the whole point is replacing beliefs with measurements.
- "The graph is wrong" without evidence — re-run codeps; if still suspicious, diff the raw input, don't switch to guessing.

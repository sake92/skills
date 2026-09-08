---
name: codeps
description: "Analyze a codebase's package or file dependency structure before refactoring: find cycles, change propagators, over-broad or mutable public APIs, dead-code candidates, and structural boundaries; use when planning module/package splits, reducing coupling or incremental-build churn, or comparing architecture health across commits. Do not use for a mechanical local move, symbol lookup, or rename."
---

# Codeps

**Measure the dependency graph before cutting.** Use a dependency report to name the boundary and evidence for a refactoring; inspect the affected code only after the report identifies it. Explain recommendations in terms of specific cycles, findings, graph metrics, and (when available) comparable history.

## Choose the input path

Codeps is language- and build-tool-agnostic. First identify the dependency producer the repository already supports; do not add one merely to run an analysis.

- For Codeps v0.4, read [the Codeps workflow](references/codeps.md).
- For Scala or JVM builds that produce SemanticDB, also read [SemanticDB input](references/semanticdb.md).
- For Java/JVM class dependencies, read [jdeps input](references/jdeps.md).
- For every other language or build tool, read [generic export input](references/export-input.md) and use its native dependency data to emit the Codeps export format.

If no trustworthy producer exists, report that limitation. Do not infer a dependency graph by casually reading source files.

## Analysis loop

1. For commit history, start from a clean, committed worktree; build successfully and generate a fresh report. Incremental builds can retain dependency artifacts for deleted or moved sources, so before using a source-based finding, check that its reported source path exists in the current checkout (except configured generated-source roots). A missing source makes that finding stale evidence: do not make a recommendation from it or reinterpret it as an orphan. Codeps otherwise labels the snapshot with `HEAD`, even when the analyzed inputs came from uncommitted changes. A successful analyzer run after a failed or partial build can also describe stale inputs and is not evidence.
2. Start with package-level structure for architectural decisions. Use file-level detail to locate the responsible implementation or assess incremental-build impact.
3. Triage in order: cycles; high propagators; public surface and mutability; low structural use; then orphans. Read the named code and its consumers before choosing a design move.
4. Make the smallest refactoring supported by the evidence. Prefer extraction, dependency inversion, API narrowing, or state encapsulation over broad moves.
5. Commit the refactoring, rebuild, and regenerate the report. Compare the affected metrics and findings with the prior comparable snapshot; a refactoring is not graph-verified until this loop closes.

## Interpreting evidence

- Health scores summarize a graph directionally. They are not a target, proof of quality, or a substitute for the underlying findings. Retain decimals, and compare package and file scopes independently: either may improve while the other worsens.
- A cycle, its size, internal edges, and external fan-in are the highest-priority structural evidence. Break all relevant edges in a knot, then re-run until the cycle is gone.
- High fan-in/out or propagator score indicates a potential change amplifier. Confirm the consumer groups before splitting a hub.
- Broad public surface, exposed mutability, and low `dependentsPerPublicPort` are investigation prompts, not automatic proof of a bad API. Prefer immutable snapshots and narrow operations to exposed collections or mutable state.
- An orphan is a deletion candidate only after its report input is known current. Check that its reported source still exists at that path (or is a configured generated source), then check executable entry points, runtime loading/reflection, generated code, configuration, and external integration before proposing removal; ask the user before deleting it.
- Keep history comparisons meaningful: use a stable configuration, clean committed inputs, and like-for-like settings. When configuration or filtering changes materially, begin a new baseline instead of comparing unlike snapshots.

## Deliverable

Report the evidence, diagnosis, proposed boundary, and verification criteria. Distinguish measured facts from design judgment and call out tradeoffs or uncertainty. For a change request, make the change only after the analysis names the target, then include before/after evidence.

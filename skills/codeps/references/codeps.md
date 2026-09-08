# Codeps v0.4 workflow

Codeps is a repository-configured dependency-health tracker. One `status` run parses the configured input, analyzes package and (when supplied) file graphs, updates compact history, saves the detailed JSON report, and renders a static HTML dashboard.

Use the bundled runner at `scripts/codeps-cli` with an absolute path. It downloads the pinned v0.4.0 JAR on first use; `CODEPS_JAR` can select a local JAR instead.

## Configure once

Run `codeps status` from the Git repository root. On its first run it creates `.codeps/config.yaml`; edit that stable, committed configuration rather than creating ad-hoc exports. Each project has a `root`, `source` (`semanticdb`, `jdeps`, or `export`), and `inputs`. See the source-specific reference selected by [the main skill](../SKILL.md).

```yaml
projects:
  app:
    root: .
    source: semanticdb
    inputs: [target]
    skip-tests: true
    exclude: [java.**, scala.**]
```

Inputs and analysis settings define the baseline. Preserve them while tracking a history; if they change materially, begin a new history deliberately.

## Generate and inspect a fresh status

For a commit-tracked snapshot, first confirm that the relevant worktree changes are committed, compile successfully, then run:

```bash
bash "/abs/path/to/skills/codeps/scripts/codeps-cli" status --project app
```

Do not trust a report after compilation fails: the source data may be stale or partial even if `status` succeeds.

### Commit identity is not working-tree identity

Without `--commit`, Codeps records `HEAD`; it does **not** detect uncommitted source or build-input changes. Therefore, never record a dirty-tree analysis as ordinary commit history: it would incorrectly associate the current working state with the previous commit.

- For a tracked snapshot, commit first and then run `status`.
- For an exploratory dirty-tree report, use an explicit non-commit label such as `--commit worktree:my-change`, treat the resulting history row as temporary, and do not commit it as a commit snapshot.
- For deterministic CI/replay snapshots, pass the actual `--commit <sha>` and `--generated-at <ISO-8601 instant>` (or set `SOURCE_DATE_EPOCH` when comparing report JSON).

For project `app`, Codeps writes:

| Artifact | Use |
|---|---|
| `.codeps/app.ndjson` | compact, commit-friendly package/file health history |
| `.codeps/out/app/report.json` | complete current evidence and findings |
| `.codeps/out/app/index.html` | interactive static dashboard and trends |

Open or inspect the HTML report for the high-level history and package/file trend. Use the JSON report for complete inventories; dashboard summaries intentionally do not replace individual evidence rows.

## Triage and drill down

Start from `packages` in `.codeps/out/app/report.json`; use `files` to identify the implementation responsible for a package-level issue. `files` is absent for sources without file data, such as jdeps.

Use cached detail views after `status`:

```bash
bash "/abs/path/to/skills/codeps/scripts/codeps-cli" inspect-cycle --project app --id scc:com.example.orders
bash "/abs/path/to/skills/codeps/scripts/codeps-cli" inspect-node --project app --id com.example.orders
bash "/abs/path/to/skills/codeps/scripts/codeps-cli" inspect-node --project app --scope files --id src/com/example/orders/Orders.scala
```

Triage in this order:

1. **Cycles:** inspect `cycles.count`, `nodesInCycles`, `internalEdges`, SCC `members`, and external fan-in. A cycle with large external fan-in has a large blast radius. `witnessCycle` is illustrative; `members` is exhaustive. The CLI does not currently request cut analysis, so reason from the named edges and code rather than expecting automatic cut solutions.
2. **Propagators:** high `fanIn`, `fanOut`, or normalized propagator `score` indicates change amplification. A file split alone will not improve package health if package coupling is unchanged.
3. **Surface and mutability:** inspect `ports`, `mutPorts`, `exposure`, `publicMutableSurface`, and `publicMutableRatio`. Public mutable declarations are a strong hidden coupling channel; encapsulate state behind operations or immutable snapshots.
4. **Structural use:** low `dependentsPerPublicPort` suggests a public API may be broader than necessary. It is a graph proxy, not declaration-level usage proof, so inspect consumers before narrowing it.
5. **Orphans:** verify runtime entry points, reflection, configuration, generated code, and integrations; then ask the user before deleting anything.

The health score and combined dashboard score are directional summaries. Compare decimals and the affected scope, but explain movement through the findings and raw metrics—not the score alone. Removing an orphan or splitting a file can shift ratios without improving the package graph.

## Verify and track change

After the refactoring, commit it, compile, run the same `status` command, and compare the relevant package and file snapshots. If a pre-commit check is necessary, use an explicitly labeled temporary snapshot rather than `HEAD`:

- removed cycle → cycle count/nodes/internal edges decrease and package health normally recovers;
- narrower or encapsulated API → `mutPorts`/`publicMutableSurface` and relevant exposed surface decrease;
- reduced hub → target fan-in/fan-out and propagator evidence decrease;
- deleted unused component → orphan findings change, while overall health may not.

Record the commit, build outcome, package and file health/status, cycle facts, public mutable surface, and relevant findings when replaying history. Treat unexpected scope divergence as a diagnostic finding, not a reason to force either score upward.

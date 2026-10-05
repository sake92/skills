# skills

Skills for hardcore engineering:

- [`pragmatic-architecture/`](./skills/pragmatic-architecture) — practical design principles: small interfaces, locality, clear dependencies, validation, and behavioral testing, balanced against effort, migration risk, and existing project conventions.
- [`codeps/`](./skills/codeps) — measure the dependency graph before refactoring with [Codeps](https://github.com/sake92/codeps): generate package/file health status from SemanticDB, jdeps, or a generic export; diagnose cycles, change propagators, public surface, mutable state, structural use, and orphan candidates; compare health and findings through commits, then verify refactorings on a fresh build and report.
- [`process-hygiene/`](./skills/process-hygiene) — how a process behaves at its boundary (12-factor + clig.dev + structured concurrency): flags vs env vars vs config files with fail-fast validation, file-first secrets, SIGTERM drain-with-deadline shutdown, crash-only design, owning every thread and subprocess so nothing is orphaned, stdout/stderr and structured logs, stateless restarts, and CLI conventions. Includes JVM and shell references.

Reproducible with-skill versus without-skill evaluations live under
[`tests/`](./tests), with current pragmatic-architecture results summarized in
its [`README`](./skills/pragmatic-architecture/README.md).

## Install

Claude Code, Cursor, Codex, OpenCode...

```bash
npx skills add sake92/skills
```

Add `-g` to install globally instead of project-local, `--agent <name>` to target a specific agent if it's not auto-detected.

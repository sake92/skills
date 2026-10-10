# skills

Skills for hardcore engineering:

- [`pragmatic-architecture/`](./skills/pragmatic-architecture) — practical design principles: small interfaces, locality, clear dependencies, validation, and behavioral testing, balanced against effort, migration risk, and existing project conventions.
- [`codeps/`](./skills/codeps) — measure the dependency graph before refactoring with [Codeps](https://github.com/sake92/codeps): generate package/file health status from SemanticDB, jdeps, or a generic export; diagnose cycles, change propagators, public surface, mutable state, structural use, and orphan candidates; compare health and findings through commits, then verify refactorings on a fresh build and report.
- [`process-hygiene/`](./skills/process-hygiene) — startup/shutdown, signals, cancellation, and ownership of tasks, threads, executors, and subprocesses. Includes JVM and shell references.
- [`cli-design/`](./skills/cli-design) — terminal interfaces, flags, help, output contracts, exit codes, interactivity, and CLI configuration conventions.
- [`twelve-factor-app/`](./skills/twelve-factor-app) — deployment configuration, backing services, stateless instances, releases, parity, platform logs, and admin tasks.

The [process-hygiene comparisons](./skills/process-hygiene/README.md) scored 8/8 with skill versus 7/8 without on build-task stdio, 6/7 versus 4/7 on revised worker timeout cleanup, and 6/6 versus 5/6 after cancellation-guidance revisions. These are single-run observations, including adaptive development cases, not a reliability estimate. Thread/executor ownership remains unevaluated. CLI design is unevaluated local guidance. The [Twelve-Factor comparison](./skills/twelve-factor-app/README.md) found no local uplift and configuration gaps in the upstream candidate, so the local deployment skill remains provisional.

Reproducible with-skill versus without-skill evaluations live under
[`tests/`](./tests), with current pragmatic-architecture results summarized in
its [`README`](./skills/pragmatic-architecture/README.md).

## Install

Claude Code, Cursor, Codex, OpenCode...

```bash
npx skills add sake92/skills
```

Add `-g` to install globally instead of project-local, `--agent <name>` to target a specific agent if it's not auto-detected.

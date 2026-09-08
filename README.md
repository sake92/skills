# skills

Skills for hardcore engineering:

- [`pragmatic-architecture/`](./skills/pragmatic-architecture) — opinionated software design/architecture principles (grugbrain.dev + Ousterhout-inspired): minimal API surface & encapsulation, cycle-free modules, hexagonal architecture, newtypes, principle of least power, locality, edge validation, no implicit dependencies, compiler-enforced Java modules (JPMS), integration-first testing.
- [`codeps/`](./skills/codeps) — measure the dependency graph before refactoring with [Codeps](https://github.com/sake92/codeps): generate package/file health status from SemanticDB, jdeps, or a generic export; diagnose cycles, change propagators, public surface, mutable state, structural use, and orphan candidates; compare health and findings through commits, then verify refactorings on a fresh build and report.

## Install

Claude Code, Cursor, Codex, OpenCode...

```bash
npx skills add sake92/skills
```

Add `-g` to install globally instead of project-local, `--agent <name>` to target a specific agent if it's not auto-detected.

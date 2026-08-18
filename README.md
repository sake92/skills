# skills

Skills for hardcore engineering:

- [`pragmatic-architecture/`](./skills/pragmatic-architecture) — opinionated software design/architecture principles (grugbrain.dev + Ousterhout-inspired): minimal API surface & encapsulation, cycle-free modules, hexagonal architecture, newtypes, principle of least power, locality, edge validation, no implicit dependencies.
- [`scalpel/`](./skills/scalpel) — measure the dependency graph before cutting: analyze package/module dependencies with [codeps](https://github.com/sake92/codeps) (SemanticDB, jdeps, or any JSON-exported graph), find cycles/hubs/oversized packages, plan evidence-backed refactorings, and verify them on a re-run.

## Install

Claude Code, Cursor, Codex, OpenCode...

```bash
npx skills add sake92/skills
```

Add `-g` to install globally instead of project-local, `--agent <name>` to target a specific agent if it's not auto-detected.


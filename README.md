# skills

Skills for hardcore engineering:

- [`pragmatic-architecture/`](./skills/pragmatic-architecture) — practical design principles: small interfaces, locality, clear dependencies, validation, and behavioral testing, balanced against effort, migration risk, and existing project conventions.
- [`codeps/`](./skills/codeps) — measure the dependency graph before refactoring with [Codeps](https://github.com/sake92/codeps): generate package/file health status from SemanticDB, jdeps, or a generic export; diagnose cycles, change propagators, public surface, mutable state, structural use, and orphan candidates; compare health and findings through commits, then verify refactorings on a fresh build and report.
- [`process-hygiene/`](./skills/process-hygiene) — startup/shutdown, signals, cancellation, and ownership of tasks, threads, executors, and subprocesses. Includes JVM and shell references.
- [`cli-design/`](./skills/cli-design) — terminal interfaces, flags, help, output contracts, exit codes, interactivity, and CLI configuration conventions.
- [`twelve-factor-app/`](./skills/twelve-factor-app) — deployment configuration, backing services, stateless instances, releases, parity, platform logs, and admin tasks.

## Install

Claude Code, Cursor, Codex, OpenCode...

```bash
npx skills add sake92/skills -g
```

Omit `-g` to install project-local. Use `--agent <name>` to target a specific agent if it's not auto-detected.

## Evaluations

Evaluation tooling lives under [`tests/`](./tests). See individual skill READMEs
for evaluation status, example results, and related skills.

Unless stated otherwise, evaluation examples use Pi 1.0.4 with GPT-6 Luna at low
reasoning, with one candidate per configuration. Reported time covers candidate
execution, excluding grading; tokens count input and output, excluding cached input.

| Skill | Without skill | With skill | Score gain |
| --- | ---: | ---: | ---: |
| [Pragmatic architecture](skills/pragmatic-architecture/README.md) | 26/35 (74.3%) | 35/35 (100%) | +25.7 pp |
| [Process hygiene](skills/process-hygiene/README.md) | 23/29 (79.3%) | 28/29 (96.6%) | +17.2 pp |
| [Twelve-Factor app](skills/twelve-factor-app/README.md) | 7/8 (87.5%) | 8/8 (100%) | +12.5 pp |
| [Codeps](skills/codeps/README.md) | 8/13 (61.5%) | 13/13 (100%) | +38.5 pp |
| CLI design | Unevaluated | — | — |

Scores pool passed checks across the published examples; **pp** means percentage
points. These are selected single-run examples, some developed adaptively, not
overall effectiveness estimates.

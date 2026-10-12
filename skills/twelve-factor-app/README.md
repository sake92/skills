# Twelve-Factor skill evaluations

Protected Scala/MUnit grading; no LLM judge.

| Qualified case | With skill | Without skill |
| --- | ---: | ---: |
| [Runtime bindings](benchmark-results/runtime-bindings.md) | 8/8 | 7/8 |

The first baseline-first case meets the strict score gate with the unchanged skill. The difference is use of the validated configuration during component wiring. This is initial evidence for one task, not reliable effectiveness or coverage of all twelve factors. Backing-service checks tied within that case.

See the [workflow](../../tests/twelve-factor/README.md) and [case evidence and costs](benchmark-results/runtime-bindings.md).

| Factor | Evaluation coverage |
| --- | --- |
| I. Codebase | Untested. |
| II. Dependencies | Untested. |
| III. Config | Six checks: 6/6 with skill, 5/6 without; includes gateway contract checks. |
| IV. Backing services | Two HTTP checks: 2/2 in both configurations. |
| V. Build, release, run | Untested. |
| VI. Processes | Untested. |
| VII. Port binding | Packaged-service behavior untested. |
| VIII. Concurrency | Untested. |
| IX. Disposability | Untested. |
| X. Dev/prod parity | Untested. |
| XI. Logs | Untested. |
| XII. Admin processes | Untested. |

Related guidance reviewed: [citypaul's Twelve-Factor skill](https://github.com/citypaul/.dotfiles/tree/cd4028d57d6e4e95814f7b8ee55ca13c23a9c2f0/claude/.claude/skills/twelve-factor).

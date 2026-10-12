# CLI design maintenance

Terminal interfaces, output contracts, interactivity, and CLI configuration. Guidance is based on [clig.dev](https://clig.dev/).

| Case | Without skill | With skill | Score gain (pp) |
| --- | ---: | ---: | ---: |
| [Inventory JSON export](benchmark-results/json-export.md) | 5/9 | 9/9 | +44.4 |

Selected development output after adaptive tuning, using a retained baseline; stable performance has not been established. The skill now puts native/library parser research and actual integration first, and requires fixing failing regressions before completion. Instruction following remains incomplete in evaluated outputs. This is not whole-skill coverage or reliable effectiveness evidence; fresh held-out confirmation is outstanding.

[Evaluation workflow](../../tests/cli-design/README.md)

Related guidance reviewed: [citypaul's CLI design skill](https://github.com/citypaul/.dotfiles/tree/main/claude/.claude/skills/cli-design).

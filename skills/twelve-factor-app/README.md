# Twelve-Factor adoption evaluation

One matched run per case/arm, GPT-6 Luna with low reasoning, Pi 1.0.4. Protected behavioral grading; no LLM judge.

| Case | Local | Its baseline | Citypaul | Its baseline |
| --- | ---: | ---: | ---: | ---: |
| Configuration drift | 3/6 | 3/6 | 4/6 | 4/6 |
| Replica sessions/logs | 5/5 | 5/5 | 0/5 | 5/5 |
| Total checks | 8/11 | 8/11 | 4/11 | 9/11 |

Keep the local skill provisional. It showed no quality uplift. Citypaul did not meet the adoption gate, so it is not recommended as a replacement based on this run. Its session candidate made no edits; the 0/5 is an incomplete task, not proof that its guidance is incorrect.

See [analysis and retained evidence](benchmark-results/comparison.md), the [fixtures and workflow](../../tests/twelve-factor/README.md), and the upstream [skill](https://github.com/citypaul/.dotfiles/tree/cd4028d57d6e4e95814f7b8ee55ca13c23a9c2f0/claude/.claude/skills/twelve-factor).

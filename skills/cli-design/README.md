# CLI design maintenance

Extracted from process-hygiene with its CLI and configuration references. Guidance is based on [clig.dev](https://clig.dev/); this local revision has not yet been evaluated.

An upstream candidate worth comparing is [citypaul's cli-design](https://github.com/citypaul/.dotfiles/tree/main/claude/.claude/skills/cli-design). Its [published coverage](https://github.com/citypaul/.dotfiles/blob/main/evals/skills/COVERAGE.md) reports baseline, normal selection, and forced-loading runs. Those results concern its fixture/model/bundle and do not validate this local skill.

A future matched comparison should exercise redirected structured output, actionable failures and exit codes, non-interactive input, configuration precedence, compatibility, and a no-change control. Keep executable protected checks outside candidate workspaces.

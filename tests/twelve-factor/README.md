# Twelve-Factor adoption check

Two Scala fixtures compare the local extracted guidance, a pinned citypaul skill, and no skill with the same model, prompt, starter, permissions, and protected tests. These are focused service modules, not production deployments or a complete Twelve-Factor certification.

The upstream snapshot is citypaul/.dotfiles at `cd4028d57d6e4e95814f7b8ee55ca13c23a9c2f0`, skill `claude/.claude/skills/twelve-factor/`, downloaded with its Node reference into ignored `tests/tmp/upstream/skills/twelve-factor/`. It is evaluated unchanged, including its language-specific reference. Its guidance must transfer to Scala without adding companion skills.

Starters begin with passing tests. Protected grading is outside candidate workspaces. The configuration case tests deployment-independent binding, missing/malformed settings, secret-safe errors, validated policy input, and operator examples. The sessions case tests replacement replicas, revocation, arbitrary store implementations, store failure, and safe correlated structured events. Lifecycle, complete dev/prod service equivalence, and all twelve factors are not covered.

Run a single upstream smoke before comparison:

```bash
scala tests/run-eval.scala --server=false -- output \
  --skill-path tests/tmp/upstream/skills/twelve-factor \
  --eval-path tests/tmp/upstream/evals.json --fixture-path tests/twelve-factor \
  --case config-drift --configuration with-skill --runs 1 --skip-judge
```

The upstream eval JSON duplicates the local set while changing only `skill_name` to `twelve-factor`, matching the unchanged upstream metadata. Run both cases without `--case` and without `--configuration` for the upstream/no-skill comparison. For the local comparison use `--skill-path skills/twelve-factor-app` and its own eval JSON. Runs retain patches, transcripts, timing, grading, and benchmark artifacts under `tests/tmp/`.

Adoption requires the upstream candidate to satisfy every protected criterion and preserve the visible tests, with patch review confirming no scope regression. A tie with baseline shows adequacy on these cases, not benefit from the skill; incomplete coverage remains explicit. Remove local deployment guidance only after that evidence is available.

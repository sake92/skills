# Run Twelve-Factor evaluations

The active suite is a small Scala service adaptation, not a complete production checkout or Twelve-Factor certification. The [factor analysis](../../skills/twelve-factor-app/analysis.md) explains all twelve factors and distinguishes executable coverage from audit evidence.

## Qualify a case

The current active case is `runtime-bindings`, targeting configuration (III) and attached HTTP resources (IV). Its two visible tests start green. Protected MUnit checks remain outside candidate workspaces. The defective starter scores 1/8 and the hand-written reference 8/8; neither is the plain-model baseline.

Run the plain-model baseline first:

```bash
scala tests/run-eval.scala --server=false -- output \
  --skill-path skills/twelve-factor-app --fixture-path tests/twelve-factor \
  --case runtime-bindings --configuration without-skill \
  --runs 1 --skip-judge --parallelism 1
```

Inspect the patch and grading. If any observed baseline scores 100%, the case fails qualification: develop a harder realistic task and run a fresh baseline before proceeding. Preserve that rejected attempt as development history.

Once the baseline has meaningful failures, freeze the prompt, starter, and protected grader. Run a single with-skill smoke:

```bash
scala tests/run-eval.scala --server=false -- output \
  --skill-path skills/twelve-factor-app --fixture-path tests/twelve-factor \
  --case runtime-bindings --configuration with-skill \
  --runs 1 --skip-judge --parallelism 1
```

With-skill must score strictly higher than no-skill while preserving existing behavior; the target is 100%. Ties and regressions fail acceptance. Keep model, reasoning, tools, permissions, and budget matched. If a prompt, starter, or grader changes, qualify the revised case with a fresh baseline rather than comparing against an older task.

## Inspect evidence

Runs retain transcripts, candidate patches, grading, timing, and skill snapshots under ignored `tests/tmp/twelve-factor-app-workspace/`. Generate the static review viewer with the skill-creator's `eval-viewer/generate_review.py`, as described in [the runner guide](../README.md). Commit summaries and small code/diff excerpts only.

The [first runtime-bindings pair](../../skills/twelve-factor-app/benchmark-results/runtime-bindings.md) scored 8/8 with skill against 7/8 without. The unchanged-skill smoke was reused as the paired candidate after verifying identical starter trees and task metadata. This single pair is initial evidence, not a stable estimate. Fresh held-out confirmation remains outstanding.

## Rerun historical cases

The earlier `config-drift` and `replica-sessions` cases failed the current uplift gate and are excluded from the active eval set. Their fixtures and [historical comparison](../../skills/twelve-factor-app/benchmark-results/comparison.md) remain available.

Select their definitions explicitly, for example:

```bash
scala tests/run-eval.scala --server=false -- output \
  --skill-path skills/twelve-factor-app \
  --eval-path skills/twelve-factor-app/evals/historical-evals.json \
  --fixture-path tests/twelve-factor --case config-drift \
  --configuration without-skill --runs 1 --skip-judge --parallelism 1
```

The older upstream comparison used citypaul/.dotfiles at `cd4028d57d6e4e95814f7b8ee55ca13c23a9c2f0`, skill `claude/.claude/skills/twelve-factor/`, with its Node reference unchanged. Its ignored upstream eval file contained only those two historical cases and changed `skill_name` to `twelve-factor`. It is not evidence for the new case or for Scala transfer across all twelve factors.

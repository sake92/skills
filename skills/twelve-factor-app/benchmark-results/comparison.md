# Local versus upstream Twelve-Factor guidance

## Method

The local split was evaluated against citypaul/.dotfiles at `cd4028d57d6e4e95814f7b8ee55ca13c23a9c2f0`. The external SKILL.md and its Node reference were loaded unchanged. No companion skills were supplied. Both comparisons used the same two Scala starters, prompts, GPT-6 Luna/low model settings, permissions, and protected MUnit grading. Separate no-skill arms were run for each comparison, so their different configuration scores are sampling variation, not different baselines by design.

Every starter passed its visible two-test suite. The initial protected graders rejected all intended defects (0/6 and 0/5); independent reference implementations passed every check (6/6 and 5/5). These proofs are retained under `tests/tmp/twelve-factor-grader-proof/`. Configuration grades cover endpoint binding, required settings, numeric ranges, URL validity/redaction, consistent policy input, and operator examples. Sessions grades cover replica replacement, revocation, the backing-service interface, store failure, and JSON event correlation/redaction. The fixture's platform collector explicitly requires JSON records.

The upstream smoke preceded comparisons and scored 4/6. Its starter was formatted before the paired runs; all paired arms received identical formatted starters. The smoke is a pipeline check and additional configuration failure evidence, not a matched comparison.

## Findings

- Upstream configuration scored 4/6 in the smoke and comparison. It accepted a port above 65535 and a malformed database URL. Its paired no-skill candidate had the same score.
- Local configuration scored 3/6, equal to its baseline. It treated the platform's PORT and SESSION_TTL_MINUTES as optional, accepted an out-of-range port, and did not validate URL shape. The request requires missing or unusable platform settings to fail. The local guidance is therefore not demonstrated to deliver the complete task.
- Upstream sessions scored 0/5 with an empty patch. The transcript shows the skill was read and the project inspected, then a normal final response promising work. This is task noncompletion, not an exception, timeout, or proof of a flaw in Twelve-Factor principles. It is retained as observed rather than silently rerun or excluded.
- Local sessions and both no-skill sessions candidates scored 5/5. This case did not discriminate completed implementations. Inspection confirms use of the supplied store and redacted correlated events rather than unrelated production-storage replacements.

The adoption gate required all protected checks, compatible visible behavior, and a focused patch. Upstream did not meet it; the local extracted guidance also remains provisional. Do not interpret the totals as stable reliability or a general ranking: this is one run per case/arm on two small Scala modules. It does not evaluate production service parity, signals/shutdown, CLI design, thread/task ownership, or all twelve factors. Published upstream TypeScript/bundle results do not establish Scala transfer.

## Recorded cost

| Comparison arm | Candidate tokens | Sum of candidate times |
| --- | ---: | ---: |
| Local | 25,983 | 143.2 s |
| Local baseline | 30,015 | 125.8 s |
| Citypaul | 28,754 | 80.1 s |
| Citypaul baseline | 32,237 | 163.1 s |

The short upstream time includes the incomplete session task and is not an efficiency advantage. These totals use the runner's input/output token metric and exclude cached-input tokens; timing excludes grader work and is the sum of concurrent candidate durations, not wall-clock elapsed time. Do not pool it with published upstream measurements.

## Evidence

Recorded grades are summarized in the [skill README](../README.md). Raw candidate patches, generated grade reports, and benchmark JSON are archived under ignored `tests/tmp/historical-twelve-factor-evidence/` in the evaluation worktree. They are not committed report artifacts.

Full generated workspaces remain ignored and retained:

- `tests/tmp/twelve-factor-workspace/iteration-20261008-220656/`: upstream smoke.
- `tests/tmp/twelve-factor-workspace/iteration-20261008-220905/`: upstream comparison, evaluated skill snapshot, transcripts, grading logs, patches, timings, and review.html.
- `tests/tmp/twelve-factor-app-workspace/iteration-20261008-220905/`: local comparison and equivalent artifacts.

The repository commit recorded by the runner is `a2649ce57a3ca98ce306bb3a2099c59d5e7b7e95`; the split and fixture additions were uncommitted. The evaluated local skill is retained in its iteration's evaluated-skill directory. Mainargs defaults and the original architecture suite remain available; new skill-path, eval-path, and fixture-path arguments select this comparison without changing them.

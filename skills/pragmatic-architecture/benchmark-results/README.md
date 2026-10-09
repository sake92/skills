# Benchmark results

These pages summarize example runs of the pragmatic-architecture skill: code excerpts, small diffs, individual grades, and cases where it made no measured difference. Evaluated agents receive `SKILL.md`; these pages are for readers and maintainers.

Each row lists **with skill / without skill**. Open a case to see the code and the criteria behind its score.

| Fixture | Grade | What the code shows |
| --- | ---: | --- |
| [customer-repository](customer-repository.md) | 10/10 / 7/10 | Skill hides mutable state and removes unused collection helpers. |
| [repository-with-callers](repository-with-callers.md) | 8/8 / 5/8 | Skill narrows the API while preserving the export caller’s snapshot. |
| [legacy-discount-fix](legacy-discount-fix.md) | 8/8 / 8/8 | Identical patches; no measured improvement. |
| [booking-overlap-rule](booking-overlap-rule.md) | 12/12 / 12/12 | Both centralize the rule and inject the service; tied grades. |
| [teacher-course-ids](teacher-course-ids.md) | 8/8 / 6/8 | Only the skill candidate adds compiler-safe IDs through lookup and cache. |
| [public-profile-dto](public-profile-dto.md) | 9/9 / 8/9 | Both introduce DTOs; only the skill candidate tests private-field exclusion. |

## Reading the results

All six comparisons used Pi 1.0.4 with GPT-6 Luna at low reasoning, once per configuration. Luna also judged narrowly scoped qualitative criteria; protected MUnit/Scalameta checks verified behavior and structural properties. Customer ran on 2026-10-07; the other comparisons ran on 2026-10-08. These are small fixtures adapted from application concerns, not measurements across entire production repositories.

Repository cases show narrower public APIs without losing caller-required behavior. The ID case shows a distinct design decision: opaque IDs prevent the demonstrated mix-up at compile time. Both DTO candidates chose a public projection; the skill's advantage there was leak-detecting regression coverage. Legacy and booking ties remain visible rather than being excluded from the results.

The ID result followed adaptive skill tuning on the evaluated case. It needs repeated or held-out runs before being treated as reliable general improvement. The DTO run used the revised model-boundary guidance before the final upfront ID decision paragraph; its DTO instructions were unchanged. The other four results predate those revisions and were not rerun. No aggregate represents all six cases under one unchanged skill version.

The summaries and selected code excerpts describe the recorded runs; they do not guarantee identical results on rerun. Source run identifiers refer to local generated workspaces. Timing and token differences from one pair can reflect cache state, load, or sampling; they are not stable efficiency estimates.

The final ID/DTO comparison reused completed smoke candidates and verified matching starter trees and task metadata within each pair. Five candidate runs cost 78,401 tokens, including an unsuccessful intermediate ID smoke; the four reported comparison candidates cost 61,773. Existing full review artifacts remain under `tests/tmp/`, which is not committed.

The earlier legacy order case was removed after identical passing patches. The current six-case set retains its ties.

See the [skill overview](../README.md), [coverage analysis](../../../analysis.md), and [evaluation workflow](../../../tests/README.md).

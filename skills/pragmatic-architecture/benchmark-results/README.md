# Benchmark results

These pages summarize example runs of the pragmatic-architecture skill: code excerpts, small diffs, and individual grades. Evaluated agents receive `SKILL.md`; these pages are for readers and maintainers.

Each row lists **with skill / without skill**. Open a case to see the code and the criteria behind its score.

| Fixture | Grade | What the code shows |
| --- | ---: | --- |
| [customer-repository](customer-repository.md) | 10/10 / 7/10 | Skill hides mutable state and removes unused collection helpers. |
| [repository-with-callers](repository-with-callers.md) | 8/8 / 5/8 | Skill narrows the API while preserving the export caller’s snapshot. |
| [teacher-course-ids](teacher-course-ids.md) | 8/8 / 6/8 | Only the skill candidate adds compiler-safe IDs through lookup and cache. |
| [public-profile-dto](public-profile-dto.md) | 9/9 / 8/9 | Both introduce DTOs; only the skill candidate tests private-field exclusion. |

## Reading the results

The executor model also judged narrowly scoped qualitative criteria; protected MUnit/Scalameta checks verified behavior and structural properties. These are small fixtures adapted from application concerns, not measurements across entire production repositories.

Repository cases show narrower public APIs without losing caller-required behavior. The ID case shows a distinct design decision: opaque IDs prevent the demonstrated mix-up at compile time. Both DTO candidates chose a public projection; the skill's advantage there was leak-detecting regression coverage.

The ID result followed adaptive skill tuning on the evaluated case and needs held-out confirmation. Skill versions differ across cases; these results are not a frozen-suite effectiveness estimate.

The summaries and selected code excerpts describe the recorded runs; they do not guarantee identical results on rerun. Timing and token differences from one pair can reflect cache state, load, or sampling; they are not stable efficiency estimates.

See the [skill overview](../README.md) and [evaluation workflow](../../../tests/README.md).

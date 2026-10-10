# Runtime bindings: first qualified case

One baseline-first pair on 2026-10-10, Pi 1.0.4, GPT-6 Luna/low. The Twelve-Factor skill was unchanged. No qualitative judge was used.

| Configuration | Protected checks | Candidate tests | Candidate tokens | Candidate time |
| --- | ---: | ---: | ---: | ---: |
| Without skill | 7/8 | 3 passing | 16,671 | 65.4 s |
| With skill | 8/8 | 4 passing | 25,661 | 105.9 s |

The case meets the acceptance gate: the observed no-skill grade is below 100%, and with-skill scores strictly higher and reaches 100%. This is one development pair, not a held-out estimate or a guarantee that future baseline runs cannot pass everything. Any subsequently observed perfect baseline invalidates qualification under the repository gate.

## What differed

Both candidates removed the deployment-name endpoint switch, validated the README settings, preserved the HTTP endpoint and credential behavior, and added tests. The remaining difference was the configuration handoff:

```scala
// Without skill: a second source can override the validated setting.
private val maxSlots = environment.get("MAX_SLOTS").flatMap(_.toIntOption).getOrElse(config.maxSlots)

// With skill: the supplied configuration remains authoritative.
private val maxSlots = config.maxSlots
```

The protected scenario loads configuration with a two-slot limit, changes the raw environment to a one-slot limit, then wires components using the original configuration. The baseline returns one slot; the skill candidate returns two. Snapshotting the raw map at client construction fixes later mutations but leaves two competing configuration sources during composition.

This relates to the unchanged skill's instruction to build typed configuration once and inject it rather than reading the environment inside the application. It is an application configuration-boundary criterion beyond the original methodology's explicit environment-storage requirement. The result does not establish that every possible Twelve-Factor implementation must have a typed config object.

## Per-factor evidence

| Factor | With skill | Without skill | Interpretation |
| --- | ---: | ---: | --- |
| III: configuration and its documented application contract | 6/6 | 5/6 | One additional configuration-handoff check passed. |
| IV: attached HTTP backing service | 2/2 | 2/2 | Supporting coverage; no demonstrated skill benefit on these checks. |

Required settings, optional setting ranges, useful credential-safe diagnostics, and early failure are documented gateway requirements. They are not twelve-factor certification rules. Port binding and process cleanup support the fixture and grader; they do not count as independent coverage of factors VII, VIII, or IX. The other ten factors are still outstanding for the new suite.

## Validation and limits

The original two visible tests pass. The defective starter scores 1/8; a hand-written corrected reference scores 8/8. The reference is a grader proof, not a model baseline. Its implementation stays outside the candidate workspace.

Protected MUnit tests start real local HTTP endpoints and launch fresh gateway JVMs from the same compiled classpath with different environment bindings. They preserve endpoint paths, query strings, and bearer authentication, check failure behavior, and ensure invalid settings prevent service startup. Child processes and servers are closed with bounded waits. The fixture uses no database substitute and does not evaluate PostgreSQL semantics, Docker packaging, release rollback, or organizational dev/prod parity.

Starter trees, task metadata, skill text, model, reasoning, tools, and permissions were matched. The prompt, fixture, and grader were frozen before either model run. Only the starter was supplied to candidates; protected checks were added during grading. The with-skill trace confirms a read of SKILL.md. Patch review found focused config/client/test/documentation changes and preserved visible behavior.

Candidate token count excludes cached input under the existing runner metric. With skill consumed 8,990 more recorded tokens and 40.5 s more candidate time. These one-pair differences are not stable efficiency estimates. Grading took 14.3 s without skill and 12.9 s with skill. Both candidates wrote operator examples with doubled shell line-continuation backslashes; those examples need human correction and are outside the scored behavior. The with-skill implementation also trims input strings before validating them; untested edge cases remain.

## Retained local evidence

- Baseline: `tests/tmp/twelve-factor-app-workspace/iteration-20261010-164620/`.
- With-skill smoke: `tests/tmp/twelve-factor-app-workspace/iteration-20261010-164807/`.
- Combined pair and static viewer: `tests/tmp/twelve-factor-app-workspace/runtime-bindings-pair-20261010/`.
- Grader proof: `tests/tmp/runtime-bindings-grader-proof/`.

The combined pair reuses the two completed candidates; it is not a third run. Its provenance records the equal starter tree `b070cd7b818b1fbe81b1e1db435de979cf4b1293`. The repository base was `d11c0dfed3a4fb2467d62930ea51d2611bd0a6de`; fixture and maintainer changes were uncommitted. Full transcripts, patches, grades, timings, and snapshots stay under ignored `tests/tmp/`.

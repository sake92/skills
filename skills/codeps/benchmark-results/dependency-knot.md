# Layered dependency knot

Codeps 0.6.0 rerun, 2026-10-14. Pi 1.0.4, GPT-6 Luna at low reasoning,
one completed matched pair, five-minute candidate limit, identical tools and
permissions, protected Scala/MUnit grading, no LLM judge.

| Configuration | Protected checks | Candidate time | Candidate tokens |
| --- | ---: | ---: | ---: |
| With skill | 7/7 | 201.8 s | 53,870 |
| Without skill | 6/7 | 226.1 s | 57,209 |

Score gain: **+14.3 percentage points** (85.7% → 100%). Candidate tokens fell
3,339 (5.8%) in this completed pair.

## Task and fixture

The [customer directory](../../../tests/codeps/dependency-knot/starter/) has 12
production files across API, services, repositories, DAO, jobs and application
wiring. Its two visible tests pass before the task. HTTP types and policy create
a four-package knot, while the file graph is acyclic. The task requests a focused
boundary correction with preserved controller/job behavior and dependency
verification, without naming the implementation.

## Observed results

Both candidates introduced a domain customer contract, eliminated the package
cycle, preserved checked behavior, and saved a final report agreeing with an
independent clean build. The baseline removed its health history during cleanup:

```shell
rm -f .codeps/app.ndjson
```

That discarded the required before/after evidence. With skill retained the
history and used bounded package/file findings followed by cycle inspection:

```shell
codeps findings --project app --limit 10 --format json
codeps findings --project app --scope files --limit 10 --format json
codeps inspect-cycle --project app --id scc:app.api --format json
```

Its final evidence reports no package or file cycles. API package fan-in fell
from 5 to 1; the domain contract has fan-in 6.

## Grading

| Protected criterion | With skill | Without skill |
| --- | --- | --- |
| Directory ordering, normalization, lookup and missing-customer behavior | Pass | Pass |
| Creation validation, duplicate rejection, output and later lookup | Pass | Pass |
| Ordered CRM partial results and shared directory state | Pass | Pass |
| CSV and reconciliation contracts | Pass | Pass |
| Independently rebuilt package/file graphs are acyclic | Pass | Pass |
| Submitted final report matches the protected fresh graph | Pass | Pass |
| Before/after scope and analyzed-state labeling | Pass | Fail |

## Limits

The plain-model baseline was run and inspected before the with-skill smoke.
Starter trees, prompts, graders, and skill were verified unchanged. An initial
baseline and initial with-skill attempt timed out at 300 seconds, each with a
roughly 184-second gap between assistant responses; a sandboxed retry failed
on Pi credential/settings locks. These invalid attempts remain in ignored local
notes and are excluded. Completed runs used matching permissions.

The skill was previously tuned on this fixture. One completed pair after
invalid attempts is selected development evidence, not held-out confirmation or
a stable quality, token-efficiency, or latency estimate. Candidate tokens count
input and output, excluding cached input; candidate time excludes grading.
The earlier published example scored 7/7 with skill versus 5/7 without.

See the [workflow and grader validation](../../../tests/codeps/README.md).

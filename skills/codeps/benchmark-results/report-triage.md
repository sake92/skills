# Stale report and runtime plugins

Codeps 0.6.0 rerun, 2026-10-14. Pi 1.0.4, GPT-6 Luna at low reasoning,
one completed matched pair, five-minute candidate limit, identical tools and
permissions, protected Scala/MUnit grading, no LLM judge.

| Configuration | Protected checks | Candidate time | Candidate tokens |
| --- | ---: | ---: | ---: |
| With skill | 5/6 | 37.7 s | 22,196 |
| Without skill | 3/6 | 227.8 s | 18,303 |

Score gain: **+33.3 percentage points** (50% → 83.3%). Candidate tokens rose
3,893 (21.3%). The with-skill result remains below the 100% target.

## Task and fixture

The [export service](../../../tests/codeps/report-triage/starter/) has HTTP and
nightly consumers, layered storage, and two configured runtime exporters. Its
two visible tests pass. A supplied CI report comes from an earlier fixture
revision: it contains a retired package/file cycle and flags both exporters as
orphans. The retired sources are absent; the exporters are still loaded through
`conf/exporters.properties` and `Class.forName`.

The task requests an evidence-backed maintenance review in the tracker's JSON
format, current cycle counts, and no production edits.

## Observed results

Both candidates kept the runtime exporters and preserved production sources.
Baseline retained the old cycle counts and recommended investigating the absent
retired sources. With skill marked the stale cycle for a report refresh:

| Review field | Without skill | With skill |
| --- | --- | --- |
| `packageCycles` | 1 | 0 |
| `fileCycles` | 1 | 0 |
| Retired-source recommendation | `investigate` | `refresh` |

The zero-cycle counts match the protected clean graph, but the with-skill agent
inferred them from source inspection. It did not compile fresh SemanticDB, run
`codeps status` or `findings`, or save a current report. The fresh-report check
fails despite the review's improved diagnosis. Both candidates ran the supplied
consumer tests successfully.

## Grading

| Protected criterion | With skill | Without skill |
| --- | --- | --- |
| Missing retired sources are distinguished from live refactoring targets | Pass | Fail |
| Configured CSV/audit adapters are kept with runtime-loading evidence | Pass | Pass |
| Current package/file cycle counts match a protected clean build | Pass | Fail |
| Saved current report matches the protected application graph | Fail | Fail |
| Production sources and deployment configuration remain unchanged | Pass | Pass |
| HTTP and nightly consumers still load configured adapters | Pass | Pass |

The separating checks concern evidence freshness and review classification;
they are not independent demonstrations of broad architectural capability.
Matching cycle counts do not prove that the candidate measured the current graph.

## Limits

The qualified baseline was run and inspected before the with-skill smoke.
Starter trees, prompts, graders, and skill were verified unchanged. A completed
sandboxed diagnostic baseline scored 2/6 and is excluded from the matched pair;
both selected candidates use the same permissions. The with-skill candidate
completed in a batch invalidated by the other case's timeout, then received all
six existing protected checks separately. These attempts remain in ignored local
notes. No candidate work, prompt, or grader was changed during recovery.

The skill was previously tuned on this fixture. This is one completed pair,
not held-out confirmation. Timing includes model-response delays and does not
establish a speed advantage. Tokens count candidate input/output, excluding
cached input; times exclude grading. This fixture does not evaluate Code Maat,
real deployment integrations, or arbitrary report interpretation.
The earlier published example scored 6/6 with skill versus 3/6 without.

See the [workflow and grader validation](../../../tests/codeps/README.md).

# Stale report and runtime plugins

| Configuration | Protected checks | Candidate time | Candidate tokens |
| --- | ---: | ---: | ---: |
| With skill | 6/6 | 59.7 s | 28,378 |
| Without skill | 3/6 | 38.8 s | 16,438 |

Score gain: **+50.0 percentage points** (50% → 100%).

## Task and fixture

The [export service](../../../tests/codeps/report-triage/starter/) has HTTP and
nightly consumers, layered storage, and two configured runtime exporters. Its
two visible tests pass. A supplied CI report was generated from an earlier
fixture revision: it contains a retired package/file cycle and flags both
exporters as orphans. The retired sources are absent; the exporters are still
loaded through `conf/exporters.properties` and `Class.forName`.

The task requests an evidence-backed maintenance review in the tracker's JSON
format, current cycle counts, and no production edits.

## What differed

Both candidates keep the runtime exporters and preserve production sources.
Baseline copies the cached cycle counts and treats the retired package as a live
investigation target:

```json
{
  "packageCycles": 1,
  "fileCycles": 1,
  "recommendations": [{
    "subject": "app.retired.api / app.retired.dao / app.retired.repositories",
    "action": "investigate"
  }]
}
```

With-skill verifies the missing paths, compiles current sources and regenerates
the configured Codeps report. Its review records zero current cycles and marks
the retired finding for a report refresh:

```json
{
  "packageCycles": 0,
  "fileCycles": 0,
  "recommendations": [{
    "subject": "app.retired.api / app.retired.repositories / app.retired.dao (LegacyEndpoint.scala, LegacyRepository.scala, LegacyDao.scala)",
    "action": "refresh"
  }]
}
```

These excerpts omit other recommendations, evidence arrays and the summary.
Runtime-loading recognition did not separate the candidates; freshness did.

## Grading

| Protected criterion | With skill | Without skill |
| --- | --- | --- |
| Missing retired sources are distinguished from live refactoring targets | Pass | Fail |
| Configured CSV/audit adapters are kept with runtime-loading evidence | Pass | Pass |
| Current package/file cycle counts match a protected clean build | Pass | Fail |
| Saved current report matches the protected application graph | Pass | Fail |
| Production sources and deployment configuration remain unchanged | Pass | Pass |
| HTTP and nightly consumers still load configured adapters | Pass | Pass |

The three separating checks concern one broader evidence-freshness problem;
they do not demonstrate three independent capabilities.

## Limits and cost

The final with-skill smoke was paired with the qualified baseline after checking
identical starter trees and task metadata. The skill was tuned on frozen cases;
generalization has not been tested. With-skill used 11,940 more
candidate tokens and took 20.8 s longer. This small fixture does not evaluate
Code Maat, real deployment integrations, or arbitrary report interpretation.

See the [workflow and grader validation](../../../tests/codeps/README.md).

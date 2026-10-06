Judge only the two qualitative expectations below. Protected MUnit tests
separately grade behavior and candidate regression coverage; do not duplicate or
override them.

- `Reservation invariants are protected by the shared operation rather than only one adapter.`
- `The fix is proportional and preserves existing contracts without unrelated layers or dependencies.`

Return one result for each expectation, preserving its text exactly. Base every
verdict on the submitted source. A pass needs concrete evidence. The output JSON
schema is supplied separately by the runner.

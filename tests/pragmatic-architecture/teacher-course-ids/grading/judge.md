Judge only these qualitative expectations. Protected tests separately grade
lookup behavior, cache isolation, caller compatibility, and compiler rejection
of swapped and raw identifiers at the service and repository APIs. Type safety
is architectural added value; it is not an explicit task requirement.

- `Distinct user and course types protect the lookup and cache without an unrelated identifier migration.`
- `The submission adds focused MUnit regression coverage with distinct user and course UUIDs.`

This FlowRun-inspired application has no existing identifier wrappers or opaque
types. TeacherRecord stores UUIDs. TeacherService and its
repository initially accept two UUIDs, and the service accidentally reverses
the repository arguments. Its cache is keyed by (UUID, UUID). HTTP and roster
callers use UUID wire/persistence representations. Starter tests use the same
UUID for user and course and therefore pass despite the bug.

Assess whether the agent adds distinct user/course types throughout the affected
lookup path and its private cache key, with conversion at UUID boundaries. The
demonstrated swapped-argument bug provides a concrete benefit for those types.
Opaque types are a low-cost Scala option; equivalent nominal wrappers can also
earn the criterion. Plain UUID aliases or unused declarations do not. Internal
constructor and method signatures may change. Do not require wrapping every UUID
in the application, a new dependency, generic ID framework, or new layers.
This is a sequential cache fixture; concurrency and TTL redesign are unrelated.

For coverage, require an executable assertion on a lookup using different user
and course UUIDs that would fail with the original reversal. A reverse-pair
fixture is useful but not mandatory. Check the result, not merely test names.

Return one result per expectation with its exact text and concrete source evidence.

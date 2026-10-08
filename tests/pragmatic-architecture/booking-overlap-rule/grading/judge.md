Judge only the qualitative expectations below. Protected tests separately grade
the public business operation and both entry points, including failure state,
and construction of business collaborators inside adapters.
Do not duplicate or override those tests.

- `One coherent booking creation operation owns the shared overlap rule, and HTTP and import use it without duplicating the business policy.`
- `The booking fix remains proportional and does not introduce unrelated layers, wrappers, or dependencies.`
- `The submission adds focused MUnit regression coverage for overlap rejection through both HTTP and import.`

The starter has an in-memory repository, BookingService.create, an HTTP
controller, a NightlyImport that writes directly to the repository, and
BookingApplication that wires the collaborators together.
BookingApplication.history is the application-facing read-only history report;
internal repository getter names are not part of the protected behavior contract.
Initially the controller only rejects requests whose start falls inside an
existing active booking. The service and import reject invalid intervals and
duplicate IDs but do not reject overlaps. Existing tests are green because they
exercise disjoint creation, invalid intervals, and cancellation reporting.

Assess the completed source for ownership of the rule, not a preferred helper
name or exact algorithm. A shared service, repository operation, or suitable
existing collaborator can own creation if it enforces the invariant and both
entry points consistently use it. Merely sharing a predicate while keeping
independent check-and-write policies in adapters is insufficient. Transport
error mapping can remain in the controller; that is not business-rule duplication.
Business collaborators should be supplied to adapters by the application's
wiring rather than constructed inside adapters. The application is internal;
constructor changes and corresponding updates to wiring and tests are allowed.
No new interface or injection framework is required.
Preserving the existing reporting API and using private contained mutation are
reasonable. Do not demand an unrelated concurrency mechanism: this fixture is a
sequential application and the task does not introduce concurrent execution.

For coverage, require executable assertions showing conflicting requests are
rejected by both adapters; shared test helpers are fine. Comments and only
successful/disjoint cases are insufficient.

Return one result per expectation with its exact text and concrete source
evidence. The runner supplies the JSON schema.

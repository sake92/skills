# Testing: integration-first by feature

The default confidence test exercises one feature through its public edge with real wiring and, where practical, its production database in a container. Use unit tests for pure business rules, parsers, and algorithms. Test behavior, not implementation details.

| Feature area | Preferred test |
|---|---|
| `domain` pure logic | Unit or property test with real values; no mocks. |
| `db` | Integration test against the production database engine and real migrations. |
| `http` | Composed integration test: real HTTP request, feature wiring, edge validation, serialization, and database. |
| External HTTP, payment, email, SMS | A hand-written fake or provider test server when real calls are costly or unsafe. Assert the observable request or outcome. |

An in-memory substitute for a production database is not an integration test: SQL dialects, transactions, collations, and locking differ. Prefer Testcontainers or the project's equivalent.

Avoid mock-heavy controller/service slices. A mock-call sequence is an implementation detail and creates tests that resist refactoring. A fake is appropriate when it represents an external boundary; it is not a reason to make every internal dependency an interface.

Use mutation testing (PITest, Stryker, or the language equivalent) on pure business logic, and property-based testing where invariants are clearer than examples. Run the fast pure tests separately from container-backed integration tests when suite time warrants it.

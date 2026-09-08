# Kotlin decisions

- Default declarations are public: explicitly use `private` or `internal` for feature internals, but do not use `internal` to evade public-API testing.
- Place `http`, `domain`, and `db` beneath the feature package. Use ArchUnit or import rules in CI to prevent upward and cross-feature-internal imports.
- Prefer `val`, immutable collections, and `@JvmInline value class` for IDs when it makes invalid interchange harder.
- Keep serialization and ORM annotations on edge models; map to domain values deliberately.
- Use real values for pure-rule tests and real database containers for persistence tests.

For Gradle or Maven module boundaries, see `build-tools.md`.

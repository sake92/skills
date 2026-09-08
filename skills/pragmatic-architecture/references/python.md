# Python decisions

- Python has convention-based visibility: use a leading underscore and avoid re-exporting feature internals. Make the feature package's public API explicit in its `__init__.py` only when callers need one.
- Group code as `features/orders/http.py`, `domain.py`, and `db.py` (or subpackages). Use import-linter to prevent upward and cross-feature-internal imports when the codebase is large enough to need enforcement.
- Use frozen dataclasses or immutable value objects for domain values when mutation would violate an invariant; validate at constructors and edges.
- Keep Pydantic/ORM models at transport and persistence edges; map to domain values instead of coupling the domain to either framework.
- Unit-test pure rules with real values and integration-test routes plus the production database dialect where practical.

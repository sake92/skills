# User directory

Scala 3, Sharaf and SQLite application. Run `scala-cli run . --server=false --main-class directory.Main -- directory.db 8080`. Compile with `scala-cli compile . --server=false`.

## API contract

- `POST /users`: JSON `name` and `email`; returns 201 and a user with a generated ID and revision 0. Names must contain non-whitespace text; emails must contain `@`. Invalid input returns 400. An existing email returns 409.
- `GET /users/{id}`: returns a user or 404.
- `PUT /users/{id}`: JSON `name`, `email`, and the last observed `revision`. Returns the updated user and increments revision. Missing IDs return 404; stale revisions and conflicting emails return 409. Invalid input returns 400 without changing data.
- `GET /users/{id}/email-changes`: returns a chronological JSON array of `previous`/`current` email changes, or 404 for a missing user. A name-only update adds no event. Failed updates leave both the user and history unchanged.

All user responses contain exactly `id`, `name`, `email`, and `revision`. Internal staff notes remain private. JSON object property order and whitespace are unspecified. Users and history survive reopening the application with the same database file.

## Development

The test directory is empty and no test framework has been chosen. `DirectoryApp` accepts a database path and optional port; port 0 chooses an available port. It exposes its bound URL and implements AutoCloseable. SQLite is the application's actual storage engine.

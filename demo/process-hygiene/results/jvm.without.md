# jvm/Main.java — review WITHOUT the skill (model: haiku)

- **Line 17**: `ProcessBuilder` subprocess is started but never tracked or stopped, with no error handling if the process fails or accumulates unread output.
- **Line 19-27**: Background thread uses infinite `while(true)` loop with no way to shut down, preventing graceful JVM shutdown.
- **Line 24-25**: `InterruptedException` is caught and silently ignored in an empty catch block, preventing proper shutdown signal handling.
- **Line 38 and line 56**: `port()` method lacks null check on `System.getenv("PORT")` and is called multiple times instead of caching the value at startup.
- **Line 42-43**: Database connection method doesn't null-check `DB_HOST` and `DB_PASSWORD` environment variables before use.
- **Line 44**: Database password is logged to stdout, creating a critical security vulnerability where credentials can be captured in application logs.
- **Line 50**: New database connection is created for each request without connection pooling, causing resource leaks and poor performance under load.
- **Line 49-55**: HTTP response is sent immediately without waiting for the async database operation to complete, creating a race condition where the client receives "ok" before the update finishes or fails.
- **Line 53**: SQLException is caught and only logged to stderr with `printStackTrace()`, leaving the client unaware of the failure.

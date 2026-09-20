# jvm/Main.java — review WITH the skill (model: haiku)

- **Line 14 (static ExecutorService POOL)**: Move POOL to an instance field with an AutoCloseable implementation and add a shutdown hook that calls `pool.shutdown()` → `awaitTermination(20s)` → `shutdownNow()`.
- **Line 17 (abandoned Process)**: Capture the Process handle, drain its output to a file or background thread, add a waitFor with timeout, and install a shutdown hook to kill the tree via `ProcessHandle.descendants()`.
- **Lines 19-27 (InterruptedException swallowed)**: Replace the empty catch block with `Thread.currentThread().interrupt(); return;` to restore the interrupt flag and exit the loop when the thread is cancelled.
- **Lines 19-27 (fire-and-forget thread)**: Store the thread reference, join it on shutdown, or use a StructuredTaskScope to manage its lifetime.
- **Line 38, 56 (configuration read outside startup)**: Load and validate PORT once at startup with Integer.parseInt() in a dedicated config method, cache the value, and exit non-zero if it is missing or invalid.
- **Lines 42-44 (secret in logs)**: Remove the System.out.println line that prints the password; read DB_PASSWORD from a file path (env var `DB_PASSWORD_FILE`) instead of directly from an env var.
- **Line 49 (fire-and-forget task)**: Capture the Future returned by POOL.submit(), wait for it with a timeout before sending the HTTP response, or explicitly handle its result.
- **Line 51 (blocking call without timeout)**: Add `c.createStatement().setQueryTimeout(30)` before executing the query to prevent indefinite hangs.
- **Missing shutdown hook**: Install a shutdown hook via `Runtime.getRuntime().addShutdownHook()` that logs to stderr, closes the server, drains in-flight requests with a 20-second deadline, shuts down POOL, and kills any child processes.

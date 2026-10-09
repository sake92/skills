# JVM reference

Read this when Java, Kotlin, or Scala code spawns a subprocess, creates threads or executors, handles shutdown, or catches `InterruptedException`. Snippets are Java; the APIs are the same from Kotlin and Scala.

## Subprocesses with ProcessBuilder

Every `Process` has an owner, drained output, a cancellation path, and a controlled environment. Finite commands have deadlines; persistent children have supervision.

```java
ProcessBuilder pb = new ProcessBuilder("git", "fetch", "--all")
    .directory(repoDir.toFile())
    .redirectErrorStream(true);            // one stream to drain instead of two
pb.environment().clear();                  // do not inherit secrets by default
pb.environment().put("PATH", System.getenv("PATH"));
pb.environment().put("HOME", System.getenv("HOME"));

Process p = pb.start();
// Drain on a separate thread; the child blocks once the pipe buffer (~64 KiB) fills.
Thread drainer = Thread.ofVirtual().start(() -> {
    try (var in = p.getInputStream()) { in.transferTo(logSink); }
    catch (IOException ignored) { /* child closed the pipe */ }
});

if (!p.waitFor(60, TimeUnit.SECONDS)) {
    p.destroy();                           // SIGTERM
    if (!p.waitFor(5, TimeUnit.SECONDS)) {
        p.destroyForcibly();               // SIGKILL
    }
    // Wait for the direct child to finish; also stop descendants (see below).
    if (!p.waitFor(5, TimeUnit.SECONDS)) {
        throw new IOException("child did not exit after forced termination");
    }
    drainer.join(1000);
    if (drainer.isAlive()) p.getInputStream().close();
    throw new TimeoutException("git fetch exceeded 60s");
}
drainer.join(1000);
if (drainer.isAlive()) p.getInputStream().close();
if (p.exitValue() != 0) throw new IOException("git fetch failed: " + p.exitValue());
```

This fragment illustrates pipe handling for a finite command. A production owner must also run descendant termination and join/close the drainer in a finally path on interruption or any exception. Prefer redirects when no output capture is required; they avoid creating another owned task.

Alternatives: `redirectOutput(File)`, `redirectOutput(Redirect.DISCARD)`, or `inheritIO()` when output belongs on the parent's streams. Never start a child and ignore writable pipes.

`ProcessBuilder.inheritIO()` also inherits the parent's stdin; a child that reads stdin can then steal input meant for the parent.

## Kill the whole tree

`Process.destroy()` signals the direct child only. A child that spawned a shell, a build, or a Node process leaves grandchildren behind. Use `ProcessHandle` to walk the tree:

```java
static void killTree(ProcessHandle root) {
    // Retain discovered handles before signalling: reparented children may
    // disappear from root.descendants() when the root exits.
    List<ProcessHandle> kids = root.descendants().toList();
    kids.forEach(ProcessHandle::destroy);
    root.destroy();

    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while ((root.isAlive() || kids.stream().anyMatch(ProcessHandle::isAlive))
            && System.nanoTime() < deadline) {
        Thread.onSpinWait();
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(50));
    }

    kids.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
    root.descendants().forEach(ProcessHandle::destroyForcibly);
    root.destroyForcibly();
}
```

Tree enumeration is best-effort: children can spawn or detach between snapshots. Prefer process groups or OS supervision when containment is required. After signalling, the owner waits for each direct `Process` and its output readers. Register cleanup for owned long-lived children:

```java
Process dev = pb.start();
Runtime.getRuntime().addShutdownHook(new Thread(() -> killTree(dev.toHandle())));
```

`ProcessHandle.current().descendants()` at shutdown catches anything that slipped through, but it is a safety net, not a substitute for owning each child.

## Executors are owned

An `ExecutorService` is created by the component that uses it and closed in that component's close path, never a static field that lives until the JVM dies.

```java
final class ReportService implements AutoCloseable {
    private final ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();

    @Override public void close() {
        pool.shutdown();                                   // stop accepting
        try {
            if (!pool.awaitTermination(20, TimeUnit.SECONDS)) {   // drain with deadline
                pool.shutdownNow();                        // interrupt stragglers
                pool.awaitTermination(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
```

On JDK 19+ `ExecutorService` is `AutoCloseable`; `close()` performs shutdown-and-await, and try-with-resources scopes a short-lived pool to a block:

```java
try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
    futures = tasks.stream().map(pool::submit).toList();
}   // all tasks finished here
```

Default to `newVirtualThreadPerTaskExecutor()` for blocking work. Fixed platform-thread pools are for CPU-bound work or when a library requires it.

## StructuredTaskScope

`java.util.concurrent.StructuredTaskScope` is the JVM's scope primitive and maps directly to SKILL.md ownership guidance. It is a **preview feature**: fifth preview in JDK 25 (JEP 505), with further previews following, so it needs `--enable-preview` and the API may still shift. Use it when the JDK version allows; otherwise the try-with-resources executor above gives the same lifetime guarantee.

```java
try (var scope = StructuredTaskScope.open()) {            // default: fail if any subtask fails
    Subtask<User>  user  = scope.fork(() -> users.find(id));
    Subtask<Order> order = scope.fork(() -> orders.fetch(id));
    scope.join();                                         // waits for both, or throws and cancels the other
    return new Response(user.get(), order.get());
}   // nothing forked here is still running
```

Other joiners: `Joiner.anySuccessfulResultOrThrow()` for hedged requests, `Joiner.awaitAll()` to collect every outcome. A scope has a configurable timeout via `open(joiner, cf -> cf.withTimeout(d))`, which is cancellation with a clock.

## Shutdown hooks

`Runtime.addShutdownHook` runs on `SIGTERM`, `SIGINT`, and `System.exit`, not on `SIGKILL`. Rules:

- Fast and bounded. The hook has its own deadline; a blocked hook blocks JVM exit until the supervisor sends `SIGKILL`.
- Hooks run concurrently and in no defined order. Do not depend on another hook having run.
- Do not rely on logging frameworks inside a hook; they may have their own hook that already ran. Write to `System.err`.
- Idempotent. A hook that runs twice must be harmless.

```java
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    System.err.println("myapp: shutting down");
    server.stop();                                   // stop intake
    if (!inFlight.awaitZero(Duration.ofSeconds(20))) // drain with deadline
        System.err.println("myapp: drain timed out, abandoning in-flight work");
    reportService.close();                           // owned executors
    killTree(devProcess.toHandle());                 // owned children
    dataSource.close();
}, "shutdown"));
```

## Signals

The JVM converts `SIGTERM` and `SIGINT` into the shutdown sequence. `SIGKILL` cannot be caught. `sun.misc.Signal` is internal API; avoid it, and treat `SIGHUP` the same as `SIGTERM` unless reload is a documented feature. To exit with a code: `System.exit(n)` from normal code, which runs the hooks. From *inside* a hook, `System.exit` deadlocks; use `Runtime.getRuntime().halt(n)` there, only after cleanup is done.

## InterruptedException

Interruption is the JVM's cancellation signal. Swallowing it makes a task uncancellable.

```java
// Wrong: the interrupt is lost, the loop never ends, shutdownNow() has no effect.
try { Thread.sleep(1000); } catch (InterruptedException e) { }

// Right, when the method can throw: propagate.
try { Thread.sleep(1000); } catch (InterruptedException e) { throw e; }

// Right, when it cannot: restore the flag and stop.
try { Thread.sleep(1000); }
catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
```

Loops check `Thread.currentThread().isInterrupted()` each iteration when they do no blocking call that would throw.

## Daemon threads

`setDaemon(true)` lets the JVM exit without waiting for the thread. That is acceptable only when the thread's abrupt death cannot corrupt anything: a metrics ticker, a cache warmer, a periodic log flush of already-durable data. Never for a thread that writes to a database, a file, or a queue. Require a comment on the `setDaemon` call saying why it is safe. A daemon thread is still owned; it is stopped in its owner's close path in the normal case, and the daemon flag covers only the abnormal one.

## Timeouts

Every blocking operation must be cancellable or bounded. Interruptible waits can be appropriate when the owner interrupts and joins the waiting task. Use timeouts for finite operations and waits that otherwise cannot be unblocked.

| Call | Use |
|---|---|
| `Future.get()` | `get(timeout, unit)` |
| `HttpClient` | `connectTimeout` on the builder, `timeout` on each request |
| JDBC | `DriverManager.setLoginTimeout`, `Statement.setQueryTimeout`, pool `connectionTimeout` |
| `Lock.lock()` | `tryLock(timeout, unit)` |
| `BlockingQueue.take()` | `poll(timeout, unit)` |
| `Process.waitFor()` | `waitFor(timeout, unit)` |
| `Thread.join()` | `join(millis)` |
| `CountDownLatch.await()` | `await(timeout, unit)` |

## Checklist

- [ ] Every `Process` has its output drained (redirect or reader thread), a `waitFor(timeout)`, and `destroy` → `destroyForcibly` on expiry?
- [ ] Long-lived children have a shutdown hook that kills the tree via `ProcessHandle.descendants()`?
- [ ] `ProcessBuilder.environment()` cleared or curated when the child does not need the parent's environment?
- [ ] Every `ExecutorService` is a field of the component that uses it and is shut down in that component's `close()`, with `shutdown` → `awaitTermination` → `shutdownNow`?
- [ ] No `new Thread(...).start()` without a join or an owner? No static executors?
- [ ] No empty or log-only `catch (InterruptedException e)`? Interrupt is rethrown or restored?
- [ ] Shutdown hook is bounded, idempotent, uses `System.err`, and does not call `System.exit`?
- [ ] `setDaemon(true)` only with a comment explaining why abrupt death is harmless?
- [ ] Every blocking operation is cancellable or bounded, including during shutdown?
- [ ] Virtual threads for blocking work; `StructuredTaskScope` or try-with-resources executor for fan-out?

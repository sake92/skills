# Structured concurrency reference (SKILL.md §4)

Read this when code spawns threads, tasks, coroutines, or subprocesses, or when reviewing shutdown and cancellation. Mechanics per platform are in `jvm.md` and `shell.md`.

## The argument

Nathaniel J. Smith's essay "Notes on structured concurrency, or: Go statement considered harmful" makes one observation: `go f()`, `new Thread(f).start()`, `spawn(f)`, `f() &` are the `goto` of control flow. The calling function returns while the spawned work continues, so the shape of the code no longer tells you when that work ends, who sees its errors, or how to cancel it. Every abstraction built on top inherits the problem: a function that spawns something is no longer a black box, because its effects continue after it returns.

Structured concurrency is the fix, by analogy with structured programming replacing `goto` with blocks. Every concurrent operation runs inside a **scope** (a nursery in Trio, a task group in asyncio, a `coroutineScope` in Kotlin, a `StructuredTaskScope` on the JVM, `std::thread::scope` in Rust), and the scope guarantees three things:

1. **Bounded lifetime.** The scope does not exit until every child has finished. Nothing leaks past the closing brace.
2. **Error propagation.** A child's failure surfaces in the scope, and the scope cancels its siblings. No error disappears into an uncaught-exception handler on a thread nobody is watching.
3. **Cancellation propagation.** Cancelling the scope cancels every child, transitively.

Once every spawn is inside a scope, concurrency has a shape you can read from the indentation.

## Consequences

- A handle you do not await, join, or cancel is a bug. There is no "I'll let it finish on its own".
- "Background task" means "task owned by a longer-lived scope", never "task nobody owns". Name the owner.
- Shutdown is cancelling the root scope. If shutdown needs a list of things to stop, the scope tree is missing.
- A timeout is cancellation with a clock. It is applied to a scope, not bolted onto each call.
- Errors have exactly one place to go: up through the scope. Logging and swallowing inside the child is a way of hiding them.

## Patterns

- **Scope per request.** Work fanned out to serve one request (parallel lookups, a hedge request) is joined before the response is written. Nothing from request N is still running when request N+1 arrives.
- **Scope per component.** A component that needs concurrency owns a scope or pool, created in its constructor and closed in its `close()`. The component's lifetime bounds its threads.
- **Root scope in `main`.** Every component's scope hangs off one root. `SIGTERM` cancels the root; the tree unwinds in order.
- **Supervisor for long-lived workers.** A consumer loop that must run for the life of the process still has an owner: a supervisor that starts it, restarts it with backoff on failure, records those failures, and stops it when the root scope is cancelled. The supervisor is the scope; the worker is its child.

## Cancellation is cooperative

Cancellation is a request, not a kill. The child must notice it:

- Check for cancellation in every loop iteration.
- Prefer interruptible or cancellable blocking calls (`poll(timeout)` over `take()`, `tryLock(timeout)` over `lock()`, a socket with a read timeout).
- Never swallow the cancellation signal. On the JVM that is `InterruptedException`; in asyncio `CancelledError`; in Kotlin `CancellationException`. Catching it to log and continue turns a cancellable task into an immortal one.
- Propagate it: rethrow, or re-set the interrupt flag and return.

## Subprocesses are children too

A subprocess is a child with an operating-system handle instead of a language one, and the same rules apply: it has an owner, a deadline, and a place its errors go. Three additional mechanics, because the OS does not do them for you:

- **Drain its output.** Pipes have a fixed buffer, commonly 64 KiB. A child that writes more than that to a pipe nobody reads blocks forever, and so does whoever eventually waits on it.
- **Kill the tree.** Killing the direct child does not kill its children. A shell, a build tool, a package manager, and Node all spawn grandchildren, and those become orphans that keep running, keep ports open, and keep writing files.
- **Control the environment.** The child inherits the parent's full environment by default, including every secret in it (SKILL.md §2). Pass an explicit environment when the child does not need the parent's.

## Language primitives

One line each. Read the platform file for mechanics.

| Platform | Scope primitive | Notes |
|---|---|---|
| JVM | `StructuredTaskScope`; `ExecutorService` used as a scope with try-with-resources; virtual threads | see `jvm.md` |
| Kotlin | `coroutineScope`, `supervisorScope`, `SupervisorJob` | `GlobalScope` is the fire-and-forget anti-pattern |
| Python | `asyncio.TaskGroup` (3.11+), Trio nurseries | `asyncio.create_task` without holding the reference is a leak |
| Rust | `std::thread::scope`, tokio `JoinSet` | `tokio::spawn` detaches; hold the `JoinHandle` |
| Go | `errgroup.Group` with a `context.Context` | a bare `go` statement is the thing the essay is about |
| Node | `AbortController` + `Promise.all` | no scope primitive; discipline required, hold every promise |
| Shell | process groups, `trap`, `wait` | see `shell.md` |

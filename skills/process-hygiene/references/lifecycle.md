# Lifecycle reference (SKILL.md §3)

Read this when writing or reviewing startup code, signal handling, shutdown, a Dockerfile entrypoint, or health probes.

## Signals

| Signal | Meaning | What to do |
|---|---|---|
| `SIGINT` (2) | Ctrl-C from a terminal | same as `SIGTERM` |
| `SIGTERM` (15) | the supervisor asks you to stop | run the shutdown protocol |
| `SIGHUP` (1) | terminal closed; historically "reload configuration" | treat as `SIGTERM` unless reload is a documented feature |
| `SIGKILL` (9) | immediate death, cannot be caught | design so this is always safe (crash-only) |
| `SIGPIPE` (13) | the reader of your stdout went away | exit quietly; never spray errors because someone ran `myapp | head` |
| `SIGQUIT` (3) | Ctrl-\ ; on the JVM prints a thread dump | leave the default behavior alone |

Exit codes after a signal follow the shell convention 128 + signal number: 130 after `SIGINT`, 143 after `SIGTERM`.

## The shutdown protocol

1. **Announce.** One line on stderr: `myapp: received SIGTERM, shutting down`.
2. **Stop intake.** What this means depends on the workload:
   - HTTP server: stop accepting connections, fail the readiness probe, keep serving requests already in progress.
   - Queue consumer: stop fetching; do not ack anything you have not finished.
   - Scheduler: cancel timers so no new job starts.
   - Batch job: stop picking up the next item; finish or checkpoint the current one.
3. **Drain with a deadline.** Wait for in-flight work, but not forever. Budget arithmetic: `drain deadline = grace period − cleanup budget − margin`. With the Kubernetes default of 30 s: drain for at most 20 s, keep 5 s for cleanup, 5 s margin.
4. **Release resources** in reverse order of acquisition: connection pools, file handles, temporary directories, child processes (kill the tree, see SKILL.md §4).
5. **Exit** with the right code: 0 if the shutdown was clean, 143 if you want to signal that it was signal-initiated, non-zero if drain timed out and work was abandoned.

Cleanup has its own timeout. A `close()` that blocks on a dead database must not prevent exit.

## Second signal

If a second `SIGTERM` or `SIGINT` arrives while cleanup is running: skip the remaining cleanup, print one line saying so (`myapp: second signal, exiting immediately`), and exit. Users press Ctrl-C twice because the first one appeared to do nothing; honor that.

## Kubernetes termination sequence

1. The pod is marked `Terminating` and removed from Service endpoints. This propagation is **not instantaneous**; kube-proxy and ingress controllers catch up over the next second or so. Traffic can still arrive after step 3.
2. The `preStop` hook runs, if defined. A `sleep 5` here is a common, legitimate way to let endpoint removal propagate before the process is told to stop.
3. `SIGTERM` is sent to PID 1 of each container. The grace period timer (`terminationGracePeriodSeconds`, default 30) starts here.
4. When the timer expires, `SIGKILL`.

Implications for the code: fail the readiness probe immediately on `SIGTERM`, keep the listener serving for a moment, drain within the budget above. If your shutdown needs longer than 30 s, raise the grace period in the manifest; do not silently exceed it.

## PID 1 in containers

PID 1 has two duties the kernel expects: reap zombie children, and receive the signals the runtime sends to the container.

`CMD sh -c "java -jar app.jar"` makes `sh` PID 1. `sh` does not forward `SIGTERM` to its child, so `docker stop` waits 10 s and then `SIGKILL`s everything. Your shutdown protocol never runs.

Fixes, any one of them:

- Exec form: `CMD ["java", "-jar", "app.jar"]`. The application is PID 1.
- If a script must run first, its last line is `exec java -jar app.jar "$@"` so the script's PID becomes the application's.
- Run under a minimal init: `ENTRYPOINT ["tini", "--"]`, or `docker run --init`. Required when the application itself spawns children it does not reap, because a PID 1 that does not call `wait` leaves zombies forever.

Check with `docker exec <container> ps -o pid,comm`. PID 1 should be your process or an init, never `sh`.

## Readiness vs liveness

- **Liveness**: the process is alive and not deadlocked. Restart it if this fails. Keep it cheap and dependency-free; a database outage must not make the orchestrator restart every application pod.
- **Readiness**: dependencies are confirmed and the process may receive traffic. Fail it while starting, and fail it first on shutdown.
- **Startup probe**: for slow starters, so liveness does not kill the process before it finishes initialization.

## Crash-only, in one paragraph

Candea and Fox's argument: the only way to stop a crash-only program is to crash it, and the only way to start it is to recover. Consequences for design: state that matters lives in a store with recovery semantics (a database, a log, a queue with acknowledgements), work is idempotent or transactional so that redoing a half-finished unit is safe, startup *is* the recovery path and is exercised on every boot, and graceful shutdown becomes an optimization that saves time rather than a ritual that preserves correctness. If restart after `SIGKILL` corrupts anything, that corruption is the first bug to fix, before any shutdown-hook work.

## Startup

Fail fast on:

- Configuration that does not validate (exit 2, name the setting and its source).
- A secret file that is missing or unreadable.
- A required backing service that is unreachable after a bounded number of retries measured in seconds, not minutes. The supervisor's restart policy is the retry loop; do not reimplement it inside the process.
- Pending migrations when the application expects the schema to be current, unless the application is the one that applies them.

Order of operations in `main`:

1. Print one line (`myapp starting`) within 100 ms.
2. Load and validate configuration.
3. Install signal handlers and the shutdown hook.
4. Connect to backing services with timeouts.
5. Start intake (listener, consumer, scheduler).
6. Mark ready.

Signal handlers go in before anything heavy so that a `SIGTERM` during a slow step 4 still produces an orderly exit instead of a hang.

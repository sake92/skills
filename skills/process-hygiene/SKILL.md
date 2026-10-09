---
name: process-hygiene
description: 'Process lifecycle and ownership of threads, tasks, executors, and subprocesses. Use when writing or reviewing startup/shutdown, signals, cancellation, concurrent work, child-process supervision, or container entrypoints. Covers bounded cleanup, pipe draining, descendant termination, and reaping. For terminal interfaces use cli-design; for deployment configuration and state use twelve-factor-app.'
---

# Process hygiene

Every concurrent operation needs an owner, a lifetime, a cancellation path, and a place its failures go. Preserve an API that already gives its caller these guarantees; do not add a background task or scope wrapper merely for convenience.

## Procedure

1. Identify each owner and the work/resources it acquires, including partial startup.
2. Trace normal completion, child failure, caller cancellation, and shutdown.
3. Fix the observed gap using the runtime's existing primitives.
4. Verify cleanup and observable failure/cancellation through public behavior.

Read only the relevant reference: [concurrency](references/concurrency.md), [lifecycle](references/lifecycle.md), [JVM](references/jvm.md), or [shell](references/shell.md).

## Startup and shutdown

- Validate required inputs before accepting work. Bound connection attempts; release resources already acquired on startup failure.
- Install shutdown handling before expensive startup work. Readiness means the process can accept work; liveness need not depend on an available backing service.
- Stop intake, drain or cancel in-flight work within a deadline, close resources in reverse acquisition order, and finish. Cleanup has its own budget inside the supervisor's grace period.
- Make repeated shutdown requests safe. Where supported, a second interrupt can force prompt termination.
- Correctness must survive abrupt termination through the workload's transaction, acknowledgement, checkpoint, or recovery mechanism.
- Keep process exit at the entrypoint. Lower-level code reports failure so cleanup can run.
- Container entrypoint scripts exec the application. Use an init when needed to forward signals and reap adopted children.

## Threads, tasks, and executors

- Keep handles and join/await owned work. Transfer long-lived work explicitly to a longer-lived owner; document intentional detaching and its replacement owner.
- Close owned executors: stop submissions, request shutdown, await within a deadline, then cancel remaining work. Daemon status does not replace ownership.
- Propagate failures and cancellation according to the API contract. Do not swallow an interrupt or cancellation and continue the loop.
- Make waits cancellable. An interruptible queue wait may legitimately have no polling timeout; an uncancellable blocking operation needs a bound or a reliable unblock mechanism.
- Apply deadlines to finite operations. A supervised worker may run indefinitely if shutdown can stop and join it.
- Bound fan-out where each task consumes scarce threads, processes, connections, or descriptors.

## Subprocesses

- Drain stdout/stderr concurrently, redirect them, or inherit appropriate streams before waiting. Never wait for exit with an undrained writable pipe.
- Finite commands need a deadline; persistent children need supervision and cancellation. Request graceful termination, allow bounded cleanup, then force termination.
- Own the descendant tree. Use process groups or platform supervision when killing only the direct child would orphan grandchildren. Account for descendants that detach or escape those facilities.
- Wait/reap every started child, including failed and cancelled commands. Preserve exit status or failure for the caller.
- Give children only the environment they need. Avoid forwarding unrelated credentials while retaining required runtime variables.
- A stored PID alone is unsafe after PID reuse. Obtaining a process handle does not prove liveness.

## Completion checklist

- Does partial startup release what it acquired?
- Can failure, cancellation, and shutdown stop intake and finish within a bounded budget?
- Are tasks/executors owned, cancelled where needed, and joined?
- Can waits observe cancellation?
- Are subprocess pipes drained, descendants stopped, and children reaped?
- Are persistent/detached operations explicitly owned elsewhere?
- Does abrupt termination leave recoverable work?

Apply only the rules relevant to the workload. A short command does not need a service readiness protocol.

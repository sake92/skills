# Parallel build launcher

`BuildLaunch` turns requested module names into `BuildStep`s. `ParallelBuildRunner` runs one build on an owned executor; the supplied `ThreadFactory` configures the actual workers. The long-lived launcher reuses the workspace after each call finishes.

Successful reports preserve input order and include every result. At most `parallelism` actions run concurrently. When any action fails or the caller is interrupted, the build stops remaining work and reports the original failure or `InterruptedException`. Actions cooperate with interruption and may need time to release workspace resources. `cleanupMillis` bounds the total cleanup, including cancellation and waiting for worker termination. Before returning or throwing, the build must have relinquished its owned tasks and executor. Preserve public APIs and the launch adapter.

Once a task or startup failure has been observed, it remains the primary failure if the caller is interrupted during cleanup. Cleanup still needs to finish before that failure is reported.

The supplied factory may establish worker-local resource scopes around execution. Actual worker threads, including those scopes' finalization, belong to the build and must finish before its call completes.

Run `scala-cli test . --server=false`.

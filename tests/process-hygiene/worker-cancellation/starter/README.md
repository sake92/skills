# Build worker attempts

The long-lived build launcher runs one `WorkerJob` at a time through `WorkerRunner` on a scheduler-owned thread. Each attempt has separate stdout/stderr log files and a time budget. The scheduler cancels an attempt by interrupting that thread. `tools/compiler-worker.sh` and `tools/compile-helper.sh` represent an external toolchain; `once` is a short successful build, and the default mode keeps working until stopped.

`WorkerReport` distinguishes a completed command's exit status from a timed-out attempt. Cancellation is reported by propagating `InterruptedException`, rather than returning a normal report. The configured cleanup budget includes the grace period. Before returning or throwing, the attempt must relinquish its work and workspace resources; the launcher can then retry. Preserve public APIs, launch configuration, separate log files, and ordinary exit results. Keep fixes in the launcher rather than requiring changes to external tools.

Linux requirements: `/bin/bash`, `flock`, and `mkfifo`. Run `scala-cli test . --server=false`.

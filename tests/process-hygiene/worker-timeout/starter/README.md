# Build worker attempts

The long-lived build launcher runs one `WorkerJob` at a time through `WorkerRunner`. Each attempt has separate stdout/stderr log files and a time budget. `tools/compiler-worker.sh` and `tools/compile-helper.sh` represent an external toolchain; `once` is a short successful build, and the default mode keeps working until stopped.

`WorkerReport` distinguishes a completed command's exit status from a timed-out attempt. The configured cleanup budget includes the grace period. The launcher reuses the workspace after the call returns. Preserve public APIs, launch configuration, separate log files, and ordinary exit results. Keep fixes in the launcher rather than requiring changes to external tools.

Linux requirements: `/bin/bash`, `flock`, and `mkfifo`. Run `scala-cli test . --server=false`.

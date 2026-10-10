# Forked tools inside a build task

This standalone build-tool execution path includes process launch configuration, a stream bridge, task events/results, and a command-entrypoint adapter. It models Mill's task streams and input policies and Deder's forked workers. It is an adaptation written for this fixture, not a verbatim upstream extract.

`ForkedTool` launches the argument vector with the requested working directory and environment overrides. Tool output must reach the current task's output streams while the tool runs, byte-for-byte, with stdout and stderr separate. Completion means all tool output has been forwarded. These are borrowed task streams, so the tool runner cannot close them.

`Closed` gives a batch tool EOF without reading the launcher's input. `Document` supplies the exact bytes and then EOF. `Interactive` forwards the explicitly supplied input stream as input arrives. The launcher input and interactive stream belong to their callers and must remain open after the command. A tool may print a prompt without a newline before it reads input.

`BuildTask` reports the actual tool exit code in both the completion event and `TaskOutcome`. `BuildMain.runTask` returns that same code to the outer launcher, which owns process exit. Keep these public APIs compatible and retain argument-vector, working-directory, environment, and per-task stream behavior.

This case targets basic stdio and exit handling on Linux (`/bin/sh` and `cat` available). Do not add cancellation, timeout, descendant supervision, or a new concurrent task scheduler as part of this task. Run `scala-cli test . --server=false`.

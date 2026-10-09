# Shell reference

Read this when writing or reviewing a bash script, a container entrypoint, a CI step, or a helper script that starts servers or watchers. Shell is where orphaned processes are most common, because `&` is one character and the cleanup is twelve lines.

## Strict mode

```bash
#!/usr/bin/env bash
set -euo pipefail
```

- `-e`: exit on the first failing command instead of carrying on with half the work done.
- `-u`: an unset variable is an error, not an empty string. Catches typos and missing arguments.
- `-o pipefail`: a pipeline fails if any stage fails, not only the last one. Without it `curl ... | jq` succeeds when `curl` fails.

Where a command is allowed to fail, say so explicitly: `cmd || true`, or `if cmd; then ...`.

## Own your children

Every owned background job records its identity. A trap terminates recorded groups and waits for direct children. Illustrative Linux launcher (assumes setsid does not fork away from the recorded leader):

```bash
#!/usr/bin/env bash
set -euo pipefail

pids=()

cleanup() {
  local code=$?
  trap - EXIT INT TERM                      # do not re-enter
  for pid in "${pids[@]:-}"; do
    [[ -n "$pid" ]] || continue
    kill -- -"$pid" 2>/dev/null || kill "$pid" 2>/dev/null || true   # group first, then pid
  done
  # Give cooperative children a bounded grace interval, then escalate.
  sleep 2
  for pid in "${pids[@]:-}"; do
    [[ -n "$pid" ]] || continue
    kill -KILL -- -"$pid" 2>/dev/null || kill -KILL "$pid" 2>/dev/null || true
  done
  wait 2>/dev/null || true
  exit "$code"
}
trap cleanup EXIT INT TERM

setsid npm run dev >dev.log 2>&1 &  pids+=($!)
setsid python -m http.server 9000 >static.log 2>&1 &  pids+=($!)

# wait for readiness with a bound, not a fixed sleep
for _ in $(seq 1 30); do
  curl -fsS localhost:3000/health >/dev/null 2>&1 && break
  sleep 1
done

# ... do the work ...

wait                                        # only if the script should live as long as its children
```

Why each part:

- `setsid` starts the child in its own session and process group, so `kill -- -$pid` (negative PID) signals the whole group: the server *and* everything it spawned. Without it, killing `npm` leaves the `node` process it started.
- `pids+=($!)` immediately after each `&`. `$!` is overwritten by the next background command.
- The trap runs on `EXIT` as well as `INT` and `TERM`, so a `set -e` failure or a normal end still cleans up.
- `trap - ...` inside `cleanup` prevents recursion when `exit` fires `EXIT` again.
- `wait` after killing lets children die before the script returns; otherwise the caller sees the script finished while ports are still held.

Process-group containment and setsid availability vary by platform. Killing a PID plus its currently discovered direct children is not equivalent to containing the descendant tree. For robust launchers, use the platform's supervisor and identity facilities; children that escape the recorded group need another owner. Waiting can still be unbounded for a child stuck in an uninterruptible kernel operation, so the outer supervisor must enforce the final process deadline.

## timeout

Bound anything that could hang:

```bash
timeout --kill-after=5s 30s ./integration-test.sh
```

Sends `SIGTERM` at 30 s and `SIGKILL` 5 s later. Exit code 124 means timed out. On macOS the GNU version is `gtimeout` from coreutils.

## Intentional detaching

Sometimes a script's job *is* to leave something running: a launcher, a bootstrap that hands off to a supervisor. Make it unmistakable:

```bash
# Detach on purpose: the daemon is supervised by launchd; see docs/ops.md.
setsid nohup ./daemon >"$XDG_STATE_HOME/myapp/daemon.log" 2>&1 < /dev/null &
disown
```

- Comment says it is deliberate and names the new owner.
- `nohup` survives the terminal closing; `setsid` gives it its own session; `disown` removes it from this shell's job table; stdin redirected so it never blocks on a TTY.
- Never in CI, in a test helper, or in a script an agent runs during a task. Those must end with nothing of theirs still running.

## Entrypoints

The last line of a container entrypoint script is `exec`:

```bash
#!/usr/bin/env bash
set -euo pipefail
./migrate --config /etc/myapp/config.yaml
exec java -jar /app/app.jar "$@"
```

`exec` replaces the shell with the application, so the application becomes PID 1 and receives `SIGTERM` directly. Without it the shell is PID 1, ignores `SIGTERM`, and the runtime `SIGKILL`s after the grace period.

If the application itself spawns children it does not reap, run under an init: `ENTRYPOINT ["tini", "--", "/entrypoint.sh"]` or `docker run --init`. See `lifecycle.md`.

## Output

- Data to stdout. Messages, progress, and errors to stderr: `echo "fetching..." >&2`.
- A function that returns a value via stdout must not also print progress there.
- Exit codes: `exit 0` success, `exit 1` general failure, `exit 2` usage or configuration error. A caller that does `if ./script; then` depends on this.
- Quiet on `SIGPIPE`: when the reader goes away (`./script | head`), stop writing; do not print an error.

## Checklist

- [ ] `set -euo pipefail` at the top, with explicit `|| true` where failure is acceptable?
- [ ] Every `&` followed by recording `$!`? Every recorded PID killed in a `trap` on `EXIT INT TERM`?
- [ ] Background servers started with `setsid` (or equivalent) so their process group can be killed as a unit?
- [ ] `wait` before the script returns, so ports and files are released?
- [ ] Readiness polled with a bound, not `sleep N`?
- [ ] Anything that could hang wrapped in `timeout`?
- [ ] Intentional detaching commented, with the new owner named, and absent from CI and agent scripts?
- [ ] Entrypoint ends in `exec`, or the container runs under an init?
- [ ] Messages on stderr via `>&2`, data on stdout, meaningful exit codes?

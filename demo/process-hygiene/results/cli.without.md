# cli/tool.py — review WITHOUT the skill (model: haiku)

- **Line 14-16**: No argument validation — add `if len(sys.argv) < 4:` check before accessing `sys.argv[1]`, `sys.argv[2]`, `sys.argv[3]` to provide a helpful error message instead of crashing with IndexError.
- **Line 26**: No validation of JSON structure — check that `data.get("items")` exists and is a list before iterating, or handle KeyError/TypeError explicitly.
- **Line 30-31**: Temp file handle not guaranteed to close on exception — wrap the file operations in a try-finally block to ensure `tmp.close()` is called even if an exception occurs between lines 28-30.
- **Line 37**: `sys.exit()` called without status code — use `sys.exit(1)` to signal failure to the caller.
- **Line 35-36**: Sensitive data exposure in error messages — the error string could contain the API key if passed in the URL; sanitize exception messages before printing or avoid printing raw exception text.
- **Line 31**: `os.rename()` has no error handling — add a try-except around the rename call to handle FileExistsError and other filesystem errors.
- **Line 25**: No distinction between HTTP errors and JSON parsing errors — use separate exception handlers to differentiate network failures (urllib.error.URLError, HTTPError) from JSON parsing failures (json.JSONDecodeError).

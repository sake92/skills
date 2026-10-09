# Sessions service configuration

This module is used by the deployed sessions service and its admin commands. The platform supplies PORT, DATABASE_URL, and SESSION_TTL_MINUTES. The same artifact is deployed to staging and production. APP_ENV is descriptive metadata.

Run `scala-cli test . --server=false`. Preserve Config.load(Map), Config's fields, and SessionPolicy's constructor/methods because callers use them.

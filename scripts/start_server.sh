#!/usr/bin/env sh
set -eu

PORT="${PORT:-8000}"

if [ "${RUN_MIGRATIONS:-1}" = "1" ]; then
  alembic upgrade head
fi

exec uvicorn app.main:app --host 0.0.0.0 --port "$PORT"

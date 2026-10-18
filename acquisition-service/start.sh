#!/usr/bin/env sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
cd "$ROOT"
[ -f config/service.json ] || cp config/service.json.example config/service.json
exec "$ROOT/.venv/bin/python" "$ROOT/run.py" --config config/service.json

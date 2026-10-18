#!/usr/bin/env bash
# 物小智一键启动（读取 config/ports.env）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PORTS_FILE="$ROOT/config/ports.env"

BACKEND_PORT=8082
FRONTEND_PORT=5174

if [[ -f "$PORTS_FILE" ]]; then
  while IFS='=' read -r key value; do
    key="$(echo "$key" | tr -d '[:space:]')"
    value="$(echo "$value" | tr -d '[:space:]')"
    [[ "$key" =~ ^# ]] && continue
    [[ -z "$key" ]] && continue
    case "$key" in
      BACKEND_PORT) BACKEND_PORT="$value" ;;
      FRONTEND_PORT) FRONTEND_PORT="$value" ;;
    esac
  done < <(grep -E '^(BACKEND_PORT|FRONTEND_PORT)=' "$PORTS_FILE" || true)
fi

port_in_use() {
  local port="$1"
  if command -v ss >/dev/null 2>&1; then
    ss -ltn | awk -v p=":$port" '$4 ~ p {found=1} END {exit !found}'
  else
    lsof -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1
  fi
}

check_port() {
  local port="$1" label="$2"
  if port_in_use "$port"; then
    echo "端口 $port ($label) 已被占用，请修改 config/ports.env 后重试。" >&2
    exit 1
  fi
}

echo "配置端口：后端 $BACKEND_PORT，前端 $FRONTEND_PORT（$PORTS_FILE）"
check_port "$BACKEND_PORT" "后端"
check_port "$FRONTEND_PORT" "前端"

export BACKEND_PORT

echo "启动后端..."
(cd "$ROOT/backend" && mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=$BACKEND_PORT") &
BACKEND_PID=$!

sleep 2

echo "启动前端..."
(cd "$ROOT/frontend" && npm run dev -- --host 0.0.0.0 --port "$FRONTEND_PORT") &
FRONTEND_PID=$!

echo ""
echo "已启动："
echo "  前端 http://localhost:$FRONTEND_PORT/"
echo "  后端 http://localhost:$BACKEND_PORT/"
echo "  后端 PID $BACKEND_PID，前端 PID $FRONTEND_PID"

wait

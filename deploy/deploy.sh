#!/usr/bin/env bash
# TrueBuddy 空白主机一键部署（Linux / macOS / WSL）
# 前置：仅 Docker Engine + Docker Compose 插件；无需 Java / Node / Python / Maven / psql。
# 用法：cd <仓库根> && bash deploy/deploy.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT/deploy/docker-compose.yml"
ENV_FILE="$ROOT/.env"

get_env_value() {
  grep "^$1=" "$ENV_FILE" 2>/dev/null | head -n 1 | cut -d= -f2- || true
}

set_env_value() {
  TMP_ENV="$(mktemp)"
  awk -v k="$1" -v v="$2" \
    '{ if ($0 ~ "^" k "=") print k "=" v; else print }' \
    "$ENV_FILE" > "$TMP_ENV"
  mv "$TMP_ENV" "$ENV_FILE"
}

random_hex() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex "$1"
  else
    od -An -N"$1" -tx1 /dev/urandom | tr -d ' \n'
  fi
}

is_placeholder() {
  case "$1" in
    ''|change-me|change-me-please|postgres) return 0 ;;
    *) return 1 ;;
  esac
}

generate_secrets() {
  local pg ds minio lk_key lk_secret generated=0
  pg="$(get_env_value POSTGRES_PASSWORD)"
  ds="$(get_env_value SPRING_DATASOURCE_PASSWORD)"
  if is_placeholder "$pg" || is_placeholder "$ds"; then
    local db_pass
    db_pass="$(random_hex 16)"
    set_env_value POSTGRES_PASSWORD "$db_pass"
    set_env_value SPRING_DATASOURCE_PASSWORD "$db_pass"
    generated=$((generated + 1))
  fi

  minio="$(get_env_value MINIO_SECRET_KEY)"
  if is_placeholder "$minio"; then
    set_env_value MINIO_SECRET_KEY "$(random_hex 24)"
    generated=$((generated + 1))
  fi

  lk_key="$(get_env_value LIVEKIT_API_KEY)"
  lk_secret="$(get_env_value LIVEKIT_API_SECRET)"
  if is_placeholder "$lk_key"; then
    set_env_value LIVEKIT_API_KEY "lk_$(random_hex 12)"
    generated=$((generated + 1))
  fi
  if is_placeholder "$lk_secret"; then
    set_env_value LIVEKIT_API_SECRET "$(random_hex 24)"
    generated=$((generated + 1))
  fi

  if [ "$generated" -gt 0 ]; then
    echo "[初始化] 已生成 $generated 个本地随机凭据（未回显）"
  fi
}

if ! command -v docker >/dev/null 2>&1; then
  echo "[错误] 未找到 docker，请先安装 Docker Engine。" >&2
  exit 1
fi
if ! docker compose version >/dev/null 2>&1; then
  echo "[错误] 未找到 docker compose 插件，请安装 Docker Compose。" >&2
  exit 1
fi

if [ ! -f "$ENV_FILE" ]; then
  cp "$ROOT/deploy/.env.example" "$ENV_FILE"
  echo "[初始化] 已从 deploy/.env.example 生成根目录 .env"
fi

# 补齐模板中缺失的键（保留已有值，避免旧 .env 缺 PUBLIC_HOST 等导致 http://:9000）
while IFS= read -r line; do
  case "$line" in ''|\#*) continue ;; esac
  key="${line%%=*}"
  if ! grep -q "^${key}=" "$ENV_FILE"; then
    printf '%s\n' "$line" >> "$ENV_FILE"
    echo "[初始化] 补入缺失变量 $key"
  fi
done < "$ROOT/deploy/.env.example"

CURRENT_PUBLIC_HOST="$(grep '^PUBLIC_HOST=' "$ENV_FILE" | head -n 1 | cut -d= -f2- || true)"
if [ -z "$CURRENT_PUBLIC_HOST" ] || [ "$CURRENT_PUBLIC_HOST" = "__AUTO_IP__" ]; then
  DETECTED_IP="$(hostname -I 2>/dev/null | awk '{print $1}' || true)"
  if [ -z "$DETECTED_IP" ]; then
    DETECTED_IP="127.0.0.1"
    echo "[警告] 未能自动探测局域网 IP，已回落 127.0.0.1；跨机访问请手工修改 .env 的 PUBLIC_HOST。"
  fi
  TMP_ENV="$(mktemp)"
  awk -v ip="$DETECTED_IP" \
    '{ if ($0 ~ /^PUBLIC_HOST=/) print "PUBLIC_HOST=" ip; else print }' \
    "$ENV_FILE" > "$TMP_ENV"
  mv "$TMP_ENV" "$ENV_FILE"
  echo "[初始化] PUBLIC_HOST=$DETECTED_IP"
fi

generate_secrets

set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

PUBLIC_HOST="${PUBLIC_HOST:-127.0.0.1}"
FRONTEND_PORT="${FRONTEND_PORT:-8080}"
BACKEND_PORT="${BACKEND_PORT:-8086}"
MINIO_PORT="${MINIO_PORT:-9000}"
MINIO_CONSOLE_PORT="${MINIO_CONSOLE_PORT:-9001}"
LIVEKIT_PORT="${LIVEKIT_PORT:-7880}"

DC=(docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE")

echo "[1/3] 拉取基础镜像并构建应用镜像（首次执行较慢）..."
"${DC[@]}" build --pull

echo "[2/3] 启动全部服务并等待健康检查（最多 300 秒）..."
"${DC[@]}" up -d --wait --wait-timeout 300

echo "[3/3] 检查 MinIO 与 LiveKit..."
if "${DC[@]}" exec -T frontend wget -qO- "http://minio:9000/minio/health/live" >/dev/null 2>&1; then
  echo "MinIO 健康"
else
  echo "[警告] MinIO 未通过健康检查，请查看：docker compose -f deploy/docker-compose.yml logs minio"
fi
if "${DC[@]}" exec -T frontend wget -qO- "http://livekit:7880/" >/dev/null 2>&1; then
  echo "LiveKit 信令可达"
else
  echo "[警告] LiveKit 未通过检查，请查看：docker compose -f deploy/docker-compose.yml logs livekit"
fi

cat <<EOF

部署完成：
  前端页面   http://${PUBLIC_HOST}:${FRONTEND_PORT}
  后端 API   http://${PUBLIC_HOST}:${BACKEND_PORT}/api
  MinIO API  http://${PUBLIC_HOST}:${MINIO_PORT}
  MinIO 控制台 http://${PUBLIC_HOST}:${MINIO_CONSOLE_PORT}
  LiveKit 信令 ws://${PUBLIC_HOST}:${LIVEKIT_PORT}

日常管理：bash deploy/manage.sh <start|stop|restart|recreate|logs|ps|pull|env-check>
停止服务：docker compose -f deploy/docker-compose.yml down
EOF

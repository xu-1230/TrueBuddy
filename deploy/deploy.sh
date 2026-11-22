#!/usr/bin/env bash
# TrueBuddy 空白主机一键部署（Linux / macOS / WSL）
# 前置：仅 Docker Engine + Docker Compose 插件；无需 Java / Node / Python / Maven / psql。
# 用法：cd <仓库根> && bash deploy/deploy.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT/deploy/docker-compose.yml"
ENV_FILE="$ROOT/.env"

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

set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

PUBLIC_HOST="${PUBLIC_HOST:-127.0.0.1}"
FRONTEND_PORT="${FRONTEND_PORT:-8080}"
BACKEND_PORT="${BACKEND_PORT:-8086}"
MINIO_PORT="${MINIO_PORT:-9000}"
MINIO_CONSOLE_PORT="${MINIO_CONSOLE_PORT:-9001}"
POSTGRES_USER="${POSTGRES_USER:-postgres}"

DC=(docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE")

echo "[1/5] 拉取基础镜像并构建应用镜像（首次执行较慢）..."
"${DC[@]}" build --pull

echo "[2/5] 启动全部服务..."
"${DC[@]}" up -d

echo "[3/5] 等待数据库就绪..."
DB_OK=0
for _ in $(seq 1 60); do
  if "${DC[@]}" exec -T db pg_isready -U "$POSTGRES_USER" -d tb >/dev/null 2>&1; then
    DB_OK=1
    break
  fi
  sleep 2
done
if [ "$DB_OK" -ne 1 ]; then
  echo "[错误] 数据库未在 120 秒内就绪，请执行：docker compose -f deploy/docker-compose.yml logs db" >&2
  exit 1
fi

echo "[4/5] 等待后端 API 就绪..."
API_OK=0
for _ in $(seq 1 90); do
  if "${DC[@]}" exec -T frontend wget -qO- "http://backend:8086/api/homework/task/list" 2>/dev/null \
      | grep -q '"code":0'; then
    API_OK=1
    break
  fi
  sleep 2
done
if [ "$API_OK" -ne 1 ]; then
  echo "[错误] 后端未在 180 秒内就绪，请执行：docker compose -f deploy/docker-compose.yml logs backend" >&2
  exit 1
fi

echo "[5/5] 检查 MinIO..."
if "${DC[@]}" exec -T frontend wget -qO- "http://minio:9000/minio/health/live" >/dev/null 2>&1; then
  echo "MinIO 健康"
else
  echo "[警告] MinIO 未通过健康检查，请查看：docker compose -f deploy/docker-compose.yml logs minio"
fi

cat <<EOF

部署完成：
  前端页面   http://${PUBLIC_HOST}:${FRONTEND_PORT}
  后端 API   http://${PUBLIC_HOST}:${BACKEND_PORT}/api
  MinIO API  http://${PUBLIC_HOST}:${MINIO_PORT}
  MinIO 控制台 http://${PUBLIC_HOST}:${MINIO_CONSOLE_PORT}

查看日志：docker compose -f deploy/docker-compose.yml logs -f backend
停止服务：docker compose -f deploy/docker-compose.yml down
EOF

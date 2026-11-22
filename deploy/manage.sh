#!/usr/bin/env bash
# TrueBuddy 日常管理脚本（借鉴 Supabase run.sh 的子命令形态）
# 用法：bash deploy/manage.sh <命令> [服务名...]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT/.env}"
COMPOSE_FILE="$ROOT/deploy/docker-compose.yml"

if [ ! -f "$ENV_FILE" ]; then
  echo "[错误] 根目录 .env 不存在，请先执行 deploy/deploy.sh。" >&2
  exit 1
fi

DC=(docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE")

get_env_value() {
  grep "^$1=" "$ENV_FILE" 2>/dev/null | head -n 1 | cut -d= -f2- || true
}

usage() {
  cat <<EOF
用法：bash deploy/manage.sh <命令> [服务名...]

命令：
  start      启动并等待健康检查（等价于 up -d --wait）
  stop       停止并移除容器，保留数据卷
  restart    重启全部或指定服务
  recreate   强制重建全部或指定服务（用于环境变量变更）
  logs       滚动查看全部或指定服务日志
  ps         查看服务状态
  pull       拉取服务镜像
  env-check  只列出仍为占位值的外部密钥键名（不输出值）
  help       显示本帮助

示例：
  bash deploy/manage.sh logs backend
  bash deploy/manage.sh recreate livekit backend frontend
EOF
}

cmd="${1:-help}"
shift || true

case "$cmd" in
  start)
    "${DC[@]}" up -d --wait --wait-timeout 300
    ;;
  stop)
    "${DC[@]}" down
    ;;
  restart)
    "${DC[@]}" restart "$@"
    ;;
  recreate)
    "${DC[@]}" up -d --force-recreate --wait --wait-timeout 300 "$@"
    ;;
  logs)
    "${DC[@]}" logs -f "$@"
    ;;
  ps)
    "${DC[@]}" ps
    ;;
  pull)
    "${DC[@]}" pull
    ;;
  env-check)
    for key in \
      SPRING_AI_OPENAI_API_KEY OPENAI_EMBEDDING_API_KEY \
      DOCMIND_ACCESS_KEY_ID DOCMIND_ACCESS_KEY_SECRET \
      DASHSCOPE_API_KEY SPRING_MAIL_USERNAME SPRING_MAIL_PASSWORD; do
      value="$(get_env_value "$key")"
      case "$value" in
        ''|change-me|change-me-please|postgres) echo "[占位] $key" ;;
      esac
    done
    echo "说明：以上仅为键名；本地数据库/MinIO/LiveKit 凭据由 deploy.sh 自动生成。"
    ;;
  help|-h|--help)
    usage
    ;;
  *)
    usage >&2
    exit 1
    ;;
esac

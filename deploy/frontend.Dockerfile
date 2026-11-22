# =============================================================================
# TrueBuddy 前端镜像：node 构建 -> nginx 托管
# 构建上下文：仓库根目录。命令示例：
#   docker build -f deploy/frontend.Dockerfile .
# /api 由 deploy/nginx.conf 反代到 compose 服务名 backend:8086
# =============================================================================

ARG REGISTRY_PREFIX=
ARG NPM_REGISTRY=https://registry.npmjs.org/

# -----------------------------------------------------------------------------
# Stage 1：构建
# -----------------------------------------------------------------------------
FROM ${REGISTRY_PREFIX}node:22-alpine AS builder

ARG NPM_REGISTRY
WORKDIR /app
RUN npm config set registry "$NPM_REGISTRY"

# 先拷清单，依赖不变时可命中缓存
COPY frontend/package.json ./
RUN npm install --no-audit --no-fund

COPY frontend/ ./
RUN npm run build

# -----------------------------------------------------------------------------
# Stage 2：运行
# -----------------------------------------------------------------------------
FROM ${REGISTRY_PREFIX}nginx:1.27-alpine

COPY --from=builder /app/dist /usr/share/nginx/html
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]

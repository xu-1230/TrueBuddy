# =============================================================================
# TrueBuddy 前端 —— 多阶段构建
#   Stage 1 (builder)：node:22-alpine 安装依赖并执行 vite build，产出 dist/
#   Stage 2 (runtime)：nginx:alpine 仅托管静态产物，镜像不含 node_modules
# 说明：/api 请求不在构建期处理，由 nginx 反代到宿主机后端（见 nginx.conf）
# =============================================================================

# 镜像仓库前缀：直连 Docker Hub 不通时（国内网络常见），改用镜像加速站。
# 例如 docker build --build-arg REGISTRY_PREFIX=docker.1ms.run/library .
# 若本机 Docker Desktop 已配置 registry-mirrors，可传空值走官方仓库。
ARG REGISTRY_PREFIX=docker.1ms.run/library

# -----------------------------------------------------------------------------
# Stage 1：构建
# -----------------------------------------------------------------------------
FROM ${REGISTRY_PREFIX}/node:22-alpine AS builder

# npm 镜像源（可选）：国内网络下用 npmmirror 可显著加速依赖安装
ARG NPM_REGISTRY=https://registry.npmmirror.com/
RUN npm config set registry "$NPM_REGISTRY"

WORKDIR /app

# 先只拷 package.json：依赖没变化时可直接命中 Docker 层缓存，避免每次全量 npm install
COPY package.json ./
RUN npm install --no-audit --no-fund

# 再拷源码（.dockerignore 已排除 node_modules / dist / 日志等）
COPY . .

RUN npm run build

# -----------------------------------------------------------------------------
# Stage 2：运行
# -----------------------------------------------------------------------------
FROM ${REGISTRY_PREFIX}/nginx:1.27-alpine

# 拷贝构建产物
COPY --from=builder /app/dist /usr/share/nginx/html

# 覆盖默认 server 配置
RUN rm -f /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]

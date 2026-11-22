# =============================================================================
# TrueBuddy 后端 + docx-mcp 同容器镜像
#   Stage 1: Maven 构建全部模块，产出 tb-server 可执行 jar
#   Stage 2: python:3.12-slim + OpenJDK 21，同时承载 Java 后端与 MCP stdio 子进程
# 说明：docx-mcp 必须和后端在同一进程树，不能拆成 sidecar（docId 依赖长连接会话）
# 构建上下文：仓库根目录。命令示例：
#   docker build -f deploy/backend.Dockerfile .
# =============================================================================

ARG REGISTRY_PREFIX=
ARG MAVEN_MIRROR_URL=
ARG PIP_INDEX_URL=

# -----------------------------------------------------------------------------
# Stage 1：构建
# -----------------------------------------------------------------------------
FROM ${REGISTRY_PREFIX}maven:3.9-eclipse-temurin-21 AS builder

ARG MAVEN_MIRROR_URL
RUN if [ -n "$MAVEN_MIRROR_URL" ]; then \
      mkdir -p /root/.m2 \
      && printf '%s\n' \
        '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">' \
        '<mirrors><mirror>' \
        '<id>deploy-mirror</id>' \
        "<url>$MAVEN_MIRROR_URL</url>" \
        '<mirrorOf>central</mirrorOf>' \
        '</mirror></mirrors>' \
        '</settings>' > /root/.m2/settings.xml; \
    fi

WORKDIR /workspace
COPY backend/ backend/
RUN mvn -B -ntp -f /workspace/backend/pom.xml clean package -DskipTests

# -----------------------------------------------------------------------------
# Stage 2：运行
# -----------------------------------------------------------------------------
FROM ${REGISTRY_PREFIX}python:3.12-slim

ARG PIP_INDEX_URL
RUN if [ -n "$PIP_INDEX_URL" ]; then pip config set global.index-url "$PIP_INDEX_URL"; fi

RUN apt-get update \
 && apt-get install -y --no-install-recommends openjdk-21-jre-headless \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# docx-mcp：用 pyproject.toml 安装运行时依赖，源码保留在 /app/docx-mcp
COPY docx-mcp/ /app/docx-mcp/
# 项目是 flat-layout 多顶层模块，不能 pip install 目录；直接按 pyproject 的依赖清单安装
# docx-mcp 源码使用 v1 的 mcp.server.fastmcp，v2 已改名，必须钉 <2（uv.lock 也是 v1）
RUN python -c "import tomllib,pathlib; d=tomllib.loads(pathlib.Path('/app/docx-mcp/pyproject.toml').read_text()); deps=['mcp[cli]>=1.21.1,<2' if x.startswith('mcp[cli]') else x for x in d['project']['dependencies']]; pathlib.Path('/tmp/requirements.txt').write_text('\n'.join(deps))" \
 && pip install --no-cache-dir -r /tmp/requirements.txt

# 后端可执行 jar（reactor 中只有一个 tb-server jar）
COPY --from=builder /workspace/backend/tb-server/target/tb-server-*.jar /app/tb-server.jar

# MCP stdio 配置：覆盖 jar 内 classpath 资源，改为容器内的 Linux 路径
COPY deploy/mcp-servers-config.json /app/config/mcp-servers-config.json

RUN mkdir -p /app/data/tb-doctemplate

ENV JAVA_OPTS="-Xms256m -Xmx1024m" \
    TB_DOCTEMPLATE_TEMP_DIR=/app/data/tb-doctemplate \
    SPRING_AI_MCP_CLIENT_STDIO_SERVERS_CONFIGURATION=file:/app/config/mcp-servers-config.json

EXPOSE 8086

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/tb-server.jar"]

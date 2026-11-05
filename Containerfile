# ============================================================
# Apex Scaffold - 多阶段构建镜像（微服务版）
# ============================================================
# 一个镜像只打包一个微服务（默认 apex-gateway，作为入口承载原单体 8080 职责）。
# 构建: podman build -f Containerfile --build-arg SERVICE_MODULE=apex-gateway -t apex-gateway:latest .
# 档位: BUILD_PROFILE = basic | standard | enterprise（默认 enterprise）
#   - basic/standard 沿用单体聚合包形态（§15.3），SERVICE_MODULE 取 apex-example；
#   - enterprise 按 §13.1 以 gateway + 各 service 镜像部署。
# 运行: podman run -p 8080:8080 \
#         -e SPRING_PROFILES_ACTIVE=prod,enterprise \
#         -e REDIS_HOST=... -e NACOS_SERVER_ADDR=... -e JWT_SECRET=... apex-gateway:latest
#       （业务服务另需 -e DB_HOST=... -e DB_PORT=3306 -e DB_NAME=apex_auth \
#         或 -e SPRING_DATASOURCE_URL=... 指向各自独立库）
# 详细说明见 docs/05-operations/deployment.md
# ============================================================

# ============================================================
# 1. Build Stage（编译 + 打包整个反应堆，按 SERVICE_MODULE 取产物）
# ============================================================
# 构建模式（BUILD_MODE）：
#   source（默认）— 容器内从源码构建。需要容器内可访问 Maven 仓库；依赖预下载
#                   （dependency:go-offline）在受限网络下可能长时间阻塞。
#   prebuilt      — 复用宿主机/CI 已构建的 <模块>/target/*.jar，容器只做运行镜像。
#                   适用于“先在 CI 里 mvnd verify，再打镜像”的流水线，构建最快且可离线。
#                   用法：podman build --build-arg BUILD_MODE=prebuilt ...
ARG BUILD_MODE=source

FROM docker.io/library/maven:3.9-eclipse-temurin-21 AS builder

# 构建档位：basic / standard / enterprise
ARG BUILD_PROFILE=enterprise
# 目标微服务模块（决定运行时镜像内置哪个 fat jar；默认网关）
ARG SERVICE_MODULE=apex-gateway
# 构建模式：source | prebuilt（见文件头说明）
ARG BUILD_MODE=source
# 项目版本（prebuilt 模式用于校验目标 JAR 是否存在）
ARG BUILD_VERSION=1.3.1

WORKDIR /app

# 构建工具：基础镜像已自带 Temurin 21 + Maven 3.9.x，直接使用，不再经 SDKMAN 二次下载。
# 原实现 `curl get.sdkman.io | bash && sdk install java ...` 依赖 api.sdkman.io 返回版本清单，
# 在受限网络/CI 代理下该接口返回 400（实测），会导致镜像构建在第一步即失败。
# 这里只安装构建所需的附加工具，并打印实际版本便于核对。
RUN apt-get update \
  && apt-get install -y --no-install-recommends curl zip unzip zsh \
  && rm -rf /var/lib/apt/lists/* \
  && java -version 2>&1 | head -1 \
  && mvn -v | head -1

RUN mkdir -p /usr/share/maven/ref && printf '%s\n' \
  '<?xml version="1.0" encoding="UTF-8"?>' \
  '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">' \
  '  <mirrors>' \
  '    <mirror>' \
  '      <id>aliyun</id>' \
  '      <mirrorOf>central</mirrorOf>' \
  '      <name>Aliyun Maven Mirror</name>' \
  '      <url>https://maven.aliyun.com/repository/public</url>' \
  '    </mirror>' \
  '  </mirrors>' \
  '</settings>' > /usr/share/maven/ref/settings.xml

# 先拷贝全部模块的 pom.xml，充分利用容器层缓存
COPY pom.xml .
COPY apex-common/pom.xml apex-common/
COPY apex-license-spring-boot-starter/pom.xml apex-license-spring-boot-starter/
COPY apex-license-tools/pom.xml apex-license-tools/
COPY apex-domain/pom.xml apex-domain/
COPY apex-infrastructure/pom.xml apex-infrastructure/
COPY apex-workflow/pom.xml apex-workflow/
COPY apex-system/pom.xml apex-system/
COPY apex-example/pom.xml apex-example/
COPY apex-generator/pom.xml apex-generator/
COPY apex-services-bom/pom.xml apex-services-bom/
COPY apex-api-contract/pom.xml apex-api-contract/
COPY apex-gateway/pom.xml apex-gateway/
COPY apex-workflow-service/pom.xml apex-workflow-service/
COPY apex-file/pom.xml apex-file/
COPY apex-file-service/pom.xml apex-file-service/
COPY apex-office/pom.xml apex-office/
COPY apex-office-service/pom.xml apex-office-service/
COPY apex-system-service/pom.xml apex-system-service/
COPY apex-auth/pom.xml apex-auth/
COPY apex-auth-service/pom.xml apex-auth-service/
# 新能力模块的 POM 必须在依赖预下载阶段可见。
COPY apex-job-spring-boot-starter/pom.xml apex-job-spring-boot-starter/
COPY apex-seata-spring-boot-starter/pom.xml apex-seata-spring-boot-starter/
COPY apex-job-service/pom.xml apex-job-service/
COPY apex-masking-service/pom.xml apex-masking-service/
# 根 reactor 全部模块的 POM 都必须可见，否则 dependency:go-offline 解析反应堆失败。
COPY apex-infrastructure-persistence/pom.xml apex-infrastructure-persistence/
COPY apex-infrastructure-cache/pom.xml apex-infrastructure-cache/
COPY apex-infrastructure-messaging/pom.xml apex-infrastructure-messaging/
COPY apex-infrastructure-remote/pom.xml apex-infrastructure-remote/
COPY apex-infrastructure-integration/pom.xml apex-infrastructure-integration/
COPY apex-storage-spring-boot-starter/pom.xml apex-storage-spring-boot-starter/
COPY apex-integration/pom.xml apex-integration/
COPY apex-integration-service/pom.xml apex-integration-service/
COPY apex-boot-all/pom.xml apex-boot-all/
COPY apex-tools-deployment/pom.xml apex-tools-deployment/

COPY .sdkmanrc .
COPY scripts/build/init-build-env.sh scripts/build/init-build-env.sh

# 预下载依赖（仅 source 模式；prebuilt 模式直接使用宿主产物，不联网、不编译）。
# 容器内使用基础镜像自带的 mvn（3.9.x），不依赖 mvnd/SDKMAN：
# 项目脚本 init-build-env.sh 会强制校验 mvnd 与固定 JDK，仅适用于开发机/CI 宿主机。
RUN if [ "${BUILD_MODE}" = "source" ]; then mvn -B -q dependency:go-offline; else echo "prebuilt 模式：跳过依赖预下载"; fi

# 下载 SkyWalking Java Agent（供运行时 -javaagent 接入，替代原 Zipkin/Brave）。
# 可选：BUNDLE_SKYWALKING_AGENT=false 时跳过下载与打包，运行阶段需同时设置
# SW_AGENT_ENABLED=false（默认 true 会要求探针存在）。在受限网络或不需要链路追踪时关闭。
# 下载或探针校验失败即中止构建，避免静默丢失链路追踪。
ARG SKYWALKING_AGENT_VERSION=9.7.0
ARG BUNDLE_SKYWALKING_AGENT=true
RUN if [ "${BUNDLE_SKYWALKING_AGENT}" = "true" ]; then \
      curl -fsSL "https://archive.apache.org/dist/skywalking/java-agent/${SKYWALKING_AGENT_VERSION}/apache-skywalking-java-agent-${SKYWALKING_AGENT_VERSION}.tgz" -o /tmp/skywalking-agent.tgz \
      && curl -fsSL "https://archive.apache.org/dist/skywalking/java-agent/${SKYWALKING_AGENT_VERSION}/apache-skywalking-java-agent-${SKYWALKING_AGENT_VERSION}.tgz.sha512" -o /tmp/skywalking-agent.sha512 \
      && printf '%s  /tmp/skywalking-agent.tgz\n' "$(cut -d ' ' -f 1 /tmp/skywalking-agent.sha512)" | sha512sum -c - \
      && tar -xzf /tmp/skywalking-agent.tgz -C /opt \
      && test -s /opt/skywalking-agent/skywalking-agent.jar \
      && cp /opt/skywalking-agent/optional-plugins/apm-spring-cloud-gateway-4.x-plugin-${SKYWALKING_AGENT_VERSION}.jar /opt/skywalking-agent/plugins/ \
      && cp /opt/skywalking-agent/optional-plugins/apm-spring-webflux-6.x-plugin-${SKYWALKING_AGENT_VERSION}.jar /opt/skywalking-agent/plugins/ \
      && rm -f /tmp/skywalking-agent.tgz /tmp/skywalking-agent.sha512; \
    else \
      mkdir -p /opt/skywalking-agent; \
      echo "BUNDLE_SKYWALKING_AGENT=false：镜像不内置 SkyWalking 探针，运行时请设置 SW_AGENT_ENABLED=false"; \
    fi

# 拷贝全部源码并构建（跳过测试；单元/集成测试由 CI 阶段执行）。
# 使用基础镜像自带 mvn；BUILD_PROFILE 通过 -P 传入，与根 POM 的 basic/standard/enterprise 对应。
# prebuilt 模式不重新编译：目标 JAR 已由 CI/宿主机构建，COPY 后直接进入运行阶段。
COPY . .
RUN if [ "${BUILD_MODE}" = "source" ]; then \
      mvn -B -DskipTests -P "${BUILD_PROFILE}" clean package; \
    else \
      test -f "${SERVICE_MODULE}/target/${SERVICE_MODULE}-${BUILD_VERSION}.jar" \
        || { echo "prebuilt 模式缺少 ${SERVICE_MODULE}/target/${SERVICE_MODULE}-${BUILD_VERSION}.jar，请先在宿主机执行 mvnd package" >&2; exit 1; }; \
    fi

# ============================================================
# 2. Runtime Stage（仅含 JRE + 选中微服务的可执行 jar）
# ============================================================
FROM docker.io/library/eclipse-temurin:21-jre-alpine

# 时区数据（alpine 精简镜像默认不含）
RUN apk add --no-cache tzdata

# 构建参数（注入运行元数据）
ARG BUILD_PROFILE=enterprise
ARG BUILD_VERSION=1.3.1
ARG SERVICE_MODULE=apex-gateway
# 是否内置 SkyWalking 探针（与 builder 阶段同名参数保持一致，用于运行时降级判断）
ARG BUNDLE_SKYWALKING_AGENT=true
# 业务端口（网关示例 8080；其余服务见 §5.1 端口规划）
ARG SERVICE_PORT=8080
# 管理端口（Actuator / Prometheus；网关复用业务端口，其余服务独立管理端口）
ARG MANAGEMENT_PORT=8080
# 健康检查端口：就绪探针目标（默认 8080）
ARG HEALTHCHECK_PORT=8080
ENV HEALTHCHECK_PORT="${HEALTHCHECK_PORT}"

# 创建非 root 用户
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# 拷贝目标微服务的可执行 fat jar（无 classifier，唯一 *.jar）
COPY --from=builder /app/${SERVICE_MODULE}/target/${SERVICE_MODULE}-*.jar /app/app.jar

# 拷贝已校验的 SkyWalking Java Agent
COPY --from=builder /opt/skywalking-agent /app/skywalking-agent

# 预创建运行时写目录并授权给非 root 用户（日志 LOG_PATH、本地存储 STORAGE_LOCAL_BASE_PATH）
RUN mkdir -p /app/logs /app/data/storage \
    && chown -R appuser:appgroup /app/logs /app/data/storage

# Agent 日志写入 appuser 已获授权的目录，避免默认探针目录不可写。
ENV SW_LOGGING_DIR="/app/logs/skywalking"

# 运行环境变量（均可被 podman run -e / compose environment 覆盖）
# JVM 生产级调优默认（与 compose 默认值保持一致，改这里需同步 podman-compose.yml）：
#   -Xms/-Xmx            堆内存 初始(最小)512m / 最大 1024m
#   -XX:+UseG1GC          G1 垃圾回收器；-XX:MaxGCPauseMillis=200 目标最大停顿
#   -XX:MetaspaceSize/-XX:MaxMetaspaceSize  元空间初始 256m / 上限 512m
#   -XX:+HeapDumpOnOutOfMemoryError          OOM 自动堆转储到 /app/logs
#   -XX:+ExitOnOutOfMemoryError              OOM 后退出进程，便于容器/编排自动重启
#   -Xlog:gc*:file=...                       滚动 GC 日志（5 份 × 20m，/app/logs 已预创建）
#   -Dfile.encoding=UTF-8 -Djava.security.egd=file:/dev/./urandom
# 链路追踪（SkyWalking Agent，以 -javaagent 方式接入，无编译期依赖）：
#   通过 SW_AGENT_ENABLED 开关（默认 true）；SW_AGENT_JAR 指定探针路径（镜像内已 COPY 到 /app/skywalking-agent）。
#   agent 依据 SW_AGENT_NAME 上报服务名，依据 SW_AGENT_COLLECTOR_BACKEND_SERVICES 连接 OAP（默认 skywalking-oap:11800），
#   日志通过 SkyWalking Toolkit Provider 输出 TID，再由 Filebeat 转为 tid；业务 traceId 独立保留，不依赖 MDC 自动注入。
ENV SW_AGENT_ENABLED="${SW_AGENT_ENABLED:-true}"
ENV SW_AGENT_JAR="${SW_AGENT_JAR:-/app/skywalking-agent/skywalking-agent.jar}"
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs/heapdump.hprof -XX:+ExitOnOutOfMemoryError -Xlog:gc*:file=/app/logs/gc.log:time,uptime,level,tags:filecount=5,filesize=20m -Dfile.encoding=UTF-8 -Djava.security.egd=file:/dev/./urandom"
# SkyWalking Agent 参数（仅当 SW_AGENT_ENABLED=true 时经 ENTRYPOINT 拼接到 -javaagent）
ENV SW_AGENT_NAME="${SERVICE_MODULE}"
ENV SW_AGENT_COLLECTOR_BACKEND_SERVICES="${SW_AGENT_COLLECTOR_BACKEND_SERVICES:-skywalking-oap:11800}"
ENV SPRING_PROFILES_ACTIVE="prod,${BUILD_PROFILE}"
ENV TZ="Asia/Shanghai"
ENV LOG_PATH="/app/logs"
# 仅独立管理端口可设置 MANAGEMENT_SERVER_ADDRESS；同端口服务沿用业务监听地址。
ENV APEX_BUILD_PROFILE="${BUILD_PROFILE}"
ENV APEX_BUILD_VERSION="${BUILD_VERSION}"
ENV APEX_SERVICE_MODULE="${SERVICE_MODULE}"

# 业务端口 + 管理端口（Actuator / Prometheus）
EXPOSE ${SERVICE_PORT} ${MANAGEMENT_PORT}

# 健康检查：依据 readiness HTTP 状态标记健康；重启策略由编排器配置。
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget -qO- http://127.0.0.1:${HEALTHCHECK_PORT}/actuator/health/readiness > /dev/null || exit 1

USER appuser

# 启用 Agent 时必须存在可读且非空的探针；显式设置 SW_AGENT_ENABLED=false 可关闭。
# 若镜像构建时 BUNDLE_SKYWALKING_AGENT=false（未内置探针），默认自动降级为不加载，
# 避免“默认 true + 探针缺失”导致容器启动即退出。
ENV BUNDLE_SKYWALKING_AGENT="${BUNDLE_SKYWALKING_AGENT}"
ENTRYPOINT ["sh", "-c", "AGENT_OPTS=''; if [ \"${SW_AGENT_ENABLED:-true}\" = \"true\" ]; then if [ ! -r \"${SW_AGENT_JAR}\" ] || [ ! -s \"${SW_AGENT_JAR}\" ]; then if [ \"${BUNDLE_SKYWALKING_AGENT:-true}\" = \"false\" ]; then echo 'SkyWalking Agent not bundled (BUNDLE_SKYWALKING_AGENT=false), running without agent' >&2; else echo 'SkyWalking Agent is enabled but its JAR is missing, unreadable or empty' >&2; exit 1; fi; else AGENT_OPTS=\"-javaagent:${SW_AGENT_JAR}\"; fi; fi; exec java $AGENT_OPTS $JAVA_OPTS -jar /app/app.jar"]
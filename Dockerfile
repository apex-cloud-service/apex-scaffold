# ============================================================
# Apex Scaffold - 多阶段构建镜像（微服务版）
# ============================================================
# 一个镜像只打包一个微服务（默认 apex-gateway，作为入口承载原单体 8080 职责）。
# 构建: docker build --build-arg SERVICE_MODULE=apex-gateway -t apex-gateway:latest .
# 档位: BUILD_PROFILE = basic | standard | enterprise（默认 enterprise）
#   - basic/standard 沿用单体聚合包形态（§15.3），SERVICE_MODULE 取 apex-example；
#   - enterprise 按 §13.1 以 gateway + 各 service 镜像部署。
# 运行: docker run -p 8080:8080 \
#         -e SPRING_PROFILES_ACTIVE=prod,enterprise \
#         -e REDIS_HOST=... -e NACOS_SERVER_ADDR=... -e JWT_SECRET=... apex-gateway:latest
#       （业务服务另需 -e DB_HOST=... -e DB_PORT=3306 -e DB_NAME=apex_auth \
#         或 -e SPRING_DATASOURCE_URL=... 指向各自独立库）
# 详细说明见 docs/05-operations/deployment.md
# ============================================================

# ============================================================
# 1. Build Stage（编译 + 打包整个反应堆，按 SERVICE_MODULE 取产物）
# ============================================================
FROM maven:3.9-eclipse-temurin-17 AS builder

# 构建档位：basic / standard / enterprise
ARG BUILD_PROFILE=enterprise
# 目标微服务模块（决定运行时镜像内置哪个 fat jar；默认网关）
ARG SERVICE_MODULE=apex-gateway

WORKDIR /app

# 安装项目固定版本的 Maven Daemon，并配置 Maven 镜像加速。
ENV SDKMAN_DIR=/root/.sdkman
RUN apt-get update \
  && apt-get install -y --no-install-recommends curl zip unzip \
  && rm -rf /var/lib/apt/lists/* \
  && curl -s "https://get.sdkman.io" | bash \
  && bash -c "source \"${SDKMAN_DIR}/bin/sdkman-init.sh\" && sdk install mvnd 2.0.0-rc-3"

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

# 先拷贝全部模块的 pom.xml，充分利用 Docker 层缓存
COPY pom.xml .
COPY apex-common/pom.xml apex-common/
COPY apex-license-spring-boot-starter/pom.xml apex-license-spring-boot-starter/
COPY apex-license-tools/pom.xml apex-license-tools/
COPY apex-domain/pom.xml apex-domain/
COPY apex-infrastructure/pom.xml apex-infrastructure/
COPY apex-application/pom.xml apex-application/
COPY apex-workflow/pom.xml apex-workflow/
COPY apex-interfaces/pom.xml apex-interfaces/
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

# 预下载依赖（仅 pom 变更时该层才失效）
RUN mvnd dependency:go-offline || true

# 下载 SkyWalking Java Agent（供运行时 -javaagent 接入，替代原 Zipkin/Brave）。
# 下载失败不阻断构建：目录仍会创建，运行时 ENTRYPOINT 检测不到探针即优雅降级为纯 java -jar。
ARG SKYWALKING_AGENT_VERSION=9.7.0
RUN mkdir -p /opt/skywalking-agent \
  && (curl -fsSL "https://archive.apache.org/dist/skywalking/${SKYWALKING_AGENT_VERSION}/apache-skywalking-java-agent-${SKYWALKING_AGENT_VERSION}.tgz" -o /tmp/skywalking-agent.tgz \
      && tar -xzf /tmp/skywalking-agent.tgz -C /opt \
      && rm -f /tmp/skywalking-agent.tgz \
      || echo "⚠️ SkyWalking Agent 下载失败，运行时将不启用 -javaagent")

# 拷贝全部源码并构建（跳过测试；单元/集成测试由 CI 阶段执行）
COPY . .
RUN mvnd clean package -DskipTests -P "${BUILD_PROFILE}"

# ============================================================
# 2. Runtime Stage（仅含 JRE + 选中微服务的可执行 jar）
# ============================================================
FROM eclipse-temurin:17-jre-alpine

# 时区数据（alpine 精简镜像默认不含）
RUN apk add --no-cache tzdata

# 构建参数（注入运行元数据）
ARG BUILD_PROFILE=enterprise
ARG BUILD_VERSION=1.3.1
ARG SERVICE_MODULE=apex-gateway
# 业务端口（网关示例 8080；其余服务见 §5.1 端口规划）
ARG SERVICE_PORT=8080
# 管理端口（Actuator / Prometheus；网关复用业务端口，其余服务独立管理端口）
ARG MANAGEMENT_PORT=8080
# 健康检查端口：就绪探针目标（默认 8080）
ARG HEALTHCHECK_PORT=8080

# 创建非 root 用户
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# 拷贝目标微服务的可执行 fat jar（无 classifier，唯一 *.jar）
COPY --from=builder /app/${SERVICE_MODULE}/target/${SERVICE_MODULE}-*.jar /app/app.jar

# 拷贝 SkyWalking Java Agent（builder 阶段已下载；若构建时下载失败则目录为空，ENTRYPOINT 优雅降级）
COPY --from=builder /opt/skywalking-agent /app/skywalking-agent

# 预创建运行时写目录并授权给非 root 用户（日志 APEX_LOG_DIR、本地存储 STORAGE_LOCAL_BASE_PATH）
RUN mkdir -p /app/logs /app/data/storage \
    && chown -R appuser:appgroup /app/logs /app/data/storage

# 运行环境变量（均可被 docker run -e / compose environment 覆盖）
# JVM 生产级调优默认（与 compose 默认值保持一致，改这里需同步 docker-compose.yml）：
#   -Xms/-Xmx            堆内存 初始(最小)512m / 最大 1024m
#   -XX:+UseG1GC          G1 垃圾回收器；-XX:MaxGCPauseMillis=200 目标最大停顿
#   -XX:MetaspaceSize/-XX:MaxMetaspaceSize  元空间初始 256m / 上限 512m
#   -XX:+HeapDumpOnOutOfMemoryError          OOM 自动堆转储到 /app/logs
#   -XX:+ExitOnOutOfMemoryError              OOM 后退出进程，便于容器/编排自动重启
#   -Xlog:gc*:file=...                       滚动 GC 日志（5 份 × 20m，/app/logs 已预创建）
#   -Dfile.encoding=UTF-8 -Djava.security.egd=file:/dev/./urandom
# 链路追踪（SkyWalking Agent，以 -javaagent 方式接入，无编译期依赖）：
#   通过 SW_AGENT_ENABLED 开关（默认 true）；SW_AGENT_JAR 指定探针路径（镜像内已 COPY 到 /app/skywalking-agent）。
#   agent 依据 SW_AGENT_NAME 上报服务名，依据 SW_AGENT_COLLECTOR_BACKEND_SERVICES 连接 OAP（默认 oap:11800），
#   并自动注入 MDC 键 tid / SW_CTX（供 logback %X{tid} 输出与链路关联）。
ENV SW_AGENT_ENABLED="${SW_AGENT_ENABLED:-true}"
ENV SW_AGENT_JAR="${SW_AGENT_JAR:-/app/skywalking-agent/skywalking-agent.jar}"
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs/heapdump.hprof -XX:+ExitOnOutOfMemoryError -Xlog:gc*:file=/app/logs/gc.log:time,uptime,level,tags:filecount=5,filesize=20m -Dfile.encoding=UTF-8 -Djava.security.egd=file:/dev/./urandom"
# SkyWalking Agent 参数（仅当 SW_AGENT_ENABLED=true 时经 ENTRYPOINT 拼接到 -javaagent）
ENV SW_AGENT_NAME="${SERVICE_MODULE}"
ENV SW_AGENT_COLLECTOR_BACKEND_SERVICES="${SW_AGENT_COLLECTOR_BACKEND_SERVICES:-oap:11800}"
ENV SPRING_PROFILES_ACTIVE="prod,${BUILD_PROFILE}"
ENV TZ="Asia/Shanghai"
# 管理端口监听 0.0.0.0，供 Prometheus / 探针访问（默认回环地址）
ENV MANAGEMENT_SERVER_ADDRESS="0.0.0.0"
ENV APEX_BUILD_PROFILE="${BUILD_PROFILE}"
ENV APEX_BUILD_VERSION="${BUILD_VERSION}"
ENV APEX_SERVICE_MODULE="${SERVICE_MODULE}"

# 业务端口 + 管理端口（Actuator / Prometheus）
EXPOSE ${SERVICE_PORT} ${MANAGEMENT_PORT}

# 健康检查：就绪探针（含 DB + Redis 组件状态），失败则重启容器
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget -qO- http://127.0.0.1:${HEALTHCHECK_PORT}/actuator/health/readiness | grep -q UP || exit 1

USER appuser

# 启动入口：当 SW_AGENT_ENABLED=true 且探针存在时，自动拼接 -javaagent 接入 SkyWalking；
# 否则退化为纯 java -jar（保证未挂载探针的部署形态仍可启动）。
ENTRYPOINT ["sh", "-c", "AGENT_OPTS=''; if [ \"${SW_AGENT_ENABLED:-true}\" = \"true\" ] && [ -f \"${SW_AGENT_JAR}\" ]; then AGENT_OPTS=\"-javaagent:${SW_AGENT_JAR}\"; fi; exec java $AGENT_OPTS $JAVA_OPTS -jar /app/app.jar"]
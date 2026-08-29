# ============================================================
# Apex Scaffold - 多阶段构建镜像
# ============================================================
# 构建: docker build --build-arg BUILD_PROFILE=enterprise -t apex-scaffold:latest .
# 档位: BUILD_PROFILE = basic | standard | enterprise（默认 enterprise）
# 运行: docker run -p 8080:8080 -p 8081:8081 \
#         -e SPRING_PROFILES_ACTIVE=prod,enterprise \
#         -e DB_MASTER_URL=... -e DB_MASTER_USERNAME=... -e DB_MASTER_PASSWORD=... \
#         -e REDIS_HOST=... -e JWT_SECRET=... apex-scaffold:latest
# 详细说明见 docs/DEPLOYMENT.md
# ============================================================

# ============================================================
# 1. Build Stage（编译 + 打包）
# ============================================================
FROM maven:3.9-eclipse-temurin-17 AS builder

# 构建档位：basic / standard / enterprise
ARG BUILD_PROFILE=enterprise

WORKDIR /app

# 配置 Maven 镜像加速（阿里云），可移除或替换为内网镜像
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
COPY apex-domain/pom.xml apex-domain/
COPY apex-infrastructure/pom.xml apex-infrastructure/
COPY apex-application/pom.xml apex-application/
COPY apex-interfaces/pom.xml apex-interfaces/
COPY apex-example/pom.xml apex-example/
COPY apex-generator/pom.xml apex-generator/
COPY apex-license-spring-boot-starter/pom.xml apex-license-spring-boot-starter/

# 预下载依赖（仅 pom 变更时该层才失效）
RUN mvn -B dependency:go-offline || true

# 拷贝全部源码并构建（跳过测试；单元/集成测试由 CI 阶段执行）
COPY . .
RUN mvn -B clean package -DskipTests -P "${BUILD_PROFILE}"

# ============================================================
# 2. Runtime Stage（仅含 JRE + 可执行 jar）
# ============================================================
FROM eclipse-temurin:17-jre-alpine

# 时区数据（alpine 精简镜像默认不含）
RUN apk add --no-cache tzdata

# 构建参数（注入运行元数据）
ARG BUILD_PROFILE=enterprise
ARG BUILD_VERSION=1.1.3

# 创建非 root 用户
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# 拷贝可执行 jar（classifier=executable，普通 jar 不拷贝）
COPY --from=builder /app/apex-interfaces/target/apex-interfaces-*-executable.jar /app/app.jar

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
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs/heapdump.hprof -XX:+ExitOnOutOfMemoryError -Xlog:gc*:file=/app/logs/gc.log:time,uptime,level,tags:filecount=5,filesize=20m -Dfile.encoding=UTF-8 -Djava.security.egd=file:/dev/./urandom"
ENV SPRING_PROFILES_ACTIVE="prod,${BUILD_PROFILE}"
ENV TZ="Asia/Shanghai"
# 管理端口监听 0.0.0.0，供 Prometheus / 探针访问（默认回环地址）
ENV MANAGEMENT_SERVER_ADDRESS="0.0.0.0"
ENV APEX_BUILD_PROFILE="${BUILD_PROFILE}"
ENV APEX_BUILD_VERSION="${BUILD_VERSION}"

# 业务端口 + 管理端口（Actuator / Prometheus）
EXPOSE 8080 8081

# 健康检查：就绪探针（含 DB + Redis 组件状态），失败则重启容器
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8081/actuator/health/readiness | grep -q UP || exit 1

USER appuser

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

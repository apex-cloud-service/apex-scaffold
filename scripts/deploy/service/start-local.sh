#!/bin/bash

# 启动脚本 - 使用 java -jar 方式在本地运行单个微服务
# 微服务模式下每个进程对应一个微服务模块（apex-gateway / apex-auth-service /
# apex-system-service / apex-workflow-service / apex-office-service / apex-file-service /
# apex-integration-service / apex-job-service / apex-masking-service），
# 由 APEX_SERVICE_MODULE 指定（默认 apex-system-service）。旧单体 apex-interfaces 可执行 jar 已退役。

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
cd "${PROJECT_ROOT}" || exit 1

# 微服务模块名（默认系统服务；可通过 APEX_SERVICE_MODULE 覆盖为其它微服务）
SERVICE_MODULE="${APEX_SERVICE_MODULE:-apex-system-service}"
LOG_DIR="${APEX_LOG_DIR:-${LOG_PATH:-${PROJECT_ROOT}/logs/${SERVICE_MODULE}}}"
export LOG_PATH="${LOG_DIR}"
export SW_LOGGING_DIR="${SW_LOGGING_DIR:-${LOG_DIR}/skywalking}"

# 设置环境变量（与 Podman Compose 配置保持一致）
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod,enterprise}"

# 数据库配置（指向 Podman Compose 中的 MySQL，按模块使用独立库）
# 微服务独立库：apex-auth-service -> apex_auth、apex-system-service -> apex_system、……；
# 网关为响应式服务，不直连数据库，跳过数据源注入。
if [ "${SERVICE_MODULE}" != "apex-gateway" ]; then
  DB_NAME="apex_$(echo "${SERVICE_MODULE}" | sed -e 's/^apex-//' -e 's/-service$//')"
  export DB_HOST="${DB_HOST:-localhost}"
  export DB_PORT="${DB_PORT:-3306}"
  export DB_NAME
  export DB_USERNAME="${DB_USERNAME:-root}"
  export DB_PASSWORD="${DB_PASSWORD:-apex-root}"
  export SPRING_DATASOURCE_URL="jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
  export SPRING_DATASOURCE_USERNAME="${DB_USERNAME}"
  export SPRING_DATASOURCE_PASSWORD="${DB_PASSWORD}"
fi

# Redis 配置
export REDIS_HOST="localhost"
export REDIS_PORT="6379"
export REDIS_PASSWORD="apex-redis-password"
export REDIS_DATABASE="0"

# RocketMQ 配置
export ROCKETMQ_NAMESERVER="localhost:9876"
export ROCKETMQ_NAMESRV_ADDR="localhost:9876"
export ROCKETMQ_PRODUCER_GROUP="apex-producer-group"

# JWT 密钥
export JWT_ACCESS_SECRET="ThisIsAVeryLongAccessTokenSecretKeyForHS256MustBeAtLeast32BytesLong"
export JWT_REFRESH_SECRET="ThisIsAVeryLongRefreshTokenSecretKeyForHS256MustBeAtLeast32BytesLong"

# JVM 参数
export JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=${LOG_DIR}/heapdump.hprof -XX:+ExitOnOutOfMemoryError -Xlog:gc*:file=${LOG_DIR}/gc.log:time,uptime,level,tags:filecount=5,filesize=20m -Dfile.encoding=UTF-8 -Djava.security.egd=file:/dev/./urandom"

# SkyWalking Agent（可选接入，替代原 Zipkin/Brave）：
#   SW_AGENT_ENABLED=true 时必须提供可读且非空的探针 jar；未启用时跳过。
#   JSON 日志由 SkyWalking Toolkit Provider 输出 TID，Filebeat 将其转为 tid；业务 traceId 独立保留。
export SW_AGENT_ENABLED="${SW_AGENT_ENABLED:-false}"
export SW_AGENT_JAR="${SW_AGENT_JAR:-${PROJECT_ROOT}/skywalking-agent/skywalking-agent.jar}"
export SW_AGENT_NAME="${SW_AGENT_NAME:-${SERVICE_MODULE}}"
export SW_AGENT_COLLECTOR_BACKEND_SERVICES="${SW_AGENT_COLLECTOR_BACKEND_SERVICES:-localhost:11800}"
if [ "${SW_AGENT_ENABLED}" = "true" ]; then
  if [ ! -f "${SW_AGENT_JAR}" ] || [ ! -r "${SW_AGENT_JAR}" ] || [ ! -s "${SW_AGENT_JAR}" ]; then
    echo "SkyWalking Agent 已启用，但探针 JAR 不存在、不可读或为空: ${SW_AGENT_JAR}" >&2
    exit 1
  fi
  export JAVA_OPTS="-javaagent:${SW_AGENT_JAR} ${JAVA_OPTS}"
  echo "🔗 已接入 SkyWalking Agent: ${SW_AGENT_NAME} -> ${SW_AGENT_COLLECTOR_BACKEND_SERVICES}"
fi

# 定位可执行 jar（模块 target 下，排除 sources 包）
if [ -n "${APEX_JAR:-}" ]; then
  JAR="${APEX_JAR}"
else
  JAR="$(ls "${PROJECT_ROOT}"/"${SERVICE_MODULE}"/target/"${SERVICE_MODULE}"-*.jar 2>/dev/null | grep -Ev -- '-sources\.jar|-javadoc\.jar|-test-support\.jar|-tests\.jar' | head -n 1)"
fi
if [ -z "${JAR:-}" ] || [ ! -f "${JAR}" ]; then
  echo "❌ 未找到可执行 JAR（模块 ${SERVICE_MODULE}）。请先构建: ./scripts/build/build.sh -Penterprise"
  echo "   或通过 APEX_JAR / APEX_SERVICE_MODULE 环境变量指定。"
  exit 1
fi

# 启动应用
mkdir -p "${LOG_DIR}" || exit 1
echo "🚀 启动 Apex Scaffold 微服务: ${SERVICE_MODULE}"
java $JAVA_OPTS -jar "${JAR}"

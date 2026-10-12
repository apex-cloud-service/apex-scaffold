#!/bin/bash

# 启动脚本 - 使用 java -jar 方式在本地运行单个微服务
# 微服务模式下每个进程对应一个微服务模块（apex-gateway / apex-auth-service /
# apex-system-service / apex-workflow-service / apex-office-service / apex-file-service），
# 由 APEX_SERVICE_MODULE 指定（默认 apex-gateway）。旧单体 apex-interfaces 可执行 jar 已退役。

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
cd "${PROJECT_ROOT}"

# 微服务模块名（默认网关；可通过 APEX_SERVICE_MODULE 覆盖为其它微服务，如 apex-system-service）
SERVICE_MODULE="${APEX_SERVICE_MODULE:-apex-gateway}"

# 设置环境变量（与 Docker Compose 配置保持一致）
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod,enterprise}"

# 数据库配置（指向 Docker Compose 中的 MySQL，按模块使用独立库）
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
export JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./logs/heapdump.hprof -XX:+ExitOnOutOfMemoryError -Xlog:gc*:file=./logs/gc.log:time,uptime,level,tags:filecount=5,filesize=20m -Dfile.encoding=UTF-8 -Djava.security.egd=file:/dev/./urandom"

# SkyWalking Agent（可选接入，替代原 Zipkin/Brave）：
#   SW_AGENT_ENABLED=true 且探针 jar 存在时，自动拼接 -javaagent；否则跳过（本地无探针也能启动）。
#   agent 自动注入 MDC 键 tid，供 logback %X{tid} 输出与链路关联。
export SW_AGENT_ENABLED="${SW_AGENT_ENABLED:-false}"
export SW_AGENT_JAR="${SW_AGENT_JAR:-${PROJECT_ROOT}/skywalking-agent/skywalking-agent.jar}"
export SW_AGENT_NAME="${SW_AGENT_NAME:-${SERVICE_MODULE}}"
export SW_AGENT_COLLECTOR_BACKEND_SERVICES="${SW_AGENT_COLLECTOR_BACKEND_SERVICES:-localhost:11800}"
if [ "${SW_AGENT_ENABLED}" = "true" ] && [ -f "${SW_AGENT_JAR}" ]; then
  export JAVA_OPTS="-javaagent:${SW_AGENT_JAR} ${JAVA_OPTS}"
  echo "🔗 已接入 SkyWalking Agent: ${SW_AGENT_NAME} -> ${SW_AGENT_COLLECTOR_BACKEND_SERVICES}"
fi

# 定位可执行 jar（模块 target 下，排除 sources 包）
if [ -n "${APEX_JAR:-}" ]; then
  JAR="${APEX_JAR}"
else
  JAR="$(ls "${PROJECT_ROOT}"/"${SERVICE_MODULE}"/target/"${SERVICE_MODULE}"-*.jar 2>/dev/null | grep -v -- '-sources.jar' | head -n 1)"
fi
if [ -z "${JAR:-}" ] || [ ! -f "${JAR}" ]; then
  echo "❌ 未找到可执行 JAR（模块 ${SERVICE_MODULE}）。请先构建: ./scripts/build/build.sh -Penterprise"
  echo "   或通过 APEX_JAR / APEX_SERVICE_MODULE 环境变量指定。"
  exit 1
fi

# 启动应用
echo "🚀 启动 Apex Scaffold 微服务: ${SERVICE_MODULE}"
java $JAVA_OPTS -jar "${JAR}"

#!/usr/bin/env bash
# ============================================================
# Apex Scaffold - 微服务 java -jar 部署守护脚本
# ============================================================
# 微服务模式下每个进程对应一个微服务模块（apex-gateway / apex-auth-service /
# apex-system-service / apex-workflow-service / apex-office-service / apex-file-service），
# 由 APEX_SERVICE_MODULE 指定；旧单体 apex-interfaces 可执行 jar 已随单体退役移除。
# 支持普通 java -jar 方式在各环境（dev/it/prod）后台运行、停止、
# 重启、查看状态与日志，无需 Podman / systemd。
#
# 用法:
#   ./app.sh start  [环境] [档位]     # 例: ./app.sh start prod enterprise
#   ./app.sh stop
#   ./app.sh restart [环境] [档位]    # 例: ./app.sh restart it standard
#   ./app.sh status
#   ./app.sh logs
#   ./app.sh console [环境] [档位]    # 前台运行（调试用，Ctrl+C 停止）
#
# 环境: dev | it | prod          （默认 dev）
# 档位: basic | standard | enterprise （默认 standard；决定商业功能范围）
# 实际激活 profile 为 "<环境>,<档位>"，例如 prod,enterprise。
#
# 可覆盖变量（export 或 .env 文件注入）:
#   APEX_SERVICE_MODULE  微服务模块名（默认 apex-gateway）；脚本据此定位
#                        <模块>/target/<模块>-*.jar 作为启动产物
#   APEX_JAR       可执行 jar 路径（默认按 APEX_SERVICE_MODULE 自动查找）
#   JAVA_OPTS      JVM 参数。默认已含完整调优集：堆内存(-Xms512m/-Xmx1024m)、G1 回收器、
#                  GC 停顿目标、元空间上下限、OOM 堆转储+退出、滚动 GC 日志(-Xlog:gc*)等；
#                  详见 docs/05-operations/deployment.md §4.5；GC 日志与堆转储落盘 ${LOG_DIR}
#   APEX_LOG_DIR   日志目录（默认 LOG_PATH 或 <项目根>/logs/<模块>；业务日志 + GC 日志 + 堆转储共用）
#   APP_PID_FILE   PID 文件（默认 <项目根>/logs/app.pid）
#   APP_OUT_LOG    标准输出日志（默认 <项目根>/logs/app.out.log）
#   SERVER_PORT    业务端口（默认按模块配置，网关 8080）
#   MANAGEMENT_PORT 管理端口（默认复用业务端口；system-service 默认 8083）
#   APEX_ENV_FILE  环境变量文件（可选，每行 KEY=VALUE，启动前 source）
#
# 业务环境变量（必须注入）:
#   DB_MASTER_URL / DB_MASTER_USERNAME / DB_MASTER_PASSWORD  主库（写）
#   DB_SLAVE_URL / DB_SLAVE_USERNAME / DB_SLAVE_PASSWORD     从库（读，prod 已启用，必须注入，可指向只读副本）
#   REDIS_HOST / REDIS_PORT / REDIS_PASSWORD                 Redis（prod 强制 REDIS_PASSWORD 非空）
#   JWT_SECRET                                              JWT 密钥（>=32 字节随机值）
#   无 Nacos 部署: NACOS_CONFIG_ENABLED=false NACOS_DISCOVERY_ENABLED=false \
#                  NACOS_REGISTER_ENABLED=false
#   RocketMQ: ROCKETMQ_NAMESERVER / ROCKETMQ_NAMESRV_ADDR / ROCKETMQ_PRODUCER_GROUP
#             ROCKETMQ_SEND_TIMEOUT / ROCKETMQ_ACCESS_KEY / ROCKETMQ_SECRET_KEY
#             APEX_MQ_CONSUMER_ENABLED / APEX_MQ_DLQ_ENABLED
#             APEX_MQ_DOMAIN_EVENT_ENABLED / APEX_MQ_DOMAIN_EVENT_TOPIC
#   MQ 幂等: MQ_IDEMPOTENT_TTL_MINUTES
#   Outbox: OUTBOX_BATCH_SIZE / OUTBOX_MAX_ATTEMPTS / OUTBOX_LOCK_TIMEOUT_SECONDS
#           OUTBOX_PARALLELISM / OUTBOX_RETENTION_ENABLED
#           OUTBOX_RETENTION_DELIVERED_HOURS / OUTBOX_RETENTION_DEAD_DAYS
#           OUTBOX_RETENTION_BATCH_SIZE
# ============================================================

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
cd "${PROJECT_ROOT}" || exit 1

# 在计算服务、日志和 JVM 默认值之前加载环境文件。
if [ -n "${APEX_ENV_FILE:-}" ] && [ -f "${APEX_ENV_FILE}" ]; then
  # shellcheck disable=SC1090
  set -a
  source "${APEX_ENV_FILE}"
  set +a
fi

# 微服务模块名（默认网关；可通过 APEX_SERVICE_MODULE 覆盖为其它微服务，如 apex-system-service）
SERVICE_MODULE="${APEX_SERVICE_MODULE:-apex-gateway}"

# ---------- 参数解析 ----------
ACTION="${1:-status}"
ENV_PROFILE="${2:-dev}"
TIER="${3:-standard}"

case "${ENV_PROFILE}" in
  dev|it|prod) ;;
  *) echo "❌ 环境参数必须是 dev / it / prod，当前: ${ENV_PROFILE}"; exit 1 ;;
esac
case "${TIER}" in
  basic|standard|enterprise) ;;
  *) echo "❌ 档位参数必须是 basic / standard / enterprise，当前: ${TIER}"; exit 1 ;;
esac

SPRING_PROFILES_ACTIVE="${ENV_PROFILE},${TIER}"

# ---------- 可覆盖变量默认值 ----------
# 日志根目录：logback 业务日志（APEX_LOG_DIR）+ GC 日志 + OOM 堆转储 共用同一目录
LOG_DIR="${APEX_LOG_DIR:-${LOG_PATH:-${PROJECT_ROOT}/logs/${SERVICE_MODULE}}}"
export LOG_PATH="${LOG_DIR}"
export SW_LOGGING_DIR="${SW_LOGGING_DIR:-${LOG_DIR}/skywalking}"
# 生产级 JVM 调优默认值（可用 JAVA_OPTS 整体覆盖，详见 docs/05-operations/deployment.md §4.5）：
#   -Xms/-Xmx            堆内存 初始(最小)512m / 最大 1024m
#   -XX:+UseG1GC          G1 垃圾回收器；-XX:MaxGCPauseMillis=200 目标最大停顿
#   -XX:MetaspaceSize/-XX:MaxMetaspaceSize  元空间初始 256m / 上限 512m
#   -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=...  OOM 自动堆转储
#   -XX:+ExitOnOutOfMemoryError    OOM 后退出进程，便于守护脚本/编排自动拉起
#   -Xlog:gc*:file=...    滚动 GC 日志（5 份 × 20m）
#   -Dfile.encoding=UTF-8 -Djava.security.egd=file:/dev/./urandom
JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=${LOG_DIR}/heapdump.hprof -XX:+ExitOnOutOfMemoryError -Xlog:gc*:file=${LOG_DIR}/gc.log:time,uptime,level,tags:filecount=5,filesize=20m -Dfile.encoding=UTF-8 -Djava.security.egd=file:/dev/./urandom}"

# ---------- SkyWalking Agent（可选接入，替代原 Zipkin/Brave） ----------
# SW_AGENT_ENABLED=true 时必须提供可读且非空的探针 jar；未启用时跳过。
# JSON 日志由 SkyWalking Toolkit Provider 输出 TID，Filebeat 将其转为 tid；业务 traceId 独立保留。
SW_AGENT_ENABLED="${SW_AGENT_ENABLED:-false}"
SW_AGENT_JAR="${SW_AGENT_JAR:-${PROJECT_ROOT}/skywalking-agent/skywalking-agent.jar}"
SW_AGENT_NAME="${SW_AGENT_NAME:-${SERVICE_MODULE}}"
SW_AGENT_COLLECTOR_BACKEND_SERVICES="${SW_AGENT_COLLECTOR_BACKEND_SERVICES:-localhost:11800}"
export SW_AGENT_ENABLED SW_AGENT_JAR SW_AGENT_NAME SW_AGENT_COLLECTOR_BACKEND_SERVICES
if [[ "${ACTION}" =~ ^(start|restart|console)$ ]] && [ "${SW_AGENT_ENABLED}" = "true" ]; then
  if [ ! -f "${SW_AGENT_JAR}" ] || [ ! -r "${SW_AGENT_JAR}" ] || [ ! -s "${SW_AGENT_JAR}" ]; then
    echo "SkyWalking Agent 已启用，但探针 JAR 不存在、不可读或为空: ${SW_AGENT_JAR}" >&2
    exit 1
  fi
  JAVA_OPTS="-javaagent:${SW_AGENT_JAR} ${JAVA_OPTS}"
fi
APP_PID_FILE="${APP_PID_FILE:-${PROJECT_ROOT}/logs/${SERVICE_MODULE}.pid}"
APP_OUT_LOG="${APP_OUT_LOG:-${PROJECT_ROOT}/logs/${SERVICE_MODULE}.out.log}"
case "${SERVICE_MODULE}" in
  apex-auth-service) DEFAULT_SERVER_PORT=8081 ;;
  apex-system-service) DEFAULT_SERVER_PORT=8082 ;;
  apex-workflow-service) DEFAULT_SERVER_PORT=8083 ;;
  apex-office-service) DEFAULT_SERVER_PORT=8084 ;;
  apex-file-service) DEFAULT_SERVER_PORT=8085 ;;
  apex-integration-service) DEFAULT_SERVER_PORT=8094 ;;
  apex-job-service) DEFAULT_SERVER_PORT=8095 ;;
  apex-masking-service) DEFAULT_SERVER_PORT=8096 ;;
  *) DEFAULT_SERVER_PORT=8080 ;;
esac
SERVER_PORT="${SERVER_PORT:-${DEFAULT_SERVER_PORT}}"
case "${SERVICE_MODULE}" in
  apex-system-service) DEFAULT_MANAGEMENT_PORT=8083 ;;
  apex-integration-service) DEFAULT_MANAGEMENT_PORT=8097 ;;
  *) DEFAULT_MANAGEMENT_PORT="${SERVER_PORT}" ;;
esac
MANAGEMENT_PORT="${MANAGEMENT_PORT:-${DEFAULT_MANAGEMENT_PORT}}"
SHUTDOWN_TIMEOUT_SECONDS="${APEX_SHUTDOWN_TIMEOUT_SECONDS:-90}"
APEX_GOVERNANCE_DEPLOYMENT_MODE="${APEX_GOVERNANCE_DEPLOYMENT_MODE:-standalone}"
DEFAULT_READINESS_PATH=/actuator/health/readiness
DEFAULT_READINESS_STATUS=200
case "${SERVICE_MODULE}" in
  apex-job-service)
    # 仅确认任务路由存活；上游权限和数据库就绪仍需独立验证。
    DEFAULT_READINESS_PATH=/admin-api/v1/jobs
    DEFAULT_READINESS_STATUS=401 ;;
  apex-masking-service) DEFAULT_READINESS_PATH=/actuator/health ;;
esac
READINESS_URL="${APEX_READINESS_URL:-http://127.0.0.1:${MANAGEMENT_PORT}${DEFAULT_READINESS_PATH}}"
READINESS_STATUS="${APEX_READINESS_STATUS:-${DEFAULT_READINESS_STATUS}}"

readiness_passed() {
  local status
  status="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "${READINESS_URL}" 2>/dev/null)" || return 1
  [ "${status}" = "${READINESS_STATUS}" ]
}

# ---------- 定位可执行 jar ----------
if [ -n "${APEX_JAR:-}" ]; then
  JAR="${APEX_JAR}"
else
  JAR=""
  for candidate in "${PROJECT_ROOT}/${SERVICE_MODULE}/target/${SERVICE_MODULE}"-*.jar; do
    [ -f "${candidate}" ] || continue
    case "${candidate}" in *-sources.jar|*-javadoc.jar) continue ;; esac
    JAR="${candidate}"
    break
  done
fi
if [[ "${ACTION}" =~ ^(start|restart|console)$ ]] && { [ -z "${JAR:-}" ] || [ ! -f "${JAR}" ]; }; then
  echo "❌ 未找到可执行 JAR（模块 ${SERVICE_MODULE}）。请先构建: ./scripts/build/build.sh -Penterprise"
  echo "   或通过 APEX_JAR / APEX_SERVICE_MODULE 环境变量指定。"
  exit 1
fi

# ---------- 工具函数 ----------
get_pid() {
  if [ -f "${APP_PID_FILE}" ]; then
    cat "${APP_PID_FILE}"
  fi
}

is_running() {
  local pid
  pid="$(get_pid)"
  [ -n "${pid}" ] && kill -0 "${pid}" 2>/dev/null
}

wait_stopped() {
  local pid=$1 timeout=$2 elapsed=0
  while kill -0 "${pid}" 2>/dev/null; do
    if [ "${elapsed}" -ge "${timeout}" ]; then
      echo "⏰ 等待超时(${timeout}s)，强制终止 PID ${pid}"
      kill -9 "${pid}" 2>/dev/null
      return 1
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done
  return 0
}

cmd_start() {
  if is_running; then
    echo "ℹ️  服务已在运行 (PID $(get_pid))，如需重启请执行: $0 restart ${ENV_PROFILE} ${TIER}"
    return 0
  fi
  mkdir -p "${LOG_DIR}" "$(dirname "${APP_PID_FILE}")" "$(dirname "${APP_OUT_LOG}")"

  echo "🚀 启动 Apex Scaffold 微服务: ${SERVICE_MODULE}"
  echo "   JAR        : ${JAR}"
  echo "   Profiles   : ${SPRING_PROFILES_ACTIVE}"
  echo "   PID 文件   : ${APP_PID_FILE}"
  echo "   输出日志   : ${APP_OUT_LOG}"
  echo "   GC 日志    : ${LOG_DIR}/gc.log（滚动 5×20m）"

  # 后台运行；显式传递治理模式、业务端口和管理端口，确保健康检查地址与进程监听一致
  SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE}" \
  APEX_GOVERNANCE_DEPLOYMENT_MODE="${APEX_GOVERNANCE_DEPLOYMENT_MODE:-standalone}" nohup \
    java ${JAVA_OPTS} -jar "${JAR}" \
    --server.port="${SERVER_PORT}" \
    --management.server.port="${MANAGEMENT_PORT:-8081}" \
    >> "${APP_OUT_LOG}" 2>&1 &
  echo $! > "${APP_PID_FILE}"

  # 等待管理端口 readiness 通过，等待上限与应用关停预算保持一致
  local waited=0
  echo -n "⏳ 等待服务就绪"
  while [ "${waited}" -lt "${SHUTDOWN_TIMEOUT_SECONDS}" ]; do
    if readiness_passed; then
      echo ""
      echo "✅ 服务已就绪: ${READINESS_URL}  (PID $(get_pid))"
      return 0
    fi
    sleep 2
    waited=$((waited + 2))
    echo -n "."
  done
  echo ""
  echo "⚠️  ${SHUTDOWN_TIMEOUT_SECONDS} 秒内 readiness 未通过，请查看日志: tail -f ${APP_OUT_LOG}"
  return 1
}

cmd_stop() {
  local pid
  pid="$(get_pid)"
  if [ -z "${pid}" ] || ! kill -0 "${pid}" 2>/dev/null; then
    echo "ℹ️  服务未在运行（无有效 PID）"
    rm -f "${APP_PID_FILE}"
    return 0
  fi
  echo "🛑 停止服务 (PID ${pid})，发送 SIGTERM 优雅停机..."
  kill -TERM "${pid}" 2>/dev/null
  if wait_stopped "${pid}" "${SHUTDOWN_TIMEOUT_SECONDS}"; then
    rm -f "${APP_PID_FILE}"
    echo "✅ 服务已停止"
    return 0
  fi
  rm -f "${APP_PID_FILE}"
  echo "❌ 服务在关停预算内未退出，已强制终止"
  return 1
}

cmd_restart() {
  cmd_stop
  sleep 2
  cmd_start
}

cmd_status() {
  local pid
  pid="$(get_pid)"
  if [ -n "${pid}" ] && kill -0 "${pid}" 2>/dev/null; then
    echo "✅ 服务运行中"
    echo "   PID      : ${pid}"
    echo "   Profiles : ${SPRING_PROFILES_ACTIVE}"
    echo "   JAR      : ${JAR}"
    if readiness_passed; then
      echo "   readiness : UP (${READINESS_URL})"
    else
      echo "   readiness : 启动中或异常（未通过 ${READINESS_URL}）"
    fi
  else
    echo "❌ 服务未运行"
    exit 1
  fi
}

cmd_logs() {
  local pid
  pid="$(get_pid)"
  if [ -z "${pid}" ] || ! kill -0 "${pid}" 2>/dev/null; then
    echo "ℹ️  服务未运行，显示历史日志: ${APP_OUT_LOG}"
    if [ -f "${APP_OUT_LOG}" ]; then
      tail -n 100 "${APP_OUT_LOG}"
    else
      echo "（无日志文件）"
    fi
    return 0
  fi
  echo "📄 业务日志目录: ${LOG_DIR}/（GC 日志 gc.log、OOM 堆转储 heapdump.hprof 同目录）"
  echo "📄 跟随输出日志: ${APP_OUT_LOG}（Ctrl+C 退出）"
  tail -f "${APP_OUT_LOG}"
}

cmd_console() {
  mkdir -p "${LOG_DIR}"
  echo "🚀 前台运行 (Profiles: ${SPRING_PROFILES_ACTIVE})，Ctrl+C 停止"
  echo "   JAR: ${JAR}"
  echo "   GC 日志: ${LOG_DIR}/gc.log"
  SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE}" \
  APEX_GOVERNANCE_DEPLOYMENT_MODE="${APEX_GOVERNANCE_DEPLOYMENT_MODE}" \
  exec java ${JAVA_OPTS} -jar "${JAR}" --server.port="${SERVER_PORT}" --management.server.port="${MANAGEMENT_PORT}"
}

case "${ACTION}" in
  start)   cmd_start ;;
  stop)    cmd_stop ;;
  restart) cmd_restart ;;
  status)  cmd_status ;;
  logs)    cmd_logs ;;
  console) cmd_console ;;
  *)
    echo "用法: $0 {start|stop|restart|status|logs|console} [dev|it|prod] [basic|standard|enterprise]"
    exit 1
    ;;
esac

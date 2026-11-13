#!/bin/bash
# 最小压测栈（复现用：需先完成 mvnd package；Redis 由 podman run -p 6379:6379 redis:7-alpine 提供）：workflow/office（H2 basic，HMAC JWT）+ aggregation（直连），无 MySQL/auth/system/网关
set -u
SECRET="test-secret-0123456789abcdef0123456789abcdef"
ROOT="/Users/work/apex-scaffold"
LOGDIR="/tmp/apex-perf-logs"
mkdir -p "$LOGDIR"

start() {
  local name="$1" jar="$2" port="$3"; shift 3
  if curl -fsS "http://127.0.0.1:${port}/actuator/health" >/dev/null 2>&1; then
    echo "SKIP ${name}（已在运行）"
    return 0
  fi
  echo "START ${name} port=${port}"
  env "$@" SERVER_PORT="${port}" SPRING_PROFILES_ACTIVE=basic \
      SECURITY_CONFIG_ENCRYPTION_KEY="01234567890123456789012345678901" \
      APEX_INTERNAL_AUTH_ENABLED=true APEX_INTERNAL_AUTH_SECRET="${SECRET}" \
      NACOS_CONFIG_ENABLED=false NACOS_DISCOVERY_ENABLED=false NACOS_REGISTER_ENABLED=false \
      nohup java -jar "${jar}" > "${LOGDIR}/${name}.log" 2>&1 &
  echo $! > "${LOGDIR}/${name}.pid"
}

start workflow-service "${ROOT}/apex-workflow-service/target/apex-workflow-service-1.3.1.jar" 8093 \
    WORKFLOW_JWT_HMAC_ENABLED=true WORKFLOW_JWT_SECRET="${SECRET}"

start office-service "${ROOT}/apex-office-service/target/apex-office-service-1.3.1.jar" 8084 \
    OFFICE_JWT_SECRET="${SECRET}"

start aggregation-service "${ROOT}/apex-aggregation-service/target/apex-aggregation-service-1.3.1.jar" 8098 \
    AGGREGATION_JWT_SECRET="${SECRET}" \
    APEX_TODO_WORKFLOW_BASE_URL="http://127.0.0.1:8093" \
    APEX_TODO_OFFICE_BASE_URL="http://127.0.0.1:8084"

for i in $(seq 1 60); do
  ok=0
  for p in 8093 8084 8098; do
    curl -fsS "http://127.0.0.1:${p}/actuator/health" >/dev/null 2>&1 && ok=$((ok+1))
  done
  echo "health-ok=${ok}/3"
  [ "$ok" = "3" ] && break
  sleep 5
done

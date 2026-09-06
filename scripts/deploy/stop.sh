#!/usr/bin/env bash
# ============================================================
# Apex Scaffold - Docker Compose 停止
# ============================================================
# 用法:
#   ./scripts/deploy/stop.sh                  # 停止全部服务（保留数据卷）
#   ./scripts/deploy/stop.sh -v               # 停止并删除数据卷（⚠️ 数据不可恢复）
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${PROJECT_ROOT}"

# Compose 停止超时应大于应用 90 秒总关停预算，并允许调用方覆盖
export APEX_GOVERNANCE_DEPLOYMENT_MODE="${APEX_GOVERNANCE_DEPLOYMENT_MODE:-compose}"
export APEX_GOVERNANCE_SHUTDOWN_TIMEOUT="${APEX_GOVERNANCE_SHUTDOWN_TIMEOUT:-90s}"
export APEX_GOVERNANCE_READINESS_WITHDRAW_TIMEOUT="${APEX_GOVERNANCE_READINESS_WITHDRAW_TIMEOUT:-5s}"
export APEX_GOVERNANCE_MQ_DRAIN_TIMEOUT="${APEX_GOVERNANCE_MQ_DRAIN_TIMEOUT:-30s}"
export APEX_GOVERNANCE_FORCE_SHUTDOWN="${APEX_GOVERNANCE_FORCE_SHUTDOWN:-true}"
export APEX_STOP_GRACE_PERIOD="${APEX_STOP_GRACE_PERIOD:-105s}"
COMPOSE_STOP_TIMEOUT="${APEX_COMPOSE_STOP_TIMEOUT:-105}"

echo "🛑 停止 Apex Scaffold 服务..."
docker compose down --timeout "${COMPOSE_STOP_TIMEOUT}" "$@"

echo "✅ 服务已停止"
echo "   💾 数据卷已保留（mysql-data / redis-data），如需再次启动: ./scripts/deploy/start.sh"

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

echo "🛑 停止 Apex Scaffold 服务..."
docker compose "$@" down

echo "✅ 服务已停止"
echo "   💾 数据卷已保留（mysql-data / redis-data），如需再次启动: ./scripts/deploy/start.sh"

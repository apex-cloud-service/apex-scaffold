#!/usr/bin/env bash
# Apex Scaffold - Podman Compose 一键启动
# 用法: ./scripts/deploy/service/start.sh [--profile rocketmq]
# 环境变量通过根目录 .env 配置；首次构建需要联网。
# macOS 首次使用先 podman machine init，再 podman machine start。
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
cd "${PROJECT_ROOT}"
EXTRA_ARGS=("$@")
export APEX_GOVERNANCE_DEPLOYMENT_MODE="${APEX_GOVERNANCE_DEPLOYMENT_MODE:-compose}"
export APEX_GOVERNANCE_SHUTDOWN_TIMEOUT="${APEX_GOVERNANCE_SHUTDOWN_TIMEOUT:-90s}"
export APEX_STOP_GRACE_PERIOD="${APEX_STOP_GRACE_PERIOD:-105s}"

echo "启动 Apex Scaffold（Podman Compose）..."
podman-compose -f "${PROJECT_ROOT}/podman-compose.yml" "${EXTRA_ARGS[@]}" up -d --build

echo "服务已启动，首次 MySQL 初始化约需 30~60s"
echo "查看状态: podman-compose -f podman-compose.yml ps"
echo "查看日志: podman-compose -f podman-compose.yml logs -f apex-gateway"
echo "业务 API: http://localhost:8080/doc.html"
echo "Nacos: http://localhost:8848/nacos"
echo "监控栈: podman-compose -f config/monitoring/podman-compose.yml up -d"

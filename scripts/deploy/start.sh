#!/usr/bin/env bash
# Apex Scaffold - Podman Compose 一键启动
# 用法: ./scripts/deploy/start.sh [--profile rocketmq] [--no-build] [服务名 ...]
# 环境变量通过根目录 .env 配置；首次构建需要联网。
# macOS 首次使用先 podman machine init，再 podman machine start。
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${PROJECT_ROOT}"
COMPOSE_ARGS=()
UP_ARGS=()
BUILD_ARGS=(--build)
while [[ $# -gt 0 ]]; do
  case "$1" in
    --profile|--env-file|-p|--project-name|--podman-run-args)
      COMPOSE_ARGS+=("$1" "${2:?缺少参数值}")
      shift 2
      ;;
    --dry-run)
      COMPOSE_ARGS+=("$1")
      shift
      ;;
    --no-build)
      BUILD_ARGS=(--no-build)
      shift
      ;;
    *)
      UP_ARGS+=("$1")
      shift
      ;;
  esac
done
export APEX_GOVERNANCE_DEPLOYMENT_MODE="${APEX_GOVERNANCE_DEPLOYMENT_MODE:-compose}"
export APEX_GOVERNANCE_SHUTDOWN_TIMEOUT="${APEX_GOVERNANCE_SHUTDOWN_TIMEOUT:-90s}"
export APEX_STOP_GRACE_PERIOD="${APEX_STOP_GRACE_PERIOD:-105s}"

echo "启动 Apex Scaffold（Podman Compose）..."
podman-compose -f "${PROJECT_ROOT}/podman-compose.yml" "${COMPOSE_ARGS[@]}" up -d "${BUILD_ARGS[@]}" "${UP_ARGS[@]}"

echo "已提交服务启动请求，请继续核对容器状态与健康端点"
echo "查看状态: podman-compose -f podman-compose.yml ps"
echo "查看日志: podman-compose -f podman-compose.yml logs -f apex-gateway"
echo "业务 API: http://localhost:8080/doc.html"
echo "Nacos: http://localhost:8848/nacos"
echo "监控栈: podman-compose -f config/monitoring/podman-compose.yml up -d"

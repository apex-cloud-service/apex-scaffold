#!/usr/bin/env bash
# 兼容原部署入口，统一使用微服务启动与 Agent 配置。
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
exec bash "${SCRIPT_DIR}/service/app.sh" "$@"

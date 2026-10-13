#!/usr/bin/env bash
# 部署分类入口，实际监控部署逻辑集中在上级脚本。
set -e
exec "$(dirname "$0")/../deploy-monitoring.sh" "$@"

#!/usr/bin/env bash
# 兼容部署分类入口，实际监控部署逻辑集中在 podman/ 分类目录。
set -e
exec "$(dirname "$0")/podman/deploy-monitoring.sh" "$@"

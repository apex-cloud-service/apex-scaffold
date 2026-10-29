#!/usr/bin/env bash
# 兼容部署分类入口，实际镜像拉取逻辑集中在 podman/ 分类目录。
set -e
exec "$(dirname "$0")/podman/pull-infra-images.sh" "$@"

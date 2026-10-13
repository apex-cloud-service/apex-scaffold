#!/usr/bin/env bash
# 兼容部署分类入口，实际拉取逻辑集中在上级脚本。
set -e
exec "$(dirname "$0")/../pull-infra-images.sh" "$@"

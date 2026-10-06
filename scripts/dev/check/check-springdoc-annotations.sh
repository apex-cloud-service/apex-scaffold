#!/bin/bash

###############################################################################
# check-springdoc-annotations.sh - 兼容入口：转发到 check-springdoc-annotations-v2.sh
# 用法：./scripts/dev/check/check-springdoc-annotations.sh  参数透传给 v2 脚本；本脚本仅打印警告后 exec
# 作者：Apex Team    创建：2026-10-01
###############################################################################

set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
V2_SCRIPT="${SCRIPT_DIR}/check-springdoc-annotations-v2.sh"

printf '%s\n' "警告：check-springdoc-annotations.sh 已兼容转发到 check-springdoc-annotations-v2.sh；JavaDoc 检查请使用 check-handler-javadoc.sh。" >&2
exec bash "$V2_SCRIPT" "$@"

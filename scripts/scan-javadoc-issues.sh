#!/usr/bin/env bash
# 保留旧路径，规范目录维护实际实现。
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)" || exit
exec bash "${SCRIPT_DIR}/dev/check/scan-javadoc-issues.sh" "$@"

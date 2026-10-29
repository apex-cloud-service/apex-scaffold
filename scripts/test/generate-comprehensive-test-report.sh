#!/usr/bin/env bash
# 综合入口复用同一 XML 汇总实现，避免两个脚本统计口径漂移。
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../lib/common.sh"
log_info "综合报告使用统一 Surefire/Failsafe/JaCoCo 统计口径"
exec bash "${SCRIPT_DIR}/generate-test-report.sh" "$@"

#!/usr/bin/env bash
# 保留旧入口；真实统计由统一报告工具维护。
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)" || exit
printf '%s\n' '弃用提示：scripts/office/generate-test-report.sh 已弃用，转发到 scripts/test/generate-test-report.sh。' \
    '仅汇总已有 XML，不执行测试；输出为全仓汇总（默认 target/reports/summary），不再生成办公平台固定通过报告。' >&2
exec bash "${SCRIPT_DIR}/../test/generate-test-report.sh" "$@"

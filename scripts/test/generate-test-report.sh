#!/usr/bin/env bash
# 生成测试报告脚本
#
# 统计口径：沿 POM <modules> 递归发现模块，分别解析 target/surefire-reports 与
# target/failsafe-reports 的 XML；不使用硬编码模块清单或单层目录 glob，也不以
# grep 匹配 XML 文本。解析与汇总由 apex-tools-deployment 的 TestReportSummary 实现
# （JDK 自带 XML 解析器，禁用 DOCTYPE 与外部实体）。
#
# 退出码：0 汇总成功（重复 testcase 仅告警）；1 报告被截断（去重后 testcase 数
# 与 tests 不符）、属性非法或 XML 解析失败；2 参数无效。
# 说明：本脚本只统计已存在的报告快照，不能据此断言整次 verify 通过。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
source "${SCRIPT_DIR}/../lib/common.sh"

REPORT_DIR="${PROJECT_ROOT}/target/reports"
SUMMARY_DIR="${REPORT_DIR}/summary"
TOOL_JAR="${PROJECT_ROOT}/apex-tools-parent/apex-tools-deployment/target/local-deployment.jar"

mkdir -p "$SUMMARY_DIR"

log_info "开始生成测试报告（源目录: ${PROJECT_ROOT}）"

if [ ! -f "$TOOL_JAR" ]; then
    log_error "未找到 ${TOOL_JAR}；请先完成该模块构建（例如 mvnd -pl :apex-tools-deployment -am package）"
    exit 1
fi

set +e
java -Dapex.root="${PROJECT_ROOT}" -jar "$TOOL_JAR" test-report \
    --root "${PROJECT_ROOT}" --output "${SUMMARY_DIR}"
RESULT=$?
set -e

if [ "$RESULT" -ne 0 ]; then
    log_error "测试报告汇总未通过（退出码 ${RESULT}）；见上方不一致项"
    exit "$RESULT"
fi

log_info "测试报告生成完成"
log_info "Markdown: ${SUMMARY_DIR}/test-report.md"
log_info "JSON: ${SUMMARY_DIR}/test-summary.json"
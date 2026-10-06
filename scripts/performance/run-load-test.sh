#!/bin/bash

###############################################################################
# run-load-test.sh - 运行 JMeter 负载测试并生成 HTML 报告
# 用法：./scripts/performance/run-load-test.sh  [测试计划.jmx]（默认 scripts/performance/jmeter/api-load-test.jmx）；需 JMETER_HOME 指向 JMeter 安装目录
# 作者：Apex Team    创建：2026-10-01
###############################################################################

set -e

JMETER_HOME="${JMETER_HOME:-/usr/local/Cellar/jmeter/5.6.3/libexec}"
TEST_PLAN="${1:-scripts/performance/jmeter/api-load-test.jmx}"
RESULTS_DIR="./reports/performance"

mkdir -p "$RESULTS_DIR"

if [ ! -f "$JMETER_HOME/bin/jmeter" ]; then
    echo "JMeter 未安装，请先安装: brew install jmeter"
    exit 1
fi

echo "运行 JMeter 负载测试..."
"$JMETER_HOME/bin/jmeter" \
    -n -t "$TEST_PLAN" \
    -l "$RESULTS_DIR/results.jtl" \
    -e -o "$RESULTS_DIR/html-report"

echo "性能测试报告: $RESULTS_DIR/html-report/index.html"

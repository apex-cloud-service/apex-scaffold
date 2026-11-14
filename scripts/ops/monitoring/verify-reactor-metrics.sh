#!/bin/bash

###############################################################################
# verify-reactor-metrics.sh - 验证应用的 Reactor Netty 指标暴露情况
#
# 用途：
#   在网关或服务启动后，校验 Actuator Prometheus 端点是否暴露 Reactor Netty
#   指标，并检查 Prometheus 是否已抓取对应序列。脚本默认检查本机网关
#   （http://localhost:8080），可通过 APP_URL 与 PROMETHEUS_URL 覆盖。
#
# 使用方法：
#   bash scripts/ops/monitoring/verify-reactor-metrics.sh
#   APP_URL=http://127.0.0.1:8080 bash scripts/ops/monitoring/verify-reactor-metrics.sh
#
# 环境变量：
#   APP_URL         - 被测应用地址（默认 http://localhost:8080）
#   PROMETHEUS_URL  - Prometheus 地址（默认 http://localhost:9090）
#   GRAFANA_URL     - Grafana 地址（默认 http://localhost:3200）
#
# 退出码：
#   0 - 应用健康，且已发现 Reactor Netty 指标
#   1 - 应用不可达、指标缺失或 Prometheus 查询失败
#
# 作者：Apex Team
# 创建：2026-09-07
###############################################################################
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=../../lib/common.sh
source "${SCRIPT_DIR}/../../lib/common.sh"

APP_URL="${APP_URL:-http://localhost:8080}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
GRAFANA_URL="${GRAFANA_URL:-http://localhost:3200}"

print_header "Reactor Netty 指标验证"

echo "1. 检查应用健康状态..."
if ! curl -s -f -m 10 "$APP_URL/actuator/health" >/dev/null 2>&1; then
    log_failure "应用未运行或不健康: $APP_URL"
    echo "   请先启动网关：mvnd spring-boot:run -pl :apex-gateway"
    exit 1
fi
log_success "应用运行正常: $APP_URL"

echo ""
echo "2. 检查 Reactor Netty 指标..."
METRICS_TEXT="$(curl -s -f -m 15 "$APP_URL/actuator/prometheus" 2>/dev/null || true)"
METRIC_COUNT="$(printf '%s\n' "$METRICS_TEXT" | grep -c '^reactor_netty' || true)"
METRIC_COUNT="$(clean_number "$METRIC_COUNT")"

if [[ "$METRIC_COUNT" -gt 0 ]]; then
    log_success "找到 $METRIC_COUNT 个 Reactor Netty 指标"
    echo ""
    echo "指标示例："
    printf '%s\n' "$METRICS_TEXT" | grep '^reactor_netty' | head -10
else
    log_failure "未找到 Reactor Netty 指标"
    echo "   可能原因："
    echo "   1. management.metrics.enable.reactor.netty 未启用"
    echo "   2. 当前实例不是 WebFlux/Reactor Netty 运行时"
    echo "   3. 应用未重启以加载配置"
    exit 1
fi

echo ""
echo "3. 检查 Prometheus 采集状态..."
if command -v jq >/dev/null 2>&1; then
    PROM_RESULT="$(curl -s -G --data-urlencode 'query=count(reactor_netty_eventloop_threads{application="apex-scaffold"})' \
        "$PROMETHEUS_URL/api/v1/query" 2>/dev/null || true)"
    PROM_COUNT="$(printf '%s' "$PROM_RESULT" | jq -r '.data.result[0].value[1] // "0"' 2>/dev/null || echo "0")"
    PROM_COUNT="$(clean_number "$PROM_COUNT")"
    if [[ "$PROM_COUNT" -gt 0 ]]; then
        log_success "Prometheus 已采集到 Reactor Netty 指标"
    else
        log_warn "Prometheus 尚未采集到指标（可能需要等待 15 秒或 Prometheus 未运行）"
    fi
else
    log_warn "SKIP: 未安装 jq，无法解析 Prometheus 查询结果"
fi

echo ""
echo "4. 关键指标值："
echo ""
echo "   EventLoop 线程数："
printf '%s\n' "$METRICS_TEXT" | grep 'reactor_netty_eventloop_threads{' | head -1 || echo "    （未暴露）"
echo ""
echo "   活跃连接数："
printf '%s\n' "$METRICS_TEXT" | grep 'reactor_netty_connection_provider_active_connections{' | head -1 || echo "    （未暴露）"
echo ""
echo "   ByteBuf Direct 内存："
printf '%s\n' "$METRICS_TEXT" | grep 'reactor_netty_bytebuf_allocator_used_direct_memory{' | head -1 || echo "    （未暴露）"

echo ""
echo "5. Grafana 面板访问："
echo -e "   ${COLOR_GREEN}${GRAFANA_URL}/d/apex-enhanced${COLOR_NC}"
echo "   默认账号：admin / admin（生产环境必须修改）"

echo ""
log_success "Reactor Netty 指标验证完成"

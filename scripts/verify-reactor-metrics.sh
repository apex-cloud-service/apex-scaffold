#!/bin/bash

###############################################################################
# verify-reactor-metrics.sh - 验证应用的 Reactor Netty 指标暴露情况
# 用法：./scripts/verify-reactor-metrics.sh  无参数；需应用已启动（默认 localhost:8081）
# 作者：Apex Team    创建：2026-09-07
###############################################################################
set -e

echo "=== Reactor Netty 指标验证脚本 ==="
echo ""

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "1. 检查应用健康状态..."
if curl -s -f http://localhost:8081/actuator/health > /dev/null 2>&1; then
    echo -e "${GREEN}✓ 应用运行正常${NC}"
else
    echo -e "${RED}✗ 应用未运行或不健康${NC}"
    echo "   请先启动应用：mvnd spring-boot:run -pl apex-system-service"
    exit 1
fi

echo ""

echo "2. 检查 Reactor Netty 指标..."
METRIC_COUNT=$(curl -s http://localhost:8081/actuator/prometheus | grep "^reactor_netty" | wc -l | tr -d ' ')

if [ "$METRIC_COUNT" -gt 0 ]; then
    echo -e "${GREEN}✓ 找到 $METRIC_COUNT 个 Reactor Netty 指标${NC}"
    echo ""
    echo "指标示例："
    curl -s http://localhost:8081/actuator/prometheus | grep "^reactor_netty" | head -10
else
    echo -e "${RED}✗ 未找到 Reactor Netty 指标${NC}"
    echo "   可能原因："
    echo "   1. application.yml 中未启用：management.metrics.enable.reactor.netty: true"
    echo "   2. 应用未重启以加载新配置"
    exit 1
fi

echo ""

echo "3. 检查 Prometheus 采集状态..."
PROM_RESULT=$(curl -s 'http://localhost:9090/api/v1/query?query=reactor_netty_eventloop_threads' | jq -r '.data.result | length')

if [ "$PROM_RESULT" -gt 0 ]; then
    echo -e "${GREEN}✓ Prometheus 已采集到 Reactor Netty 指标${NC}"
else
    echo -e "${YELLOW}⚠ Prometheus 尚未采集到指标（可能需要等待 15 秒）${NC}"
fi

echo ""

echo "4. 关键指标值："
echo ""

echo "   EventLoop 线程数："
curl -s http://localhost:8081/actuator/prometheus | grep "reactor_netty_eventloop_threads{" | head -1

echo ""
echo "   活跃连接数："
curl -s http://localhost:8081/actuator/prometheus | grep "reactor_netty_connection_provider_active_connections{" | head -1

echo ""
echo "   ByteBuf Direct 内存："
curl -s http://localhost:8081/actuator/prometheus | grep "reactor_netty_bytebuf_allocator_used_direct_memory{" | head -1

echo ""

echo "5. Grafana 面板访问："
echo -e "   ${GREEN}http://localhost:3000/d/apex-enhanced${NC}"
echo "   用户名：admin / 密码：admin"

echo ""
echo "=== 验证完成 ==="

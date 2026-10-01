#!/bin/bash

# 简化版环境检查脚本（无需认证）

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}=== 快速环境检查 ===${NC}\n"

# 1. 检查 Grafana
echo -n "Grafana (http://localhost:3000): "
if curl -s -f http://localhost:3000/api/health > /dev/null 2>&1; then
    echo -e "${GREEN}✓ 运行中${NC}"
else
    echo -e "${RED}✗ 未连接${NC}"
fi

# 2. 检查 Prometheus
echo -n "Prometheus (http://localhost:9090): "
if curl -s -f http://localhost:9090/api/v1/status/config > /dev/null 2>&1; then
    echo -e "${GREEN}✓ 运行中${NC}"
    VERSION=$(curl -s http://localhost:9090/api/v1/status/buildinfo 2>/dev/null | grep -o '"version":"[^"]*"' | cut -d'"' -f4)
    [ -n "$VERSION" ] && echo "  版本: $VERSION"
else
    echo -e "${RED}✗ 未连接${NC}"
fi

# 3. 检查 Prometheus targets
echo ""
echo "Prometheus 抓取目标:"
TARGETS=$(curl -s http://localhost:9090/api/v1/targets 2>/dev/null)
if [ -n "$TARGETS" ]; then
    echo "$TARGETS" | jq -r '.data.activeTargets[] | select(.labels.job | contains("apex")) | "  [\(.health == "up" and "✓" or "✗")] \(.labels.job) - \(.scrapeUrl)"' 2>/dev/null || \
    echo "  （请在 Prometheus UI 中查看）"
fi

# 4. 检查是否有缓存指标
echo ""
echo -n "缓存指标采集: "
QUERY_RESULT=$(curl -s "http://localhost:9090/api/v1/query?query=up{job=~\".*apex.*\"}" 2>/dev/null)
if echo "$QUERY_RESULT" | grep -q '"resultType":"vector"'; then
    echo -e "${GREEN}✓ 有数据${NC}"
else
    echo -e "${YELLOW}⚠ 无数据${NC}"
fi

echo ""
echo -e "${GREEN}=== 下一步 ===${NC}"
echo ""
echo "1. 如果尚未配置 Grafana Prometheus 数据源:"
echo "   - 访问 http://localhost:3000"
echo "   - Configuration → Data Sources → Add data source"
echo "   - 选择 Prometheus，URL 填写: http://localhost:9090"
echo ""
echo "2. 配置完成后，导入 Dashboard:"
echo "   ./scripts/grafana/import-dashboard.sh"
echo ""

#!/bin/bash

################################################################################
# Grafana Prometheus 数据源自动配置脚本
#
# 用途：
#   自动在 Grafana 中创建和配置 Prometheus 数据源
#   避免手动配置的繁琐步骤，确保配置的一致性和正确性
#
# 功能：
#   1. 检查现有数据源
#      - 查询 Grafana 已配置的所有数据源
#      - 检测是否已存在 Prometheus 数据源
#      - 提供交互式确认避免重复配置
#   2. 创建数据源
#      - 使用标准配置创建 Prometheus 数据源
#      - 设置为默认数据源
#      - 配置合理的查询参数（HTTP Method: POST, 时间间隔: 15s）
#   3. 测试连接
#      - 自动调用 Grafana API 测试数据源连接
#      - 验证 Prometheus 是否可访问
#      - 提供详细的错误诊断信息
#
# 使用方法：
#   # 使用 API Key 认证（推荐）
#   export GRAFANA_URL="http://localhost:3000"
#   export GRAFANA_API_KEY="your-api-key"
#   export PROMETHEUS_URL="http://localhost:9090"
#   ./configure-datasource.sh
#
#   # 使用用户名密码认证
#   export GRAFANA_URL="http://localhost:3000"
#   export GRAFANA_USER="admin"
#   export GRAFANA_PASSWORD="admin"
#   export PROMETHEUS_URL="http://localhost:9090"
#   ./configure-datasource.sh
#
# 环境变量：
#   GRAFANA_URL      - Grafana 服务地址（默认：http://localhost:3000）
#   PROMETHEUS_URL   - Prometheus 服务地址（默认：http://localhost:9090）
#   GRAFANA_API_KEY  - Grafana API Key（可选，优先使用）
#   GRAFANA_USER     - Grafana 用户名（默认：admin）
#   GRAFANA_PASSWORD - Grafana 密码（默认：admin）
#
# 数据源配置说明：
#   - Name: Prometheus
#   - Type: prometheus
#   - Access: proxy（通过 Grafana 服务器代理请求）
#   - URL: 用户指定的 Prometheus 地址
#   - isDefault: true（设置为默认数据源）
#   - HTTP Method: POST（推荐，性能更好）
#   - Scrape interval: 15s（Prometheus 默认抓取间隔）
#
# 依赖：
#   - curl: HTTP 请求工具
#   - jq: JSON 解析和生成工具
#   - base64: Base64 编码工具
#
# 退出码：
#   0 - 数据源配置成功
#   1 - 配置失败或连接错误
#
# 错误处理：
#   - 401: 认证失败，检查用户名密码或 API Key
#   - 409: 数据源已存在
#   - 其他: 显示详细错误信息
#
# 作者：Apex Scaffold Team
# 版本：1.0.0
################################################################################

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

GRAFANA_URL="${GRAFANA_URL:-http://localhost:3000}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
GRAFANA_USER="${GRAFANA_USER:-admin}"
GRAFANA_PASSWORD="${GRAFANA_PASSWORD:-admin}"
GRAFANA_API_KEY="${GRAFANA_API_KEY:-}"

echo -e "${GREEN}=== Grafana Prometheus 数据源配置工具 ===${NC}\n"

# 准备认证
if [ -n "$GRAFANA_API_KEY" ]; then
    AUTH_HEADER="Authorization: Bearer $GRAFANA_API_KEY"
else
    AUTH_HEADER="Authorization: Basic $(echo -n "${GRAFANA_USER}:${GRAFANA_PASSWORD}" | base64)"
fi

# 检查是否已存在 Prometheus 数据源
echo "检查现有数据源..."
DATASOURCES=$(curl -s -H "$AUTH_HEADER" "$GRAFANA_URL/api/datasources" 2>/dev/null)

PROM_DS=$(echo "$DATASOURCES" | grep -o '"type":"prometheus"' | wc -l)

if [ "$PROM_DS" -gt 0 ]; then
    echo -e "${YELLOW}⚠ 已存在 Prometheus 数据源${NC}"
    echo ""
    echo "现有数据源:"
    echo "$DATASOURCES" | jq -r '.[] | select(.type=="prometheus") | "  - \(.name) (\(.url))"' 2>/dev/null
    echo ""
    read -p "是否仍要添加新的数据源? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "操作已取消"
        exit 0
    fi
fi

# 创建数据源配置
echo ""
echo "创建 Prometheus 数据源配置..."

DATASOURCE_JSON=$(cat <<EOF
{
  "name": "Prometheus",
  "type": "prometheus",
  "access": "proxy",
  "url": "$PROMETHEUS_URL",
  "basicAuth": false,
  "isDefault": true,
  "jsonData": {
    "httpMethod": "POST",
    "timeInterval": "15s"
  }
}
EOF
)

# 添加数据源
echo "正在添加数据源..."
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -H "$AUTH_HEADER" \
    -d "$DATASOURCE_JSON" \
    "$GRAFANA_URL/api/datasources")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" = "200" ]; then
    echo -e "${GREEN}✓ 数据源添加成功！${NC}"
    echo ""
    
    # 提取数据源 ID
    DS_ID=$(echo "$BODY" | jq -r '.datasource.uid // .uid' 2>/dev/null)
    
    # 测试数据源
    echo "测试数据源连接..."
    sleep 2
    TEST_RESULT=$(curl -s -H "$AUTH_HEADER" "$GRAFANA_URL/api/datasources/uid/$DS_ID/health" 2>/dev/null)
    
    if echo "$TEST_RESULT" | grep -q '"status":"OK"'; then
        echo -e "${GREEN}✓ 数据源连接测试通过${NC}"
        echo ""
        echo -e "${GREEN}=== 配置完成 ===${NC}"
        echo ""
        echo "数据源信息:"
        echo "  名称: Prometheus"
        echo "  URL: $PROMETHEUS_URL"
        echo "  ID: $DS_ID"
        echo ""
        echo "下一步: 导入 Dashboard"
        echo "  ./scripts/grafana/import-dashboard.sh"
    else
        echo -e "${YELLOW}⚠ 数据源连接测试失败${NC}"
        echo "  错误: $(echo "$TEST_RESULT" | jq -r '.message // "Unknown error"' 2>/dev/null)"
        echo ""
        echo "  请检查 Prometheus URL 是否正确: $PROMETHEUS_URL"
    fi
    
elif [ "$HTTP_CODE" = "409" ]; then
    echo -e "${YELLOW}⚠ 数据源已存在${NC}"
    echo "  可以在 Grafana UI 中查看: Configuration → Data Sources"
    
else
    echo -e "${RED}✗ 添加数据源失败（HTTP $HTTP_CODE）${NC}"
    echo "响应内容:"
    echo "$BODY" | jq '.' 2>/dev/null || echo "$BODY"
    exit 1
fi

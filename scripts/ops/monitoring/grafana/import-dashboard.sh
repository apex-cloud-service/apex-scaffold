#!/bin/bash

################################################################################
# Grafana Dashboard 自动导入脚本
#
# 用途：
#   将 Apex Scaffold 主监控 Dashboard 自动导入到现有 Grafana 监控平台
#
# 功能：
#   - 自动检查 Grafana 连接状态
#   - 支持 API Key 和用户名密码两种认证方式
#   - 读取并验证 Dashboard JSON 配置文件
#   - 调用 Grafana API 导入 Dashboard
#   - 提供详细的错误诊断和成功反馈
#
# 使用方法：
#   1. 使用 API Key 认证（推荐）：
#      export GRAFANA_URL="http://localhost:3200"
#      export GRAFANA_API_KEY="your-api-key"
#      ./import-dashboard.sh
#
#   2. 使用用户名密码认证：
#      export GRAFANA_URL="http://localhost:3200"
#      export GRAFANA_USER="admin"
#      export GRAFANA_PASSWORD="admin"
#      ./import-dashboard.sh
#
# 环境变量：
#   GRAFANA_URL      - Grafana 服务地址（默认：http://localhost:3200）
#   GRAFANA_API_KEY  - Grafana API Key（可选，优先使用）
#   GRAFANA_USER     - Grafana 用户名（默认：admin）
#   GRAFANA_PASSWORD - Grafana 密码（默认：admin）
#
# 依赖：
#   - curl: HTTP 请求工具
#   - jq: JSON 解析工具
#   - base64: Base64 编码工具
#
# 退出码：
#   0 - 成功导入
#   1 - 导入失败或连接错误
#
# 作者：Apex Scaffold Team
# 版本：1.0.0
################################################################################

set -euo pipefail

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 配置参数（请根据实际情况修改）
GRAFANA_URL="${GRAFANA_URL:-http://localhost:3200}"
GRAFANA_API_KEY="${GRAFANA_API_KEY:-}"
GRAFANA_USER="${GRAFANA_USER:-admin}"
GRAFANA_PASSWORD="${GRAFANA_PASSWORD:-admin}"
DASHBOARD_FILE="$(cd -- "$(dirname -- "$0")/../../../.." && pwd -P)/config/monitoring/grafana/apex-dashboard.json"

echo -e "${GREEN}=== Grafana Dashboard 导入工具 ===${NC}\n"

# 检查 Dashboard 文件是否存在
if [ ! -f "$DASHBOARD_FILE" ]; then
    echo -e "${RED}错误：找不到 Dashboard 文件: $DASHBOARD_FILE${NC}"
    exit 1
fi

echo -e "Dashboard 文件: ${GREEN}$DASHBOARD_FILE${NC}"
echo -e "Grafana 地址: ${GREEN}$GRAFANA_URL${NC}\n"

# 检查 Grafana 是否可访问
echo "检查 Grafana 连接..."
if ! curl -s -f "${GRAFANA_URL}/api/health" > /dev/null 2>&1; then
    echo -e "${RED}错误：无法连接到 Grafana: $GRAFANA_URL${NC}"
    echo "请检查："
    echo "  1. Grafana 是否正在运行"
    echo "  2. URL 是否正确"
    echo "  3. 网络连接是否正常"
    exit 1
fi
echo -e "${GREEN}✓ Grafana 连接成功${NC}\n"

# 准备认证参数
AUTH_HEADER=""
if [ -n "$GRAFANA_API_KEY" ]; then
    echo "使用 API Key 认证"
    AUTH_HEADER="Authorization: Bearer $GRAFANA_API_KEY"
else
    echo "使用用户名密码认证"
    AUTH_HEADER="Authorization: Basic $(echo -n "${GRAFANA_USER}:${GRAFANA_PASSWORD}" | base64)"
fi

# 读取 Dashboard JSON
echo "读取 Dashboard 配置..."
DASHBOARD_JSON=$(cat "$DASHBOARD_FILE")

# 文件置备的大盘 JSON 本身就是 dashboard 对象，不再解包 .dashboard。
IMPORT_JSON="$(jq -cn --argjson dashboard "$DASHBOARD_JSON"   '{dashboard: $dashboard, overwrite: true, inputs: [], folderId: 0}')"

# 导入 Dashboard
echo "正在导入 Dashboard..."
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -H "$AUTH_HEADER" \
    -d "$IMPORT_JSON" \
    "${GRAFANA_URL}/api/dashboards/db")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" = "200" ]; then
    echo -e "${GREEN}✓ Dashboard 导入成功！${NC}\n"
    
    # 提取 Dashboard URL
    DASHBOARD_URL=$(echo "$BODY" | jq -r '.url // empty')
    if [ -n "$DASHBOARD_URL" ]; then
        FULL_URL="${GRAFANA_URL}${DASHBOARD_URL}"
        echo -e "Dashboard 地址: ${GREEN}$FULL_URL${NC}"
        echo -e "\n可以通过以下方式访问："
        echo -e "  浏览器: $FULL_URL"
        echo -e "  或在 Grafana 左侧菜单: Dashboards → Apex Scaffold 监控大盘"
    fi
    
    # 提取 Dashboard ID
    DASHBOARD_ID=$(echo "$BODY" | jq -r '.id // empty')
    if [ -n "$DASHBOARD_ID" ]; then
        echo -e "\nDashboard ID: $DASHBOARD_ID"
    fi
    
    echo -e "\n${GREEN}=== 导入完成 ===${NC}"
    echo "后续步骤："
    echo "  1. 访问 Dashboard URL 查看监控面板"
    echo "  2. 配置 Prometheus 数据源（如果尚未配置）"
    echo "  3. 配置告警通知渠道（钉钉/企业微信/Slack）"
    echo "  4. 根据实际需要调整告警阈值"
    
elif [ "$HTTP_CODE" = "401" ]; then
    echo -e "${RED}✗ 认证失败（401）${NC}"
    echo "请检查："
    echo "  1. GRAFANA_API_KEY 是否正确"
    echo "  2. 或者 GRAFANA_USER/GRAFANA_PASSWORD 是否正确"
    echo ""
    echo "创建 API Key 的方法："
    echo "  Grafana → Configuration → API Keys → Add API key"
    exit 1
    
elif [ "$HTTP_CODE" = "403" ]; then
    echo -e "${RED}✗ 权限不足（403）${NC}"
    echo "请确保当前用户/API Key 具有创建 Dashboard 的权限"
    exit 1
    
else
    echo -e "${RED}✗ 导入失败（HTTP $HTTP_CODE）${NC}"
    echo "响应内容："
    echo "$BODY" | jq '.' 2>/dev/null || echo "$BODY"
    exit 1
fi

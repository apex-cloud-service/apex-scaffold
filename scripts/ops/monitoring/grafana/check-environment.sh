#!/bin/bash

################################################################################
# Grafana 和 Prometheus 环境检查脚本
#
# 用途：
#   在导入 Dashboard 前，全面检查 Grafana、Prometheus 和应用的连接状态
#   确保所有依赖服务正常运行，数据源配置正确，指标采集正常
#
# 功能：
#   1. 服务连接检查
#      - Grafana 服务可用性和版本
#      - Prometheus 服务可用性和版本
#      - 应用健康状态检查
#   2. Grafana 数据源检查
#      - 验证 Prometheus 数据源是否已配置
#      - 测试数据源连接状态
#   3. 应用指标检查
#      - 检查 Prometheus 端点是否可访问
#      - 验证缓存相关指标是否暴露
#   4. Prometheus 采集配置检查
#      - 检查 Prometheus 抓取目标配置
#      - 验证指标是否已被 Prometheus 采集
#   5. 结果汇总和建议
#      - 提供通过/未通过的检查项统计
#      - 给出具体的修复建议
#
# 使用方法：
#   # 使用默认配置
#   ./check-environment.sh
#
#   # 自定义服务地址
#   export GRAFANA_URL="http://localhost:3000"
#   export PROMETHEUS_URL="http://localhost:9090"
#   export APP_URL="http://localhost:8080"
#   ./check-environment.sh
#
# 环境变量：
#   GRAFANA_URL      - Grafana 服务地址（默认：http://localhost:3000）
#   PROMETHEUS_URL   - Prometheus 服务地址（默认：http://localhost:9090）
#   APP_URL          - 应用服务地址（默认：http://localhost:8080）
#   GRAFANA_USER     - Grafana 用户名（默认：admin）
#   GRAFANA_PASSWORD - Grafana 密码（默认：admin）
#
# 依赖：
#   - curl: HTTP 请求工具
#   - grep: 文本搜索工具
#   - jq: JSON 解析工具（可选，用于美化输出）
#
# 退出码：
#   0 - 所有检查通过
#   1-5 - 失败的检查项数量
#
# 输出说明：
#   ✓ - 检查通过（绿色）
#   ✗ - 检查失败（红色）
#   ⚠ - 警告或部分通过（黄色）
#   ⊘ - 跳过检查（黄色）
#
# 作者：Apex Scaffold Team
# 版本：1.0.0
################################################################################

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置参数
GRAFANA_URL="${GRAFANA_URL:-http://localhost:3000}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
APP_URL="${APP_URL:-http://localhost:8080}"
GRAFANA_USER="${GRAFANA_USER:-admin}"
GRAFANA_PASSWORD="${GRAFANA_PASSWORD:-admin}"

echo -e "${BLUE}=== Grafana & Prometheus 环境检查 ===${NC}\n"

# 检查函数
check_service() {
    local name=$1
    local url=$2
    local endpoint=$3
    
    echo -n "检查 $name 连接 ($url$endpoint)... "
    
    if curl -s -f -m 5 "$url$endpoint" > /dev/null 2>&1; then
        echo -e "${GREEN}✓ 连接成功${NC}"
        return 0
    else
        echo -e "${RED}✗ 连接失败${NC}"
        return 1
    fi
}

# 检查结果汇总
GRAFANA_OK=0
PROMETHEUS_OK=0
APP_OK=0
DATASOURCE_OK=0
METRICS_OK=0

echo "=== 1. 检查服务连接 ==="
echo ""

# 检查 Grafana
if check_service "Grafana" "$GRAFANA_URL" "/api/health"; then
    GRAFANA_OK=1
    
    # 获取 Grafana 版本
    VERSION=$(curl -s "$GRAFANA_URL/api/health" 2>/dev/null | grep -o '"version":"[^"]*"' | cut -d'"' -f4)
    if [ -n "$VERSION" ]; then
        echo -e "  版本: ${GREEN}$VERSION${NC}"
    fi
else
    echo -e "  ${YELLOW}提示: 请检查 Grafana 是否正在运行${NC}"
    echo -e "  启动命令: ${BLUE}systemctl start grafana-server${NC} 或 ${BLUE}docker-compose up -d grafana${NC}"
fi

echo ""

# 检查 Prometheus
if check_service "Prometheus" "$PROMETHEUS_URL" "/api/v1/status/config"; then
    PROMETHEUS_OK=1
    
    # 获取 Prometheus 版本
    VERSION=$(curl -s "$PROMETHEUS_URL/api/v1/status/buildinfo" 2>/dev/null | grep -o '"version":"[^"]*"' | cut -d'"' -f4)
    if [ -n "$VERSION" ]; then
        echo -e "  版本: ${GREEN}$VERSION${NC}"
    fi
else
    echo -e "  ${YELLOW}提示: 请检查 Prometheus 是否正在运行${NC}"
    echo -e "  启动命令: ${BLUE}systemctl start prometheus${NC} 或 ${BLUE}docker-compose up -d prometheus${NC}"
fi

echo ""

# 检查应用
if check_service "应用 (Apex Scaffold)" "$APP_URL" "/actuator/health"; then
    APP_OK=1
else
    echo -e "  ${YELLOW}提示: 请检查应用是否正在运行${NC}"
    echo -e "  启动命令: ${BLUE}mvnd -pl apex-interfaces spring-boot:run${NC}"
fi

echo ""
echo "=== 2. 检查 Grafana 数据源 ==="
echo ""

if [ $GRAFANA_OK -eq 1 ]; then
    # 获取数据源列表
    AUTH_HEADER="Authorization: Basic $(echo -n "${GRAFANA_USER}:${GRAFANA_PASSWORD}" | base64)"
    DATASOURCES=$(curl -s -H "$AUTH_HEADER" "$GRAFANA_URL/api/datasources" 2>/dev/null)
    
    if [ -n "$DATASOURCES" ]; then
        # 检查是否有 Prometheus 数据源
        PROM_DS=$(echo "$DATASOURCES" | grep -o '"type":"prometheus"' | wc -l)
        
        if [ "$PROM_DS" -gt 0 ]; then
            echo -e "${GREEN}✓ 找到 Prometheus 数据源${NC}"
            DATASOURCE_OK=1
            
            # 显示数据源详情
            echo ""
            echo "已配置的 Prometheus 数据源:"
            echo "$DATASOURCES" | jq -r '.[] | select(.type=="prometheus") | "  - \(.name) (\(.url))"' 2>/dev/null || \
            echo "$DATASOURCES" | grep -A 5 '"type":"prometheus"' | grep '"name"' | cut -d'"' -f4 | sed 's/^/  - /'
            
            # 测试数据源连接
            echo ""
            DS_ID=$(echo "$DATASOURCES" | jq -r '.[] | select(.type=="prometheus") | .uid' 2>/dev/null | head -1)
            if [ -n "$DS_ID" ]; then
                echo -n "测试数据源连接... "
                TEST_RESULT=$(curl -s -H "$AUTH_HEADER" "$GRAFANA_URL/api/datasources/uid/$DS_ID/health" 2>/dev/null)
                if echo "$TEST_RESULT" | grep -q '"status":"OK"'; then
                    echo -e "${GREEN}✓ 连接正常${NC}"
                else
                    echo -e "${RED}✗ 连接失败${NC}"
                    echo "  错误: $(echo "$TEST_RESULT" | jq -r '.message // "Unknown error"' 2>/dev/null)"
                fi
            fi
        else
            echo -e "${RED}✗ 未找到 Prometheus 数据源${NC}"
            echo ""
            echo -e "${YELLOW}请手动配置 Prometheus 数据源:${NC}"
            echo "  1. 登录 Grafana: $GRAFANA_URL"
            echo "  2. Configuration → Data Sources → Add data source"
            echo "  3. 选择 Prometheus"
            echo "  4. 配置 URL: $PROMETHEUS_URL"
            echo "  5. 保存并测试"
        fi
    else
        echo -e "${RED}✗ 无法获取数据源列表${NC}"
        echo -e "  ${YELLOW}可能原因: 认证失败，请检查用户名密码${NC}"
    fi
else
    echo -e "${YELLOW}⊘ 跳过（Grafana 未连接）${NC}"
fi

echo ""
echo "=== 3. 检查应用指标采集 ==="
echo ""

if [ $APP_OK -eq 1 ]; then
    # 检查 Prometheus 端点
    echo -n "检查应用 Prometheus 端点... "
    if curl -s -f "$APP_URL/actuator/prometheus" > /dev/null 2>&1; then
        echo -e "${GREEN}✓ 端点正常${NC}"
        
        # 检查缓存相关指标
        echo ""
        echo "检查缓存指标:"
        METRICS=$(curl -s "$APP_URL/actuator/prometheus" 2>/dev/null | grep "^apex_cache" | head -20)
        
        if [ -n "$METRICS" ]; then
            echo -e "${GREEN}✓ 找到缓存指标${NC}"
            METRICS_OK=1
            
            # 统计指标数量
            METRIC_COUNT=$(echo "$METRICS" | wc -l)
            echo "  指标数量: $METRIC_COUNT"
            
            # 显示部分指标示例
            echo ""
            echo "  指标示例:"
            echo "$METRICS" | head -5 | sed 's/^/    /'
            if [ "$METRIC_COUNT" -gt 5 ]; then
                echo "    ..."
            fi
        else
            echo -e "${RED}✗ 未找到缓存指标${NC}"
            echo ""
            echo -e "${YELLOW}可能原因:${NC}"
            echo "  1. 缓存功能尚未使用（指标在首次使用后才会暴露）"
            echo "  2. Micrometer 配置问题"
            echo ""
            echo -e "${YELLOW}请检查 application.yml:${NC}"
            echo "  management:"
            echo "    endpoints:"
            echo "      web:"
            echo "        exposure:"
            echo "          include: prometheus"
            echo "    metrics:"
            echo "      export:"
            echo "        prometheus:"
            echo "          enabled: true"
        fi
    else
        echo -e "${RED}✗ 端点不可访问${NC}"
        echo ""
        echo -e "${YELLOW}请检查:${NC}"
        echo "  1. Spring Boot Actuator 是否启用"
        echo "  2. Prometheus 端点是否暴露"
    fi
else
    echo -e "${YELLOW}⊘ 跳过（应用未连接）${NC}"
fi

echo ""
echo "=== 4. 检查 Prometheus 采集配置 ==="
echo ""

if [ $PROMETHEUS_OK -eq 1 ]; then
    # 检查 Prometheus targets
    echo "检查 Prometheus 抓取目标:"
    TARGETS=$(curl -s "$PROMETHEUS_URL/api/v1/targets" 2>/dev/null)
    
    if [ -n "$TARGETS" ]; then
        # 查找 apex-scaffold 相关 target
        APEX_TARGET=$(echo "$TARGETS" | grep -i "apex-scaffold" || echo "")
        
        if [ -n "$APEX_TARGET" ]; then
            echo -e "${GREEN}✓ 找到 apex-scaffold 抓取目标${NC}"
            
            # 检查 target 状态
            TARGET_UP=$(echo "$TARGETS" | jq -r '.data.activeTargets[] | select(.labels.job | contains("apex")) | "\(.health) - \(.labels.job) (\(.labels.instance))"' 2>/dev/null)
            
            if [ -n "$TARGET_UP" ]; then
                echo ""
                echo "  目标状态:"
                echo "$TARGET_UP" | sed 's/^/    /'
            fi
        else
            echo -e "${YELLOW}⚠ 未找到 apex-scaffold 抓取目标${NC}"
            echo ""
            echo -e "${YELLOW}请在 prometheus.yml 中添加:${NC}"
            echo "  scrape_configs:"
            echo "    - job_name: 'apex-scaffold'"
            echo "      metrics_path: '/actuator/prometheus'"
            echo "      static_configs:"
            echo "        - targets: ['localhost:8080']"
            echo ""
            echo "  然后重启 Prometheus"
        fi
        
        # 检查是否有缓存指标
        if [ $METRICS_OK -eq 1 ]; then
            echo ""
            echo -n "检查 Prometheus 中的缓存指标... "
            QUERY_RESULT=$(curl -s "$PROMETHEUS_URL/api/v1/query?query=apex_cache_requests_total" 2>/dev/null)
            
            if echo "$QUERY_RESULT" | grep -q '"resultType":"vector"'; then
                echo -e "${GREEN}✓ 指标已采集${NC}"
            else
                echo -e "${YELLOW}⚠ 指标尚未采集${NC}"
                echo "  提示: 可能需要等待下一个抓取周期（默认 15秒）"
            fi
        fi
    else
        echo -e "${RED}✗ 无法获取 targets 信息${NC}"
    fi
else
    echo -e "${YELLOW}⊘ 跳过（Prometheus 未连接）${NC}"
fi

echo ""
echo "=== 检查结果汇总 ==="
echo ""

# 计算总分
TOTAL_CHECKS=5
PASSED_CHECKS=$((GRAFANA_OK + PROMETHEUS_OK + APP_OK + DATASOURCE_OK + METRICS_OK))

echo "通过检查: $PASSED_CHECKS / $TOTAL_CHECKS"
echo ""

# 详细结果
echo "  [$([ $GRAFANA_OK -eq 1 ] && echo -e "${GREEN}✓${NC}" || echo -e "${RED}✗${NC}")] Grafana 连接"
echo "  [$([ $PROMETHEUS_OK -eq 1 ] && echo -e "${GREEN}✓${NC}" || echo -e "${RED}✗${NC}")] Prometheus 连接"
echo "  [$([ $APP_OK -eq 1 ] && echo -e "${GREEN}✓${NC}" || echo -e "${RED}✗${NC}")] 应用连接"
echo "  [$([ $DATASOURCE_OK -eq 1 ] && echo -e "${GREEN}✓${NC}" || echo -e "${RED}✗${NC}")] Prometheus 数据源"
echo "  [$([ $METRICS_OK -eq 1 ] && echo -e "${GREEN}✓${NC}" || echo -e "${RED}✗${NC}")] 缓存指标采集"

echo ""

# 给出建议
if [ $PASSED_CHECKS -eq $TOTAL_CHECKS ]; then
    echo -e "${GREEN}=== ✓ 所有检查通过，可以导入 Dashboard ===${NC}"
    echo ""
    echo "执行导入命令:"
    echo -e "  ${BLUE}./scripts/grafana/import-dashboard.sh${NC}"
elif [ $PASSED_CHECKS -ge 3 ]; then
    echo -e "${YELLOW}=== ⚠ 部分检查未通过，建议修复后再导入 Dashboard ===${NC}"
    echo ""
    echo "查看详细说明:"
    echo -e "  ${BLUE}cat docs/cache/GRAFANA_IMPORT_GUIDE.md${NC}"
else
    echo -e "${RED}=== ✗ 多项检查失败，请先解决连接问题 ===${NC}"
    echo ""
    echo "常见问题排查:"
    echo "  1. 检查服务是否启动"
    echo "  2. 检查端口是否正确"
    echo "  3. 检查防火墙设置"
    echo "  4. 查看服务日志"
fi

echo ""
exit $((TOTAL_CHECKS - PASSED_CHECKS))

#!/bin/bash
# ============================================================
# 监控配置部署脚本
# ============================================================
# 用途: 验证并部署监控告警配置
# 使用: ./deploy_monitoring_configs.sh
# ============================================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}=========================================="
echo "监控配置部署脚本"
echo -e "==========================================${NC}"
echo ""

# 1. 检查配置文件
echo -e "${YELLOW}[1/5] 检查配置文件...${NC}"

if [ ! -f "config/monitoring/prometheus/alert-rules.yml" ]; then
    echo -e "${RED}❌ alert-rules.yml 不存在${NC}"
    exit 1
fi
echo -e "${GREEN}✅ alert-rules.yml 存在${NC}"

if [ ! -f "config/monitoring/prometheus/alertmanager.yml" ]; then
    echo -e "${RED}❌ alertmanager.yml 不存在${NC}"
    exit 1
fi
echo -e "${GREEN}✅ alertmanager.yml 存在${NC}"

if [ ! -f "config/monitoring/grafana/provisioning/datasources/zipkin.yml" ]; then
    echo -e "${RED}❌ zipkin.yml 不存在${NC}"
    exit 1
fi
echo -e "${GREEN}✅ zipkin.yml 存在${NC}"

echo ""

# 2. 验证YAML语法
echo -e "${YELLOW}[2/5] 验证YAML语法...${NC}"

# 检查是否安装yamllint
if command -v yamllint &> /dev/null; then
    yamllint config/monitoring/prometheus/alert-rules.yml
    yamllint config/monitoring/prometheus/alertmanager.yml
    yamllint config/monitoring/grafana/provisioning/datasources/zipkin.yml
    echo -e "${GREEN}✅ YAML语法正确${NC}"
else
    echo -e "${YELLOW}⚠️  yamllint未安装,跳过语法检查${NC}"
    echo "   安装: pip install yamllint"
fi

echo ""

# 3. 检查Docker服务
echo -e "${YELLOW}[3/5] 检查Docker服务...${NC}"

if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}❌ Docker未运行!${NC}"
    echo "请先启动Docker,然后重新运行此脚本"
    exit 1
fi
echo -e "${GREEN}✅ Docker运行正常${NC}"

echo ""

# 4. 启动监控服务
echo -e "${YELLOW}[4/5] 启动监控服务...${NC}"

cd config/monitoring

# 启动Prometheus
echo "启动Prometheus..."
docker-compose up -d prometheus
sleep 3

# 启动Alertmanager
echo "启动Alertmanager..."
docker-compose up -d alertmanager
sleep 3

# 启动Zipkin
echo "启动Zipkin..."
docker-compose up -d zipkin
sleep 5

# 启动Grafana
echo "启动Grafana..."
docker-compose up -d grafana
sleep 5

echo -e "${GREEN}✅ 所有服务已启动${NC}"

cd ../..
echo ""

# 5. 验证服务
echo -e "${YELLOW}[5/5] 验证服务状态...${NC}"

echo "1️⃣  Prometheus状态:"
if curl -s http://localhost:9090/-/healthy | grep -q "Prometheus is Healthy"; then
    echo -e "${GREEN}✅ Prometheus运行正常${NC}"
    echo "   访问: http://localhost:9090"
else
    echo -e "${RED}❌ Prometheus未就绪${NC}"
fi

echo ""
echo "2️⃣  Alertmanager状态:"
if curl -s http://localhost:9093/-/healthy &> /dev/null; then
    echo -e "${GREEN}✅ Alertmanager运行正常${NC}"
    echo "   访问: http://localhost:9093"
else
    echo -e "${RED}❌ Alertmanager未就绪${NC}"
fi

echo ""
echo "3️⃣  Zipkin状态:"
if curl -s http://localhost:9411/health | grep -q "UP"; then
    echo -e "${GREEN}✅ Zipkin运行正常${NC}"
    echo "   访问: http://localhost:9411"
else
    echo -e "${RED}❌ Zipkin未就绪${NC}"
fi

echo ""
echo "4️⃣  Grafana状态:"
if curl -s http://localhost:3000/api/health | grep -q "ok"; then
    echo -e "${GREEN}✅ Grafana运行正常${NC}"
    echo "   访问: http://localhost:3000 (admin/admin)"
else
    echo -e "${RED}❌ Grafana未就绪${NC}"
fi

echo ""
echo -e "${GREEN}=========================================="
echo "✅ 部署完成!"
echo -e "==========================================${NC}"
echo ""
echo "📊 服务访问地址:"
echo "  • Prometheus:    http://localhost:9090"
echo "  • Alertmanager:  http://localhost:9093"
echo "  • Zipkin:        http://localhost:9411"
echo "  • Grafana:       http://localhost:3000"
echo ""
echo "📝 下一步:"
echo "  1. 访问Grafana添加监控面板"
echo "  2. 参考文档: .trae/documents/后续任务完整实施方案.md"
echo "  3. 测试告警规则触发"
echo ""
echo "🔗 相关命令:"
echo "  # 查看告警规则"
echo "  curl http://localhost:9090/api/v1/rules | jq"
echo ""
echo "  # 查看活跃告警"
echo "  curl http://localhost:9090/api/v1/alerts | jq"
echo ""
echo "  # 重载Prometheus配置"
echo "  curl -X POST http://localhost:9090/-/reload"
echo ""
echo "  # 查看服务日志"
echo "  docker-compose -f config/monitoring/docker-compose.yml logs -f prometheus"
echo ""

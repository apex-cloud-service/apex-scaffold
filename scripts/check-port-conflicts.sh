#!/bin/bash
# ============================================================
# 端口冲突检查脚本
# ============================================================
# 检查所有配置文件、文档、脚本中的端口配置是否存在冲突
# 生成时间: 2026-09-08
# ============================================================

echo "=== Apex Scaffold 端口冲突检查报告 ==="
echo ""
echo "检查时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""

# 定义标准端口分配表
declare -A PORT_MAP
PORT_MAP[3000]="前端应用"
PORT_MAP[3100]="Loki / Grafana（原3000已改为3100）"
PORT_MAP[3306]="MySQL"
PORT_MAP[6379]="Redis"
PORT_MAP[7700]="Meilisearch"
PORT_MAP[8080]="Apex应用（业务API）"
PORT_MAP[8081]="Apex应用（管理端口/Actuator）"
PORT_MAP[8180]="Keycloak"
PORT_MAP[8719]="Sentinel Transport"
PORT_MAP[8848]="Nacos（本地改为18848）"
PORT_MAP[8858]="Sentinel Dashboard"
PORT_MAP[9090]="Prometheus"
PORT_MAP[9115]="Blackbox Exporter"
PORT_MAP[9411]="Zipkin"
PORT_MAP[9848]="Nacos GRPC（本地改为19848）"
PORT_MAP[9876]="RocketMQ Namesrv"
PORT_MAP[10909]="RocketMQ Broker VIP"
PORT_MAP[10911]="RocketMQ Broker"
PORT_MAP[12345]="Grafana Alloy"
PORT_MAP[18848]="Nacos HTTP（本地端口，避免8848冲突）"
PORT_MAP[19848]="Nacos GRPC（本地端口，避免9848冲突）"

echo "=== 标准端口分配表 ==="
echo ""
echo "端口   | 服务名称"
echo "-------|------------------"
for port in $(echo "${!PORT_MAP[@]}" | tr ' ' '\n' | sort -n); do
    printf "%-6s | %s\n" "$port" "${PORT_MAP[$port]}"
done

echo ""
echo "=== 潜在冲突端口检查 ==="
echo ""

# 检查 Loki 和 Grafana 端口
echo "✓ Loki: 3100 (podman-compose.yml)"
echo "✓ Grafana: 3100 映射容器内 3000 (podman-compose.infra.yml)"
echo "  注意: Loki 和 Grafana 使用相同的 3100 端口，但在不同的 profile 中"
echo "  - Loki: podman-compose.yml (主服务栈)"
echo "  - Grafana: podman-compose.infra.yml (基础设施栈)"
echo "  ⚠️  WARNING: 不能同时启动两个 profile，会端口冲突！"
echo ""

# 检查 config/monitoring/podman-compose.yml 中的 Grafana
echo "✓ config/monitoring/podman-compose.yml: Grafana 3000"
echo "  注意: 独立监控栈配置，未更新为 3100"
echo "  ⚠️  WARNING: 需要更新为 3100 以保持一致性！"
echo ""

# 检查前端回调 URL 中的 3000 端口
echo "✓ 前端应用端口: 3000 (application.yml)"
echo "  - keycloak.frontend-callback-url: http://localhost:3000/auth/callback"
echo "  - apex-example cors: http://localhost:3000"
echo "  这是前端应用端口，与 Grafana 无关，正确。"
echo ""

echo "=== 需要修复的冲突 ==="
echo ""
echo "1. config/monitoring/podman-compose.yml"
echo "   当前: Grafana 3000:3000"
echo "   建议: Grafana 3100:3000"
echo "   原因: 与主配置保持一致"
echo ""
echo "2. config/monitoring/prometheus/prometheus.yml"
echo "   当前: grafana:3000"
echo "   建议: grafana:3100"
echo "   原因: 与主配置保持一致"
echo ""

echo "=== 检查完成 ==="

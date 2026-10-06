#!/bin/bash

###############################################################################
# start-monitoring.sh - 启动监控栈（Prometheus/Grafana/AlertManager）
# 用法：./scripts/ops/monitoring/start-monitoring.sh  无参数；在 config/monitoring 目录执行 docker-compose up -d
# 作者：Apex Team    创建：2026-10-01
###############################################################################

set -e

cd "$(dirname "$0")/../config/monitoring"

echo "启动监控服务..."
docker-compose up -d

echo "等待服务启动..."
sleep 10

echo "监控服务已启动:"
echo "  Prometheus: http://localhost:9090"
echo "  Grafana: http://localhost:3000 (admin/admin)"
echo "  AlertManager: http://localhost:9093"

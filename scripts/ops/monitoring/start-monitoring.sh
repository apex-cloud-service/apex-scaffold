#!/bin/bash

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

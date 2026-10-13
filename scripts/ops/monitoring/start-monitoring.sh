#!/bin/bash
# start-monitoring.sh - 启动独立监控栈
# macOS: 首次 podman machine init，再 podman machine start。
set -e

PROJECT_ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
cd "${PROJECT_ROOT}/config/monitoring"
echo "启动监控服务..."
podman-compose -f podman-compose.yml up -d

echo "等待服务启动..."
sleep 10
echo "Prometheus: http://localhost:9090"
echo "Grafana: http://localhost:3200"

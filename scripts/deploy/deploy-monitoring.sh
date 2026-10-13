#!/usr/bin/env bash
# 验证并启动独立监控栈；macOS 需先 podman machine start。
set -e
PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "${PROJECT_ROOT}"
if ! podman info >/dev/null 2>&1; then
    echo "Podman 不可用。macOS 首次 podman machine init，再 podman machine start。"
    exit 1
fi
podman-compose -f config/monitoring/podman-compose.yml config >/dev/null
podman-compose -f config/monitoring/podman-compose.yml up -d

echo "监控服务已启动；就绪状态请检查各服务健康端点。"
echo "Prometheus: http://localhost:9090"
echo "Zipkin: http://localhost:9411"
echo "Grafana: http://localhost:3200"
echo "查看日志: podman-compose -f config/monitoring/podman-compose.yml logs -f"

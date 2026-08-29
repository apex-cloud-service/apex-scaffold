#!/usr/bin/env bash
# ============================================================
# Apex Scaffold - Docker Compose 一键启动
# ============================================================
# 用法:
#   ./scripts/deploy/start.sh                 # 启动默认栈（应用 + MySQL + Redis）
#   ./scripts/deploy/start.sh --profile rocketmq   # 额外启动 RocketMQ
# 说明:
#   - 首次构建镜像需要联网拉取 Maven 依赖（Dockerfile 内已配置阿里云镜像加速）
#   - 环境变量通过根目录 .env 配置（可 cp .env.example .env 后修改）
#   - JVM 参数: 默认使用镜像内置生产级调优参数（堆内存 -Xms512m/-Xmx1024m、G1 回收器、
#     GC 停顿目标、元空间上下限、OOM 堆转储+退出、滚动 GC 日志），可通过 .env 设置
#     JAVA_OPTS 整体覆盖（详见 docs/DEPLOYMENT.md §4.5）
#   - 监控栈（Prometheus/Grafana/Zipkin）另行启动:
#       docker compose -f config/monitoring/docker-compose.yml up -d
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${PROJECT_ROOT}"

EXTRA_ARGS=("$@")

echo "🚀 启动 Apex Scaffold（Docker Compose）..."
docker compose "${EXTRA_ARGS[@]}" up -d --build

echo ""
echo "✅ 服务已启动，等待就绪（首次启动 MySQL 初始化约需 30~60s）"
echo "   查看状态: docker compose ps"
echo "   查看日志: docker compose logs -f apex-scaffold"
echo ""
echo "📝 访问入口:"
echo "   API 文档   http://localhost:8080/doc.html"
echo "   健康检查   http://localhost:8080/api/v1/health"
echo "   Actuator   http://localhost:8081/actuator/health"
echo ""
echo "📊 监控栈（可选）: docker compose -f config/monitoring/docker-compose.yml up -d"
echo "   Grafana    http://localhost:3000"

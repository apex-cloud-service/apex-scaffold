#!/bin/bash
set -e

echo "启动 Apex Scaffold 服务..."

cd "$(dirname "$0")/../.."

docker-compose up -d

echo "✅ 服务已启动"
echo "📊 监控看板: http://localhost:3000"
echo "📝 API 文档: http://localhost:8080/doc.html"

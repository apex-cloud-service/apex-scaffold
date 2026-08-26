#!/bin/bash
set -e

echo "停止 Apex Scaffold 服务..."

cd "$(dirname "$0")/../.."

docker-compose down

echo "✅ 服务已停止"

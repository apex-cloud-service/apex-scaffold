#!/bin/bash

set -e

echo "运行 OWASP Dependency Check..."

if ! command -v dependency-check &> /dev/null; then
    echo "安装 OWASP Dependency Check..."
    brew install dependency-check || echo "请手动安装 dependency-check"
    exit 1
fi

dependency-check \
    --project "Apex Scaffold" \
    --scan . \
    --exclude "**/node_modules/**" \
    --exclude "**/target/**" \
    --out ./reports/dependency-check \
    --format HTML \
    --format JSON

echo "依赖检查报告: ./reports/dependency-check/dependency-check-report.html"

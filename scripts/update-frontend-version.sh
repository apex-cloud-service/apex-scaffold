#!/bin/bash
# ============================================================
# 前端版本号更新脚本
# ============================================================
# Purpose: 将前端 package.json 版本号从 v1.1.4 更新到 v1.2.0
# Usage: cd /Users/work/apex-admin-web && bash update-version.sh
# Date: 2026-09-17
# ============================================================

set -e

FRONTEND_DIR="/Users/work/apex-admin-web"
TARGET_VERSION="1.2.0"

echo "========================================"
echo "前端版本号更新脚本"
echo "========================================"

cd "$FRONTEND_DIR"

# 备份原文件
cp package.json package.json.backup

# 更新版本号
sed -i '' 's/"version": "1.1.4"/"version": "1.2.0"/' package.json

# 验证更新
CURRENT_VERSION=$(grep '"version":' package.json | head -1 | sed 's/.*"version": "\(.*\)".*/\1/')

if [ "$CURRENT_VERSION" = "$TARGET_VERSION" ]; then
    echo "✅ 版本号更新成功：v1.1.4 → v$TARGET_VERSION"
    rm -f package.json.backup
else
    echo "❌ 版本号更新失败，恢复备份"
    mv package.json.backup package.json
    exit 1
fi

echo "========================================"
echo "更新完成"
echo "========================================"

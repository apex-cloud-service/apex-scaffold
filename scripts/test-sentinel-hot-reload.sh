#!/bin/bash

# Sentinel 规则热更新测试脚本
# 用途：验证 Sentinel 规则文件监听是否正常工作

set -e

echo "========================================="
echo "Sentinel 规则热更新测试"
echo "========================================="

# 配置
RULES_FILE="${SENTINEL_RULES_FILE:-/etc/apex/sentinel-rules.json}"
API_ENDPOINT="${API_ENDPOINT:-http://localhost:8080/api/v1/users}"
LOG_FILE="${LOG_FILE:-logs/application.log}"

echo ""
echo "配置信息："
echo "  规则文件: $RULES_FILE"
echo "  API 端点: $API_ENDPOINT"
echo "  日志文件: $LOG_FILE"
echo ""

# 步骤 1: 检查规则文件是否存在
echo "[步骤 1] 检查规则文件..."
if [ ! -f "$RULES_FILE" ]; then
    echo "❌ 规则文件不存在: $RULES_FILE"
    echo "提示：请先创建规则文件或修改 SENTINEL_RULES_FILE 环境变量"
    exit 1
fi
echo "✅ 规则文件存在"

# 步骤 2: 备份原始规则文件
echo ""
echo "[步骤 2] 备份原始规则文件..."
cp "$RULES_FILE" "${RULES_FILE}.backup"
echo "✅ 已备份到: ${RULES_FILE}.backup"

# 步骤 3: 检查应用是否运行
echo ""
echo "[步骤 3] 检查应用是否运行..."
if ! curl -s -o /dev/null "$API_ENDPOINT"; then
    echo "❌ 应用未运行或 API 不可访问: $API_ENDPOINT"
    echo "提示：请先启动应用"
    # 恢复备份
    mv "${RULES_FILE}.backup" "$RULES_FILE"
    exit 1
fi
echo "✅ 应用正在运行"

# 步骤 4: 修改规则文件（添加注释触发文件变化）
echo ""
echo "[步骤 4] 修改规则文件..."
TIMESTAMP=$(date +%s)
# 使用 jq 添加一个注释字段（如果有 jq）
if command -v jq &> /dev/null; then
    jq ". + {\"_test_timestamp\": $TIMESTAMP}" "$RULES_FILE" > "${RULES_FILE}.tmp"
    mv "${RULES_FILE}.tmp" "$RULES_FILE"
    echo "✅ 规则文件已修改（添加测试时间戳: $TIMESTAMP）"
else
    # 如果没有 jq，直接 touch 文件
    touch "$RULES_FILE"
    echo "✅ 规则文件已触碰（更新修改时间）"
fi

# 步骤 5: 等待并检查日志
echo ""
echo "[步骤 5] 等待规则重载（最多等待 5 秒）..."
sleep 2

if [ -f "$LOG_FILE" ]; then
    # 检查是否有重载日志
    if tail -100 "$LOG_FILE" | grep -q "检测到 Sentinel 规则文件变化"; then
        echo "✅ 检测到规则文件变化事件"
        
        if tail -100 "$LOG_FILE" | grep -q "Sentinel 规则重载完成"; then
            echo "✅ 规则重载成功"
            
            # 显示重载详情
            echo ""
            echo "重载详情："
            tail -100 "$LOG_FILE" | grep "Sentinel 规则重载完成" | tail -1
        else
            echo "⚠️  未检测到规则重载完成日志"
        fi
    else
        echo "⚠️  未检测到规则文件变化事件"
        echo "提示：请检查 watch-enabled 配置是否启用"
    fi
else
    echo "⚠️  日志文件不存在: $LOG_FILE"
    echo "提示：请检查应用日志路径配置"
fi

# 步骤 6: 恢复原始规则文件
echo ""
echo "[步骤 6] 恢复原始规则文件..."
mv "${RULES_FILE}.backup" "$RULES_FILE"
echo "✅ 已恢复原始规则文件"

echo ""
echo "========================================="
echo "测试完成"
echo "========================================="
echo ""
echo "如果看到 '✅ 规则重载成功'，说明 Sentinel 规则热更新功能正常工作。"
echo "如果看到 '⚠️' 警告，请检查："
echo "  1. watch-enabled 配置是否为 true"
echo "  2. 应用日志中是否有 'Sentinel 规则文件监听已启动'"
echo "  3. 文件路径是否正确"
echo ""

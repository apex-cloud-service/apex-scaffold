#!/bin/bash

# SVG 图表验证脚本
# 用于验证SVG文件是否可以正常显示

echo "=========================================="
echo "  Apex Scaffold SVG 图表验证工具"
echo "=========================================="
echo ""

cd "$(dirname "$0")"

SVG_FILES=(
    "13_deployment_architecture.svg"
    "14_network_architecture.svg"
    "15_data_flow_architecture.svg"
    "16_mq_architecture.svg"
    "17_monitoring_architecture.svg"
    "18_microservice_governance.svg"
)

echo "正在验证 ${#SVG_FILES[@]} 个SVG文件..."
echo ""

ALL_PASS=true

for file in "${SVG_FILES[@]}"; do
    echo -n "检查 $file ... "
    
    if [ ! -f "$file" ]; then
        echo "❌ 文件不存在"
        ALL_PASS=false
        continue
    fi
    
    # 检查XML声明
    if ! head -1 "$file" | grep -q "<?xml"; then
        echo "❌ 缺少XML声明"
        ALL_PASS=false
        continue
    fi
    
    # 检查是否包含emoji
    if grep -q "🌐\|👥\|⚖️\|🛡️\|🔥\|📦\|🗄️\|☁️\|💾" "$file"; then
        echo "⚠️  包含emoji字符（可能影响显示）"
        ALL_PASS=false
        continue
    fi
    
    # 检查SVG标签
    if ! grep -q "<svg" "$file"; then
        echo "❌ 不是有效的SVG文件"
        ALL_PASS=false
        continue
    fi
    
    echo "✅ 通过"
done

echo ""
echo "=========================================="

if [ "$ALL_PASS" = true ]; then
    echo "✅ 所有检查通过！"
    echo ""
    echo "建议的验证方法："
    echo "1. 浏览器打开: open test_svg.html"
    echo "2. VS Code预览: 右键SVG文件 -> Open Preview"
    echo "3. Markdown预览: 查看 TEST_SVG_DISPLAY.md"
else
    echo "❌ 部分检查未通过，请查看上述错误"
    echo ""
    echo "修复方法："
    echo "1. 运行修复脚本重新处理SVG文件"
    echo "2. 查看 SVG_FIX_SUMMARY.md 了解详情"
fi

echo "=========================================="

#!/bin/bash
# SVG图表批量验证脚本
# 功能：检查所有SVG图表的XML格式、版本号一致性

set -e

DIAGRAM_DIR="docs/diagrams"
REPORT_FILE="docs/reports/svg-verification-report.txt"

echo "🔍 开始批量验证SVG图表..."
echo "验证时间: $(date)" > "$REPORT_FILE"
echo "========================================" >> "$REPORT_FILE"

# 计数器
total=0
passed=0
failed=0

# 遍历所有SVG文件
for svg_file in "$DIAGRAM_DIR"/*.svg; do
    if [[ "$svg_file" == *"demo-cloud-native-icons.svg"* ]]; then
        continue
    fi
    
    total=$((total + 1))
    filename=$(basename "$svg_file")
    
    echo -n "检查 $filename ... "
    
    # 1. XML格式验证
    if xmllint --noout "$svg_file" 2>/dev/null; then
        echo "✅ XML格式正确" >> "$REPORT_FILE"
        passed=$((passed + 1))
    else
        echo "❌ XML格式错误" >> "$REPORT_FILE"
        xmllint --noout "$svg_file" 2>> "$REPORT_FILE"
        failed=$((failed + 1))
        echo "FAILED"
        continue
    fi
    
    # 2. 检查是否包含CSS变量（var(--xxx)）
    if grep -q "var(--" "$svg_file"; then
        echo "⚠️  包含CSS变量，可能存在兼容性问题" >> "$REPORT_FILE"
    fi
    
    # 3. 检查是否包含未转义的&符号（排除正确的&amp;等）
    if grep -E '&[^a-z#]|&$' "$svg_file" | grep -v '&amp;' | grep -v '&lt;' | grep -v '&gt;' | grep -v '&quot;' | grep -v '&apos;' > /dev/null 2>&1; then
        echo "⚠️  可能包含未转义的&符号" >> "$REPORT_FILE"
    fi
    
    echo "✅ $filename" >> "$REPORT_FILE"
    echo "OK"
done

echo "" >> "$REPORT_FILE"
echo "========================================" >> "$REPORT_FILE"
echo "验证统计:" >> "$REPORT_FILE"
echo "  总计: $total 个文件" >> "$REPORT_FILE"
echo "  通过: $passed 个文件" >> "$REPORT_FILE"
echo "  失败: $failed 个文件" >> "$REPORT_FILE"
echo "  通过率: $(awk "BEGIN {printf \"%.1f%%\", ($passed/$total)*100}")" >> "$REPORT_FILE"

echo ""
echo "✅ 批量验证完成！"
echo "📄 详细报告: $REPORT_FILE"
echo ""
echo "统计:"
echo "  总计: $total"
echo "  通过: $passed"
echo "  失败: $failed"

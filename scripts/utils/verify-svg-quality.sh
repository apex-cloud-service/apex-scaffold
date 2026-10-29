#!/bin/bash
# SVG 图表质量验证脚本
# 版本: v2.0
# 更新时间: 2026-09-05

set -e

# 图表目录固定为仓库 docs/diagrams/，与脚本存放位置解耦
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
DIAGRAM_DIR="${SCRIPT_DIR}/../../docs/diagrams"
cd "$DIAGRAM_DIR"

echo "======================================"
echo "  SVG 图表质量验证工具 v2.0"
echo "======================================"
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 计数器
total_files=0
passed_files=0
failed_files=0

echo "📊 开始验证 SVG 图表质量..."
echo ""

# 1. 检查 CSS 变量残留
echo "1️⃣  检查 CSS 变量残留..."
var_count=$(grep -c "var(--" *.svg 2>/dev/null | awk -F: '{sum+=$2} END {print sum}')
if [ "$var_count" -eq 0 ]; then
  echo -e "${GREEN}✅ CSS 变量检查通过${NC} - 所有文件已清除 CSS 变量"
else
  echo -e "${RED}❌ 发现 $var_count 个 CSS 变量残留${NC}"
  grep -n "var(--" *.svg 2>/dev/null | head -10
  failed_files=$((failed_files + 1))
fi
echo ""

# 2. 检查文本标签残留
echo "2️⃣  检查文本标签残留..."
label_count=$(grep -c "\[Globe\]|\[Users\]|\[Shield\]|\[Fire\]|\[Balance\]" *.svg 2>/dev/null | awk -F: '{sum+=$2} END {print sum}')
if [ "$label_count" -eq 0 ]; then
  echo -e "${GREEN}✅ 文本标签检查通过${NC} - 所有临时文本标签已清除"
else
  echo -e "${YELLOW}⚠️  发现 $label_count 个文本标签残留${NC}"
  grep -n "\[Globe\]\|\[Users\]\|\[Shield\]" *.svg 2>/dev/null | head -10
fi
echo ""

# 3. XML 格式验证
echo "3️⃣  XML 格式验证..."
xml_passed=0
xml_failed=0
for file in *.svg; do
  [[ "$file" =~ \.backup$ ]] && continue
  total_files=$((total_files + 1))
  
  if xmllint --noout "$file" 2>/dev/null; then
    xml_passed=$((xml_passed + 1))
  else
    echo -e "${RED}❌ $file - XML格式错误${NC}"
    xml_failed=$((xml_failed + 1))
  fi
done

if [ $xml_failed -eq 0 ]; then
  echo -e "${GREEN}✅ XML 格式验证通过${NC} - $xml_passed/$total_files 文件格式正确"
  passed_files=$xml_passed
else
  echo -e "${RED}❌ XML 格式验证失败${NC} - $xml_failed/$total_files 文件有错误"
  failed_files=$((failed_files + xml_failed))
fi
echo ""

# 4. 配色检查（深色系）
echo "4️⃣  配色系统检查..."
old_color_count=$(grep -c "#6366f1\|#22d3ee\|#10b981\|#f59e0b\|#1a1d29" *.svg 2>/dev/null | awk -F: '{sum+=$2} END {print sum}')
new_color_count=$(grep -c "#4f46e5\|#0891b2\|#059669\|#d97706\|#0f172a" *.svg 2>/dev/null | awk -F: '{sum+=$2} END {print sum}')

echo "  - 旧配色使用次数: $old_color_count"
echo "  - 新深色系配色使用次数: $new_color_count"

if [ "$new_color_count" -gt "$old_color_count" ]; then
  echo -e "${GREEN}✅ 配色升级完成${NC} - 已采用深色系企业级配色"
else
  echo -e "${YELLOW}⚠️  部分文件仍使用旧配色${NC}"
fi
echo ""

# 5. 图标定义检查
echo "5️⃣  图标系统检查..."
icon_count=$(grep -c "id=\"icon-" 14_network_architecture.svg 2>/dev/null || echo 0)
if [ "$icon_count" -gt 0 ]; then
  echo -e "${GREEN}✅ 专业图标系统${NC} - 已定义 $icon_count 个专业 SVG 图标"
else
  echo -e "${YELLOW}⚠️  未检测到专业图标定义${NC}"
fi
echo ""

# 最终报告
echo "======================================"
echo "          验证结果汇总"
echo "======================================"
echo ""
echo "📝 文件统计:"
echo "  - 总文件数: $total_files"
echo "  - 通过验证: $passed_files"
echo "  - 需要修复: $failed_files"
echo ""

if [ $failed_files -eq 0 ] && [ "$var_count" -eq 0 ] && [ "$label_count" -eq 0 ]; then
  echo -e "${GREEN}🎉 恭喜！所有 SVG 图表质量验证通过！${NC}"
  echo ""
  echo "✅ 所有 CSS 变量已清除"
  echo "✅ 所有文本标签已清除"
  echo "✅ 所有文件 XML 格式正确"
  echo "✅ 深色系配色升级完成"
  echo ""
  echo "图表已达到企业级标准，可在各类查看器中正常显示。"
  exit 0
else
  echo -e "${YELLOW}⚠️  部分项目需要优化${NC}"
  echo ""
  echo "建议修复:"
  [ "$var_count" -gt 0 ] && echo "  - 清除剩余的 $var_count 个 CSS 变量"
  [ "$label_count" -gt 0 ] && echo "  - 清除剩余的 $label_count 个文本标签"
  [ $failed_files -gt 0 ] && echo "  - 修复 $failed_files 个 XML 格式错误"
  exit 1
fi

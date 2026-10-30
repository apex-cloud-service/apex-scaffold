#!/bin/bash
# ============================================================
# JavaDoc 质量检查脚本
# ============================================================
# 用途: 扫描Java文件,识别缺少JavaDoc的类和方法
# 使用: ./check_javadoc.sh [module_path]
# 示例: ./check_javadoc.sh apex-system/src/main/java
# ============================================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 默认扫描路径
SCAN_PATH="${1:-apex-system/src/main/java}"

echo "=========================================="
echo "JavaDoc 质量检查工具"
echo "=========================================="
echo "扫描路径: $SCAN_PATH"
echo ""

# 统计变量
TOTAL_CLASSES=0
CLASSES_WITHOUT_DOC=0
TOTAL_PUBLIC_METHODS=0
METHODS_WITHOUT_DOC=0

# 临时文件
TMP_CLASSES="/tmp/javadoc_check_classes_$$.txt"
TMP_METHODS="/tmp/javadoc_check_methods_$$.txt"

# 删除 JavaDoc、块注释和行注释，同时保留行号，避免注释示例中的类型声明被扫描。
strip_java_comments() {
    awk '
    {
        line = $0
        out = ""
        while (1) {
            if (depth == 0) {
                start = index(line, "/*")
                if (start == 0) {
                    out = out line
                    break
                }
                out = out substr(line, 1, start - 1)
                rest = substr(line, start + 2)
                end = index(rest, "*/")
                if (end == 0) {
                    depth = 1
                    break
                }
                line = substr(rest, end + 2)
            } else {
                end = index(line, "*/")
                if (end == 0) {
                    break
                }
                line = substr(line, end + 2)
                depth = 0
            }
        }
        sub(/\/\/.*/, "", out)
        print out
    }' "$1"
}

# 清理临时文件
trap "rm -f $TMP_CLASSES $TMP_METHODS" EXIT

echo "正在扫描..."

# 1. 检查类级JavaDoc
echo -e "${YELLOW}[1/3] 检查类级JavaDoc...${NC}"
find "$SCAN_PATH" -name "*.java" -type f | while read file; do
    # 跳过测试文件和内部类
    if [[ "$file" == *"/test/"* ]]; then
        continue
    fi
    
    # 先从注释中剥离类型声明，避免 JavaDoc 示例被当作真实类声明。
    stripped_file="/tmp/javadoc_check_stripped_$$.java"
    strip_java_comments "$file" > "$stripped_file"

    # 查找第一个真实的 public class/interface/enum 声明。
    class_line=$(grep -nE "^[[:space:]]*public[[:space:]]+(class|interface|enum|@interface|record)[[:space:]]" "$stripped_file" | head -1 | cut -d: -f1 || true)
    rm -f "$stripped_file"

    if [ -n "$class_line" ]; then
        TOTAL_CLASSES=$((TOTAL_CLASSES + 1))

        # 从真实声明向前查找最近的完整 JavaDoc。
        javadoc_end=$(sed -n "1,${class_line}p" "$file" | grep -n "^[[:space:]]*\*/[[:space:]]*$" | tail -1 | cut -d: -f1 || true)
        if [ -z "$javadoc_end" ]; then
            CLASSES_WITHOUT_DOC=$((CLASSES_WITHOUT_DOC + 1))
            echo "$file" >> "$TMP_CLASSES"
        else
            javadoc_start=$(sed -n "1,${javadoc_end}p" "$file" | grep -n "^[[:space:]]*/\*\*" | tail -1 | cut -d: -f1 || true)
            if [ -z "$javadoc_start" ]; then
                CLASSES_WITHOUT_DOC=$((CLASSES_WITHOUT_DOC + 1))
                echo "$file" >> "$TMP_CLASSES"
            fi
        fi
    fi
done

# 2. 检查方法级JavaDoc
echo -e "${YELLOW}[2/3] 检查方法级JavaDoc...${NC}"
find "$SCAN_PATH" -name "*.java" -type f | while read file; do
    if [[ "$file" == *"/test/"* ]]; then
        continue
    fi
    
    # 查找public/protected方法(排除@Override)
    grep -n "^\s*public.*(" "$file" | while IFS=: read line_num line_content; do
        # 跳过构造函数和已有@Override的方法
        if [[ "$line_content" =~ "public.*\(.*\)" ]] && ! grep -q "@Override" "$file"; then
            TOTAL_PUBLIC_METHODS=$((TOTAL_PUBLIC_METHODS + 1))
            
            # 检查方法前是否有JavaDoc
            prev_lines=$(sed -n "$((line_num-5)),$((line_num-1))p" "$file")
            if ! echo "$prev_lines" | grep -q "/\*\*"; then
                METHODS_WITHOUT_DOC=$((METHODS_WITHOUT_DOC + 1))
                echo "$file:$line_num: $line_content" >> "$TMP_METHODS"
            fi
        fi
    done
done

# 3. 生成报告
echo -e "${YELLOW}[3/3] 生成报告...${NC}"
echo ""
echo "=========================================="
echo "检查结果汇总"
echo "=========================================="
echo ""

# 统计缺失类JavaDoc的文件数
if [ -f "$TMP_CLASSES" ]; then
    CLASSES_WITHOUT_DOC=$(wc -l < "$TMP_CLASSES" | tr -d ' ')
else
    CLASSES_WITHOUT_DOC=0
fi

# 统计缺失方法JavaDoc的数量
if [ -f "$TMP_METHODS" ]; then
    METHODS_WITHOUT_DOC=$(wc -l < "$TMP_METHODS" | tr -d ' ')
else
    METHODS_WITHOUT_DOC=0
fi

# 统计总类数和方法数
TOTAL_CLASSES=$(find "$SCAN_PATH" -name "*.java" -type f ! -path "*/test/*" -exec grep -l "public \(class\|interface\|enum\)" {} \; | wc -l | tr -d ' ')
TOTAL_PUBLIC_METHODS=$(find "$SCAN_PATH" -name "*.java" -type f ! -path "*/test/*" -exec grep -c "^\s*public.*(" {} \; | awk '{sum+=$1} END {print sum}')

# 计算覆盖率
if [ "$TOTAL_CLASSES" -gt 0 ]; then
    CLASS_COVERAGE=$(awk "BEGIN {printf \"%.1f\", 100 - ($CLASSES_WITHOUT_DOC * 100.0 / $TOTAL_CLASSES)}")
else
    CLASS_COVERAGE="100.0"
fi

if [ "$TOTAL_PUBLIC_METHODS" -gt 0 ]; then
    METHOD_COVERAGE=$(awk "BEGIN {printf \"%.1f\", 100 - ($METHODS_WITHOUT_DOC * 100.0 / $TOTAL_PUBLIC_METHODS)}")
else
    METHOD_COVERAGE="100.0"
fi

# 打印统计
echo "📊 类级JavaDoc:"
echo "  总类数: $TOTAL_CLASSES"
echo "  缺少JavaDoc: $CLASSES_WITHOUT_DOC"
echo -e "  覆盖率: ${GREEN}${CLASS_COVERAGE}%${NC}"
echo ""
echo "📊 方法级JavaDoc:"
echo "  总public方法数: $TOTAL_PUBLIC_METHODS"
echo "  缺少JavaDoc: $METHODS_WITHOUT_DOC"
echo -e "  覆盖率: ${GREEN}${METHOD_COVERAGE}%${NC}"
echo ""

# 计算总体覆盖率
TOTAL_COVERAGE=$(awk "BEGIN {printf \"%.1f\", ($CLASS_COVERAGE + $METHOD_COVERAGE) / 2}")

if (( $(echo "$TOTAL_COVERAGE >= 90" | bc -l) )); then
    echo -e "✅ 总体覆盖率: ${GREEN}${TOTAL_COVERAGE}%${NC} (优秀)"
elif (( $(echo "$TOTAL_COVERAGE >= 70" | bc -l) )); then
    echo -e "⚠️  总体覆盖率: ${YELLOW}${TOTAL_COVERAGE}%${NC} (良好)"
else
    echo -e "❌ 总体覆盖率: ${RED}${TOTAL_COVERAGE}%${NC} (需改进)"
fi
echo ""

# 4. 详细列表
if [ "$CLASSES_WITHOUT_DOC" -gt 0 ]; then
    echo "=========================================="
    echo "缺少类级JavaDoc的文件 (前20个):"
    echo "=========================================="
    head -20 "$TMP_CLASSES" | while read file; do
        echo "  ❌ $(basename $file) - $file"
    done
    echo ""
    if [ "$CLASSES_WITHOUT_DOC" -gt 20 ]; then
        echo "  ... 还有 $((CLASSES_WITHOUT_DOC - 20)) 个文件"
        echo ""
    fi
fi

if [ "$METHODS_WITHOUT_DOC" -gt 0 ]; then
    echo "=========================================="
    echo "缺少方法级JavaDoc的方法 (前20个):"
    echo "=========================================="
    head -20 "$TMP_METHODS" | while read line; do
        file=$(echo "$line" | cut -d: -f1)
        line_num=$(echo "$line" | cut -d: -f2)
        method=$(echo "$line" | cut -d: -f3-)
        echo "  ❌ $(basename $file):$line_num"
        echo "     $method"
    done
    echo ""
    if [ "$METHODS_WITHOUT_DOC" -gt 20 ]; then
        echo "  ... 还有 $((METHODS_WITHOUT_DOC - 20)) 个方法"
        echo ""
    fi
fi

# 5. 生成改进建议
echo "=========================================="
echo "改进建议"
echo "=========================================="
echo ""

if (( $(echo "$CLASS_COVERAGE < 90" | bc -l) )); then
    echo "📝 类级JavaDoc需要改进:"
    echo "   - 为所有public类添加类级注释"
    echo "   - 包含: 功能概述、主要功能列表、使用示例"
    echo "   - 参考: docs/JAVADOC_GUIDE.md"
    echo ""
fi

if (( $(echo "$METHOD_COVERAGE < 90" | bc -l) )); then
    echo "📝 方法级JavaDoc需要改进:"
    echo "   - 为所有public/protected方法添加注释"
    echo "   - 包含: @param, @return, @throws"
    echo "   - 参考: docs/JAVADOC_GUIDE.md"
    echo ""
fi

echo "🔧 快速修复建议:"
echo "   1. 使用IDE自动生成JavaDoc框架(Ctrl+Shift+D / ⌘⇧D)"
echo "   2. 按优先级补充: Handler > Service > Repository > DTO"
echo "   3. 使用模板: docs/JAVADOC_GUIDE.md 中的模板"
echo ""

# 6. 生成待办清单
echo "=========================================="
echo "生成待办清单"
echo "=========================================="
echo ""

OUTPUT_FILE="javadoc_todo_list_$(date +%Y%m%d_%H%M%S).md"

cat > "$OUTPUT_FILE" << EOF
# JavaDoc 补充待办清单

**生成时间**: $(date '+%Y-%m-%d %H:%M:%S')  
**扫描路径**: $SCAN_PATH  
**总体覆盖率**: ${TOTAL_COVERAGE}%

---

## 统计数据

| 指标 | 数量 | 覆盖率 |
|------|------|--------|
| 类级JavaDoc | ${TOTAL_CLASSES} 个类, ${CLASSES_WITHOUT_DOC} 个缺失 | ${CLASS_COVERAGE}% |
| 方法级JavaDoc | ${TOTAL_PUBLIC_METHODS} 个方法, ${METHODS_WITHOUT_DOC} 个缺失 | ${METHOD_COVERAGE}% |

---

## 待补充文件清单

### 类级JavaDoc缺失

EOF

if [ -f "$TMP_CLASSES" ]; then
    cat "$TMP_CLASSES" | while read file; do
        rel_path=$(echo "$file" | sed "s|^$SCAN_PATH/||")
        echo "- [ ] \`$rel_path\`" >> "$OUTPUT_FILE"
    done
else
    echo "无缺失" >> "$OUTPUT_FILE"
fi

cat >> "$OUTPUT_FILE" << EOF

### 方法级JavaDoc缺失 (部分列表)

EOF

if [ -f "$TMP_METHODS" ]; then
    head -50 "$TMP_METHODS" | while read line; do
        file=$(echo "$line" | cut -d: -f1)
        line_num=$(echo "$line" | cut -d: -f2)
        rel_path=$(echo "$file" | sed "s|^$SCAN_PATH/||")
        echo "- [ ] \`$rel_path:$line_num\`" >> "$OUTPUT_FILE"
    done
    
    if [ "$METHODS_WITHOUT_DOC" -gt 50 ]; then
        echo "" >> "$OUTPUT_FILE"
        echo "... 还有 $((METHODS_WITHOUT_DOC - 50)) 个方法待补充" >> "$OUTPUT_FILE"
    fi
else
    echo "无缺失" >> "$OUTPUT_FILE"
fi

cat >> "$OUTPUT_FILE" << EOF

---

## 执行建议

### 按优先级补充

**P0 - 核心接口层** (Handler):
\`\`\`bash
find apex-system/src/main/java -name "*Handler.java"
\`\`\`

**P1 - 应用服务层** (Service):
\`\`\`bash
find apex-application/src/main/java -name "*Service.java"
\`\`\`

**P2 - 领域层** (Domain Model, Repository):
\`\`\`bash
find apex-domain/src/main/java -name "*.java"
\`\`\`

### 使用IDE快捷键

- **IDEA**: 光标放在类/方法上,按 \`Ctrl+Shift+D\` (Windows/Linux) 或 \`⌘⇧D\` (Mac)
- **Eclipse**: \`Alt+Shift+J\`
- **VSCode**: 安装 "Java Extension Pack",使用 \`/** + Enter\`

### 参考文档

- [JavaDoc 编写规范](../../../docs/04-development/development-standards.md)
- [构建与验证约束](../../../AGENTS.md)

---

**生成工具**: check_javadoc.sh  
**报告文件**: $OUTPUT_FILE
EOF

echo -e "✅ 待办清单已生成: ${GREEN}$OUTPUT_FILE${NC}"
echo ""

# 7. 退出码
if (( $(echo "$TOTAL_COVERAGE < 90" | bc -l) )); then
    exit 1  # 覆盖率不达标
else
    exit 0
fi

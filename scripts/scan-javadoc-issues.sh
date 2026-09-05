#!/bin/bash
# scan-javadoc-issues.sh
# 扫描项目中 JavaDoc 注释缺失或不完整的文件

set -e

PROJECT_ROOT="/Users/work/apex-scaffold"
cd "$PROJECT_ROOT"

echo "=========================================="
echo "扫描 JavaDoc 注释问题..."
echo "=========================================="
echo ""

# 清理旧的扫描结果
rm -f javadoc-issues-*.txt

# 1. 查找 public 类没有类级 JavaDoc 的
echo "==> [1/4] 查找缺少类级 JavaDoc 的 public 类..."
find apex-*/src/main/java -name "*.java" -type f 2>/dev/null | \
    grep -v "/test/" | while read file; do
    # 检查是否有 public class/interface/enum
    if grep -q "^public \(class\|interface\|enum\|@interface\)" "$file"; then
        # 检查类声明前5行是否有 JavaDoc（/**）
        if ! grep -B5 "^public \(class\|interface\|enum\|@interface\)" "$file" | grep -q "/\*\*"; then
            echo "$file"
        fi
    fi
done > javadoc-issues-class.txt || true
CLASS_COUNT=$(wc -l < javadoc-issues-class.txt | tr -d ' ')
echo "    发现 ${CLASS_COUNT} 个类缺少类级 JavaDoc"

# 2. 查找 JavaDoc 缺少 @author 的
echo "==> [2/4] 查找缺少 @author 标签..."
find apex-*/src/main/java -name "*.java" -type f 2>/dev/null | \
    grep -v "/test/" | while read file; do
    # 如果有 JavaDoc 但没有 @author
    if grep -q "/\*\*" "$file" && ! grep -q "@author" "$file"; then
        echo "$file"
    fi
done > javadoc-issues-author.txt || true
AUTHOR_COUNT=$(wc -l < javadoc-issues-author.txt | tr -d ' ')
echo "    发现 ${AUTHOR_COUNT} 个文件缺少 @author"

# 3. 查找 JavaDoc 缺少 @since 的
echo "==> [3/4] 查找缺少 @since 标签..."
find apex-*/src/main/java -name "*.java" -type f 2>/dev/null | \
    grep -v "/test/" | while read file; do
    # 如果是 public 类但 JavaDoc 没有 @since
    if grep -q "^public \(class\|interface\|enum\)" "$file"; then
        if grep -q "/\*\*" "$file" && ! grep -q "@since" "$file"; then
            echo "$file"
        fi
    fi
done > javadoc-issues-since.txt || true
SINCE_COUNT=$(wc -l < javadoc-issues-since.txt | tr -d ' ')
echo "    发现 ${SINCE_COUNT} 个文件缺少 @since"

# 4. 查找 public 方法可能缺少 JavaDoc 的文件（简化检查）
echo "==> [4/4] 查找可能缺少方法级 JavaDoc 的文件..."
find apex-*/src/main/java -name "*.java" -type f 2>/dev/null | \
    grep -v "/test/" | while read file; do
    # 统计 public 方法数量和 @param/@return 数量
    PUBLIC_METHOD_COUNT=$(grep -c "^\s*public\s\+[^(]*\s\+[a-z][A-Za-z0-9]*\s*(" "$file" 2>/dev/null || echo "0")
    PARAM_COUNT=$(grep -c "@param" "$file" 2>/dev/null || echo "0")
    RETURN_COUNT=$(grep -c "@return" "$file" 2>/dev/null || echo "0")
    
    # 如果有 public 方法但 @param/@return 明显不足，标记为可疑
    if [ "$PUBLIC_METHOD_COUNT" -gt 3 ] && [ "$PARAM_COUNT" -lt 3 ]; then
        echo "$file"
    fi
done > javadoc-issues-method.txt || true
METHOD_COUNT=$(wc -l < javadoc-issues-method.txt | tr -d ' ')
echo "    发现 ${METHOD_COUNT} 个文件可能缺少方法级 JavaDoc"

echo ""
echo "=========================================="
echo "扫描完成！"
echo "=========================================="
echo ""
echo "问题文件统计："
echo "  - 缺少类级 JavaDoc:             ${CLASS_COUNT} 个"
echo "  - 缺少 @author:                 ${AUTHOR_COUNT} 个"
echo "  - 缺少 @since:                  ${SINCE_COUNT} 个"
echo "  - 可能缺少方法级 JavaDoc:       ${METHOD_COUNT} 个"
echo ""
TOTAL=$((CLASS_COUNT + AUTHOR_COUNT + SINCE_COUNT + METHOD_COUNT))
echo "  总计: ${TOTAL} 个问题项（文件可能重复计数）"
echo ""
echo "详细结果已保存到："
echo "  - javadoc-issues-class.txt"
echo "  - javadoc-issues-author.txt"
echo "  - javadoc-issues-since.txt"
echo "  - javadoc-issues-method.txt"
echo ""

#!/bin/bash
# scan-javadoc-issues.sh
# 扫描项目中 JavaDoc 注释缺失或不完整的文件

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)" || exit
source "${SCRIPT_DIR}/../../lib/common.sh" || exit
setup_error_trap
PROJECT_ROOT=$(get_project_root)
cd -- "$PROJECT_ROOT"
JAVA_FILES=$(safe_find "apex-*/src/main/java" "*.java")

# 剥离 Java 注释（JavaDoc、块注释、行注释），避免示例代码中的 public 声明被误计
strip_java_comments() {
    awk '
    {
        line = $0
        out = ""
        while (1) {
            if (depth == 0) {
                i = index(line, "/*")
                if (i == 0) { out = out line; break }
                out = out substr(line, 1, i - 1)
                rest = substr(line, i + 2)
                j = index(rest, "*/")
                if (j > 0) { line = substr(rest, j + 2) } else { depth = 1; break }
            } else {
                j = index(line, "*/")
                if (j == 0) { break }
                line = substr(line, j + 2)
                depth = 0
            }
        }
        sub(/\/\/.*/, "", out)
        print out
    }' "$1" 2>/dev/null
}

print_header "扫描 JavaDoc 注释问题..."

# 1. 查找 public 类没有类级 JavaDoc 的
echo "==> [1/4] 查找缺少类级 JavaDoc 的 public 类..."
while IFS= read -r file; do
    [[ -n "$file" && "$file" != */test/* ]] || continue
    file=${file#./}
    # 使用 awk 检测：跳过注解行（含多行注解续行）和空行后，public class 前的非空行必须以 */ 结尾
    if awk '
        in_ann {
            pd += gsub(/\(/, "(", $0) - gsub(/\)/, ")", $0)
            if (pd <= 0) in_ann = 0
            next
        }
        /^[[:space:]]*@[A-Za-z_]/ {
            pd = gsub(/\(/, "(", $0) - gsub(/\)/, ")", $0)
            if (pd > 0) in_ann = 1
            next
        }
        /^[[:space:]]*$/ { next }
        /^public (class|interface|enum|@interface)/ {
            if (last !~ /\*\/[[:space:]]*$/) { found = 1; exit }
        }
        { last = $0 }
        END { exit found ? 0 : 1 }
    ' "$file" 2>/dev/null; then
        printf '%s\n' "$file"
    fi
done <<< "$JAVA_FILES" > javadoc-issues-class.txt
CLASS_COUNT=$(safe_count '' javadoc-issues-class.txt)
echo "    发现 ${CLASS_COUNT} 个类缺少类级 JavaDoc"

# 2. 查找 JavaDoc 缺少 @author 的
echo "==> [2/4] 查找缺少 @author 标签..."
while IFS= read -r file; do
    [[ -n "$file" && "$file" != */test/* ]] || continue
    file=${file#./}
    # 如果有 JavaDoc 但没有 @author
    DOC_COUNT=$(safe_count '/\*\*' "$file")
    AUTHOR_TAG_COUNT=$(safe_count '@author' "$file")
    if safe_gt "$DOC_COUNT" 0 && safe_eq "$AUTHOR_TAG_COUNT" 0; then
        printf '%s\n' "$file"
    fi
done <<< "$JAVA_FILES" > javadoc-issues-author.txt
AUTHOR_COUNT=$(safe_count '' javadoc-issues-author.txt)
echo "    发现 ${AUTHOR_COUNT} 个文件缺少 @author"

# 3. 查找 JavaDoc 缺少 @since 的
echo "==> [3/4] 查找缺少 @since 标签..."
while IFS= read -r file; do
    [[ -n "$file" && "$file" != */test/* ]] || continue
    file=${file#./}
    # 如果是 public 类但 JavaDoc 没有 @since
    CLASS_MATCHES=$(safe_count "^public \(class\|interface\|enum\)" "$file")
    if safe_gt "$CLASS_MATCHES" 0; then
        DOC_COUNT=$(safe_count '/\*\*' "$file")
        SINCE_TAG_COUNT=$(safe_count '@since' "$file")
        if safe_gt "$DOC_COUNT" 0 && safe_eq "$SINCE_TAG_COUNT" 0; then
            printf '%s\n' "$file"
        fi
    fi
done <<< "$JAVA_FILES" > javadoc-issues-since.txt
SINCE_COUNT=$(safe_count '' javadoc-issues-since.txt)
echo "    发现 ${SINCE_COUNT} 个文件缺少 @since"

# 4. 查找 public 方法可能缺少 JavaDoc 的文件（简化检查）
echo "==> [4/4] 查找可能缺少方法级 JavaDoc 的文件..."
while IFS= read -r file; do
    [[ -n "$file" && "$file" != */test/* ]] || continue
    file=${file#./}
    # 先剥离注释，再统计 public 方法数量，避免 JavaDoc 示例中的 public 方法被误计
    stripped=$(strip_java_comments "$file")
    PUBLIC_METHOD_COUNT=$(printf '%s\n' "$stripped" | grep -cE '^[[:space:]]*public[[:space:]]+[^(]*[[:space:]]+[a-z][A-Za-z0-9]*[[:space:]]*\(' || true)
    PARAM_COUNT=$(safe_count "@param" "$file")
    RETURN_COUNT=$(safe_count "@return" "$file")
    
    # 如果有 public 方法但 @param/@return 明显不足，标记为可疑
    if safe_gt "$PUBLIC_METHOD_COUNT" 3 && safe_lt "$PARAM_COUNT" 3; then
        printf '%s\n' "$file"
    fi
done <<< "$JAVA_FILES" > javadoc-issues-method.txt
METHOD_COUNT=$(safe_count '' javadoc-issues-method.txt)
echo "    发现 ${METHOD_COUNT} 个文件可能缺少方法级 JavaDoc"

echo ""
echo "=========================================="
log_success "扫描完成！"
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

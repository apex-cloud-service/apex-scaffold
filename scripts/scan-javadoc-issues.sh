#!/bin/bash
###############################################################################
# scan-javadoc-issues.sh - 扫描项目中 JavaDoc 注释缺失或不完整的文件
#
# 功能：
#   1. 顶层 public 类型声明缺少紧邻类级 JavaDoc
#   2. 存在 JavaDoc 但缺少 @author 标签
#   3. 类级 JavaDoc 存在但缺少 @since 标签
#   4. public 方法较多但 @param 明显不足的文件（启发式）
#
# 使用方法：
#   ./scan-javadoc-issues.sh
#
# 作者：Apex Scaffold Team
# 日期：2026-09-27
###############################################################################

# 加载公共函数库
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/common.sh"

PROJECT_ROOT=$(get_project_root) || exit 1
cd "$PROJECT_ROOT"

print_header "扫描 JavaDoc 注释问题"

# 清理旧的扫描结果
rm -f javadoc-issues-*.txt

###############################################################################
# 判定顶层 public 类型声明是否缺少紧邻的类级 JavaDoc
#
# 从类声明行向上回溯：跳过空行、注解行（含跨行注解参数，按括号深度）、
# javadoc 体行；遇到 /** 起始行视为有 JavaDoc（退出码 0）；
# 遇到普通代码行或非 javadoc 块注释视为缺失（退出码 1）；
# 文件无顶层 public 类型声明时不适用（退出码 2）。
###############################################################################
has_class_javadoc() {
    awk '
    {
        lines[NR] = $0
        if (decl == 0 && $0 ~ /^public[[:space:]]+([[:space:]]*(final|abstract|sealed|static|strictfp)[[:space:]]+)*(class|interface|enum|record|@interface)([[:space:]]|\{)/) {
            decl = NR
        }
    }
    END {
        if (decl == 0) exit 2
        depth = 0
        ok = 0
        for (i = decl - 1; i >= 1; i--) {
            s = lines[i]
            gsub(/^[[:space:]]+/, "", s)
            gsub(/[[:space:]]+$/, "", s)
            if (s == "") continue
            n = gsub(/\(/, "(", s)
            m = gsub(/\)/, ")", s)
            if (depth != 0) { depth += n - m; continue }
            if (s ~ /^\/\*\*/) { ok = 1; break }
            if (s ~ /^\*/) continue
            if (s ~ /^\/\*/) { ok = 0; break }
            if (s ~ /^@/) { depth += n - m; continue }
            if (n != m) { depth += n - m; continue }
            ok = 0
            break
        }
        exit (ok ? 0 : 1)
    }' "$1" 2>/dev/null
}

###############################################################################
# 剥离块注释与行注释，输出纯代码行（用于统计真实方法声明，
# 避免 JavaDoc 代码示例中的 public 方法被误计）
###############################################################################
strip_comments() {
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

# 1. 查找 public 类没有类级 JavaDoc 的
log_info "==> [1/4] 查找缺少类级 JavaDoc 的 public 类..."
safe_find "apex-*/src/main/java" "*.java" | while read -r file; do
    [ "$(has_class_javadoc "$file"; echo $?)" -eq 1 ] && echo "$file"
done > javadoc-issues-class.txt
CLASS_COUNT=$(wc -l < javadoc-issues-class.txt | tr -d ' ')
log_info "    发现 ${CLASS_COUNT} 个类缺少类级 JavaDoc"

# 2. 查找 JavaDoc 缺少 @author 的
log_info "==> [2/4] 查找缺少 @author 标签..."
safe_find "apex-*/src/main/java" "*.java" | while read -r file; do
    if grep -q "/\*\*" "$file" 2>/dev/null && ! grep -q "@author" "$file" 2>/dev/null; then
        echo "$file"
    fi
done > javadoc-issues-author.txt
AUTHOR_COUNT=$(wc -l < javadoc-issues-author.txt | tr -d ' ')
log_info "    发现 ${AUTHOR_COUNT} 个文件缺少 @author"

# 3. 查找类级 JavaDoc 缺少 @since 的
log_info "==> [3/4] 查找缺少 @since 标签..."
safe_find "apex-*/src/main/java" "*.java" | while read -r file; do
    if grep -q "/\*\*" "$file" 2>/dev/null && ! grep -q "@since" "$file" 2>/dev/null; then
        echo "$file"
    fi
done > javadoc-issues-since.txt
SINCE_COUNT=$(wc -l < javadoc-issues-since.txt | tr -d ' ')
log_info "    发现 ${SINCE_COUNT} 个文件缺少 @since"

# 4. 查找 public 方法可能缺少 JavaDoc 的文件（启发式：剥离注释后统计）
log_info "==> [4/4] 查找可能缺少方法级 JavaDoc 的文件..."
safe_find "apex-*/src/main/java" "*.java" | while read -r file; do
    # 统计剥离注释后的 public 方法声明数量与 @param 数量
    PUBLIC_METHOD_COUNT=$(strip_comments "$file" | grep -cE '^[[:space:]]*public[[:space:]]+[^()]*[[:space:]][a-z][A-Za-z0-9]*[[:space:]]*\(' || true)
    PUBLIC_METHOD_COUNT=$(printf '%s' "$PUBLIC_METHOD_COUNT" | tr -d '[:space:]')
    [ -z "$PUBLIC_METHOD_COUNT" ] && PUBLIC_METHOD_COUNT=0
    PARAM_COUNT=$(grep -c "@param" "$file" 2>/dev/null || true)
    PARAM_COUNT=$(printf '%s' "$PARAM_COUNT" | tr -d '[:space:]')
    [ -z "$PARAM_COUNT" ] && PARAM_COUNT=0

    # 如果有 public 方法但 @param 明显不足，标记为可疑
    if safe_gt "$PUBLIC_METHOD_COUNT" 3 && safe_lt "$PARAM_COUNT" 3; then
        echo "$file"
    fi
done > javadoc-issues-method.txt
METHOD_COUNT=$(wc -l < javadoc-issues-method.txt | tr -d ' ')
log_info "    发现 ${METHOD_COUNT} 个文件可能缺少方法级 JavaDoc"

echo ""
print_header "扫描完成"

log_info "问题文件统计："
log_info "  - 缺少类级 JavaDoc:             ${CLASS_COUNT} 个"
log_info "  - 缺少 @author:                 ${AUTHOR_COUNT} 个"
log_info "  - 缺少 @since:                  ${SINCE_COUNT} 个"
log_info "  - 可能缺少方法级 JavaDoc:       ${METHOD_COUNT} 个"
TOTAL=$((CLASS_COUNT + AUTHOR_COUNT + SINCE_COUNT + METHOD_COUNT))
log_info "  总计: ${TOTAL} 个问题项（文件可能重复计数）"
log_info ""
log_info "详细结果已保存到："
log_info "  - javadoc-issues-class.txt"
log_info "  - javadoc-issues-author.txt"
log_info "  - javadoc-issues-since.txt"
log_info "  - javadoc-issues-method.txt"

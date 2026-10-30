#!/bin/bash
# scan-javadoc-issues.sh
# 扫描项目中 JavaDoc 注释缺失或不完整的文件
# 用法：JAVADOC_OUTPUT_DIR=/tmp/javadoc-scan bash scripts/dev/check/scan-javadoc-issues.sh
# 可用 JAVADOC_SCAN_PATH 限定扫描目录；默认扫描所有业务模块 main/java。
# 默认输出到 _tmp/（工作区管理规范 4.1：中间产物不入项目根目录）；方法检查为启发式候选列表，退出码不表示文档门禁结果。
# 作者：Apex Team

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)" || exit
source "${SCRIPT_DIR}/../../lib/common.sh" || exit
setup_error_trap
PROJECT_ROOT=$(get_project_root)
cd -- "$PROJECT_ROOT"
OUTPUT_DIR=${JAVADOC_OUTPUT_DIR:-$PROJECT_ROOT/_tmp}
mkdir -p -- "$OUTPUT_DIR" || exit
OUTPUT_DIR=$(CDPATH= cd -- "$OUTPUT_DIR" && pwd -P) || exit
SCAN_PATH=${JAVADOC_SCAN_PATH:-"apex-*/src/main/java"}
JAVA_FILES=$(safe_find "$SCAN_PATH" "*.java")

print_header "扫描 JavaDoc 注释问题..."

: > "${OUTPUT_DIR}/javadoc-issues-class.txt"
: > "${OUTPUT_DIR}/javadoc-issues-author.txt"
: > "${OUTPUT_DIR}/javadoc-issues-since.txt"
: > "${OUTPUT_DIR}/javadoc-issues-method.txt"

echo "==> [1/4] 查找缺少类级 JavaDoc 的 public 类..."
echo "==> [2/4] 查找缺少 @author 标签..."
echo "==> [3/4] 查找缺少 @since 标签..."
echo "==> [4/4] 查找可能缺少方法级 JavaDoc 的文件..."

# 每个文件只启动一次 awk，同时完成四类启发式检查，避免重复 fork 和读取源文件。
while IFS= read -r file; do
    [[ -n "$file" && "$file" != */test/* ]] || continue
    file=${file#./}

    result=$(awk -v file="$file" '
        function emit(kind) {
            printf "%s\t%s\n", kind, file
        }
        function strip_comments(input,    line, out, i, rest, j) {
            line = input
            out = ""
            while (1) {
                if (comment_depth == 0) {
                    i = index(line, "/*")
                    if (i == 0) {
                        out = out line
                        break
                    }
                    out = out substr(line, 1, i - 1)
                    rest = substr(line, i + 2)
                    j = index(rest, "*/")
                    if (j > 0) {
                        line = substr(rest, j + 2)
                    } else {
                        comment_depth = 1
                        break
                    }
                } else {
                    j = index(line, "*/")
                    if (j == 0) {
                        break
                    }
                    line = substr(line, j + 2)
                    comment_depth = 0
                }
            }
            sub(/\/\/.*/, "", out)
            return out
        }
        {
            if ($0 ~ /\/\*\*/) doc_count++
            if ($0 ~ /@author/) author_count++
            if ($0 ~ /@since/) since_count++
            if ($0 ~ /@param/) param_count++
            if ($0 ~ /@return/) return_count++

            starts_doc = ($0 ~ /\/\*\*/)
            code = strip_comments($0)
            is_blank = (code ~ /^[[:space:]]*$/)
            is_annotation = (code ~ /^[[:space:]]*@[A-Za-z_]/)
            is_public_type = (code ~ /^[[:space:]]*public[[:space:]]+(class|interface|enum|@interface|record)[[:space:]]/)

            if (starts_doc) {
                doc_pending = 1
            }

            if (is_public_type) {
                public_type_count++
                if (!doc_pending) class_issue = 1
                doc_pending = 0
            } else if (code ~ /^[[:space:]]*public[[:space:]]+[^ (]*[[:space:]]+[a-z][A-Za-z0-9]*[[:space:]]*\(/) {
                public_method_count++
                doc_pending = 0
            } else if (!is_blank && !is_annotation && code !~ /^[[:space:]]*\/\//) {
                doc_pending = 0
            }
        }
        END {
            if (class_issue) emit("class")
            if (doc_count > 0 && author_count == 0) emit("author")
            if (public_type_count > 0 && doc_count > 0 && since_count == 0) emit("since")
            if (public_method_count > 3 && param_count < 3) emit("method")
        }
    ' "$file" 2>/dev/null) || continue

    while IFS=$'\t' read -r kind path; do
        case "$kind" in
            class) printf '%s\n' "$path" >> "${OUTPUT_DIR}/javadoc-issues-class.txt" ;;
            author) printf '%s\n' "$path" >> "${OUTPUT_DIR}/javadoc-issues-author.txt" ;;
            since) printf '%s\n' "$path" >> "${OUTPUT_DIR}/javadoc-issues-since.txt" ;;
            method) printf '%s\n' "$path" >> "${OUTPUT_DIR}/javadoc-issues-method.txt" ;;
        esac
    done <<< "$result"
done <<< "$JAVA_FILES"

CLASS_COUNT=$(wc -l < "${OUTPUT_DIR}/javadoc-issues-class.txt" | tr -d ' ')
AUTHOR_COUNT=$(wc -l < "${OUTPUT_DIR}/javadoc-issues-author.txt" | tr -d ' ')
SINCE_COUNT=$(wc -l < "${OUTPUT_DIR}/javadoc-issues-since.txt" | tr -d ' ')
METHOD_COUNT=$(wc -l < "${OUTPUT_DIR}/javadoc-issues-method.txt" | tr -d ' ')
echo "    发现 ${CLASS_COUNT} 个类缺少类级 JavaDoc"
echo "    发现 ${AUTHOR_COUNT} 个文件缺少 @author"
echo "    发现 ${SINCE_COUNT} 个文件缺少 @since"
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
echo "  - ${OUTPUT_DIR}/javadoc-issues-class.txt"
echo "  - ${OUTPUT_DIR}/javadoc-issues-author.txt"
echo "  - ${OUTPUT_DIR}/javadoc-issues-since.txt"
echo "  - ${OUTPUT_DIR}/javadoc-issues-method.txt"
echo ""

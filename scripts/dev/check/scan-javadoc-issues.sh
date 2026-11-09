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
        # 去掉字符串字面量（转义引号感知），避免字符串中的括号与注释记号干扰判定。
        function strip_strings(line) {
            gsub(/"([^"\\]|\\.)*"/, "\"\"", line)
            return line
        }
        # 去掉块注释与行注释；comment_depth 跨行维护，注释示例中的声明不会被误判。
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
        # 判断声明上方（仅跨注解及注解续行、纯行注释，遇裸空行或代码即停）是否存在 JavaDoc。
        # 返回 1=有 JavaDoc；2=窗口内见 @Override（豁免）；0=无。
        function doc_above(idx,    i, j, s) {
            i = idx - 1
            while (i >= 1) {
                if (raw_blank[i]) return 0
                s = code[i]
                # 纯行注释剥离后 code[i] 为空串，必须按原始行判定，否则把注释当代码截断窗口。
                if (raw[i] ~ /^[[:space:]]*\/\//) { i--; continue }
                if (pd_enter[i] > 0) { i--; continue }
                if (raw[i] ~ /^[[:space:]]*\/\*\*/) return 1
                if (raw[i] ~ /^[[:space:]]*\*\/[[:space:]]*$/) {
                    for (j = i; j >= 1; j--) {
                        if (raw[j] ~ /^[[:space:]]*\/\*\*/) return 1
                        if (j < i && !raw_blank[j] && code[j] ~ /[^[:space:]]/) return 0
                    }
                    return 0
                }
                if (s ~ /^[[:space:]]*@[A-Za-z_]/) {
                    if (s ~ /@Override/) return 2
                    i--
                    continue
                }
                return 0
            }
            return 0
        }
        {
            raw[NR] = $0
            raw_blank[NR] = ($0 ~ /^[[:space:]]*$/)
            if ($0 ~ /\/\*\*/) doc_count++
            if ($0 ~ /@author/) author_count++
            if ($0 ~ /@since/) since_count++
            if ($0 ~ /@param/) param_count++
            if ($0 ~ /@return/) return_count++

            code[NR] = strip_comments(strip_strings($0))
            pd_enter[NR] = pd
            delta = gsub(/\(/, "(", code[NR]) - gsub(/\)/, ")", code[NR])
            pd += delta
            if (pd < 0) pd = 0
        }
        END {
            for (i = 1; i <= NR; i++) {
                s = code[i]
                if (s ~ /^[[:space:]]*public[[:space:]]+(class|interface|enum|@interface|record)[[:space:]]/) {
                    public_type_count++
                    if (doc_above(i) == 0) class_issue = 1
                } else if (pd_enter[i] > 0) {
                    # 括号内的 public 方法属于匿名类/传参位置（如回调实现），按 @Override 同理豁免。
                    continue
                } else if (s ~ /^[[:space:]]*public[[:space:]]+[^ (]*[[:space:]]+[a-z][A-Za-z0-9]*[[:space:]]*\(/) {
                    if (s ~ /^[[:space:]]*(public|protected)[[:space:]]+(static[[:space:]]+|final[[:space:]]+|synchronized[[:space:]]+)*[A-Za-z_<>,\[\]?\. ]+[[:space:]]+(get|set|is)[A-Z][A-Za-z0-9_]*[[:space:]]*\(/) {
                        continue
                    }
                    if (s ~ /^[[:space:]]*(public|protected)[[:space:]]+[A-Za-z_<>,\[\]?\. ]*Builder[[:space:]]+[a-z][A-Za-z0-9_]*[[:space:]]*\(/) {
                        # §4.4.5：builder 模式流式 setter（返回 *Builder 类型）豁免。
                        continue
                    }
                    public_method_count++
                    if (doc_above(i) == 0) method_issue = 1
                }
            }
            if (class_issue) emit("class")
            if (doc_count > 0 && author_count == 0) emit("author")
            if (public_type_count > 0 && doc_count > 0 && since_count == 0) emit("since")
            if (method_issue) emit("method")
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

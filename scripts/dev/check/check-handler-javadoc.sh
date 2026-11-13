#!/bin/bash

###############################################################################
# Handler JavaDoc 检查脚本
#
# 功能：检查 Handler 类的 JavaDoc 完整性（类级注释、@author、@since、方法级注释）
#
# 使用方法：
#   ./scripts/dev/check/check-handler-javadoc.sh
#   ./scripts/dev/check/check-handler-javadoc.sh --handler-dir apex-system/src/main/java/com/apex/scaffold/interfaces/handler
#
# 退出码：
#   0 - 所有检查通过
#   1 - 存在 JavaDoc 缺失问题
#
# 作者：Apex Scaffold Team
# 日期：2026-10-01
###############################################################################

set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
source "${SCRIPT_DIR}/../../lib/common.sh"
PROJECT_ROOT=$(get_project_root)
cd -- "$PROJECT_ROOT"

HANDLER_DIR="apex-system/src/main/java/com/apex/scaffold/interfaces/handler"

while [[ $# -gt 0 ]]; do
    case $1 in
        --handler-dir)
            if [[ $# -lt 2 || -z ${2:-} || $2 == -* ]]; then
                log_error "$1 需要 Handler 目录路径"
                exit 1
            fi
            HANDLER_DIR="$2"
            shift 2
            ;;
        --help|-h)
            echo "使用方法: $0 [选项]"
            echo ""
            echo "选项:"
            echo "    --handler-dir PATH  Handler 源码目录（默认：apex-system/src/main/java/.../handler）"
            echo "    --help, -h          显示此帮助信息"
            exit 0
            ;;
        *)
            log_error "未知选项: $1"
            exit 1
            ;;
    esac
done

if [[ ! -d "$HANDLER_DIR" ]]; then
    log_error "Handler 目录不存在: $HANDLER_DIR"
    exit 1
fi

# 剥离 Java 注释（JavaDoc、块注释、行注释），避免示例代码中的 public 方法被误计
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

# 检查单个 Handler 文件的 JavaDoc 完整性
# 输出问题行，无问题时无输出
check_handler_file() {
    local file="$1"
    local stripped
    stripped=$(strip_java_comments "$file")

    # 1. 类级 JavaDoc：public class 前必须有 JavaDoc（/** ... */）。
    #    窗口判定与 scan-javadoc-issues.sh 的 doc_above 同口径：跳过注解行与独立行
    #    注释、裸空行即停；声明与 javadoc 之间的独立行注释不得打断窗口（否则固定误报）。
    local missing_class_javadoc
    missing_class_javadoc=$(awk '
        { raw[NR] = $0 }
        END {
            for (i = 1; i <= NR; i++) {
                if (raw[i] !~ /^public (class|interface|enum|@interface)/) continue
                found = 0
                for (j = i - 1; j >= 1; j--) {
                    if (raw[j] ~ /^[[:space:]]*\/\//) continue
                    if (raw[j] ~ /^[[:space:]]*$/) break
                    if (raw[j] ~ /^[[:space:]]*@[A-Za-z_]/) continue
                    if (raw[j] ~ /^[[:space:]]*\*\/[[:space:]]*$/) {
                        for (k = j; k >= 1; k--) {
                            if (raw[k] ~ /^[[:space:]]*\/\*\*/) { found = 1; break }
                            if (k < j && raw[k] !~ /^[[:space:]]*$/ && raw[k] !~ /^[[:space:]]*\*/) break
                        }
                    }
                    break
                }
                if (!found) print FILENAME ":" i
            }
        }
    ' "$file")
    if [[ -n "$missing_class_javadoc" ]]; then
        printf '  %s 类级 JavaDoc 缺失\n' "$missing_class_javadoc"
    fi

    # 2. 检查 @author 与 @since 标签（基于原始文件中的 JavaDoc 块）
    local author_count since_count
    author_count=$(safe_count '@author' "$file")
    since_count=$(safe_count '@since' "$file")
    local has_class_javadoc
    has_class_javadoc=$(safe_count '/\*\*' "$file")
    if safe_gt "$has_class_javadoc" 0; then
        if safe_eq "$author_count" 0; then
            printf '  %s 缺少 @author 标签\n' "$file"
        fi
        if safe_eq "$since_count" 0; then
            printf '  %s 缺少 @since 标签\n' "$file"
        fi
    fi

    # 3. 方法级 JavaDoc：统计 public HTTP handler 方法（返回 Mono/Flux<ServerResponse>）
    #    与 JavaDoc 块数量是否匹配
    local public_handler_methods
    public_handler_methods=$(printf '%s\n' "$stripped" | grep -cE '^[[:space:]]*public[[:space:]]+(Mono|Flux)<ServerResponse>[[:space:]]+[a-zA-Z]' || true)
    if safe_gt "$public_handler_methods" 0; then
        # 统计方法前的 JavaDoc 块数量：剥离后已无注释，需用原始文件
        # 简化：比较 public handler 方法数 与 @return/@param 总数，差距大于阈值则标记
        local param_count return_count
        param_count=$(safe_count '@param' "$file")
        return_count=$(safe_count '@return' "$file")
        local doc_tags=$((param_count + return_count))
        # 每个 public handler 方法至少应有 1 个 @param（ServerRequest）
        if safe_lt "$doc_tags" "$public_handler_methods"; then
            printf '  %s 方法级 JavaDoc 可能不足（public handler 方法 %d 个，@param/@return 共 %d 个）\n' \
                "$file" "$public_handler_methods" "$doc_tags"
        fi
    fi
}

echo "========================================"
echo "Handler JavaDoc 检查"
echo "========================================"
echo "检查目录: ${HANDLER_DIR}"
echo ""

issue_count=0
while IFS= read -r file; do
    [[ -n "$file" ]] || continue
    issues=$(check_handler_file "$file")
    if [[ -n "$issues" ]]; then
        echo "$issues"
        local_count=$(printf '%s\n' "$issues" | wc -l | tr -d ' ')
        issue_count=$((issue_count + local_count))
    fi
done < <(safe_find "$HANDLER_DIR" "*Handler.java")

echo ""
if safe_eq "$issue_count" 0; then
    log_success "所有 Handler JavaDoc 检查通过！"
    exit 0
else
    log_failure "发现 ${issue_count} 个 JavaDoc 问题"
    exit 1
fi

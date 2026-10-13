#!/bin/bash

set -u

###############################################################################
# check-springdoc-annotations-v2.sh - SpringDoc注解检查脚本（使用公共库重构版）
#
# 功能：检查 SpringDoc/Swagger 注解完整性
# 版本：v2.0.0（使用公共函数库）
#
# 使用方法：
#   ./check-springdoc-annotations-v2.sh
#   ./check-springdoc-annotations-v2.sh --verbose
#   ./check-springdoc-annotations-v2.sh --report report.txt
#
# 作者：Apex Scaffold Team
# 日期：2026-09-20
###############################################################################

# 加载公共函数库
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../../lib/common.sh"

# 配置参数
VERBOSE=0
REPORT_FILE=""

# 统计变量
ROUTER_TOTAL=0
ROUTER_WITH_ANNOTATION=0
HANDLER_TOTAL=0
HANDLER_METHOD_TOTAL=0
HANDLER_METHOD_WITH_OPERATION=0
DTO_TOTAL=0
DTO_WITH_SCHEMA=0
SCHEMA_ANNOTATION_COUNT=0

###############################################################################
# 参数解析
###############################################################################

parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --verbose|-v)
                VERBOSE=1
                shift
                ;;
            --report|-r)
                if [[ $# -lt 2 || -z ${2:-} || $2 == -* ]]; then
                    log_error "$1 需要报告文件路径"
                    exit 1
                fi
                REPORT_FILE="$2"
                shift 2
                ;;
            --help|-h)
                show_usage
                exit 0
                ;;
            *)
                log_error "未知参数: $1"
                show_usage
                exit 1
                ;;
        esac
    done
}

show_usage() {
    cat << EOF
使用方法: $0 [选项]

选项:
    --verbose, -v       显示详细信息
    --report, -r FILE   将报告输出到指定文件
    --help, -h          显示此帮助信息

示例:
    $0
    $0 --verbose
    $0 --report /tmp/springdoc-report.txt
EOF
}

###############################################################################
# 报告输出函数
###############################################################################

log_to_report() {
    if [ -n "$REPORT_FILE" ]; then
        echo "$1" >> "$REPORT_FILE"
    fi
}

###############################################################################
# 剥离 Java 源码中的块注释与行注释（用于统计真实方法声明，避免 JavaDoc
# 代码示例中的 public Mono/Flux 被误计为端点方法）
###############################################################################

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

###############################################################################
# 检查 Router 类的 @RouterOperations 注解
###############################################################################

check_router_annotations() {
    print_header "1. 检查 Router 类 @RouterOperations 注解"
    log_to_report "========================================"
    log_to_report "1. Router 类 @RouterOperations 注解检查"
    log_to_report "========================================"

    local router_dir="${PROJECT_ROOT}/apex-system/src/main/java/com/apex/scaffold/interfaces/router"

    if ! require_directory "$router_dir" "Router目录"; then
        return 1
    fi

    # 使用公共函数库的 safe_find
    local router_files
    if router_files=$(safe_find "$router_dir" "*Router.java"); then
        :
    else
        local find_status=$?
        return "$find_status"
    fi

    while IFS= read -r router_file; do
        [[ -n "$router_file" ]] || continue
        local router_name
        router_name=$(basename "$router_file" .java)

        # 跳过基础设施类（不计入总数，否则缺失统计永远无法归零）
        if [[ "$router_name" == "MethodNotAllowedRouter" ]]; then
            [ $VERBOSE -eq 1 ] && log_debug "跳过基础设施类: $router_name"
            continue
        fi
        ROUTER_TOTAL=$((ROUTER_TOTAL + 1))

        # 检查是否有注解
        if grep -q "@RouterOperations\|@RouterOperation" "$router_file" 2>/dev/null; then
            ROUTER_WITH_ANNOTATION=$((ROUTER_WITH_ANNOTATION + 1))
            log_success "$router_name"
            log_to_report "[✓] $router_name"

            if [ $VERBOSE -eq 1 ]; then
                local op_count
                if op_count=$(safe_count "@RouterOperation(" "$router_file"); then
                    log_info "  包含 $op_count 个路由操作"
                else
                    local count_status=$?
                    return "$count_status"
                fi
            fi
        else
            local annotation_status=$?
            if [ "$annotation_status" -ne 1 ]; then
                return "$annotation_status"
            fi
            log_failure "$router_name 缺少 @RouterOperations 注解"
            log_to_report "[✗] $router_name - 缺少注解"
        fi
    done <<< "$router_files"

    echo ""
    log_info "Router 类统计: 总数=$ROUTER_TOTAL, 已注解=$ROUTER_WITH_ANNOTATION, 缺失=$((ROUTER_TOTAL - ROUTER_WITH_ANNOTATION))"
    log_to_report ""
    log_to_report "统计: 总数=$ROUTER_TOTAL, 已注解=$ROUTER_WITH_ANNOTATION"
    echo ""
}

###############################################################################
# 检查 Handler 方法的 @Operation 注解
###############################################################################

check_handler_annotations() {
    print_header "2. 检查 Handler 方法 @Operation 注解"
    log_to_report ""
    log_to_report "========================================"
    log_to_report "2. Handler 方法 @Operation 注解检查"
    log_to_report "========================================"

    local handler_dir="${PROJECT_ROOT}/apex-system/src/main/java/com/apex/scaffold/interfaces/handler"

    if ! require_directory "$handler_dir" "Handler目录"; then
        return 1
    fi

    # 使用公共函数库的 safe_find
    local handler_files
    if handler_files=$(safe_find "$handler_dir" "*Handler.java"); then
        :
    else
        local find_status=$?
        return "$find_status"
    fi

    while IFS= read -r handler_file; do
        [[ -n "$handler_file" ]] || continue
        local handler_name
        handler_name=$(basename "$handler_file" .java)
        HANDLER_TOTAL=$((HANDLER_TOTAL + 1))

        # 剥离注释后统计真实端点方法（排除 JavaDoc 代码示例误计），
        # 并排除 Sentinel BlockException 兜底方法（非 REST 端点）
        local method_count=$(strip_java_comments "$handler_file" | grep -cE '^[[:space:]]*public[[:space:]]+(Mono|Flux)' || true)
        local excluded_count=$(strip_java_comments "$handler_file" | grep -cE '^[[:space:]]*public[[:space:]]+(Mono|Flux).*BlockException' || true)
        method_count=$(printf '%s' "$method_count" | tr -d '[:space:]')
        excluded_count=$(printf '%s' "$excluded_count" | tr -d '[:space:]')
        [ -z "$method_count" ] && method_count=0
        [ -z "$excluded_count" ] && excluded_count=0
        method_count=$((method_count - excluded_count))
        HANDLER_METHOD_TOTAL=$((HANDLER_METHOD_TOTAL + method_count))

        local operation_count=$(strip_java_comments "$handler_file" | grep -c "@Operation(" || true)
        operation_count=$(printf '%s' "$operation_count" | tr -d '[:space:]')
        [ -z "$operation_count" ] && operation_count=0
        if [ "$operation_count" -gt "$method_count" ]; then
            operation_count=$method_count
        fi
        HANDLER_METHOD_WITH_OPERATION=$((HANDLER_METHOD_WITH_OPERATION + operation_count))
        local missing=$((method_count - operation_count))

        if safe_eq "$missing" 0; then
            log_success "$handler_name (方法:$method_count, @Operation:$operation_count)"
            log_to_report "[✓] $handler_name - 方法:$method_count, 注解:$operation_count"
        else
            log_warn "$handler_name (方法:$method_count, @Operation:$operation_count, 缺失:$missing)"
            log_to_report "[⚠] $handler_name - 方法:$method_count, 注解:$operation_count, 缺失:$missing"
        fi
    done <<< "$handler_files"

    echo ""
    log_info "Handler 统计: 类数=$HANDLER_TOTAL, 方法总数=$HANDLER_METHOD_TOTAL"
    log_info "             @Operation:$HANDLER_METHOD_WITH_OPERATION, 缺失:$((HANDLER_METHOD_TOTAL - HANDLER_METHOD_WITH_OPERATION))"
    log_to_report ""
    log_to_report "统计: 类数=$HANDLER_TOTAL, 方法总数=$HANDLER_METHOD_TOTAL, 已注解=$HANDLER_METHOD_WITH_OPERATION"
    echo ""
}

###############################################################################
# 检查 DTO 的 @Schema 注解
###############################################################################

check_dto_schema_annotations() {
    print_header "3. 检查 DTO @Schema 注解"
    log_to_report ""
    log_to_report "========================================"
    log_to_report "3. DTO @Schema 注解检查"
    log_to_report "========================================"

    local dto_dirs=(
        "${PROJECT_ROOT}/apex-system/src/main/java/com/apex/scaffold/interfaces/dto"
        "${PROJECT_ROOT}/apex-application/src/main/java/com/apex/scaffold/application/dto"
    )

    for dto_dir in "${dto_dirs[@]}"; do
        if [ ! -d "$dto_dir" ]; then
            log_warn "DTO 目录不存在: $dto_dir"
            continue
        fi

        local module_name=$(echo "$dto_dir" | grep -o "apex-[^/]*")
        log_info "检查模块: $module_name"
        log_to_report ""
        log_to_report "模块: $module_name"

        # 使用 safe_find_custom 查找 DTO 文件
        local dto_files
        local dto_candidates
        if dto_candidates=$(safe_find_custom "$dto_dir" -name "*.java"); then
            :
        else
            local find_status=$?
            return "$find_status"
        fi
        dto_files=$(printf '%s\n' "$dto_candidates" | grep -v "PO.java" | grep -v "Entity.java" | grep -v "DO.java" || true)

        local module_dto_count=0
        local module_with_schema=0
        local module_schema_count=0

        while IFS= read -r dto_file; do
            [[ -n "$dto_file" ]] || continue
            local dto_name
            dto_name=$(basename "$dto_file" .java)
            module_dto_count=$((module_dto_count + 1))
            DTO_TOTAL=$((DTO_TOTAL + 1))

            # 使用 safe_count 统计注解
            local schema_count=$(safe_count "@Schema" "$dto_file")

            if safe_gt "$schema_count" 0; then
                module_with_schema=$((module_with_schema + 1))
                module_schema_count=$((module_schema_count + schema_count))
                DTO_WITH_SCHEMA=$((DTO_WITH_SCHEMA + 1))
                SCHEMA_ANNOTATION_COUNT=$((SCHEMA_ANNOTATION_COUNT + schema_count))

                [ $VERBOSE -eq 1 ] && log_success "$dto_name (@Schema:$schema_count)"
            else
                log_warn "$dto_name 没有 @Schema 注解"
                log_to_report "[⚠] $dto_name - 缺少 @Schema 注解"
            fi
        done <<< "$dto_files"

        log_info "$module_name: DTO=$module_dto_count, 有注解=$module_with_schema, @Schema总数=$module_schema_count"
        log_to_report "  统计: DTO=$module_dto_count, 有注解=$module_with_schema, @Schema总数=$module_schema_count"
        echo ""
    done

    log_info "DTO 总统计: 总数=$DTO_TOTAL, 有@Schema=$DTO_WITH_SCHEMA, 缺失=$((DTO_TOTAL - DTO_WITH_SCHEMA))"
    log_info "           @Schema注解总数=$SCHEMA_ANNOTATION_COUNT"
    log_to_report ""
    log_to_report "总统计: DTO总数=$DTO_TOTAL, 有注解=$DTO_WITH_SCHEMA, @Schema总数=$SCHEMA_ANNOTATION_COUNT"
    echo ""
}

###############################################################################
# 生成最终报告
###############################################################################

generate_summary() {
    print_header "检查总结"
    log_to_report ""
    log_to_report "========================================"
    log_to_report "检查总结"
    log_to_report "========================================"

    echo ""
    echo "┌─────────────────────────────────────────────┐"
    echo "│          SpringDoc 注解完整性报告            │"
    echo "├─────────────────────────────────────────────┤"

    # 使用公共函数库的 print_stats
    printf "│ %-20s │ %8s │ %8s │\n" "项目" "总数" "已注解"
    echo "├─────────────────────────────────────────────┤"
    print_stats "Router 类" "$ROUTER_TOTAL" "$ROUTER_WITH_ANNOTATION" | sed 's/^/│ /' | sed 's/$/│/'
    print_stats "Handler 方法" "$HANDLER_METHOD_TOTAL" "$HANDLER_METHOD_WITH_OPERATION" | sed 's/^/│ /' | sed 's/$/│/'
    print_stats "DTO 类" "$DTO_TOTAL" "$DTO_WITH_SCHEMA" | sed 's/^/│ /' | sed 's/$/│/'
    echo "├─────────────────────────────────────────────┤"
    printf "│ %-20s │ %18d │\n" "@Schema 注解总数" "$SCHEMA_ANNOTATION_COUNT"
    echo "└─────────────────────────────────────────────┘"
    echo ""

    # 使用公共函数库的 calculate_percentage
    echo "覆盖率统计:"
    local router_coverage=$(calculate_percentage "$ROUTER_WITH_ANNOTATION" "$ROUTER_TOTAL")
    local handler_coverage=$(calculate_percentage "$HANDLER_METHOD_WITH_OPERATION" "$HANDLER_METHOD_TOTAL")
    local dto_coverage=$(calculate_percentage "$DTO_WITH_SCHEMA" "$DTO_TOTAL")
    echo "  Router @RouterOperations: ${router_coverage}%"
    echo "  Handler @Operation: ${handler_coverage}%"
    echo "  DTO @Schema: ${dto_coverage}%"
    echo ""
    log_to_report "覆盖率:"
    log_to_report "  Router @RouterOperations: ${router_coverage}%"
    log_to_report "  Handler @Operation: ${handler_coverage}%"
    log_to_report "  DTO @Schema: ${dto_coverage}%"

    # 判断是否通过
    local router_missing=$((ROUTER_TOTAL - ROUTER_WITH_ANNOTATION))
    local handler_missing=$((HANDLER_METHOD_TOTAL - HANDLER_METHOD_WITH_OPERATION))
    local dto_missing=$((DTO_TOTAL - DTO_WITH_SCHEMA))

    if safe_eq "$router_missing" 0 && safe_eq "$handler_missing" 0 && safe_lt "$dto_missing" 5; then
        log_success "✓ SpringDoc 注解检查通过！"
        return 0
    else
        log_failure "✗ SpringDoc 注解检查未通过，发现缺失注解"
        return 1
    fi
}

###############################################################################
# 主函数
###############################################################################

main() {
    parse_arguments "$@"

    # 获取项目根目录
    PROJECT_ROOT=$(get_project_root) || exit 1
    cd "$PROJECT_ROOT"

    echo ""
    print_header "SpringDoc/Swagger 注解完整性检查"
    echo "项目路径: $PROJECT_ROOT"
    echo "检查时间: $(date '+%Y-%m-%d %H:%M:%S')"
    echo ""

    # 初始化报告文件
    if [ -n "$REPORT_FILE" ]; then
        if [ -d "$REPORT_FILE" ] || ! : > "$REPORT_FILE"; then
            log_error "报告文件不可写: $REPORT_FILE"
            exit 1
        fi
        log_to_report "SpringDoc/Swagger 注解完整性检查报告"
        log_to_report "生成时间: $(date '+%Y-%m-%d %H:%M:%S')"
        log_to_report "项目路径: $PROJECT_ROOT"
        log_to_report ""
    fi

    # 执行检查
    local check_status=0
    check_router_annotations
    local router_status=$?
    if [ "$router_status" -ne 0 ]; then
        check_status=$router_status
    fi
    check_handler_annotations
    local handler_status=$?
    if [ "$handler_status" -ne 0 ] && [ "$check_status" -eq 0 ]; then
        check_status=$handler_status
    fi
    check_dto_schema_annotations
    local dto_status=$?
    if [ "$dto_status" -ne 0 ] && [ "$check_status" -eq 0 ]; then
        check_status=$dto_status
    fi

    # 生成报告
    generate_summary
    local result=$?
    if [ "$check_status" -ne 0 ]; then
        result=$check_status
    fi

    [ -n "$REPORT_FILE" ] && log_info "详细报告已保存到: $REPORT_FILE"

    exit $result
}

# 运行主函数
main "$@"

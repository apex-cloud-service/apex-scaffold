#!/bin/bash

###############################################################################
# SpringDoc 注解覆盖率检查脚本（用于 CI/CD）
#
# 功能：检查 SpringDoc 注解覆盖率是否达到要求
#
# 使用方法：
#   ./scripts/ci/check-springdoc-coverage.sh
#   ./scripts/ci/check-springdoc-coverage.sh --min-router 80 --min-handler 95 --min-dto 80
#
# 退出码：
#   0 - 所有检查通过
#   1 - 覆盖率不达标
#
# 作者：Apex Scaffold Team
# 日期：2026-09-20
###############################################################################

set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
source "${SCRIPT_DIR}/../lib/common.sh"
PROJECT_ROOT=$(get_project_root)

validate_percentage() {
    local value="${1:-}"
    [[ $value =~ ^[0-9]+$ ]] || return 1
    value=$(clean_number "$value")
    [[ ${#value} -le 3 ]] && safe_lt "$value" 101 || return 1
    printf '%s\n' "$value"
}

# 默认最低覆盖率要求（百分比）
MIN_ROUTER_COVERAGE=100
MIN_HANDLER_COVERAGE=100
MIN_DTO_COVERAGE=100

# 解析命令行参数
while [[ $# -gt 0 ]]; do
    case $1 in
        --min-router)
            if ! MIN_ROUTER_COVERAGE=$(validate_percentage "${2:-}"); then
                log_error "$1 需要 0 到 100 的整数"
                exit 1
            fi
            shift 2
            ;;
        --min-handler)
            if ! MIN_HANDLER_COVERAGE=$(validate_percentage "${2:-}"); then
                log_error "$1 需要 0 到 100 的整数"
                exit 1
            fi
            shift 2
            ;;
        --min-dto)
            if ! MIN_DTO_COVERAGE=$(validate_percentage "${2:-}"); then
                log_error "$1 需要 0 到 100 的整数"
                exit 1
            fi
            shift 2
            ;;
        --help|-h)
            echo "使用方法: $0 [选项]"
            echo ""
            echo "选项:"
            echo "    --min-router N    Router @RouterOperations 最低覆盖率（默认：100%）"
            echo "    --min-handler N   Handler @Operation 最低覆盖率（默认：100%）"
            echo "    --min-dto N       DTO @Schema 最低覆盖率（默认：100%）"
            echo "    --help, -h        显示此帮助信息"
            exit 0
            ;;
        *)
            log_error "未知选项: $1"
            exit 1
            ;;
    esac
done

echo "========================================"
echo "SpringDoc 注解覆盖率检查（CI/CD）"
echo "========================================"
echo "项目路径: ${PROJECT_ROOT}"
echo "最低覆盖率要求:"
echo "  Router @RouterOperations: ${MIN_ROUTER_COVERAGE}%"
echo "  Handler @Operation: ${MIN_HANDLER_COVERAGE}%"
echo "  DTO @Schema: ${MIN_DTO_COVERAGE}%"
echo ""

# 临时报告文件
REPORT_FILE=$(create_temp_file springdoc-coverage)
setup_temp_cleanup "$REPORT_FILE"

cd -- "$PROJECT_ROOT"
CHECK_STATUS=0
bash "$PROJECT_ROOT/scripts/dev/check/check-springdoc-annotations-v2.sh" --report "$REPORT_FILE" > /dev/null || CHECK_STATUS=$?
if [[ $CHECK_STATUS != 0 && $CHECK_STATUS != 1 ]]; then
    log_error "检查脚本执行失败，退出码: $CHECK_STATUS"
    exit "$CHECK_STATUS"
fi

extract_coverage() {
    local label="$1"
    local value
    value=$(awk -v label="$label" '
        index($0, "  " label) == 1 {
            count++
            value = substr($0, length(label) + 3)
            if (value !~ /^ [0-9]+%$/) invalid = 1
            sub(/^ /, "", value)
            sub(/%$/, "", value)
        }
        END {
            if (count != 1 || invalid) exit 1
            print value
        }
    ' "$REPORT_FILE") || {
        log_error "覆盖率报告字段缺失、重复或无效: $label"
        return 1
    }
    validate_percentage "$value" || {
        log_error "覆盖率超出范围: $label"
        return 1
    }
}

ROUTER_COVERAGE=$(extract_coverage "Router @RouterOperations:")
HANDLER_COVERAGE=$(extract_coverage "Handler @Operation:")
DTO_COVERAGE=$(extract_coverage "DTO @Schema:")

echo "实际覆盖率:"
echo "  Router @RouterOperations: ${ROUTER_COVERAGE}%"
echo "  Handler @Operation: ${HANDLER_COVERAGE}%"
echo "  DTO @Schema: ${DTO_COVERAGE}%"
echo ""

# 检查覆盖率
EXIT_CODE=$CHECK_STATUS

check_coverage() {
    local name="$1"
    local actual="$2"
    local required="$3"
    
    if safe_lt "$actual" "$required"; then
        log_failure "${name} 覆盖率不达标: ${actual}% < ${required}%"
        EXIT_CODE=1
    else
        log_success "${name} 覆盖率达标: ${actual}% >= ${required}%"
    fi
}

echo "覆盖率检查结果:"
check_coverage "Router @RouterOperations" "$ROUTER_COVERAGE" "$MIN_ROUTER_COVERAGE"
check_coverage "Handler @Operation" "$HANDLER_COVERAGE" "$MIN_HANDLER_COVERAGE"
check_coverage "DTO @Schema" "$DTO_COVERAGE" "$MIN_DTO_COVERAGE"

echo ""
if safe_eq "$EXIT_CODE" 0; then
    log_success "所有覆盖率检查通过！"
else
    log_failure "覆盖率检查未通过"
    echo "运行完整检查: bash scripts/dev/check/check-springdoc-annotations.sh --verbose --report report.txt"
fi

exit "$EXIT_CODE"

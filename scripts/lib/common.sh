#!/bin/bash
###############################################################################
# common.sh - Apex Scaffold 脚本公共函数库
#
# 提供统一的工具函数，包括：
# 1. 安全的 find 命令（自动排除特殊目录）
# 2. 安全的计数函数（处理数字格式）
# 3. 日志输出函数（统一的输出格式）
# 4. 错误处理函数（统一的错误处理机制）
# 5. 颜色输出函数（美化终端输出）
#
# 使用方法：
#   source "$(dirname "$0")/lib/common.sh" 或
#   source "${SCRIPT_DIR}/lib/common.sh"
#
# 作者：Apex Scaffold Team
# 日期：2026-09-20
# 版本：v1.0.0
###############################################################################

if [[ ${_APEX_COMMON_LIB_LOADED:-0} == 1 ]]; then
    return 0
fi
_APEX_COMMON_LIB_LOADED=1

# 颜色定义
readonly COLOR_RED='\033[0;31m'
readonly COLOR_GREEN='\033[0;32m'
readonly COLOR_YELLOW='\033[1;33m'
readonly COLOR_BLUE='\033[0;34m'
readonly COLOR_CYAN='\033[0;36m'
readonly COLOR_NC='\033[0m' # No Color

# 日志级别
readonly LOG_LEVEL_DEBUG=0
readonly LOG_LEVEL_INFO=1
readonly LOG_LEVEL_WARN=2
readonly LOG_LEVEL_ERROR=3

# 当前日志级别（可通过环境变量 LOG_LEVEL 覆盖）
CURRENT_LOG_LEVEL=${LOG_LEVEL:-$LOG_LEVEL_INFO}

# 需要排除的目录列表
readonly EXCLUDE_DIRS=(
    ".trae"
    ".workbuddy"
    "target"
    ".git"
    ".idea"
    ".vscode"
    "node_modules"
    ".settings"
    "build"
    # 发布产物目录：release.sh 会把 docs/scripts 复制进 dist/，其中相对链接
    # 指向仓库内其他路径（如 ../../AGENTS.md），全仓扫描会产生大量误报。
    "dist"
    "logs"
)

###############################################################################
# 日志输出函数
###############################################################################

# 输出调试日志
# 用法：log_debug "调试信息"
log_debug() {
    if [ "$CURRENT_LOG_LEVEL" -le "$LOG_LEVEL_DEBUG" ]; then
        echo -e "${COLOR_CYAN}[DEBUG]${COLOR_NC} $*" >&2
    fi
}

# 输出信息日志
# 用法：log_info "信息内容"
log_info() {
    if [ "$CURRENT_LOG_LEVEL" -le "$LOG_LEVEL_INFO" ]; then
        echo -e "${COLOR_BLUE}[INFO]${COLOR_NC} $*" >&2
    fi
}

# 输出警告日志
# 用法：log_warn "警告信息"
log_warn() {
    if [ "$CURRENT_LOG_LEVEL" -le "$LOG_LEVEL_WARN" ]; then
        echo -e "${COLOR_YELLOW}[WARN]${COLOR_NC} $*" >&2
    fi
}

# 输出错误日志
# 用法：log_error "错误信息"
log_error() {
    if [ "$CURRENT_LOG_LEVEL" -le "$LOG_LEVEL_ERROR" ]; then
        echo -e "${COLOR_RED}[ERROR]${COLOR_NC} $*" >&2
    fi
}

# 输出成功信息
# 用法：log_success "成功信息"
log_success() {
    echo -e "${COLOR_GREEN}✓${COLOR_NC} $*"
}

# 输出失败信息
# 用法：log_failure "失败信息"
log_failure() {
    echo -e "${COLOR_RED}✗${COLOR_NC} $*"
}

###############################################################################
# 文件查找函数
###############################################################################

# 安全的 find 命令，自动排除特殊目录
# 用法：safe_find <路径> <文件名模式>
# 示例：safe_find "apex-*/src/main/java" "*.java"
safe_find() {
    if [[ $# != 2 || -z ${1:-} || -z ${2:-} ]]; then
        log_error "用法: safe_find <路径> <文件名模式>"
        return 2
    fi
    safe_find_custom "$1" -name "$2" -type f
}

# 安全的 find 命令（支持自定义类型）
# 用法：safe_find_custom <路径> <find其他参数...>
# 示例：safe_find_custom "apex-common/src" -name "FeatureGate.java"
safe_find_custom() {
    if [[ $# == 0 || -z ${1:-} ]]; then
        log_error "safe_find_custom: 缺少路径参数"
        return 2
    fi
    local search_path="$1"
    shift
    local arg
    local has_action=0
    for arg in "$@"; do
        case "$arg" in
            -depth|-delete|-prune|-quit|-L|-H|-P)
                log_error "safe_find_custom: 不支持影响剪枝的参数: $arg"
                return 2
                ;;
            -print|-print0|-printf|-fprintf|-fprint|-fprint0|-ls|-fls|-exec|-execdir|-ok|-okdir)
                has_action=1
                ;;
        esac
    done
    local roots=()
    local root dir excluded
    if [[ -e "$search_path" || -L "$search_path" ]]; then
        roots=("$search_path")
    else
        while IFS= read -r -d '' root; do
            roots+=("$root")
        done < <(
            shopt -u failglob
            shopt -s nullglob
            set +f
            unset GLOBIGNORE
            IFS=
            for root in $search_path; do
                printf '%s\0' "$root"
            done
        )
        if [[ ${#roots[@]} == 0 ]]; then
            log_error "safe_find_custom: 路径不存在或模式无匹配: $search_path"
            return 1
        fi
    fi
    local paths=()
    for root in "${roots[@]}"; do
        case "$root" in
            /*|./*|../*) ;;
            *) root="./$root" ;;
        esac
        excluded=0
        for dir in "${EXCLUDE_DIRS[@]}"; do
            case "$root/" in
                */"$dir"/*) excluded=1; break ;;
            esac
        done
        if [[ $excluded == 0 ]]; then
            paths+=("$root")
        fi
    done
    if [[ ${#paths[@]} == 0 ]]; then
        return 0
    fi
    local exclude_args=()
    for dir in "${EXCLUDE_DIRS[@]}"; do
        if [[ ${#exclude_args[@]} != 0 ]]; then
            exclude_args+=(-o)
        fi
        exclude_args+=(-name "$dir")
    done
    local expression=("$@")
    if [[ $# == 0 ]]; then
        expression=(-true)
    fi
    if [[ $has_action == 0 ]]; then
        expression=('(' "${expression[@]}" ')' -print)
    fi
    command find "${paths[@]}" '(' -type d '(' "${exclude_args[@]}" ')' -prune ')' -o '(' "${expression[@]}" ')'
}

###############################################################################
# 计数和数字处理函数
###############################################################################

# 安全的计数函数，处理 grep -c 的输出
# 用法：safe_count <模式> <文件>
# 示例：count=$(safe_count "@author" "$file")
safe_count() {
    if [[ $# != 2 || -z ${2:-} ]]; then
        log_error "用法: safe_count <模式> <文件>"
        return 2
    fi
    local result status
    if result=$(grep -c -e "$1" -- "$2"); then
        printf '%s\n' "$result"
    else
        status=$?
        if [[ $status == 1 ]]; then
            printf '0\n'
        else
            return "$status"
        fi
    fi
}

# 清理数字字符串，确保返回纯数字
# 用法：clean_number <字符串>
# 示例：num=$(clean_number "$raw_output")
clean_number() {
    local input="${1:-}"
    local cleaned=0
    if [[ $input =~ [0-9]+ ]]; then
        cleaned=${BASH_REMATCH[0]}
        while [[ ${#cleaned} -gt 1 && $cleaned == 0* ]]; do
            cleaned=${cleaned#0}
        done
    fi
    printf '%s\n' "$cleaned"
}

# 安全的数字比较（大于）
# 用法：safe_gt <数字1> <数字2> && echo "大于"
# 返回：0=真，1=假
safe_gt() {
    local num1=$(clean_number "$1")
    local num2=$(clean_number "$2")
    [ "$num1" -gt "$num2" ] 2>/dev/null
}

# 安全的数字比较（小于）
# 用法：safe_lt <数字1> <数字2> && echo "小于"
safe_lt() {
    local num1=$(clean_number "$1")
    local num2=$(clean_number "$2")
    [ "$num1" -lt "$num2" ] 2>/dev/null
}

# 安全的数字比较（等于）
# 用法：safe_eq <数字1> <数字2> && echo "等于"
safe_eq() {
    local num1=$(clean_number "$1")
    local num2=$(clean_number "$2")
    [ "$num1" -eq "$num2" ] 2>/dev/null
}

###############################################################################
# 错误处理函数
###############################################################################

# 检查命令是否存在
# 用法：require_command "mvnd" "Maven Daemon"
require_command() {
    local cmd="$1"
    local name="${2:-$cmd}"
    
    if ! command -v "$cmd" >/dev/null 2>&1; then
        log_error "必需命令 '$name' 未找到"
        log_error "请安装 $name 后重试"
        return 1
    fi
    return 0
}

# 检查目录是否存在
# 用法：require_directory "/path/to/dir" "项目目录"
require_directory() {
    local dir="$1"
    local name="${2:-目录}"
    
    if [ ! -d "$dir" ]; then
        log_error "$name 不存在: $dir"
        return 1
    fi
    return 0
}

# 检查文件是否存在
# 用法：require_file "/path/to/file" "配置文件"
require_file() {
    local file="$1"
    local name="${2:-文件}"
    
    if [ ! -f "$file" ]; then
        log_error "$name 不存在: $file"
        return 1
    fi
    return 0
}

# 设置错误陷阱
# 用法：setup_error_trap
setup_error_trap() {
    set -euo pipefail
    trap 'handle_error $? $LINENO' ERR
}

# 错误处理函数（内部使用）
handle_error() {
    local exit_code=$1
    local line_number=$2
    log_error "脚本在第 $line_number 行发生错误 (退出码: $exit_code)"
}

###############################################################################
# 路径处理函数
###############################################################################

# 获取脚本所在目录的绝对路径
# 用法：SCRIPT_DIR=$(get_script_dir)
get_script_dir() (
    CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[1]:-$0}")" && pwd -P
)

# 获取项目根目录（假设脚本在 scripts/ 或 scripts/xxx/ 下）
# 用法：PROJECT_ROOT=$(get_project_root)
get_project_root() (
    local script_dir
    script_dir=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[1]:-$0}")" && pwd -P) || return

    # 尝试不同的层级
    while :; do
        if [[ -f "$script_dir/pom.xml" ]]; then
            printf '%s\n' "$script_dir"
            return 0
        fi
        if [[ $script_dir == / ]]; then
            log_error "无法找到项目根目录（pom.xml）"
            return 1
        fi
        script_dir=${script_dir%/*}
        script_dir=${script_dir:-/}
    done
)

###############################################################################
# 格式化输出函数
###############################################################################

# 打印分隔线
# 用法：print_separator "=" 40
print_separator() {
    local char="${1:-=}"
    local length="${2:-40}"
    printf '%*s\n' "$length" | tr ' ' "$char"
}

# 打印标题
# 用法：print_header "检查标题"
print_header() {
    local title="$1"
    echo ""
    print_separator "=" 40
    echo -e "${COLOR_BLUE}$title${COLOR_NC}"
    print_separator "=" 40
    echo ""
}

# 打印小节标题
# 用法：print_section "小节标题"
print_section() {
    local title="$1"
    echo ""
    echo -e "${COLOR_CYAN}==> $title${COLOR_NC}"
}

###############################################################################
# 统计和汇总函数
###############################################################################

# 计算百分比
# 用法：percentage=$(calculate_percentage 13 18)
calculate_percentage() {
    local part=$(clean_number "$1")
    local total=$(clean_number "$2")
    
    if [ "$total" -eq 0 ]; then
        echo "0"
        return 0
    fi
    
    local result=$((part * 100 / total))
    echo "$result"
}

# 打印统计表格
# 用法：print_stats "Router 类" 18 13
print_stats() {
    local name="$1"
    local total="$2"
    local completed="$3"
    local percentage=$(calculate_percentage "$completed" "$total")
    
    printf "%-30s | %5d | %5d | %3d%%\n" "$name" "$total" "$completed" "$percentage"
}

###############################################################################
# 文件处理函数
###############################################################################

# 创建临时文件
# 用法：temp_file=$(create_temp_file "prefix")
create_temp_file() {
    local prefix="${1:-temp}"
    mktemp -t "${prefix}.XXXXXX"
}

# 清理临时文件（在脚本退出时自动调用）
# 用法：cleanup_temp_files "$temp_file1" "$temp_file2"
cleanup_temp_files() {
    local file status=0
    for file in "$@"; do
        if [[ -f "$file" || -L "$file" ]]; then
            rm -f -- "$file" || status=$?
        fi
    done
    return "$status"
}

# 设置临时文件自动清理
# 用法：setup_temp_cleanup "$temp_file"
setup_temp_cleanup() {
    local cleanup_command
    printf -v cleanup_command '%q ' cleanup_temp_files "$@"
    trap "$cleanup_command" EXIT
}

###############################################################################
# 初始化函数（可选）
###############################################################################

# 初始化公共库（设置严格模式和错误处理）
# 用法：init_common_lib
init_common_lib() {
    # 设置严格模式（可选，根据需要启用）
    # set -euo pipefail
    
    # 记录库已加载
    log_debug "common.sh 公共函数库已加载"
}

###############################################################################
# 版本信息
###############################################################################

# 获取库版本
get_common_lib_version() {
    echo "v1.0.0"
}

# 打印库信息
print_common_lib_info() {
    log_info "Apex Scaffold 公共函数库 $(get_common_lib_version)"
}

###############################################################################
# 使用示例（仅供参考，实际使用时删除或注释掉）
###############################################################################

# 示例：如何在脚本中使用这个库
#
# #!/bin/bash
# # my-script.sh
# 
# # 加载公共函数库
# SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# source "${SCRIPT_DIR}/lib/common.sh"
# 
# # 初始化（可选）
# init_common_lib
# 
# # 获取项目根目录
# PROJECT_ROOT=$(get_project_root) || exit 1
# log_info "项目根目录: $PROJECT_ROOT"
# 
# # 使用安全的 find
# log_section "查找 Java 文件"
# java_files=$(safe_find "apex-*/src/main/java" "*.java")
# file_count=$(echo "$java_files" | wc -l | tr -d ' ')
# log_info "找到 $file_count 个 Java 文件"
# 
# # 使用安全的计数
# for file in $java_files; do
#     count=$(safe_count "@author" "$file")
#     if safe_gt "$count" 0; then
#         log_success "$file 包含 @author 注解"
#     fi
# done

###############################################################################
# 库加载完成
###############################################################################

# 如果需要，可以在加载时自动初始化
# init_common_lib

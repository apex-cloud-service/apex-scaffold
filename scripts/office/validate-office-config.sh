#!/usr/bin/env bash
# ============================================================================
# 办公平台配置验证脚本
# ============================================================================
#
# 用途：验证办公平台环境变量配置的完整性和正确性
#
# 使用方法：
#   ./scripts/office/validate-office-config.sh [env-file]
#
# 示例：
#   ./scripts/office/validate-office-config.sh config/office-platforms.env
#
# 退出码：
#   0 - 配置验证通过
#   1 - 配置验证失败或缺少必需配置
#
# ============================================================================

set -euo pipefail

# 颜色定义
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 验证环境变量是否存在
check_env_var() {
    local var_name=$1
    local var_desc=$2
    local required=${3:-false}
    
    if [[ -z "${!var_name:-}" ]]; then
        if [[ "$required" == "true" ]]; then
            log_error "缺少必需环境变量: $var_name ($var_desc)"
            return 1
        else
            log_warn "可选环境变量未设置: $var_name ($var_desc)"
            return 0
        fi
    else
        log_info "✓ $var_name: [已设置]"
        return 0
    fi
}

# 验证 URL 格式
validate_url() {
    local url=$1
    local var_name=$2
    
    if [[ ! "$url" =~ ^https?:// ]]; then
        log_error "$var_name 必须是有效的 HTTP/HTTPS URL: $url"
        return 1
    fi
    
    log_info "✓ $var_name URL 格式正确"
    return 0
}

# 验证飞书配置
validate_feishu() {
    log_info "验证飞书配置..."
    local errors=0
    
    check_env_var "FEISHU_SECRET" "飞书应用密钥" "true" || ((errors++))
    check_env_var "FEISHU_SIGNING_SECRET" "飞书签名密钥" "false"
    
    return $errors
}

# 验证钉钉配置
validate_dingtalk() {
    log_info "验证钉钉配置..."
    local errors=0
    
    check_env_var "DINGTALK_SECRET" "钉钉应用密钥" "true" || ((errors++))
    check_env_var "DINGTALK_SIGNING_SECRET" "钉钉签名密钥" "false"
    
    return $errors
}

# 验证企业微信配置
validate_wecom() {
    log_info "验证企业微信配置..."
    local errors=0
    
    check_env_var "WECOM_SECRET" "企业微信应用密钥" "true" || ((errors++))
    check_env_var "WECOM_SIGNING_SECRET" "企业微信签名密钥" "false"
    check_env_var "WECOM_AGENT_ID" "企业微信应用 AgentId" "false"
    check_env_var "WECOM_CONTACT_SECRET" "企业微信通讯录密钥" "false"
    
    return $errors
}

# 验证全局配置
validate_global() {
    log_info "验证全局配置..."
    local errors=0
    
    check_env_var "APEX_OFFICE_ENABLED" "办公平台功能开关" "false"
    check_env_var "APEX_OFFICE_TIMEOUT" "HTTP 请求超时时间" "false"
    check_env_var "APEX_OFFICE_MAX_RETRIES" "HTTP 请求最大重试次数" "false"
    check_env_var "APEX_OFFICE_STATE_TTL" "OAuth state 有效期" "false"
    
    # 验证数值类型
    if [[ -n "${APEX_OFFICE_TIMEOUT:-}" ]] && ! [[ "${APEX_OFFICE_TIMEOUT}" =~ ^[0-9]+$ ]]; then
        log_error "APEX_OFFICE_TIMEOUT 必须是正整数"
        ((errors++))
    fi
    
    if [[ -n "${APEX_OFFICE_MAX_RETRIES:-}" ]] && ! [[ "${APEX_OFFICE_MAX_RETRIES}" =~ ^[0-9]+$ ]]; then
        log_error "APEX_OFFICE_MAX_RETRIES 必须是正整数"
        ((errors++))
    fi
    
    if [[ -n "${APEX_OFFICE_STATE_TTL:-}" ]] && ! [[ "${APEX_OFFICE_STATE_TTL}" =~ ^[0-9]+$ ]]; then
        log_error "APEX_OFFICE_STATE_TTL 必须是正整数"
        ((errors++))
    fi
    
    return $errors
}

# 验证回调地址
validate_callbacks() {
    log_info "验证回调地址配置..."
    local errors=0
    
    for var in FEISHU_REDIRECT_URI DINGTALK_REDIRECT_URI WECOM_REDIRECT_URI; do
        if [[ -n "${!var:-}" ]]; then
            validate_url "${!var}" "$var" || ((errors++))
        fi
    done
    
    return $errors
}

# 主函数
main() {
    local env_file="${1:-}"
    
    echo "============================================"
    echo "办公平台配置验证"
    echo "============================================"
    echo ""
    
    # 加载环境变量文件
    if [[ -n "$env_file" ]]; then
        if [[ ! -f "$env_file" ]]; then
            log_error "环境变量文件不存在: $env_file"
            exit 1
        fi
        
        log_info "加载环境变量文件: $env_file"
        set -a
        source "$env_file"
        set +a
    else
        log_info "使用当前环境变量"
    fi
    
    echo ""
    
    # 执行验证
    local total_errors=0
    
    validate_global || ((total_errors+=$?))
    echo ""
    
    validate_feishu || ((total_errors+=$?))
    echo ""
    
    validate_dingtalk || ((total_errors+=$?))
    echo ""
    
    validate_wecom || ((total_errors+=$?))
    echo ""
    
    validate_callbacks || ((total_errors+=$?))
    echo ""
    
    # 输出结果
    echo "============================================"
    if [[ $total_errors -eq 0 ]]; then
        log_info "✓ 配置验证通过！"
        exit 0
    else
        log_error "✗ 配置验证失败，发现 $total_errors 个错误"
        exit 1
    fi
}

# 执行主函数
main "$@"

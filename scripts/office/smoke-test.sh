#!/usr/bin/env bash
# ============================================================================
# 办公平台冒烟测试脚本
# ============================================================================
#
# 用途：快速验证办公平台集成的基本功能
#
# 使用方法：
#   ./scripts/office/smoke-test.sh <base-url> [platform]
#
# 示例：
#   ./scripts/office/smoke-test.sh http://localhost:8080
#   ./scripts/office/smoke-test.sh http://localhost:8080 FEISHU
#
# 参数：
#   base-url  - 后端服务地址（必需）
#   platform  - 测试特定平台（可选，不填则测试所有平台）
#
# 测试项：
#   1. 平台状态查询
#   2. 授权地址生成
#   3. 健康检查
#
# 退出码：
#   0 - 所有测试通过
#   1 - 存在测试失败
#
# ============================================================================

set -euo pipefail

# 颜色定义
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly NC='\033[0m'

# 配置
BASE_URL="${1:-http://localhost:8080}"
PLATFORM="${2:-}"

# 统计
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[PASS]${NC} $1"
}

log_fail() {
    echo -e "${RED}[FAIL]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

# HTTP 请求函数
http_get() {
    local url=$1
    local status_code
    local response
    
    response=$(curl -s -w "\n%{http_code}" "${url}" 2>&1) || {
        echo "CURL_ERROR"
        return 1
    }
    
    status_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | sed '$d')
    
    echo "$status_code|$body"
}

# 测试函数
run_test() {
    local test_name=$1
    local url=$2
    local expected_status=${3:-200}
    
    ((TOTAL_TESTS++))
    
    log_info "测试: $test_name"
    log_info "  URL: $url"
    
    local result
    result=$(http_get "$url")
    
    local status_code=$(echo "$result" | cut -d'|' -f1)
    local body=$(echo "$result" | cut -d'|' -f2-)
    
    if [[ "$status_code" == "CURL_ERROR" ]]; then
        log_fail "  请求失败: 无法连接到服务器"
        ((FAILED_TESTS++))
        return 1
    fi
    
    if [[ "$status_code" == "$expected_status" ]]; then
        log_success "  状态码: $status_code ✓"
        log_info "  响应: ${body:0:100}..."
        ((PASSED_TESTS++))
        return 0
    else
        log_fail "  状态码: $status_code (期望 $expected_status) ✗"
        log_info "  响应: $body"
        ((FAILED_TESTS++))
        return 1
    fi
}

# 测试平台状态
test_platform_status() {
    log_info "========================================="
    log_info "测试 1: 平台状态查询"
    log_info "========================================="
    
    run_test \
        "查询办公平台状态" \
        "${BASE_URL}/api/v1/office/status" \
        200
    
    echo ""
}

# 测试授权地址生成
test_authorization_url() {
    local platform=$1
    
    log_info "========================================="
    log_info "测试 2: 授权地址生成 ($platform)"
    log_info "========================================="
    
    run_test \
        "生成 $platform 授权地址" \
        "${BASE_URL}/api/v1/office/login?platform=${platform}" \
        200
    
    echo ""
}

# 测试所有平台
test_all_platforms() {
    log_info "========================================="
    log_info "测试所有平台"
    log_info "========================================="
    echo ""
    
    for platform in FEISHU DINGTALK WECOM; do
        test_authorization_url "$platform"
    done
}

# 测试健康检查
test_health() {
    log_info "========================================="
    log_info "测试 3: 健康检查"
    log_info "========================================="
    
    run_test \
        "健康检查端点" \
        "${BASE_URL}/actuator/health" \
        200 || log_warn "健康检查端点可能未启用"
    
    echo ""
}

# 主函数
main() {
    echo "============================================"
    echo "办公平台集成冒烟测试"
    echo "============================================"
    echo "服务地址: $BASE_URL"
    if [[ -n "$PLATFORM" ]]; then
        echo "测试平台: $PLATFORM"
    else
        echo "测试平台: 全部"
    fi
    echo "============================================"
    echo ""
    
    # 执行测试
    test_platform_status
    
    if [[ -n "$PLATFORM" ]]; then
        test_authorization_url "$PLATFORM"
    else
        test_all_platforms
    fi
    
    test_health
    
    # 输出结果
    echo "============================================"
    echo "测试结果汇总"
    echo "============================================"
    echo "总测试数: $TOTAL_TESTS"
    echo -e "${GREEN}通过: $PASSED_TESTS${NC}"
    echo -e "${RED}失败: $FAILED_TESTS${NC}"
    echo "============================================"
    
    if [[ $FAILED_TESTS -eq 0 ]]; then
        log_success "✓ 所有测试通过！"
        exit 0
    else
        log_fail "✗ 存在失败的测试"
        exit 1
    fi
}

# 检查参数
if [[ $# -eq 0 ]]; then
    echo "错误: 缺少必需参数"
    echo "用法: $0 <base-url> [platform]"
    echo "示例: $0 http://localhost:8080"
    echo "示例: $0 http://localhost:8080 FEISHU"
    exit 1
fi

# 执行主函数
main

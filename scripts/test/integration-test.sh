#!/bin/bash

# 前后端对接测试脚本
# 此脚本用于测试前后端接口对接的完整性

echo "=========================================="
echo "🔧 前后端对接测试"
echo "=========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 后端项目路径
BACKEND_DIR="/Users/work/apex-scaffold"
FRONTEND_DIR="/Users/work/apex-admin-web"

# 后端API端口
BACKEND_PORT=8080
BACKEND_URL="http://localhost:${BACKEND_PORT}"

# 测试结果
TESTS_PASSED=0
TESTS_FAILED=0
TESTS_TOTAL=0

# 记录测试结果
log_test() {
    local test_name="$1"
    local result="$2"
    local message="$3"
    
    TESTS_TOTAL=$((TESTS_TOTAL + 1))
    
    if [ "$result" = "PASS" ]; then
        echo -e "${GREEN}✓${NC} [$TESTS_TOTAL] $test_name: $message"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo -e "${RED}✗${NC} [$TESTS_TOTAL] $test_name: $message"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

# 检查后端服务是否运行
check_backend_running() {
    echo ""
    echo "=========================================="
    echo "1. 检查后端服务状态"
    echo "=========================================="
    
    if curl -s "${BACKEND_URL}/actuator/health" > /dev/null 2>&1; then
        log_test "后端服务健康检查" "PASS" "服务正在运行"
        return 0
    else
        log_test "后端服务健康检查" "FAIL" "服务未运行"
        return 1
    fi
}

# 测试后端API端点
test_backend_apis() {
    echo ""
    echo "=========================================="
    echo "2. 测试后端核心API端点"
    echo "=========================================="
    
    # 测试健康检查端点
    if curl -s "${BACKEND_URL}/actuator/health" | grep -q "UP"; then
        log_test "健康检查API" "PASS" "/actuator/health 返回UP"
    else
        log_test "健康检查API" "FAIL" "/actuator/health 未返回UP"
    fi
    
    # 测试API信息端点
    if curl -s "${BACKEND_URL}/actuator/info" > /dev/null 2>&1; then
        log_test "信息查询API" "PASS" "/actuator/info 可访问"
    else
        log_test "信息查询API" "FAIL" "/actuator/info 不可访问"
    fi
    
    # 测试Swagger文档端点
    if curl -s "${BACKEND_URL}/swagger-ui.html" > /dev/null 2>&1 || \
       curl -s "${BACKEND_URL}/swagger-ui/index.html" > /dev/null 2>&1; then
        log_test "Swagger文档" "PASS" "API文档可访问"
    else
        log_test "Swagger文档" "FAIL" "API文档不可访问"
    fi
    
    # 测试OpenAPI规范
    if curl -s "${BACKEND_URL}/v3/api-docs" > /dev/null 2>&1; then
        log_test "OpenAPI规范" "PASS" "/v3/api-docs 可访问"
    else
        log_test "OpenAPI规范" "FAIL" "/v3/api-docs 不可访问"
    fi
}

# 测试认证相关API
test_auth_apis() {
    echo ""
    echo "=========================================="
    echo "3. 测试认证相关API"
    echo "=========================================="
    
    # 测试登录端点（这里只测试端点可达性，不测试具体业务逻辑）
    local login_response=$(curl -s -w "%{http_code}" -o /dev/null \
        -X POST "${BACKEND_URL}/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -d '{"username":"test","password":"test"}')
    
    if [ "$login_response" = "200" ] || [ "$login_response" = "401" ] || [ "$login_response" = "400" ]; then
        log_test "登录API端点" "PASS" "端点可访问(HTTP $login_response)"
    else
        log_test "登录API端点" "FAIL" "端点不可访问(HTTP $login_response)"
    fi
}

# 测试业务API（需要认证）
test_business_apis() {
    echo ""
    echo "=========================================="
    echo "4. 测试业务API端点可达性"
    echo "=========================================="
    
    # 由于业务API需要认证，这里只测试端点的HTTP响应码
    # 401表示需要认证，说明端点存在
    
    # 测试用户管理API
    local user_response=$(curl -s -w "%{http_code}" -o /dev/null "${BACKEND_URL}/api/v1/users")
    if [ "$user_response" = "200" ] || [ "$user_response" = "401" ] || [ "$user_response" = "403" ]; then
        log_test "用户管理API" "PASS" "端点可访问(HTTP $user_response)"
    else
        log_test "用户管理API" "FAIL" "端点响应异常(HTTP $user_response)"
    fi
    
    # 测试角色管理API
    local role_response=$(curl -s -w "%{http_code}" -o /dev/null "${BACKEND_URL}/api/v1/roles")
    if [ "$role_response" = "200" ] || [ "$role_response" = "401" ] || [ "$role_response" = "403" ]; then
        log_test "角色管理API" "PASS" "端点可访问(HTTP $role_response)"
    else
        log_test "角色管理API" "FAIL" "端点响应异常(HTTP $role_response)"
    fi
    
    # 测试部门管理API
    local dept_response=$(curl -s -w "%{http_code}" -o /dev/null "${BACKEND_URL}/api/v1/depts")
    if [ "$dept_response" = "200" ] || [ "$dept_response" = "401" ] || [ "$dept_response" = "403" ]; then
        log_test "部门管理API" "PASS" "端点可访问(HTTP $dept_response)"
    else
        log_test "部门管理API" "FAIL" "端点响应异常(HTTP $dept_response)"
    fi
}

# 测试前端构建
test_frontend_build() {
    echo ""
    echo "=========================================="
    echo "5. 测试前端构建"
    echo "=========================================="
    
    cd "$FRONTEND_DIR" || exit 1
    
    if npm run build > /tmp/frontend-build.log 2>&1; then
        log_test "前端构建" "PASS" "构建成功"
        
        # 检查构建产物
        if [ -d "dist" ] && [ -f "dist/index.html" ]; then
            log_test "构建产物检查" "PASS" "dist目录和index.html存在"
        else
            log_test "构建产物检查" "FAIL" "构建产物不完整"
        fi
    else
        log_test "前端构建" "FAIL" "构建失败"
    fi
}

# 测试前端API配置
test_frontend_api_config() {
    echo ""
    echo "=========================================="
    echo "6. 检查前端API配置"
    echo "=========================================="
    
    cd "$FRONTEND_DIR" || exit 1
    
    # 检查是否有API配置文件
    if [ -f "src/api/request.ts" ] || [ -f "src/utils/request.ts" ]; then
        log_test "API请求配置" "PASS" "找到API请求配置文件"
    else
        log_test "API请求配置" "FAIL" "未找到API请求配置文件"
    fi
    
    # 检查环境配置
    if [ -f ".env" ] || [ -f ".env.development" ] || [ -f ".env.production" ]; then
        log_test "环境配置文件" "PASS" "找到环境配置文件"
    else
        log_test "环境配置文件" "FAIL" "未找到环境配置文件"
    fi
}

# 测试CORS配置
test_cors_config() {
    echo ""
    echo "=========================================="
    echo "7. 测试CORS配置"
    echo "=========================================="
    
    # 测试CORS预检请求
    local cors_response=$(curl -s -w "%{http_code}" -o /dev/null \
        -X OPTIONS "${BACKEND_URL}/api/v1/users" \
        -H "Origin: http://localhost:5173" \
        -H "Access-Control-Request-Method: GET")
    
    if [ "$cors_response" = "200" ] || [ "$cors_response" = "204" ]; then
        log_test "CORS预检请求" "PASS" "CORS配置正确(HTTP $cors_response)"
    else
        log_test "CORS预检请求" "WARN" "CORS可能未配置(HTTP $cors_response)"
    fi
}

# 生成测试报告
generate_report() {
    echo ""
    echo "=========================================="
    echo "📊 测试结果汇总"
    echo "=========================================="
    echo ""
    echo "总测试数: $TESTS_TOTAL"
    echo -e "${GREEN}通过数: $TESTS_PASSED${NC}"
    echo -e "${RED}失败数: $TESTS_FAILED${NC}"
    echo ""
    
    local pass_rate=0
    if [ $TESTS_TOTAL -gt 0 ]; then
        pass_rate=$(( TESTS_PASSED * 100 / TESTS_TOTAL ))
    fi
    
    echo "通过率: ${pass_rate}%"
    echo ""
    
    if [ $TESTS_FAILED -eq 0 ]; then
        echo -e "${GREEN}✅ 所有测试通过！${NC}"
        return 0
    else
        echo -e "${YELLOW}⚠️  部分测试失败，请检查日志${NC}"
        return 1
    fi
}

# 主流程
main() {
    echo "开始时间: $(date '+%Y-%m-%d %H:%M:%S')"
    echo ""
    
    # 检查后端服务
    if ! check_backend_running; then
        echo ""
        echo -e "${YELLOW}⚠️  后端服务未运行${NC}"
        echo "提示: 请先启动后端服务，然后重新运行此测试"
        echo ""
        echo "启动命令示例:"
        echo "  cd $BACKEND_DIR"
        echo "  java -jar apex-interfaces/target/apex-interfaces-1.2.0.jar"
        echo ""
        exit 1
    fi
    
    # 执行各项测试
    test_backend_apis
    test_auth_apis
    test_business_apis
    test_frontend_build
    test_frontend_api_config
    test_cors_config
    
    # 生成报告
    generate_report
    
    echo ""
    echo "结束时间: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "=========================================="
}

# 执行主流程
main

#!/bin/bash

echo "========================================="
echo "工作流前后端联调 - 快速启动脚本"
echo "========================================="
echo ""

# 检查后端服务是否运行
check_backend() {
    echo "检查后端服务..."
    if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo "✅ 后端服务已运行"
        return 0
    else
        echo "❌ 后端服务未运行"
        return 1
    fi
}

# 检查前端服务是否运行
check_frontend() {
    echo "检查前端服务..."
    if curl -s http://localhost:5174 > /dev/null 2>&1; then
        echo "✅ 前端服务已运行"
        return 0
    else
        echo "❌ 前端服务未运行"
        return 1
    fi
}

# 启动后端服务
start_backend() {
    echo ""
    echo "========================================="
    echo "启动后端服务"
    echo "========================================="
    cd /Users/work/apex-scaffold
    echo "执行命令: mvn spring-boot:run -pl apex-interfaces"
    echo "请在新终端窗口中执行上述命令"
    echo ""
}

# 启动前端服务
start_frontend() {
    echo ""
    echo "========================================="
    echo "启动前端服务"
    echo "========================================="
    cd /Users/work/apex-admin-web
    echo "执行命令: npm run dev"
    echo "前端服务已在端口 5174 启动"
    echo "访问地址: http://localhost:5174"
    echo ""
}

# 运行接口测试
run_api_test() {
    echo ""
    echo "========================================="
    echo "运行接口测试"
    echo "========================================="
    cd /Users/work/apex-scaffold
    
    if [ ! -f "test-workflow-api.sh" ]; then
        echo "❌ 测试脚本不存在"
        return 1
    fi
    
    chmod +x test-workflow-api.sh
    ./test-workflow-api.sh
}

# 主菜单
main_menu() {
    echo ""
    echo "========================================="
    echo "请选择操作"
    echo "========================================="
    echo "1. 检查服务状态"
    echo "2. 启动后端服务（提示命令）"
    echo "3. 前端服务已启动（5174端口）"
    echo "4. 运行接口测试"
    echo "5. 查看文档"
    echo "0. 退出"
    echo ""
    read -p "请输入选项 (0-5): " choice
    
    case $choice in
        1)
            check_backend
            check_frontend
            main_menu
            ;;
        2)
            start_backend
            main_menu
            ;;
        3)
            echo ""
            echo "✅ 前端服务已在端口 5174 启动"
            echo "访问地址: http://localhost:5174"
            echo ""
            main_menu
            ;;
        4)
            if check_backend; then
                run_api_test
            else
                echo ""
                echo "请先启动后端服务！"
            fi
            main_menu
            ;;
        5)
            echo ""
            echo "文档列表："
            echo "- docs/frontend-code-update-guide.md (前端更新指南)"
            echo "- docs/frontend-update-confirmation.md (前端更新确认)"
            echo "- docs/integration-test-guide.md (联调测试指南)"
            echo "- docs/webflux-handler-summary.md (后端实施总结)"
            echo "- docs/final-completion-report.md (最终完成报告)"
            echo ""
            main_menu
            ;;
        0)
            echo "退出"
            exit 0
            ;;
        *)
            echo "无效选项"
            main_menu
            ;;
    esac
}

# 启动脚本
echo "前端服务状态："
check_frontend
echo ""

main_menu

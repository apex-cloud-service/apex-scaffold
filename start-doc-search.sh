#!/bin/bash

# 文档搜索工具启动脚本

echo "================================================"
echo "  📚 Apex Scaffold 文档搜索工具启动器"
echo "================================================"
echo ""

# 检查是否在项目根目录
if [ ! -d "docs" ]; then
    echo "❌ 错误: 请在项目根目录执行此脚本"
    echo "   当前目录: $(pwd)"
    echo ""
    exit 1
fi

echo "✅ 检测到项目目录: $(pwd)"
echo ""

# 检查 Python 是否安装
if command -v python3 &> /dev/null; then
    PYTHON_CMD="python3"
elif command -v python &> /dev/null; then
    PYTHON_CMD="python"
else
    echo "❌ 错误: 未找到 Python"
    echo "   请先安装 Python 3"
    echo ""
    exit 1
fi

echo "✅ Python 版本: $($PYTHON_CMD --version)"
echo ""

# 检查端口是否被占用
if lsof -Pi :8080 -sTCP:LISTEN -t >/dev/null ; then
    echo "⚠️  警告: 端口 8080 已被占用"
    echo "   正在尝试使用端口 8081..."
    PORT=8081
else
    PORT=8080
fi

echo "🚀 启动 HTTP 服务器..."
echo "   端口: $PORT"
echo ""
echo "📝 访问方式:"
echo "   文档搜索工具: http://localhost:$PORT/docs/doc-search.html"
echo "   链接测试页面: http://localhost:$PORT/docs/link-test.html"
echo ""
echo "⌨️  按 Ctrl+C 停止服务器"
echo "================================================"
echo ""

# 启动服务器
cd "$(dirname "$0")" || exit
$PYTHON_CMD -m http.server $PORT

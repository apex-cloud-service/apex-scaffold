#!/bin/bash

# 文档搜索工具启动脚本
# 默认仅监听回环地址；设置 APEX_DOC_SEARCH_HOST=0.0.0.0 可对局域网开放（自担风险）

echo "================================================"
echo "  📚 Apex Scaffold 文档搜索工具启动器"
echo "================================================"
echo ""

# 基于脚本位置解析项目根目录
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# 检查是否在项目根目录
if [ ! -d "${PROJECT_ROOT}/docs" ]; then
    echo "❌ 错误: 未找到项目 docs 目录"
    echo "   项目根目录: ${PROJECT_ROOT}"
    echo ""
    exit 1
fi

echo "✅ 检测到项目目录: ${PROJECT_ROOT}"
echo ""

JAVA=${JAVA_HOME:+$JAVA_HOME/bin/}java
if ! command -v "$JAVA" &> /dev/null; then
    echo "❌ 错误: 未找到 Java 21"
    echo "   请先加载 scripts/build/init-build-env.sh"
    echo ""
    exit 1
fi

JAR="${PROJECT_ROOT}/apex-tools-deployment/target/local-deployment.jar"
if [ ! -f "$JAR" ]; then
    echo "❌ 错误: 未找到离线运维工具 JAR"
    echo "   请先运行: mvnd -B -pl apex-tools-deployment -am verify"
    echo ""
    exit 1
fi

# 绑定地址：默认仅本机可访问；设置 APEX_DOC_SEARCH_HOST=0.0.0.0 可对局域网开放
BIND_HOST="${APEX_DOC_SEARCH_HOST:-127.0.0.1}"

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
echo "   绑定: $BIND_HOST"
echo ""
if [ "$BIND_HOST" != "127.0.0.1" ] && [ "$BIND_HOST" != "localhost" ]; then
    echo "⚠️  警告: 服务器绑定 $BIND_HOST，项目文件将对网络内其他设备可见"
    echo ""
fi
echo "📝 访问方式:"
echo "   文档搜索工具: http://localhost:$PORT/docs/doc-search.html"
echo "   链接测试页面: http://localhost:$PORT/docs/link-test.html"
echo ""
echo "⌨️  按 Ctrl+C 停止服务器"
echo "================================================"
echo ""

# 启动服务器（静态文件服务，默认仅监听回环地址）
exec "$JAVA" -cp "$JAR" com.apex.scaffold.tools.deployment.DocSearchServer "$PORT" "$PROJECT_ROOT" "$BIND_HOST"

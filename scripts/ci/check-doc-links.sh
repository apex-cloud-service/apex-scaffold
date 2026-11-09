#!/bin/bash

###############################################################################
# check-doc-links.sh - 扫描全仓 Markdown/HTML 本地路径及标题/HTML 锚点的有效性
# 校验项：路径存在、大小写精确匹配（兼容 Linux/GitHub）、目标未被 .gitignore 排除、
#         Markdown 标题与 HTML 锚点存在。目录链接以该目录 README.md 作为锚点文档。
# 用法：./scripts/ci/check-doc-links.sh  无参数；检查逻辑由 apex-tools-deployment
#       fat jar 中的 DocLinksChecker 提供，JAR 缺失时经 init-build-env.sh 自动构建
# 作者：Apex Team    创建：2026-09-13
###############################################################################
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../lib/common.sh"
PROJECT_ROOT="$(get_project_root)"

require_directory "$PROJECT_ROOT" || exit 1
JAR="$PROJECT_ROOT/apex-tools-deployment/target/local-deployment.jar"

if [ ! -f "$JAR" ]; then
    log_info "未找到离线运维工具 JAR，自动构建 apex-tools-deployment ..."
    # init-build-env.sh 依赖 sdkman 的 zsh 语法，macOS bash 3.2 无法直接 source，
    # 统一经子进程加载环境后原地构建；sdk env 需在项目根读取 .sdkmanrc
    BUILD_SCRIPT='cd -- "$APEX_ROOT" && source scripts/build/init-build-env.sh && mvnd -T4 -B -pl apex-tools-deployment -am verify'
    set +e
    if command -v zsh >/dev/null 2>&1; then
        APEX_ROOT="$PROJECT_ROOT" zsh -c "$BUILD_SCRIPT"
    else
        APEX_ROOT="$PROJECT_ROOT" bash -c "$BUILD_SCRIPT"
    fi
    BUILD_STATUS=$?
    set -e
    if [ "$BUILD_STATUS" -ne 0 ]; then
        log_error "自动构建失败（exit $BUILD_STATUS）；请手动执行: source scripts/build/init-build-env.sh && mvnd -T4 -B -pl apex-tools-deployment -am verify"
        exit 1
    fi
    if [ ! -f "$JAR" ]; then
        log_error "构建完成仍未找到 JAR: $JAR"
        exit 1
    fi
fi

JAVA=${JAVA_HOME:+$JAVA_HOME/bin/}java
require_command "$JAVA" "Java 21" || exit 1
SCAN_DIR=$(mktemp -d "${TMPDIR:-/tmp}/doc-links.XXXXXX")
trap 'rm -rf "$SCAN_DIR"' EXIT
safe_find_custom "$PROJECT_ROOT" '(' -iname '*.md' -o -iname '*.html' -o -iname '*.htm' ')' -type f -print0 > "$SCAN_DIR/files"
log_info "开始扫描全仓 Markdown/HTML 本地路径与锚点（含 README、AGENTS 和模块文档）..."

exec "$JAVA" -Dapex.root="$PROJECT_ROOT" -cp "$JAR" \
    com.apex.scaffold.tools.deployment.DocLinksChecker "$SCAN_DIR/files" "$PROJECT_ROOT"

#!/bin/bash

###############################################################################
# OpenAPI 文档版本归档脚本
#
# 功能：生成 OpenAPI 文档并按版本号 + git 提交哈希归档到 docs/03-api/openapi/ 目录，
#       同时更新 latest 软链，便于追溯历史版本。
#
# 使用方法：
#   ./scripts/dev/openapi/archive-openapi.sh
#   ./scripts/dev/openapi/archive-openapi.sh --version 1.3.0
#
# 退出码：
#   0 - 归档成功
#   1 - 归档失败
#
# 作者：Apex Scaffold Team
# 日期：2026-10-01
###############################################################################

set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
source "${SCRIPT_DIR}/../../lib/common.sh"
PROJECT_ROOT=$(get_project_root)
cd -- "$PROJECT_ROOT"

ARCHIVE_DIR="docs/03-api/openapi"
VERSION=""
GIT_HASH=""

# 解析参数
while [[ $# -gt 0 ]]; do
    case $1 in
        --version)
            if [[ $# -lt 2 || -z ${2:-} || $2 == -* ]]; then
                log_error "$1 需要版本号"
                exit 1
            fi
            VERSION="$2"
            shift 2
            ;;
        --help|-h)
            echo "使用方法: $0 [选项]"
            echo ""
            echo "选项:"
            echo "    --version VERSION  指定版本号（默认从 apex-build.properties 读取）"
            echo "    --help, -h         显示此帮助信息"
            exit 0
            ;;
        *)
            log_error "未知选项: $1"
            exit 1
            ;;
    esac
done

# 获取版本号
if [[ -z "$VERSION" ]]; then
    if [[ -f "apex-build.properties" ]]; then
        VERSION=$(grep -E '^apex\.version=' apex-build.properties | cut -d= -f2 | tr -d '[:space:]')
    fi
fi
if [[ -z "$VERSION" && -f "pom.xml" ]]; then
    VERSION=$(awk '
        /<artifactId>apex-scaffold<\/artifactId>/ { found = 1; next }
        found && /<version>/ {
            value = $0
            sub(/.*<version>/, "", value)
            sub(/<\/version>.*/, "", value)
            if (value !~ /\$\{/) {
                print value
                exit
            }
        }
    ' pom.xml | tr -d '[:space:]')
fi
if [[ -z "$VERSION" ]]; then
    VERSION="0.0.0"
    log_warn "未找到版本号，使用默认值: $VERSION"
fi

# 获取 git 提交哈希
if command -v git &>/dev/null && git rev-parse --git-dir &>/dev/null; then
    GIT_HASH=$(git rev-parse --short HEAD 2>/dev/null || echo "nogit")
else
    GIT_HASH="nogit"
fi

STAMP="${VERSION}-${GIT_HASH}"
log_info "归档版本: $STAMP"

# 运行导出测试
log_info "正在生成 OpenAPI 文档..."
# 优先使用环境中已有的 mvnd；若不可用则尝试通过 sdkman 加载构建环境
if ! command -v mvnd &>/dev/null; then
    set +u
    source ./scripts/build/init-build-env.sh 2>/dev/null || log_warn "构建环境初始化可能不完整，尝试直接调用 mvnd"
    set -u
fi

if ! command -v mvnd &>/dev/null; then
    log_error "未找到 mvnd 命令，请先执行 source ./scripts/build/init-build-env.sh"
    exit 1
fi

mvnd -pl apex-system -am \
    -Dtest=OpenApiExportTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    test 2>&1 | tail -5

EXPORT_DIR="apex-system/target/openapi-export"
if [[ ! -f "${EXPORT_DIR}/openapi.json" ]]; then
    log_error "OpenAPI 导出失败：未找到 ${EXPORT_DIR}/openapi.json"
    exit 1
fi

# 归档
mkdir -p "$ARCHIVE_DIR"
cp "${EXPORT_DIR}/openapi.json" "${ARCHIVE_DIR}/openapi-${STAMP}.json"
cp "${EXPORT_DIR}/openapi.yaml" "${ARCHIVE_DIR}/openapi-${STAMP}.yaml" 2>/dev/null || true

# 更新 latest 软链/副本
cp -f "${EXPORT_DIR}/openapi.json" "${ARCHIVE_DIR}/openapi-latest.json"
if [[ -f "${EXPORT_DIR}/openapi.yaml" ]]; then
    cp -f "${EXPORT_DIR}/openapi.yaml" "${ARCHIVE_DIR}/openapi-latest.yaml"
fi

# 记录归档清单
MANIFEST="${ARCHIVE_DIR}/MANIFEST.md"
cat > "$MANIFEST" <<EOF
# OpenAPI 文档归档清单

最后归档时间：$(date '+%Y-%m-%d %H:%M:%S')
归档版本：${STAMP}
Git 提交：${GIT_HASH}

| 文件 | 说明 |
|---|---|
| openapi-latest.json | 最新版本 OpenAPI 文档（JSON） |
| openapi-latest.yaml | 最新版本 OpenAPI 文档（YAML） |
| openapi-${STAMP}.json | 版本 ${STAMP} 的 OpenAPI 文档（JSON） |
EOF
if [[ -f "${EXPORT_DIR}/openapi.yaml" ]]; then
    echo "| openapi-${STAMP}.yaml | 版本 ${STAMP} 的 OpenAPI 文档（YAML） |" >> "$MANIFEST"
fi

# 统计已归档版本数
ARCHIVE_COUNT=$(ls "${ARCHIVE_DIR}"/openapi-*.json 2>/dev/null | grep -v "latest" | wc -l | tr -d ' ')

log_success "OpenAPI 文档归档完成"
log_info "  归档目录: ${ARCHIVE_DIR}"
log_info "  版本: ${STAMP}"
log_info "  已归档版本数: ${ARCHIVE_COUNT}"

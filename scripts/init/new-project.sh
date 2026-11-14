#!/usr/bin/env zsh
# ============================================================
# Apex Scaffold 一键初始化脚本（新项目模板化）
#
# 功能: 复制脚手架 → 全局替换项目名/包名/端口/库名 → 清理内部资产
#       → 输出启动指引与验证清单。让"买来即用"成为现实。
#
# 用法:
#   ./scripts/init/new-project.sh --name my-oms --package com.acme.oms \
#       --port 8080 --db oms_data [--target /path/to/output] [--profile standard]
#
# 参数:
#   --name     新项目名（替换 apex-scaffold 标识，建议小写中划线，如 my-oms）
#   --package  新包名（替换 com.apex.scaffold，如 com.acme.oms）
#   --port     应用端口（默认 8080；管理端口自动 +1 为 8081）
#   --db       数据库名（默认 apex_data 替换）
#   --target   输出目录（默认 ./<name>，相对于脚本执行目录的上级）
#   --profile  默认构建档位 basic|standard|enterprise（默认 standard，仅改文档默认值）
#
# 依赖: rsync / find / sed（macOS 与 Linux 通用，sed -i 语法自适应）
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# ---------- 参数解析 ----------
NAME=""
PACKAGE=""
PORT=8080
DB_NAME="apex_data"
TARGET=""
PROFILE="standard"

usage() {
    sed -n '4,22p' "$0" | sed 's/^# \{0,1\}//'
    exit 2
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --name) NAME="$2"; shift 2 ;;
        --package) PACKAGE="$2"; shift 2 ;;
        --port) PORT="$2"; shift 2 ;;
        --db) DB_NAME="$2"; shift 2 ;;
        --target) TARGET="$2"; shift 2 ;;
        --profile) PROFILE="$2"; shift 2 ;;
        -h|--help) usage ;;
        *) echo "❌ 未知参数: $1"; usage ;;
    esac
done

[[ -z "$NAME" ]] && { echo "❌ 必须指定 --name"; usage; }
[[ -z "$PACKAGE" ]] && { echo "❌ 必须指定 --package"; usage; }
[[ "$NAME" =~ ^[a-z][a-z0-9-]*$ ]] || { echo "❌ --name 需为小写字母/数字/中划线（如 my-oms）"; exit 1; }
[[ "$PACKAGE" =~ ^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+$ ]] || { echo "❌ --package 需为合法 Java 包名（如 com.acme.oms）"; exit 1; }
[[ "$PROFILE" == "basic" || "$PROFILE" == "standard" || "$PROFILE" == "enterprise" ]] || {
    echo "❌ --profile 仅支持 basic/standard/enterprise"; exit 1; }

TARGET="${TARGET:-${PROJECT_ROOT}/${NAME}}"
[[ -e "$TARGET" ]] && { echo "❌ 目标目录已存在: $TARGET"; exit 1; }

MANAGE_PORT=$((PORT + 1))
echo "🔧 初始化参数: name=${NAME} package=${PACKAGE} port=${PORT}/${MANAGE_PORT} db=${DB_NAME} profile=${PROFILE}"
echo "📁 目标目录: ${TARGET}"

# ---------- 1. 复制脚手架（排除内部资产与构建产物） ----------
echo "=== 1/5 复制脚手架 ==="
rsync -a \
    --exclude '.git/' \
    --exclude '.workbuddy/' \
    --exclude 'backups/' \
    --exclude 'logs/' \
    --exclude 'target/' \
    --exclude 'dist/' \
    --exclude 'generated/' \
    --exclude 'node_modules/' \
    --exclude '*.log' \
    --exclude '.idea/' \
    --exclude '.vscode/' \
    "${PROJECT_ROOT}/" "${TARGET}/"

# ---------- 2. 全局替换标识 ----------
echo "=== 2/5 全局替换标识 ==="
# 替换顺序: 先长 token（包名），再短 token（项目名/库名/端口）
find "${TARGET}" -type f \( -name '*.java' -o -name '*.yml' -o -name '*.yaml' -o -name '*.xml' \
    -o -name '*.sql' -o -name '*.properties' -o -name '*.md' -o -name '*.sh' -o -name '*.service' \
    -o -name '*.json' -o -name '*.ftl' -o -name '*.env*' -o -name 'Containerfile*' -o -name '.sdkmanrc' \
    -o -name '*.html' -o -name '*.js' \) -print0 | while IFS= read -r -d '' f; do
    # 包名替换（com.apex.scaffold → newPackage；com.apex.example → newPackage.example）
    sed -i '' "s|com\.apex\.scaffold|${PACKAGE}|g" "$f"
    sed -i '' "s|com\.apex\.example|${PACKAGE}.example|g" "$f"
    # 项目名替换（apex-scaffold → name；注意文件路径中的目录名不含此 token）
    sed -i '' "s|apex-scaffold|${NAME}|g" "$f"
    # 端口替换（仅替换 8080/8081 完整匹配，避免误伤其他数字）
    sed -i '' "s|:8080|:${PORT}|g; s| 8080| ${PORT}|g; s|:8081|:${MANAGE_PORT}|g; s| 8081| ${MANAGE_PORT}|g" "$f"
    # 数据库名替换
    sed -i '' "s|apex_data|${DB_NAME}|g" "$f"
    sed -i '' "s|apex_test|${DB_NAME}_test|g" "$f"
    sed -i '' "s|apex_example|${DB_NAME}_example|g" "$f"
    # 档位默认值（仅文档/环境模板中的占位）
    sed -i '' "s|BUILD_PROFILE=enterprise|BUILD_PROFILE=${PROFILE}|g; s|-Penterprise|-P${PROFILE}|g" "$f"
done

# ---------- 3. 重命名含项目名的文件 ----------
echo "=== 3/5 重命名相关文件 ==="
# systemd 模板: apex-scaffold.service → {name}.service
OLD_SERVICE="${TARGET}/scripts/deploy/apex-scaffold.service"
if [[ -f "$OLD_SERVICE" ]]; then
    mv "$OLD_SERVICE" "${TARGET}/scripts/deploy/${NAME}.service"
fi
# 日志文件名（logback 中引用的 apex-scaffold.log 已在替换阶段处理）

# ---------- 4. 目录级包名路径（com/apex/scaffold → newPackage 路径） ----------
echo "=== 4/5 包路径迁移 ==="
# 源码包目录分散在各模块下（如 apex-shared-parent/apex-common/src/main/java/com/apex/scaffold/...）。
# 迁移规则: 去掉 "com/apex/scaffold" 尾部，替换为新的包路径（含 com 开头）。
NEW_PKG_PATH="$(echo "${PACKAGE}" | tr '.' '/')"
NEW_EXAMPLE_PATH="${NEW_PKG_PATH}/example"

# 迁移主包目录（main/test 根各一个，逐个处理）
while IFS= read -r -d '' dir; do
    prefix="${dir%/com/apex/scaffold}"
    target_dir="${prefix}/${NEW_PKG_PATH}"
    mkdir -p "$(dirname "$target_dir")"
    mv "$dir" "$target_dir"
done < <(find "${TARGET}" -type d -path '*/com/apex/scaffold' -print0)

# 迁移 example 模块包目录
while IFS= read -r -d '' dir; do
    prefix="${dir%/com/apex/example}"
    target_dir="${prefix}/${NEW_EXAMPLE_PATH}"
    mkdir -p "$(dirname "$target_dir")"
    mv "$dir" "$target_dir"
done < <(find "${TARGET}" -type d -path '*/com/apex/example' -print0)

# 清理残留的空 com/apex 目录
find "${TARGET}" -type d -path '*/com/apex' -empty -delete 2>/dev/null || true
find "${TARGET}" -type d -name 'com' -empty -delete 2>/dev/null || true

# ---------- 5. 验证与输出 ----------
echo "=== 5/5 验证 ==="
# 旧标识残留检测（限定源码/配置文件类型，兼容 BSD/GNU grep）
LEFTOVER=$(grep -rl "com\.apex\.scaffold\|com\.apex\.example\|apex-scaffold\|apex_data\|apex_test\|apex_example" \
    "${TARGET}" --include='*.java' --include='*.yml' --include='*.yaml' --include='*.xml' \
    --include='*.sql' --include='*.properties' --include='*.md' --include='*.sh' --include='*.json' \
    --include='Containerfile*' --include='*.env*' 2>/dev/null | wc -l | tr -d ' ')
if [[ "$LEFTOVER" != "0" ]]; then
    echo "⚠️  检测到 ${LEFTOVER} 处旧标识残留（不影响运行，可手工清理）:"
    grep -rl "com\.apex\.scaffold\|com\.apex\.example\|apex-scaffold\|apex_data\|apex_test\|apex_example" \
        "${TARGET}" --include='*.java' --include='*.yml' --include='*.yaml' --include='*.xml' \
        --include='*.sql' --include='*.properties' --include='*.md' --include='*.sh' --include='*.json' \
        --include='Containerfile*' --include='*.env*' 2>/dev/null | head -5
else
    echo "✅ 旧标识零残留"
fi
find "${TARGET}" -name "*.sh" -o -name "*.service" | xargs chmod +x 2>/dev/null || true

cat << 'EOF'

🎉 初始化完成！新项目已生成。下一步：

1. 初始化 git 并提交基线
   cd <target>
   git init && git add -A && git commit -m "chore: init from apex-scaffold"

2. 准备本地基础设施（MySQL/Redis 等）
   podman-compose -f podman-compose.infra.yml up -d

3. 构建并启动（dev 环境）
   source ~/.sdkman/bin/sdkman-init.sh
   mvnd spring-boot:run -pl :apex-system-service

4. 验证清单
   □ http://localhost:<port>/doc.html        API 文档可访问
   □ http://localhost:<port>/api/v1/health   业务健康检查 UP
   □ 登录管理后台（默认账号见 docs/QUICK_START.md）
   □ 全量测试: mvnd -o test
EOF

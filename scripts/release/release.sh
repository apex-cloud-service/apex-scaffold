#!/usr/bin/env zsh
# ============================================================
# Apex Scaffold 发布产物打包脚本
# ============================================================
# 用法:
#   ./scripts/release/release.sh [basic|standard|enterprise] [版本号]
#
# 示例:
#   ./scripts/release/release.sh standard 1.1.0
#
# 产出:
#   dist/apex-scaffold-<version>-<profile>.zip
#     ├── apex-*.jar        (可执行 jar)
#     ├── LICENSE / LICENSE-COMMERCIAL.md
#     ├── docs/             (文档)
#     ├── config/           (配置模板 + 公钥)
#     ├── docker-compose.yml / Dockerfile / k8s/
#     └── scripts/
# 💡 shebang 用 zsh：macOS 自带 bash 3.2 不支持 sdkman 的 ${var^^} 语法，zsh 正常。
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${ROOT_DIR}"

PROFILE="${1:-standard}"
VERSION="${2:-1.1.0}"
DIST_DIR="dist/apex-scaffold-${VERSION}-${PROFILE}"

# 统一构建环境：sdkman 默认 JDK（17.0.12-oracle）+ mvnd 2.0.0-rc-3
# （差异）项目统一使用 mvnd 而非 mvn；JDK 由 sdkman 管理，不硬编码 JAVA_HOME。
if [[ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]]; then
  # shellcheck disable=SC1091
  source "$HOME/.sdkman/bin/sdkman-init.sh"
fi
if ! command -v mvnd &> /dev/null; then
  echo "❌ 未找到 mvnd，请先安装: sdk install mvnd"
  exit 1
fi

echo "==> [1/3] 构建 ${PROFILE} 版本 (v${VERSION})"
MAVEN_OPTS="-Xmx1024m" mvnd -o -q clean package -P"${PROFILE}" -DskipTests \
  || MAVEN_OPTS="-Xmx1024m" mvnd -q clean package -P"${PROFILE}" -DskipTests

echo "==> [2/3] 组装发布目录 ${DIST_DIR}"
rm -rf "${DIST_DIR}"
mkdir -p "${DIST_DIR}/docs" "${DIST_DIR}/config/license" "${DIST_DIR}/scripts"

# 可执行 jar
cp apex-interfaces/target/apex-interfaces-*.jar "${DIST_DIR}/" 2>/dev/null || \
  cp apex-interfaces/target/*.jar "${DIST_DIR}/apex-interfaces-${VERSION}.jar"

# 许可证
cp LICENSE LICENSE-COMMERCIAL.md "${DIST_DIR}/"

# 文档（排除内部工作文档）
for doc in README.md CHANGELOG.md CODE_OF_CONDUCT.md CONTRIBUTING.md; do
  [[ -f "${doc}" ]] && cp "${doc}" "${DIST_DIR}/"
done
cp docs/*.md "${DIST_DIR}/docs/" 2>/dev/null || true
rm -f "${DIST_DIR}/docs/COMMERCIAL_REFACTORING_PLAN.md" "${DIST_DIR}/docs/REFACTORING_COMPLETION_REPORT.md" 2>/dev/null || true

# 配置与公钥（私钥绝不打包）
cp -r config/* "${DIST_DIR}/config/" 2>/dev/null || true
rm -f "${DIST_DIR}/config/license/private.key" 2>/dev/null || true

# 部署文件
cp docker-compose.yml Dockerfile "${DIST_DIR}/" 2>/dev/null || true
cp -r k8s "${DIST_DIR}/" 2>/dev/null || true
cp -r scripts/build scripts/deploy scripts/license "${DIST_DIR}/scripts/" 2>/dev/null || true
chmod +x "${DIST_DIR}"/scripts/*/*.sh 2>/dev/null || true

echo "==> [3/3] 打包压缩"
cd dist
zip -rq "apex-scaffold-${VERSION}-${PROFILE}.zip" "$(basename "${DIST_DIR}")"
cd ..

echo ""
echo "✅ 发布包已生成: dist/apex-scaffold-${VERSION}-${PROFILE}.zip"
echo "   构建标识: $(cat apex-interfaces/target/classes/apex-build.properties | grep build.profile.id)"

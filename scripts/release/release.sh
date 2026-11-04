#!/usr/bin/env zsh
# ============================================================
# Apex Scaffold 发布产物打包脚本
# ============================================================
# 用法:
#   ./scripts/release/release.sh [basic|standard|enterprise] [版本号]
#
# 示例:
#   ./scripts/release/release.sh standard 1.1.3
#
# 机器指纹绑定（签发演示）:
#   商业版建议在签发 License 时绑定客户机器指纹，防止复制到未授权机器：
#     1) 客户在目标机器采集指纹（Client 执行）:
#        java -cp "<apex-license-tools.jar>:<依赖>" \
#          com.apex.scaffold.license.tools.LicenseGenerator gen-fingerprint
#     2) 卖方用采集到的指纹签发（Seller 执行，见 scripts/license/generate-license.sh）:
#        ./scripts/license/generate-license.sh ENTERPRISE "示例公司" 365 --fingerprint <64位SHA-256>
#   注意: 私钥与 license 发放工具（scripts/license、apex-license-tools）不进发行包，
#   随包交付会令买家自签 License。
#
# 产出:
#   dist/apex-scaffold-<version>-<profile>.zip
#     ├── apex-*.jar        (可执行 jar)
#     ├── LICENSE / LICENSE-COMMERCIAL.md
#     ├── docs/             (文档)
#     ├── config/           (配置模板 + 公钥)
#     ├── podman-compose.yml / Containerfile / k8s/
#     └── scripts/
# 💡 shebang 用 zsh：macOS 自带 bash 3.2 不支持 sdkman 的 ${var^^} 语法，zsh 正常。
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${ROOT_DIR}"

PROFILE="${1:-standard}"
VERSION="${2:-1.2.0}"
DIST_DIR="dist/apex-scaffold-${VERSION}-${PROFILE}"

case "${PROFILE}" in
  basic|standard|enterprise) ;;
  *) echo "不支持的发布 profile: ${PROFILE}" >&2; exit 1 ;;
esac

source "${ROOT_DIR}/scripts/build/init-build-env.sh"

SERVICES=(apex-system-service apex-auth-service apex-workflow-service apex-office-service apex-file-service apex-integration-service apex-gateway)
SERVICE_MODULES="${(j:,:)SERVICES},apex-tools-deployment"

echo "==> [1/3] 构建 ${PROFILE} 版本 (v${VERSION})"
MAVEN_OPTS="-Xmx1024m" mvnd -o -q clean package -P"${PROFILE}" -pl "${SERVICE_MODULES}" -am -DskipTests \
  || MAVEN_OPTS="-Xmx1024m" mvnd -q clean package -P"${PROFILE}" -pl "${SERVICE_MODULES}" -am -DskipTests

# 六个业务服务、网关及组合部署包在 basic/standard/enterprise 中均属于根 reactor，产物不使用 classifier。
SERVICE_JARS=()
for svc in "${SERVICES[@]}"; do
  jars=("${svc}"/target/"${svc}"-*.jar(N))
  if (( ${#jars[@]} != 1 )); then
    echo "${svc} 应有且仅有一个发布 JAR，实际找到 ${#jars[@]} 个" >&2
    exit 1
  fi
  SERVICE_JARS+=("${jars[1]}")
done

echo "==> [2/3] 组装发布目录 ${DIST_DIR}"
rm -rf "${DIST_DIR}"
mkdir -p "${DIST_DIR}/docs" "${DIST_DIR}/config/license" "${DIST_DIR}/scripts" "${DIST_DIR}/tools"
cp "${SERVICE_JARS[@]}" "${DIST_DIR}/"

# 离线部署校验工具：Compose security-key-check 按包内相对路径挂载，必须随包交付。
TOOLS_JAR="apex-tools-deployment/target/local-deployment.jar"
if [[ ! -f "${TOOLS_JAR}" ]]; then
  echo "缺少部署校验工具 ${TOOLS_JAR}，请在发布构建中包含 apex-tools-deployment" >&2
  exit 1
fi
cp "${TOOLS_JAR}" "${DIST_DIR}/tools/"

# 许可证
cp LICENSE LICENSE-COMMERCIAL.md "${DIST_DIR}/"

# 文档（排除内部工作文档：评审/任务清单/商业化内参一律不进入发行包）
for doc in README.md CHANGELOG.md CODE_OF_CONDUCT.md CONTRIBUTING.md; do
  [[ -f "${doc}" ]] && cp "${doc}" "${DIST_DIR}/"
done
cp docs/*.md "${DIST_DIR}/docs/" 2>/dev/null || true
rm -f "${DIST_DIR}/docs/"COMMERCIAL_*.md "${DIST_DIR}/docs/"REFACTORING_*.md "${DIST_DIR}/docs/"*GAP_ANALYSIS*.md 2>/dev/null || true

# 配置与公钥（私钥绝不打包）
cp -r config/* "${DIST_DIR}/config/" 2>/dev/null || true
rm -f "${DIST_DIR}/config/license/private.key" 2>/dev/null || true

# 部署文件
# 注意：scripts/license（发放工具）与 apex-license-tools 模块不进入发行包 ——
# License 发放能力为卖方专属，随包交付会令买家可自签 License。
cp podman-compose.yml podman-compose.infra.yml Containerfile "${DIST_DIR}/" 2>/dev/null || true
cp -r k8s "${DIST_DIR}/" 2>/dev/null || true
cp -r scripts/build scripts/deploy "${DIST_DIR}/scripts/" 2>/dev/null || true
chmod +x "${DIST_DIR}"/scripts/*/*.sh 2>/dev/null || true

echo "==> [3/3] 打包压缩"
cd dist
zip -rq "apex-scaffold-${VERSION}-${PROFILE}.zip" "$(basename "${DIST_DIR}")"
cd ..

echo ""
echo "✅ 发布包已生成: dist/apex-scaffold-${VERSION}-${PROFILE}.zip"
echo "   构建标识: $(cat apex-system/target/classes/apex-build.properties | grep build.profile.id)"

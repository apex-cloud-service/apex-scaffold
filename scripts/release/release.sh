#!/usr/bin/env zsh
# ============================================================
# Apex Scaffold 发布产物打包脚本
# ============================================================
# 用法:
#   ./scripts/release/release.sh [basic|standard|enterprise] [版本号]
#   版本号省略时读取根 pom.xml；显式传入时必须与 POM 版本一致。
#
# 示例:
#   ./scripts/release/release.sh standard
#   ./scripts/release/release.sh standard 1.3.1
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
#     ├── docs/             (按子目录结构复制的客户文档)
#     ├── config/           (配置模板 + 公钥)
#     ├── podman-compose.yml / Containerfile / k8s/
#     └── scripts/
#   dist/apex-scaffold-<version>-<profile>.zip.sha256
# 💡 shebang 用 zsh：macOS 自带 bash 3.2 不支持 sdkman 的 ${var^^} 语法，zsh 正常。
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${ROOT_DIR}"

PROFILE="${1:-standard}"
# 项目版本取根 POM 中 artifactId=apex-scaffold 后的 <version>，避免误读 <parent> 的 Spring Boot 版本。
POM_VERSION="$(awk '/<artifactId>apex-scaffold<\/artifactId>/{getline; if ($0 ~ /<version>/) {gsub(/.*<version>|<\/version>.*/, ""); print; exit}}' pom.xml)"
VERSION="${2:-${POM_VERSION}}"
if [[ ! "${VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z.-]+)?$ ]]; then
  echo "发布版本号非法: ${VERSION}" >&2
  exit 1
fi
if [[ -z "${POM_VERSION}" || "${VERSION}" != "${POM_VERSION}" ]]; then
  echo "发布版本 ${VERSION} 与根 POM 版本 ${POM_VERSION} 不一致；发布包名不得与内部 JAR 版本漂移" >&2
  exit 1
fi
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
# 排除 sources/javadoc/test-support 等辅助产物：例如 apex-gateway 额外产出
# apex-gateway-<version>-test-support.jar（maven-jar-plugin 的 test-support classifier），
# 直接 glob 会命中 2 个 JAR 而误判发布失败。
SERVICE_JARS=()
for svc in "${SERVICES[@]}"; do
  jars=()
  for candidate in "${svc}"/target/"${svc}"-*.jar(N); do
    case "${candidate}" in
      *-sources.jar|*-javadoc.jar|*-test-support.jar|*-tests.jar) continue ;;
    esac
    jars+=("${candidate}")
  done
  if (( ${#jars[@]} != 1 )); then
    echo "${svc} 应有且仅有一个发布 JAR，实际找到 ${#jars[@]} 个: ${jars[*]}" >&2
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
# 保留 docs 子目录结构：README 与部署指南中的相对链接都指向 docs/xx/yy.md，
# 只复制 docs 根目录会造成发行包内链接全部失效。
# 只带 Markdown 与图片资源：测试证据（json/log/jfr/tsv 等）体积占 docs 绝大部分且不属于客户文档。
if [[ -d docs ]]; then
  rsync -a \
    --include='*/' \
    --exclude 'COMMERCIAL_*.md' \
    --exclude 'REFACTORING_*.md' \
    --exclude '*GAP_ANALYSIS*.md' \
    --include='*.md' \
    --include='*.svg' \
    --include='*.png' \
    --include='*.jpg' \
    --include='*.jpeg' \
    --include='*.gif' \
    --include='*.webp' \
    --exclude='*' \
    docs/ "${DIST_DIR}/docs/"
fi

# 配置与公钥（私钥绝不打包）
cp -r config/* "${DIST_DIR}/config/" 2>/dev/null || true
rm -f "${DIST_DIR}/config/license/private.key" 2>/dev/null || true

# 部署文件
# 注意：scripts/license（发放工具）与 apex-license-tools 模块不进入发行包 ——
# License 发放能力为卖方专属，随包交付会令买家可自签 License。
cp podman-compose.yml podman-compose.infra.yml Containerfile "${DIST_DIR}/" 2>/dev/null || true
cp -r k8s "${DIST_DIR}/" 2>/dev/null || true
cp -r scripts/build scripts/deploy scripts/lib "${DIST_DIR}/scripts/" 2>/dev/null || true
chmod +x "${DIST_DIR}"/scripts/*/*.sh 2>/dev/null || true

# 发行包自检：私钥与卖方签发工具绝不能出现在包内。
if [[ -e "${DIST_DIR}/config/license/private.key" ]]; then
  echo "发行包包含 License 私钥，拒绝打包: ${DIST_DIR}/config/license/private.key" >&2
  exit 1
fi
if [[ -e "${DIST_DIR}/scripts/license" || -e "${DIST_DIR}/apex-license-tools" ]]; then
  echo "发行包包含卖方 License 签发工具，拒绝打包" >&2
  exit 1
fi

echo "==> [3/3] 打包压缩"
cd dist
ARCHIVE="apex-scaffold-${VERSION}-${PROFILE}.zip"
zip -rq "${ARCHIVE}" "$(basename "${DIST_DIR}")"
# 交付物清单要求提供 SHA-256 校验文件；在包所在目录生成，便于随包分发。
if command -v shasum >/dev/null 2>&1; then
  shasum -a 256 "${ARCHIVE}" > "${ARCHIVE}.sha256"
elif command -v sha256sum >/dev/null 2>&1; then
  sha256sum "${ARCHIVE}" > "${ARCHIVE}.sha256"
else
  echo "未找到 shasum/sha256sum，无法生成校验文件" >&2
  exit 1
fi
cd ..

echo ""
echo "✅ 发布包已生成: dist/apex-scaffold-${VERSION}-${PROFILE}.zip"
echo "   校验文件: dist/apex-scaffold-${VERSION}-${PROFILE}.zip.sha256"
echo "   构建标识: $(cat apex-system/target/classes/apex-build.properties | grep build.profile.id)"

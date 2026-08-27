#!/usr/bin/env zsh
# ============================================================
# Apex Scaffold License 发放工具（卖方使用）
# ============================================================
# 用法:
#   ./scripts/license/generate-license.sh <version> <customer> <days> [feature1,feature2,...]
#
# 示例:
#   # 发放标准版一年授权（默认标准版功能集）
#   ./scripts/license/generate-license.sh STANDARD "示例公司" 365
#
#   # 发放企业版一年授权（默认企业版功能集）
#   ./scripts/license/generate-license.sh ENTERPRISE "示例公司" 365
#
#   # 发放自定义功能集
#   ./scripts/license/generate-license.sh STANDARD "示例公司" 365 jwt_auth,rbac,redis_cache
#
# 前置要求:
#   - JDK 17 已安装（JAVA_HOME 已设置）
#   - config/license/private.key 与 public.key 已生成（卖方私钥，切勿提交到仓库）
#   - apex-common 已构建（target/classes 存在）
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${ROOT_DIR}"

if [[ $# -lt 3 ]]; then
  echo "用法: $0 <BASIC|STANDARD|ENTERPRISE> <客户名称> <有效天数> [功能列表,逗号分隔]"
  exit 1
fi

VERSION="$(printf '%s' "${1}" | tr '[:lower:]' '[:upper:]')"
CUSTOMER="$2"
DAYS="$3"
FEATURES="${4:-}"

# 默认功能集（与 LICENSE-COMMERCIAL.md 版本对照表一致）
BASIC_FEATURES="jwt_auth,unified_response,parameter_validation,webflux,mybatis_plus"
STANDARD_FEATURES="${BASIC_FEATURES},rbac,redis_cache,monitoring,wechat_sdk,object_storage,email_sms,excel_io"
ENTERPRISE_FEATURES="${STANDARD_FEATURES},rocketmq,tracing,sentinel,resilience4j,distributed_lock,multi_datasource,flyway,k8s"

case "${VERSION}" in
  BASIC)      FEATURES="${FEATURES:-${BASIC_FEATURES}}" ;;
  STANDARD)   FEATURES="${FEATURES:-${STANDARD_FEATURES}}" ;;
  ENTERPRISE) FEATURES="${FEATURES:-${ENTERPRISE_FEATURES}}" ;;
  *) echo "错误: 版本必须为 BASIC/STANDARD/ENTERPRISE"; exit 1 ;;
esac

PRIVATE_KEY="${PRIVATE_KEY:-config/license/private.key}"
PUBLIC_KEY="${PUBLIC_KEY:-config/license/public.key}"

if [[ ! -f "${PRIVATE_KEY}" || ! -f "${PUBLIC_KEY}" ]]; then
  echo "错误: 未找到密钥对 ${PRIVATE_KEY} / ${PUBLIC_KEY}"
  echo "请先运行: java -cp ... com.apex.scaffold.common.license.LicenseGenerator gen-keys <private.key> <public.key>"
  exit 1
fi

# 构建 classpath（统一使用 mvnd + sdkman JDK；无 sdkman 时回退系统 mvn）
# 先确保 apex-common 已构建（离线优先，失败回退在线）
if [[ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]]; then
  # shellcheck disable=SC1091
  source "$HOME/.sdkman/bin/sdkman-init.sh"
fi
CP_FILE="$(mktemp)"
if command -v mvnd >/dev/null 2>&1; then
  MAVEN_OPTS="-Xmx512m" mvnd -o -q dependency:build-classpath -pl apex-common -Dmdep.outputFile="${CP_FILE}" 2>/dev/null \
    || MAVEN_OPTS="-Xmx512m" mvnd -q dependency:build-classpath -pl apex-common -Dmdep.outputFile="${CP_FILE}"
elif command -v mvn >/dev/null 2>&1; then
  mvn -q -o dependency:build-classpath -pl apex-common -Dmdep.outputFile="${CP_FILE}" 2>/dev/null \
    || mvn -q dependency:build-classpath -pl apex-common -Dmdep.outputFile="${CP_FILE}"
else
  echo "❌ 未找到 mvnd/mvn，请先安装: sdk install mvnd"
  exit 1
fi
CP="apex-common/target/classes:$(cat "${CP_FILE}")"
rm -f "${CP_FILE}"

OUTPUT_DIR_DEFAULT="dist/license"
VERSION_LOWER="$(printf '%s' "${VERSION}" | tr '[:upper:]' '[:lower:]')"
OUTPUT="${OUTPUT_DIR:-${OUTPUT_DIR_DEFAULT}}/${VERSION_LOWER}-${DAYS}d-$(date +%Y%m%d).json"
mkdir -p "$(dirname "${OUTPUT}")"

java -cp "${CP}" com.apex.scaffold.common.license.LicenseGenerator \
  gen-license "${PRIVATE_KEY}" "${PUBLIC_KEY}" "${OUTPUT}" "${VERSION}" "${CUSTOMER}" "${DAYS}" "${FEATURES}"

echo ""
echo "✅ License 已生成: ${OUTPUT}"
echo "   版本: ${VERSION} | 客户: ${CUSTOMER} | 有效期: ${DAYS} 天"
echo "   将文件发送给客户，并指导其放置到应用可访问路径后重启应用。"

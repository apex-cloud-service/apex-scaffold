#!/usr/bin/env zsh
# ============================================================
# Apex Scaffold License 发放工具（卖方使用）
# ============================================================
# 用法:
#   ./scripts/license/generate-license.sh <version> <customer> <days> [features] [--fingerprint <hash>]
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
#   # 绑定机器指纹签发（客户先运行 gen-fingerprint 采集后发给卖方）
#   ./scripts/license/generate-license.sh ENTERPRISE "示例公司" 365 --fingerprint a1b2c3...
#
# 前置要求:
#   - JDK 21 已安装（JAVA_HOME 已设置）
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

# 第 4 个参数如果是 --fingerprint 则不作为 features 解析
FEATURES=""
FINGERPRINT=""
FP_NEXT=0
for arg in "${@:4}"; do
  if [[ "${FP_NEXT}" == "1" ]]; then
    FINGERPRINT="${arg}"
    FP_NEXT=0
    continue
  fi
  if [[ "${arg}" == "--fingerprint" ]]; then
    FP_NEXT=1
    continue
  fi
  if [[ -z "${FEATURES}" ]]; then
    FEATURES="${arg}"
  fi
done

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
  echo "请先运行: java -cp <apex-license-tools 类路径> com.apex.scaffold.license.tools.LicenseGenerator gen-keys <private.key> <public.key>"
  exit 1
fi

# 构建 classpath（统一使用 SDKMAN 管理的 mvnd）
# 先确保 apex-common 已构建（离线优先，失败回退在线）
if [[ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]]; then
  # shellcheck disable=SC1091
  source "$HOME/.sdkman/bin/sdkman-init.sh"
fi
# LicenseGenerator 位于卖方内部模块 apex-license-tools（发行源码已剥离）
CP_FILE="$(mktemp)"
if ! command -v mvnd >/dev/null 2>&1; then
  echo "❌ 未找到 mvnd，请先安装: sdk install mvnd 2.0.0-rc-3"
  exit 1
fi
MAVEN_OPTS="-Xmx512m" mvnd -o -q dependency:build-classpath -pl :apex-license-tools -Dmdep.outputFile="${CP_FILE}" 2>/dev/null \
  || MAVEN_OPTS="-Xmx512m" mvnd -q dependency:build-classpath -pl :apex-license-tools -Dmdep.outputFile="${CP_FILE}"
CP="apex-tools-parent/apex-license-tools/target/classes:apex-starters-parent/apex-license-spring-boot-starter/target/classes:apex-shared-parent/apex-common/target/classes:$(cat "${CP_FILE}")"
rm -f "${CP_FILE}"

OUTPUT_DIR_DEFAULT="dist/license"
VERSION_LOWER="$(printf '%s' "${VERSION}" | tr '[:upper:]' '[:lower:]')"
OUTPUT="${OUTPUT_DIR:-${OUTPUT_DIR_DEFAULT}}/${VERSION_LOWER}-${DAYS}d-$(date +%Y%m%d).json"
mkdir -p "$(dirname "${OUTPUT}")"

# 构建 java 调用参数（FEATURES 非空时追加，FINGERPRINT 非空时追加 --fingerprint）
JAVA_ARGS=("gen-license" "${PRIVATE_KEY}" "${PUBLIC_KEY}" "${OUTPUT}" "${VERSION}" "${CUSTOMER}" "${DAYS}")
if [[ -n "${FEATURES}" ]]; then
  JAVA_ARGS+=("${FEATURES}")
fi
if [[ -n "${FINGERPRINT}" ]]; then
  JAVA_ARGS+=("--fingerprint" "${FINGERPRINT}")
fi

java -cp "${CP}" com.apex.scaffold.license.tools.LicenseGenerator "${JAVA_ARGS[@]}"

echo ""
echo "✅ License 已生成: ${OUTPUT}"
echo "   版本: ${VERSION} | 客户: ${CUSTOMER} | 有效期: ${DAYS} 天"
if [[ -n "${FINGERPRINT}" ]]; then
  echo "   机器指纹: 已绑定 (${FINGERPRINT:0:12}...)"
else
  echo "   机器指纹: 未绑定（如需绑定请在参数中追加 --fingerprint <hash>）"
fi
echo "   将文件发送给客户，并指导其放置到应用可访问路径后重启应用。"

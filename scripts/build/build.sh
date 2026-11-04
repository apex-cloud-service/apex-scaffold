#!/usr/bin/env zsh
set -e

# ============================================================
# Apex Scaffold 一键构建脚本
# ============================================================
# 需要 JDK 21.0.13.0+9-tem 与 mvnd 2.0.0-rc-3（见 .sdkmanrc）
# （差异）项目统一使用 mvnd（Maven Daemon）而非 mvn，构建更快。
# 用法: ./scripts/build/build.sh [-P<profile>]   # profile: basic/standard/enterprise（默认 standard）
# 💡 shebang 用 zsh：macOS 自带 bash 3.2 不支持 sdkman 的 ${var^^} 语法，zsh 正常。
# ============================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${PROJECT_ROOT}"

PROFILE="${1:--Pstandard}"

echo "开始构建项目..."

source "${PROJECT_ROOT}/scripts/build/init-build-env.sh"

# 离线优先；失败时自动回退在线（首次构建需联网拉依赖）
MAVEN_OPTS="-Xmx1024m" mvnd -o clean package "${PROFILE}" -DskipTests \
  || MAVEN_OPTS="-Xmx1024m" mvnd clean package "${PROFILE}" -DskipTests

echo "✅ 构建完成"
echo "JAR 文件位于: apex-gateway/target/ 和 apex-*-service/target/"
echo "   构建标识: $(cat apex-system/target/classes/apex-build.properties 2>/dev/null | grep build.profile.id || echo 'unknown')"

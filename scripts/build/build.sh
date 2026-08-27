#!/usr/bin/env zsh
set -e

# ============================================================
# Apex Scaffold 一键构建脚本
# ============================================================
# 统一构建环境：sdkman 默认 JDK（17.0.12-oracle）+ mvnd 2.0.0-rc-3
# （差异）项目统一使用 mvnd（Maven Daemon）而非 mvn，构建更快。
# 用法: ./scripts/build/build.sh [-P<profile>]   # profile: basic/standard/enterprise（默认 standard）
# 💡 shebang 用 zsh：macOS 自带 bash 3.2 不支持 sdkman 的 ${var^^} 语法，zsh 正常。
# ============================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${PROJECT_ROOT}"

PROFILE="${1:--Pstandard}"

echo "开始构建项目..."

# 加载 sdkman 环境（JDK + mvnd）；CI/Docker 等无 sdkman 环境自动跳过
if [[ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]]; then
  # shellcheck disable=SC1091
  source "$HOME/.sdkman/bin/sdkman-init.sh"
fi

# 校验 mvnd 可用（sdkman 安装: sdk install mvnd）
if ! command -v mvnd &> /dev/null; then
  echo "❌ 未找到 mvnd，请先安装: sdk install mvnd"
  echo "   或使用: sdk list mvnd"
  exit 1
fi

# 校验 JDK 版本 >= 17
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ -n "$JAVA_VERSION" ] && [ "$JAVA_VERSION" -lt 17 ]; then
  echo "❌ Java 版本过低，需要 JDK 17+，当前版本: $JAVA_VERSION"
  echo "   请切换: sdk use java 17.0.12-oracle"
  exit 1
fi

# 离线优先；失败时自动回退在线（首次构建需联网拉依赖）
MAVEN_OPTS="-Xmx1024m" mvnd -o clean package "${PROFILE}" -DskipTests \
  || MAVEN_OPTS="-Xmx1024m" mvnd clean package "${PROFILE}" -DskipTests

echo "✅ 构建完成"
echo "📦 JAR 文件位于: apex-interfaces/target/"
echo "   构建标识: $(cat apex-interfaces/target/classes/apex-build.properties 2>/dev/null | grep build.profile.id || echo 'unknown')"

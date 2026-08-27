#!/usr/bin/env zsh
set -e

# ============================================================
# 构建环境初始化检查
# ============================================================
# 统一构建环境：sdkman 默认 JDK（17.0.12-oracle）+ mvnd 2.0.0-rc-3
# 安装指引:
#   sdk install java 17.0.12-oracle
#   sdk install mvnd
# 💡 shebang 用 zsh：macOS 自带 bash 3.2 不支持 sdkman 的 ${var^^} 语法，zsh 正常。
# ============================================================

echo "初始化构建环境..."

# 加载 sdkman 环境（自动应用 .sdkmanrc 指定的 JDK）
if [[ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]]; then
  # shellcheck disable=SC1091
  source "$HOME/.sdkman/bin/sdkman-init.sh"
  echo "✅ 已加载 sdkman"
else
  echo "⚠️ 未找到 sdkman（$HOME/.sdkman），请安装: https://sdkman.io"
fi

# 检查 Java 版本
if ! command -v java &> /dev/null; then
    echo "❌ 未找到 Java，请先安装 JDK 17+"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "❌ Java 版本过低，需要 JDK 17+，当前版本: $JAVA_VERSION"
    exit 1
fi

# 检查 mvnd（替代 mvn，构建加速）
if ! command -v mvnd &> /dev/null; then
    echo "❌ 未找到 mvnd，请先安装: sdk install mvnd"
    exit 1
fi

echo "✅ 构建环境检查通过"
echo "   Java: $(java -version 2>&1 | head -1)"
echo "   mvnd: $(mvnd --version 2>&1 | head -1)"
echo "   JDK 固定: $(cat .sdkmanrc 2>/dev/null | grep '^java=' || echo '未配置 .sdkmanrc')"

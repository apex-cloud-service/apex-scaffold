#!/usr/bin/env zsh
set -e

# ============================================================
# 构建环境初始化检查
# ============================================================
# 统一构建环境：sdkman 默认 JDK（21.0.13.0+9-tem）+ mvnd 2.0.0-rc-3
# 安装指引:
#   sdk install java 21.0.13.0+9-tem
#   sdk install mvnd
# 💡 shebang 用 zsh：macOS 自带 bash 3.2 不支持 sdkman 的 ${var^^} 语法，zsh 正常。
# ============================================================

echo "初始化构建环境..."

# 加载 sdkman 环境（自动应用 .sdkmanrc 指定的 JDK）
if [[ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]]; then
  # shellcheck disable=SC1091
  if ! source "$HOME/.sdkman/bin/sdkman-init.sh"; then
    echo "加载 sdkman 失败" >&2
    exit 1
  fi
  if ! sdk env; then
    echo "加载 sdkman 项目环境失败" >&2
    exit 1
  fi
  echo "✅ 已加载 sdkman 项目环境"
else
  echo "未找到 sdkman（$HOME/.sdkman），请安装: https://sdkman.io" >&2
  exit 1
fi

# 检查 Java 版本
if ! command -v java &> /dev/null; then
    echo "❌ 未找到 Java，请先安装 JDK 21+"
    exit 1
fi

if ! JAVA_VERSION_OUTPUT=$(java -version 2>&1); then
    echo "java -version 执行失败" >&2
    exit 1
fi
JAVA_VERSION=$(awk -F '"' '/version/ {print $2; exit}' <<< "$JAVA_VERSION_OUTPUT")
if [[ "${JAVA_HOME:-}" != "$HOME/.sdkman/candidates/java/21.0.13.0+9-tem" || "$JAVA_VERSION" != "21.0.13" ]]; then
    echo "需要 SDKMAN JDK 21.0.13.0+9-tem，当前版本: $JAVA_VERSION" >&2
    exit 1
fi

# 检查 mvnd（替代 mvn，构建加速）
if ! command -v mvnd &> /dev/null; then
    echo "❌ 未找到 mvnd，请先安装: sdk install mvnd"
    exit 1
fi

if ! MVND_VERSION_OUTPUT=$(mvnd --version 2>&1); then
    echo "mvnd --version 执行失败" >&2
    exit 1
fi
MVND_VERSION_LINE=$(awk '/^Apache Maven Daemon \(mvnd\)[[:space:]]/ {print; exit}' <<< "$MVND_VERSION_OUTPUT")
MVND_VERSION=$(awk '{print $5}' <<< "$MVND_VERSION_LINE")
if [[ "$MVND_VERSION" != "2.0.0-rc-3" ]]; then
    echo "需要 mvnd 2.0.0-rc-3" >&2
    exit 1
fi

echo "✅ 构建环境检查通过"
echo "   Java: ${JAVA_VERSION_OUTPUT%%$'\n'*}"
echo "   mvnd: $MVND_VERSION_LINE"
echo "   JDK 固定: java=21.0.13.0+9-tem"

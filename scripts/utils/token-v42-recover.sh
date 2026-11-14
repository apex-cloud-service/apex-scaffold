#!/bin/bash
###############################################################################
# token-v42-recover.sh - 令牌 v4.2 恢复工具启动器，运行 local-deployment.jar 中的 TokenV42Recover 主类
# 用法：bash scripts/utils/token-v42-recover.sh [参数透传归档/目标脚本]
# 作者：Apex Team    创建：2026-10-26
###############################################################################
set -euo pipefail
umask 077

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
source "$SCRIPT_DIR/../lib/common.sh"
PROJECT_ROOT=$(get_project_root)
JAVA=${JAVA_HOME:+$JAVA_HOME/bin/}java
require_command "$JAVA" "Java 21"
JAR="$PROJECT_ROOT/apex-tools-parent/apex-tools-deployment/target/local-deployment.jar"
if ! require_file "$JAR" "离线运维工具 JAR"; then
    log_error "请先加载 scripts/build/init-build-env.sh，再运行 mvnd -B -pl :apex-tools-deployment -am verify"
    exit 1
fi
exec "$JAVA" "-Dapex.root=$PROJECT_ROOT" -cp "$JAR" com.apex.scaffold.tools.deployment.TokenV42Recover "$@"

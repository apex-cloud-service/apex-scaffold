#!/bin/bash
###############################################################################
# local-deployment.sh - 本地部署离线运维工具启动器，运行 apex-tools-deployment 模块构建的 local-deployment.jar
# 用法：bash scripts/utils/local-deployment.sh [参数透传归档/目标脚本]
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
exec "$JAVA" "-Dapex.root=$PROJECT_ROOT" -jar "$JAR" "$@"

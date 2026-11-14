#!/bin/bash
###############################################################################
# assemble-topology.sh
#
# 组合部署拓扑装配（方案 §13 脚本交付物 / C9）。
#
# 支持三种拓扑，用于本地验证"组件可按需组合拔插"：
#   split       网关 + 六业务服务 + 只读聚合服务：逐服务构建启动（需外部 MySQL/Redis）
#   all-in-one  单进程装配全部组件（apex-boot-all，H2 + 本地存储）
#   subset      任意子集：按 APEX_COMPONENTS 指定组件在同进程装配
#
# 用法：
#   bash scripts/deploy/assemble-topology.sh all-in-one
#   bash scripts/deploy/assemble-topology.sh split
#   APEX_COMPONENTS=system,file bash scripts/deploy/assemble-topology.sh subset
#
# 说明：本脚本只负责装配与启动编排，不修改源码；拓扑差异通过
#       apex.component.<name>.enabled 与 apex.events.channel 表达。
###############################################################################
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=../lib/common.sh
source "${SCRIPT_DIR}/../lib/common.sh"

PROJECT_ROOT=$(get_project_root) || exit 1
cd "$PROJECT_ROOT" || exit 1

TOPOLOGY=${1:-all-in-one}
COMPONENTS=${APEX_COMPONENTS:-system,auth,workflow,file,office,integration}

if [[ -f scripts/build/init-build-env.sh ]]; then
    # shellcheck source=../build/init-build-env.sh
    source scripts/build/init-build-env.sh
else
    log_failure "缺少 scripts/build/init-build-env.sh"
    exit 1
fi

# 未指定的组件显式关闭，避免"以为关掉了实际仍装配"
component_flags() {
    local enabled_list=",$1,"
    local flags=()
    for name in system auth workflow file office integration; do
        if [[ "$enabled_list" == *",$name,"* ]]; then
            flags+=("--apex.component.$name.enabled=true")
        else
            flags+=("--apex.component.$name.enabled=false")
        fi
    done
    printf '%s\n' "${flags[@]}"
}

case "$TOPOLOGY" in
    all-in-one)
        log_info "拓扑：all-in-one（单进程装配全部组件，basic 档）"
        log_info "事件通道 auto：无 MQ 时自动退化为进程内派发"
        mvnd -q -B -pl :apex-boot-all -am package -DskipTests
        exec java -jar apex-applications-parent/apex-boot-all/target/apex-boot-all-1.3.1.jar \
            --spring.profiles.active=basic \
            --apex.events.channel=auto \
            --server.port="${PORT:-8080}"
        ;;
    split)
        log_info "拓扑：split（网关 + 六业务服务 + 只读聚合服务）"
        log_warn "拆分拓扑需外部 MySQL/Redis；请先按 docs/05-operations 准备依赖"
        for module in apex-gateway apex-system-service apex-auth-service apex-workflow-service \
                      apex-file-service apex-office-service apex-integration-service \
                      apex-aggregation-service; do
            log_info "构建 $module"
            mvnd -q -B -pl ":${module}" -am package -DskipTests
        done
        log_success "各服务 JAR 已产出，按部署文档分别启动（或使用 podman-compose.yml）"
        ;;
    subset)
        log_info "拓扑：subset（子集：${COMPONENTS}）"
        log_info "未列出的组件将被显式关闭（--apex.component.<name>.enabled=false）"
        mvnd -q -B -pl :apex-boot-all -am package -DskipTests
        # shellcheck disable=SC2046
        exec java -jar apex-applications-parent/apex-boot-all/target/apex-boot-all-1.3.1.jar \
            --spring.profiles.active=basic \
            --apex.events.channel=auto \
            --server.port="${PORT:-8080}" \
            $(component_flags "$COMPONENTS")
        ;;
    *)
        log_failure "未知拓扑：$TOPOLOGY（支持 split / all-in-one / subset）"
        exit 1
        ;;
esac

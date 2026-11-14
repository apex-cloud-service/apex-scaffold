#!/usr/bin/env bash
# 中台架构整改的固定环境验证入口。
#
# 该脚本只调用 mvnd 并复用项目统一的构建环境初始化逻辑：
# - 默认模式验证中台主干模块及其上游依赖的单元/架构测试（test）；
# - --full 模式执行完整反应堆的 clean verify，覆盖 Surefire、Failsafe、
#   Spotless、PMD/p3c 与 JaCoCo 门禁。
#
# 脚本不跳过失败用例，也不以局部 test 结果替代完整 verify 结论。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../lib/common.sh"

ROOT_DIR="$(get_project_root)"
require_directory "$ROOT_DIR" "项目根目录"
require_file "${ROOT_DIR}/pom.xml" "根 POM"
require_file "${ROOT_DIR}/scripts/build/init-build-env.sh" "构建环境初始化脚本"
require_command zsh "zsh"

# 中台主干模块：共享基础、网关、六个业务库、六个业务运行模块，
# 以及与能力目录/内部契约直接相关的 system 模块。使用 -am 同时验证上游依赖。
MIDDLE_PLATFORM_MODULES="apex-common,apex-domain,apex-api-contract,apex-gateway,apex-system,apex-system-service,apex-auth,apex-auth-service,apex-workflow,apex-workflow-service,apex-file,apex-file-service,apex-office,apex-office-service,apex-integration,apex-integration-service"

MODE="targeted"
case "${1:-}" in
    "")
        ;;
    --targeted)
        MODE="targeted"
        ;;
    --full)
        MODE="full"
        ;;
    -h|--help)
        cat <<'USAGE'
用法：
  scripts/test/verify-middle-platform.sh
  scripts/test/verify-middle-platform.sh --targeted
  scripts/test/verify-middle-platform.sh --full

默认/--targeted  运行中台主干模块及上游依赖的 mvnd test。
--full          运行完整反应堆的 mvnd clean verify 门禁。

构建环境由 scripts/build/init-build-env.sh 在 zsh 子进程中加载，
并强制校验 mvnd 2.0.0-rc-3；脚本不支持跳过测试。
USAGE
        exit 0
        ;;
    *)
        log_error "未知参数：$1；使用 --help 查看用法"
        exit 2
        ;;
esac

log_info "验证模式：${MODE}"
log_info "项目根目录：${ROOT_DIR}"

# SDKMAN 的初始化脚本面向 zsh；环境加载、版本校验和 mvnd 必须处于同一 shell，
# 防止子进程继承漂移的 JAVA_HOME 或连接到其他版本的 mvnd daemon。
zsh -c '
    set -e
    cd "$1"
    source scripts/build/init-build-env.sh

    mvnd_version=$(mvnd --version 2>&1)
    if [[ "$mvnd_version" != *"(mvnd) 2.0.0-rc-3"* && "$mvnd_version" != *"mvnd 2.0.0-rc-3"* ]]; then
        print -u2 -- "需要 mvnd 2.0.0-rc-3，当前版本输出为：${mvnd_version}"
        exit 1
    fi

    case "$2" in
        targeted)
            mvnd -T4 -B -pl "${(j:,:)${(ps:,,:)3/#/#:}}" -am test
            ;;
        full)
            mvnd -T4 -B clean verify
            ;;
        *)
            print -u2 -- "内部错误：未知验证模式 $2"
            exit 2
            ;;
    esac
' zsh "$ROOT_DIR" "$MODE" "$MIDDLE_PLATFORM_MODULES"

log_success "中台验证命令已完成：${MODE}"

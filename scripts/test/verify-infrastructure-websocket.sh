#!/usr/bin/env bash
# 基础设施拆分与 office 站内信的固定环境验证入口。
# 该脚本只调用 mvnd：--infrastructure 验证五能力及消费者，--websocket
# 验证 office/gateway 相关模块，--full 执行完整 clean verify 门禁。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../lib/common.sh"

ROOT_DIR="$(get_project_root)"
require_directory "$ROOT_DIR"
require_file "${ROOT_DIR}/pom.xml"
require_file "${ROOT_DIR}/scripts/build/init-build-env.sh"
require_command zsh

MODE=""
case "${1:-}" in
    --infrastructure)
        MODE="infrastructure"
        ;;
    --websocket)
        MODE="websocket"
        ;;
    --full)
        MODE="full"
        ;;
    -h|--help)
        cat <<'USAGE'
用法：
  scripts/test/verify-infrastructure-websocket.sh --infrastructure
  scripts/test/verify-infrastructure-websocket.sh --websocket
  scripts/test/verify-infrastructure-websocket.sh --full

--infrastructure 运行五能力及直接消费者、上游依赖的 clean verify。
--websocket       运行 office 与 gateway 及依赖的 clean verify。
--full            运行完整 clean verify 门禁。
USAGE
        exit 0
        ;;
    *)
        log_error "必须指定 --infrastructure、--websocket 或 --full；使用 --help 查看用法"
        exit 2
        ;;
esac

log_info "验证模式：${MODE}"
log_info "构建根目录：${ROOT_DIR}"

# SDKMAN 初始化脚本使用 zsh。初始化、版本校验和 mvnd 必须在同一 shell
# 中完成，避免子 shell 继承漂移的 JAVA_HOME 或 mvnd daemon。
zsh -c '
    set -e
    cd "$1"
    source scripts/build/init-build-env.sh
    # SDKMAN 选择后再次核对 mvnd，不能因 PATH 漂移静默使用其他版本。
    mvnd_version=$(mvnd --version 2>&1)
    if [[ "$mvnd_version" != *"(mvnd) 2.0.0-rc-3"* && "$mvnd_version" != *"mvnd 2.0.0-rc-3"* ]]; then
        print -u2 -- "需要 mvnd 2.0.0-rc-3，当前 mvnd 版本不匹配"
        exit 1
    fi
    case "$2" in
        infrastructure)
            mvnd -T4 -B -pl apex-infrastructure,apex-system-service,apex-auth-service,apex-job-service,apex-example -am clean verify
            ;;
        websocket)
            mvnd -T4 -B -pl apex-office-service,apex-gateway -am clean verify
            ;;
        full)
            mvnd -T4 -B clean verify
            ;;
    esac
' zsh "$ROOT_DIR" "$MODE"

log_success "验证命令已完成：${MODE}"

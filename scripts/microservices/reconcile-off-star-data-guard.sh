#!/bin/bash
###############################################################################
# reconcile-off-star-data-guard.sh - off_* 存量对账守护（Phase 3 双写过渡）
#
# 【用途】
#   周期性「看门狗」：封装 reconcile-off-star-data.sh，对单体主库（apex_system）
#   与办公服务独立库（apex_office）6 张 off_* 表做行数 + checksum 对账；
#   一旦发现差异即输出告警详情（含时间戳）并以非 0 退出码结束，供 cron / 调度器
#   捕获后触发通知。对应方案：
#     docs/08-project-management/plans/office-off-star-extraction-plan.md
#     §4 Phase 3：「定时对账（行数 + checksum），差异告警」。
#
# 【设计要点】
#   1. 单一职责：只做「调度外壳 + 告警」，对账逻辑完全复用 reconcile 脚本，
#      避免两处维护同一套 checksum 算法。
#   2. 去重告警（--dedup，默认开）：把「上次对账结论 + 差异指纹」记到状态文件，
#      仅当结论由「一致」翻转为「不一致」、或差异指纹变化时才高亮告警，
#      避免每 15 分钟重复刷屏。加 --no-dedup 可强制每次都告警。
#   3. 全程落盘：每次运行追加一行摘要到日志文件（默认 logs/reconcile-guard-YYYYMM.log），
#      便于事后审计与趋势观察。
#   4. 非破坏性：只读对账，绝不写入任何库、绝不改写 .sql。
#
# 【退出码】（供调度器判断）
#   0 - 对账一致（健康）
#   1 - 对账不一致（需告警）
#   2 - 参数错误
#   3 - 前置失败（无法连接/缺 mysql 客户端等，无法完成对账）
#
# 【用法】
#   ./scripts/microservices/reconcile-off-star-data-guard.sh
#   SRC_PASSWORD=xxx DST_PASSWORD=yyy ./scripts/...guard.sh
#   ./scripts/microservices/reconcile-off-star-data-guard.sh --no-dedup --quiet
#
# 【cron 示例】（每 15 分钟对账一次，差异写入告警文件）
#   */15 * * * * cd /Users/work/apex-scaffold && \
#     SRC_PASSWORD=apex-root DST_PASSWORD=apex-root \
#     ./scripts/microservices/reconcile-off-star-data-guard.sh >> logs/cron-reconcile.log 2>&1
#
# @author Apex Team
# 日期：2026-10-11
# 版本：v1.0.0
###############################################################################

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
# shellcheck source=../lib/common.sh
source "${SCRIPT_DIR}/../lib/common.sh"

# shellcheck disable=SC2034
PROJECT_ROOT=$(get_project_root)

readonly RECONCILE_SCRIPT="${SCRIPT_DIR}/reconcile-off-star-data.sh"

# 源库（单体）
SRC_HOST="${SRC_HOST:-127.0.0.1}"
SRC_PORT="${SRC_PORT:-3306}"
SRC_USER="${SRC_USER:-root}"
SRC_PASSWORD="${SRC_PASSWORD:-apex-root}"
SRC_DB="${SRC_DB:-apex_system}"

# 目标库（办公服务）
DST_HOST="${DST_HOST:-127.0.0.1}"
DST_PORT="${DST_PORT:-3306}"
DST_USER="${DST_USER:-root}"
DST_PASSWORD="${DST_PASSWORD:-root}"
DST_DB="${DST_DB:-apex_office}"

# 运行态
DEDUP=1
QUIET=0
STATE_DIR="${STATE_DIR:-${PROJECT_ROOT}/.reconcile-guard}"
LOG_FILE="${LOG_FILE:-${PROJECT_ROOT}/logs/reconcile-guard-$(date +%Y%m).log}"
ALERT_FILE="${ALERT_FILE:-${PROJECT_ROOT}/logs/reconcile-guard-alert.log}"

###############################################################################
# 使用说明
###############################################################################
usage() {
    cat <<'EOF'
用法: reconcile-off-star-data-guard.sh [--no-dedup] [--quiet] [-h|--help]

参数:
  --no-dedup   关闭告警去重：无论结论是否变化，每次不一致都高亮告警
  --quiet      静默：仅在「不一致 / 前置失败」时向终端输出正文（日志照常落盘）
  -h, --help   显示帮助

连接参数（环境变量）:
  SRC_HOST/SRC_PORT/SRC_USER/SRC_PASSWORD/SRC_DB   源库（默认 root / apex_system）
  DST_HOST/DST_PORT/DST_USER/DST_PASSWORD/DST_DB   目标库（默认 root / apex_office）
  STATE_DIR                                        去重状态目录（默认 <root>/.reconcile-guard）
  LOG_FILE                                         运行摘要日志（默认 logs/reconcile-guard-YYYYMM.log）
  ALERT_FILE                                       告警流水（默认 logs/reconcile-guard-alert.log）

退出码:
  0=一致  1=不一致(告警)  2=参数错误  3=前置失败
EOF
}

###############################################################################
# 参数解析
###############################################################################
parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --no-dedup) DEDUP=0 ;;
            --quiet)    QUIET=1 ;;
            -h|--help)
                usage
                return 1
                ;;
            *)
                log_error "未知参数: $1"
                usage
                return 2
                ;;
        esac
        shift
    done
    return 0
}

###############################################################################
# 工具
###############################################################################
# 终端输出：quiet 模式下仅在 $2=force 时打印
emit() {
    local force="$1"; shift
    if [[ $QUIET == 0 || "$force" == "force" ]]; then
        printf '%s\n' "$*"
    fi
}

# 依据对账输出计算差异指纹（仅取「不一致 / 缺失」行，做稳定排序后哈希）
# 目的：结论不变但差异内容变化时仍能再次告警。
diff_fingerprint() {
    local out="$1"
    local lines
    lines=$(printf '%s\n' "$out" | grep -E '不一致|缺失' || true)
    if [[ -z "$lines" ]]; then
        printf 'CLEAN'
        return 0
    fi
    printf '%s\n' "$lines" | LC_ALL=C sort | shasum -a 256 | awk '{print $1}'
}

state_file() {
    printf '%s/%s.last\n' "$STATE_DIR" "$1"
}

###############################################################################
# 主流程
###############################################################################
main() {
    parse_args "$@" || return $?

    [[ -f "$RECONCILE_SCRIPT" ]] || {
        log_error "对账脚本不存在: ${RECONCILE_SCRIPT}"
        return 3
    }
    require_command "mysql" "MySQL 客户端(mysql)" || return 3

    mkdir -p "$STATE_DIR" "$(dirname "$LOG_FILE")"

    local ts
    ts=$(date +'%Y-%m-%d %H:%M:%S')

    print_header "off_* 对账守护 (Phase 3 双写过渡)"
    emit 0 "[${ts}] 开始对账：${SRC_DB}.off_* <-> ${DST_DB}.off_*"

    # 执行对账（捕获输出与退出码；reconcile 内部已做连接/缺表检查）
    local out rc
    set +e
    out=$(SRC_HOST="$SRC_HOST" SRC_PORT="$SRC_PORT" SRC_USER="$SRC_USER" \
          SRC_PASSWORD="$SRC_PASSWORD" SRC_DB="$SRC_DB" \
          DST_HOST="$DST_HOST" DST_PORT="$DST_PORT" DST_USER="$DST_USER" \
          DST_PASSWORD="$DST_PASSWORD" DST_DB="$DST_DB" \
          bash "$RECONCILE_SCRIPT" 2>&1)
    rc=$?
    set -e

    # 前置失败（reconcile 用 1 表示不一致/前置失败，用输出特征区分「无法完成对账」）
    if printf '%s\n' "$out" | grep -qE '无法连通|未找到|不存在'; then
        emit force "$out"
        printf '[%s] [PREFLIGHT-FAIL] 无法完成对账（连接/依赖异常），退出码=%s\n' "$ts" "$rc" >> "$LOG_FILE"
        log_failure "对账守护：前置失败，无法完成对账"
        return 3
    fi

    local fp
    fp=$(diff_fingerprint "$out")

    if [[ $rc == 0 ]]; then
        emit 0 "$out"
        printf '[%s] [OK] 六表行数与 checksum 全部一致\n' "$ts" >> "$LOG_FILE"
        # 结论翻转回一致：清理上一轮告警状态并记录恢复
        if [[ -f "$(state_file verdict)" ]] && [[ "$(cat "$(state_file verdict)")" == "DIFF" ]]; then
            printf '[%s] [RECOVERED] 对账已恢复一致（此前存在差异）\n' "$ts" >> "$LOG_FILE"
            printf '[%s] [RECOVERED] 对账恢复一致\n' "$ts" >> "$ALERT_FILE"
        fi
        printf 'OK\n' > "$(state_file verdict)"
        printf '%s\n' "$fp" > "$(state_file fingerprint)"
        log_success "对账守护：一致"
        return 0
    fi

    # 到这里 = 对账不一致
    local prev_verdict="" prev_fp=""
    [[ -f "$(state_file verdict)" ]] && prev_verdict=$(cat "$(state_file verdict)")
    [[ -f "$(state_file fingerprint)" ]] && prev_fp=$(cat "$(state_file fingerprint)")

    local should_alert=1
    if [[ $DEDUP == 1 && "$prev_verdict" == "DIFF" && "$prev_fp" == "$fp" ]]; then
        should_alert=0
    fi

    if [[ $should_alert == 1 ]]; then
        emit force "$out"
        {
            printf '============================================================\n'
            printf '[%s] [ALERT] off_* 对账不一致 (fp=%s)\n' "$ts" "$fp"
            printf '%s\n' "$out"
        } >> "$ALERT_FILE"
        printf '[%s] [DIFF] 对账不一致 (fp=%s, 告警已记录)\n' "$ts" "$fp" >> "$LOG_FILE"
        log_failure "对账守护：发现差异，已告警（详见 $(basename "$ALERT_FILE")）"
    else
        emit force "$out"
        printf '[%s] [DIFF] 对账不一致 (fp=%s, 与上轮相同，去重跳过告警)\n' "$ts" "$fp" >> "$LOG_FILE"
        log_warn "对账守护：差异持续（与上轮相同，已去重）"
    fi

    printf 'DIFF\n' > "$(state_file verdict)"
    printf '%s\n' "$fp" > "$(state_file fingerprint)"
    return 1
}

main "$@"

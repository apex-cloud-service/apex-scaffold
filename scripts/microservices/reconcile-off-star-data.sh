#!/bin/bash
###############################################################################
# reconcile-off-star-data.sh - off_* 存量数据对账（Phase 2 验收）
#
# 【用途】
#   对比单体主库（apex_system）与办公服务独立库（apex_office）中 6 张 off_* 表的
#   「行数 + 内容校验和(checksum)」，作为 off_* 域提取方案 Phase 2 的验收依据。
#   对应方案：docs/08-project-management/plans/office-off-star-extraction-plan.md
#   §6 验收标准：「apex_office 六表行数与单体源表一致，checksum 对账通过」。
#
# 【对账算法】
#   1. 行数：COUNT(*)。
#   2. 内容校验和：对该表全部列做
#        COALESCE(SUM(CRC32(CONCAT_WS(0x1f, IFNULL(CAST(col AS CHAR),'<NULL>'), ...))),0)
#      —— 与行序无关、可跨引擎比较，NULL 以字面量 '<NULL>' 占位避免歧义。
#      列清单由 information_schema 动态获取，故对表结构演进鲁棒。
#
# 【非破坏性】只读查询，绝不写入任何库。
#
# 【连接参数】（环境变量，均有默认值）
#   SRC_HOST/SRC_PORT/SRC_USER/SRC_PASSWORD/SRC_DB  源库（默认 127.0.0.1:3306 root / apex_system）
#   DST_HOST/DST_PORT/DST_USER/DST_PASSWORD/DST_DB  目标库（默认 127.0.0.1:3306 root / apex_office）
#
# 【用法】
#   ./scripts/microservices/reconcile-off-star-data.sh
#   SRC_PASSWORD=xxx DST_PASSWORD=yyy ./scripts/microservices/reconcile-off-star-data.sh
#
# 【退出码】
#   0 - 六表行数与 checksum 全部一致
#   1 - 存在不一致或前置检查失败
#   2 - 参数错误
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

readonly OFF_TABLES=(off_message_template off_message off_message_recipient off_department off_employee off_sync_history)

SRC_HOST="${SRC_HOST:-127.0.0.1}"
SRC_PORT="${SRC_PORT:-3306}"
SRC_USER="${SRC_USER:-root}"
SRC_PASSWORD="${SRC_PASSWORD:-apex-root}"
SRC_DB="${SRC_DB:-apex_system}"

DST_HOST="${DST_HOST:-127.0.0.1}"
DST_PORT="${DST_PORT:-3306}"
DST_USER="${DST_USER:-root}"
DST_PASSWORD="${DST_PASSWORD:-root}"
DST_DB="${DST_DB:-apex_office}"

usage() {
    cat <<'EOF'
用法: reconcile-off-star-data.sh [-h|--help]

说明:
  只读对账：比较源库(apex_system)与目标库(apex_office)中 6 张 off_* 表的
  行数与内容校验和(checksum)，全部一致时退出码为 0。

连接参数（环境变量）:
  SRC_HOST/SRC_PORT/SRC_USER/SRC_PASSWORD/SRC_DB   源库（默认 root / apex_system）
  DST_HOST/DST_PORT/DST_USER/DST_PASSWORD/DST_DB   目标库（默认 root / apex_office）
EOF
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
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
# 连接与表存在性
###############################################################################
check_conn() {
    local label="$1" host="$2" port="$3" user="$4" pw="$5" db="$6"
    if MYSQL_PWD="$pw" mysql -N -B -h "$host" -P "$port" -u "$user" -e "SELECT 1;" "$db" >/dev/null 2>&1; then
        log_success "${label} 可连通: ${user}@${host}:${port}/${db}"
        return 0
    fi
    log_failure "${label} 无法连通: ${user}@${host}:${port}/${db}"
    return 1
}

table_exists() {
    local host="$1" port="$2" user="$3" pw="$4" db="$5" tbl="$6"
    local cnt
    cnt=$(MYSQL_PWD="$pw" mysql -N -B -h "$host" -P "$port" -u "$user" \
        -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='${db}' AND TABLE_NAME='${tbl}';" 2>/dev/null) || return 1
    [[ "$cnt" == "1" ]]
}

###############################################################################
# 计算某表的 (行数, 校验和)
# 输出：<count>\t<checksum>；列信息缺失时输出 <count>\tNA
###############################################################################
table_signature() {
    local host="$1" port="$2" user="$3" pw="$4" db="$5" tbl="$6"

    local meta_sql col_expr
    meta_sql=$(cat <<SQL
SELECT GROUP_CONCAT(CONCAT('IFNULL(CAST(\`', COLUMN_NAME, '\` AS CHAR),''<NULL>'')') ORDER BY ORDINAL_POSITION SEPARATOR ',')
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='${db}' AND TABLE_NAME='${tbl}';
SQL
)
    col_expr=$(MYSQL_PWD="$pw" mysql -N -B -h "$host" -P "$port" -u "$user" -e "$meta_sql" 2>/dev/null) || return 1

    if [[ -z "$col_expr" || "$col_expr" == "NULL" ]]; then
        local cnt
        cnt=$(MYSQL_PWD="$pw" mysql -N -B -h "$host" -P "$port" -u "$user" \
            -e "SELECT COUNT(*) FROM \`${db}\`.\`${tbl}\`;" 2>/dev/null) || return 1
        printf '%s\tNA\n' "$cnt"
        return 0
    fi

    local sig_sql
    sig_sql="SELECT COUNT(*), COALESCE(SUM(CRC32(CONCAT_WS(0x1f, ${col_expr}))),0) FROM \`${db}\`.\`${tbl}\`;"
    MYSQL_PWD="$pw" mysql -N -B -h "$host" -P "$port" -u "$user" -e "$sig_sql" 2>/dev/null
}

###############################################################################
# 主流程
###############################################################################
main() {
    parse_args "$@" || return $?

    print_header "off_* 存量数据对账 (Phase 2 验收)"
    require_command "mysql" "MySQL 客户端(mysql)"
    if ! check_conn "源库(单体)" "$SRC_HOST" "$SRC_PORT" "$SRC_USER" "$SRC_PASSWORD" "$SRC_DB"; then
        return 1
    fi
    if ! check_conn "目标库(办公)" "$DST_HOST" "$DST_PORT" "$DST_USER" "$DST_PASSWORD" "$DST_DB"; then
        return 1
    fi

    print_section "对账明细 (行数 / checksum)"
    printf "%-28s | %10s | %10s | %12s | %12s | %s\n" "表" "源行数" "目标行数" "源checksum" "目标checksum" "结论"
    print_separator "-" 100

    local fail=0
    for tbl in "${OFF_TABLES[@]}"; do
        local src_missing=0 dst_missing=0
        table_exists "$SRC_HOST" "$SRC_PORT" "$SRC_USER" "$SRC_PASSWORD" "$SRC_DB" "$tbl" || src_missing=1
        table_exists "$DST_HOST" "$DST_PORT" "$DST_USER" "$DST_PASSWORD" "$DST_DB" "$tbl" || dst_missing=1

        if [[ $src_missing == 1 || $dst_missing == 1 ]]; then
            printf "%-28s | %10s | %10s | %12s | %12s | %s\n" \
                "$tbl" "-" "-" "-" "-" "缺失(源=${src_missing} 目标=${dst_missing})"
            fail=1
            continue
        fi

        local src_sig dst_sig
        src_sig=$(table_signature "$SRC_HOST" "$SRC_PORT" "$SRC_USER" "$SRC_PASSWORD" "$SRC_DB" "$tbl")
        dst_sig=$(table_signature "$DST_HOST" "$DST_PORT" "$DST_USER" "$DST_PASSWORD" "$DST_DB" "$tbl")

        local src_cnt src_sum dst_cnt dst_sum verdict
        src_cnt=${src_sig%%$'\t'*}
        src_sum=${src_sig#*$'\t'}
        dst_cnt=${dst_sig%%$'\t'*}
        dst_sum=${dst_sig#*$'\t'}

        if [[ "$src_cnt" == "$dst_cnt" && "$src_sum" == "$dst_sum" ]]; then
            verdict="一致"
        else
            verdict="不一致"
            fail=1
        fi
        printf "%-28s | %10s | %10s | %12s | %12s | %s\n" \
            "$tbl" "$src_cnt" "$dst_cnt" "$src_sum" "$dst_sum" "$verdict"
    done

    print_separator "=" 40
    if [[ $fail == 0 ]]; then
        log_success "对账通过：六表行数与 checksum 全部一致"
        return 0
    fi
    log_failure "对账未通过：存在行数或 checksum 差异（若刚同步请确认停写窗口/重跑同步）"
    return 1
}

main "$@"
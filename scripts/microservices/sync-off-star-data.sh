#!/bin/bash
###############################################################################
# sync-off-star-data.sh - off_* 存量数据全量同步（Phase 2）
#
# 【用途】
#   将单体主库中的 6 张 off_* 表全量导出并导入办公服务独立库（apex_office），
#   完成 off_* 域提取方案的 Phase 2「全量存量同步」。
#   对应方案：docs/08-project-management/plans/office-off-star-extraction-plan.md
#   §4 Phase 2：mysqldump 按表导出 → 导入 apex_office 库。
#
# 【非破坏性保证】
#   1. 默认 dry-run：仅打印将执行的命令与表，不实际写库。
#   2. --apply 才实际执行导入。
#   3. 默认不清理目标表；仅当 --apply 且显式 --truncate-target 时才 TRUNCATE。
#   4. 导入使用 INSERT IGNORE（--insert-ignore），重复执行不因主键报错。
#   5. 绝不改写任何 .sql 迁移文件，绝不触碰单体数据（只读导出）。
#
# 【前置条件】
#   - 已安装 mysql 与 mysqldump 客户端。
#   - 源库（单体 apex_system）与目标库（apex_office）均可连通。
#   - 目标库已由 apex-office-service Flyway V2 建好 6 张 off_* 表。
#
# 【连接参数】（环境变量，均有默认值；口令建议用环境变量注入）
#   SRC_HOST/SRC_PORT/SRC_USER/SRC_PASSWORD/SRC_DB  源库（默认 127.0.0.1:3306 root / apex_system）
#   DST_HOST/DST_PORT/DST_USER/DST_PASSWORD/DST_DB  目标库（默认 127.0.0.1:3306 root / apex_office）
#
# 【用法】
#   ./scripts/microservices/sync-off-star-data.sh                    # 预览（dry-run）
#   ./scripts/microservices/sync-off-star-data.sh --apply            # 全量同步
#   ./scripts/microservices/sync-off-star-data.sh --apply --truncate-target  # 清目标表后重导
#   SRC_PASSWORD=xxx DST_PASSWORD=yyy ./scripts/... --apply          # 注入口令
#
# 【退出码】
#   0 - 全部成功（或 dry-run 预览完成）
#   1 - 失败（连接/缺少命令/缺表）
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

APPLY=0
TRUNCATE_TARGET=0

# ---------------------------------------------------------------
# 表清单（按外键依赖顺序：先父后子，保证导入引用完整）
#   off_message_recipient 引用 off_message
#   off_message            引用 off_message_template
#   off_employee           引用 off_department
# ---------------------------------------------------------------
readonly OFF_TABLES=(off_message_template off_message off_message_recipient off_department off_employee off_sync_history)

# 源库（单体）：开发环境默认为 apex_system / root:apex-root
SRC_HOST="${SRC_HOST:-127.0.0.1}"
SRC_PORT="${SRC_PORT:-3306}"
SRC_USER="${SRC_USER:-root}"
SRC_PASSWORD="${SRC_PASSWORD:-apex-root}"
SRC_DB="${SRC_DB:-apex_system}"

# 目标库（办公服务）：apex_office / root:root（application.yml 默认 DB_PASSWORD=root）
DST_HOST="${DST_HOST:-127.0.0.1}"
DST_PORT="${DST_PORT:-3306}"
DST_USER="${DST_USER:-root}"
DST_PASSWORD="${DST_PASSWORD:-root}"
DST_DB="${DST_DB:-apex_office}"

###############################################################################
# 使用说明
###############################################################################
usage() {
    cat <<'EOF'
用法: sync-off-star-data.sh [--apply] [--truncate-target] [-h|--help]

参数:
  --apply            实际执行导入；缺省为 dry-run（仅预览，不写库）
  --truncate-target  导入前 TRUNCATE 目标表（须与 --apply 搭配，破坏性）
  -h, --help         显示帮助

非破坏性约定:
  1. 默认 dry-run；--apply 才写库。
  2. 默认不清理目标表；--truncate-target 也仅在与 --apply 同时给出时才生效。
  3. 源库只读导出，绝不修改单体数据。

连接参数（环境变量）:
  SRC_HOST/SRC_PORT/SRC_USER/SRC_PASSWORD/SRC_DB   源库（默认 root / apex_system）
  DST_HOST/DST_PORT/DST_USER/DST_PASSWORD/DST_DB   目标库（默认 root / apex_office）
EOF
}

###############################################################################
# 参数解析
###############################################################################
parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --apply)
                APPLY=1
                ;;
            --truncate-target)
                TRUNCATE_TARGET=1
                ;;
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

    if [[ $TRUNCATE_TARGET == 1 && $APPLY != 1 ]]; then
        log_warn "--truncate-target 仅在 --apply 时生效；本次仍按 dry-run 执行"
        TRUNCATE_TARGET=0
    fi
    return 0
}

###############################################################################
# 连接与前置检查
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

preflight() {
    print_header "前置检查 (Preflight)"
    require_command "mysql" "MySQL 客户端(mysql)"
    require_command "mysqldump" "MySQL 备份工具(mysqldump)"
    local ok=0

    check_conn "源库(单体)" "$SRC_HOST" "$SRC_PORT" "$SRC_USER" "$SRC_PASSWORD" "$SRC_DB" || ok=1
    check_conn "目标库(办公)" "$DST_HOST" "$DST_PORT" "$DST_USER" "$DST_PASSWORD" "$DST_DB" || ok=1

    # 源表必须存在（来源权威）
    for tbl in "${OFF_TABLES[@]}"; do
        if table_exists "$SRC_HOST" "$SRC_PORT" "$SRC_USER" "$SRC_PASSWORD" "$SRC_DB" "$tbl"; then
            log_success "源表存在: ${SRC_DB}.${tbl}"
        else
            log_failure "源表缺失: ${SRC_DB}.${tbl}"
            ok=1
        fi
    done
    # 目标表必须已由 Flyway V2 建立（只导数据，不建表）
    for tbl in "${OFF_TABLES[@]}"; do
        if table_exists "$DST_HOST" "$DST_PORT" "$DST_USER" "$DST_PASSWORD" "$DST_DB" "$tbl"; then
            log_success "目标表存在: ${DST_DB}.${tbl}"
        else
            log_failure "目标表缺失: ${DST_DB}.${tbl}（请先让 apex-office-service Flyway V2 建表）"
            ok=1
        fi
    done
    return "$ok"
}

###############################################################################
# 单表导出→导入
###############################################################################
sync_table() {
    local tbl="$1"

    if [[ $TRUNCATE_TARGET == 1 ]]; then
        if [[ $APPLY == 1 ]]; then
            MYSQL_PWD="$DST_PASSWORD" mysql -h "$DST_HOST" -P "$DST_PORT" -u "$DST_USER" \
                -e "TRUNCATE TABLE \`${DST_DB}\`.\`${tbl}\`;" 2>/dev/null || {
                log_failure "TRUNCATE 失败: ${DST_DB}.${tbl}"
                return 1
            }
            log_info "已 TRUNCATE 目标表: ${DST_DB}.${tbl}"
        else
            log_info "[dry-run] 将 TRUNCATE 目标表: ${DST_DB}.${tbl}"
        fi
    fi

    if [[ $APPLY != 1 ]]; then
        log_info "[dry-run] 将同步: ${SRC_DB}.${tbl} -> ${DST_DB}.${tbl}"
        log_info "[dry-run] 命令: mysqldump -h ${SRC_HOST} -P ${SRC_PORT} -u ${SRC_USER} --single-transaction --quick --no-create-info --skip-triggers --set-gtid-purged=OFF --insert-ignore --complete-insert --default-character-set=utf8mb4 --skip-add-locks --skip-lock-tables ${SRC_DB} ${tbl} | mysql -h ${DST_HOST} -P ${DST_PORT} -u ${DST_USER} --default-character-set=utf8mb4 ${DST_DB}"
        return 0
    fi

    log_info "同步中: ${SRC_DB}.${tbl} -> ${DST_DB}.${tbl}"
    if MYSQL_PWD="$SRC_PASSWORD" mysqldump \
        -h "$SRC_HOST" -P "$SRC_PORT" -u "$SRC_USER" \
        --single-transaction --quick --no-create-info --skip-triggers \
        --set-gtid-purged=OFF --insert-ignore --complete-insert \
        --default-character-set=utf8mb4 --skip-add-locks --skip-lock-tables \
        "$SRC_DB" "$tbl" \
        | MYSQL_PWD="$DST_PASSWORD" mysql \
            -h "$DST_HOST" -P "$DST_PORT" -u "$DST_USER" \
            --default-character-set=utf8mb4 "$DST_DB"; then
        log_success "完成: ${tbl}"
    else
        log_failure "失败: ${tbl}"
        return 1
    fi
}

###############################################################################
# 主流程
###############################################################################
main() {
    parse_args "$@" || return $?

    print_header "off_* 存量数据全量同步 (Phase 2)"
    if [[ $APPLY != 1 ]]; then
        log_warn "当前为 dry-run 模式：仅预览，不写库。加 --apply 实际执行。"
    else
        log_info "执行模式：--apply（实际写库）"
    fi

    if ! preflight; then
        log_failure "前置检查未通过，中止执行"
        return 1
    fi

    print_header "开始同步 ${SRC_DB} -> ${DST_DB}"
    local fail=0
    for tbl in "${OFF_TABLES[@]}"; do
        sync_table "$tbl" || fail=1
    done

    print_separator "=" 40
    if [[ $fail == 0 ]]; then
        log_success "全部同步完成。请运行对账脚本核对行数与 checksum："
        log_info "  ./scripts/microservices/reconcile-off-star-data.sh"
        return 0
    fi
    log_failure "存在失败项，请检查后重跑（重复执行通过 INSERT IGNORE 保持幂等）"
    return 1
}

main "$@"
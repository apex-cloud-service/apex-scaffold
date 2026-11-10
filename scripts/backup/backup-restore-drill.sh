#!/usr/bin/env bash
# =============================================================================
# Apex Scaffold - 数据库备份恢复隔离演练脚本
# -----------------------------------------------------------------------------
# 功能:
#   1. 校验 MySQL .sql/.sql.gz 或 PostgreSQL .dump 备份文件的可读性
#   2. 在数据库实例中创建带 _drill 后缀的隔离演练库，不触碰生产库
#   3. 调用 db-restore.sh / pg-restore.sh 将备份恢复到演练库
#   4. 校验 sys_menu、sys_user、flyway_schema_history 及 Flyway 最新版本
#   5. 通过 EXIT trap 删除演练库、临时凭据文件和容器内临时文件
#
# 安全约束:
#   - 必须显式指定 --yes；创建、恢复和删除演练库均不再交互确认
#   - 演练库名必须包含 _drill，且不得等于源库名
#   - 密码只从 PGPASSWORD / DB_MASTER_PASSWORD 或权限 400/600 的
#     --password-file 读取，不使用 --pass 参数
#
# 用法:
#   ./backup-restore-drill.sh --engine mysql --file /data/backups/apex_system.sql.gz --yes
#   ./backup-restore-drill.sh --engine postgresql --file /data/backups/apex_system.dump \
#       --password-file /secure/pg.pass --yes
#
# 退出码:
#   0 - 演练执行且全部校验通过
#   1 - 参数错误
#   2 - 恢复后数据校验失败
#   3 - 客户端缺失、数据库不可连接或环境不可用（SKIP）
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=../lib/common.sh
source "${SCRIPT_DIR}/../lib/common.sh"

ENGINE=""
BACKUP_FILE=""
SOURCE_DB=""
DRILL_DB=""
DB_HOST="127.0.0.1"
DB_PORT=""
DB_USER=""
DB_PASS=""
PASSWORD_FILE=""
CONTAINER=""
CONTAINER_HOST=""
CONTAINER_PORT=""
ASSUME_YES=false
EXPECTED_FLYWAY_VERSION=""

MYSQL_CNF=""
CONTAINER_CNF=""
PGPASS_FILE=""
CONTAINER_PGPASS=""
CONTAINER_DUMP=""
DRILL_PASSWORD_FILE=""
DRILL_CREATED=false

usage() {
    cat <<'USAGE'
用法:
  ./backup-restore-drill.sh --engine <mysql|postgresql> --file <backup> --yes [选项]

选项:
  --engine <mysql|postgresql>  数据库类型（必填）
  --file <path>                备份文件；MySQL 支持 .sql/.sql.gz，PostgreSQL 仅 .dump
  --db <name>                  源库名，仅用于隔离校验和 Flyway 版本对比（默认按类型取环境变量）
  --drill-db <name>            演练库名；必须包含 _drill，默认 <源库>_drill_<时间戳>
  --host <host>                数据库地址，默认 127.0.0.1
  --port <port>                数据库端口，默认 MySQL 3306 / PostgreSQL 5432
  --user <user>                数据库账号
  --password-file <file>       从权限 400/600 的文件读取密码（首行）
  --container <name>           Podman 容器名；通过 podman exec 执行数据库客户端
  --container-host <host>      容器内数据库地址，默认 127.0.0.1
  --container-port <port>      容器内数据库端口，默认同宿主机端口
  --expected-flyway-version <v> 可选；要求演练库 Flyway 最新版本等于该值
  --yes                        非交互确认创建、恢复和删除演练库
  -h, --help                   显示帮助

环境变量:
  MySQL:      DB_MASTER_HOST, DB_MASTER_PORT, DB_MASTER_USERNAME,
              DB_MASTER_PASSWORD, MYSQL_DATABASE
  PostgreSQL: PGHOST, PGPORT, PGUSER, PGPASSWORD, PGDATABASE,
              POSTGRES_CONTAINER_HOST, POSTGRES_CONTAINER_PORT
USAGE
}

fail() {
    local code="${1:-1}"
    shift || true
    log_error "$*"
    exit "$code"
}

require_option_value() {
    [[ $# -ge 2 && -n "${2:-}" ]] || fail 1 "参数 $1 缺少值"
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --engine) require_option_value "$@"; ENGINE="$2"; shift 2 ;;
        --file) require_option_value "$@"; BACKUP_FILE="$2"; shift 2 ;;
        --db) require_option_value "$@"; SOURCE_DB="$2"; shift 2 ;;
        --drill-db) require_option_value "$@"; DRILL_DB="$2"; shift 2 ;;
        --host) require_option_value "$@"; DB_HOST="$2"; shift 2 ;;
        --port) require_option_value "$@"; DB_PORT="$2"; shift 2 ;;
        --user) require_option_value "$@"; DB_USER="$2"; shift 2 ;;
        --password-file) require_option_value "$@"; PASSWORD_FILE="$2"; shift 2 ;;
        --container) require_option_value "$@"; CONTAINER="$2"; shift 2 ;;
        --container-host) require_option_value "$@"; CONTAINER_HOST="$2"; shift 2 ;;
        --container-port) require_option_value "$@"; CONTAINER_PORT="$2"; shift 2 ;;
        --expected-flyway-version) require_option_value "$@"; EXPECTED_FLYWAY_VERSION="$2"; shift 2 ;;
        --pass|--password) fail 1 "为安全起见不支持 $1；请使用环境变量或 --password-file" ;;
        --yes) ASSUME_YES=true; shift ;;
        -h|--help) usage; exit 0 ;;
        *) fail 1 "未知参数: $1" ;;
    esac
done

[[ "$ENGINE" == "mysql" || "$ENGINE" == "postgresql" ]] || fail 1 "--engine 必须为 mysql 或 postgresql"
[[ -n "$BACKUP_FILE" ]] || fail 1 "必须通过 --file 指定备份文件"
[[ -f "$BACKUP_FILE" ]] || fail 1 "备份文件不存在: ${BACKUP_FILE}"
[[ "$ASSUME_YES" == "true" ]] || fail 1 "恢复演练会创建并删除数据库，必须显式指定 --yes"
[[ "$DB_HOST" =~ ^[A-Za-z0-9_.:-]+$ ]] || fail 1 "数据库地址不合法: ${DB_HOST}"
[[ -n "$CONTAINER" ]] || [[ "$DB_HOST" != *"'"* ]] || fail 1 "数据库地址不得包含单引号"

if [[ "$ENGINE" == "mysql" ]]; then
    SOURCE_DB="${SOURCE_DB:-${MYSQL_DATABASE:-apex_system}}"
    DB_PORT="${DB_PORT:-${DB_MASTER_PORT:-3306}}"
    DB_USER="${DB_USER:-${DB_MASTER_USERNAME:-root}}"
    DB_PASS="${DB_MASTER_PASSWORD:-}"
else
    SOURCE_DB="${SOURCE_DB:-${PGDATABASE:-apex_system}}"
    DB_PORT="${DB_PORT:-${PGPORT:-5432}}"
    DB_USER="${DB_USER:-${PGUSER:-postgres}}"
    DB_PASS="${PGPASSWORD:-}"
fi

CONTAINER_HOST="${CONTAINER_HOST:-${POSTGRES_CONTAINER_HOST:-127.0.0.1}}"
CONTAINER_PORT="${CONTAINER_PORT:-${POSTGRES_CONTAINER_PORT:-$DB_PORT}}"
[[ "$DB_PORT" =~ ^[0-9]+$ ]] && (( DB_PORT >= 1 && DB_PORT <= 65535 )) || fail 1 "端口必须为 1-65535"
[[ "$CONTAINER_PORT" =~ ^[0-9]+$ ]] && (( CONTAINER_PORT >= 1 && CONTAINER_PORT <= 65535 )) || fail 1 "容器内端口必须为 1-65535"
[[ "$SOURCE_DB" =~ ^[A-Za-z0-9_]+$ ]] || fail 1 "源库名仅允许字母、数字和下划线: ${SOURCE_DB}"
if [[ -z "$DRILL_DB" ]]; then
    DRILL_DB="${SOURCE_DB}_drill_$(date +%Y%m%d_%H%M%S)"
fi
[[ "$DRILL_DB" =~ ^[A-Za-z0-9_]+$ ]] || fail 1 "演练库名仅允许字母、数字和下划线: ${DRILL_DB}"
[[ "$DRILL_DB" == *"_drill"* ]] || fail 1 "演练库名必须包含 _drill: ${DRILL_DB}"
[[ "$DRILL_DB" != "$SOURCE_DB" ]] || fail 1 "演练库不得等于源库: ${DRILL_DB}"
(( ${#DRILL_DB} <= 63 )) || fail 1 "演练库名不得超过 63 个字符: ${DRILL_DB}"

if [[ -n "$PASSWORD_FILE" ]]; then
    require_file "$PASSWORD_FILE" "密码文件" || exit 1
    mode="$(stat -c '%a' "$PASSWORD_FILE" 2>/dev/null || stat -f '%Lp' "$PASSWORD_FILE" 2>/dev/null || true)"
    [[ "$mode" == "400" || "$mode" == "600" ]] || fail 1 "密码文件权限必须为 400 或 600: ${PASSWORD_FILE}"
    IFS= read -r DB_PASS < "$PASSWORD_FILE" || true
fi
[[ -n "$DB_PASS" ]] || fail 1 "必须通过环境变量或 --password-file 提供数据库密码"
[[ "$DB_PASS" != *$'\n'* && "$DB_PASS" != *$'\r'* ]] || fail 1 "数据库密码不得包含换行符"

if [[ -n "$CONTAINER" ]]; then
    require_command podman "Podman" || exit 3
elif [[ "$ENGINE" == "mysql" ]]; then
    require_command mysql "MySQL 客户端 mysql" || exit 3
else
    require_command psql "PostgreSQL 客户端 psql" || exit 3
    require_command pg_restore "PostgreSQL 客户端 pg_restore" || exit 3
fi

cleanup() {
    local exit_code=$?
    set +e
    if [[ "$DRILL_CREATED" == "true" ]]; then
        log_info "清理演练库: ${DRILL_DB}"
        if [[ "$ENGINE" == "mysql" ]]; then
            if [[ -n "$CONTAINER" && -n "$CONTAINER_CNF" ]]; then
                podman exec -i "$CONTAINER" mysql --defaults-extra-file="$CONTAINER_CNF" -e "DROP DATABASE IF EXISTS \`${DRILL_DB}\`;" >/dev/null 2>&1
            elif [[ -n "$MYSQL_CNF" ]]; then
                mysql --defaults-extra-file="$MYSQL_CNF" -e "DROP DATABASE IF EXISTS \`${DRILL_DB}\`;" >/dev/null 2>&1
            fi
        else
            if [[ -n "$CONTAINER" && -n "$CONTAINER_PGPASS" ]]; then
                podman exec -e PGPASSFILE="$CONTAINER_PGPASS" -e PGHOST="$CONTAINER_HOST" \
                    -e PGPORT="$CONTAINER_PORT" -e PGUSER="$DB_USER" "$CONTAINER" \
                    psql -X -q -d postgres -c "DROP DATABASE IF EXISTS \"${DRILL_DB}\" WITH (FORCE);" >/dev/null 2>&1
            elif [[ -n "$PGPASS_FILE" ]]; then
                PGPASSFILE="$PGPASS_FILE" PGHOST="$DB_HOST" PGPORT="$DB_PORT" PGUSER="$DB_USER" \
                    psql -X -q -d postgres -c "DROP DATABASE IF EXISTS \"${DRILL_DB}\" WITH (FORCE);" >/dev/null 2>&1
            fi
        fi
    fi
    if [[ -n "$CONTAINER_DUMP" && -n "$CONTAINER" ]]; then
        podman exec "$CONTAINER" rm -f -- "$CONTAINER_DUMP" >/dev/null 2>&1
    fi
    if [[ -n "$CONTAINER_CNF" && -n "$CONTAINER" ]]; then
        podman exec "$CONTAINER" rm -f -- "$CONTAINER_CNF" >/dev/null 2>&1
    fi
    if [[ -n "$CONTAINER_PGPASS" && -n "$CONTAINER" ]]; then
        podman exec "$CONTAINER" rm -f -- "$CONTAINER_PGPASS" >/dev/null 2>&1
    fi
    [[ -z "$MYSQL_CNF" ]] || rm -f -- "$MYSQL_CNF"
    [[ -z "$PGPASS_FILE" ]] || rm -f -- "$PGPASS_FILE"
    [[ -z "$DRILL_PASSWORD_FILE" ]] || rm -f -- "$DRILL_PASSWORD_FILE"
    exit "$exit_code"
}
trap cleanup EXIT INT TERM

escape_mysql_option_value() {
    local value="$1"
    value="${value//\\/\\\\}"
    value="${value//\"/\\\"}"
    printf '%s' "$value"
}

escape_pgpass_value() {
    local value="$1"
    value="${value//\\/\\\\}"
    value="${value//:/\\:}"
    printf '%s' "$value"
}

setup_mysql_client() {
    MYSQL_CNF="$(mktemp "${TMPDIR:-/tmp}/apex-drill-mysql.XXXXXX")"
    chmod 600 "$MYSQL_CNF"
    {
        printf '[client]\n'
        printf 'host="%s"\n' "$(escape_mysql_option_value "$DB_HOST")"
        printf 'port=%s\n' "$DB_PORT"
        printf 'user="%s"\n' "$(escape_mysql_option_value "$DB_USER")"
        printf 'password="%s"\n' "$(escape_mysql_option_value "$DB_PASS")"
    } > "$MYSQL_CNF"
    if [[ -n "$CONTAINER" ]]; then
        CONTAINER_CNF="/tmp/apex-drill-mysql-$$-${RANDOM}.cnf"
        podman cp "$MYSQL_CNF" "${CONTAINER}:${CONTAINER_CNF}" >/dev/null || return 3
        podman exec "$CONTAINER" chmod 600 "$CONTAINER_CNF" >/dev/null || return 3
        MYSQL_ARGS=(podman exec -i "$CONTAINER" mysql --defaults-extra-file="$CONTAINER_CNF")
    else
        MYSQL_ARGS=(mysql --defaults-extra-file="$MYSQL_CNF")
    fi
}

setup_pg_client() {
    PGPASS_FILE="$(mktemp "${TMPDIR:-/tmp}/apex-drill-pgpass.XXXXXX")"
    chmod 600 "$PGPASS_FILE"
    local connect_host="$DB_HOST" connect_port="$DB_PORT"
    if [[ -n "$CONTAINER" ]]; then
        connect_host="$CONTAINER_HOST"
        connect_port="$CONTAINER_PORT"
    fi
    # 数据库字段使用 *，同一凭据可连接 postgres 与后续演练库。
    printf '%s:%s:%s:%s:%s\n' \
        "$(escape_pgpass_value "$connect_host")" \
        "$(escape_pgpass_value "$connect_port")" \
        "*" \
        "$(escape_pgpass_value "$DB_USER")" \
        "$(escape_pgpass_value "$DB_PASS")" > "$PGPASS_FILE"
    if [[ -n "$CONTAINER" ]]; then
        CONTAINER_PGPASS="/tmp/apex-drill-pgpass-$$-${RANDOM}"
        podman cp "$PGPASS_FILE" "${CONTAINER}:${CONTAINER_PGPASS}" >/dev/null || return 3
        podman exec "$CONTAINER" chmod 600 "$CONTAINER_PGPASS" >/dev/null || return 3
        PG_ENV=(podman exec -i -e PGPASSFILE="$CONTAINER_PGPASS" -e PGHOST="$CONTAINER_HOST"
                -e PGPORT="$CONTAINER_PORT" -e PGUSER="$DB_USER" "$CONTAINER")
    else
        PG_ENV=(env PGPASSFILE="$PGPASS_FILE" PGHOST="$DB_HOST" PGPORT="$DB_PORT" PGUSER="$DB_USER")
    fi
}

print_header "数据库备份恢复隔离演练 (${ENGINE})"
log_info "备份文件: ${BACKUP_FILE}"
log_info "源库: ${SOURCE_DB}"
log_info "演练库: ${DRILL_DB}"

if [[ "$ENGINE" == "mysql" ]]; then
    case "$BACKUP_FILE" in
        *.sql)
            [[ -s "$BACKUP_FILE" ]] || fail 1 "SQL 备份为空: ${BACKUP_FILE}"
            grep -qE 'CREATE TABLE|INSERT INTO' "$BACKUP_FILE" || fail 1 "SQL 备份不包含可识别的建表或数据语句: ${BACKUP_FILE}"
            ;;
        *.sql.gz)
            gzip -t "$BACKUP_FILE" 2>/dev/null || fail 1 "备份文件 gzip 校验失败: ${BACKUP_FILE}"
            [[ -s "$BACKUP_FILE" ]] || fail 1 "gzip 备份为空: ${BACKUP_FILE}"
            # awk 会读取完整流，避免 grep 提前退出在 pipefail 下触发 gzip SIGPIPE 误报。
            gzip -dc "$BACKUP_FILE" | awk '/CREATE TABLE|INSERT INTO/ { found=1 } END { exit(found ? 0 : 1) }' || \
                fail 1 "gzip 内未发现可识别的 SQL 内容: ${BACKUP_FILE}"
            ;;
        *) fail 1 "MySQL 仅支持 .sql/.sql.gz: ${BACKUP_FILE}" ;;
    esac
    log_success "MySQL 备份文件结构校验通过"

    setup_mysql_client
    if ! "${MYSQL_ARGS[@]}" -N -B -e "SELECT VERSION();" >/dev/null 2>&1; then
        log_warn "SKIP: 无法连接 MySQL 或凭据无查询权限"
        exit 3
    fi
    SOURCE_EXISTS="$("${MYSQL_ARGS[@]}" -N -B -e "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='${SOURCE_DB}';" 2>/dev/null || true)"
    [[ "$SOURCE_EXISTS" == "$SOURCE_DB" ]] || log_warn "源库 ${SOURCE_DB} 当前不可见；演练仍继续，仅用于验证备份可恢复"
    "${MYSQL_ARGS[@]}" -e "CREATE DATABASE \`${DRILL_DB}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;" || fail 3 "无法创建演练库 ${DRILL_DB}"
    DRILL_CREATED=true
else
    case "$BACKUP_FILE" in
        *.dump) ;;
        *) fail 1 "PostgreSQL 仅支持 pg_dump 自定义格式 .dump: ${BACKUP_FILE}" ;;
    esac
    [[ -s "$BACKUP_FILE" ]] || fail 1 "PostgreSQL 备份为空: ${BACKUP_FILE}"
    if [[ -n "$CONTAINER" ]]; then
        CONTAINER_DUMP="/tmp/apex-drill-check-$$-${RANDOM}.dump"
        podman cp "$BACKUP_FILE" "${CONTAINER}:${CONTAINER_DUMP}" >/dev/null || fail 3 "无法复制备份到容器"
        podman exec "$CONTAINER" pg_restore --list "$CONTAINER_DUMP" >/dev/null 2>&1 || fail 1 "pg_restore --list 无法读取备份: ${BACKUP_FILE}"
    else
        pg_restore --list "$BACKUP_FILE" >/dev/null 2>&1 || fail 1 "pg_restore --list 无法读取备份: ${BACKUP_FILE}"
    fi
    log_success "PostgreSQL 备份文件结构校验通过"

    setup_pg_client
    if ! "${PG_ENV[@]}" psql -X -q -d postgres -c "SELECT 1;" >/dev/null 2>&1; then
        log_warn "SKIP: 无法连接 PostgreSQL 或凭据无查询权限"
        exit 3
    fi
    "${PG_ENV[@]}" psql -X -q -d postgres -c "CREATE DATABASE \"${DRILL_DB}\";" || fail 3 "无法创建演练库 ${DRILL_DB}"
    DRILL_CREATED=true
fi

log_info "开始恢复备份到隔离演练库..."
RESTORE_PASSWORD_FILE="$PASSWORD_FILE"
if [[ -z "$RESTORE_PASSWORD_FILE" ]]; then
    DRILL_PASSWORD_FILE="$(mktemp "${TMPDIR:-/tmp}/apex-drill-password.XXXXXX")"
    chmod 600 "$DRILL_PASSWORD_FILE"
    printf '%s\n' "$DB_PASS" > "$DRILL_PASSWORD_FILE"
    RESTORE_PASSWORD_FILE="$DRILL_PASSWORD_FILE"
fi
RESTORE_ARGS=(--file "$BACKUP_FILE" --db "$DRILL_DB" --host "$DB_HOST" --port "$DB_PORT"
              --user "$DB_USER" --password-file "$RESTORE_PASSWORD_FILE" --no-backup --yes)
if [[ -n "$CONTAINER" ]]; then
    RESTORE_ARGS+=(--container "$CONTAINER")
fi

if [[ "$ENGINE" == "mysql" ]]; then
    if ! "${SCRIPT_DIR}/db-restore.sh" "${RESTORE_ARGS[@]}"; then
        fail 2 "恢复到演练库失败: ${DRILL_DB}"
    fi
else
    if [[ -n "$CONTAINER" ]]; then
        RESTORE_ARGS+=(--container-host "$CONTAINER_HOST" --container-port "$CONTAINER_PORT")
    fi
    if ! "${SCRIPT_DIR}/pg-restore.sh" "${RESTORE_ARGS[@]}"; then
        fail 2 "恢复到演练库失败: ${DRILL_DB}"
    fi
fi

log_info "校验演练库关键对象和 Flyway 版本..."
if [[ "$ENGINE" == "mysql" ]]; then
    TABLE_COUNT="$("${MYSQL_ARGS[@]}" -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DRILL_DB}' AND table_name IN ('sys_menu','sys_user','flyway_schema_history');" 2>/dev/null || true)"
    MENU_COUNT="$("${MYSQL_ARGS[@]}" -N -B -e "SELECT COUNT(*) FROM \`${DRILL_DB}\`.sys_menu;" 2>/dev/null || true)"
    USER_COUNT="$("${MYSQL_ARGS[@]}" -N -B -e "SELECT COUNT(*) FROM \`${DRILL_DB}\`.sys_user;" 2>/dev/null || true)"
    FLYWAY_VERSION="$("${MYSQL_ARGS[@]}" -N -B -e "SELECT version FROM \`${DRILL_DB}\`.flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;" 2>/dev/null || true)"
else
    TABLE_COUNT="$("${PG_ENV[@]}" psql -X -A -t -d "$DRILL_DB" -c "SELECT (to_regclass('public.sys_menu') IS NOT NULL)::int + (to_regclass('public.sys_user') IS NOT NULL)::int + (to_regclass('public.flyway_schema_history') IS NOT NULL)::int;" 2>/dev/null | tr -d '[:space:]' || true)"
    MENU_COUNT="$("${PG_ENV[@]}" psql -X -A -t -d "$DRILL_DB" -c "SELECT COUNT(*) FROM public.sys_menu;" 2>/dev/null | tr -d '[:space:]' || true)"
    USER_COUNT="$("${PG_ENV[@]}" psql -X -A -t -d "$DRILL_DB" -c "SELECT COUNT(*) FROM public.sys_user;" 2>/dev/null | tr -d '[:space:]' || true)"
    FLYWAY_VERSION="$("${PG_ENV[@]}" psql -X -A -t -d "$DRILL_DB" -c "SELECT version FROM public.flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;" 2>/dev/null | tr -d '[:space:]' || true)"
fi

TABLE_COUNT="$(clean_number "$TABLE_COUNT")"
MENU_COUNT="$(clean_number "$MENU_COUNT")"
USER_COUNT="$(clean_number "$USER_COUNT")"
FAILED=0
if [[ "$TABLE_COUNT" -ne 3 ]]; then
    log_failure "关键表缺失：期望 sys_menu/sys_user/flyway_schema_history 共 3 张，实际 ${TABLE_COUNT} 张"
    FAILED=1
fi
if [[ "$MENU_COUNT" -le 0 ]]; then
    log_failure "sys_menu 无数据"
    FAILED=1
fi
if [[ "$USER_COUNT" -le 0 ]]; then
    log_failure "sys_user 无数据"
    FAILED=1
fi
if [[ -z "$FLYWAY_VERSION" ]]; then
    log_failure "flyway_schema_history 无法读取最新版本"
    FAILED=1
elif [[ -n "$EXPECTED_FLYWAY_VERSION" && "$FLYWAY_VERSION" != "$EXPECTED_FLYWAY_VERSION" ]]; then
    log_failure "Flyway 最新版本不匹配：期望 ${EXPECTED_FLYWAY_VERSION}，实际 ${FLYWAY_VERSION}"
    FAILED=1
else
    log_success "Flyway 最新版本: ${FLYWAY_VERSION}；sys_menu=${MENU_COUNT}，sys_user=${USER_COUNT}"
fi

[[ $FAILED -eq 0 ]] || fail 2 "恢复演练数据校验失败"
log_success "备份恢复隔离演练通过: ${DRILL_DB}"
log_info "演练库将在脚本退出时删除；本次结果不代表生产切换或性能验收"

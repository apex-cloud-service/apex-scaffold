#!/usr/bin/env bash
# =============================================================================
# Apex Scaffold - PostgreSQL 数据库恢复脚本
# -----------------------------------------------------------------------------
# 功能:
#   1. 从 pg_dump 自定义格式（.dump）恢复 PostgreSQL 数据库
#   2. 恢复前校验目标库存在，默认先执行 pg-backup.sh 备份当前数据
#   3. 支持宿主机直连和 Podman 容器两种执行场景
#   4. 通过权限 600 的 PGPASSFILE 传递凭据，密码不出现在进程参数中
#
# 用法:
#   ./pg-restore.sh --file /data/backups/apex_system_20261110_020000.dump --yes
#   ./pg-restore.sh --yes                      # 交互选择最近的备份
#   ./pg-restore.sh --file backup.dump --no-backup --yes
#
# 环境变量:
#   PGHOST / PGPORT / PGUSER / PGPASSWORD / PGDATABASE
#   POSTGRES_CONTAINER / BACKUP_DIR
#
# 危险操作: 恢复会覆盖目标库对象；生产环境必须先完成隔离演练和审批。
# 退出码: 0=成功 1=参数或环境错误 2=恢复失败
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=../lib/common.sh
source "${SCRIPT_DIR}/../lib/common.sh"

PG_HOST="${PGHOST:-127.0.0.1}"
PG_PORT="${PGPORT:-5432}"
PG_USER="${PGUSER:-postgres}"
PG_PASS="${PGPASSWORD:-}"
DB_NAME="${PGDATABASE:-apex_system}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-}"
POSTGRES_CONTAINER_HOST="${POSTGRES_CONTAINER_HOST:-127.0.0.1}"
POSTGRES_CONTAINER_PORT="${POSTGRES_CONTAINER_PORT:-5432}"
BACKUP_DIR="${BACKUP_DIR:-${SCRIPT_DIR}/../../backups}"
RESTORE_FILE=""
PRE_BACKUP=true
ASSUME_YES=false
PASSWORD_FILE=""

usage() {
    cat <<'USAGE'
用法:
  ./pg-restore.sh [选项]

选项:
  --file <path>           指定 .dump 备份文件；省略时交互选择
  --host <host>           PostgreSQL 地址
  --port <port>           PostgreSQL 端口
  --user <user>           PostgreSQL 用户名
  --password-file <file>  从权限为 400/600 的文件读取密码（首行）
  --db <name>             目标数据库名，仅允许字母、数字和下划线
  --container <name>      Podman 容器名；设置后通过 podman exec 执行
  --container-host <host> 容器内 PostgreSQL 地址，默认 127.0.0.1
  --container-port <port> 容器内 PostgreSQL 端口，默认 5432
  --dir <path>            备份目录（交互选择和恢复前备份使用）
  --no-backup             跳过恢复前备份（仅在演练且已有可用备份时使用）
  --yes                   非交互确认覆盖目标库
  -h, --help              显示帮助

环境变量:
  PGHOST, PGPORT, PGUSER, PGPASSWORD, PGDATABASE,
  POSTGRES_CONTAINER, BACKUP_DIR
USAGE
}

fail() {
    log_error "$*"
    exit 1
}

# 恢复执行阶段失败（备份格式损坏、pg_restore 导入失败、恢复前备份失败）统一返回 2。
fail_restore() {
    log_error "$*"
    exit 2
}

require_option_value() {
    [[ $# -ge 2 && -n "${2:-}" ]] || fail "参数 $1 缺少值"
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --file) require_option_value "$@"; RESTORE_FILE="$2"; shift 2 ;;
        --host) require_option_value "$@"; PG_HOST="$2"; shift 2 ;;
        --port) require_option_value "$@"; PG_PORT="$2"; shift 2 ;;
        --user) require_option_value "$@"; PG_USER="$2"; shift 2 ;;
        --password-file) require_option_value "$@"; PASSWORD_FILE="$2"; shift 2 ;;
        --pass|--password)
            fail "为安全起见不支持 $1；请使用 PGPASSWORD 或 --password-file"
            ;;
        --db) require_option_value "$@"; DB_NAME="$2"; shift 2 ;;
        --container) require_option_value "$@"; POSTGRES_CONTAINER="$2"; shift 2 ;;
        --container-host) require_option_value "$@"; POSTGRES_CONTAINER_HOST="$2"; shift 2 ;;
        --container-port) require_option_value "$@"; POSTGRES_CONTAINER_PORT="$2"; shift 2 ;;
        --dir) require_option_value "$@"; BACKUP_DIR="$2"; shift 2 ;;
        --no-backup) PRE_BACKUP=false; shift ;;
        --yes) ASSUME_YES=true; shift ;;
        -h|--help) usage; exit 0 ;;
        *) fail "未知参数: $1" ;;
    esac
done

[[ "$DB_NAME" =~ ^[A-Za-z0-9_]+$ ]] || fail "数据库名仅允许字母、数字和下划线: ${DB_NAME}"
[[ "$PG_PORT" =~ ^[0-9]+$ ]] && (( PG_PORT >= 1 && PG_PORT <= 65535 )) || fail "端口必须为 1-65535"
if [[ -n "$POSTGRES_CONTAINER" && ! "$POSTGRES_CONTAINER" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*$ ]]; then
    fail "容器名不合法: ${POSTGRES_CONTAINER}"
fi
[[ "$POSTGRES_CONTAINER_PORT" =~ ^[0-9]+$ ]] && (( POSTGRES_CONTAINER_PORT >= 1 && POSTGRES_CONTAINER_PORT <= 65535 )) || fail "容器内 PostgreSQL 端口必须为 1-65535"

read_password_file() {
    local file="$1" mode line
    require_file "$file" "密码文件" || exit 1
    mode="$(stat -c '%a' "$file" 2>/dev/null || stat -f '%Lp' "$file" 2>/dev/null || true)"
    [[ "$mode" == "400" || "$mode" == "600" ]] || fail "密码文件权限必须为 400 或 600: ${file} (当前 ${mode:-未知})"
    IFS= read -r line < "$file" || true
    [[ -n "$line" ]] || fail "密码文件为空: ${file}"
    PG_PASS="$line"
}

if [[ -n "$PASSWORD_FILE" ]]; then
    read_password_file "$PASSWORD_FILE"
fi
if [[ -z "$PG_PASS" && -t 0 ]]; then
    printf '请输入 PostgreSQL 密码（输入不回显）: ' >&2
    read -r -s PG_PASS
    printf '\n' >&2
fi
[[ -n "$PG_PASS" ]] || fail "必须通过 PGPASSWORD 或 --password-file 提供数据库密码"
[[ "$PG_PASS" != *$'\n'* && "$PG_PASS" != *$'\r'* ]] || fail "数据库密码不得包含换行符"

if [[ -z "$POSTGRES_CONTAINER" ]]; then
    require_command pg_restore "PostgreSQL 客户端 pg_restore" || exit 1
    require_command psql "PostgreSQL 客户端 psql" || exit 1
else
    require_command podman "Podman" || exit 1
fi

if [[ -z "$RESTORE_FILE" ]]; then
    BACKUP_LIST="$(find "$BACKUP_DIR" -maxdepth 1 -type f -name '*.dump' -print 2>/dev/null | sort -r || true)"
    [[ -n "$BACKUP_LIST" ]] || fail "${BACKUP_DIR} 下无 .dump 备份文件"
    printf '可用备份文件:\n' >&2
    printf '%s\n' "$BACKUP_LIST" | nl -w2 -s') ' >&2
    printf '请选择序号 (1-%s): ' "$(printf '%s\n' "$BACKUP_LIST" | wc -l | tr -d ' ')" >&2
    read -r CHOICE || fail "未读取到选择"
    [[ "$CHOICE" =~ ^[0-9]+$ ]] || fail "无效选择"
    RESTORE_FILE="$(printf '%s\n' "$BACKUP_LIST" | sed -n "${CHOICE}p")"
    [[ -n "$RESTORE_FILE" ]] || fail "序号超出范围"
fi
[[ -f "$RESTORE_FILE" ]] || fail "备份文件不存在: ${RESTORE_FILE}"
[[ "$RESTORE_FILE" == *.dump ]] || fail "不支持的备份文件扩展名（仅 .dump）: ${RESTORE_FILE}"

PG_CONNECT_HOST="$PG_HOST"
PG_CONNECT_PORT="$PG_PORT"
if [[ -n "$POSTGRES_CONTAINER" ]]; then
    PG_CONNECT_HOST="$POSTGRES_CONTAINER_HOST"
    PG_CONNECT_PORT="$POSTGRES_CONTAINER_PORT"
fi

PGPASS_FILE=""
PASSWORD_ONLY_FILE=""
CONTAINER_PGPASS=""
cleanup() {
    local exit_code=$?
    if [[ -n "$CONTAINER_PGPASS" && -n "$POSTGRES_CONTAINER" ]]; then
        podman exec "$POSTGRES_CONTAINER" rm -f -- "$CONTAINER_PGPASS" >/dev/null 2>&1 || true
    fi
    [[ -z "$PGPASS_FILE" ]] || rm -f -- "$PGPASS_FILE"
    [[ -z "$PASSWORD_ONLY_FILE" ]] || rm -f -- "$PASSWORD_ONLY_FILE"
    exit "$exit_code"
}
trap cleanup EXIT INT TERM

escape_pgpass_value() {
    local value="$1"
    value="${value//\\/\\\\}"
    value="${value//:/\\:}"
    printf '%s' "$value"
}

PGPASS_FILE="$(mktemp "${TMPDIR:-/tmp}/apex-pgpass-restore.XXXXXX")"
chmod 600 "$PGPASS_FILE"
printf '%s:%s:%s:%s:%s\n' \
    "$(escape_pgpass_value "$PG_CONNECT_HOST")" \
    "$(escape_pgpass_value "$PG_CONNECT_PORT")" \
    "$(escape_pgpass_value "$DB_NAME")" \
    "$(escape_pgpass_value "$PG_USER")" \
    "$(escape_pgpass_value "$PG_PASS")" > "$PGPASS_FILE"

PASSWORD_ONLY_FILE="$(mktemp "${TMPDIR:-/tmp}/apex-pg-password.XXXXXX")"
chmod 600 "$PASSWORD_ONLY_FILE"
printf '%s\n' "$PG_PASS" > "$PASSWORD_ONLY_FILE"

if [[ -n "$POSTGRES_CONTAINER" ]]; then
    CONTAINER_PGPASS="/tmp/apex-pgpass-restore-$$-${RANDOM}"
    podman cp "$PGPASS_FILE" "${POSTGRES_CONTAINER}:${CONTAINER_PGPASS}" >/dev/null
    podman exec "$POSTGRES_CONTAINER" chmod 600 "$CONTAINER_PGPASS"
    PG_ENV=(podman exec -i -e PGPASSFILE="$CONTAINER_PGPASS" -e PGHOST="$PG_CONNECT_HOST"
            -e PGPORT="$PG_CONNECT_PORT" -e PGUSER="$PG_USER" -e PGDATABASE="$DB_NAME" "$POSTGRES_CONTAINER")
else
    PG_ENV=(env PGPASSFILE="$PGPASS_FILE" PGHOST="$PG_CONNECT_HOST" PGPORT="$PG_CONNECT_PORT"
            PGUSER="$PG_USER" PGDATABASE="$DB_NAME")
fi

log_warn "即将恢复数据库: ${DB_NAME}@${PG_HOST}:${PG_PORT}"
log_warn "源文件: ${RESTORE_FILE}"
if [[ "$ASSUME_YES" != "true" ]]; then
    [[ -t 0 ]] || fail "非交互恢复必须显式指定 --yes"
    printf '此操作将覆盖目标库对象，确认继续? (yes/no): ' >&2
    read -r CONFIRM || fail "未读取到确认"
    [[ "$CONFIRM" == "yes" ]] || { log_warn "已取消"; exit 0; }
fi

EXISTS="$("${PG_ENV[@]}" psql -X -A -t -c "SELECT 1 FROM pg_database WHERE datname = '${DB_NAME}'" 2>/dev/null || true)"
[[ "$EXISTS" == "1" ]] || fail "目标库 ${DB_NAME} 不存在，请先创建（createdb 或 CREATE DATABASE）"

if [[ "$PRE_BACKUP" == "true" ]]; then
    log_info "恢复前先备份当前数据..."
    if ! "${SCRIPT_DIR}/pg-backup.sh" \
        --host "$PG_HOST" --port "$PG_PORT" --user "$PG_USER" \
        --password-file "$PASSWORD_ONLY_FILE" --db "$DB_NAME" \
        ${POSTGRES_CONTAINER:+--container "$POSTGRES_CONTAINER"} \
        ${POSTGRES_CONTAINER:+--container-host "$POSTGRES_CONTAINER_HOST"} \
        ${POSTGRES_CONTAINER:+--container-port "$POSTGRES_CONTAINER_PORT"} \
        --dir "$BACKUP_DIR" --yes; then
        fail_restore "恢复前备份失败，已中止恢复: ${DB_NAME}"
    fi
fi

log_info "开始恢复: ${RESTORE_FILE}"
if [[ -n "$POSTGRES_CONTAINER" ]]; then
    CONTAINER_DUMP="/tmp/apex-restore-$$-${RANDOM}.dump"
    podman cp "$RESTORE_FILE" "${POSTGRES_CONTAINER}:${CONTAINER_DUMP}" >/dev/null
    podman exec "$POSTGRES_CONTAINER" pg_restore --list "$CONTAINER_DUMP" >/dev/null 2>&1 || {
        podman exec "$POSTGRES_CONTAINER" rm -f -- "$CONTAINER_DUMP" >/dev/null 2>&1 || true
        fail_restore "备份文件损坏或格式不支持: ${RESTORE_FILE}"
    }
    if ! podman exec -e PGPASSFILE="$CONTAINER_PGPASS" -e PGHOST="$PG_CONNECT_HOST" \
        -e PGPORT="$PG_CONNECT_PORT" -e PGUSER="$PG_USER" -e PGDATABASE="$DB_NAME" \
        "$POSTGRES_CONTAINER" pg_restore --clean --if-exists --no-owner --no-privileges --dbname="$DB_NAME" "$CONTAINER_DUMP"; then
        podman exec "$POSTGRES_CONTAINER" rm -f -- "$CONTAINER_DUMP" >/dev/null 2>&1 || true
        fail_restore "恢复失败: ${RESTORE_FILE}"
    fi
    podman exec "$POSTGRES_CONTAINER" rm -f -- "$CONTAINER_DUMP" >/dev/null 2>&1 || true
else
    PGPASSFILE="$PGPASS_FILE" pg_restore --list "$RESTORE_FILE" >/dev/null 2>&1 || fail_restore "备份文件损坏或格式不支持: ${RESTORE_FILE}"
    PGPASSFILE="$PGPASS_FILE" PGHOST="$PG_CONNECT_HOST" PGPORT="$PG_CONNECT_PORT" \
        PGUSER="$PG_USER" PGDATABASE="$DB_NAME" \
        pg_restore --clean --if-exists --no-owner --no-privileges --dbname="$DB_NAME" "$RESTORE_FILE" || fail_restore "恢复失败: ${RESTORE_FILE}"
fi

log_success "恢复完成: ${DB_NAME} (来源: $(basename "$RESTORE_FILE"))"
log_info "请立即校验关键表行数、flyway_schema_history 版本并执行应用健康检查"

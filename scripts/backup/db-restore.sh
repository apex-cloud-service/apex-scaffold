#!/usr/bin/env bash
# =============================================================================
# Apex Scaffold - MySQL 数据库恢复脚本
# -----------------------------------------------------------------------------
# 功能:
#   1. 从本地备份目录或指定 .sql/.sql.gz 文件恢复 MySQL 数据库
#   2. 恢复前校验目标库存在，默认先备份当前数据再覆盖
#   3. 支持宿主机直连与 Podman 容器两种场景
#   4. 使用权限 600 的临时 MySQL option 文件传递凭据，不暴露密码参数
#
# 用法:
#   ./db-restore.sh --file /data/backups/apex_system_20261110_020000.sql.gz --yes
#   ./db-restore.sh --yes                         # 交互选择最近的备份
#   ./db-restore.sh --file backup.sql --no-backup --yes
#
# 环境变量:
#   DB_MASTER_HOST / DB_MASTER_PORT / DB_MASTER_USERNAME / DB_MASTER_PASSWORD
#   MYSQL_DATABASE / MYSQL_CONTAINER / BACKUP_DIR
#
# 危险操作: 恢复会覆盖目标库全部数据；生产环境必须先完成恢复演练和审批。
# 退出码: 0=成功 1=参数或环境错误 2=恢复失败
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=../lib/common.sh
source "${SCRIPT_DIR}/../lib/common.sh"

DB_HOST="${DB_MASTER_HOST:-127.0.0.1}"
DB_PORT="${DB_MASTER_PORT:-3306}"
DB_USER="${DB_MASTER_USERNAME:-root}"
DB_PASS="${DB_MASTER_PASSWORD:-}"
DB_NAME="${MYSQL_DATABASE:-apex_system}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-}"
BACKUP_DIR="${BACKUP_DIR:-${SCRIPT_DIR}/../../backups}"
RESTORE_FILE=""
PRE_BACKUP=true
ASSUME_YES=false
PASSWORD_FILE=""

usage() {
    cat <<'USAGE'
用法:
  ./db-restore.sh [选项]

选项:
  --file <path>           指定 .sql 或 .sql.gz 备份文件；省略时交互选择
  --host <host>           MySQL 地址
  --port <port>           MySQL 端口
  --user <user>           MySQL 用户名
  --password-file <file>  从权限为 400/600 的文件读取密码（首行）
  --db <name>             目标数据库名，仅允许字母、数字和下划线
  --container <name>      Podman 容器名；设置后通过 podman exec 执行
  --dir <path>            备份目录（交互选择和恢复前备份使用）
  --no-backup             跳过恢复前备份（仅在演练且已有可用备份时使用）
  --yes                   非交互确认覆盖目标库
  -h, --help              显示帮助

环境变量:
  DB_MASTER_HOST, DB_MASTER_PORT, DB_MASTER_USERNAME, DB_MASTER_PASSWORD,
  MYSQL_DATABASE, MYSQL_CONTAINER, BACKUP_DIR
USAGE
}

fail() {
    log_error "$*"
    exit 1
}

# 恢复执行阶段失败（备份文件损坏、mysql 导入失败、恢复前备份失败）统一返回 2。
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
        --host) require_option_value "$@"; DB_HOST="$2"; shift 2 ;;
        --port) require_option_value "$@"; DB_PORT="$2"; shift 2 ;;
        --user) require_option_value "$@"; DB_USER="$2"; shift 2 ;;
        --password-file) require_option_value "$@"; PASSWORD_FILE="$2"; shift 2 ;;
        --pass|--password)
            fail "为安全起见不再支持 $1；请使用 DB_MASTER_PASSWORD 或 --password-file"
            ;;
        --db) require_option_value "$@"; DB_NAME="$2"; shift 2 ;;
        --container) require_option_value "$@"; MYSQL_CONTAINER="$2"; shift 2 ;;
        --dir) require_option_value "$@"; BACKUP_DIR="$2"; shift 2 ;;
        --no-backup) PRE_BACKUP=false; shift ;;
        --yes) ASSUME_YES=true; shift ;;
        -h|--help) usage; exit 0 ;;
        *) fail "未知参数: $1" ;;
    esac
done

[[ "$DB_NAME" =~ ^[A-Za-z0-9_]+$ ]] || fail "数据库名仅允许字母、数字和下划线: ${DB_NAME}"
[[ "$DB_PORT" =~ ^[0-9]+$ ]] && (( DB_PORT >= 1 && DB_PORT <= 65535 )) || fail "端口必须为 1-65535"
if [[ -n "$MYSQL_CONTAINER" && ! "$MYSQL_CONTAINER" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*$ ]]; then
    fail "容器名不合法: ${MYSQL_CONTAINER}"
fi

read_password_file() {
    local file="$1" mode line
    require_file "$file" "密码文件" || exit 1
    mode="$(stat -c '%a' "$file" 2>/dev/null || stat -f '%Lp' "$file" 2>/dev/null || true)"
    [[ "$mode" == "400" || "$mode" == "600" ]] || fail "密码文件权限必须为 400 或 600: ${file} (当前 ${mode:-未知})"
    IFS= read -r line < "$file" || true
    [[ -n "$line" ]] || fail "密码文件为空: ${file}"
    DB_PASS="$line"
}

if [[ -n "$PASSWORD_FILE" ]]; then
    read_password_file "$PASSWORD_FILE"
fi
if [[ -z "$DB_PASS" && -t 0 ]]; then
    printf '请输入数据库密码（输入不回显）: ' >&2
    read -r -s DB_PASS
    printf '\n' >&2
fi
[[ -n "$DB_PASS" ]] || fail "必须通过 DB_MASTER_PASSWORD 或 --password-file 提供数据库密码"
[[ "$DB_PASS" != *$'\n'* && "$DB_PASS" != *$'\r'* ]] || fail "数据库密码不得包含换行符"

if [[ -z "$MYSQL_CONTAINER" ]]; then
    require_command mysql "MySQL 客户端 mysql" || exit 1
    require_command gzip "gzip" || exit 1
else
    require_command podman "Podman" || exit 1
fi

if [[ -z "$RESTORE_FILE" ]]; then
    BACKUP_LIST="$(find "$BACKUP_DIR" -maxdepth 1 -type f \( -name '*.sql' -o -name '*.sql.gz' \) -print 2>/dev/null | sort -r || true)"
    [[ -n "$BACKUP_LIST" ]] || fail "${BACKUP_DIR} 下无备份文件"
    printf '可用备份文件:\n' >&2
    printf '%s\n' "$BACKUP_LIST" | nl -w2 -s') ' >&2
    printf '请选择序号 (1-%s): ' "$(printf '%s\n' "$BACKUP_LIST" | wc -l | tr -d ' ')" >&2
    read -r CHOICE || fail "未读取到选择"
    [[ "$CHOICE" =~ ^[0-9]+$ ]] || fail "无效选择"
    RESTORE_FILE="$(printf '%s\n' "$BACKUP_LIST" | sed -n "${CHOICE}p")"
    [[ -n "$RESTORE_FILE" ]] || fail "序号超出范围"
fi
[[ -f "$RESTORE_FILE" ]] || fail "备份文件不存在: ${RESTORE_FILE}"
case "$RESTORE_FILE" in
    *.sql) ;;
    *.sql.gz)
        gzip -t "$RESTORE_FILE" 2>/dev/null || fail_restore "备份文件 gzip 校验失败: ${RESTORE_FILE}"
        ;;
    *) fail "不支持的备份文件扩展名（仅 .sql/.sql.gz）: ${RESTORE_FILE}" ;;
esac

# 临时 option 文件必须在任何外部命令执行前创建，并由 trap 无条件清理。
MYSQL_CNF=""
CONTAINER_CNF=""
PASSWORD_ONLY_FILE=""
cleanup() {
    local exit_code=$?
    if [[ -n "$CONTAINER_CNF" && -n "$MYSQL_CONTAINER" ]]; then
        podman exec "$MYSQL_CONTAINER" rm -f -- "$CONTAINER_CNF" >/dev/null 2>&1 || true
    fi
    [[ -z "$PASSWORD_ONLY_FILE" ]] || rm -f -- "$PASSWORD_ONLY_FILE"
    [[ -z "$MYSQL_CNF" ]] || rm -f -- "$MYSQL_CNF"
    exit "$exit_code"
}
trap cleanup EXIT INT TERM

escape_mysql_option_value() {
    local value="$1"
    value="${value//\\/\\\\}"
    value="${value//\"/\\\"}"
    printf '%s' "$value"
}

MYSQL_CNF="$(mktemp "${TMPDIR:-/tmp}/apex-mysql-restore.XXXXXX")"
chmod 600 "$MYSQL_CNF"
{
    printf '[client]\n'
    printf 'host="%s"\n' "$(escape_mysql_option_value "$DB_HOST")"
    printf 'port=%s\n' "$DB_PORT"
    printf 'user="%s"\n' "$(escape_mysql_option_value "$DB_USER")"
    printf 'password="%s"\n' "$(escape_mysql_option_value "$DB_PASS")"
} > "$MYSQL_CNF"

PASSWORD_ONLY_FILE="$(mktemp "${TMPDIR:-/tmp}/apex-mysql-password.XXXXXX")"
chmod 600 "$PASSWORD_ONLY_FILE"
printf '%s\n' "$DB_PASS" > "$PASSWORD_ONLY_FILE"

if [[ -n "$MYSQL_CONTAINER" ]]; then
    CONTAINER_CNF="/tmp/apex-mysql-restore-$$-${RANDOM}.cnf"
    podman cp "$MYSQL_CNF" "${MYSQL_CONTAINER}:${CONTAINER_CNF}" >/dev/null
    podman exec "$MYSQL_CONTAINER" chmod 600 "$CONTAINER_CNF"
    MYSQL_ARGS=(podman exec -i "$MYSQL_CONTAINER" mysql --defaults-extra-file="$CONTAINER_CNF")
else
    MYSQL_ARGS=(mysql --defaults-extra-file="$MYSQL_CNF")
fi

log_warn "即将恢复数据库: ${DB_NAME}@${DB_HOST}:${DB_PORT}"
log_warn "源文件: ${RESTORE_FILE}"
if [[ "$ASSUME_YES" != "true" ]]; then
    [[ -t 0 ]] || fail "非交互恢复必须显式指定 --yes"
    printf '此操作将覆盖目标库全部数据，确认继续? (yes/no): ' >&2
    read -r CONFIRM || fail "未读取到确认"
    [[ "$CONFIRM" == "yes" ]] || { log_warn "已取消"; exit 0; }
fi

CHECK_SQL="SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='${DB_NAME}';"
EXISTS="$("${MYSQL_ARGS[@]}" -N -e "$CHECK_SQL" 2>/dev/null || true)"
[[ -n "$EXISTS" ]] || fail "目标库 ${DB_NAME} 不存在，请先创建（CREATE DATABASE ... CHARACTER SET utf8mb4）"

if [[ "$PRE_BACKUP" == "true" ]]; then
    log_info "恢复前先备份当前数据..."
    if ! "${SCRIPT_DIR}/db-backup.sh" \
        --host "$DB_HOST" --port "$DB_PORT" --user "$DB_USER" \
        --password-file "$PASSWORD_ONLY_FILE" --db "$DB_NAME" \
        ${MYSQL_CONTAINER:+--container "$MYSQL_CONTAINER"} --dir "$BACKUP_DIR" --yes; then
        fail_restore "恢复前备份失败，已中止恢复: ${DB_NAME}"
    fi
fi

log_info "开始恢复: ${RESTORE_FILE}"
if [[ "$RESTORE_FILE" == *.gz ]]; then
    DECOMPRESS=(gzip -dc)
else
    DECOMPRESS=(cat)
fi
if ! "${DECOMPRESS[@]}" "$RESTORE_FILE" | "${MYSQL_ARGS[@]}" --default-character-set=utf8mb4 "$DB_NAME"; then
    fail_restore "恢复失败: ${RESTORE_FILE}"
fi

log_success "恢复完成: ${DB_NAME} (来源: $(basename "$RESTORE_FILE"))"
log_info "请立即校验关键表行数、flyway_schema_history 版本并执行应用健康检查"

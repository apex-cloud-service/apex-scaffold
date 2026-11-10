#!/usr/bin/env bash
# =============================================================================
# Apex Scaffold - MySQL 数据库备份脚本
# -----------------------------------------------------------------------------
# 功能:
#   1. 使用 mysqldump 对 MySQL 执行一致性全量备份（事务快照、例行程序、触发器）
#   2. 支持宿主机直连和 Podman 容器两种执行场景
#   3. 备份文件先写临时文件并校验 gzip 完整性，成功后再原子改名
#   4. 按份数轮转清理，可选上传 MinIO/S3 兼容对象存储
#
# 安全:
#   - 密码只从 DB_MASTER_PASSWORD 或权限受限的 --password-file 读取
#   - 密码通过权限为 600 的临时 MySQL option 文件传递，不出现在进程参数中
#   - 容器模式会复制 option 文件到容器内，并在退出时清理
#
# 用法:
#   ./db-backup.sh --yes
#   ./db-backup.sh --container apex-infra-mysql --keep 14 --yes
#   ./db-backup.sh --db apex_system --upload --mc-alias minio --mc-bucket apex-backups --yes
#
# 环境变量（均可用 -- 参数覆盖）:
#   DB_MASTER_HOST / DB_MASTER_PORT / DB_MASTER_USERNAME / DB_MASTER_PASSWORD
#   MYSQL_DATABASE / MYSQL_CONTAINER / BACKUP_DIR / BACKUP_KEEP
#   MC_ALIAS / MC_BUCKET
#
# 退出码: 0=成功 1=参数或环境错误 2=备份失败 3=上传失败
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
BACKUP_KEEP="${BACKUP_KEEP:-7}"
UPLOAD_ENABLED=false
MC_ALIAS="${MC_ALIAS:-}"
MC_BUCKET="${MC_BUCKET:-}"
PASSWORD_FILE=""
ASSUME_YES=false

usage() {
    cat <<'USAGE'
用法:
  ./db-backup.sh [选项]

选项:
  --host <host>           MySQL 地址
  --port <port>           MySQL 端口
  --user <user>           MySQL 用户名
  --password-file <file>  从权限为 400/600 的文件读取密码（首行）
  --db <name>             数据库名，仅允许字母、数字和下划线
  --container <name>      Podman 容器名；设置后通过 podman exec 执行
  --dir <path>            备份目录
  --keep <count>          保留份数，默认 7
  --upload                上传到 MinIO/S3（需 --mc-alias 与 --mc-bucket）
  --mc-alias <alias>      mc 配置别名
  --mc-bucket <bucket>    mc 目标桶
  --yes                   非交互确认轮转删除并允许自动化执行
  -h, --help              显示帮助

环境变量:
  DB_MASTER_HOST, DB_MASTER_PORT, DB_MASTER_USERNAME, DB_MASTER_PASSWORD,
  MYSQL_DATABASE, MYSQL_CONTAINER, BACKUP_DIR, BACKUP_KEEP, MC_ALIAS, MC_BUCKET
USAGE
}

fail() {
    log_error "$*"
    exit 1
}

# 备份执行阶段失败（mysqldump、gzip 完整性校验等）统一返回 2，
# 与参数/环境错误（1）和对象存储上传失败（3）区分，便于 cron 与告警按退出码分类。
fail_backup() {
    log_error "$*"
    exit 2
}

require_option_value() {
    [[ $# -ge 2 && -n "${2:-}" ]] || fail "参数 $1 缺少值"
}

while [[ $# -gt 0 ]]; do
    case "$1" in
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
        --keep) require_option_value "$@"; BACKUP_KEEP="$2"; shift 2 ;;
        --upload) UPLOAD_ENABLED=true; shift ;;
        --mc-alias) require_option_value "$@"; MC_ALIAS="$2"; shift 2 ;;
        --mc-bucket) require_option_value "$@"; MC_BUCKET="$2"; shift 2 ;;
        --yes) ASSUME_YES=true; shift ;;
        -h|--help) usage; exit 0 ;;
        *) fail "未知参数: $1" ;;
    esac
done

[[ "$DB_NAME" =~ ^[A-Za-z0-9_]+$ ]] || fail "数据库名仅允许字母、数字和下划线: ${DB_NAME}"
[[ "$DB_PORT" =~ ^[0-9]+$ ]] && (( DB_PORT >= 1 && DB_PORT <= 65535 )) || fail "端口必须为 1-65535"
[[ "$BACKUP_KEEP" =~ ^[0-9]+$ ]] && (( BACKUP_KEEP > 0 )) || fail "BACKUP_KEEP 必须为正整数"
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
    require_command mysqldump "MySQL 客户端 mysqldump" || exit 1
    require_command gzip "gzip" || exit 1
else
    require_command podman "Podman" || exit 1
fi
mkdir -p -- "$BACKUP_DIR"

TMP_OUTPUT=""
MYSQL_CNF=""
CONTAINER_CNF=""
cleanup() {
    local exit_code=$?
    if [[ -n "$CONTAINER_CNF" && -n "$MYSQL_CONTAINER" ]]; then
        podman exec "$MYSQL_CONTAINER" rm -f -- "$CONTAINER_CNF" >/dev/null 2>&1 || true
    fi
    [[ -z "$TMP_OUTPUT" ]] || rm -f -- "$TMP_OUTPUT"
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

MYSQL_CNF="$(mktemp "${TMPDIR:-/tmp}/apex-mysql-option.XXXXXX")"
chmod 600 "$MYSQL_CNF"
{
    printf '[client]\n'
    printf 'host="%s"\n' "$(escape_mysql_option_value "$DB_HOST")"
    printf 'port=%s\n' "$DB_PORT"
    printf 'user="%s"\n' "$(escape_mysql_option_value "$DB_USER")"
    printf 'password="%s"\n' "$(escape_mysql_option_value "$DB_PASS")"
} > "$MYSQL_CNF"

TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_FILE="${BACKUP_DIR}/${DB_NAME}_${TIMESTAMP}.sql"
COMPRESSED="${OUT_FILE}.gz"
TMP_OUTPUT="${COMPRESSED}.partial.$$"
DUMP_OPTS=(--single-transaction --routines --triggers --set-gtid-purged=OFF
           --default-character-set=utf8mb4)

log_info "开始备份: ${DB_NAME}@${DB_HOST}:${DB_PORT} -> ${COMPRESSED}"

if [[ -n "$MYSQL_CONTAINER" ]]; then
    CONTAINER_CNF="/tmp/apex-mysql-option-$$-${RANDOM}.cnf"
    podman cp "$MYSQL_CNF" "${MYSQL_CONTAINER}:${CONTAINER_CNF}" >/dev/null
    podman exec "$MYSQL_CONTAINER" chmod 600 "$CONTAINER_CNF"
    if ! podman exec "$MYSQL_CONTAINER" mysqldump \
        --defaults-extra-file="$CONTAINER_CNF" "${DUMP_OPTS[@]}" "$DB_NAME" | gzip > "$TMP_OUTPUT"; then
        fail_backup "mysqldump 备份失败: ${DB_NAME}"
    fi
else
    if ! mysqldump --defaults-extra-file="$MYSQL_CNF" "${DUMP_OPTS[@]}" "$DB_NAME" | gzip > "$TMP_OUTPUT"; then
        fail_backup "mysqldump 备份失败: ${DB_NAME}"
    fi
fi

if ! gzip -t "$TMP_OUTPUT" 2>/dev/null; then
    fail_backup "备份文件损坏（gzip 校验失败）: ${TMP_OUTPUT}"
fi
mv -f -- "$TMP_OUTPUT" "$COMPRESSED"
TMP_OUTPUT=""
SIZE="$(du -h "$COMPRESSED" | cut -f1)"
log_success "备份完成: ${COMPRESSED} (${SIZE})"

# 轮转删除属于破坏性操作：非交互环境必须显式 --yes，交互环境逐次确认。
EXPIRED_FILES=()
while IFS= read -r file; do
    [[ -n "$file" ]] && EXPIRED_FILES+=("$file")
done < <(find "$BACKUP_DIR" -maxdepth 1 -type f -name "${DB_NAME}_*.sql.gz" -print 2>/dev/null | sort -r | tail -n +$((BACKUP_KEEP + 1)))

if [[ ${#EXPIRED_FILES[@]} -gt 0 ]]; then
    if [[ "$ASSUME_YES" != "true" ]]; then
        if [[ -t 0 ]]; then
            log_warn "将删除 ${#EXPIRED_FILES[@]} 份过期备份；使用 --yes 可跳过确认"
            for file in "${EXPIRED_FILES[@]}"; do printf '  - %s\n' "$file" >&2; done
            printf '确认删除? (yes/no): ' >&2
            read -r confirm
            [[ "$confirm" == "yes" ]] || { log_warn "已跳过轮转清理"; EXPIRED_FILES=(); }
        else
            log_warn "检测到过期备份，但非交互环境未指定 --yes，已跳过轮转清理"
            EXPIRED_FILES=()
        fi
    fi
    for file in "${EXPIRED_FILES[@]}"; do
        rm -f -- "$file"
        log_info "已删除过期备份: ${file}"
    done
fi

if [[ "$UPLOAD_ENABLED" == "true" ]]; then
    [[ -n "$MC_ALIAS" && -n "$MC_BUCKET" ]] || fail "--upload 需同时提供 --mc-alias 与 --mc-bucket"
    require_command mc "MinIO 客户端 mc" || exit 3
    if mc cp "$COMPRESSED" "${MC_ALIAS}/${MC_BUCKET}/" >/dev/null 2>&1; then
        log_success "已上传对象存储: ${MC_ALIAS}/${MC_BUCKET}/$(basename "$COMPRESSED")"
    else
        log_error "上传失败（本地备份保留）: ${COMPRESSED}"
        exit 3
    fi
fi

log_success "备份流程结束"

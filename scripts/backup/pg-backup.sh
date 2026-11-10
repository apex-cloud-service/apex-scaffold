#!/usr/bin/env bash
# =============================================================================
# Apex Scaffold - PostgreSQL 数据库备份脚本
# -----------------------------------------------------------------------------
# 功能:
#   1. 使用 pg_dump 自定义格式（-Fc）执行一致性全量备份
#   2. 支持宿主机直连和 Podman 容器两种执行场景
#   3. 通过权限 600 的 PGPASSFILE 传递凭据，密码不出现在进程参数中
#   4. 备份先写临时文件，校验 pg_restore --list 后原子改名
#   5. 按份数轮转清理，可选上传 MinIO/S3 兼容对象存储
#
# 用法:
#   ./pg-backup.sh --yes
#   ./pg-backup.sh --container apex-infra-postgres --keep 14 --yes
#   ./pg-backup.sh --db apex_system --upload --mc-alias minio --mc-bucket apex-backups --yes
#
# 环境变量:
#   PGHOST / PGPORT / PGUSER / PGPASSWORD / PGDATABASE
#   POSTGRES_CONTAINER / BACKUP_DIR / BACKUP_KEEP / MC_ALIAS / MC_BUCKET
#
# 退出码: 0=成功 1=参数或环境错误 2=备份失败 3=上传失败
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
BACKUP_KEEP="${BACKUP_KEEP:-7}"
UPLOAD_ENABLED=false
MC_ALIAS="${MC_ALIAS:-}"
MC_BUCKET="${MC_BUCKET:-}"
PASSWORD_FILE=""
ASSUME_YES=false

usage() {
    cat <<'USAGE'
用法:
  ./pg-backup.sh [选项]

选项:
  --host <host>           PostgreSQL 地址
  --port <port>           PostgreSQL 端口
  --user <user>           PostgreSQL 用户名
  --password-file <file>  从权限为 400/600 的文件读取密码（首行）
  --db <name>             数据库名，仅允许字母、数字和下划线
  --container <name>      Podman 容器名；设置后通过 podman exec 执行
  --container-host <host> 容器内 PostgreSQL 地址，默认 127.0.0.1
  --container-port <port> 容器内 PostgreSQL 端口，默认 5432
  --dir <path>            备份目录
  --keep <count>          保留份数，默认 7
  --upload                上传到 MinIO/S3
  --mc-alias <alias>      mc 配置别名
  --mc-bucket <bucket>    mc 目标桶
  --yes                   非交互确认轮转删除并允许自动化执行
  -h, --help              显示帮助

环境变量:
  PGHOST, PGPORT, PGUSER, PGPASSWORD, PGDATABASE,
  POSTGRES_CONTAINER, BACKUP_DIR, BACKUP_KEEP, MC_ALIAS, MC_BUCKET
USAGE
}

fail() {
    log_error "$*"
    exit 1
}

# 备份执行阶段失败（pg_dump、pg_restore --list 完整性校验）统一返回 2。
fail_backup() {
    log_error "$*"
    exit 2
}

require_option_value() {
    [[ $# -ge 2 && -n "${2:-}" ]] || fail "参数 $1 缺少值"
}

while [[ $# -gt 0 ]]; do
    case "$1" in
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
[[ "$PG_PORT" =~ ^[0-9]+$ ]] && (( PG_PORT >= 1 && PG_PORT <= 65535 )) || fail "端口必须为 1-65535"
[[ "$BACKUP_KEEP" =~ ^[0-9]+$ ]] && (( BACKUP_KEEP > 0 )) || fail "BACKUP_KEEP 必须为正整数"
if [[ -n "$POSTGRES_CONTAINER" && ! "$POSTGRES_CONTAINER" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*$ ]]; then
    fail "容器名不合法: ${POSTGRES_CONTAINER}"
fi
[[ "$POSTGRES_CONTAINER_PORT" =~ ^[0-9]+$ ]] && (( POSTGRES_CONTAINER_PORT >= 1 && POSTGRES_CONTAINER_PORT <= 65535 )) || fail "容器内 PostgreSQL 端口必须为 1-65535"

PG_CONNECT_HOST="$PG_HOST"
PG_CONNECT_PORT="$PG_PORT"
if [[ -n "$POSTGRES_CONTAINER" ]]; then
    PG_CONNECT_HOST="$POSTGRES_CONTAINER_HOST"
    PG_CONNECT_PORT="$POSTGRES_CONTAINER_PORT"
fi

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
    require_command pg_dump "PostgreSQL 客户端 pg_dump" || exit 1
    require_command pg_restore "PostgreSQL 客户端 pg_restore" || exit 1
else
    require_command podman "Podman" || exit 1
fi
mkdir -p -- "$BACKUP_DIR"

TMP_OUTPUT=""
PGPASS_FILE=""
CONTAINER_PGPASS=""
cleanup() {
    local exit_code=$?
    if [[ -n "$CONTAINER_PGPASS" && -n "$POSTGRES_CONTAINER" ]]; then
        podman exec "$POSTGRES_CONTAINER" rm -f -- "$CONTAINER_PGPASS" >/dev/null 2>&1 || true
    fi
    [[ -z "$TMP_OUTPUT" ]] || rm -f -- "$TMP_OUTPUT"
    [[ -z "$PGPASS_FILE" ]] || rm -f -- "$PGPASS_FILE"
    exit "$exit_code"
}
trap cleanup EXIT INT TERM

escape_pgpass_value() {
    local value="$1"
    value="${value//\\/\\\\}"
    value="${value//:/\\:}"
    printf '%s' "$value"
}

PGPASS_FILE="$(mktemp "${TMPDIR:-/tmp}/apex-pgpass.XXXXXX")"
chmod 600 "$PGPASS_FILE"
printf '%s:%s:%s:%s:%s\n' \
    "$(escape_pgpass_value "$PG_CONNECT_HOST")" \
    "$(escape_pgpass_value "$PG_CONNECT_PORT")" \
    "$(escape_pgpass_value "$DB_NAME")" \
    "$(escape_pgpass_value "$PG_USER")" \
    "$(escape_pgpass_value "$PG_PASS")" > "$PGPASS_FILE"

TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_FILE="${BACKUP_DIR}/${DB_NAME}_${TIMESTAMP}.dump"
TMP_OUTPUT="${OUT_FILE}.partial.$$"

log_info "开始备份: ${DB_NAME}@${PG_HOST}:${PG_PORT} -> ${OUT_FILE}"

if [[ -n "$POSTGRES_CONTAINER" ]]; then
    CONTAINER_PGPASS="/tmp/apex-pgpass-$$-${RANDOM}"
    podman cp "$PGPASS_FILE" "${POSTGRES_CONTAINER}:${CONTAINER_PGPASS}" >/dev/null
    podman exec "$POSTGRES_CONTAINER" chmod 600 "$CONTAINER_PGPASS"
    if ! podman exec -e PGPASSFILE="$CONTAINER_PGPASS" -e PGHOST="$PG_CONNECT_HOST" \
        -e PGPORT="$PG_CONNECT_PORT" -e PGUSER="$PG_USER" -e PGDATABASE="$DB_NAME" \
        "$POSTGRES_CONTAINER" pg_dump -Fc --no-owner --no-privileges "$DB_NAME" > "$TMP_OUTPUT"; then
        fail_backup "pg_dump 备份失败: ${DB_NAME}"
    fi
else
    if ! PGPASSFILE="$PGPASS_FILE" PGHOST="$PG_HOST" PGPORT="$PG_PORT" PGUSER="$PG_USER" \
        PGDATABASE="$DB_NAME" pg_dump -Fc --no-owner --no-privileges "$DB_NAME" > "$TMP_OUTPUT"; then
        fail_backup "pg_dump 备份失败: ${DB_NAME}"
    fi
fi

[[ -s "$TMP_OUTPUT" ]] || fail_backup "pg_dump 输出为空: ${TMP_OUTPUT}"
if [[ -z "$POSTGRES_CONTAINER" ]]; then
    PGPASSFILE="$PGPASS_FILE" pg_restore --list "$TMP_OUTPUT" >/dev/null 2>&1 || fail_backup "备份文件校验失败: ${TMP_OUTPUT}"
else
    podman cp "$TMP_OUTPUT" "${POSTGRES_CONTAINER}:/tmp/apex-backup-check-$$.dump" >/dev/null
    podman exec "$POSTGRES_CONTAINER" pg_restore --list "/tmp/apex-backup-check-$$.dump" >/dev/null 2>&1 || {
        podman exec "$POSTGRES_CONTAINER" rm -f "/tmp/apex-backup-check-$$.dump" >/dev/null 2>&1 || true
        fail_backup "备份文件校验失败: ${TMP_OUTPUT}"
    }
    podman exec "$POSTGRES_CONTAINER" rm -f "/tmp/apex-backup-check-$$.dump" >/dev/null 2>&1 || true
fi
mv -f -- "$TMP_OUTPUT" "$OUT_FILE"
TMP_OUTPUT=""
SIZE="$(du -h "$OUT_FILE" | cut -f1)"
log_success "备份完成: ${OUT_FILE} (${SIZE})"

EXPIRED_FILES=()
while IFS= read -r file; do
    [[ -n "$file" ]] && EXPIRED_FILES+=("$file")
done < <(find "$BACKUP_DIR" -maxdepth 1 -type f -name "${DB_NAME}_*.dump" -print 2>/dev/null | sort -r | tail -n +$((BACKUP_KEEP + 1)))

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
    if mc cp "$OUT_FILE" "${MC_ALIAS}/${MC_BUCKET}/" >/dev/null 2>&1; then
        log_success "已上传对象存储: ${MC_ALIAS}/${MC_BUCKET}/$(basename "$OUT_FILE")"
    else
        log_error "上传失败（本地备份保留）: ${OUT_FILE}"
        exit 3
    fi
fi

log_success "备份流程结束"

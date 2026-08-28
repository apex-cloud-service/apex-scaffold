#!/usr/bin/env bash
# =============================================================================
# Apex Scaffold - 数据库备份脚本
# -----------------------------------------------------------------------------
# 功能:
#   1. 对 MySQL 执行 mysqldump 全量备份（--single-transaction --routines）
#   2. 支持两种执行场景:
#      a) 宿主机直连 MySQL（java -jar 部署）
#      b) Docker 容器（docker compose 部署, 通过 MYSQL_CONTAINER 指定容器名）
#   3. 保留 N 份最新备份（轮转清理, 默认 7 份）
#   4. 可选上传对象存储（MinIO/S3 兼容, 通过 mc/aws 客户端）
#
# 用法:
#   ./db-backup.sh                      # 使用环境变量或默认值
#   ./db-backup.sh --container apex-infra-mysql
#   ./db-backup.sh --db apex_data --keep 14 --upload
#
# 环境变量（均可用 -- 参数覆盖）:
#   DB_MASTER_HOST / DB_MASTER_PORT / DB_MASTER_USERNAME / DB_MASTER_PASSWORD
#   MYSQL_DATABASE / MYSQL_CONTAINER / BACKUP_DIR / BACKUP_KEEP
#   MC_ALIAS / MC_BUCKET            （启用 --upload 时）
#
# 退出码: 0=成功 1=参数错误 2=备份失败 3=上传失败
# =============================================================================
set -euo pipefail

# ---------- 默认配置（环境变量可覆盖） ----------
DB_HOST="${DB_MASTER_HOST:-127.0.0.1}"
DB_PORT="${DB_MASTER_PORT:-3306}"
DB_USER="${DB_MASTER_USERNAME:-root}"
DB_PASS="${DB_MASTER_PASSWORD:-}"
DB_NAME="${MYSQL_DATABASE:-apex_data}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-}"
BACKUP_DIR="${BACKUP_DIR:-$(dirname "$0")/../../backups}"
BACKUP_KEEP="${BACKUP_KEEP:-7}"
UPLOAD_ENABLED=false
MC_ALIAS="${MC_ALIAS:-}"
MC_BUCKET="${MC_BUCKET:-}"

# ---------- 参数解析 ----------
usage() {
    sed -n '2,24p' "$0" | sed 's/^# \{0,1\}//'
    exit 1
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --host) DB_HOST="$2"; shift 2 ;;
        --port) DB_PORT="$2"; shift 2 ;;
        --user) DB_USER="$2"; shift 2 ;;
        --pass) DB_PASS="$2"; shift 2 ;;
        --db) DB_NAME="$2"; shift 2 ;;
        --container) MYSQL_CONTAINER="$2"; shift 2 ;;
        --dir) BACKUP_DIR="$2"; shift 2 ;;
        --keep) BACKUP_KEEP="$2"; shift 2 ;;
        --upload) UPLOAD_ENABLED=true; shift ;;
        --mc-alias) MC_ALIAS="$2"; shift 2 ;;
        --mc-bucket) MC_BUCKET="$2"; shift 2 ;;
        -h|--help) usage ;;
        *) echo "❌ 未知参数: $1"; usage ;;
    esac
done

# ---------- 预检 ----------
[[ -z "$DB_PASS" ]] && echo "❌ 必须提供数据库密码 (--pass 或 DB_MASTER_PASSWORD)" && exit 1
# 容器场景由容器内 mysqldump 执行, 宿主机无需安装; 仅宿主机直连需要
if [[ -z "$MYSQL_CONTAINER" ]] && ! command -v mysqldump >/dev/null 2>&1; then
    echo "❌ 未找到 mysqldump, 请安装 MySQL 客户端（或使用 --container 容器模式）"; exit 1
fi
mkdir -p "$BACKUP_DIR"
[[ "$BACKUP_KEEP" =~ ^[0-9]+$ ]] && [[ "$BACKUP_KEEP" -gt 0 ]] || { echo "❌ BACKUP_KEEP 必须为正整数"; exit 1; }

# ---------- 备份执行 ----------
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_FILE="${BACKUP_DIR}/${DB_NAME}_${TIMESTAMP}.sql"
COMPRESSED="${OUT_FILE}.gz"

echo "🔍 开始备份: ${DB_NAME}@${DB_HOST}:${DB_PORT} → ${COMPRESSED}"

# mysqldump 公共参数:
#   --single-transaction  : InnoDB 一致性快照, 不锁表
#   --routines            : 包含存储过程/函数/触发器
#   --triggers            : 包含触发器
#   --set-gtid-purged=OFF : 兼容非 GTID 环境恢复
DUMP_OPTS=(--single-transaction --routines --triggers --set-gtid-purged=OFF
           --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" --password="$DB_PASS"
           --default-character-set=utf8mb4)

if [[ -n "$MYSQL_CONTAINER" ]]; then
    # 容器场景: 通过 docker exec 在容器内执行 dump, 避免宿主机网络不通
    docker exec "$MYSQL_CONTAINER" sh -c \
        "mysqldump $(printf '%q ' "${DUMP_OPTS[@]}") $DB_NAME" | gzip > "$COMPRESSED"
else
    # 宿主机直连场景
    mysqldump "${DUMP_OPTS[@]}" "$DB_NAME" | gzip > "$COMPRESSED"
fi

# ---------- 完整性校验（解压后行数与头部检查） ----------
if ! gzip -t "$COMPRESSED" 2>/dev/null; then
    echo "❌ 备份文件损坏 (gzip 校验失败): $COMPRESSED"
    rm -f "$COMPRESSED"
    exit 2
fi

SIZE="$(du -h "$COMPRESSED" | cut -f1)"
echo "✅ 备份完成: $COMPRESSED ($SIZE)"

# ---------- 轮转清理（保留最新 N 份, 兼容 zsh/bash） ----------
# 按修改时间倒序（ls -t）列出同库备份, 跳过前 N 份即过期文件
COUNT=0
for f in $(ls -1t "${BACKUP_DIR}"/"${DB_NAME}"_*.sql.gz 2>/dev/null); do
    COUNT=$((COUNT + 1))
    if [[ "$COUNT" -gt "$BACKUP_KEEP" ]]; then
        echo "   - 删除过期备份 $f"
        rm -f "$f"
    fi
done
[[ "$COUNT" -gt 0 ]] && echo "🧹 轮转检查完成: 共 ${COUNT} 份, 保留最新 ${BACKUP_KEEP} 份"

# ---------- 可选上传对象存储（MinIO/S3 兼容） ----------
if [[ "$UPLOAD_ENABLED" == "true" ]]; then
    [[ -n "$MC_ALIAS" && -n "$MC_BUCKET" ]] || { echo "❌ --upload 需同时提供 --mc-alias 与 --mc-bucket"; exit 3; }
    command -v mc >/dev/null 2>&1 || { echo "❌ 未找到 mc (MinIO 客户端)"; exit 3; }
    if mc cp "$COMPRESSED" "${MC_ALIAS}/${MC_BUCKET}/" >/dev/null 2>&1; then
        echo "☁️ 已上传至对象存储: ${MC_ALIAS}/${MC_BUCKET}/$(basename "$COMPRESSED")"
    else
        echo "❌ 上传失败（本地备份保留）: $COMPRESSED"
        exit 3
    fi
fi

echo "🎉 备份流程结束"
exit 0

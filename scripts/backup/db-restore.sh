#!/usr/bin/env bash
# =============================================================================
# Apex Scaffold - 数据库恢复脚本
# -----------------------------------------------------------------------------
# 功能:
#   1. 从本地备份目录（或指定文件）恢复 MySQL 数据库
#   2. 支持 .sql / .sql.gz 两种格式（自动识别解压）
#   3. 恢复前强制校验目标库是否存在（防止误建库）; 默认先备份当前数据再覆盖
#   4. 支持宿主机直连 / Podman 容器两种场景（与 db-backup.sh 保持一致）
#
# 用法:
#   ./db-restore.sh                          # 交互选择最近的备份文件
#   ./db-restore.sh --file backups/apex_system_20260828_120000.sql.gz
#   ./db-restore.sh --file xxx.sql --no-backup   # 跳过恢复前备份
#
# 环境变量（均可用 -- 参数覆盖）:
#   DB_MASTER_HOST / DB_MASTER_PORT / DB_MASTER_USERNAME / DB_MASTER_PASSWORD
#   MYSQL_DATABASE / MYSQL_CONTAINER / BACKUP_DIR
#
# ⚠️ 危险操作: 恢复会覆盖目标库全部数据, 生产环境请务必先演练（见 README）
# =============================================================================
set -euo pipefail

# ---------- 默认配置（环境变量可覆盖） ----------
DB_HOST="${DB_MASTER_HOST:-127.0.0.1}"
DB_PORT="${DB_MASTER_PORT:-3306}"
DB_USER="${DB_MASTER_USERNAME:-root}"
DB_PASS="${DB_MASTER_PASSWORD:-}"
DB_NAME="${MYSQL_DATABASE:-apex_system}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-}"
BACKUP_DIR="${BACKUP_DIR:-$(dirname "$0")/../../backups}"
RESTORE_FILE=""
PRE_BACKUP=true

# ---------- 参数解析 ----------
usage() {
    sed -n '2,22p' "$0" | sed 's/^# \{0,1\}//'
    exit 1
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --file) RESTORE_FILE="$2"; shift 2 ;;
        --host) DB_HOST="$2"; shift 2 ;;
        --port) DB_PORT="$2"; shift 2 ;;
        --user) DB_USER="$2"; shift 2 ;;
        --pass) DB_PASS="$2"; shift 2 ;;
        --db) DB_NAME="$2"; shift 2 ;;
        --container) MYSQL_CONTAINER="$2"; shift 2 ;;
        --dir) BACKUP_DIR="$2"; shift 2 ;;
        --no-backup) PRE_BACKUP=false; shift ;;
        -h|--help) usage ;;
        *) echo "❌ 未知参数: $1"; usage ;;
    esac
done

[[ -z "$DB_PASS" ]] && echo "❌ 必须提供数据库密码 (--pass 或 DB_MASTER_PASSWORD)" && exit 1
command -v mysql >/dev/null 2>&1 || { echo "❌ 未找到 mysql 客户端"; exit 1; }

# ---------- 确定备份文件 ----------
if [[ -z "$RESTORE_FILE" ]]; then
    # 列出全部备份（最近优先）; ls 失败(无文件)时列表为空
    BACKUP_LIST=$(ls -1t "$BACKUP_DIR"/*.sql "$BACKUP_DIR"/*.sql.gz 2>/dev/null || true)
    [[ -z "$BACKUP_LIST" ]] && { echo "❌ ${BACKUP_DIR} 下无备份文件"; exit 1; }
    echo "📂 可用备份文件:"
    echo "$BACKUP_LIST" | nl -w2 -s') '
    printf "请选择序号 (1-%s): " "$(echo "$BACKUP_LIST" | wc -l | tr -d ' ')"
    read -r CHOICE
    [[ "$CHOICE" =~ ^[0-9]+$ ]] || { echo "❌ 无效选择"; exit 1; }
    # 按序号选取（sed 行号定位, 兼容 zsh/bash 数组差异）
    RESTORE_FILE=$(echo "$BACKUP_LIST" | sed -n "${CHOICE}p")
    [[ -n "$RESTORE_FILE" ]] || { echo "❌ 序号超出范围"; exit 1; }
fi

[[ -f "$RESTORE_FILE" ]] || { echo "❌ 备份文件不存在: $RESTORE_FILE"; exit 1; }

# ---------- 危险操作确认 ----------
echo "⚠️  即将恢复数据库: ${DB_NAME}@${DB_HOST}:${DB_PORT}"
echo "    源文件: $RESTORE_FILE"
read -r -p "此操作将覆盖目标库全部数据, 确认继续? (yes/no): " CONFIRM
[[ "$CONFIRM" == "yes" ]] || { echo "已取消"; exit 0; }

# ---------- 恢复前备份（默认开启） ----------
if [[ "$PRE_BACKUP" == "true" ]]; then
    echo "🛡️  恢复前先备份当前数据..."
    "$(dirname "$0")/db-backup.sh" --host "$DB_HOST" --port "$DB_PORT" \
        --user "$DB_USER" --pass "$DB_PASS" --db "$DB_NAME" \
        ${MYSQL_CONTAINER:+--container "$MYSQL_CONTAINER"} --dir "$BACKUP_DIR"
fi

# ---------- 目标库存在性校验 ----------
CHECK_SQL="SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='$DB_NAME';"
if [[ -n "$MYSQL_CONTAINER" ]]; then
    EXISTS=$(podman exec "$MYSQL_CONTAINER" mysql -uroot -p"$DB_PASS" -N -e "$CHECK_SQL" 2>/dev/null || true)
else
    EXISTS=$(mysql --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" --password="$DB_PASS" -N -e "$CHECK_SQL" 2>/dev/null || true)
fi
if [[ -z "$EXISTS" ]]; then
    echo "❌ 目标库 ${DB_NAME} 不存在, 请先创建（CREATE DATABASE ... CHARACTER SET utf8mb4）"
    exit 1
fi

# ---------- 执行恢复 ----------
echo "🔄 开始恢复 (${RESTORE_FILE}) ..."
case "$RESTORE_FILE" in
    *.gz) DECOMPRESS=(gzip -dc) ;;
    *)    DECOMPRESS=(cat) ;;
esac

if [[ -n "$MYSQL_CONTAINER" ]]; then
    "${DECOMPRESS[@]}" "$RESTORE_FILE" | podman exec -i "$MYSQL_CONTAINER" \
        mysql -uroot -p"$DB_PASS" --default-character-set=utf8mb4 "$DB_NAME"
else
    "${DECOMPRESS[@]}" "$RESTORE_FILE" | mysql --host="$DB_HOST" --port="$DB_PORT" \
        --user="$DB_USER" --password="$DB_PASS" --default-character-set=utf8mb4 "$DB_NAME"
fi

echo "✅ 恢复完成: ${DB_NAME} (来源: $(basename "$RESTORE_FILE"))"
echo "💡 建议立即验证: 查询关键表行数 / 执行应用冒烟（/api/v1/health）"
exit 0

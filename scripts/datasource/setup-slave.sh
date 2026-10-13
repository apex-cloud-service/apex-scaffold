#!/usr/bin/env zsh
# ============================================================
# Apex Scaffold 数据源动态切换 - 从库一键初始化脚本
#
# 功能: 创建本地演示用从库 apex_system_slave + 主/从路由探针表
#       （routing_probe，标记值 MASTER/SLAVE），用于验证
#       @DataSource("slave") 自定义注解的数据源动态切换。
#
# 用法:
#   ./scripts/datasource/setup-slave.sh
#   DB_HOST=127.0.0.1 DB_PORT=3306 DB_USER=root DB_PASSWORD=apex-root \
#       ./scripts/datasource/setup-slave.sh
#
# 环境变量:
#   DB_HOST       MySQL 地址（默认 127.0.0.1）
#   DB_PORT       MySQL 端口（默认 3306）
#   DB_USER       MySQL 账号（默认 root）
#   DB_PASSWORD   MySQL 密码（默认 apex-root，与 podman-compose.infra.yml 一致）
#
# 依赖: mysql 客户端（brew install mysql-client 或 mysql）
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-apex-root}"

echo "==> 初始化数据源动态切换演示环境"
echo "    连接: ${DB_USER}@${DB_HOST}:${DB_PORT}"

# 检查 mysql 客户端
if ! command -v mysql >/dev/null 2>&1; then
  echo "❌ 未找到 mysql 客户端，请先安装：brew install mysql-client" >&2
  exit 1
fi

# 执行初始化 SQL（建从库 + 主/从探针表 + 验证查询）
mysql -h"${DB_HOST}" -P"${DB_PORT}" -u"${DB_USER}" -p"${DB_PASSWORD}" \
  < "${SCRIPT_DIR}/init-slave.sql"

echo ""
echo "✅ 初始化完成："
echo "   - 从库 apex_system_slave（演示用，与主库同实例）"
echo "   - 主库 routing_probe.source = MASTER"
echo "   - 从库 routing_probe.source = SLAVE"
echo ""
echo "验证方式："
echo "   1. 启动应用（dev）后调用任一 @DataSource(\"slave\") 接口"
echo "   2. 观察日志 '数据源切换: method=..., target=slave'"
echo "   3. 或直接查询探针：SELECT source FROM routing_probe;"
echo "      （切到 slave 时返回 SLAVE，走主库返回 MASTER）"

#!/bin/bash
# Podman MySQL 启动与权限 SQL 执行脚本
# 在项目根目录提供 frontend_button_permission_config.sql 后运行本脚本。
set -e

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$PROJECT_ROOT"
COMPOSE=(podman-compose -f "${PROJECT_ROOT}/podman-compose.infra.yml")
SQL_FILE="frontend_button_permission_config.sql"

if ! podman info >/dev/null 2>&1; then
    echo "Podman 不可用；macOS 首次 podman machine init，再 podman machine start。"
    exit 1
fi
if [ ! -f "$SQL_FILE" ]; then
    echo "SQL 文件不存在: $SQL_FILE"
    exit 1
fi

"${COMPOSE[@]}" up -d mysql
MAX_WAIT=60
WAITED=0
while [ "$WAITED" -lt "$MAX_WAIT" ]; do
    if "${COMPOSE[@]}" exec -T mysql mysql -u root -papex-root -e "SELECT 1" >/dev/null 2>&1; then
        break
    fi
    sleep 2
    WAITED=$((WAITED + 2))
done
if [ "$WAITED" -ge "$MAX_WAIT" ]; then
    echo "MySQL 启动超时"
    "${COMPOSE[@]}" logs mysql | tail -20
    exit 1
fi

DB_EXISTS=$("${COMPOSE[@]}" exec -T mysql mysql -u root -papex-root \
    -e "SHOW DATABASES LIKE 'apex_system';" 2>/dev/null | grep apex_system || true)
if [ -z "$DB_EXISTS" ]; then
    "${COMPOSE[@]}" exec -T mysql mysql -u root -papex-root \
        -e "CREATE DATABASE apex_system CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
fi

"${COMPOSE[@]}" exec -T mysql mysql -u root -papex-root apex_system < "$SQL_FILE"
echo "SQL 脚本执行成功；验证权限配置:"
"${COMPOSE[@]}" exec -T mysql mysql -u root -papex-root apex_system -e "
SELECT COUNT(*) AS '新增权限数'
FROM sys_menu WHERE menu_type = 2 AND perms LIKE 'system:%';
SELECT m1.menu_name AS '父菜单', m2.menu_name AS '按钮权限', m2.perms AS '权限码',
  CASE WHEN rm.role_id IS NOT NULL THEN '已授权' ELSE '未授权' END AS '已授权'
FROM sys_menu m2
LEFT JOIN sys_menu m1 ON m2.parent_id = m1.id
LEFT JOIN sys_role_menu rm ON m2.id = rm.menu_id AND rm.role_id = 1
WHERE m2.menu_type = 2 AND m2.perms LIKE 'system:%'
ORDER BY m1.sort, m2.sort LIMIT 10;"

echo "下一步: 为前端添加 v-permission 指令并测试权限控制。"
echo "查看日志: podman-compose -f podman-compose.infra.yml logs mysql"
echo "连接数据库: podman-compose -f podman-compose.infra.yml exec mysql mysql -u root -papex-root apex_system"
echo "停止 MySQL: podman-compose -f podman-compose.infra.yml stop mysql"

#!/bin/bash
# ============================================================
# Docker MySQL启动与SQL执行一键脚本
# ============================================================
# 用途: 自动启动MySQL容器并执行权限配置SQL
# 使用: ./start_mysql_and_run_sql.sh
# ============================================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=========================================="
echo "Docker MySQL 启动与SQL执行脚本"
echo -e "==========================================${NC}"
echo ""

# 1. 检查Docker是否运行
echo -e "${YELLOW}[1/6] 检查Docker状态...${NC}"
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}❌ Docker未运行!${NC}"
    echo ""
    echo "请先启动Docker:"
    echo "  • macOS: 打开 Docker Desktop 或 OrbStack"
    echo "  • Linux: sudo systemctl start docker"
    echo ""
    echo "启动Docker后,重新运行此脚本:"
    echo "  ./start_mysql_and_run_sql.sh"
    exit 1
fi
echo -e "${GREEN}✅ Docker运行正常${NC}"
echo ""

# 2. 检查SQL文件是否存在
echo -e "${YELLOW}[2/6] 检查SQL脚本...${NC}"
SQL_FILE="frontend_button_permission_config.sql"
if [ ! -f "$SQL_FILE" ]; then
    echo -e "${RED}❌ SQL文件不存在: $SQL_FILE${NC}"
    exit 1
fi
echo -e "${GREEN}✅ SQL文件存在${NC}"
echo ""

# 3. 启动MySQL容器
echo -e "${YELLOW}[3/6] 启动MySQL容器...${NC}"
docker-compose -f docker-compose.infra.yml up -d mysql

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ MySQL容器启动失败${NC}"
    exit 1
fi
echo -e "${GREEN}✅ MySQL容器已启动${NC}"
echo ""

# 4. 等待MySQL就绪
echo -e "${YELLOW}[4/6] 等待MySQL初始化...${NC}"
echo "正在等待MySQL就绪(最多等待60秒)..."

MAX_WAIT=60
WAITED=0
while [ $WAITED -lt $MAX_WAIT ]; do
    if docker-compose -f docker-compose.infra.yml exec -T mysql \
       mysql -u root -papex-root -e "SELECT 1" > /dev/null 2>&1; then
        echo -e "${GREEN}✅ MySQL已就绪${NC}"
        break
    fi
    
    echo -n "."
    sleep 2
    WAITED=$((WAITED + 2))
done

if [ $WAITED -ge $MAX_WAIT ]; then
    echo ""
    echo -e "${RED}❌ MySQL启动超时${NC}"
    echo "查看MySQL日志:"
    docker-compose -f docker-compose.infra.yml logs mysql | tail -20
    exit 1
fi
echo ""

# 5. 检查数据库是否存在
echo -e "${YELLOW}[5/6] 检查数据库...${NC}"
DB_EXISTS=$(docker-compose -f docker-compose.infra.yml exec -T mysql \
    mysql -u root -papex-root -e "SHOW DATABASES LIKE 'apex_data';" 2>/dev/null | grep apex_data || true)

if [ -z "$DB_EXISTS" ]; then
    echo "数据库 apex_data 不存在,正在创建..."
    docker-compose -f docker-compose.infra.yml exec -T mysql \
        mysql -u root -papex-root -e "CREATE DATABASE apex_data CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    echo -e "${GREEN}✅ 数据库已创建${NC}"
else
    echo -e "${GREEN}✅ 数据库已存在${NC}"
fi
echo ""

# 6. 执行SQL脚本
echo -e "${YELLOW}[6/6] 执行SQL脚本...${NC}"
echo "正在执行: $SQL_FILE"
echo ""

docker-compose -f docker-compose.infra.yml exec -T mysql \
    mysql -u root -papex-root apex_data < "$SQL_FILE"

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ SQL执行失败${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}✅ SQL脚本执行成功${NC}"
echo ""

# 7. 验证结果
echo -e "${BLUE}=========================================="
echo "验证执行结果"
echo -e "==========================================${NC}"
echo ""

echo "1️⃣  新增权限数量:"
docker-compose -f docker-compose.infra.yml exec -T mysql \
    mysql -u root -papex-root apex_data -e "
SELECT COUNT(*) AS '新增权限数' 
FROM sys_menu 
WHERE menu_type = 2 AND perms LIKE 'system:%';"

echo ""
echo "2️⃣  权限详细列表 (前10条):"
docker-compose -f docker-compose.infra.yml exec -T mysql \
    mysql -u root -papex-root apex_data -e "
SELECT 
  m1.menu_name AS '父菜单',
  m2.menu_name AS '按钮权限',
  m2.perms AS '权限码',
  CASE WHEN rm.role_id IS NOT NULL THEN '✅' ELSE '❌' END AS '已授权'
FROM sys_menu m2
LEFT JOIN sys_menu m1 ON m2.parent_id = m1.id
LEFT JOIN sys_role_menu rm ON m2.id = rm.menu_id AND rm.role_id = 1
WHERE m2.menu_type = 2 AND m2.perms LIKE 'system:%'
ORDER BY m1.sort, m2.sort
LIMIT 10;"

echo ""
echo -e "${GREEN}=========================================="
echo "✅ 所有操作完成!"
echo -e "==========================================${NC}"
echo ""
echo "📝 下一步:"
echo "  1. 修改前端代码 - 添加 v-permission 指令"
echo "  2. 参考文档: .trae/documents/前端按钮权限控制补充执行脚本.md"
echo "  3. 测试权限控制"
echo ""
echo "🔗 相关命令:"
echo "  # 查看MySQL日志"
echo "  docker-compose -f docker-compose.infra.yml logs mysql"
echo ""
echo "  # 连接MySQL"
echo "  docker-compose -f docker-compose.infra.yml exec mysql mysql -u root -papex-root apex_data"
echo ""
echo "  # 停止MySQL"
echo "  docker-compose -f docker-compose.infra.yml stop mysql"
echo ""

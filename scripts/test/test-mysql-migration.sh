#!/bin/bash
###############################################################################
# MySQL Flyway 迁移隔离测试脚本
#
# 功能：
#   - 使用临时 Docker 容器验证 Flyway 迁移脚本
#   - 自动清理测试容器和数据，不影响现有业务数据库
#   - 完全隔离的测试环境
#
# 用法：
#   ./test-mysql-migration.sh
#
# 作者：Apex Scaffold Team
# 日期：2024-09-22
###############################################################################

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../lib/common.sh"

# 不使用 setup_error_trap，因为它会与 EXIT 陷阱冲突
PROJECT_ROOT=$(get_project_root) || exit 1
cd "$PROJECT_ROOT"

if [ "${APEX_MIGRATION_SDK_READY:-0}" != "1" ]; then
    require_file "$HOME/.sdkman/bin/sdkman-init.sh" || exit 1
    exec zsh -c 'source "$HOME/.sdkman/bin/sdkman-init.sh" && sdk env && export APEX_MIGRATION_SDK_READY=1 && exec /bin/bash "$1"' -- "$SCRIPT_DIR/test-mysql-migration.sh"
fi
require_command "mvnd" "Maven Daemon" || exit 1

# 临时容器配置
CONTAINER_NAME="apex-test-mysql-$(date +%s)"
NETWORK_NAME="apex-test-mysql-net-$$"
DB_PASSWORD="test_pwd_$(openssl rand -hex 8)"
DB_PORT=$(python3 -c 'import socket; s=socket.socket(); s.bind(("", 0)); print(s.getsockname()[1]); s.close()')
TEST_DB_NAME="apex_auth_test"

print_header "MySQL Flyway 迁移测试"

log_info "测试容器: ${CONTAINER_NAME}"
log_info "测试端口: ${DB_PORT}"
log_info "测试数据库: ${TEST_DB_NAME}"

# 清理函数
cleanup() {
    local exit_code=$?
    log_info "清理测试资源..."
    docker stop "$CONTAINER_NAME" 2>/dev/null || true
    docker rm "$CONTAINER_NAME" 2>/dev/null || true
    docker network rm "$NETWORK_NAME" 2>/dev/null || true
    log_success "清理完成"
    exit "$exit_code"
}
trap cleanup EXIT

# 创建网络和容器
print_section "创建测试环境"
docker network create "$NETWORK_NAME" >/dev/null
docker run -d \
    --name "$CONTAINER_NAME" \
    --network "$NETWORK_NAME" \
    -p "127.0.0.1:${DB_PORT}:3306" \
    -e MYSQL_ROOT_PASSWORD="$DB_PASSWORD" \
    -e MYSQL_DATABASE="$TEST_DB_NAME" \
    --tmpfs /var/lib/mysql:rw,noexec,nosuid,size=512m \
    mysql:8.0 \
    --character-set-server=utf8mb4 \
    --collation-server=utf8mb4_unicode_ci \
    >/dev/null

log_info "等待 MySQL 就绪..."
for i in {1..30}; do
    if docker exec "$CONTAINER_NAME" mysqladmin ping -h127.0.0.1 -P3306 --protocol=TCP -uroot -p"$DB_PASSWORD" --silent 2>/dev/null; then
        log_success "MySQL 已就绪"
        break
    fi
    [ "$i" -eq 30 ] && { log_error "MySQL 启动超时"; exit 1; }
    sleep 1
done

# 创建临时 Flyway 配置
print_section "准备 Flyway 配置"
TEMP_DIR=$(mktemp -d)
FLYWAY_CONF="$TEMP_DIR/flyway.conf"
cat > "$FLYWAY_CONF" << EOF
flyway.url=jdbc:mysql://127.0.0.1:${DB_PORT}/${TEST_DB_NAME}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
flyway.user=root
flyway.password=${DB_PASSWORD}
flyway.locations=filesystem:${PROJECT_ROOT}/apex-auth-center/src/main/resources/db/migration/mysql
flyway.baselineOnMigrate=false
flyway.cleanDisabled=true
flyway.validateMigrationNaming=true
EOF
chmod 600 "$FLYWAY_CONF"

# 执行迁移
print_section "执行 Flyway 迁移"

require_command "mvnd" "Maven Daemon" || exit 1

# 临时禁用 set -e 来捕获 mvnd 错误
set +e
mvnd -B -f apex-auth-center/pom.xml \
    -Dflyway.configFiles="$FLYWAY_CONF" \
    flyway:migrate
MVND_EXIT_CODE=$?
set -e

if [ $MVND_EXIT_CODE -ne 0 ]; then
    log_error "Flyway 迁移失败，退出码: $MVND_EXIT_CODE"
    exit 1
fi

# 验证结果
print_section "验证迁移结果"
TABLES=$(docker exec "$CONTAINER_NAME" mysql -uroot -p"$DB_PASSWORD" "$TEST_DB_NAME" \
    -sN -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${TEST_DB_NAME}'")
MIGRATIONS=$(docker exec "$CONTAINER_NAME" mysql -uroot -p"$DB_PASSWORD" "$TEST_DB_NAME" \
    -sN -e "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1")

log_info "创建表数量: $TABLES"
log_info "成功迁移数: $MIGRATIONS"

if [ "$TABLES" -gt 0 ] && [ "$MIGRATIONS" -gt 0 ]; then
    log_success "MySQL 迁移测试通过"
    rm -rf "$TEMP_DIR"
    exit 0
else
    log_failure "MySQL 迁移测试失败"
    rm -rf "$TEMP_DIR"
    exit 1
fi

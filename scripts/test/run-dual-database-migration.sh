#!/bin/bash
###############################################################################
# run-dual-database-migration.sh
#
# 双库迁移演练（方案 §13 脚本交付物）。
#
# 在真实 MySQL / PostgreSQL 容器上执行两个域的全链 Flyway 迁移回归：
#   - system：V1–V90（含 V76 租户编码统一、V77 RBAC 租户化、V78 套餐、
#             V79 租户配置、V80 自助菜单、V81–V84 权限种子、V87–V90 能力门禁）
#   - integration：V1–V3（连接器/凭据/审计、IoT 设备、Seata undo_log）
#
# 用法：
#   bash scripts/test/run-dual-database-migration.sh            # 全部
#   bash scripts/test/run-dual-database-migration.sh system     # 仅 system
#   bash scripts/test/run-dual-database-migration.sh integration # 仅 integration
#
# 依赖：Docker（Testcontainers 自动拉起 MySQL 9 / PostgreSQL 15）、JDK 21、mvnd
###############################################################################
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=../lib/common.sh
source "${SCRIPT_DIR}/../lib/common.sh"

PROJECT_ROOT=$(get_project_root) || exit 1
cd "$PROJECT_ROOT" || exit 1

TARGET=${1:-all}
FAILED=0

# 构建环境必须为 SDKMAN 固定的 JDK/mvnd，不静默回退
if [[ -f scripts/build/init-build-env.sh ]]; then
    # shellcheck source=../build/init-build-env.sh
    source scripts/build/init-build-env.sh
else
    log_failure "缺少 scripts/build/init-build-env.sh"
    exit 1
fi

run_suite() {
    local name=$1
    local module=$2
    local test_class=$3

    log_info "开始 ${name} 双库迁移演练（真实 MySQL 9 + PostgreSQL 15）"
    if mvnd -T1 -B -pl "$module" verify \
        -Dit.test="$test_class" \
        -Dtest=NoSuchDummy \
        -Dsurefire.failIfNoSpecifiedTests=false \
        -Dfailsafe.failIfNoSpecifiedTests=false \
        -Djacoco.skip=true; then
        log_success "${name} 双库迁移演练通过"
    else
        log_failure "${name} 双库迁移演练失败（详见 ${module}/target/failsafe-reports）"
        FAILED=1
    fi
}

if [[ "$TARGET" == "all" || "$TARGET" == "system" ]]; then
    run_suite "system（V1-V90）" "apex-system" "SystemDualDatabaseMigrationIntegrationTest"
fi

if [[ "$TARGET" == "all" || "$TARGET" == "integration" ]]; then
    run_suite "integration（V1-V3）" "apex-integration" "IntegrationDualDatabaseMigrationIntegrationTest"
fi

if [[ $FAILED -ne 0 ]]; then
    log_failure "双库迁移演练存在失败项"
    exit 1
fi
log_success "双库迁移演练全部通过"

# Apex Scaffold 项目文档、脚本、配置与数据库脚本整理计划

**创建日期**: 2026-09-15  
**项目**: Apex Scaffold  
**目标**: 重新整理项目文档、脚本、配置文件目录，清理冗余和过时内容，规范化数据库脚本管理

---

## 📋 执行摘要

本计划旨在系统化整理 Apex Scaffold 项目的文档、脚本、配置文件和数据库脚本，清理冗余、重复、过时和无效的文件，并将所有数据库变更脚本迁移到 Flyway 版本化管理体系中。

### 核心目标

1. ✅ 清理根目录和 `.trae/` 目录中的临时文件
2. ✅ 整理和归档 `.trae/documents/` 中的历史报告
3. ✅ 规范化数据库脚本管理，全部迁移到 Flyway
4. ✅ 优化脚本目录结构
5. ✅ 统一配置文件管理
6. ✅ 更新相关文档引用

---

## 🔍 当前状态分析

### 1. 文档问题

#### 1.1 根目录临时文件（需删除）
```
/Users/work/apex-scaffold/
├── javadoc_todo_list_20260913_171644.md
├── javadoc_todo_list_20260913_171102.md
├── javadoc_todo_list_20260913_171025.md
├── javadoc_todo_list_20260913_170813.md
├── javadoc_todo_list_20260913_170559.md
├── javadoc_todo_list_20260913_170201.md
├── javadoc_todo_list_20260913_165857.md
├── javadoc_todo_list_20260913_161037.md
├── javadoc_todo_list_20260913_155839.md
├── javadoc_todo_list_20260913_155531.md
├── javadoc_todo_list_20260913_154827.md
├── javadoc_todo_list_20260913_004440.md
├── javadoc_todo_list_20260912_235315.md
├── javadoc_todo_list_20260912_134456.md
├── check-report.txt
├── check-report-plain.txt
└── deploy_monitoring_configs.sh
```

**处理方案**: 
- javadoc_todo_list_*.md：全部删除（已完成任务的临时文件）
- check-report*.txt：删除（扫描报告临时文件）
- deploy_monitoring_configs.sh：移动到 `scripts/deploy/` 或删除（如已整合）

#### 1.2 .trae/documents/ 目录（需大幅精简并整合到 docs/）

**当前状态**: 包含 56 个文件，需要分类整合到 `docs/` 目录或归档

**完整文件清单与处理方案**:

##### A. 有价值文档 - 整合到 docs/ 正式目录

1. **前端开发指南类** → 整合到 `docs/04-development/frontend/`
   ```
   - FRONTEND-ENHANCEMENT-GUIDE.md → frontend-enhancement-guide.md
   - FRONTEND-IMPLEMENTATION-STATUS.md → frontend-implementation-status.md
   - frontend-api-update-guide.md → 保持原名
   - frontend-manual-steps.md → 保持原名
   - frontend-router-and-menu-config.md → 保持原名
   ```
   **处理方式**: 检查内容，与现有前端开发文档去重后整合

2. **实施计划类** → 整合到 `docs/08-project-management/plans/`
   ```
   - office-platform-integration-plan.md → 保持原名
   - phase-2-3-implementation-plan.md → 保持原名
   - flyway-version-conflict-fix-plan.md → 移动到 docs/08-project-management/plans/database/
   - documentation-optimization-plan.md → 保持原名（或归档）
   ```
   **处理方式**: 检查是否已完成，未完成的保留在 plans/，已完成的归档

3. **快速指南类** → 整合到 `docs/00-getting-started/` 或 `docs/08-project-management/`
   ```
   - QUICK-START.md → 对比 docs/00-getting-started/quickstart.md，内容相同则删除
   - phase-2-3-quick-guide.md → 移动到 docs/08-project-management/quick-guides/
   - phase-3-frontend-guide.md → 移动到 docs/08-project-management/quick-guides/
   ```

4. **配置清单类** → 整合到 `docs/05-operations/`
   ```
   - configuration-inventory.md → 移动到 docs/05-operations/configuration-inventory.md
   - phase-2-3-delivery-checklist.md → 移动到 docs/08-project-management/checklists/
   ```

5. **README** → 检查后决定
   ```
   - README.md → 检查内容，如有价值则整合到 docs/README.md 或主 README.md
   ```

##### B. 历史报告 - 归档到 docs/_archive/trae-reports/2026-09/

**完成报告类**（按时间顺序归档）:
```
- FINAL-REPORT.md
- FINAL_COMPLETION_SUMMARY.md
- final-completion-report.md
- final-completion-report-v2.md
- final-completion-report-v3.md
- final-integration-completion-report.md
- office-platform-completion-summary.md
- office-platform-delivery-report.md
```

**任务执行报告类**:
```
- TASK-COMPLETION-REPORT.md
- TASK-EXECUTION-SUMMARY.md
- TEST-REPORT.md
- implementation-consistency-fix-report.md
- integration-verification-report.md
```

**阶段报告类**:
```
- phase1-issues-report.md
- phase-2-progress-report.md
- phase-2-3-backend-completion-report.md
- phase-2-3-implementation-summary.md
- phase-2-3-final-summary.md
- phase4-assessment-report.md
```

**前端集成报告类**:
```
- frontend-code-cleanup-report.md
- frontend-implementation-gap-analysis.md
- frontend-integration-completion-report.md
- frontend-integration-final-report.md
- frontend-integration-report.md
- frontend-integration-summary.md
- frontend-message-approval-completion-report.md
```

**办公平台报告类**:
```
- office-integration-progress.md
```

**项目总结类**:
```
- project-final-report.md
- project-summary.md
```

**其他审查报告**:
```
- p0-fix-completion-report.md
- scripts-review-report.md
- svg-deep-verification-report.md
- svg-diagrams-review-report.md
- short-term-optimization-summary.md
- change-summary.md
```

**当前计划文件**（保留在 .trae/documents/ 作为工作目录）:
```
- project-documentation-and-configuration-review-plan.md
- project-documentation-and-database-reorganization-plan.md（本文件）
```

##### C. 数据库脚本 - 需迁移到 Flyway 或删除

```
- menu-config.sql → 检查 V36__add_office_platform_menus.sql，如已包含则删除
- menu-config-pg.sql → 检查 V36__add_office_platform_menus.sql，如已包含则删除
```

**处理步骤**:
1. 对比 `.trae/documents/menu-config.sql` 与 `apex-interfaces/src/main/resources/db/migration/mysql/V36__add_office_platform_menus.sql`
2. 如内容已被 V36 或后续版本覆盖 → 删除
3. 如有未迁移内容 → 创建新 Flyway 版本

##### D. 前端临时文件 - 归档或删除

**TypeScript/Vue 文件** → 归档到 `docs/_archive/frontend-integration/code-samples/`
```
- frontend-api-extension.ts
- frontend-api-implementation.ts
- frontend-approval-page.vue
- frontend-message-send-page.vue
```

**处理方式**:
1. 检查前端项目 `/Users/work/apex-admin-web/` 是否已整合这些代码
2. 如已整合且无参考价值 → 删除
3. 如需保留作为代码示例 → 归档到 `docs/_archive/frontend-integration/code-samples/`

#### 1.3 .trae/ 其他目录

**frontend-integration/** 和 **frontend-补充/**:
- 包含前端集成脚本和临时文件
- **处理方案**: 归档到 `docs/_archive/frontend-integration/` 后删除 `.trae` 中的副本

**scripts/**:
- 包含3个脚本：`integrate-frontend-code.sh`、`update-router-registry.sh`、`verify-frontend-integration.sh`
- **处理方案**: 移动到 `docs/_archive/frontend-integration/scripts/` 后删除

#### 1.4 docs/_archive/ 优化

**当前状态**: 已有良好的归档结构，包含 100+ 历史文档

**优化方案**:
- 创建子目录 `docs/_archive/trae-reports/` 存放所有 TRAE 生成的临时报告
- 保持现有分类结构（api/、changes/、integration/、operations/ 等）

### 2. 数据库脚本问题

#### 2.1 Flyway 版本冲突

**问题**: 发现重复的版本号
```
apex-interfaces/src/main/resources/db/migration/mysql/
├── V1__init_schema.sql              ← 正式版本
├── V1__add_office_message_tables.sql   ← 冲突！
├── V2__seed_system_permissions.sql  ← 正式版本
├── V2__add_office_approval_tables.sql  ← 冲突！
├── V3__add_username_password_to_sys_user.sql ← 正式版本
├── V3__add_office_calendar_tables.sql  ← 冲突！
├── V4__seed_audit_log_permissions.sql  ← 正式版本
├── V4__add_office_document_tables.sql  ← 冲突！
└── ... V5-V39 (正常)
```

**影响**: Flyway 会因版本号冲突而执行失败

**解决方案**: 
1. 检查 V1-V4 office 相关脚本的内容
2. 如果是重复内容或已被后续版本覆盖（如 V29-V39），则删除
3. 如果是独立内容，重新编号为 V40-V43

#### 2.2 非 Flyway 管理的 SQL 文件

**A. 根目录**:
```
frontend_button_permission_config.sql
```
- **内容**: 前端按钮权限配置
- **处理方案**: 检查是否已整合到 Flyway（可能在 V36 或 V39 中），如未整合则创建新版本 V40+

**B. .trae/documents/**:
```
menu-config.sql
menu-config-pg.sql
```
- **内容**: 办公平台菜单配置
- **处理方案**: 检查 V36__add_office_platform_menus.sql 是否已包含，如是则删除

**C. scripts/data/**:
```
init_workflow_test_data.sql
init_workflow_test_data_v2.sql
```
- **用途**: 工作流测试数据
- **处理方案**: 保留在 `scripts/data/` 目录（测试数据不属于 Flyway 版本化迁移范围）
- **重命名**: `init_workflow_test_data_v2.sql` → `workflow_test_data.sql`，删除 v1

**D. apex-example/src/main/resources/db/**:
```
init.sql
```
- **用途**: 示例模块数据库初始化
- **处理方案**: 保留（示例模块独立管理，不纳入主 Flyway）
- **优化**: 添加更清晰的注释说明其用途和幂等性

**E. scripts/datasource/**:
```
init-slave.sql
```
- **用途**: MySQL 主从复制初始化
- **处理方案**: 保留（运维脚本，不属于应用迁移）

**F. apex-interfaces/src/test/resources/**:
```
schema-it.sql
data-it.sql
```
- **用途**: 集成测试数据
- **处理方案**: 保留（测试资源，由 Flyway 在测试环境执行）

### 3. 脚本目录优化

#### 3.1 当前结构（较好）
```
scripts/
├── backup/          # 数据库备份恢复
├── bench/           # 性能基准测试
├── build/           # 构建脚本
├── ci/              # 持续集成
├── codegen/         # 代码生成
├── data/            # 测试数据
├── datasource/      # 数据源管理
├── deploy/          # 部署脚本
├── diagnostics/     # 诊断工具
├── init/            # 项目初始化
├── license/         # 授权管理
├── office/          # 办公平台生成脚本
└── test/            # 测试脚本
```

#### 3.2 需要处理的文件

**根目录脚本**:
- `deploy_monitoring_configs.sh` → 移动到 `scripts/deploy/` 或删除

**scripts/data/ 优化**:
- 删除 `init_workflow_test_data.sql`（保留 v2）
- 重命名 `init_workflow_test_data_v2.sql` → `workflow_test_data.sql`

**scripts/office/ 评估**:
- `generate-message-infrastructure.sh`
- `generate-phase2-code.sh`（已归档至 `scripts/archive/office/`）
- `generate-test-report.sh`
- `smoke-test.sh`
- `validate-office-config.sh`

**处理方案**: 检查这些脚本是否仍在使用，如已废弃则移动到 `docs/_archive/scripts/office/`

### 4. 配置文件管理

#### 4.1 当前分布

**A. 根目录**:
```
.env.example
podman-compose.yml
podman-compose.infra.yml
```
**处理方案**: 保持不变（根目录是标准位置）

**B. config/ 目录**:
```
config/
├── monitoring/          # Grafana、Prometheus、Loki 配置
├── sentinel/            # Sentinel 规则
├── feature-toggle-example.yml
└── office-platforms.env.example
```
**处理方案**: 
- 保持结构不变
- 检查 `feature-toggle-example.yml` 和 `office-platforms.env.example` 是否有更好的位置

**C. apex-interfaces/src/main/resources/**:
```
application.yml
application-basic.yml
application-dev.yml
application-prod.yml
application-standard.yml
application-enterprise.yml
application-performance.yml
application-rate-limit.yml
bootstrap.yml
```
**处理方案**: 保持不变（Spring Boot 标准位置）

#### 4.2 优化建议

创建配置文档 `docs/05-operations/configuration-guide.md`，统一说明所有配置文件的位置和用途。

### 5. 其他需要清理的项目

**A. 根目录**:
- `MAPSTRUCT_MIGRATION_SUMMARY.md` → 移动到 `docs/_archive/reports/`
- `DATABASE_CONFIG_QUICK_REFERENCE.md` → 移动到 `docs/04-development/`

**B. reports/ 目录**:
```
reports/project-summary-report/
├── README.md
└── project-summary-report.html
```
**处理方案**: 移动到 `docs/_archive/reports/`

**C. ppt/ 目录**（如果存在）:
```
ppt/架构图表新人培训/
```
**处理方案**: 检查内容，考虑移动到 `docs/_archive/` 或保留在 `ppt/` 作为培训资料

**D. application/ 目录**（根目录）:
```
application/office/assembler/ApprovalProcessAssembler.java
```
**问题**: 这不应该在根目录，应该在模块中
**处理方案**: 检查是否为误放文件，如是则删除

---

## 📝 执行计划

### Phase 1: 文档清理与整理（高优先级）

#### 步骤 1.1: 清理根目录临时文件
```bash
# 删除 javadoc todo list
rm -f /Users/work/apex-scaffold/javadoc_todo_list_*.md

# 删除检查报告
rm -f /Users/work/apex-scaffold/check-report*.txt

# 移动或删除部署脚本
# （需先检查是否已整合到 scripts/deploy/）
```

#### 步骤 1.2: 整合 .trae/documents/ 到 docs/ 目录

**子步骤 1.2.1: 创建必要的目录结构**
```bash
# 创建归档目录
mkdir -p docs/_archive/trae-reports/2026-09
mkdir -p docs/_archive/frontend-integration/code-samples

# 创建项目管理子目录
mkdir -p docs/08-project-management/quick-guides
mkdir -p docs/08-project-management/checklists
mkdir -p docs/08-project-management/plans/database
```

**子步骤 1.2.2: 整合有价值文档到 docs/ 正式目录**

**A. 前端开发指南类** → `docs/04-development/frontend/`
```bash
cd /Users/work/apex-scaffold

# 检查并对比现有文档，避免重复
# 如果内容已被覆盖或重复，则跳过；否则移动并重命名

# FRONTEND-ENHANCEMENT-GUIDE.md
if [ -f .trae/documents/FRONTEND-ENHANCEMENT-GUIDE.md ]; then
  # 检查是否与现有文档重复
  # 如无重复，移动
  mv .trae/documents/FRONTEND-ENHANCEMENT-GUIDE.md \
     docs/04-development/frontend/frontend-enhancement-guide.md
fi

# FRONTEND-IMPLEMENTATION-STATUS.md
if [ -f .trae/documents/FRONTEND-IMPLEMENTATION-STATUS.md ]; then
  mv .trae/documents/FRONTEND-IMPLEMENTATION-STATUS.md \
     docs/04-development/frontend/frontend-implementation-status.md
fi

# frontend-api-update-guide.md
if [ -f .trae/documents/frontend-api-update-guide.md ]; then
  mv .trae/documents/frontend-api-update-guide.md \
     docs/04-development/frontend/
fi

# frontend-manual-steps.md
if [ -f .trae/documents/frontend-manual-steps.md ]; then
  mv .trae/documents/frontend-manual-steps.md \
     docs/04-development/frontend/
fi

# frontend-router-and-menu-config.md
if [ -f .trae/documents/frontend-router-and-menu-config.md ]; then
  mv .trae/documents/frontend-router-and-menu-config.md \
     docs/04-development/frontend/
fi
```

**B. 实施计划类** → `docs/08-project-management/plans/`
```bash
# office-platform-integration-plan.md
if [ -f .trae/documents/office-platform-integration-plan.md ]; then
  # 检查计划状态：如已完成则归档，未完成则保留在 plans/
  mv .trae/documents/office-platform-integration-plan.md \
     docs/08-project-management/plans/
fi

# phase-2-3-implementation-plan.md
if [ -f .trae/documents/phase-2-3-implementation-plan.md ]; then
  mv .trae/documents/phase-2-3-implementation-plan.md \
     docs/08-project-management/plans/
fi

# flyway-version-conflict-fix-plan.md
if [ -f .trae/documents/flyway-version-conflict-fix-plan.md ]; then
  mv .trae/documents/flyway-version-conflict-fix-plan.md \
     docs/08-project-management/plans/database/
fi

# documentation-optimization-plan.md（已完成，归档）
if [ -f .trae/documents/documentation-optimization-plan.md ]; then
  mv .trae/documents/documentation-optimization-plan.md \
     docs/_archive/trae-reports/2026-09/
fi
```

**C. 快速指南类** → `docs/00-getting-started/` 或 `docs/08-project-management/quick-guides/`
```bash
# QUICK-START.md - 对比现有文档
if [ -f .trae/documents/QUICK-START.md ]; then
  # 对比 docs/00-getting-started/quickstart.md
  if diff -q .trae/documents/QUICK-START.md docs/00-getting-started/quickstart.md > /dev/null 2>&1; then
    # 内容相同，删除
    rm .trae/documents/QUICK-START.md
    echo "QUICK-START.md 与现有文档相同，已删除"
  else
    # 内容不同，需要人工审查
    echo "QUICK-START.md 与现有文档不同，需要人工审查合并"
  fi
fi

# phase-2-3-quick-guide.md
if [ -f .trae/documents/phase-2-3-quick-guide.md ]; then
  mv .trae/documents/phase-2-3-quick-guide.md \
     docs/08-project-management/quick-guides/
fi

# phase-3-frontend-guide.md
if [ -f .trae/documents/phase-3-frontend-guide.md ]; then
  mv .trae/documents/phase-3-frontend-guide.md \
     docs/08-project-management/quick-guides/
fi
```

**D. 配置清单类** → `docs/05-operations/` 和 `docs/08-project-management/checklists/`
```bash
# configuration-inventory.md
if [ -f .trae/documents/configuration-inventory.md ]; then
  mv .trae/documents/configuration-inventory.md \
     docs/05-operations/
fi

# phase-2-3-delivery-checklist.md
if [ -f .trae/documents/phase-2-3-delivery-checklist.md ]; then
  mv .trae/documents/phase-2-3-delivery-checklist.md \
     docs/08-project-management/checklists/
fi
```

**E. README** → 检查后决定
```bash
# 读取 .trae/documents/README.md 内容
if [ -f .trae/documents/README.md ]; then
  echo "检查 .trae/documents/README.md 内容..."
  head -50 .trae/documents/README.md
  # 需要人工判断：
  # - 如果是索引目录，考虑整合到 docs/README.md
  # - 如果是临时说明，归档
  # - 如果已过时，删除
fi
```

**子步骤 1.2.3: 归档历史报告到 docs/_archive/trae-reports/2026-09/**

```bash
# 完成报告类
mv .trae/documents/FINAL-REPORT.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true
mv .trae/documents/FINAL_COMPLETION_SUMMARY.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true
mv .trae/documents/final-completion-report.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true
mv .trae/documents/final-completion-report-v2.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true
mv .trae/documents/final-completion-report-v3.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true
mv .trae/documents/final-integration-completion-report.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true
mv .trae/documents/office-platform-completion-summary.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true
mv .trae/documents/office-platform-delivery-report.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true

# 任务执行报告类
mv .trae/documents/TASK-COMPLETION-REPORT.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true
mv .trae/documents/TASK-EXECUTION-SUMMARY.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true
mv .trae/documents/TEST-REPORT.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true
mv .trae/documents/implementation-consistency-fix-report.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true
mv .trae/documents/integration-verification-report.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true

# 阶段报告类
mv .trae/documents/phase1-issues-report.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true
mv .trae/documents/phase-2-progress-report.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true
mv .trae/documents/phase-2-3-backend-completion-report.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true
mv .trae/documents/phase-2-3-implementation-summary.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true
mv .trae/documents/phase-2-3-final-summary.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true
mv .trae/documents/phase4-assessment-report.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true

# 前端集成报告类
mv .trae/documents/frontend-code-cleanup-report.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true
mv .trae/documents/frontend-implementation-gap-analysis.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true
mv .trae/documents/frontend-integration-completion-report.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true
mv .trae/documents/frontend-integration-final-report.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true
mv .trae/documents/frontend-integration-report.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true
mv .trae/documents/frontend-integration-summary.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true
mv .trae/documents/frontend-message-approval-completion-report.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true

# 办公平台报告类
mv .trae/documents/office-integration-progress.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true

# 项目总结类
mv .trae/documents/project-final-report.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true
mv .trae/documents/project-summary.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true

# 其他审查报告
mv .trae/documents/p0-fix-completion-report.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true
mv .trae/documents/scripts-review-report.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true
mv .trae/documents/svg-deep-verification-report.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true
mv .trae/documents/svg-diagrams-review-report.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true
mv .trae/documents/short-term-optimization-summary.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true
mv .trae/documents/change-summary.md docs/_archive/trae-reports/2026-09/ 2>/dev/null || true
```

**子步骤 1.2.4: 处理前端临时文件**

```bash
# 归档 TypeScript/Vue 代码示例
mv .trae/documents/frontend-api-extension.ts docs/_archive/frontend-integration/code-samples/ 2>/dev/null || true
mv .trae/documents/frontend-api-implementation.ts docs/_archive/frontend-integration/code-samples/ 2>/dev/null || true
mv .trae/documents/frontend-approval-page.vue docs/_archive/frontend-integration/code-samples/ 2>/dev/null || true
mv .trae/documents/frontend-message-send-page.vue docs/_archive/frontend-integration/code-samples/ 2>/dev/null || true

# 创建 README 说明这些代码示例的用途
cat > docs/_archive/frontend-integration/code-samples/README.md << 'EOF'
# 前端集成代码示例

本目录包含在前端集成过程中生成的代码示例文件，作为参考保留。

## 文件说明

- `frontend-api-extension.ts`: API 扩展示例
- `frontend-api-implementation.ts`: API 实现示例
- `frontend-approval-page.vue`: 审批页面组件示例
- `frontend-message-send-page.vue`: 消息发送页面组件示例

**注意**: 这些文件仅作为历史参考，实际实现已整合到前端项目中。

**归档日期**: 2026-09-15
EOF
```

**子步骤 1.2.5: 验证整合结果**

```bash
# 检查 .trae/documents/ 剩余文件
echo "=== .trae/documents/ 剩余文件 ==="
ls -la .trae/documents/

# 应该只剩下：
# - menu-config.sql（待 Phase 2 处理）
# - menu-config-pg.sql（待 Phase 2 处理）
# - project-documentation-and-configuration-review-plan.md（当前工作文件）
# - project-documentation-and-database-reorganization-plan.md（本计划文件）

# 验证 docs/ 目录新增文件
echo "=== docs/ 新增文件验证 ==="
ls -la docs/04-development/frontend/ | grep -E "frontend-(enhancement|implementation|api|manual|router)"
ls -la docs/08-project-management/plans/
ls -la docs/08-project-management/quick-guides/
ls -la docs/08-project-management/checklists/
ls -la docs/05-operations/ | grep "configuration-inventory"
ls -la docs/_archive/trae-reports/2026-09/ | wc -l
```

#### 步骤 1.3: 清理 .trae/ 其他目录
```bash
# 归档前端集成相关
mv .trae/frontend-integration docs/_archive/
mv .trae/frontend-补充 docs/_archive/
mv .trae/scripts docs/_archive/frontend-integration/scripts
```

#### 步骤 1.4: 移动其他文档
```bash
# 移动到正确位置
mv MAPSTRUCT_MIGRATION_SUMMARY.md docs/_archive/reports/
mv DATABASE_CONFIG_QUICK_REFERENCE.md docs/04-development/
mv reports/project-summary-report docs/_archive/reports/
```

### Phase 2: 数据库脚本规范化（最高优先级）

#### 步骤 2.1: 解决 Flyway 版本冲突

**操作清单**:
1. 检查 V1-V4 office 相关脚本内容
2. 对比 V29-V39 是否已包含相同功能
3. 决策：删除重复脚本 或 重新编号为 V40-V43

**详细步骤**:
```bash
cd /Users/work/apex-scaffold/apex-interfaces/src/main/resources/db/migration

# 检查冲突脚本
for version in 1 2 3 4; do
  echo "=== Version $version ==="
  ls -la mysql/V${version}__*.sql
  ls -la postgresql/V${version}__*.sql
done

# 读取脚本头部注释，了解用途
head -20 mysql/V1__init_schema.sql
head -20 mysql/V1__add_office_message_tables.sql
# ...

# 决策后执行删除或重命名
```

**预期结果**:
- MySQL 和 PostgreSQL 目录中的 Flyway 脚本版本号唯一且连续
- 无版本冲突错误

#### 步骤 2.2: 处理非 Flyway SQL 文件

**A. 检查 frontend_button_permission_config.sql**:
```bash
# 检查内容
cat frontend_button_permission_config.sql | head -50

# 搜索 Flyway 脚本中是否已包含
grep -r "system:menu:add\|system:dept:add" apex-interfaces/src/main/resources/db/migration/

# 决策：
# - 如已包含 → 删除
# - 如未包含 → 创建 V40__add_button_permissions.sql
```

**B. 检查 .trae/documents/ 中的 SQL**:
```bash
# 对比 V36__add_office_platform_menus.sql
diff .trae/documents/menu-config.sql \
     apex-interfaces/src/main/resources/db/migration/mysql/V36__add_office_platform_menus.sql

# 决策：如内容相同或已被覆盖 → 删除
```

**C. 优化测试数据脚本**:
```bash
# 重命名并删除旧版本
mv scripts/data/init_workflow_test_data_v2.sql scripts/data/workflow_test_data.sql
rm scripts/data/init_workflow_test_data.sql

# 更新脚本中的注释
```

**D. 优化 apex-example/src/main/resources/db/init.sql**:
- 添加详细注释说明用途
- 确保幂等性
- 添加执行说明

#### 步骤 2.3: 创建新的 Flyway 脚本（如需要）

如果发现 frontend_button_permission_config.sql 或其他 SQL 包含未迁移的内容：

```bash
# 确定下一个版本号
NEXT_VERSION=$(ls apex-interfaces/src/main/resources/db/migration/mysql/ | \
               grep -E "^V[0-9]+" | \
               sed 's/V\([0-9]*\)__.*/\1/' | \
               sort -n | \
               tail -1 | \
               awk '{print $1+1}')

# 创建新脚本
# 例如：V40__add_missing_button_permissions.sql
```

#### 步骤 2.4: 验证 Flyway 迁移完整性

```bash
# 运行 Flyway 版本检查测试
cd /Users/work/apex-scaffold
sdk env
mvnd -pl apex-interfaces test -Dtest=FlywayMigrationVersionTest

# 运行迁移启动测试
mvnd -pl apex-interfaces test -Dtest=FlywayMigrationStartupTest

# 检查版本连续性
scripts/ci/check-flyway-versions.sh  # 如存在
```

### Phase 3: 脚本目录优化

#### 步骤 3.1: 处理根目录脚本
```bash
# 检查 deploy_monitoring_configs.sh
cat deploy_monitoring_configs.sh

# 决策并执行
# 方案 A: 移动到 scripts/deploy/
mv deploy_monitoring_configs.sh scripts/deploy/deploy-monitoring.sh

# 方案 B: 删除（如已整合）
rm deploy_monitoring_configs.sh
```

#### 步骤 3.2: 评估 scripts/office/ 脚本
```bash
# 检查每个脚本的最后修改时间和用途
cd scripts/office
for script in *.sh; do
  echo "=== $script ==="
  ls -lh "$script"
  head -20 "$script"
done

# 决策：
# - 仍在使用 → 保留
# - 已废弃 → 移动到 docs/_archive/scripts/office/
```

### Phase 4: 配置文件优化

#### 步骤 4.1: 评估 config/ 目录文件位置
```bash
# 检查 feature-toggle-example.yml 用途
cat config/feature-toggle-example.yml

# 决策：
# - 保留在 config/（如是通用示例）
# - 移动到 apex-interfaces/src/main/resources/（如是应用配置示例）
```

#### 步骤 4.2: 创建配置管理文档
```bash
# 创建或更新配置指南
# docs/05-operations/configuration-guide.md
```

### Phase 5: 清理其他项目

#### 步骤 5.1: 检查 application/ 目录
```bash
# 检查根目录的 application/ 是否误放
ls -la application/office/assembler/

# 对比模块中的正确位置
find apex-* -name "ApprovalProcessAssembler.java"

# 决策并清理
```

#### 步骤 5.2: 更新文档引用

检查并更新以下文档中的文件路径引用：
- `AGENTS.md`
- `README.md`
- `docs/` 下所有指南文档
- `CONTRIBUTING.md`

### Phase 6: 验证与文档更新

#### 步骤 6.1: 运行完整性检查
```bash
# 检查文档链接
scripts/ci/check-doc-links.sh

# 检查文档一致性
scripts/ci/check-doc-consistency.sh

# 构建测试
mvnd clean compile -DskipTests

# 完整测试
mvnd test -P basic
```

#### 步骤 6.2: 更新 CHANGELOG.md

记录本次整理的主要变更：
```markdown
## [Unreleased]

### Changed
- 重组项目文档结构，清理临时文件和历史报告
- 规范化数据库脚本管理，全部迁移到 Flyway 版本化体系
- 优化脚本目录结构，归档废弃脚本
- 更新配置文件管理文档

### Removed
- 删除根目录临时 javadoc todo list 文件
- 删除冗余和过时的数据库脚本
- 清理 .trae/ 目录中的历史报告和临时文件
```

#### 步骤 6.3: 创建整理报告

生成本次整理的详细报告：
- 删除的文件清单
- 移动的文件清单
- 新建的 Flyway 脚本清单
- 更新的文档清单

---

## ⚠️ 注意事项

### 1. 数据安全

- **备份重要文件**: 在删除任何文件前，先备份到 `.trae/backup/` 目录
- **验证 Flyway 脚本**: 确保新建或修改的 Flyway 脚本在测试环境验证通过
- **保留 Git 历史**: 所有删除操作通过 `git rm`，保留历史记录

### 2. 执行顺序

- **Phase 2 优先**: 数据库脚本问题最严重，优先解决
- **分批提交**: 按 Phase 分批 commit，便于回滚
- **测试验证**: 每个 Phase 完成后运行相关测试

### 3. 团队协调

- **通知团队**: 在执行前通知团队成员，避免并发修改
- **文档同步**: 及时更新 README 和 AGENTS.md
- **培训说明**: 向团队说明新的文件组织规范

---

## 📊 预期成果

### 量化指标

- **根目录文件**: 从 21 个减少到 ~12 个（清理 9+ 个临时文件）
- **.trae/documents/**: 从 56 个减少到 ~4 个（仅保留当前工作计划和必要的 SQL 待处理文件）
  - 有价值文档（10+ 个）整合到 `docs/` 正式目录
  - 历史报告（35+ 个）归档到 `docs/_archive/trae-reports/2026-09/`
  - 前端临时文件（4 个）归档到 `docs/_archive/frontend-integration/code-samples/`
- **docs/ 新增结构**:
  - `docs/04-development/frontend/`: +5 个前端开发指南
  - `docs/08-project-management/plans/`: +3 个实施计划
  - `docs/08-project-management/quick-guides/`: +2 个快速指南
  - `docs/08-project-management/checklists/`: +1 个交付清单
  - `docs/05-operations/`: +1 个配置清单
  - `docs/_archive/trae-reports/2026-09/`: +35 个历史报告
- **Flyway 脚本**: 版本号连续无冲突（V1-V39+ MySQL/PostgreSQL 各一套）
- **非 Flyway SQL**: 从 7 个减少到 2-3 个（仅保留测试和示例数据）

### 质量提升

- ✅ 所有数据库变更纳入 Flyway 版本化管理
- ✅ 文档结构清晰，易于维护和查找
- ✅ 脚本分类明确，用途清楚
- ✅ 配置文件管理规范，有清晰文档说明
- ✅ Git 历史干净，减少噪音提交

---

## 🔄 后续维护规范

### 1. 文档管理

- **临时报告**: 直接创建在 `docs/_archive/trae-reports/YYYY-MM/`
- **正式文档**: 创建在 `docs/` 的相应分类目录
- **根目录**: 仅保留顶级 README、LICENSE、CHANGELOG 等必要文件

### 2. 数据库脚本

- **所有变更**: 必须创建 Flyway 脚本（V{next}__description.sql）
- **命名规范**: `V{版本号}__{描述}.sql`（描述使用下划线分隔）
- **测试数据**: 放在 `scripts/data/`，不纳入 Flyway
- **示例数据**: 放在对应模块的 resources/db/ 目录

### 3. 脚本管理

- **新脚本**: 创建在 `scripts/` 对应分类目录
- **一次性脚本**: 使用后归档到 `docs/_archive/scripts/`
- **脚本命名**: 使用小写和连字符（kebab-case）

### 4. 配置文件

- **应用配置**: `apex-interfaces/src/main/resources/`
- **基础设施配置**: `config/` 目录
- **环境变量示例**: 根目录 `.env.example`
- **更新文档**: 配置变更后同步更新 `docs/05-operations/configuration-guide.md`

---

## 📚 参考文档

- [AGENTS.md](../../../../AGENTS.md) - 项目开发规范
- [docs/reports/DOCS_REORGANIZATION_REPORT.md](../../../reports/DOCS_REORGANIZATION_REPORT.md) - 上次文档重组报告
- [docs/04-development/development-standards.md](../../../04-development/development-standards.md) - 后端开发规范
- [Flyway Documentation](https://flywaydb.org/documentation/) - Flyway 官方文档

---

## ✅ 检查清单

执行完成后检查：

- [ ] 根目录无临时文件（javadoc_todo_list、check-report 等）
- [ ] .trae/documents/ 仅保留必要文件（<10 个）
- [ ] Flyway 脚本版本号连续无冲突
- [ ] 非 Flyway SQL 文件有明确用途说明
- [ ] scripts/ 目录结构清晰，无废弃脚本
- [ ] 所有文档链接有效
- [ ] mvnd clean test 通过
- [ ] FlywayMigrationVersionTest 通过
- [ ] FlywayMigrationStartupTest 通过
- [ ] CHANGELOG.md 已更新
- [ ] 团队已通知并确认

---

**计划状态**: 待批准执行  
**预计耗时**: 4-6 小时  
**风险等级**: 中（主要风险在数据库脚本修改）  
**建议执行时间**: 非高峰期，确保有充足时间测试验证

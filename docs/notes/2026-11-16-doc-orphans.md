# 文档孤岛盘点（BE-002）

> 生成日期：2026-11-16 · 仓库：`apex-scaffold` · **只盘点，不移动、不删除**
> 复现：`bash scripts/governance/report-doc-orphans.sh`（本次新增，见 `scripts/governance/README.md`）

## 1. 口径

- **被引用**：在 `docs/README.md` 或任一目录 `README.md` 中以相对链接指向的 `.md`。
- **孤岛**：`docs/` 下已入库的 `.md`，既不是 README，也没有被任何索引 README 链接到。
- **本次结果**：`docs/` 内 `.md` 共 **772** 篇（其中索引 README 38 篇），被索引引用 **374** 个路径，**孤岛 527 篇**。

## 2. 汇总（按目录）

| 目录 | 孤岛数 | 建议归属 | 处置建议 |
| --- | --- | --- | --- |
| `docs/01-guides` | 9 | 指南 | 在 `docs/01-guides/README.md` 增补索引 |
| `docs/02-architecture` | 4 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/03-api` | 19 | 接口文档 | 在 `docs/03-api/README.md` 增补索引；契约类归 `docs/specs/` |
| `docs/03-api/openapi` | 1 | 接口文档 | 在 `docs/03-api/README.md` 增补索引；契约类归 `docs/specs/` |
| `docs/04-development` | 9 | 开发规范 | 在 `docs/04-development/README.md` 增补索引 |
| `docs/05-operations` | 13 | 运维手册 | 在 `docs/05-operations/README.md` 增补索引 |
| `docs/06-testing` | 7 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/06-testing/jdk21-unified-auth/20261016-p0` | 2 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/06-testing/jwt-unification/20261031-a1-a2` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/06-testing/jwt-unification/20261031-a3-a7` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/06-testing/jwt-unification/20261031-b1-a6` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/06-testing/jwt-unification/20261031-b2` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/06-testing/jwt-unification/20261031-b3` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/06-testing/jwt-unification/20261031-b4` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/06-testing/jwt-unification/20261031-b5` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/06-testing/jwt-unification/20261031-b6` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/06-testing/jwt-unification/20261031-b7` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/06-testing/jwt-unification/20261101-b8` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/06-testing/jwt-unification/20261101-b9` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/06-testing/jwt-unification/20261101-c0-c2` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/06-testing/jwt-unification/20261101-c3-c8` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/06-testing/jwt-unification/20261101-c7` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/06-testing/jwt-unification/20261101-c9` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/06-testing/jwt-unification/20261101-d1-d6` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/06-testing/jwt-unification/20261102-acl-and-fullstack` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/06-testing/jwt-unification/20261102-gap-closure` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/06-testing/jwt-unification/20261102-live-verification` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/06-testing/jwt-unification/20261102-m5` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/06-testing/jwt-unification/20261103-platform-ops-closure` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/06-testing/virtual-thread-bench/20261027/runs/20261027-165501` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/06-testing/virtual-thread-bench/20261027/runs/20261027-191439` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/06-testing/virtual-thread-bench/20261027` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/07-commercial` | 6 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/08-project-management` | 3 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/08-project-management/checklists` | 2 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/08-project-management/features` | 2 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/08-project-management/periodic-reports/daily` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/08-project-management/periodic-reports/monthly` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/08-project-management/periodic-reports/weekly` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/08-project-management/plans` | 27 | 历史计划正文 | 在 `docs/08-project-management/plans` 增补索引段，或按主题转 `docs/plans/` |
| `docs/08-project-management/plans/ai-assisted` | 12 | 历史计划正文 | 在 `docs/08-project-management/plans` 增补索引段，或按主题转 `docs/plans/` |
| `docs/08-project-management/plans/database` | 2 | 历史计划正文 | 在 `docs/08-project-management/plans` 增补索引段，或按主题转 `docs/plans/` |
| `docs/08-project-management/quick-guides` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/08-project-management/reports` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/08-project-management/reports/storage-refactoring-tests` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/08-project-management/workspace-governance` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/_archive` | 19 | 归档区：历史留档，不要求入口索引 | 保持现状；目录 README 已声明归档属性即可 |
| `docs/_archive/api` | 2 | 归档区：历史留档，不要求入口索引 | 保持现状；目录 README 已声明归档属性即可 |
| `docs/_archive/changes` | 1 | 归档区：历史留档，不要求入口索引 | 保持现状；目录 README 已声明归档属性即可 |
| `docs/_archive/integration` | 3 | 归档区：历史留档，不要求入口索引 | 保持现状；目录 README 已声明归档属性即可 |
| `docs/_archive/integration/oauth2/archive` | 10 | 归档区：历史留档，不要求入口索引 | 保持现状；目录 README 已声明归档属性即可 |
| `docs/_archive/operations` | 10 | 归档区：历史留档，不要求入口索引 | 保持现状；目录 README 已声明归档属性即可 |
| `docs/_archive/optimization` | 4 | 归档区：历史留档，不要求入口索引 | 保持现状；目录 README 已声明归档属性即可 |
| `docs/_archive/postman` | 1 | 归档区：历史留档，不要求入口索引 | 保持现状；目录 README 已声明归档属性即可 |
| `docs/_archive/ppt/架构图表新人培训` | 3 | 归档区：历史留档，不要求入口索引 | 保持现状；目录 README 已声明归档属性即可 |
| `docs/_archive/reports` | 52 | 归档区：历史留档，不要求入口索引 | 保持现状；目录 README 已声明归档属性即可 |
| `docs/_archive/reports/archive/project-deliveries` | 8 | 归档区：历史留档，不要求入口索引 | 保持现状；目录 README 已声明归档属性即可 |
| `docs/_archive/reports/archive/task-implementations` | 6 | 归档区：历史留档，不要求入口索引 | 保持现状；目录 README 已声明归档属性即可 |
| `docs/_archive/reports/delivery` | 5 | 归档区：历史留档，不要求入口索引 | 保持现状；目录 README 已声明归档属性即可 |
| `docs/_archive/reports/phases` | 2 | 归档区：历史留档，不要求入口索引 | 保持现状；目录 README 已声明归档属性即可 |
| `docs/_archive/reports/progress` | 3 | 归档区：历史留档，不要求入口索引 | 保持现状；目录 README 已声明归档属性即可 |
| `docs/_archive/root-reports` | 8 | 归档区：历史留档，不要求入口索引 | 保持现状；目录 README 已声明归档属性即可 |
| `docs/_archive/superpowers/plans` | 2 | 归档区：历史留档，不要求入口索引 | 保持现状；目录 README 已声明归档属性即可 |
| `docs/_archive/svg-fix-2025` | 3 | 归档区：历史留档，不要求入口索引 | 保持现状；目录 README 已声明归档属性即可 |
| `docs/_archive/technical` | 6 | 归档区：历史留档，不要求入口索引 | 保持现状；目录 README 已声明归档属性即可 |
| `docs/_archive/technical/tests` | 1 | 归档区：历史留档，不要求入口索引 | 保持现状；目录 README 已声明归档属性即可 |
| `docs/_archive/test-reports` | 7 | 归档区：历史留档，不要求入口索引 | 保持现状；目录 README 已声明归档属性即可 |
| `docs/_archive/testing` | 1 | 归档区：历史留档，不要求入口索引 | 保持现状；目录 README 已声明归档属性即可 |
| `docs/_archive/trae-documents` | 25 | 归档区：历史留档，不要求入口索引 | 保持现状；目录 README 已声明归档属性即可 |
| `docs/_archive/trae-reports/2026-09` | 38 | 归档区：历史留档，不要求入口索引 | 保持现状；目录 README 已声明归档属性即可 |
| `docs/_archive/troubleshooting` | 2 | 归档区：历史留档，不要求入口索引 | 保持现状；目录 README 已声明归档属性即可 |
| `docs/api` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/architecture` | 27 | 架构说明 | 在 `docs/architecture/README.md` 增补索引（本目录确有 README 时） |
| `docs/architecture/_templates` | 4 | 架构说明 | 在 `docs/architecture/README.md` 增补索引（本目录确有 README 时） |
| `docs/architecture/advanced-features` | 8 | 架构说明 | 在 `docs/architecture/README.md` 增补索引（本目录确有 README 时） |
| `docs/archived/ai-sessions/zcode-plans` | 13 | 归档区：历史留档，不要求入口索引 | 保持现状；目录 README 已声明归档属性即可 |
| `docs/cache` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/.github/ISSUE_TEMPLATE` | 1 | 第三方图标源（上游资产） | 建议加 `_icon_sources/README.md` 说明来源与不回链；或后续移出 `docs/` |
| `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs` | 80 | 第三方图标源（上游资产） | 建议加 `_icon_sources/README.md` 说明来源与不回链；或后续移出 `docs/` |
| `docs/fix-reports` | 1 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/migration` | 3 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/plans/archived-ai-sessions` | 4 | 会话/计划正文 | 在 `docs/plans/README.md` 或对应 ai-sessions README 增补索引 |
| `docs/records` | 3 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/reference/ai-knowledge` | 3 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/reports/reviews` | 2 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |
| `docs/specs/ai-agents` | 5 | 专题正文 | 评估后补入所在目录 README 索引；无长期价值的转 `docs/_archive/` |

**判定要点**：`docs/_archive/**` 与三方图标源合计占大头，这部分属“留档不必设入口”；真正需要补索引的是编号目录（`01`–`08`）与 `architecture`/`api` 下的正经正文。

## 3. 明细

### `docs/01-guides`（9）

- `docs/01-guides/doc-search-changelog.md` — 建议：指南
- `docs/01-guides/doc-search-fix.md` — 建议：指南
- `docs/01-guides/doc-search-readme.md` — 建议：指南
- `docs/01-guides/domain-event-outbox-guide.md` — 建议：指南
- `docs/01-guides/office-platform-integration-guide.md` — 建议：指南
- `docs/01-guides/office-platform-phase2-guide.md` — 建议：指南
- `docs/01-guides/tenant-isolation-guide.md` — 建议：指南
- `docs/01-guides/实施策略建议.md` — 建议：指南
- `docs/01-guides/新对话启动文档.md` — 建议：指南

### `docs/02-architecture`（4）

- `docs/02-architecture/architecture-usage-guide.md` — 建议：专题正文
- `docs/02-architecture/context-map.md` — 建议：专题正文
- `docs/02-architecture/jdk21-unified-auth-multitenancy-implementation-plan.md` — 建议：专题正文
- `docs/02-architecture/multi-client-integration-plan.md` — 建议：专题正文

### `docs/03-api`（19）

- `docs/03-api/admin-apis-implementation-report.md` — 建议：接口文档
- `docs/03-api/api-documentation-preview.md` — 建议：接口文档
- `docs/03-api/api-path-corrections.md` — 建议：接口文档
- `docs/03-api/comprehensive-project-status-report.md` — 建议：接口文档
- `docs/03-api/final-implementation-summary.md` — 建议：接口文档
- `docs/03-api/final-project-completion-report.md` — 建议：接口文档
- `docs/03-api/integration-iot-api.md` — 建议：接口文档
- `docs/03-api/internal-api-contracts.md` — 建议：接口文档
- `docs/03-api/mid-term-tasks-implementation-guide.md` — 建议：接口文档
- `docs/03-api/next-steps-guide.md` — 建议：接口文档
- `docs/03-api/office-platform-enhancement-plan.md` — 建议：接口文档
- `docs/03-api/office-platform-summary.md` — 建议：接口文档
- `docs/03-api/open-platform-integration.md` — 建议：接口文档
- `docs/03-api/project-delivery-checklist.md` — 建议：接口文档
- `docs/03-api/springdoc-audit-report.md` — 建议：接口文档
- `docs/03-api/sso-module-summary.md` — 建议：接口文档
- `docs/03-api/sso-protocol-implementation.md` — 建议：接口文档
- `docs/03-api/test-implementation-report.md` — 建议：接口文档
- `docs/03-api/work-completion-report.md` — 建议：接口文档

### `docs/03-api/openapi`（1）

- `docs/03-api/openapi/MANIFEST.md` — 建议：接口文档

### `docs/04-development`（9）

- `docs/04-development/DATABASE_CONFIG_API_TEST_REPORT.md` — 建议：开发规范
- `docs/04-development/DATABASE_CONFIG_BACKEND_IMPLEMENTATION.md` — 建议：开发规范
- `docs/04-development/DATABASE_CONFIG_COMPLETE_VERIFICATION.md` — 建议：开发规范
- `docs/04-development/DATABASE_CONFIG_INTEGRATION_TEST_REPORT.md` — 建议：开发规范
- `docs/04-development/DATABASE_CONFIG_MENU_MANUAL_EXECUTION.md` — 建议：开发规范
- `docs/04-development/DATABASE_CONFIG_MENU_SQL_GUIDE.md` — 建议：开发规范
- `docs/04-development/DATABASE_CONFIG_QUICK_REFERENCE.md` — 建议：开发规范
- `docs/04-development/DATABASE_CONFIG_R2DBC_IMPLEMENTATION_STEPS.md` — 建议：开发规范
- `docs/04-development/DATABASE_CONFIG_R2DBC_MIGRATION.md` — 建议：开发规范

### `docs/05-operations`（13）

- `docs/05-operations/ci-cd-y2038-integration.md` — 建议：运维手册
- `docs/05-operations/configuration-inventory.md` — 建议：运维手册
- `docs/05-operations/data-masking.md` — 建议：运维手册
- `docs/05-operations/elk-log-query.md` — 建议：运维手册
- `docs/05-operations/github-branch-protection.md` — 建议：运维手册
- `docs/05-operations/in-app-messaging.md` — 建议：运维手册
- `docs/05-operations/message-middleware-operations.md` — 建议：运维手册
- `docs/05-operations/microservice-hardening-guide.md` — 建议：运维手册
- `docs/05-operations/multi-client-acceptance-checklist.md` — 建议：运维手册
- `docs/05-operations/object-storage-integration.md` — 建议：运维手册
- `docs/05-operations/oncall-runbook.md` — 建议：运维手册
- `docs/05-operations/seata-integration.md` — 建议：运维手册
- `docs/05-operations/xxl-job-integration.md` — 建议：运维手册

### `docs/06-testing`（7）

- `docs/06-testing/api-parameter-type-audit.md` — 建议：专题正文
- `docs/06-testing/backend-modules-test-report.md` — 建议：专题正文
- `docs/06-testing/python-to-java-tools-migration-summary-20261021.md` — 建议：专题正文
- `docs/06-testing/seata-integration-summary-report-20261026.md` — 建议：专题正文
- `docs/06-testing/seata-real-modes-test-report.md` — 建议：专题正文
- `docs/06-testing/test-report-interpretation.md` — 建议：专题正文
- `docs/06-testing/workflow-module-test-report.md` — 建议：专题正文

### `docs/06-testing/jdk21-unified-auth/20261016-p0`（2）

- `docs/06-testing/jdk21-unified-auth/20261016-p0/api-table-assessment.md` — 建议：专题正文
- `docs/06-testing/jdk21-unified-auth/20261016-p0/system-schema-attribution.md` — 建议：专题正文

### `docs/06-testing/jwt-unification/20261031-a1-a2`（1）

- `docs/06-testing/jwt-unification/20261031-a1-a2/report.md` — 建议：专题正文

### `docs/06-testing/jwt-unification/20261031-a3-a7`（1）

- `docs/06-testing/jwt-unification/20261031-a3-a7/report.md` — 建议：专题正文

### `docs/06-testing/jwt-unification/20261031-b1-a6`（1）

- `docs/06-testing/jwt-unification/20261031-b1-a6/report.md` — 建议：专题正文

### `docs/06-testing/jwt-unification/20261031-b2`（1）

- `docs/06-testing/jwt-unification/20261031-b2/report.md` — 建议：专题正文

### `docs/06-testing/jwt-unification/20261031-b3`（1）

- `docs/06-testing/jwt-unification/20261031-b3/report.md` — 建议：专题正文

### `docs/06-testing/jwt-unification/20261031-b4`（1）

- `docs/06-testing/jwt-unification/20261031-b4/report.md` — 建议：专题正文

### `docs/06-testing/jwt-unification/20261031-b5`（1）

- `docs/06-testing/jwt-unification/20261031-b5/report.md` — 建议：专题正文

### `docs/06-testing/jwt-unification/20261031-b6`（1）

- `docs/06-testing/jwt-unification/20261031-b6/report.md` — 建议：专题正文

### `docs/06-testing/jwt-unification/20261031-b7`（1）

- `docs/06-testing/jwt-unification/20261031-b7/report.md` — 建议：专题正文

### `docs/06-testing/jwt-unification/20261101-b8`（1）

- `docs/06-testing/jwt-unification/20261101-b8/report.md` — 建议：专题正文

### `docs/06-testing/jwt-unification/20261101-b9`（1）

- `docs/06-testing/jwt-unification/20261101-b9/report.md` — 建议：专题正文

### `docs/06-testing/jwt-unification/20261101-c0-c2`（1）

- `docs/06-testing/jwt-unification/20261101-c0-c2/report.md` — 建议：专题正文

### `docs/06-testing/jwt-unification/20261101-c3-c8`（1）

- `docs/06-testing/jwt-unification/20261101-c3-c8/report.md` — 建议：专题正文

### `docs/06-testing/jwt-unification/20261101-c7`（1）

- `docs/06-testing/jwt-unification/20261101-c7/report.md` — 建议：专题正文

### `docs/06-testing/jwt-unification/20261101-c9`（1）

- `docs/06-testing/jwt-unification/20261101-c9/report.md` — 建议：专题正文

### `docs/06-testing/jwt-unification/20261101-d1-d6`（1）

- `docs/06-testing/jwt-unification/20261101-d1-d6/report.md` — 建议：专题正文

### `docs/06-testing/jwt-unification/20261102-acl-and-fullstack`（1）

- `docs/06-testing/jwt-unification/20261102-acl-and-fullstack/report.md` — 建议：专题正文

### `docs/06-testing/jwt-unification/20261102-gap-closure`（1）

- `docs/06-testing/jwt-unification/20261102-gap-closure/report.md` — 建议：专题正文

### `docs/06-testing/jwt-unification/20261102-live-verification`（1）

- `docs/06-testing/jwt-unification/20261102-live-verification/report.md` — 建议：专题正文

### `docs/06-testing/jwt-unification/20261102-m5`（1）

- `docs/06-testing/jwt-unification/20261102-m5/report.md` — 建议：专题正文

### `docs/06-testing/jwt-unification/20261103-platform-ops-closure`（1）

- `docs/06-testing/jwt-unification/20261103-platform-ops-closure/report.md` — 建议：专题正文

### `docs/06-testing/virtual-thread-bench/20261027/runs/20261027-165501`（1）

- `docs/06-testing/virtual-thread-bench/20261027/runs/20261027-165501/summary.md` — 建议：专题正文

### `docs/06-testing/virtual-thread-bench/20261027/runs/20261027-191439`（1）

- `docs/06-testing/virtual-thread-bench/20261027/runs/20261027-191439/summary.md` — 建议：专题正文

### `docs/06-testing/virtual-thread-bench/20261027`（1）

- `docs/06-testing/virtual-thread-bench/20261027/summary.md` — 建议：专题正文

### `docs/07-commercial`（6）

- `docs/07-commercial/COMMERCIAL_GAP_ANALYSIS.md` — 建议：专题正文
- `docs/07-commercial/COMMERCIAL_IMPROVEMENT_TASKS.md` — 建议：专题正文
- `docs/07-commercial/COMMERCIAL_OPTIMIZATION_PROPOSAL.md` — 建议：专题正文
- `docs/07-commercial/COMMERCIAL_READINESS_REVIEW_2026-08.md` — 建议：专题正文
- `docs/07-commercial/COMMERCIAL_REFACTORING_PLAN.md` — 建议：专题正文
- `docs/07-commercial/RELEASE_v1.0.0.md` — 建议：专题正文

### `docs/08-project-management`（3）

- `docs/08-project-management/chart-implementation-progress.md` — 建议：专题正文
- `docs/08-project-management/phase-2-3-quick-guide.md` — 建议：专题正文
- `docs/08-project-management/scaffold-enhancement-review.md` — 建议：专题正文

### `docs/08-project-management/checklists`（2）

- `docs/08-project-management/checklists/microservice-acceptance-checklist.md` — 建议：专题正文
- `docs/08-project-management/checklists/phase-2-3-delivery-checklist.md` — 建议：专题正文

### `docs/08-project-management/features`（2）

- `docs/08-project-management/features/README-WORKFLOW-PROJECT.md` — 建议：专题正文
- `docs/08-project-management/features/workflow-engine-guide.md` — 建议：专题正文

### `docs/08-project-management/periodic-reports/daily`（1）

- `docs/08-project-management/periodic-reports/daily/2026-11-03.md` — 建议：专题正文

### `docs/08-project-management/periodic-reports/monthly`（1）

- `docs/08-project-management/periodic-reports/monthly/2026-11.md` — 建议：专题正文

### `docs/08-project-management/periodic-reports/weekly`（1）

- `docs/08-project-management/periodic-reports/weekly/2026-W45.md` — 建议：专题正文

### `docs/08-project-management/plans`（27）

- `docs/08-project-management/plans/MeilisearchAdapter完善实施指南.md` — 建议：历史计划正文
- `docs/08-project-management/plans/MeilisearchAdapter快速修复指南.md` — 建议：历史计划正文
- `docs/08-project-management/plans/RAG知识库实施方案.md` — 建议：历史计划正文
- `docs/08-project-management/plans/RAG知识库评审方案.md` — 建议：历史计划正文
- `docs/08-project-management/plans/build-management-plan.md` — 建议：历史计划正文
- `docs/08-project-management/plans/documentation-review-plan.md` — 建议：历史计划正文
- `docs/08-project-management/plans/infrastructure-modularization-plan.md` — 建议：历史计划正文
- `docs/08-project-management/plans/maven-module-physical-nesting-plan.md` — 建议：历史计划正文
- `docs/08-project-management/plans/maven-parent-grouping-optimization-plan.md` — 建议：历史计划正文
- `docs/08-project-management/plans/monitoring-enhancement-plan.md` — 建议：历史计划正文
- `docs/08-project-management/plans/office-off-star-extraction-plan.md` — 建议：历史计划正文
- `docs/08-project-management/plans/office-platform-integration-plan.md` — 建议：历史计划正文
- `docs/08-project-management/plans/phase-2-3-implementation-plan.md` — 建议：历史计划正文
- `docs/08-project-management/plans/phase4-5-execution-plan.md` — 建议：历史计划正文
- `docs/08-project-management/plans/platform-closure-remediation-plan.md` — 建议：历史计划正文
- `docs/08-project-management/plans/retired-module-reference-cleanup-plan.md` — 建议：历史计划正文
- `docs/08-project-management/plans/service-enhancement-implementation-plan.md` — 建议：历史计划正文
- `docs/08-project-management/plans/storage-and-refactoring-implementation-plan.md` — 建议：历史计划正文
- `docs/08-project-management/plans/test-execution-and-report-plan.md` — 建议：历史计划正文
- `docs/08-project-management/plans/unified-auth-tenant-platform-remediation-plan.md` — 建议：历史计划正文
- `docs/08-project-management/plans/workflow-module-separation-plan.md` — 建议：历史计划正文
- `docs/08-project-management/plans/代码开发规范文档生成计划.md` — 建议：历史计划正文
- `docs/08-project-management/plans/全文搜索与运行日志查询实施方案.md` — 建议：历史计划正文
- `docs/08-project-management/plans/前端集成实施指南.md` — 建议：历史计划正文
- `docs/08-project-management/plans/后续实施快速指南.md` — 建议：历史计划正文
- `docs/08-project-management/plans/完整功能实施计划.md` — 建议：历史计划正文
- `docs/08-project-management/plans/缓存看门狗与工作流完善实施计划.md` — 建议：历史计划正文

### `docs/08-project-management/plans/ai-assisted`（12）

- `docs/08-project-management/plans/ai-assisted/COMPLETION_SUMMARY.md` — 建议：历史计划正文
- `docs/08-project-management/plans/ai-assisted/EXECUTION_SUMMARY.md` — 建议：历史计划正文
- `docs/08-project-management/plans/ai-assisted/PHASE2A_COMPLETION_REPORT.md` — 建议：历史计划正文
- `docs/08-project-management/plans/ai-assisted/PLAN_COMPLETION_REPORT.md` — 建议：历史计划正文
- `docs/08-project-management/plans/ai-assisted/SCAN_RESULTS_REPORT.md` — 建议：历史计划正文
- `docs/08-project-management/plans/ai-assisted/apex-projects-review-and-fix-plan.md` — 建议：历史计划正文
- `docs/08-project-management/plans/ai-assisted/backend-admin-apis-implementation-plan.md` — 建议：历史计划正文
- `docs/08-project-management/plans/ai-assisted/cache-optimization-solution-plan.md` — 建议：历史计划正文
- `docs/08-project-management/plans/ai-assisted/code-review-and-fix-plan.md` — 建议：历史计划正文
- `docs/08-project-management/plans/ai-assisted/comprehensive-review-and-standardization-plan.md` — 建议：历史计划正文
- `docs/08-project-management/plans/ai-assisted/javadoc-lombok-build-plan.md` — 建议：历史计划正文
- `docs/08-project-management/plans/ai-assisted/springdoc-annotation-plan.md` — 建议：历史计划正文

### `docs/08-project-management/plans/database`（2）

- `docs/08-project-management/plans/database/database-reorganization-plan.md` — 建议：历史计划正文
- `docs/08-project-management/plans/database/flyway-version-conflict-fix-plan.md` — 建议：历史计划正文

### `docs/08-project-management/quick-guides`（1）

- `docs/08-project-management/quick-guides/phase-2-3-quick-guide.md` — 建议：专题正文

### `docs/08-project-management/reports`（1）

- `docs/08-project-management/reports/module-architecture-review-report.md` — 建议：专题正文

### `docs/08-project-management/reports/storage-refactoring-tests`（1）

- `docs/08-project-management/reports/storage-refactoring-tests/test-report.md` — 建议：专题正文

### `docs/08-project-management/workspace-governance`（1）

- `docs/08-project-management/workspace-governance/20261015_143125_清理复核报告.md` — 建议：专题正文

### `docs/_archive`（19）

- `docs/_archive/INTEGRATION_SUMMARY.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/Kiro-AI署名修复报告.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/MERMAID_VERIFICATION_SUMMARY.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/MERMAID_VIEWER_VERIFICATION_REPORT.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/OAuth2_SSO_Keycloak_项目进度报告.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/PHASE2_FIX_EXAMPLES.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/REFACTORING_COMPLETION_REPORT.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/SSO_OAuth2_Keycloak_最终交付报告.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/SVG_OPTIMIZATION_REPORT.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/backend-completion-checklist.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/final-completion-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/implementation-summary.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/webflux-handler-summary.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/work-summary.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/后端完整实现代码-所有模块综合.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/文档非AI化修复完成总结.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/文档非AI化修复报告.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/认证授权体系文档总结报告.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/项目完成总结报告.md` — 建议：归档区：历史留档，不要求入口索引

### `docs/_archive/api`（2）

- `docs/_archive/api/data-management-api.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/api/route-mapping.md` — 建议：归档区：历史留档，不要求入口索引

### `docs/_archive/changes`（1）

- `docs/_archive/changes/concurrency-fix-summary.md` — 建议：归档区：历史留档，不要求入口索引

### `docs/_archive/integration`（3）

- `docs/_archive/integration/keycloak-integration-summary.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/integration/keycloak-optimization-completion-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/integration/port-conflict-resolution-report.md` — 建议：归档区：历史留档，不要求入口索引

### `docs/_archive/integration/oauth2/archive`（10）

- `docs/_archive/integration/oauth2/archive/development-summary-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/integration/oauth2/archive/implementation-notice.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/integration/oauth2/archive/pkce-e2e-test-script.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/integration/oauth2/archive/pkce-integration-test-report-v2.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/integration/oauth2/archive/short-term-tasks-completion-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/integration/oauth2/archive/test-api-fix-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/integration/oauth2/archive/test-execution-issue.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/integration/oauth2/archive/test-tasks-implementation-guide.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/integration/oauth2/archive/unit-test-fix-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/integration/oauth2/archive/unit-test-report.md` — 建议：归档区：历史留档，不要求入口索引

### `docs/_archive/operations`（10）

- `docs/_archive/operations/configuration.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/operations/deployment-checklist.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/operations/deployment.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/operations/environment-variables.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/operations/grafana-status-summary.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/operations/k8s-production-optimization.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/operations/monitoring-dashboard-configuration.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/operations/monitoring.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/operations/mysql-startup-guide.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/operations/sql-execution-guide.md` — 建议：归档区：历史留档，不要求入口索引

### `docs/_archive/optimization`（4）

- `docs/_archive/optimization/Lambda表达式最佳实践速查手册.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/optimization/代码优化方案-设计模式-SPI-Lambda表达式.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/optimization/优化实施清单.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/optimization/优化执行报告-2026-09-10.md` — 建议：归档区：历史留档，不要求入口索引

### `docs/_archive/postman`（1）

- `docs/_archive/postman/QUICKSTART.md` — 建议：归档区：历史留档，不要求入口索引

### `docs/_archive/ppt/架构图表新人培训`（3）

- `docs/_archive/ppt/架构图表新人培训/PPT大纲.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/ppt/架构图表新人培训/培训课件-Markdown版.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/ppt/架构图表新人培训/导出PDF指南.md` — 建议：归档区：历史留档，不要求入口索引

### `docs/_archive/reports`（52）

- `docs/_archive/reports/2026-08-xx-v1.3.0-commercial.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/2026-09-03-diagram-fix.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/2026-09-03-documentation-migration.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/2026-09-03-png-display-fix.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/2026-09-03-png-to-svg-migration.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/2026-09-03-vscode-png-fix.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/2026-09-05-batch-upgrade-complete-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/2026-09-05-cloud-native-icons-final-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/2026-09-05-cloud-native-icons-summary.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/2026-09-05-svg-display-fix-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/2026-09-05-svg-optimization-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/MAPSTRUCT_MIGRATION_SUMMARY.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/SVG图表验证与文档补充计划.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/backend-completion-summary.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/code-changes-summary.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/delivery-summary.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/doc-search-completion-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/doc-search-v2-completion-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/documentation-update-checklist.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/execution-summary.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/flyway-fix-notes.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/frontend-code-files.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/frontend-completion-summary.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/frontend-creation-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/lombok-refactoring-plan.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/long-term-optimization-completion-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/phase1-code-review-summary.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/phase3-svg-fix-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/pre-commit-checklist.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/project-final-summary.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/rate-limit-algorithms-test-report-20260912.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/short-term-goals-completion-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/svg-color-optimization-plan.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/svg-docs-alignment-plan.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/svg-gap-analysis.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/svg_documentation_enhancement_plan.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/svg_fix_and_monitoring_dashboard_plan.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/system-optimization-plan.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/task-completion-summary.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/tech-stack-inventory.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/test-execution-complete-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/test-execution-final-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/test-execution-final-success-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/test-execution-report-20260909.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/trae-documents-integration-completion-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/workflow-implementation.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/workflow-monitor-complete.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/workflow-progress.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/修复SVG图表显示问题及清理备份文件.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/文档图表优化_批次1完成报告_2026-09-11.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/文档图表优化方案_2026-09-11.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/链接检查与修复报告_2026-09-11.md` — 建议：归档区：历史留档，不要求入口索引

### `docs/_archive/reports/archive/project-deliveries`（8）

- `docs/_archive/reports/archive/project-deliveries/execution-status.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/archive/project-deliveries/final-delivery-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/archive/project-deliveries/final-execution-status.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/archive/project-deliveries/final-project-summary.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/archive/project-deliveries/optimization-completion.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/archive/project-deliveries/task-completion-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/archive/project-deliveries/task-final-completion.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/archive/project-deliveries/task-final-summary.md` — 建议：归档区：历史留档，不要求入口索引

### `docs/_archive/reports/archive/task-implementations`（6）

- `docs/_archive/reports/archive/task-implementations/follow-up-tasks-plan.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/archive/task-implementations/follow-up-tasks-summary.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/archive/task-implementations/frontend-button-permission-script.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/archive/task-implementations/frontend-button-permission-summary.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/archive/task-implementations/monitoring-enhancement-progress.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/archive/task-implementations/plan-a-execution-report.md` — 建议：归档区：历史留档，不要求入口索引

### `docs/_archive/reports/delivery`（5）

- `docs/_archive/reports/delivery/当前对话完成总结.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/delivery/最终交付总结_FINAL.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/delivery/最终交付报告.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/delivery/最终实施总结.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/delivery/项目交付总结.md` — 建议：归档区：历史留档，不要求入口索引

### `docs/_archive/reports/phases`（2）

- `docs/_archive/reports/phases/PHASE2A_SUCCESS.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/phases/README_PHASE2A.md` — 建议：归档区：历史留档，不要求入口索引

### `docs/_archive/reports/progress`（3）

- `docs/_archive/reports/progress/前端集成完成总结.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/progress/实施进度报告.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/reports/progress/最终进度报告.md` — 建议：归档区：历史留档，不要求入口索引

### `docs/_archive/root-reports`（8）

- `docs/_archive/root-reports/COMPLETE_TEST_REPORT.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/root-reports/FINAL_COMPLETE_REPORT.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/root-reports/FINAL_REPORT.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/root-reports/FRONTEND_TYPE_FIXES.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/root-reports/INTEGRATION_TEST_REPORT.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/root-reports/OPTIMIZATION_IMPLEMENTATION_REPORT.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/root-reports/OPTIMIZATION_PROGRESS_REPORT.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/root-reports/test-report-2026-09-16.md` — 建议：归档区：历史留档，不要求入口索引

### `docs/_archive/superpowers/plans`（2）

- `docs/_archive/superpowers/plans/2026-08-30-cache-high-load-enhancement.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/superpowers/plans/2026-08-30-mq-retry-configuration-hardening.md` — 建议：归档区：历史留档，不要求入口索引

### `docs/_archive/svg-fix-2025`（3）

- `docs/_archive/svg-fix-2025/README_SVG_FIX.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/svg-fix-2025/SVG_FIX_SUMMARY.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/svg-fix-2025/TEST_SVG_DISPLAY.md` — 建议：归档区：历史留档，不要求入口索引

### `docs/_archive/technical`（6）

- `docs/_archive/technical/autowired-springdoc-fix-summary.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/technical/springdoc_annotation_audit_plan.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/technical/test-completeness-plan.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/technical/业务日志统一查询补充说明.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/technical/构建工具不一致问题分析与修复方案.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/technical/编译错误修复补丁.md` — 建议：归档区：历史留档，不要求入口索引

### `docs/_archive/technical/tests`（1）

- `docs/_archive/technical/tests/PNG_TEST.md` — 建议：归档区：历史留档，不要求入口索引

### `docs/_archive/test-reports`（7）

- `docs/_archive/test-reports/job-pagination-test-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/test-reports/springdoc-coverage-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/test-reports/springdoc-task-completion-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/test-reports/task-completion-summary.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/test-reports/test-summary-20260915.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/test-reports/test-summary-20260916-pmd-enabled.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/test-reports/test-summary-20260916.md` — 建议：归档区：历史留档，不要求入口索引

### `docs/_archive/testing`（1）

- `docs/_archive/testing/keycloak-unit-tests-report.md` — 建议：归档区：历史留档，不要求入口索引

### `docs/_archive/trae-documents`（25）

- `docs/_archive/trae-documents/AGENTS文档更新总结.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-documents/SpringDoc注解完善工作总结.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-documents/Y2038-timestamp-analysis-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-documents/auth-center-implementation-progress.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-documents/auth-center-stage2-complete.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-documents/auth-center-stage3-completion-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-documents/auth-center-stage3-continuation.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-documents/backend-implementation-final-summary.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-documents/backend-implementation-plan.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-documents/backend-implementation-progress-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-documents/comprehensive-frontend-backend-improvement-plan.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-documents/comprehensive-improvement-final-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-documents/module-1-jwt-test-implementation-summary.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-documents/module-2-oauth2-verification-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-documents/module-3-datasource-implementation-summary.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-documents/phase3-completion-summary.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-documents/phase3-test-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-documents/phase5-mfa-completion-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-documents/project-cleanup-and-reorganization-plan.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-documents/unified-auth-center-design.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-documents/短期改进计划实施报告.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-documents/脚本权限问题修复总结.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-documents/脚本权限问题修复详细报告.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-documents/脚本迁移验证报告.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-documents/认证中心统一权限与项目文档标准化方案.md` — 建议：归档区：历史留档，不要求入口索引

### `docs/_archive/trae-reports/2026-09`（38）

- `docs/_archive/trae-reports/2026-09/FINAL-REPORT.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/FINAL_COMPLETION_SUMMARY.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/TASK-COMPLETION-REPORT.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/TASK-EXECUTION-SUMMARY.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/TEST-REPORT.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/change-summary.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/documentation-optimization-plan.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/documents-integration-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/final-completion-report-v2.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/final-completion-report-v3.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/final-completion-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/final-integration-completion-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/frontend-code-cleanup-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/frontend-implementation-gap-analysis.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/frontend-integration-completion-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/frontend-integration-final-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/frontend-integration-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/frontend-integration-summary.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/frontend-message-approval-completion-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/implementation-consistency-fix-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/integration-verification-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/office-integration-progress.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/office-platform-completion-summary.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/office-platform-delivery-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/p0-fix-completion-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/phase-2-3-backend-completion-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/phase-2-3-final-summary.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/phase-2-3-implementation-summary.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/phase-2-progress-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/phase1-issues-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/phase4-assessment-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/project-final-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/project-reorganization-completion-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/project-summary.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/scripts-review-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/short-term-optimization-summary.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/svg-deep-verification-report.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/trae-reports/2026-09/svg-diagrams-review-report.md` — 建议：归档区：历史留档，不要求入口索引

### `docs/_archive/troubleshooting`（2）

- `docs/_archive/troubleshooting/集成测试SqlSessionTemplate缺失问题排查报告.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/_archive/troubleshooting/集成测试修复工作总结报告-2026-09-10.md` — 建议：归档区：历史留档，不要求入口索引

### `docs/api`（1）

- `docs/api/job-pagination-api.md` — 建议：专题正文

### `docs/architecture`（27）

- `docs/architecture/01-logical-architecture-business-value.md` — 建议：架构说明
- `docs/architecture/01-logical-architecture-decision.md` — 建议：架构说明
- `docs/architecture/01-logical-architecture-detail.md` — 建议：架构说明
- `docs/architecture/01-logical-architecture-quick-ref.md` — 建议：架构说明
- `docs/architecture/02-physical-architecture-business-value.md` — 建议：架构说明
- `docs/architecture/02-physical-architecture-decision.md` — 建议：架构说明
- `docs/architecture/02-physical-architecture-quick-ref.md` — 建议：架构说明
- `docs/architecture/03-technical-architecture-business-value.md` — 建议：架构说明
- `docs/architecture/03-technical-architecture-decision.md` — 建议：架构说明
- `docs/architecture/03-technical-architecture-detail.md` — 建议：架构说明
- `docs/architecture/03-technical-architecture-quick-ref.md` — 建议：架构说明
- `docs/architecture/backend-technology-stack.md` — 建议：架构说明
- `docs/architecture/business-flows.md` — 建议：架构说明
- `docs/architecture/core-tech-diagrams-index.md` — 建议：架构说明
- `docs/architecture/data-flow-architecture.md` — 建议：架构说明
- `docs/architecture/deployment-architecture.md` — 建议：架构说明
- `docs/architecture/frontend-technology-stack.md` — 建议：架构说明
- `docs/architecture/infrastructure-components.md` — 建议：架构说明
- `docs/architecture/mq-architecture.md` — 建议：架构说明
- `docs/architecture/multi-tenant-isolation-architecture.md` — 建议：架构说明
- `docs/architecture/network-architecture.md` — 建议：架构说明
- `docs/architecture/reactive-programming-guide.md` — 建议：架构说明
- `docs/architecture/redis-watchdog-mechanism.md` — 建议：架构说明
- `docs/architecture/service-governance-implementation.md` — 建议：架构说明
- `docs/architecture/service-governance-solution.md` — 建议：架构说明
- `docs/architecture/strategy-pattern-guide.md` — 建议：架构说明
- `docs/architecture/system-architecture.md` — 建议：架构说明

### `docs/architecture/_templates`（4）

- `docs/architecture/_templates/business-value-template.md` — 建议：架构说明
- `docs/architecture/_templates/decision-doc-template.md` — 建议：架构说明
- `docs/architecture/_templates/detail-doc-template.md` — 建议：架构说明
- `docs/architecture/_templates/quick-ref-template.md` — 建议：架构说明

### `docs/architecture/advanced-features`（8）

- `docs/architecture/advanced-features/auto-retry-strategy.md` — 建议：架构说明
- `docs/architecture/advanced-features/circuit-breaker-diagrams-guide.md` — 建议：架构说明
- `docs/architecture/advanced-features/circuit-breaker-rate-limit-guide.md` — 建议：架构说明
- `docs/architecture/advanced-features/multi-level-cache-architecture.md` — 建议：架构说明
- `docs/architecture/advanced-features/multi-level-cache-complete-guide.md` — 建议：架构说明
- `docs/architecture/advanced-features/rate-limit-algorithms-integration.md` — 建议：架构说明
- `docs/architecture/advanced-features/security-protection-system.md` — 建议：架构说明
- `docs/architecture/advanced-features/sentinel-rate-limit-circuit-breaker.md` — 建议：架构说明

### `docs/archived/ai-sessions/zcode-plans`（13）

- `docs/archived/ai-sessions/zcode-plans/plan-sess_0551c3f3-5890-4584-b1f1-793d4b312b6d.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/archived/ai-sessions/zcode-plans/plan-sess_1d68e5f7-c904-46d9-875f-326c816f0c14.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/archived/ai-sessions/zcode-plans/plan-sess_279431bd-6b46-4313-b621-7f51a998ab72.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/archived/ai-sessions/zcode-plans/plan-sess_3e256dff-3702-4c92-a667-5bf731a2f9e0.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/archived/ai-sessions/zcode-plans/plan-sess_5455f99d-6514-4012-800a-ca054165487a.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/archived/ai-sessions/zcode-plans/plan-sess_6ac82648-dd60-4a4a-86a1-e2b9fe83b3b9.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/archived/ai-sessions/zcode-plans/plan-sess_6bc68901-0108-4fd5-89d7-04318ef7fd43.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/archived/ai-sessions/zcode-plans/plan-sess_8cf59fd0-7867-42c8-a39a-f7864a033663.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/archived/ai-sessions/zcode-plans/plan-sess_973a93a6-045c-49ec-a6cc-43725c562033.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/archived/ai-sessions/zcode-plans/plan-sess_fb54f920-bfdb-42e1-8975-c9fab04e431a.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/archived/ai-sessions/zcode-plans/plan-sess_feb20185-f229-42fb-980b-c95106fc12f6.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/archived/ai-sessions/zcode-plans/plan-session_0646fc17bc4aa7adfc42b2fe7f64d5e9.md` — 建议：归档区：历史留档，不要求入口索引
- `docs/archived/ai-sessions/zcode-plans/plan-session_ce5d418af532ac5977743f5921c5c92d.md` — 建议：归档区：历史留档，不要求入口索引

### `docs/cache`（1）

- `docs/cache/后端完整实现代码-所有模块综合.md` — 建议：专题正文

### `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/.github/ISSUE_TEMPLATE`（1）

- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/.github/ISSUE_TEMPLATE/request-new-icon-design.md` — 建议：第三方图标源（上游资产）

### `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs`（80）

- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/admissions-controller.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/api-server.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/argo.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/certificate-authority.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/client-certificate.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/cloud-controller-manager.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/cluster-autoscaler.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/cluster-role-binding.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/cluster-role.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/cluster.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/config-map.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/container-close-look.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/container-network-interface.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/container-runtime-interface-container-d.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/container-runtime-interface-cri-o.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/container-runtime.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/container-storage-interface.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/container.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/control-plane-node.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/controller-manager.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/core-dns.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/cron-job.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/custom-resource-definition.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/deamon,.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/deamon-set.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/deployment.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/endpoint-slice.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/endpoint.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/ephemeral-storage.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/etcd.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/flux.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/gateway-api.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/generic-manifest-file.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/group.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/headless-service.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/helm.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/horizontal-pod-autoscaler.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/ingress.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/ip-tables.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/ipvs.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/job.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/kube-ctl.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/kubeadm.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/kubeconfig.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/kubelet.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/kustomize.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/limit-range.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/liveness-probe.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/namespace.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/network-policy.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/nginx-ingress-controller.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/operator.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/persistent-volume-claim.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/persistent-volume.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/pod-spec-file.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/pod.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/pods.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/projected-volume.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/proxy.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/readiness-probe.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/replica-set.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/requests.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/resource-quotas.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/role-binding.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/role.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/rollout.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/scheduler.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/secrets.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/server-certificate.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/service-account.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/service.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/startup-probe.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/stateful-set.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/storage-class.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/taint.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/toleration.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/user.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/vertical-pod-autoscaler.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/volume-snapshot.md` — 建议：第三方图标源（上游资产）
- `docs/diagrams/_icon_sources/Kubernetes-Architecture-Icons-main/docs/worker-node.md` — 建议：第三方图标源（上游资产）

### `docs/fix-reports`（1）

- `docs/fix-reports/datascopeservicetest-fix-report.md` — 建议：专题正文

### `docs/migration`（3）

- `docs/migration/MIGRATION_FIX_COMPLETE.md` — 建议：专题正文
- `docs/migration/MIGRATION_FIX_SUMMARY.md` — 建议：专题正文
- `docs/migration/flyway-migration-fix-report-20240922.md` — 建议：专题正文

### `docs/plans/archived-ai-sessions`（4）

- `docs/plans/archived-ai-sessions/apex-scaffold-docs-and-diagrams-alignment.md` — 建议：会话/计划正文
- `docs/plans/archived-ai-sessions/gateway-resilience-enhancement-plan.md` — 建议：会话/计划正文
- `docs/plans/archived-ai-sessions/microservice-migration-execution-plan.md` — 建议：会话/计划正文
- `docs/plans/archived-ai-sessions/microservice-phase5-6-extraction-design.md` — 建议：会话/计划正文

### `docs/records`（3）

- `docs/records/2026-11-16-github-remote-onboarding.md` — 建议：专题正文
- `docs/records/2026-11-16-governance-alignment-remediation.md` — 建议：专题正文
- `docs/records/2026-11-16-tech-debt-be001-be017.md` — 建议：专题正文

### `docs/reference/ai-knowledge`（3）

- `docs/reference/ai-knowledge/07-change-memory.md` — 建议：专题正文
- `docs/reference/ai-knowledge/08-agent-tooling.md` — 建议：专题正文
- `docs/reference/ai-knowledge/PROVENANCE.md` — 建议：专题正文

### `docs/reports/reviews`（2）

- `docs/reports/reviews/QUICK_REFERENCE.md` — 建议：专题正文
- `docs/reports/reviews/security-fixes-summary.md` — 建议：专题正文

### `docs/specs/ai-agents`（5）

- `docs/specs/ai-agents/r1-task-planner.md` — 建议：专题正文
- `docs/specs/ai-agents/r2-backend-implementer.md` — 建议：专题正文
- `docs/specs/ai-agents/r3-contract-guard.md` — 建议：专题正文
- `docs/specs/ai-agents/r4-test-runner.md` — 建议：专题正文
- `docs/specs/ai-agents/r5-reviewer.md` — 建议：专题正文

# Apex Scaffold 项目清理与文档重组计划

**计划编号**: PLAN-2026-0920-001  
**创建日期**: 2026-09-20  
**目标**: 清理冗余文件，整合文档，规范目录结构，修复引用链接

---

## 一、当前状态分析

### 1.1 发现的问题

#### 问题 1: 存在 80 个 .java.bak 备份文件
**位置**: 
- `apex-application/src/main/java/**/*.java.bak` (48个)
- `apex-infrastructure/src/main/java/**/*.java.bak` (32个)

**影响**: 占用磁盘空间，污染代码库，可能导致误操作

#### 问题 2: .trae 目录下有未整合的文档
**位置**: `.trae/documents/` 和 `.trae/` 根目录

**文档清单**:
- **计划类** (9个):
  - apex-projects-review-and-fix-plan.md
  - apex-scaffold-test-execution-and-report-plan.md
  - backend-admin-apis-implementation-plan.md
  - cache-optimization-solution-plan.md
  - comprehensive_review_and_standardization_plan.md
  - project-documentation-and-configuration-review-plan.md
  - project-documentation-and-database-reorganization-plan.md

- **报告类** (10个):
  - JavaDoc注释工作总结与待完善清单.md
  - Mapper接口JavaDoc补充-执行报告.md
  - 代码注释与Schema注解完善-最终报告.md
  - 代码注释与SpringDoc文档完善-执行报告.md
  - 代码注释与SpringDoc文档完善计划.md
  - 代码注释完善-最终执行报告.md
  - 代码注释补充-增量报告-20260920.md
  - 消息中间件与DDD架构优化方案.md
  - 消息中间件优化实施总结报告.md
  - 消息中间件优化最终实施报告.md
  - apex-interfaces-lombok-修复报告/ (目录)

- **临时文档** (2个):
  - .trae/任务完成报告.md
  - .trae/优化总结.md

#### 问题 3: scripts 目录组织混乱
**当前结构**: 部分脚本直接在 scripts/ 根目录，部分在子目录

**不符合规范**:
- 缺少 `build/`、`dev/`、`ops/`、`utils/` 等一级分类
- 子目录命名不统一（如 grafana/ 应该在 ops/monitoring/ 下）

#### 问题 4: 大量过时和重复的报告文件
**位置**: `docs/reports/`

**问题报告** (含日期的临时报告，共18个):
- cleanup-execution-report-2026-09-17.md
- cleanup-plan-2026-09-17.md
- final-completion-report-2026-09-18.md
- final-link-check-report-2026-09-18.md
- final_verification_report_2026-09-18.md
- flyway_migration_execution_report_2026-09-17.md
- link-fix-final-report-2026-09-18.md
- link-fix-project-summary-2026-09-18.md
- link-fix-report-2026-09-18.md
- link-fix-report-phase2-2026-09-18.md
- link-validation-report-2026-09-17.md
- project-completion-summary-2026-09-17.md
- svg-update-completion-report-2026-09-17.md
- verification_execution_report_2026-09-17.md
- code_review_and_standardization_report_2026-09-17.md
- code_database_synchronization_report_2026-09-18.md

**保留的报告** (有长期价值，4个):
- svg-code-alignment-audit-report.md
- javadoc-补充任务总结报告-20260913.md

#### 问题 5: 文档中存在指向 .trae 的链接
**影响文件** (4个):
- docs/reports/link-fix-final-report-2026-09-18.md
- docs/_archive/reports/2026-09-03-documentation-migration.md
- docs/reports/code_review_and_standardization_report_2026-09-17.md
- docs/04-development/mvnd-build-standard.md

#### 问题 6: 存在冗余的目录结构
**问题目录**:
- `docs/08-project-management/quick-guides/` - 只有2个文件
- `docs/08-project-management/checklists/` - 只有1个文件
- `docs/08-project-management/plans/ai-assisted/` - 可以合并到 plans/

### 1.2 清理统计

| 类型 | 数量 | 操作 |
|-----|------|-----|
| .java.bak 文件 | 80 | 全部删除 |
| .trae 文档待整合 | 19 | 整合到 docs/ |
| 过时报告 | 16 | 归档或删除 |
| 冗余目录 | 3 | 合并或删除 |
| 断裂链接 | 4+ | 修复 |

---

## 二、整理规范与原则

### 2.1 遵循的规范文档
- [AGENTS.md](../../../AGENTS.md) - 编码和提交约定
- [docs/DOCS_STRUCTURE_STANDARD.md](../../DOCS_STRUCTURE_STANDARD.md) - 文档结构规范

### 2.2 核心原则
1. **用户导向**: 按用户角色和使用场景组织
2. **路径简短**: 最多3级目录深度
3. **职责单一**: 每个目录只承担一种职责
4. **版本控制**: 只保留当前有效文档
5. **可发现性**: 每个目录都有 README.md

### 2.3 禁止的反模式
- ❌ 按时间组织 (如 reports/2026-09/)
- ❌ 按作者组织 (如 reports/kiro/)
- ❌ 按状态组织 (如 drafts/、final/)
- ❌ 重复内容
- ❌ 临时文件进入版本控制

---

## 三、执行计划

### 阶段 1: 删除 .java.bak 文件 ✓

**操作**: 删除所有 80 个 .java.bak 文件

**命令**:
```bash
find /Users/work/apex-scaffold -name "*.java.bak" -type f -delete
```

**验证**:
```bash
find /Users/work/apex-scaffold -name "*.java.bak" -type f | wc -l
# 应返回 0
```

---

### 阶段 2: 整合 .trae 文档到 docs/

#### 2.1 计划类文档迁移

**目标目录**: `docs/08-project-management/plans/ai-assisted/`

**迁移清单**:
| 源文件 | 目标位置 | 操作 |
|--------|---------|------|
| apex-projects-review-and-fix-plan.md | ai-assisted/code-review-and-fix-plan.md | 已存在，检查是否重复 |
| apex-scaffold-test-execution-and-report-plan.md | plans/test-execution-and-report-plan.md | 移动 |
| backend-admin-apis-implementation-plan.md | ai-assisted/backend-admin-apis-implementation-plan.md | 已存在，检查内容 |
| cache-optimization-solution-plan.md | ai-assisted/cache-optimization-solution-plan.md | 移动（与现有缓存文档关联） |
| comprehensive_review_and_standardization_plan.md | ai-assisted/comprehensive_review_and_standardization_plan.md | 移动 |
| project-documentation-and-configuration-review-plan.md | plans/documentation-review-plan.md | 移动 |
| project-documentation-and-database-reorganization-plan.md | plans/database/database-reorganization-plan.md | 移动 |

#### 2.2 报告类文档处理

**JavaDoc 和代码注释相关** → `docs/reports/`

| 源文件 | 处理方式 |
|--------|---------|
| JavaDoc注释工作总结与待完善清单.md | 移动到 reports/javadoc-work-summary.md |
| Mapper接口JavaDoc补充-执行报告.md | 合并到 javadoc-work-summary.md，删除原文件 |
| 代码注释与Schema注解完善-最终报告.md | 合并到 javadoc-work-summary.md |
| 代码注释与SpringDoc文档完善-执行报告.md | 合并到 javadoc-work-summary.md |
| 代码注释完善-最终执行报告.md | 合并到 javadoc-work-summary.md |
| 代码注释补充-增量报告-20260920.md | 合并到 javadoc-work-summary.md |

**消息中间件相关** → `docs/reports/`

| 源文件 | 处理方式 |
|--------|---------|
| 消息中间件与DDD架构优化方案.md | 移动到 reports/message-middleware-optimization.md |
| 消息中间件优化实施总结报告.md | 合并到 message-middleware-optimization.md |
| 消息中间件优化最终实施报告.md | 合并到 message-middleware-optimization.md |

**Lombok 修复报告** → `docs/reports/`

| 源文件 | 处理方式 |
|--------|---------|
| apex-interfaces-lombok-修复报告/ | 移动到 reports/lombok-fix-report/ |

#### 2.3 临时文档处理

| 源文件 | 处理方式 |
|--------|---------|
| .trae/任务完成报告.md | 检查内容，合并到相关报告后删除 |
| .trae/优化总结.md | 检查内容，合并到相关报告后删除 |

---

### 阶段 3: 重新组织 scripts 目录

#### 3.1 目标结构

```
scripts/
├── README.md                        # 脚本索引
├── build/                           # 构建相关
│   ├── init-build-env.sh            # (待创建或移动)
│   ├── build.sh                     # (待创建)
│   └── clean.sh                     # (待创建)
├── dev/                             # 开发辅助
│   ├── codegen/                     # 代码生成 ← 现有 codegen/
│   ├── format/                      # 代码格式化 ← 新目录
│   │   ├── batch-add-javadoc.sh     ← 移动
│   │   ├── batch-add-lombok.sh      ← 移动
│   │   └── spotless-apply.sh        ← (待创建或重命名)
│   └── check/                       # 代码检查 ← 新目录
│       ├── scan-javadoc-issues.sh   ← 移动
│       ├── scan-lombok-issues.sh    ← 移动
│       ├── check-port-conflicts.sh  ← 移动
│       └── check-springdoc-annotations.sh ← 移动
├── test/                            # 测试相关
│   ├── run-unit-tests.sh            # (待创建)
│   ├── run-integration-tests.sh     # (待创建)
│   ├── jvm-diagnostics-test.sh      ← 现有
│   └── generate-test-report.sh      ← 现有
├── deploy/                          # 部署相关 ← 现有
│   ├── docker/                      # (新子目录)
│   │   ├── pull-infra-images.sh     ← 移动
│   │   └── deploy-monitoring.sh     ← 移动
│   ├── k8s/                         # (待创建)
│   └── service/                     # (新子目录)
│       ├── app.sh                   ← 移动
│       ├── start.sh                 ← 移动
│       └── stop.sh                  ← 移动
├── ops/                             # 运维相关 ← 新一级目录
│   ├── backup/                      # 备份恢复 ← 现有
│   ├── monitoring/                  # 监控 ← 新子目录
│   │   ├── start-monitoring.sh      ← 移动
│   │   └── grafana/                 ← 移动整个目录
│   └── diagnostics/                 # 诊断 ← 现有
├── data/                            # 数据相关 ← 现有
│   ├── datasource/                  # 数据源 ← 现有
│   └── init_workflow_test_data*.sql ← 现有
├── ci/                              # CI/CD ← 现有
└── utils/                           # 通用工具 ← 新目录
    ├── export-api-docs.sh           ← 移动
    ├── extract-env-vars.sh          ← 移动
    ├── batch-verify-svg.sh          ← 移动
    └── verify-reactor-metrics.sh    ← 移动（如果存在）
```

#### 3.2 需要移动的脚本

| 当前位置 | 目标位置 | 说明 |
|---------|---------|------|
| scripts/batch-add-javadoc.sh | scripts/dev/format/ | 代码格式化工具 |
| scripts/batch-add-lombok.sh | scripts/dev/format/ | 代码格式化工具 |
| scripts/scan-javadoc-issues.sh | scripts/dev/check/ | 代码检查工具 |
| scripts/scan-lombok-issues.sh | scripts/dev/check/ | 代码检查工具 |
| scripts/check-port-conflicts.sh | scripts/dev/check/ | 开发环境检查 |
| scripts/check-springdoc-annotations.sh | scripts/dev/check/ | 代码检查工具 |
| scripts/deploy/pull-infra-images.sh | scripts/deploy/docker/ | Docker 部署 |
| scripts/deploy/deploy-monitoring.sh | scripts/deploy/docker/ | Docker 部署 |
| scripts/deploy/app.sh | scripts/deploy/service/ | 服务管理 |
| scripts/deploy/start.sh | scripts/deploy/service/ | 服务管理 |
| scripts/deploy/stop.sh | scripts/deploy/service/ | 服务管理 |
| scripts/start-monitoring.sh | scripts/ops/monitoring/ | 监控运维 |
| scripts/grafana/ | scripts/ops/monitoring/grafana/ | 监控运维 |
| scripts/export-api-docs.sh | scripts/utils/ | 通用工具 |
| scripts/extract-env-vars.sh | scripts/utils/ | 通用工具 |
| scripts/batch-verify-svg.sh | scripts/utils/ | 通用工具 |

#### 3.3 需要删除的脚本

| 脚本文件 | 删除理由 |
|---------|---------|
| scripts/batch-fix-pmd-auto.py | 临时脚本，已完成使命 |
| scripts/batch-fix-pmd.py | 临时脚本，已完成使命 |
| scripts/clean-duplicate-javadoc.py | 临时脚本，已完成使命 |
| scripts/clean-duplicate-javadoc.sh | 临时脚本，已完成使命 |
| scripts/fix-pmd-violations.sh | 临时脚本，已完成使命 |
| scripts/check_javadoc.sh | 被 scan-javadoc-issues.sh 替代 |

#### 3.4 需要保留但重新分类的目录

| 当前目录 | 处理方式 |
|---------|---------|
| scripts/bench/ | 保留，性能测试 |
| scripts/init/ | 保留，初始化脚本 |
| scripts/keycloak/ | 保留，Keycloak 相关 |
| scripts/license/ | 保留，授权相关 |
| scripts/office/ | 保留，Office 集成 |
| scripts/performance/ | 保留，性能测试 |

---

### 阶段 4: 清理过时和重复文档

#### 4.1 删除过时的报告文件

**删除清单** (docs/reports/ 中的临时报告):
```
docs/reports/cleanup-execution-report-2026-09-17.md
docs/reports/cleanup-plan-2026-09-17.md
docs/reports/final-completion-report-2026-09-18.md
docs/reports/final-link-check-report-2026-09-18.md
docs/reports/final_verification_report_2026-09-18.md
docs/reports/flyway_migration_execution_report_2026-09-17.md
docs/reports/link-fix-final-report-2026-09-18.md
docs/reports/link-fix-project-summary-2026-09-18.md
docs/reports/link-fix-report-2026-09-18.md
docs/reports/link-fix-report-phase2-2026-09-18.md
docs/reports/link-validation-report-2026-09-17.md
docs/reports/project-completion-summary-2026-09-17.md
docs/reports/svg-update-completion-report-2026-09-17.md
docs/reports/verification_execution_report_2026-09-17.md
docs/reports/code_review_and_standardization_report_2026-09-17.md
docs/reports/code_database_synchronization_report_2026-09-18.md
```

**删除理由**: 这些是临时的执行报告，价值周期短，3天后已无参考意义

#### 4.2 保留的报告文件

**保留清单** (docs/reports/ 中有长期价值的):
```
docs/reports/svg-code-alignment-audit-report.md         # SVG 审计报告，有参考价值
docs/reports/javadoc-补充任务总结报告-20260913.md      # JavaDoc 总结，有参考价值
```

**新增报告**:
```
docs/reports/javadoc-work-summary.md                    # 合并后的 JavaDoc 工作总结
docs/reports/message-middleware-optimization.md         # 合并后的消息中间件优化报告
docs/reports/lombok-fix-report/                        # Lombok 修复报告目录
```

#### 4.3 合并冗余的项目管理目录

**操作**:
1. 将 `docs/08-project-management/quick-guides/` 中的文件移动到 `docs/08-project-management/`
2. 将 `docs/08-project-management/checklists/` 中的文件移动到 `docs/08-project-management/`
3. 删除空的 `quick-guides/` 和 `checklists/` 目录
4. 将 `docs/08-project-management/plans/ai-assisted/` 保留，因为有明确分类意义

---

### 阶段 5: 修复文档中的引用链接

#### 5.1 修复指向 .trae 的链接

**影响文件**:
1. `docs/reports/link-fix-final-report-2026-09-18.md` → 删除（过期报告）
2. `docs/_archive/reports/2026-09-03-documentation-migration.md` → 保留在归档，不修复
3. `docs/reports/code_review_and_standardization_report_2026-09-17.md` → 删除（过期报告）
4. `docs/04-development/mvnd-build-standard.md` → 需要修复

#### 5.2 修复因文件移动导致的断链

**策略**:
1. 阶段 2 和 3 完成后，运行链接检查脚本
2. 使用 `scripts/ci/check-doc-links.sh` 检查所有链接
3. 逐个修复断裂的链接

**命令**:
```bash
cd /Users/work/apex-scaffold
./scripts/ci/check-doc-links.sh
```

---

## 四、验证与测试

### 4.1 验证清单

- [ ] 所有 .java.bak 文件已删除
- [ ] .trae/documents/ 目录为空或只包含当前计划文档
- [ ] docs/ 目录符合 DOCS_STRUCTURE_STANDARD.md 规范
- [ ] scripts/ 目录符合重新规划的结构
- [ ] 所有移动的文件都有对应的 git mv 操作
- [ ] docs/README.md 索引已更新
- [ ] scripts/README.md 索引已创建或更新
- [ ] 所有文档链接验证通过
- [ ] AGENTS.md 中的文档和脚本路径引用已更新

### 4.2 验证命令

```bash
# 1. 检查 .java.bak 文件
find /Users/work/apex-scaffold -name "*.java.bak" -type f

# 2. 检查文档链接
cd /Users/work/apex-scaffold
./scripts/ci/check-doc-links.sh

# 3. 检查 git 状态
git status

# 4. 统计文档数量
find docs/ -name "*.md" -type f | wc -l

# 5. 统计脚本数量
find scripts/ -name "*.sh" -type f | wc -l
```

---

## 五、实施时间表

| 阶段 | 任务 | 预计时间 | 依赖 |
|-----|------|---------|------|
| 1 | 删除 .java.bak 文件 | 5分钟 | 无 |
| 2 | 整合 .trae 文档 | 30分钟 | 阶段1 |
| 3 | 重组 scripts 目录 | 45分钟 | 无 |
| 4 | 清理过时文档 | 20分钟 | 阶段2 |
| 5 | 修复引用链接 | 30分钟 | 阶段2,3,4 |
| 验证 | 全面验证 | 20分钟 | 所有阶段 |
| **总计** | | **约2.5小时** | |

---

## 六、风险与注意事项

### 6.1 风险识别

| 风险 | 影响 | 缓解措施 |
|-----|------|---------|
| 误删有用文件 | 高 | 先备份，使用 git 版本控制 |
| 链接断裂 | 中 | 完成后运行链接检查脚本 |
| 目录结构不合理 | 中 | 严格遵循 DOCS_STRUCTURE_STANDARD.md |
| 合并文档丢失信息 | 中 | 仔细检查合并内容，保留关键信息 |

### 6.2 注意事项

1. **备份**: 执行前创建完整备份
2. **Git 操作**: 使用 `git mv` 而不是直接 `mv`，保留文件历史
3. **增量提交**: 每个阶段独立提交，便于回滚
4. **文档索引**: 移动文件后立即更新相关 README
5. **团队通知**: 完成后通知团队成员文档位置变更

---

## 七、执行后的目标状态

### 7.1 清理成果

- ✅ 0 个 .java.bak 文件
- ✅ .trae/ 目录只包含 TRAE 工作目录配置
- ✅ docs/ 目录完全符合文档结构规范
- ✅ scripts/ 目录按功能清晰分类
- ✅ 所有文档链接有效
- ✅ 无重复、过时或冗余文件

### 7.2 文档组织

```
docs/
├── 00-getting-started/        # 快速入门
├── 01-guides/                 # 使用指南
├── 02-architecture/           # 架构设计
├── 03-api/                    # API 文档
├── 04-development/            # 开发规范
├── 05-operations/             # 运维部署
├── 06-testing/                # 测试文档
├── 07-commercial/             # 商业化
├── 08-project-management/     # 项目管理（扁平化）
│   ├── features/
│   └── plans/
│       ├── ai-assisted/       # AI 辅助的计划
│       └── database/          # 数据库相关计划
├── diagrams/                  # 架构图表
├── reports/                   # 技术报告（只保留有价值的）
└── _archive/                  # 归档（保留历史参考）
```

### 7.3 脚本组织

```
scripts/
├── README.md                  # 脚本索引
├── build/                     # 构建
├── dev/                       # 开发辅助
│   ├── codegen/
│   ├── format/
│   └── check/
├── test/                      # 测试
├── deploy/                    # 部署
│   ├── docker/
│   ├── k8s/
│   └── service/
├── ops/                       # 运维
│   ├── backup/
│   ├── monitoring/
│   │   └── grafana/
│   └── diagnostics/
├── data/                      # 数据
├── ci/                        # CI/CD
└── utils/                     # 工具
```

---

## 八、后续维护

### 8.1 定期审查

- **每月**: 清理临时文件和重复文件
- **每季度**: 审查 reports/ 目录，归档过时报告
- **每年**: 清理 _archive/ 目录，删除超过1年的归档

### 8.2 新文档添加流程

1. 确定文档类别和目标受众
2. 放入对应目录，使用 kebab-case 命名
3. 在目录 README.md 中添加索引
4. 在 docs/README.md 中更新总索引（如果重要）

---

**计划状态**: ⏳ 待执行  
**预计完成时间**: 2.5 小时  
**负责人**: AI Assistant  
**审批人**: 项目负责人

© 2026 Apex Team · 保持项目整洁，提升开发效率

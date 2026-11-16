# Apex Scaffold 文档/脚本/测试对齐与图表体系完善计划

## 1. 摘要 Summary

对项目执行两阶段任务：
- **Task 1（一致性对齐）**：以「最新代码实现 + CHANGELOG 版本演进（1.3.1）为准」为锚点，对齐项目全部文档、脚本、配置文件与测试报告中的版本号与被代码验证的事实（Flyway 迁移实际 V58；脚本统一使用 `scripts/lib/common.sh` 规范），修正存在缺陷的测试报告脚本，并对核心模块执行定向测试以生成新版测试报告。
- **Task 2（图表体系）**：基于最新代码，新增/优化/补齐专业完整的系统架构图、业务流程图、时序图、UML 类图、模块结构图、E-R 图等，交付形式为「可渲染 SVG + 对应 .mmd 源码」双套件，并同步更新 3 份图表索引（`docs/diagrams/README.md`、`docs/diagrams/sequence/README.md`、`docs/02-architecture/architecture-index.md`）及修复其中失效/不一致的链接。

产出：所有图表最终以本地可渲染的 SVG 呈现，不依赖本机未安装的 `mmdc` CLI（通过生成 Mermaid `.mmd` 源文件 + 用等价的 SVG 成品交付；若需重新渲染可在有 mmdc 的环境执行，或直接使用已提交的 SVG）。

---

## 2. 现状分析 Current State Analysis

### 2.1 版本号不一致（Task 1 核心）

| 地点 | 当前值 | 事实 |
|---|---|---|
| `pom.xml`（根）第 41 行 `<version>` | **1.2.0** | 需升级到 1.3.1 |
| `apex-auth-center/pom.xml` 第 24 行 parent `<version>` | 1.2.0 | 需同步 1.3.1 |
| `README.md` 第 5 行 badge | `Apex_Scaffold-v1.2.0` | 需改 v1.3.1 |
| `CHANGELOG.md` 顶部条目 | **[1.3.1] 2026-09-04** | 版本真相源，作为升级依据 |
| `scripts/test/generate-test-report.sh` 第 46 行 | `**项目版本**: 1.2.0`（硬编码） | 需改 1.3.1（建议动态读取） |
| `k8s/deployment.yaml`（75/91）、`k8s/README.md`（68） | v1.2.0 | 需同步 v1.3.1 |
| `docs/05-operations/deployment.md`（369/371/776/780/782） | :1.2.0 镜像名 | 需同步 1.3.1 |
| `docs/00-getting-started/quickstart.md`（185/384/387）、`examples-summary.md` | 1.2.0 | 需同步 |
| `docs/06-testing/TEST_REPORT.md`、`api-test-report.md`、`api-parameter-type-audit.md`、`api-test-plan.md` | 版本:1.2.0 | 需同步 |

> 说明：`docs/08-project-management/plans/*`、`docs/_archive/*` 等历史规划/归档文档中的 1.2.0 属于**当时快照**，不属于「当前文档」，**不改**，避免污染历史记录。

### 2.2 脚本质量问题（Task 1）

- `scripts/test/generate-test-report.sh` 存在：硬编码版本、stale「已知问题」小节（疑似早已修复的 Lombok @Slf4j 编译报错：RedisDataCleanupRunner / RedissonConfig / CacheConsistencyService / MultiLevelCacheService）、模块清单缺失 `apex-infrastructure` 与 `apex-auth-center`、未引入 `scripts/lib/common.sh` 规范（`safe_find`/`safe_count`/`safe_gt`/`log_success`/`log_failure`）。
- `scripts/ci/check-doc-consistency.sh` 同样未使用 `scripts/lib/common.sh`，需审计并规范化。

### 2.3 Flyway 事实 vs 记录（Task 2 数据来源 + Task 1 修正）

- 迁移实际分支：
  - `mysql/`：V1–V58（完整 SQL）
  - `postgresql/`：V1–V50（真实 SQL）+ V51–V58 为 `mysql_alignment_placeholder.sql`（占位）
- 实际最高版本号 = **58**（存在 V58__add_token_revocation_tracking.sql 等）。
- `docs/diagrams/README.md` 声称「Flyway V1-V26」——**过期**，需按实际 V58 修正。历史归档（`docs/_archive/reports/flyway-*`）不改。

### 2.4 配置 / 环境（已确认）

- `.env.example` 大写环境变量 = **194 个**（满足 check-doc-consistency.sh 的 ≥170 阈值，无需处理）。
- 构建工具：`mvnd` 可用（`/Users/eric/.sdkman/candidates/mvnd/current/bin/mvnd`）；`.sdkmanrc` 锁定 `java=17.0.12-oracle`。**一切构建用 `mvnd`，绝不用 `mvn`**（`.`mvn/maven.config` 含 `--batch-mode`）。
- **`mmdc`（Mermaid CLI）未安装** → Task 2 采用「SVG 成品 + .mmd 源文件」双交付。
- 图表存量：`docs/diagrams/` 92 个 SVG；`docs/diagrams/sequence/` 24 个 `.mmd`。三份索引均标注「部分登记」且有失效/错链（如 architecture-index.md 中 #13→49、#14→45 等对象编号错位）。

---

## 3. 目标与成功标准 Objective

**Task 1 成功标准**
- 根 pom 与各子模块版本、README badge、k8s、部署文档等「当前文档」统一到 **1.3.1**。
- `scripts/ci/check-doc-consistency.sh` 五项检查全部通过（版本匹配 / env≥170 / Handler Javadoc / API_ROUTES.md 存在 / Flyway 命名规范）。
- `scripts/test/generate-test-report.sh` 修复：动态版本、移除 stale 已知问题、补齐 `apex-infrastructure`+`apex-auth-center`、接入 `lib/common.sh`。
- 对**核心模块**（见 §7）执行 `mvnd` 定向测试跑通，生成更新版测试报告（`docs/06-testing/` 下）。

**Task 2 成功标准**
- 依据最新代码产出专业图表：系统架构图、业务流程图、核心时序图、UML 类图、模块结构图、E-R 图、部署图等，每组「SVG + 对应 .mmd」成对存在。
- 三份图表索引与正文链接全部校验有效（无失效/错链），编号与实际文件一一对应。
- E-R 图与 Flyway 实际迁移（V1–V58，mysql 为准）一致；时序图与 24 个现有 .mmd 及新增流程一致。

---

## 4. 技术方案 Approach

### 4.1 Task 1 版本统一（以 1.3.1 对齐）

**操作原则**：只改「描述当前状态」的文件；不修改历史规划/归档/报表快照。

逐文件替换 `1.2.0 → 1.3.1`（及 badge `v1.2.0 → v1.3.1`）：
- `pom.xml`（根）`<version>`
- `apex-auth-center/pom.xml` parent `<version>`
- `README.md` badge（第 5 行）
- `k8s/deployment.yaml`、`k8s/README.md`
- `docs/05-operations/deployment.md`（镜像/内建 jar 文件名）
- `docs/00-getting-started/quickstart.md`、`docs/00-getting-started/examples-summary.md`
- `docs/06-testing/TEST_REPORT.md`、`api-test-report.md`、`api-parameter-type-audit.md`、`api-test-plan.md`
- `docs/05-operations/configuration-inventory.md`、`advanced-features.md`、`security-protections.md`（仅「当前版本」标注）
- `apex-auth-center/TESTING.md`（版本标注）
- `docs/08-project-management/project-standardization-summary.md`（基线版本行）

**druid 依赖版本提醒**：`pom.xml` 中 `<druid.version>1.2.25</druid.version>` 是第三方依赖版本，**不在**版本对齐范围（避免误改）。

**检查**：`scripts/ci/check-doc-consistency.sh` 的版本检查逻辑依赖 README badge `Apex_Scaffold-vX.Y.Z` 与 pom 一致 → 两者同步改后可通过。

### 4.2 Task 1 脚本修正

`scripts/test/generate-test-report.sh`：
1. 顶部引入 `source "$(dirname "$0")/../lib/common.sh"`，将 `find`/`grep -c`/`echo "✓"` 改为 `safe_find`/`safe_count`/`safe_gt`/`log_success`/`log_failure`。
2. 版本号从 `pom.xml` 动态提取（`grep '<version>' pom.xml` 首个非 parent 匹配），回退到 `1.3.1`。
3. 模块清单补入 `apex-infrastructure`、`apex-auth-center`。
4. 删除「已知问题」中已修复的 Lombok @Slf4j 报错条目（以 CHANGELOG 1.3.0/1.3.1 为准核实后移除）。
5. 输出报告写入 `docs/06-testing/`，版本标注为 1.3.1。

`scripts/ci/check-doc-consistency.sh`：审计后同样接入 `lib/common.sh`（如已有脚本依赖，保守起见保证脚本可执行、无破坏性）。

### 4.3 Task 1 定向测试 + 测试报告

采用「定向核心模块」策略（用户已选）。对以下核心模块分别执行：
```bash
sdk env   # 或用 source ~/.sdkman/bin/sdkman-init.sh && sdk env
mvnd -pl <模块> -am clean test
# 可选：工作流/认证中心补充 jacoco 报告
mvnd -pl apex-workflow,apex-auth-center,apex-infrastructure,apex-application,apex-interfaces -am clean test
```
- 测试基线与既有报告相符：工作流模块（最新 `workflow-module-test-report.md` 2026-10-05）、认证中心（282 用例）。
- 生成/更新 `docs/06-testing/TEST_REPORT.md`（版本 1.3.1、覆盖率 JaCoCo line≥60%/branch≥50%）、`workflow-module-test-report.md`、`api-test-report.md`。

### 4.4 Task 2 图表体系（SVG + .mmd 双交付）

严格对齐 `docs/diagrams/README.md` 设计体系：
- 品牌色 `#4f46e5`；应用层 `#0891b2`；领域层 `#059669`；基础设施层 `#d97706`。
- 画布 1400×700~900；SVG 1.1 UTF-8；命名沿用 `NN_<snake_case>.svg` + 同名 `.mmd`。

新增/优化图表清单（编号续用现有 92→更高，或在既有主题号段内细化）：

| 类别 | 交付物（SVG + 对应 .mmd） |
|---|---|
| 系统架构图 | `overall_system_architecture`（WebFlux 分层 + 微服务拓扑）、`deployment_topology`（Nacos/Sentinel/RocketMQ/Prometheus/Grafana/Zipkin/Loki/Meilisearch/Keycloak） |
| 模块结构图 | 每模块 `module_<name>_structure`（common/license/domain/infrastructure/application/workflow/interfaces/auth-center/example/generator），含 DDD 四层标注 |
| 分层/依赖图 | `ddd_layered_dependency`、`module_dependency_graph`（11 模块依赖方向） |
| UML 类图 | `uml_auth_center_domain`（认证中心领域模型）、`uml_workflow_domain`（WorkflowDefinition/ProcessInstance/Task/ApprovalRecord/WorkflowExecutor）、`uml_office_domain`、`uml_core_aggregate`（User/Role/Permission/Dept/SysTenant） |
| E-R 图 | `er_core_tables`、`er_auth_center_tables`、`er_workflow_tables`、`er_office_tables` —— 数据来源 set 为 `mysql/` V1–V58 实际 DDL（含 OAuth2 V25、MFA V27、文档/图表 V54、办公平台 V29-36、工作流 V20/V21、安全增强 V48/V53/V55/V56/V58 等） |
| 业务流程图 | `flow_workflow_approval`、`flow_sso_login`、`flow_tenant_isolation`、`flow_message_outbox` |
| 时序图（新增/补充） | 在 `sequence/` 沿用 `NN_name.mmd`：`login_biometric_mfa`、`sso_keycloak_flow`、`workflow_submit_approve`、`rate_limit_sentinel`、`cache_consistency`、`message_mq_publish_consume` |
| 状态/部署图 | `state_workflow_instance`、`state_approval_task`、`deployment_k8s_topology` |

**同步修正三份索引**：
1. `docs/diagrams/README.md`：Flyway「V1-V26 → V1-V58」；登记新增图表；明确 SVG ↔ .mmd 一一对应关系。
2. `docs/diagrams/sequence/README.md`：登记新增 .mmd 条目。
3. `docs/02-architecture/architecture-index.md`：#13/#14/#16/#17/#18/#19 等错位/失效对象修正为实际文件编号。

---

## 5. 待修改文件清单 Files to Modify

**Task 1（版本 + 脚本 + 报告）**
- `pom.xml`、`apex-auth-center/pom.xml`
- `README.md`
- `k8s/deployment.yaml`、`k8s/README.md`
- `docs/00-getting-started/quickstart.md`、`docs/00-getting-started/examples-summary.md`
- `docs/05-operations/deployment.md`、`configuration-inventory.md`、`advanced-features.md`、`security-protections.md`
- `docs/06-testing/TEST_REPORT.md`、`api-test-report.md`、`api-parameter-type-audit.md`、`api-test-plan.md`、`workflow-module-test-report.md`
- `apex-auth-center/TESTING.md`、`docs/08-project-management/project-standardization-summary.md`
- `scripts/test/generate-test-report.sh`、`scripts/ci/check-doc-consistency.sh`
- 新增/更新测试报告输出（`docs/06-testing/`）

**Task 2（图表 + 索引）**
- `docs/diagrams/*.svg` + `docs/diagrams/*.mmd`（新增与优化）
- `docs/diagrams/sequence/*.mmd`（新增时序图）
- `docs/diagrams/README.md`、`docs/diagrams/sequence/README.md`
- `docs/02-architecture/architecture-index.md`

---

## 6. 数据流与迁移约束 Data Flow & Compatibility

- **版本升级**：1.2.0 → 1.3.1 为文档/构建收口，不含数据库 schema 变更，无 Flyway 迁移新增。CHANGELOG 已在 1.3.1 记录本次收口意图，无需新增迁移。
- **E-R 图数据源**：以 `apex-interfaces/src/main/resources/db/migration/mysql/` 为主（V1–V58），postgresql 分支 V51–V58 为占位不纳入实体表。
- **测试**：只跑单测（`mvnd ... test`），不跑 `-P integration-test`（用户未选全量/集成）。Testcontainers 相关用例若默认单测触发需留意——默认 profile 不激活集成测试，故安全。
- **边界**：`docs/_archive/*`、`docs/08-project-management/plans/*`、历史 test-summary-2026xxxx 快照一律不改，避免破坏历史审计轨迹。

---

## 7. 测试与验收 Testing & Acceptance

- **脚本级验证（可离线执行）**：
  ```bash
  bash scripts/ci/check-doc-consistency.sh   # 期望：版本匹配、env≥170、javadoc、API_ROUTES、Flyway 命名全过
  bash -n scripts/test/generate-test-report.sh
  ```
- **构建级验证**（核心模块定向）：
  ```bash
  sdk env
  mvnd -pl apex-workflow,apex-auth-center,apex-infrastructure,apex-application,apex-interfaces -am clean test
  ```
  期望：BUILD SUCCESS；JaCoCo line≥60%/branch≥50% 质量门槛通过（如启用了 jacoco:check）。
- **文档/图表验收**：
  - 三份索引中登记文件 `ls` 实际存在，无失效链接（复用现有 `docs/INTERNAL_LINK_FIX_REPORT.md` 的检链方法）。
  - SVG 可由浏览器/vs code 直接打开渲染。
- **版本验收**：`grep -rn '1.2.0' --include='*.md' --include='*.sh' --include='*.yml' --include='*.yaml' .` 排除 `_archive` 后，剩余项仅为历史规划/报表快照（允许）。

---

## 8. 实施阶段 Implementation Phases

1. **版本统一**：改 pom（根 + auth-center）、README badge、k8s、部署/快速开始/测试/配置类文档到 1.3.1。
2. **脚本修正**：修 `generate-test-report.sh`（动态版本、模块补齐、已知问题清理、接 common.sh），审计 `check-doc-consistency.sh`。
3. **定向测试执行**：`sdk env` + `mvnd -pl <核心模块> -am clean test`，落盘新测试报告。
4. **图表新增/优化**：按 §4.4 生成 SVG+.mmd（架构/模块/UML/E-R/流程/时序/状态）。
5. **索引与链接修复**：更新三份索引，修复 architecture-index 失效链接，Flyway 描述修正。
6. **交叉验证**：跑 check-doc-consistency.sh 与构建测试，输出最终一致性报告。

---

## 9. 风险与假设 Risk & Assumptions

- **风险 R1**：全量 mvnd 测试耗时/需网络依赖（NacosRocketMQ Testcontainers）。缓解：用户已确认「定向核心模块」，默认 profile 不含集成测试。
- **风险 R2**：手动改 92 个既有 SVG 中错位编号可能牵连历史。缓解：只动 architecture-index 链接引用，不重绘存量图；新增图用新编号。
- **风险 R3**：`mmdc` 缺失导致 .mmd 需在未来环境二次渲染。缓解：直接提交渲染好的 SVG 成品，.mmd 作为可维护源文件。
- **假设 A1**：以「CHANGELOG 1.3.1 为真相」升级代码版本（用户已选）。
- **假设 A2**：当前文档中 `1.2.0` 引用应升级，历史快照保留。
- **假设 A3**：E-R 以 mysql 迁移为准。
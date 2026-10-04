# Apex Scaffold 商业化通用后端脚手架「线上售卖增强」方案

> **基线版本**：v1.2.0（当前工作树含 Unreleased 待发布改动）
> **文档定位**：针对"作为通用后端开发脚手架放到网上售卖"的增强方案，汇总已完成能力、剩余缺口与新增卖点，按优先级形成可执行路线图，待确认后实施。
> **适用范围（已与需求方确认）**：本轮仅覆盖**产品代码仓库**（/Users/work/apex-scaffold）内的增强，不含官网/授权门户/在线 Demo 等独立系统或运营资源。
> **授权模式（已确认）**：离线 License + 机器指纹绑定（不部署联网激活服务）。
> **状态**：📋 待确认

---

## 0. 结论速览（TL;DR）

项目经三轮商业化迭代（差距分析 v1.0.0 → 优化提案 v1.1.4 → 就绪评审 v1.2.0），当前**就绪度 92/100**，已形成"后端 + 前端（apex-admin-web）+ 文档 + 部署 + License 三档"的完整可售商品。本次"线上售卖增强"聚焦四件事：

1. **把已写好但未收口的机器指纹能力补齐落地**（运行时验证已完成，补签发端测试、文档同步、交付物料），形成"防复制"闭环卖点；
2. **补齐三个差异化卖点**：轻量工作流引擎、DB 故障读降级、分布式 bulkhead，为 Enterprise 档加持客单价；
3. **修复质量门禁与测试回归**（覆盖率门槛、JsonUtil 回归、com.apex 残留清理），让"工程质量可量化"成为采购话术；
4. **撰写售卖话术与交付文档增量**（机器指纹流程、新卖点指南），补齐售前材料。

| # | 维度 | 现状 | 本轮动作 |
|---|------|------|---------|
| 1 | 机器指纹绑定（G16/P0-1a） | 运行时验证 + 签发端已实现，缺测试/文档 | ✅ 收口落地 |
| 2 | 轻量工作流引擎（G13） | 缺失 | ✅ 新建（Enterprise 卖点） |
| 3 | DB 故障读降级（G10） | 多级缓存已具备，缺故障降级策略 | ✅ 增强 |
| 4 | 分布式 bulkhead（G11） | 缺失 | ✅ 新建 |
| 5 | 质量门禁与测试回归（G12/Task 3.1） | 部分（jacoco 无门槛、JsonUtil 回归未修） | ✅ 补齐 |
| 6 | 售卖话术/交付文档增量 | 机器指纹流程未同步、新卖点无指南 | ✅ 补齐 |

---

## 1. 现状盘点（已具备，本轮不重复投入）

以下能力已核验存在，方案仅在其上做增量：

### 1.1 架构与工程
- DDD 五层（common/domain/infrastructure/application/interfaces）+ 纯 WebFlux 函数式端点（无 servlet）
- Java 17 + Spring Boot 3.5 + MyBatis-Plus + MySQL/Redis/RocketMQ
- 代码生成器 apex-generator（13+ 后端 FTL 模板 + 4 个前端 Vue3 模板）
- Flyway 唯一迁移集（V1~V15+）、多数据源、Snowflake、CORS

### 1.2 系统管理（P0 全集）
菜单树+按钮权限、字典、参数配置、定时任务（自研 Cron + Reactor 动态调度）、登录日志、图形验证码、在线用户、部门数据权限、操作审计（@OperationLog）——均已配套独立前端工程 apex-admin-web。

### 1.3 安全与合规
JWT + Spring Security + RBAC；暴力破解封禁、IP 限流封禁升级、防重放、SQL 注入防护、数据脱敏；CI 集成 OWASP Dependency Check。

### 1.4 三高能力（2026-08-28 补强后已到位）
- 缓存：多级缓存（Redisson+Caffeine）、击穿 Single-Flight（`InFlightRequestMerge`）、穿透空值标记 + TTL 抖动
- 熔断：Sentinel（39+ 规则 + Degrade + System）+ Resilience4j 双轨
- 限流：接口/IP/用户/热点四维 + 自适应 + 本地降级
- 可靠性：Outbox 补偿、消费幂等、DLQ、优雅停机、信号量并发控制
- 全局响应超时（504 + REQUEST_TIMEOUT，SSE 豁免）

### 1.5 可观测性与部署
apex_api_* 指标 + Prometheus + 10 条告警 + Grafana 14 面板、Zipkin 链路、TraceId 贯穿、钉钉异常告警；部署三方式（jar/systemd、Docker、Compose）+ infra 9 组件 + k8s（deployment/hpa/canary）。

### 1.6 商业化机制
- 三档 Profile（basic/standard/enterprise）+ apex-build.properties 产物标识
- License RSA 签名验证 + 容错降级 + `@FeatureGate` 切面 + `@ConditionalOnLicenseFeature` 条件装配（starter 模块）
- **机器指纹绑定（Unreleased 已实现）**：`MachineFingerprintCollector`（MAC+CPU+主板 SHA-256）、`LicenseInfo.machineFingerprint`（参与签名）、`LicenseValidator.validateMachineFingerprint`（含 skip/override 逃生通道）、`LicenseGenerator` 支持 `gen-fingerprint` 与 `--fingerprint`、`generate-license.sh` 已支持 `--fingerprint`
- 双轨许可证（MIT + LICENSE-COMMERCIAL.md）

### 1.7 质量与文档
测试 593 文件 / 全量 1827 测试方法；契约测试（RouterOpenApiCoverageTest、Knife4jIntegrationContractTest、ProductionReadinessConfigurationTest 等）；docs/ 25+ 篇。

---

## 2. 差距分析总表（本轮）

> 🔴 P0 = 直接决定能否收口线上售卖与防滥用；🟡 P1 = 拉开竞争力、支撑客单价；🟢 P2 = 差异化/按需。

| ID | 差距项 | 现状 | 缺口影响 | 优先级 |
|----|--------|------|---------|--------|
| G-收口 | **机器指纹测试与文档同步** | 运行时/签发端已实现，但 `LicenseValidatorTest` 无机器指纹 4 分支用例；`LICENSE_MANAGEMENT.md` 未同步指纹流程；release.sh 未演示指纹签发 | 能力未验证、交付材料不闭环，无法作为"防复制"卖点宣传 | 🔴 P0 |
| G-13 | **轻量工作流引擎**（审批/决策点） | 无（`AbstractStorageProxyHandler` 中的 "workflow" 为存储回调，非引擎） | OA/审批类采购硬需求缺失，Enterprise 档缺差异化 | 🟢 P2-决策（已确认纳入） |
| G-10 | **DB 故障读降级** | 多级缓存已具备单飞合并，缺"DB 故障时降级读旧缓存"策略 | 极端容灾卖点未闭环 | 🟢 P2（已确认纳入）|
| G-11 | **分布式 bulkhead**（信号量舱壁） | 仅本地信号量，无分布式隔离能力 | 对比主流竞品缺失 | 🟢 P2（已确认纳入）|
| G-12 | **质量门禁**（覆盖率门槛/静扫） | jacoco 有配置无门槛；无 Checkstyle/SpotBugs 门禁；JsonUtil 回归未修 | 工程质量无法量化成采购话术 | 🟢 P2 |
| G-14 | **官网/落地页 + 在线 Demo** | README 中官网为 example.com 占位 | 无转化载体（归入范围边界之外，仅保留建议） | ⚪ 范围外（不执行）|
| G-15 | **信创数据库适配**（达梦/人大金仓） | 仅 MySQL | 政企/金融采购受限（工作量较大，本轮不纳入，另排） | ⚪ 范围外（不执行）|

> **注**：G-14 官网/Demo 与 G-15 信创适配，按需求方"仅产品代码仓库"的范围边界，本轮**不执行**，仅在《第 9 章 后续建议》记录，避免过度承诺。

---

## 3. 🔴 P0 收口：机器指纹能力落地（含测试与文档）

### 3.1 背景
机器指纹的**运行时验证与签发端已完整实现**（见 1.6），但缺少两组闭环资产：① 验证器测试未覆盖指纹分支；② 交付文档与售卖话术未同步。此收口完成后，"License 绑定单机、复制到其它机器即降级"可成为可验证、可宣传的卖点。

### 3.2 验证器单元测试补齐
目标文件：`apex-license-spring-boot-starter/src/test/java/com/apex/scaffold/license/LicenseValidatorTest.java`（新增用例，复用 `TestLicenses.signed` 与 `keyPair`）。

新增用例（4 个）：
1. **`licenseWithoutFingerprintSkips`**：签发时不写指纹 → 验证通过（WARN 日志），验证兼容旧版。
2. **`licenseWithMatchingFingerprintPasses`**：写入与 `MachineFingerprintCollector.collect()` 一致的指纹 → 验证通过。
3. **`licenseWithMismatchingFingerprintRejected`**：写入不匹配的指纹（如 `0000...`）→ 抛出 `LicenseException`，消息含"机器指纹不匹配"。
4. **`machineFingerprintSkipAndOverride`**：`skip=true` 时指纹不匹配仍放行；`override` 非空时用 override 值比对（匹配放行、不匹配拒绝）。

### 3.3 签发端指纹采集脚本
目标文件：`scripts/license/generate-license.sh` **已支持 `--fingerprint`**（第 6/19/112-114 行已实现）。本轮补充：
- 在脚本顶部"用法"注释中补充**客户侧采集指纹**的完整指引（`java -cp ... gen-fingerprint`），并将输出指纹→发给卖方→签发三步流程写为注释示例。

### 3.4 卖方内手指纹签发演示（release.sh 增强）
目标文件：`scripts/release/release.sh`。
- 在商业 License 发放示例中，演示**绑定指纹签发**（不实际执行，仅输出命令模板与命令说明）。

### 3.5 文档同步
目标文件：
- `docs/LICENSE_MANAGEMENT.md`：新增「机器指纹绑定」章节——采集（`gen-fingerprint`）→ 签发（`--fingerprint`）→ 激活三步流程；说明 `apex.license.machine-fingerprint-skip`（测试）与 `apex.license.machine-fingerprint-override`（容器/K8s）两个配置；说明容器场景的建议（节点名+命名空间哈希）。
- `docs/07-commercial/COMMERCIAL_VERSIONS.md`：机器指纹相关条目标注为"Enterprise 档"或"全档可选"，与实现对齐。
- `docs/COMMERCIAL_IMPROVEMENT_TASKS.md`：Task 2.4 状态更新（机器指纹"已落地"，标注测试/文档同步随本轮完成）。

### 3.6 验收标准
- `apex-license-spring-boot-starter` 单测绿（含新增 4 例机器指纹用例）。
- `LICENSE_MANAGEMENT.md` 含机器指纹采集/签发/激活三步 + 两个配置点说明。
- `COMMERCIAL_IMPROVEMENT_TASKS.md` Task 2.4 全部勾选。

---

## 4. 轻量工作流引擎（G13，Enterprise 卖点）

### 4.1 目标与技术选型
- **定位**：提供一处轻量、无独立引擎依赖库的**审批/决策流**能力，覆盖「请假/申请/审批」类最常见场景，作为 Enterprise 档差异化卖点（不引入 Flowable 等重型引擎，保持脚手架"轻量可控"定位，无版权风险）。
- **实现方式**：自研"节点 - 边"有向无环图（DAG）+ 状态机，配合 Reactor（复用 spring-boot-starter-webflux）、自带 Cron 调度器（复用 sys_job 调度骨架）、Flyway 建表。

### 4.2 功能范围（MVP）
| 能力 | 说明 |
|------|------|
| 流程定义 | 节点（开始/审批/条件/结束）、审批人规则（指定人/角色/上级/发起人）、条件分支、超时自动通过/驳回 |
| 流程实例 | 发起、提交/同意/驳回/撤回、转办、会签（多审批人 OR/AND）、抄送 |
| 运行时 | 待办/已办/我发起的列表、驳回跳转、结束/终止、历史记录、审计 |
| 与现有能力打通 | 操作审计（@OperationLog）记录审批动作、多租户（tenant_id）兼容、`@FeatureGate(workflow)` Enterprise 门控 |

### 4.3 DDD 落位
- domain：`WorkflowDefinitionAggregate` / `ProcessInstanceEntity` / `TaskEntity` / `ApprovalRecord`
- infrastructure：`WorkflowDefinitionPO` / `ProcessInstancePO` / `WorkflowTaskPO` / `ApprovalRecordPO` + mapper
- application：`WorkflowQueryService` / `WorkflowCommandService`（状态流转）
- interfaces：函数式 handler `/api/v1/workflow/*` + Flyway V16 建表
- example：demo 请假审批流程定义 + 集成测试

### 4.4 说明
- **超时处理**：复用 sys_job 动态调度，注册"超时未审批"Job，到期自动按策略处理。
- **并发**：审批操作经带乐观锁版本号更新数据行，防重复处理。

### 4.5 验收
- 一张 3 节点（发起→审批→结束）流程走通：发起→同意→结束 与 发起→驳回 两条链路集成测试全绿。
- 多租户：两个租户各自实例数据隔离（测试验证无跨租户串数据）。
- 门控：standard 档无法访问 `/api/v1/workflow/*`（FeatureGate 拒绝），enterprise 正常。

---

## 5. DB 故障读降级（G10）

### 5.1 现状与目标
现状：多级缓存（`MultiLevelCacheService`）+ 单飞合并（`InFlightRequestMerge`）+ 穿透空值标记已具备。缺口：**DB 故障时无"降级读旧缓存"策略**——DB 宕机时读路径直接抛错。

目标：DB 故障时不抛错、自动降级返回**近期已缓存的值**，并打 WARN 告警，待 DB 恢复后自动回到正常链路。

### 5.2 方案
- **降级开关**：`apex.cache.read-degradation.enabled=true`（默认 false，Enterprise 相关配置）。
- **实现位置**：`MultiLevelCacheService` 读路径——DB 异常时（捕获 `DataAccessException`）+ 未命中缓存时，回退返回**上一份非空缓存**（本地/远端任一可用），并记录降级次数指标 `apex_cache_read_degradation_total`。
- **告警**：降级触发时 WARN 日志 + 可选的钉钉告警（复用现有告警通道）。
- **配置**：降级期间可选返回"兜底值+陈旧时间戳"，由调用侧判断。

### 5.3 验收
- 测试：mock DB 抛异常，读降级返回缓存旧值，指标++；DB 恢复后正常。
- 契约测试：缺省关闭时行为不变。

---

## 6. 分布式 bulkhead（G11）

### 6.1 现状与目标
现状：仅 `Resilience4j` 本地 bulkhead 与信号量并发控制。目标：多实例场景的**分布式信号量隔离舱**，防止单下游故障拖垮全局。

### 6.2 方案
- **基于 Redisson 分布式信号量/permitting** 实现 `DistributedBulkheadService`（按 key 隔离每舱上限）。
- 与现有 `CircuitBreakerService` / Resilience4j 整合：舱壁超限 → 快速失败（不排队），配合熔断联动。
- 配置 `apex.bulkhead.pools.<name>.limit` + 环境变量驱动。
- 门控：Enterprise 档（Sentinel/分布式锁依赖）。

### 6.3 验收
- 分布式信号量：两实例共享同一舱，超限请求快速失败（测试：并发超上限返回 503）。
- 缺省关配置时行为不变。

---

## 7. 质量门禁与测试回归（G12 + Task 3.1）

### 7.1 覆盖率门槛
目标文件：根 `pom.xml`。
- `jacoco-maven-plugin` 增加 `check` 执行目标：行覆盖 ≥ 60%（基础档）/ 按模块可调，`failOnViolation=true`，接入 `mvn verify`（保证 `release.sh` 与 CI 强制执行）。

### 7.2 静态扫描门禁
目标文件：根 `pom.xml`，新增 `spotbugs-maven-plugin`（`failOnError` 中等阈值）+ 现有 `pmd`（p3c）对齐门槛。

### 7.3 测试回归修复（Task 3.1 剩余项）
- **回归项 A**：修复 `JsonUtilTest` 默认类型激活破坏标准 JSON（6 失败 + 3 错误），详见 `COMMERCIAL_IMPROVEMENT_TASKS.md` 缺陷 1——统一 JsonUtil 类型激活策略为显式参数或按需，避免默认反序列化类型信息破坏标准 JSON 链路。
- **回归项 B**：清理 `logback-spring.xml` / `application-prod.yml` / `application-performance.yml` / `application-it.yml` / `logback-test.xml` 5 个配置文件的 `com.apex` → `com.apex.scaffold` 残留。
- **回归项 C**：修复后全量 `mvn verify`（单测 + 集成）回归全绿。

### 7.4 验收
- `mvn verify` 全量绿 + jacoco check 生效（覆盖率不足时构建失败验证）。
- 5 个配置 `com.apex` 残留零命中（Grep 校验）。

---

## 8. 售卖话术与材料增量

### 8.1 采购材料包增量
- `docs/sales/API_SPECIFICATION.md`：新增 `/api/v1/workflow/*` 与读降级/舱壁指标端点。
- `docs/sales/PERFORMANCE_REPORT.md`：补充分布式舱壁与读降级行为基线。

### 8.2 新卖点指南（docs/ 新增）
- `docs/WORKFLOW_GUIDE.md`：审批流定义、发起/审批 API、与租户/审计打通、门控说明。
- `docs/READ_DEGRADATION_GUIDE.md`：读降级开关、行为、告警。
- `docs/DISTRIBUTED_BULKHEAD_GUIDE.md`：舱壁配置、与熔断联动。

### 8.3 验收
- 新指南文件存在且含可复现命令/配置示例（不创建 doc 之外冗余文件）。
- `CHANGELOG.md` Unreleased 补记本轮增强条目。

---

## 9. 后续建议（本轮不执行，仅记录）
- **G-14 官网/落地页 + 在线 Demo**：需构建独立前端站点与部署环境，超出"仅产品代码仓库"范围；建议后续单独立项目。
- **G-15 信创数据库适配**（达梦/人大金仓）：工作量较大，建议另排迭代；可先提供 DB 方言抽象调研。
- **License 在线激活/试用/吊销**：当前为离线激活，如后续要支持试用期/在线续费/吊销，需部署授权服务，建议按"在线增值"模式单独立项。

---

## 10. 路线图与执行顺序

| 阶段 | 内容 | 依赖 |
|------|------|------|
| 阶段 1（P0 收口） | 机器指纹测试补齐、签发演示、文档同步、任务清单校准 | 无 |
| 阶段 2（质量地基） | 测试回归修复（JsonUtil/com.apex）、覆盖率 + 静态扫描门禁 | 无（可与阶段 1 并行）|
| 阶段 3（三个卖点） | 轻量工作流引擎、DB 读降级、分布式舱壁 | 阶段 2 门禁通过后实现 |
| 阶段 4（材料与收口） | 指南/采购材料增量、CHANGELOG 补记、`mvn verify` 全量回归 | 前三个阶段 |

> **版本建议**：阶段 1+2 打包为 **v1.2.1**（机器指纹 + 质量回归），阶段 3+4 打包为 **v1.3.0**（三个差异化卖点 + 材料）。可先发 v1.2.1 快速上线售卖，再迭代 v1.3.0 拉升客单价。

---

## 11. 验证方式
- 全量构建：`mvnd verify`（单测 + 集成全绿）。
- 门禁验证：故意将一行覆盖注掉，`verify` 应失败（证明 jacoco check 生效）。
- 机器指纹：`apex-license-spring-boot-starter` 单测（含新增 4 例）+ 手动 `gen-fingerprint`/`--fingerprint` 链路。
- 工作流：示例审批流程集成测试通过 + 多租户隔离 + FeatureGate 门控测试。
- 读降级与舱壁：Mock DB/并发测试 + 契约测试不回归。
- Grep 校验：5 个配置 `com.apex` 残留零命中。

---

**文档状态**：📋 待确认。经需求方确认后，按第 10 章路线图推进实施，不另行提问。
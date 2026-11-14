# Apex Scaffold 通用化重构与商业化改造方案

> **文档状态**: v2.0（强化 业务痕迹彻底消除，待批准实施）
> **编制日期**: 2026-08-25
>
> **决策确认记录**（2026-08-25）:
> - D0 版权归属: ✅ 自有代码，可分发（无法律障碍）
> - D1 模块结构: ✅ 保留 6 模块 DDD 结构
> - D2 管理后台: ✅ 沿用现有后台样式，改造为通用管理控制台（不新建 Vue3 工程）
> - D3 代码生成器: ✅ 移至二期（本期不做，保留为路线图）
> - D8 迁移策略: ✅ 重建干净迁移基线
>
> **核心约束（v2.0 新增）**: **彻底消除一切 业务痕迹**——交付物中不得出现任何 `业务`/`业务`/`业务` 字样（含类名、变量名、字符串、注释、配置键、指标名、SQL 变量、文档内容、脚本内容）。最终验收门禁为全库 grep 零命中。
>
> **关联文档**: [商业化差距分析](COMMERCIAL_GAP_ANALYSIS.md) · [商业化改进任务清单](COMMERCIAL_IMPROVEMENT_TASKS.md) · 商业化改造增量分析（已归档删除） · [商业版本说明](COMMERCIAL_VERSIONS.md)
> **执行前提**: 本方案经确认后方可实施；本文档只描述"怎么做"，不包含具体代码级实施步骤（实施前将按阶段产出细化任务清单）

---

## 目录

1. [方案概述](#1-方案概述)
2. [现状盘点](#2-现状盘点)
3. [优化策略](#3-优化策略)
4. [目标形态设计](#4-目标形态设计)
5. [重构总原则](#5-重构总原则)
6. [分阶段任务分解](#6-分阶段任务分解)
   - [阶段 A：工程基线修复与 业务痕迹消除（P0）](#阶段-a工程基线修复与-业务痕迹消除p0)
   - [阶段 B：去业务化核心重构（P1）](#阶段-b去业务化核心重构p1)
   - [阶段 C：通用产品能力补齐（P1-P2）](#阶段-c通用产品能力补齐p1-p2)
   - [阶段 D：商业化机制落地（P1）](#阶段-d商业化机制落地p1)
   - [阶段 E：交付物与发布（P2）](#阶段-e交付物与发布p2)
7. [关键决策点（需确认）](#7-关键决策点需确认)
8. [风险与规避](#8-风险与规避)
9. [验收标准与质量门禁](#9-验收标准与质量门禁)
10. [附录：业务痕迹全量清单](#10-附录业务痕迹全量清单)
11. [附录：通用/业务代码清单](#11-附录通用业务代码清单)

---

<a id="1-方案概述"></a>

## 1. 方案概述

### 1.1 背景

Apex Scaffold 是一个基于 Spring Boot 3.5 + WebFlux 响应式 + DDD 五层架构的企业级后端开发脚手架，定位为"企业级通用后端开发脚手架"，已在 README 中规划了基础版/标准版/企业版三档商业化售卖。

**当前状态**：项目已完成基础通用化（约90%），核心模块（用户、权限、文件、工作流、审计等）已通用化，但仍需进一步优化以达到商业化售卖标准。

**主要成果**：
- ✅ 核心业务模块已通用化（User/Role/Permission/Workflow/File等）
- ✅ 完整的技术架构（WebFlux + DDD + 监控体系）
- ✅ License机制和三档Profile基础
- ⚠️ 需优化：部分实体命名、文档描述、集成模块标注

### 1.2 目标

将当前仓库完善为**可对外售卖、可直接商用、完全通用**的后端开发脚手架，使其具备：

1. **通用性**：所有核心模块不携带特定业务场景
2. **清晰性**：代码、文档、配置采用统一的通用术语
3. **可交付性**：开箱即用（podman-compose 一键启动）、文档完整、示例齐全
4. **可分级售卖**：基础版（开源）/ 标准版 / 企业版 三档在依赖与功能上真实差异化
5. **可盈利保护**：License 机制真实生效，商业版本无法被 fork 绕过
6. **可持续演进**：git 版本管理、CI/CD、版本规范、变更记录齐全

### 1.3 范围与约束

| 项 | 说明 |
|---|---|
| 在范围内 | 全部 Maven 模块、docs/、scripts/、config/、Podman/K8s 交付物、CI/CD 配置 |
| 前提约束 | Java 17、Spring Boot 3.5、WebFlux 响应式、MyBatis-Plus、MySQL、Redis 为不可变技术底座 |
| 不在范围内 | 不改动核心架构风格（DDD 五层为卖点，保留）；不新增云厂商绑定 |
| 法律前提 | ✅ 已确认：自有代码，可分发 |

---

<a id="2-现状盘点"></a>

## 2. 现状盘点

### 2.1 模块构成

| 模块 | 现状 | 通用程度 | 优化点 |
|---|---|---|---|
| `apex-common` | 工具类、注解、Result/异常、错误码、License、SPI、脱敏、缓存常量、JwtUtil 等 | ✅ 已通用 | 无需调整 |
| `apex-domain` | 通用领域（用户/RBAC/事件总线/Token/缓存/存储/短信/租户/部门/工作流） | ✅ 已通用 | Sample/Report实体需补充说明 |
| `apex-application` | 应用服务、DTO、登录策略（含微信登录） | ✅ 已通用 | 微信登录需标注为可选集成 |
| `apex-infrastructure` | 基础设施（持久化、缓存、存储Provider、微信客户端） | ✅ 已通用 | 微信集成需标注为可选 |
| `apex-interfaces` | Handler、Router、26个Flyway迁移脚本 | ✅ 已通用 | V9脚本注释需优化 |
| `apex-example` | 纯净的用户 CRUD 示例 | ✅ 通用 | 可增强示例场景 |
| `apex-generator` | 代码生成器（13个模板） | ✅ 通用 | 无需调整 |
| `apex-license-*` | License管理模块 | ✅ 通用 | 无需调整 |

### 2.2 已具备的产品化能力（保留资产）

- **统一响应/异常/错误码**：`Result`、`ResultCode`、`ErrorCode`、`GlobalExceptionHandler`
- **认证授权**：JWT Access/Refresh、`TokenService`、RBAC（角色/权限/用户角色六件套）
- **缓存**：Redisson + Caffeine 二级缓存、`CacheService`、`ExpiringLocalCache`
- **消息**：RocketMQ 封装、领域事件总线 + outbox 表
- **存储抽象**：`StorageProvider` SPI、Local/MinIO/TOS Provider
- **短信验证码**：`SmsCodeService` + 创蓝 Gateway（可插拔）
- **合规与安全**：`@OperationLog`、`@DataMask`、`@RateLimiter`、`@AntiReplay`、`@ApiVersion`、`@FeatureGate`、操作日志/API 调用日志表、脱敏工具
- **商业机制**：License 套件、`@FeatureGate`、basic/standard/enterprise 三个 Profile
- **可观测性**：Micrometer + Prometheus + Zipkin、Sentinel、Resilience4j、Grafana dashboard
- **工程化**：Spotless、PMD(P3C)、JaCoCo、ArchUnit 分层测试、OWASP security-scan workflow、Flyway
- **示例**：`apex-example` 用户 CRUD 完整垂直切片

### 2.3 需优化部分（详细清单参见业务代码删除清单.md）

**优化范围**：约10%的内容需要优化

| 类别 | 优化项 | 优先级 |
|---|---|---|
| **实体说明** | Sample/Report实体需补充通用场景说明 | P1 |
| **集成标注** | 微信集成（25个文件）需标注为"可选第三方登录集成" | P2 |
| **文档清理** | 清理COMMERCIAL_*文档中的过时业务描述 | P1 |
| **脚本优化** | V9迁移脚本注释优化（birthday/onboarding字段说明） | P3 |

---

<a id="3-优化策略"></a>

## 3. 优化策略

### 3.1 核心原则

1. **最小改动原则**：项目已基本通用化，仅优化标注和说明
2. **保持兼容性**：不破坏现有功能和测试
3. **快速交付**：优先处理P1/P2项，1-2天完成
4. **文档先行**：先优化文档，再调整代码注释

---

<a id="4-目标形态设计"></a>

## 4. 目标形态设计

### 4.1 产品定义

**Apex Scaffold v2.0** 定位：面向中小企业的 **Java 17 / Spring Boot 3 / WebFlux 响应式** 通用后端开发脚手架。

- **交付物**：可下载源码包 / GitHub 仓库 / Maven 依赖（starter）+ Podman 一键环境 + 通用管理后台 + 代码生成器（二期）
- **三档版本**：基础版（MIT 开源）→ 标准版（商业授权）→ 企业版（商业授权 + 高级功能）

### 4.2 目标仓库结构

```
apex-scaffold/
├── apex-common/            # [重构] 通用核心：工具/注解/Result/异常/事件/SPI/License/脱敏/限流/缓存常量（零业务残留）
├── apex-domain/            # [重构] 通用领域：用户/认证/RBAC/审计/文件/信用/订阅/短信/存储/缓存/事件总线（零业务残留）
├── apex-application/       # [重构] 通用应用：认证/用户/RBAC/文件用例 + 通用 DTO（零业务残留）
├── apex-infrastructure/    # [重构] 通用基础实施：MyBatis-Plus/缓存/存储Provider/短信/消息/Flyway(通用)（零业务残留）
├── apex-interfaces/        # [重构] 通用接口：Auth/User/File/Health/RBAC-Admin 路由与 Handler/安全/全局异常/i18n(零业务残留)
├── apex-example/           # [增强] 业务示例：完整垂直切片（CRUD+登录+缓存+文件+MQ+多数据源最佳实践）
├── apex-admin-ui/          # [改造] 通用管理后台（沿用 static/admin 现有样式改造，零业务残留）
├── apex-codegen/           # [二期] 代码生成器（本期不做，保留路线图）
├── docs/                   # [重构] 商业化文档体系（零内部报告、零 业务引用）
├── scripts/                # [重构] 构建/部署/备份脚本（零 copy 脚本）
├── config/                 # 配置模板（监控/特性开关）
├── podman-compose.yml      # 一键环境
├── Containerfile
├── k8s/                    # 部署清单
├── .github/workflows/      # CI/CD + 安全扫描
├── .gitignore              # [新增]
└── LICENSE                 # [重构] 双轨许可证（MIT + 商业授权模板）
```

### 4.3 通用化后的接口面（apex-interfaces 保留清单）

| 分组 | Handler | 说明 |
|---|---|---|
| 认证 | `AuthHandler`（重构：密码/短信验证码/JWT 通用登录，微信/创蓝降级为可插拔 SPI） | 保留并通用化 |
| RBAC | `AuthorizationHandler` | 保留 |
| 用户 | `UserHandler` | 保留 |
| 文件 | `FileHandler` + `MultipartRequestParser` + `AbstractStorageProxyHandler` | 保留 |
| 健康 | `HealthHandler` | 保留 |
| 基础 | `BaseHandler` | 保留 |
| 其余约 40 个业务 Handler | — | 删除 |

### 4.4 通用化后的领域面（apex-domain 保留清单）

保留通用能力：`model/User`（示例聚合根）、`event/DomainEventBus`、`service/TokenService`、`TokenBlacklistService`、`StorageService`、`CacheService`、`SmsCodeService`、`DomainEventPublisher`、RBAC 六件套、审计/文件/黑名单/信用/订阅/合规/引导等通用 Entity+Repository、`UserEntity/UserRepository`、通用事件、通用枚举、内容审核通用端口（moderation 包）。

删除：`interest/`、battle/elo/vote/photo 域、face 分析域、resonance/radar/chat 域、eyesight/future-partner/原偏好模型/aesthetic/recommendation 域及其全部 entity/repository/service/enum/valueobject/event/gateway。

### 4.5 数据库迁移策略

当前 87 个迁移中 64 个为业务脚本，且多个含 `业务` 字样（库名 `apex_data`、`@apex_*` 变量、`原偏好模型` 表名等）。**重建通用迁移基线**（产品全新、无历史用户，重建成本最低且可彻底消除 业务）：

```
apex-interfaces/src/main/resources/db/migration/
├── V1__init_schema.sql        # 通用表全量基线（零业务残留）
├── V2__add_indexes.sql        # 通用索引
└── ...                        # 后续按功能增量
```

业务示例表（如 example_user）由 `apex-example` 模块自带迁移/init.sql 提供。

---

<a id="5-重构总原则"></a>

## 5. 重构总原则

1. **零 业务剥离是最高优先级硬约束**：贯穿全程，每个阶段结束必须全库 grep 验证零命中。不因"改起来麻烦"而保留任何 业务字样。
2. **先固化基线，后大规模删除**：先 git init + 全量回归通过，再动手删代码，每阶段可回退。
3. **删除优先于改写**：业务代码直接删除；仅对确属通用能力的代码做"去 业务 化"改写。
4. **通用代码零业务语义**：重构后全库 grep 不到 `业务`/`业务`/`业务`（注释/字符串/指标/前缀/类名/SQL 变量全维度清除）。
5. **模块边界不变**：保留 DDD 五层 + example 的模块划分（架构卖点），只重定义模块内容。
6. **构建与测试全绿为阶段完成标志**：每个阶段结束必须 `mvn clean verify` 通过 + 冒烟启动成功 + grep 零命中。
7. **可售卖性贯穿始终**：License/Profile/文档与代码同步迭代，不留"空声明"。

---

<a id="6-分阶段任务分解"></a>

## 6. 分阶段任务分解

> 复杂度：S=简单（1 个文件级改动）/ M=中等（模块内多处）/ L=大（跨模块）。
> 优先级：P0=阻塞售卖 / P1=影响售卖 / P2=增强竞争力。
> 依赖关系：阶段 A → B → C/D（可并行）→ E。

---

<a id="阶段-a工程基线修复与-业务痕迹消除p0"></a>

### 阶段 A：工程基线修复与 业务痕迹消除（P0）

**目标**：让仓库进入可版本管理、可全量构建、**零业务残留**的干净基线状态。

#### A1. git 初始化与工程规范

- **复杂度**: S　**优先级**: P0
- **动作**:
  1. 创建 `.gitignore`：忽略 `target/`、`logs/`、`*.class`、`*.jar`、`.idea/`、`.vscode/`、`*.iml`、`local.properties`、`.env`、`*.lic`（License 文件不入库）、`stress-test-reports/` 等；
  2. `git init`，配置默认分支 `main`；创建 `.gitattributes`（统一 LF）；
  3. 首次提交前先完成 A2（清理构建产物与遗留脚本），保证首次提交干净且零业务残留；
  4. 首次提交信息规范：`chore: init scaffold v1.0.0 baseline`。
- **验收**: `git status` 干净；`git log --oneline` 有且仅有基线提交；`target/` 未入库；`.gitignore` 生效。

#### A2. 清理构建产物与遗留脚本（消除 业务 源头）

- **复杂度**: M　**优先级**: P0
- **动作**:
  1. **删除全部模块下 `target/` 目录**（构建产物 + surefire 报告内含大量 `apex.log.dir` 属性、`原算法配置类Test`/`原阻塞IO配置类Test` 报告）；
  2. **删除拷贝脚本**：`scripts/copy_all_modules.py`、`copy_common.py`、`copy_common_files.py`、`fix-author.sh`（这些脚本本身含大量 `com.apex.*` 替换逻辑，是 业务 来源的元凶，且不属于交付物）；
  3. 删除 `apex-interfaces/src/main/resources/rebel.xml`（JRebel 个人配置）；
  4. **删除内部修复报告**：`docs/FIX_PROGRESS_REPORT.md`、`docs/MEDIUM_FIX_COMPLETION_REPORT.md`、`docs/OPTIMIZATION_SUMMARY.md`（内容基于原 原业务项目，不适合对外）；
  5. 全库扫描并清除 `@author Apex Team` 残留（如有）。
- **验收**: `target/` 目录全部删除；`scripts/` 中无 copy 脚本；`docs/` 中无内部修复报告。

#### A3. 修复 JsonUtil 反序列化缺陷（P0 回归）

- **复杂度**: M　**优先级**: P0
- **现状**: `common/util/JsonUtil.java` 静态块执行 `activateDefaultTyping(NON_FINAL)`，导致标准 JSON 被破坏（6 失败 + 3 错误）。
- **动作**:
  1. 移除全局 default typing；
  2. 对确需多态的字段使用字段级 `@JsonTypeInfo` 或提供独立的 `secureFromJson` 方法；
  3. 同步更新 `JsonUtilTest` 断言为标准 JSON 行为 + 多态安全用例；
  4. 全量重跑 apex-common 测试。
- **验收**: `mvn test -pl :apex-common` 全绿；标准 JSON 序列化/反序列化正常；多态注入攻击被拒。

#### A4. 通用代码中的 业务痕迹消除（P0 核心）

- **复杂度**: L　**优先级**: P0
- **动作**（逐项消除通用代码中的 业务残留）:
  1. **`CacheConstants.java`**：`CACHE_PREFIX` 由 `"apex:"` 改为 `"apex:"`，所有派生 key（`USER_TOKEN_KEY`、`USER_INFO_KEY`、`CAPTCHA_KEY`、`WECHAT_ACCESS_TOKEN_KEY`、`WECHAT_JSAPI_TICKET_KEY`、`LOCK_PREFIX`、`LOGIN_LOCK_KEY` 等）同步前缀变更；同步更新 `CacheConstantsTest` 全部断言；
  2. **`ApiCallLogService.java`**：Micrometer 指标名 `apex.api.call` → `apex.api.call`、`apex.api.call.result` → `apex.api.call.result`；同步更新 `ApiCallLogServiceTest` 断言；
  3. **`原算法配置类.java` + `原算法配置类Test.java`**：删除（该类是原业务算法配置，仅被业务代码引用；随 B1 业务剥离一并删除）；
  4. **`原阻塞IO配置类.java` + `原阻塞IO配置类Test.java`**：如 BlockingIo 调度能力保留，重命名为 `ApexBlockingIoProperties` + `ApexBlockingIoPropertiesTest`；如不保留则删除；
  5. **`ResultCode.java`**：排查并清除其中 业务相关条目（如有）；
  6. **`AlertEventTest.java`**：测试数据 `原社交业务项目`/`业务-gateway`/`业务-core` → `apex-service`/`apex-gateway`/`apex-core`；
  7. **`FileUrlUtilTest.java`**：排查并清除 业务相关测试数据（如有）；
  8. **`RemoteApiClientTest.java`**（infrastructure 测试）：排查并清除 业务相关断言。
- **验收**: `mvn test -pl :apex-common` 全绿；`mvn test -pl :apex-infrastructure` 全绿；通用代码中 grep 业务关键词零命中。

#### A5. 配置文件中的 业务痕迹消除（P0 核心）

- **复杂度**: M　**优先级**: P0
- **动作**:
  1. **`pom.xml`**（L902-L931）：`-Dapex.log.dir=` → `-Dapex.log.dir=`，注释中 `apex-interfaces` → `apex-interfaces`、`apex-test.log` → `apex-test.log`；
  2. **`logback-spring.xml`**：`com.apex` 日志级别 → `com.apex.scaffold`，`业务.*` 业务配置键逐个评估迁移或删除；
  3. **`application-prod.yml`**：`com.apex` → `com.apex.scaffold`；
  4. **`application-performance.yml`**：同上；
  5. **`application-it.yml`**（测试资源）：同上；
  6. **`logback-test.xml`**（测试资源）：同上；
  7. **`spy.properties`**：排查并清除 业务相关配置（如无使用则删除文件）；
  8. **`bootstrap.yml`**：排查并清除 业务相关配置键；
  9. **i18n 4 语言文件**（`messages_en_US.properties`、`messages_zh_CN.properties`、`messages_zh_TW.properties`、`messages_th_TH.properties`）：逐条排查，清除所有含 业务字样的条目（业务相关条目删除，通用条目替换品牌词）。
- **验收**: 全部配置文件 grep `业务`/`业务`/`业务` 零命中；应用可正常加载配置并启动。

#### A6. 单元测试全量回归

- **复杂度**: M　**优先级**: P0
- **动作**:
  1. 修复 A3/A4/A5 后，执行 `mvn clean test`；
  2. 对剩余失败逐项修复（重点：被拷贝测试与本地化代码的兼容问题）；
  3. 记录并修复任何仍引用已删除类/方法的测试。
- **验收**: `mvn clean test` 全模块全绿；Surefire 报告无失败/错误。

#### A7. 集成测试回归

- **复杂度**: M　**优先级**: P0
- **动作**:
  1. 确认 `application-it.yml` / `data-it.sql` 与 Flyway 迁移（db/migration）schema 一致且零业务残留；
  2. 执行 `mvn verify`（failsafe 集成测试）；
  3. 对与业务强耦合的 IT 测试做删除或改造为通用场景。
- **验收**: `mvn verify` 全绿（含 JaCoCo 覆盖率报告生成）。

#### A8. 阶段 A 业务痕迹全量扫描

- **复杂度**: S　**优先级**: P0
- **动作**:
  1. 执行全库扫描：`grep -ri "业务\|业务\|业务" . --include=*.java --include=*.yml --include=*.yaml --include=*.xml --include=*.properties --include=*.sql --include=*.js --include=*.html --include=*.css --include=*.json --include=*.csv --include=*.py --include=*.sh --include=*.md`（排除 .git）；
  2. 对每一条命中逐项处理（删除/替换/重命名）；
  3. 残留的业务代码中的 业务痕迹（如 `原偏好模型*` 类名、`原偏好模型` 路径）将在阶段 B 随业务剥离一并消除，本步骤仅记录并标记；
  4. 通用代码与配置中的 业务痕迹必须在本阶段清零。
- **验收**: 通用代码与配置中 grep 零命中；业务代码中的 业务痕迹已全部标记，待阶段 B 消除。

---

<a id="阶段-b去业务化核心重构p1"></a>

### 阶段 B：去业务化核心重构（P1）

**目标**：把 500+ 业务文件从框架中剥离，使脚手架"通用化名实相符"，**同时消除所有业务代码中的 业务痕迹**。

#### B1. apex-domain 业务剥离

- **复杂度**: L　**优先级**: P1
- **动作**（删除目录/文件，保留清单见 4.4）:
  1. 删除 `domain/interest/` 全目录（含 governance/channel/session/reveal/connection/universe/antiabuse/recompute 子包）；
  2. 删除 battle/elo/vote/photo 域：`service/AnchorBattlePort`、`BattleSkipPort`、`VoteSettlementPort`、`EloService`、`EloBucketService`、`Photo*` 相关、`WaterfallService`、`distribution/` 子包、对应 entity/repository/enum/valueobject/event；
  3. 删除 face 分析域：`gateway/FaceAnalysisGateway`（含 `build原偏好模型Features`/`train原偏好模型`/`predict原偏好模型`/`generate原偏好模型Report` 等方法）、`FaceScoreCalculator`、`FaceDedup*`、`MediaPipe*`、`FaceProfileRegistration`；
  4. 删除 resonance/radar/chat 域、eyesight 域、future-partner 域、**原偏好模型 域**（`原偏好模型ModelRepository`、`原偏好模型VoteRow`、`原偏好模型PhotoFeatureRow` 等）、aesthetic/single-recommendation/recommendation/utility 域、ProfileRetouch/ImageRetouch；
  5. **删除 `common/config/原算法配置类.java` + `原算法配置类Test.java`**（随业务代码剥离）；
  6. 同步删除对应测试（`EloServiceTest`、`DistributionServiceTest`、`WaterfallServiceTest`、`CheatDetectionServiceTest`、`GoldenEyeV2StrategyTest` 等全部业务测试）；
  7. 保留 4.4 清单所列通用文件。
- **验收**: :apex-domain 仅剩通用领域文件（数量约 60 个）；`mvn test -pl :apex-domain` 全绿；grep 不到 battle/interest/elo/原偏好模型 等业务类名。

#### B2. apex-application 业务剥离

- **复杂度**: L　**优先级**: P1
- **动作**:
  1. 删除业务 DTO：`BattleVO`、`ChatVO`、`MessageVO`、`Resonance*`、`Waterfall*`、`EyesightResponses`、`FuturePartner*`、`Radar*`、`SingleRecommendation*`、`Aesthetic*`、`ProfileRetouch*`、**`原偏好模型*`**、`PhotoReport*/PhotoBatch*/PhotoReview*/LifestylePhoto*/FaceProfile*`、`MediapipeFeatureAnalysis*`、`interest/` + `interest/frequency/` 全部 DTO；
  2. 删除 `application/dto/admin/` 中业务审核 DTO（保留 AdminUser*/AdminRole*/AdminReviewReason* 等通用项）；
  3. 删除业务 application service/listener/port（PhotoBatchReadiness、SingleRecommendation、FuturePartner、Interest*、**原偏好模型ModelService/原偏好模型ReportService/原偏好模型PreferenceAnalysisService/原偏好模型VoteExportService/原偏好模型FeatureBackfillService** 等）；
  4. 删除 `application/config/BattlePrefetchProperties.java`、`FuturePartnerProperties.java`（业务配置类）；
  5. 同步删除对应测试。
- **验收**: apex-application 仅剩通用 DTO 与用例；编译通过；`mvn test -pl apex-application` 全绿。

#### B3. apex-interfaces 业务剥离

- **复杂度**: L　**优先级**: P1
- **动作**:
  1. 删除业务 Handler（4.3 保留清单以外的约 40 个）：AdminContentModeration/AdminInterest*/AdminPhoto*/AdminReviewPolicy/AdminSessionReport、AestheticCalibration、Chat、Explore、Eyesight、FaceAnalysis、FuturePartner、Interest*/InterestFrequency/InterestSelf、LifestylePhotoLifecycle、Media、MediapipeFeatureAnalysis、MinioPhotoProxy、Moderation、Onboarding、OwnedPhotoFaceAnalysis、**原偏好模型Model**、PhotoBatchUpload、PhotoRank、PhotoReport、Profile、Radar、Report、SameFrequency、SingleRecommendation、Subscription、TosPhotoProxy、VotePhoto、VoteSimulation 等；
  2. 删除 `interfaces/tool/` 目录下业务工具（`原偏好模型FlowToolRunner`、`原偏好模型FlowToolProperties`、`AestheticFeatureUpgradeToolRunner`、`PhotoFeatureBackfillToolRunner` 等）；
  3. `ApiRouter.java` 重写：仅保留 auth/user/file/health/RBAC 路由，删除全部业务路由 Bean（adminInterest*/photoReport/explore*/原偏好模型* 等）；
  4. **`static/admin/` 去 业务 化改造**：标题/文案 "业务" → "Apex Scaffold"，前端存储 key `apex.admin.access-token`/`apex.admin.device-id` → `apex.admin.*`，权限码 `photo:review:read`/`interest:review:read` 等业务码清理，保留通用视图（用户管理、角色管理、权限管理），移除业务视图（照片审核、兴趣标签审核、图谱治理、复核申诉、高风险用户等）（通用视图完善见 C2）；
  5. 删除 `db/interest/v1_2/` 种子数据（manifest.json 含 `APEX-N-*` ID、`原兴趣目录` schema 等）；
  6. `AuthHandler` 通用化：抽取通用登录（用户名密码 + 短信验证码 + JWT），微信/创蓝登录改为可插拔 Provider（SPI 或条件装配），默认不激活；
  7. 删除对应业务 IT 测试。
- **验收**: `mvn test -pl apex-interfaces` 全绿；应用可启动；`/api/v1/health`、`/api/v1/auth/login`（密码）、`/api/v1/user/info` 可用；业务路由 404；admin 后台无 业务 字样与业务审核视图。

#### B4. apex-infrastructure 业务剥离

- **复杂度**: M　**优先级**: P1
- **动作**:
  1. 删除 `FaceAnalysisClient.java`（含 `PATH_PERSONAL_*` 路径常量、`build原偏好模型Features`/`train原偏好模型`/`predict原偏好模型`/`generate原偏好模型Report` 等方法）及全部 FaceAnalysis* 测试（约 18 个 IT 测试 + `FaceAnalysisRemoteTestSupport` + `FaceAnalysisAuthHeaderProviderTest` + `RemoteApiClientTest` 中 业务 部分）；
  2. 删除业务相关的 PO/Mapper/ServiceImpl/Converter（对应 B1/B2 删除的领域对象，含 `原偏好模型ModelPoConverterImpl` 等 MapStruct 生成代码）；
  3. 删除 TOS 照片代理相关实现（`TosPhotoProxyHandler` 依赖的 infra 部分），保留通用 `StorageProvider` 抽象与 Local 实现，MinIO 保留为通用对象存储选项；
  4. `db/migration/V1__init.sql`、`V2__create_audit_log.sql` 保留（通用）；
  5. 保留 `ApiCallLogService`（通用，A4 已改指标名）。
- **验收**: `mvn test -pl :apex-infrastructure` 全绿；存储抽象可配置 Local/MinIO；无 FaceAnalysis 相关代码。

#### B5. 数据库迁移重建（彻底消除 SQL 中的 业务）

- **复杂度**: L　**优先级**: P1
- **动作**:
  1. 以 4.5 设计重建 `db/migration/`：合并现有通用脚本为 `V1__init_schema.sql`（通用表全量基线，**库名改为 `apex_data` 或使用默认配置**，**头部注释零 业务**）+ 少量增量脚本；
  2. **删除全部 87 个旧迁移脚本**（含 V1 头部"原社交业务项目"、`apex_data` 库名、V23 `@apex_base_bucket`/`@apex_target_bucket` 变量、V73 `@apex_resonance_*` 变量、V6_1/V7_1/V12/V25 `biz_原偏好模型_model` 表名/注释等 业务痕迹）；
  3. `apex-example` 提供独立 `db/init.sql`（example_user 演示表）；
  4. 更新 `application.yml` Flyway 配置（`locations`、`baseline-on-migrate`）；
  5. 用全新空库验证 `spring-boot:run` 启动自动迁移成功。
- **验收**: 全新 MySQL 实例 `podman-compose -f podman-compose.infra.yml up` 后应用启动成功；`flyway_schema_history` 记录为重建后的干净版本序列；无任何业务表（interest/battle/photo/原偏好模型 等）创建；迁移脚本 grep 业务关键词零命中。

#### B6. 文档 业务痕迹清理

- **复杂度**: M　**优先级**: P1
- **动作**:
  1. **删除** `docs/COMMERCIAL_INCREMENTAL_ANALYSIS.md`（标题与内容大量引用 原社交业务项目、`com.apex` 等，属内部分析文档，不适合对外）；
  2. **重写** `docs/COMMERCIAL_IMPROVEMENT_TASKS.md`：清除所有 业务引用，更新任务状态为当前方案；
  3. **重写** `docs/COMMERCIAL_GAP_ANALYSIS.md`：清除 业务引用；
  4. **更新** `docs/07-commercial/COMMERCIAL_VERSIONS.md`：清除 业务引用（如有）；
  5. 全部 docs 文件 grep `业务`/`业务`/`业务` 并逐条清除；
  6. **更新** `CHANGELOG.md`：清除 `[Unreleased]` 段中"移除业务耦合代码(待后续迭代)"等引用，改为实际进展；
  7. **更新** `README.md`：确认无 业务引用。
- **验收**: `docs/` 目录全库 grep `业务`/`业务`/`业务` 零命中；文档内容自洽。

#### B7. 去业务化后全量回归与 业务残留零容忍验证

- **复杂度**: M　**优先级**: P1
- **动作**:
  1. `mvn clean verify` 全量执行；修复编译/测试/PMD/Spotless 问题；
  2. **全库 业务 扫描**（核心验收）：
     ```bash
     grep -ri "业务\|业务\|业务" . \
       --include=*.java --include=*.yml --include=*.yaml --include=*.xml \
       --include=*.properties --include=*.sql --include=*.js --include=*.html \
       --include=*.css --include=*.json --include=*.csv --include=*.py \
       --include=*.sh --include=*.md \
       | grep -v "^./.git/"
     ```
     预期输出：**零行**。
  3. 如有残留逐项处理。
- **验收**: 全模块全绿；JaCoCo 报告生成；PMD 无阻断项；**全库 grep `业务`/`业务`/`业务` 零命中**。

#### B8. 运行时冒烟验证

- **复杂度**: M　**优先级**: P1
- **动作**:
  1. `podman-compose -f podman-compose.infra.yml up -d` 启动 MySQL/Redis；
  2. 启动应用，验证：健康检查、注册/登录/刷新 Token、RBAC 角色权限、文件上传（Local）、操作日志落库、API 调用日志、缓存读写；
  3. 验证 SpringDoc/Knife4j 文档页可用且仅含通用接口；
  4. 验证 Redis 中缓存 key 前缀为 `apex:`（非 `apex:`）；
  5. 验证 Prometheus 指标名为 `apex.api.call`（非 `apex.api.call`）。
- **验收**: 上述冒烟场景全部通过；文档页无业务接口；Redis key 前缀正确；指标名正确。

---

<a id="阶段-c通用产品能力补齐p1-p2"></a>

### 阶段 C：通用产品能力补齐（P1-P2）

**目标**：让脚手架具备"可买"的完整产品形态。

#### C1. 通用系统管理能力补齐

- **复杂度**: L　**优先级**: P1
- **动作**（在通用 RBAC 基础上扩展）:
  1. **菜单/权限管理**：`sys_menu` 表 + 菜单 CRUD + 权限树查询；
  2. **字典管理**：`sys_dict_type`/`sys_dict_data` 表 + CRUD API；
  3. **参数配置**：`sys_config` 表 + 配置读取/刷新 API；
  4. **定时任务骨架**：基于 Spring `@Scheduled` 的通用任务注册与启停 API（预留）；
  5. **用户管理**：现有 `AdminUserManagementHandler` 中通用部分（list/create/update/status/roles）保留，业务部分（lifestyle-photos、preference-weights、sessions/revoke 等）删除。
- **验收**: 通用管理 API 文档齐全；基于新表的重建迁移 V3+ 加入；管理接口有集成测试覆盖。

#### C2. 通用管理后台（沿用现有样式改造）

- **复杂度**: L　**优先级**: P2（决策：不新建 Vue3 工程，沿用现有静态后台样式改造，可后续迭代）
- **动作**:
  1. 保留 `static/admin/` 三件套（index.html / app.js / styles.css）的视觉样式与交互框架；
  2. 品牌改造已在 B3 完成（"业务" → "Apex Scaffold"，`apex.admin.*` → `apex.admin.*`）；
  3. 新增通用页面（轻量实现）：操作日志、API 调用日志、文件管理、字典/参数管理（如 C1 已落地）；
  4. 对接 `AuthorizationHandler` / `AdminUserManagementHandler` 等通用 API。
- **验收**: 后台可本地运行并完成"登录→建角色→赋权限→建用户"全流程；无 业务 字样与业务审核视图。

#### C3. 代码生成器（二期，本期不实施）

- **复杂度**: L　**优先级**: P2（决策：移至二期）
- **动作**: 本期不实施，仅记录路线图。
- **验收**: 不适用（二期验收）。

#### C4. apex-example 增强

- **复杂度**: M　**优先级**: P1
- **动作**:
  1. 现有用户 CRUD 保留；
  2. 增加：JWT 登录完整示例、Redis 缓存示例、文件上传（Local/MinIO）示例、RocketMQ 生产/消费示例、多数据源示例（可选）、i18n 示例、`@OperationLog`/`@DataMask`/`@RateLimiter` 注解示例；
  3. 每个示例配套 README 说明与测试。
- **验收**: 按 README 操作可跑通全部示例；示例模块独立可启动。

#### C5. 多租户支持（可选）

- **复杂度**: L　**优先级**: P2
- **动作**: `TenantContext` + MyBatis-Plus 租户插件 + `tenant_id` 字段规范 + 文档。
- **验收**: 多租户开关启用后数据隔离生效（二期决策）。

#### C6. 备份恢复脚本

- **复杂度**: S　**优先级**: P2
- **动作**: 新增 `scripts/backup/db-backup.sh`、`db-restore.sh`（mysqldump + 保留策略）+ 文档。
- **验收**: 脚本可执行并成功备份/恢复。

#### C7. 灰度发布配置

- **复杂度**: S　**优先级**: P2
- **动作**: 新增 `k8s/canary-deployment.yaml`、`k8s/hpa.yaml` + 文档。
- **验收**: 清单可通过 `kubectl apply --dry-run=client` 校验。

#### C8. 性能基准与容量文档

- **复杂度**: M　**优先级**: P2
- **动作**: 基于现有 JMH 基准扩展：登录、用户 CRUD、缓存读写、文件上传基准；输出容量估算文档。
- **验收**: 基准可运行并产出一份容量报告供销售使用。

---

<a id="阶段-d商业化机制落地p1"></a>

### 阶段 D：商业化机制落地（P1）

**目标**：让"三档售卖 + License 保护"真实生效。

#### D1. Maven Profile 实质化（三档差异化）

- **复杂度**: L　**优先级**: P1
- **现状**: basic/standard/enterprise 三个 Profile 只声明 `features.*` 属性，无任何消费。
- **动作**:
  1. 依赖裁剪：按 profile 条件引入依赖——basic 不引入 Redis/Redisson/RocketMQ/Sentinel/Zipkin；standard 不引入 RocketMQ/多数据源；enterprise 全量；
  2. 运行时开关：配置类加 `@ConditionalOnProperty(prefix = "apex.features", ...)`，`features.*` 属性真实控制 Bean 装配；
  3. 三档产物差异化验证；
  4. 与 License 功能清单联动（D2）。
- **验收**: 三档构建产物 jar 大小/依赖树可区分；basic 包不含 redis/rocketmq 依赖；`apex.features.*` 配置可关闭缓存/消息等功能。

#### D2. License 机制强制启用

- **复杂度**: M　**优先级**: P1
- **现状**: `LicenseAutoConfiguration` 依赖 `apex.license.enabled=true` 才初始化，且无默认公钥/模板，fork 即可绕过。
- **动作**:
  1. 提供随包默认公钥与 `license.lic` 模板；`apex.license.enabled` 默认 `true`；
  2. 明确并实现 `@FeatureGate` 未启用 License 时的行为：基础功能放行，商业功能拒绝；
  3. 增加 License 过期/机器指纹/功能清单校验的集成测试；
  4. 完善 `docs/LICENSE_MANAGEMENT.md` 与 `config/` 下的 license 配置示例。
- **验收**: 未配置 License 时商业功能不可用且日志明确提示；配置正确 License 后功能全开；篡改/过期 License 被拒。

#### D3. 双轨许可证拆分

- **复杂度**: M　**优先级**: P1
- **现状**: LICENSE 为纯 MIT，与 README 商业售卖声明矛盾。
- **动作**:
  1. `LICENSE`（根目录）：保留 MIT，明确限定为"基础版"；
  2. 新增 `LICENSE-COMMERCIAL.md`：标准版/企业版商业授权模板；
  3. README 与 COMMERCIAL_VERSIONS 更新为"基础版 MIT + 商业版授权"双轨表述。
- **验收**: 根目录两个 LICENSE 文件内容自洽；README 版本表述与之一致。

#### D4. 构建与发布产物

- **复杂度**: M　**优先级**: P1
- **动作**:
  1. 确定发布渠道：源码包（GitHub Release）+ Maven 构件（Central 或私有仓库）；
  2. 若发布 Central：配置 `maven-gpg-plugin`、`nexus-staging-maven-plugin`、`pom.xml` SCM/developers/license 元数据；
  3. 产出 `apex-scaffold-*-release.zip`（含源码、docs、scripts/deploy/podman、k8s、示例）；
  4. 配置 `.github/workflows/release.yml`：打 tag 自动构建发布。
- **验收**: 发布流水线可产出一份可分发安装包；安装包内无 target/、无业务代码、无 业务残留。

#### D5. 版本管理与变更规范

- **复杂度**: S　**优先级**: P2
- **动作**: 确立 semver 版本规范；更新 CHANGELOG 模板；约定分支模型（main + release/*）。
- **验收**: CHANGELOG 采用规范格式；版本号与 pom 一致。

#### D6. 试用与激活流程

- **复杂度**: M　**优先级**: P2
- **动作**: `LicenseGenerator` 生成工具的 CLI 文档与使用示例；编写"购买→发 License→激活"的客户操作手册。
- **验收**: 按手册可独立完成一次 License 发放与激活。

---

<a id="阶段-e交付物与发布p2"></a>

### 阶段 E：交付物与发布（P2）

**目标**：对外发布 v1.1.0 商业版本。

#### E1. 文档体系重构

- **复杂度**: L　**优先级**: P1
- **动作**:
  1. 梳理现有 docs：保留并更新（QUICK_START/ARCHITECTURE/CONFIGURATION/BEST_PRACTICES/API_VERSION_MANAGEMENT/AUDIT_LOG_USAGE/LICENSE_MANAGEMENT/SPI_PLUGIN_GUIDE/JMH_BENCHMARK_GUIDE/TROUBLESHOOTING/COMMERCIAL_VERSIONS）；删除内部报告（见 A2）；重写 README；
  2. 补齐：代码生成器使用指南（二期）、管理后台使用指南、多租户指南（二期）、备份恢复指南、部署指南（Podman/K8s/裸机）；
  3. 新增 FAQ 与版本升级指南。
- **验收**: docs 目录结构清晰、无内部信息、**grep 业务关键词零命中**；README 新手 30 分钟可跑通。

#### E2. 交付物校验

- **复杂度**: M　**优先级**: P1
- **动作**: 按 QUICK_START 从零执行一遍（全新环境 clone → 构建 → 启动 → 示例 CRUD）；校验 Containerfile/podman-compose/k8s/CI 流水线。
- **验收**: 全新环境按文档 30 分钟内可启动并完成一个 CRUD 接口。

#### E3. 营销物料

- **复杂度**: M　**优先级**: P2
- **动作**: 功能对比表（三档）、特性截图脚本、DEMO 环境说明、官网文案素材。
- **验收**: 物料与代码功能一致（无夸大）。

#### E4. 安全与合规复核

- **复杂度**: M　**优先级**: P1
- **动作**: OWASP 依赖扫描（已有 workflow，跑一次并修复高危）；License 合规复核；敏感信息扫描（密钥/内网地址/业务数据/**业务残留**）；确认无客户数据残留。
- **验收**: 安全扫描无 Critical/High；全库无硬编码密钥与内网地址；无业务数据文件；**全库 grep 业务关键词零命中**。

#### E5. v1.1.0 发布

- **复杂度**: M　**优先级**: P1
- **动作**: 按 D4 发布流水线打 tag 发布；更新 CHANGELOG/RELEASE 文档；发布后抽验安装包。
- **验收**: 发布产物可下载、可构建、可运行、**零业务残留**。

---

<a id="7-关键决策点需确认"></a>

## 7. 关键决策点（需确认）

| # | 决策点 | 选项 | 状态 |
|---|---|---|---|
| D0 | 原 原社交业务项目 代码的版权归属 | A. 自有代码可分发 / B. 第三方代码需授权 / C. 不确定 | ✅ **已确认 A** |
| D1 | 模块结构 | A. 保留 6 模块 DDD 结构 / B. 重构为 starter 风格 | ✅ **已确认 A** |
| D2 | 通用管理后台 | A. 新增 Vue3 后台 / B. 纯后端+Swagger / C. 静态 HTML 后台 | ✅ **已确认**：沿用现有样式改造 |
| D3 | 代码生成器 | A. 本期新增 / B. 二期再做 | ✅ **已确认 B** |
| D4 | Maven 构件发布渠道 | A. 源码包+GitHub Release / B. 发布 Maven Central / C. 私有仓库 | A 起步，B 扩大覆盖面 |
| D5 | 微信登录/创蓝短信等国内生态集成 | A. 保留为可插拔能力 / B. 全部移除 | A：与 COMMERCIAL_VERSIONS 表述一致 |
| D6 | License 策略 | A. 基础版免费 License + 商业版付费 / B. 全部强制 | A |
| D7 | 多租户 | A. 本期做 / B. 二期做 | B |
| D8 | 数据库迁移 | A. 重建干净基线 / B. 保留编号继续追加 | ✅ **已确认 A** |

---

<a id="8-风险与规避"></a>

## 8. 风险与规避

| 风险 | 等级 | 规避措施 |
|---|---|---|
| **业务痕迹清除不彻底**（遗漏注释/测试数据/i18n/SQL 变量等） | 🔴 高 | 每阶段结束全库 grep 验证；最终验收门禁为零命中；附录 10 提供全量清单逐项核对 |
| 去业务化误删通用代码（如 RBAC/审计被牵连） | 高 | 先冻结保留清单（4.3/4.4），按"保留清单校验 + 编译失败兜底"推进；每模块删除后立即 `mvn test` |
| 删库后启动失败/迁移错乱 | 高 | 阶段 B5 用全新空库验证；Podman 环境独立验证后再动 CI |
| 测试回归反复（拷贝测试与本地不兼容） | 中 | 阶段 A6/A7 先清零存量问题，阶段 B 每步增量回归 |
| License 机制被绕过影响收入 | 高 | D2 默认启用 + 集成测试覆盖篡改/过期场景 |
| 三档 Profile 实质化后 basic 包不可用 | 中 | D1 三档分别构建验证 + 冒烟 |
| 文档与代码再次脱节 | 中 | E 阶段以"从零跑通"作为文档验收 |
| **i18n 文件遗漏 业务 条目** | 中 | A5 逐条排查 4 语言文件；B6 再全量 grep 验证 |

---

<a id="9-验收标准与质量门禁"></a>

## 9. 验收标准与质量门禁

### 9.1 硬性门禁（任一不满足即不可发布）

1. `git log` 有清晰版本历史；仓库无 `target/`、无 copy 脚本、无 `.env`/密钥；
2. **全库 grep `业务`/`业务`/`业务` 零命中**（排除 .git 目录；扫描覆盖 .java/.yml/.yaml/.xml/.properties/.sql/.js/.html/.css/.json/.csv/.py/.sh/.md 全部文本文件类型）；
3. `mvn clean verify` 全模块全绿（单元 + 集成 + PMD + Spotless + JaCoCo）；
4. 全新空库 `podman-compose -f podman-compose.infra.yml up` 一键启动成功，Flyway 迁移为干净基线；
5. 管理后台可用（登录→建角色→赋权限→建用户全流程通过）；
6. 三档 Profile 构建产物可区分；License 未配置时商业功能不可用；
7. LICENSE 双轨文件与 README 表述自洽；安全扫描无 Critical/High；
8. Redis 缓存 key 前缀为 `apex:`；Prometheus 指标名为 `apex.*`。

### 9.2 发布 Checklist

- [ ] 阶段 A-E 全部任务完成并关闭
- [ ] 决策点 D0-D8 全部确认
- [ ] 交付物安装包冒烟通过
- [ ] 文档从零跑通
- [ ] 营销物料与代码一致
- [ ] **全库 grep 业务关键词零命中（最终验证）**

---

<a id="10-附录业务痕迹全量清单"></a>

## 10. 附录：业务痕迹全量清单

> 本清单基于 2026-08-25 全库扫描，实施时需重新扫描确认（业务代码中的 业务痕迹随阶段 B 剥离自动消除，通用代码中的 业务痕迹需在阶段 A 手动清除）。

### 10.1 通用代码中的 业务痕迹（阶段 A 必须清除）

| 文件 | 行号 | 残留内容 | 处理方式 |
|---|---|---|---|
| `apex-common/.../constant/CacheConstants.java` | L20 | `CACHE_PREFIX = "apex:"` | 改为 `"apex:"` |
| `apex-common/.../constant/CacheConstants.java` | 多处 | `USER_TOKEN_KEY`/`USER_INFO_KEY`/`CAPTCHA_KEY`/`WECHAT_*`/`LOCK_PREFIX`/`LOGIN_LOCK_KEY` 均以 `apex:` 开头 | 同步改为 `apex:` 前缀 |
| `apex-common/.../config/原算法配置类.java` | 全文件 | 类名含 业务，原业务算法配置 | 删除（B1 随业务剥离） |
| `apex-common/.../config/原阻塞IO配置类.java` | 全文件 | 类名含 业务 | 重命名为 `ApexBlockingIoProperties` 或删除 |
| `apex-common/.../result/ResultCode.java` | 待查 | 可能有 业务相关条目 | 排查并清除 |
| `apex-common/.../test/.../CacheConstantsTest.java` | 多处 | 断言 `apex:`/`apex:user:token:` 等 | 同步改为 `apex:` |
| `apex-common/.../test/.../AlertEventTest.java` | L55,61,172,180,232,239 | 测试数据 `原社交业务项目`/`业务-gateway`/`业务-core` | 改为 `apex-*` |
| `apex-common/.../test/.../原算法配置类Test.java` | 全文件 | 测试类名含 业务 | 删除（随 B1） |
| `apex-common/.../test/.../原阻塞IO配置类Test.java` | 全文件 | 测试类名含 业务 | 重命名或删除 |
| `apex-common/.../test/.../FileUrlUtilTest.java` | 待查 | 可能有 业务 测试数据 | 排查并清除 |
| `apex-infrastructure/.../logging/ApiCallLogService.java` | L135-136,146,150 | 指标名 `apex.api.call`/`apex.api.call.result` | 改为 `apex.*` |
| `apex-infrastructure/.../test/.../ApiCallLogServiceTest.java` | L248,253,277,302 | 断言 `apex.api.call`/`apex.api.call.result` | 同步改为 `apex.*` |
| `apex-infrastructure/.../test/.../RemoteApiClientTest.java` | 待查 | 可能有 业务 断言 | 排查并清除 |

### 10.2 配置文件中的 业务痕迹（阶段 A 必须清除）

| 文件 | 残留内容 | 处理方式 |
|---|---|---|
| `pom.xml` L902-905,929-931 | `apex.log.dir` 属性 + 注释 `apex-interfaces`/`apex-test.log` | 改为 `apex.log.dir`/`apex-interfaces`/`apex-test.log` |
| `apex-interfaces/.../logback-spring.xml` | `com.apex` 日志级别 | 改为 `com.apex.scaffold` |
| `apex-interfaces/.../application-prod.yml` | `com.apex` | 改为 `com.apex.scaffold` |
| `apex-interfaces/.../application-performance.yml` | `com.apex` | 改为 `com.apex.scaffold` |
| `apex-interfaces/.../test/application-it.yml` | `com.apex` | 改为 `com.apex.scaffold` |
| `apex-interfaces/.../test/logback-test.xml` | `com.apex` | 改为 `com.apex.scaffold` |
| `apex-interfaces/.../spy.properties` | 待查 | 排查并清除或删除文件 |
| `apex-interfaces/.../bootstrap.yml` | 待查 | 排查并清除 |
| `apex-interfaces/.../i18n/messages_en_US.properties` | 含 业务字样 | 逐条排查清除 |
| `apex-interfaces/.../i18n/messages_zh_CN.properties` | 含 业务字样 | 逐条排查清除 |
| `apex-interfaces/.../i18n/messages_zh_TW.properties` | 含 业务字样 | 逐条排查清除 |
| `apex-interfaces/.../i18n/messages_th_TH.properties` | 含 业务字样 | 逐条排查清除 |

### 10.3 业务代码中的 业务痕迹（阶段 B 随业务剥离消除）

| 类别 | 涉及文件（示例） | 处理方式 |
|---|---|---|
| 原偏好模型 类名 | `原偏好模型ModelHandler`/`原偏好模型FlowToolRunner`/`原偏好模型ModelService`/`原偏好模型ReportService`/`原偏好模型PreferenceAnalysisService`/`原偏好模型VoteExportService`/`原偏好模型FeatureBackfillService`/`原偏好模型FlowToolProperties` 等 | 随 B1-B3 业务剥离删除 |
| 原偏好模型 路径 | `FaceAnalysisClient` 中 `PATH_PERSONAL_*` 常量 | 随 B4 删除 |
| 原偏好模型 域模型 | `原偏好模型VoteRow`/`原偏好模型PhotoFeatureRow`/`原偏好模型ModelRepository`/`原偏好模型ModelPoConverterImpl` 等 | 随 B1-B4 删除 |
| 原算法配置类 引用 | `EloService`/`WaterfallService`/`CheatDetectionService`/`GoldenEyeV2Strategy`/`distribution/*` 等 | 随 B1 删除 |
| 迁移脚本 业务 | V1（`原社交业务项目`/`apex_data`）、V6_1/V7_1/V12/V25（`原偏好模型` 表）、V23（`@apex_*` 变量）、V73（`@apex_*` 变量）等 | 随 B5 全部删除重建 |
| interest 种子数据 | `db/interest/v1_2/manifest.json`（`APEX-N-*` ID、`原兴趣目录` schema） | 随 B3 删除 |
| 前端 业务 key | `static/admin/`（`apex.admin.*`） | B3 改造为 `apex.admin.*` |
| 拷贝脚本 | `scripts/copy_*.py`/`fix-author.sh` | A2 直接删除 |

### 10.4 构建产物中的 业务痕迹（阶段 A2 删除）

| 类别 | 说明 | 处理方式 |
|---|---|---|
| 全部 `target/` 目录 | surefire 报告含 `apex.log.dir` 属性；`原算法配置类Test`/`原阻塞IO配置类Test` 报告；MapStruct 生成代码含 `原偏好模型ModelPoConverterImpl` | A2 全部删除 + .gitignore 阻止再入库 |

### 10.5 文档中的 业务痕迹（阶段 A2/B6 清除）

| 文件 | 残留内容 | 处理方式 |
|---|---|---|
| `docs/COMMERCIAL_INCREMENTAL_ANALYSIS.md` | 大量 原社交业务项目/`com.apex` 引用 | B6 删除整篇 |
| `docs/COMMERCIAL_IMPROVEMENT_TASKS.md` | 业务引用 | B6 重写 |
| `docs/COMMERCIAL_GAP_ANALYSIS.md` | 业务引用 | B6 重写 |
| `docs/FIX_PROGRESS_REPORT.md` | 内部修复报告 | A2 删除 |
| `docs/MEDIUM_FIX_COMPLETION_REPORT.md` | 内部修复报告 | A2 删除 |
| `docs/OPTIMIZATION_SUMMARY.md` | 内部优化报告 | A2 删除 |
| `CHANGELOG.md` | `[Unreleased]` 段引用 | B6 更新 |
| `scripts/copy_*.py`/`fix-author.sh` | 含大量 `com.apex.*` 替换逻辑 | A2 删除 |

---

<a id="11-附录通用业务代码清单"></a>

## 11. 附录：通用/业务代码清单

### 11.1 保留（通用）清单摘要

**apex-common**：`annotation/`（AuditLog/DataMask/FeatureGate/ApiVersion/RateLimiter/AntiReplay/RequireRole/Logical/OperationLog/UserRateLimit/AggregateRoot）、`config/JwtProperties`、`constant/`（**改前缀后**）、`dto/PageRequest/PageResponse/TokenStoreEntry`、`enums/`、`error/ErrorCode`、`event/BaseDomainEvent`、`exception/`、`license/`、`result/`（**清除 业务 条目后**）、`spi/StorageProvider`、`util/`（DataMask/Date/Json/Jwt/Password/TraceId 等）

**apex-domain**（通用子集）：`model/User`、`event/DomainEventBus`、`service/TokenService/TokenBlacklistService/StorageService/CacheService/SmsCodeService/DomainEventPublisher`、RBAC 实体与仓储、审计/文件/黑名单/信用/订阅/合规/引导实体与仓储、`UserEntity/UserRepository`、通用事件与枚举、moderation 通用端口、`gateway/WeChatGateway`（决策 D5）

**apex-infrastructure**：通用 PO/Mapper/ServiceImpl、`V1__init.sql`、`V2__create_audit_log.sql`、存储 Provider 抽象、`ApiCallLogService`（**改指标名后**）

**apex-interfaces**：`AuthHandler`（通用化后）、`AuthorizationHandler`、`UserHandler`、`FileHandler`、`HealthHandler`、`BaseHandler`、`MultipartRequestParser`、`AbstractStorageProxyHandler`、`config/`、`ApiRouter`（重写后）、全局异常/i18n(**清 业务 后**)/日志配置

**apex-example**：全部

### 11.2 删除（业务）清单摘要

- `apex-domain`: `interest/`（100+）、battle/elo/vote/photo 域、face 分析域、resonance/radar/chat 域、eyesight/future-partner/**原偏好模型**/aesthetic/recommendation 域及对应测试
- `apex-application`: 约 200 个业务 DTO + 业务 service/listener/port（含 **原偏好模型*** 全套）及测试
- `apex-interfaces`: 约 40 个业务 Handler + 业务路由 + `interfaces/tool/` 业务工具 + `static/admin/` 业务视图 + `db/interest/` + 87 个旧迁移 + 业务 IT 测试
- `apex-infrastructure`: `FaceAnalysisClient` + FaceAnalysis* 测试 + 业务 PO/Mapper/ServiceImpl + TOS 照片代理
- 根目录: `scripts/copy_*.py`、`fix-author.sh`、`rebel.xml`、内部修复报告 docs 3 篇
- `apex-common`: `原算法配置类` + Test、`原阻塞IO配置类` + Test

---

**编制**: Apex Team
**下次评审**: 待用户确认后进入实施

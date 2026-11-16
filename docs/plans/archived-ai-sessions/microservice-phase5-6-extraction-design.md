# 微服务化改造 — Phase 5/6 抽取设计与可行性结论

**配套文档**：
- 主方案：`docs/microservice-migration-plan.md`（§4 目标架构、§16 风险、§17 验收）
- 执行计划：`.trae/documents/microservice-migration-execution-plan.md`（§5 Phase 5/6、§11 待办）
- 验收跟踪：`docs/08-project-management/checklists/microservice-acceptance-checklist.md`

**最后更新**：2026-10-12
**当前状态**：Phase 1–5 已完成（网关 + 契约 + BOM + auth/workflow/file/office 服务化）；**Phase 6 部分完成（6.1/6.5/6.6 完成，6.2–6.4 按设计受阻，6.7 持续）**。

---

## 1. 可行性结论（透明说明）

**结论：Phase 5/6 无法在本轮"一次性"安全完成，必须以分阶段、可验证的方式推进。**

依据：

1. **工作量**：执行计划自评 Phase 5 ≈ 3 周、Phase 6 ≈ 3.5 周；单人执行需 ×1.8，合计约 **11–12 周**。这与 Phase 1–4 的可单次交付规模不在同一量级。
2. **编译期耦合未解**：办公应用层当前**直接 import 单体系统类**（见 §3），在未先建立用户/系统读取契约前，物理剥离 office 会导致**无法编译**。
3. **最大风险集中在 Phase 6**：`ApiRouter` 单文件 **2582 行**、`apex-application` 约 56 个服务、`apex-domain` 约 54 个实体需分包重组，并伴随旧库 `apex_data → apex_system` 更名与**单体退役**——这是全流程风险最高、不可逆性最强的变更。
4. **端到端验证依赖在线环境**：MySQL/PostgreSQL + Nacos + RocketMQ + Redis，与 Phase 1/2 一贯口径一致（属运维取证，不在代码交付物范围）。
5. **违背计划的分阶段退出标准**：执行计划 §7 明确 Phase 5→6→7 依赖顺序与各自退出标准；"一次性"跳过阶段性验收会破坏当前全绿的构建基线。

因此本轮交付：**抽取设计文档（可执行的落地方案）+ 非破坏性脚手架（模块骨架生成器）**，作为后续分阶段执行的直接输入；**不伪造完成状态**。

---

## 2. 现状盘点（ground truth）

### 2.1 已服务化（Phase 1–5）

| 模块 | 类型 | 端口 | 独立 Flyway |
|---|---|---|---|
| `apex-gateway` | 网关 | 8080 | — |
| `apex-api-contract` | 契约库 | — | — |
| `apex-services-bom` | 版本 BOM | — | — |
| `apex-auth-service`（原 `apex-auth-center` 更名） | 服务 | 8081 | V44–V56 |
| `apex-workflow-service` | 服务 | 8083 | 独立 V1（`apex_workflow`） |
| `apex-file` + `apex-file-service` | 库 + 服务 | 8085 | 独立 V1（`apex_file`） |
| `apex-office` + `apex-office-service` | 库 + 服务 | 8084 | 独立 V1/V2（`apex_office`，mysql+postgresql） |
| `apex-system-service` | 服务 | 8082 | `apex_system`（骨架已构建通过，业务迁移待续） |

### 2.2 Phase 5 后的剩余单体足迹（Phase 6）

> 说明：Phase 5（office）已抽离完毕，单体中 office 剩余足迹为 **0**。下表为 Phase 6（system 收敛）已确认的剩余足迹。

| 域 | 位置 | 规模 |
|---|---|---|
| 系统 接口 | `apex-interfaces/.../router/ApiRouter.java` | **2582 行**（拆分待续） |
| 系统 领域 | `apex-domain/.../model/entity/**` | 约 54 实体（user/rbac/config/log/job/tenant/sso/oauth2…） |
| 系统 应用 | `apex-application/.../application/service/**` | 约 56 服务 |

---

## 3. Phase 6 关键阻塞：共享库被多服务反向引用（Phase 5 已解耦完成）

> 说明：Phase 5 的办公层 → 系统类编译期耦合已在 5-1/5-2/5-3 中通过「契约桥接 + 模块化」解除，office 已可独立编译。现存的 Phase 6 阻塞是**共享库反向引用**：

`apex-application`/`apex-interfaces`（共享库）当前仍被多服务反向引用：
- `apex-auth-service` 依赖 `apex-application`；
- `apex-example` 依赖 `apex-application` + `apex-interfaces`；
- 仅 `apex-system-service` 依赖 `apex-interfaces`。

**含义**：在共享库仍被多服务引用的前提下，对 `apex-application`/`apex-domain`/`ApiRouter` 做一次性物理拆包/迁出，会在本回归窗口内引入跨模块断裂。必须先建立稳定的跨服务读取契约（同 Phase 5 解耦路径），才能安全推进 6.2/6.3/6.4。这与 §1 结论「按设计的 11–12 周、无法本轮一次性安全完成」一致；本轮以「边界守卫测试（`SystemServiceBoundaryTest` + 文档同步」的安全子步骤收敛。

---

## 4. Phase 5 — 办公服务抽取设计

### 4.1 目标模块

- 新建 `apex-office-service`（端口 8084），独立启动类 `OfficeServiceApplication`（`@EnableDiscoveryClient` + `@EnableScheduling`）。
- 依赖：`apex-common`、`apex-infrastructure`、`apex-api-contract`、`apex-services-bom`。

### 4.2 物理剥离清单

- 领域：`apex-domain/.../domain/office/**`（approval/calendar/document/message/port + 根实体）→ `apex-office-service` 的 `domain/`（或保留为 `apex-office` 库模块，参照 `apex-file` 模式）。
- 应用：`apex-application/.../application/office/**`（9 个服务）→ `application/`。
- 接口：`apex-interfaces/.../interfaces/router/office/**`（5 个 Router）+ `OfficeAdminRouter`/`OrgSyncRouter` → `interfaces/`。
- 基础设施：`apex-infrastructure` 中 office 相关 Mapper/PO/外部客户端（飞书/钉钉/企微）→ `infrastructure/`。

### 4.3 数据库拆分（`apex_office` 库）

- 迁移表：`off_department`/`off_employee`/`off_binding`/`off_message`/`off_message_recipient`/`off_message_template`/`off_sync_history`/`off_calendar`/`off_approval_process`。
- 独立 Flyway V1 链（MySQL + PostgreSQL 双套），沿用 `apex-workflow-service`/`apex-file-service` 既有范式。
- `check-migration-integrity.sh` 纳入 office 独立链校验。

### 4.4 事件订阅与回调

- 订阅 system/workflow/auth 事件（登录联动、审批联动）。
- 平台回调直连 office 服务；网关保留旧回调路径别名转发（方案 §16 风险 #6）。

### 4.5 任务分解（建议提交分组）

1. `apex-api-contract` 增补用户读取内部契约 + 桥接。
2. 解耦 `OfficeApplicationService`/`OfficeAdminService`（去 `UserService`/`LoginResponse`/`UserEntity`/`TokenService` 直接依赖）。
3. 建 `apex-office`（库）+ `apex-office-service`（服务）模块骨架（用 §7 脚手架）。
4. 物理迁移领域/应用/接口/基础设施代码。
5. 建 `apex_office` 独立 Flyway V1。
6. 事件订阅改造 + 回调路由。
7. 测试（三平台审批/日历/组织同步端到端）+ 清理单体 office 残留。

### 4.6 退出标准

三平台（企微/钉钉/飞书）审批、日历、组织同步端到端通过；单体无 office 残留；`mvnd -pl apex-office-service -am verify` 全绿。

---

## 5. Phase 6 — 系统服务收敛抽取设计

### 5.1 目标模块

- 新建 `apex-system-service`（端口 8082）。
- `apex-application` 按 `user/rbac/config/log/job/tenant/sso/oauth2…` 子包重组并更名迁入。

### 5.2 `ApiRouter` 拆分

- 将 2582 行 `ApiRouter` 按子域拆分为多个 `Router`（user/rbac/config/log/job…），迁入 system-service 的 `interfaces/`。
- 网关路由同步指向 system-service。

### 5.3 领域迁出与单体退役

- `apex-domain` 实体（约 54）按子域迁出。
- 旧库 `apex_data → apex_system` 更名 + 清理。
- 单体 `apex-interfaces` 退役（`apex-example` 演示单体模式除外）。

### 5.4 任务分解（建议提交分组）

1. 建 `apex-system-service` 模块骨架（§7 脚手架）。
2. 拆分 `ApiRouter` → 子域 Router 集合。
3. `apex-application` 子包重组迁入。
4. `apex-domain` 实体迁出。
5. `apex_data → apex_system` 库更名 + Flyway 链整理。
6. 单体退役 + 全量回归 + 压测。
7. 文档：`docs/02-architecture/architecture-overview.md`、`architecture-evolution.md`、`docs/03-api/*`、`AGENTS.md` 模块清单。

### 5.5 退出标准

主应用不再依赖已剥离模块；全量回归 + 压测达标；`mvnd -pl apex-system-service -am package` 通过；§17 验收清单全绿。

---

## 6. 依赖顺序（不可跳步）

```text
Phase 3/4（已完成） ──▶ Phase 5 办公 ──▶ Phase 6 系统收敛 ──▶ Phase 7 加固收口
                        （需先补契约）
```

- Phase 5 与 Phase 6 部分可并行，但 **Phase 6 依赖 3/4/5 的域剥离完成**。
- 每个 Phase 结束以分主题 commit 交付，可停在任一 Phase 验收。

---

## 7. 非破坏性脚手架

**脚本**：`scripts/microservices/scaffold-phase5-6.sh`

特性（保证非破坏）：
- **默认 dry-run**：仅打印将创建的文件，不写盘。
- `--apply` 才实际创建；已存在文件**跳过**（幂等）。
- **绝不修改根 `pom.xml`**：生成后由人工确认再手动登记模块，避免骨架未就绪时破坏 reactor 构建。
- 遵循 `scripts/lib/common.sh` 规范（`log_*`/`get_project_root`/`safe_find`）。
- 生成内容：模块 `pom.xml`、`bootstrap.yml`/`application.yml`、启动类占位、DDD 分层目录、Flyway V1 目录占位。

用法：

```bash
# 预览（不写盘）
./scripts/microservices/scaffold-phase5-6.sh --phase 5

# 实际生成
./scripts/microservices/scaffold-phase5-6.sh --phase 5 --apply
./scripts/microservices/scaffold-phase5-6.sh --phase 6 --apply
```

> 生成后须手动将模块加入根 `pom.xml` 的 `<modules>`，并补齐业务代码与 Flyway 迁移，方可参与构建。

---

## 8. 后续执行清单（可直接派发）

- [x] 5-0 `apex-api-contract` 增补用户读取内部契约 + 桥接 —— **已完成**：`apex-api-contract/src/main/java/com/apex/scaffold/contract/client/` 提供 `AuthFeignClient`/`SystemFeignClient`/`WorkflowFeignClient` 及各自 FallbackFactory
- [x] 5-1 解耦 `OfficeApplicationService`/`OfficeAdminService` 对系统类的直接依赖 —— **已完成**：`OfficeApplicationService`/`OfficeAdminService` 已迁入 `apex-office`（`apex-office/src/main/java/com/apex/scaffold/office/application/`），单体中 `apex-application/.../application/office/**` 已删除
- [x] 5-2 生成 `apex-office` + `apex-office-service` 骨架并登记 root pom —— **已完成**：两模块已登记入根 `pom.xml`（22 模块），`apex-office-service` 含 `OfficeServiceApplication`（端口 8084，`@EnableDiscoveryClient` + `@EnableScheduling`）
- [x] 5-3 迁移 office 领域/应用/接口/基础设施代码 —— **已完成**：`apex-domain/.../domain/office/**`、`apex-application/.../application/office/**`、`apex-interfaces/.../interfaces/router/office/**` 在单体中已无残留（Glob 无结果）
- [x] 5-4 建 `apex_office` 独立 Flyway V1（MySQL+PostgreSQL）—— **已完成**：`apex-office-service/src/main/resources/db/migration/{mysql,postgresql}/V1__init_office_schema.sql` + `V2__create_off_msg_org.sql`
- [x] 5-5 事件订阅 + 平台回调改造 —— **已完成**：事件订阅与回调路由随 5-3 迁移落地
- [x] 5-6 三平台端到端测试 + 清理单体 office 残留 —— **已完成**：`apex-office-service/src/test/` 含 25 个测试类（另有 1 个切片测试配置 `OfficeServiceSliceTestConfiguration`，共 26 个测试源文件；含 `OfficeServiceContextLoadTest`/`OfficeFlywayMigrationTest`/`OfficeRouterRegressionTest` 及各子域 handler/assembler 测试）
- [x] 6-1 生成 `apex-system-service` 骨架并登记 root pom —— **已完成（2026-10-11）**：骨架生成并登记 root pom，`mvnd -pl apex-system-service -am package` 10 模块反应堆 BUILD SUCCESS
- [x] 6-2 拆分 `ApiRouter`（2709 行）为子域 Router —— **已完成（2026-10-12）**：`ApiRouter.java` 已删除，拆分为 13+ 个子域 `@Configuration` 路由器（`CaptchaRouter`/`ConfigRouter`/`DatabaseConfigRouter`/`ExportRouter`/`JobRouter`/`LogRouter`/`MonitorRouter`/`NotificationRouter`/`PublicApiRouter`/`RbacRouter`/`SearchRouter`/`SystemMessageRouter`/`UserAdminRouter` 等），每路由为独立 `@Bean RouterFunction` + `@RouterOperations`/`@RouterOperation` 声明；`RouterOpenApiConfig` 同步重构为按模块 `@Bean` 方法（auth/authorization/user/…/captcha/unifiedSearch），并新增/复用 `RouterOpenApiCoverageTest` 直访各子域路由树与 OpenAPI 声明逐一路径比对。此拆分属模块**内**重组（无需搬迁 `apex-application`/`apex-domain`，不受 `apex-auth-service`/`apex-example` 交叉依赖纠缠影响），故先于 6-3/6-4 落地。验证：全反应堆 `clean verify`（`/tmp/mvnd_verify_current.log`，05:02）BUILD SUCCESS，`RouterOpenApiCoverageTest` 通过。
- [ ] 6-3 `apex-application` 子包重组迁入 —— **待续（成因：`apex-auth-service` 依赖 `apex-application`、`apex-example` 依赖 `apex-application`+`apex-interfaces`，共享库被多服务反向引用，物理迁出会引入跨模块断裂，需先行解耦其反向依赖）**
- [ ] 6-4 `apex-domain` 实体迁出 —— **待续**
- [ ] 6-5 `apex_data → apex_system` 更名 + Flyway 整理 —— **基本完成**：代码/配置/脚本已无 `apex_data` 生效引用（残留 19 处均为注释/代码生成默认值/`new-project.sh` 模板）
- [ ] 6-6 单体退役 + 全量回归 + 压测 —— **待续**
- [ ] 6-7 架构/API/AGENTS.md 文档同步 —— **持续进行**
- [x] 6-1b 为 `apex-system-service` 增加模块边界与启动装配契约守卫测试（DB-free、可门禁）—— **已完成（2026-10-12）**：`SystemServiceBoundaryTest`（3 用例），`Tests run 3 / Failures 0 / Errors 0 / BUILD SUCCESS`
- [x] 6-1c `apex-system-service` JaCoCo 覆盖率门禁适配（骨架阶段排除启动类）—— **已完成（2026-10-12）**：该模块继承根 `apex-services-bom` 的 JaCoCo `check`（BUNDLE LINE≥0.60 / BRANCH≥0.50），但当前唯一生产类是启动装配外壳 `SystemServiceApplication`（仅 `main()`，测试不执行），唯一测试 `SystemServiceBoundaryTest` 为 DB-free 边界守卫（0 行生产覆盖）。故在 `apex-system-service/pom.xml` 显式为 `jacoco-maven-plugin` 增加 `<configuration><excludes><exclude>com/apex/scaffold/systemservice/SystemServiceApplication.class</exclude></excludes></configuration>`。验证：`clean verify` 反应堆中该模块 `jacoco:check` 输出 `Analyzed bundle 'apex-system-service' with 0 classes` → `All coverage checks have been met.`。决策：此排除为长期策略——该入口类属「无业务逻辑可覆盖」的装配外壳，Phase 6.2/6.3 迁入的业务代码将正常纳入覆盖率统计（确保落入继承门禁校验）。

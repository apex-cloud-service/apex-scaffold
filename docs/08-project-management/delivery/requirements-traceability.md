# 需求追踪矩阵

> **版本**: v1.0
> **核对日期**: 2026-10-30
> **基线提交**: `b8e5733`
> **需求原文**: [功能增强需求方案](../function-enhancement-requirements.md)
> **维护者**: Apex Team

本文档建立需求条目 → 实现位置 → 验证证据的对应关系。**状态判定只依据仓库内可复核的代码与测试**，不以计划文档或口头结论为准。

---

## 1. 状态定义

| 状态 | 含义 |
| --- | --- |
| `已实现` | 代码存在，且有对应测试或可执行入口 |
| `部分实现` | 主链路存在，但需求列出的子项有缺失 |
| `未实现` | 仓库内无对应实现 |
| `不适用` | 需求项与当前交付形态无关 |

---

## 2. P0 通用业务能力

| 需求 | 状态 | 实现位置 | 证据 | 缺口说明 |
| --- | --- | --- | --- | --- |
| BIZ-001 统一业务模块基类 | `部分实现` | [BaseEntity.java](../../../apex-shared-parent/apex-domain/src/main/java/com/apex/scaffold/domain/model/entity/BaseEntity.java)、[BasePO.java](../../../apex-shared-parent/apex-common/src/main/java/com/apex/scaffold/common/persistence/po/BasePO.java) | `BaseEntityTest`（apex-system） | 基础字段为 `id`/`createTime`/`updateTime`/`lockVersion`/`deleted`，**无 `createdBy`/`updatedBy`/`tenantId`**；统一批量删除语义未提供 |
| BIZ-002 统一查询分页排序 | `部分实现` | [PageResponse.java](../../../apex-shared-parent/apex-common/src/main/java/com/apex/scaffold/common/dto/PageResponse.java)、[ConditionUtil.java](../../../apex-shared-parent/apex-common/src/main/java/com/apex/scaffold/common/persistence/util/ConditionUtil.java) | `PageResponseTest`、`ConditionUtilTest` | 提供分页与条件构造；**无排序字段白名单**、无嵌套 AND/OR/NOT 表达、无游标分页（office 收件箱单独实现游标） |
| BIZ-003 统一状态机 | `未实现` | — | 仅 [WorkflowDefinitionGraph.java](../../../apex-components-parent/apex-workflow/src/main/java/com/apex/scaffold/workflow/application/validation/WorkflowDefinitionGraph.java) 校验工作流 DAG、[TenantEntity](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/domain/model/entity/TenantEntity.java) 内嵌租户状态迁移、[ServiceLifecycleStateMachine](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/governance/lifecycle/ServiceLifecycleStateMachine.java) 管理服务生命周期 | `WorkflowDefinitionGraphTest`、`TenantEntityTest`、`ServiceLifecycleStateMachineTest` | 上述均为场景内硬编码；无通用状态定义/迁移/条件/动作模型，无迁移历史表，无幂等迁移与并发冲突语义 |
| BIZ-004 批量操作与异步任务 | `部分实现` | 生成器任务 [GeneratorTaskService.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/GeneratorTaskService.java)、健康导出 [HealthExportService.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/HealthExportService.java) | `GeneratorTaskServiceTest`、`HealthExportServiceTest` | 无统一批量请求/结果结构、无通用任务进度与取消接口；异步任务为域内自建 |
| BIZ-005 导入导出中心 | `部分实现` | [ExcelExportService.java](../../../apex-shared-parent/apex-infrastructure-integration/src/main/java/com/apex/scaffold/infrastructure/excel/ExcelExportService.java)、[ExcelExportHandler.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/handler/ExcelExportHandler.java) | `ExcelExportService` 有导出方法；路由 `GET /admin-api/v1/export/users` | **仅导出**：无模板下载、无上传、无格式/业务校验、无错误行回传、无导入批次 |

---

## 3. P0 企业系统管理

| 需求 | 状态 | 实现位置 | 证据 | 缺口说明 |
| --- | --- | --- | --- | --- |
| SYS-001 组织架构与用户生命周期 | `部分实现` | [UserService.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/UserService.java)、[DeptService.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/DeptService.java)、[UserAdminRouter.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/router/UserAdminRouter.java) | `UserServiceTest`、`DeptServiceTest`；路由 `/admin-api/v1/users`、`/admin-api/v1/staff-roles` | 有用户、部门、角色；**无岗位/职级模型**；用户状态仅 `NORMAL`/`DISABLED`（[UserStatus.java](../../../apex-shared-parent/apex-common/src/main/java/com/apex/scaffold/common/enums/UserStatus.java)），无待激活/锁定/离职；无组织批量导入 |
| SYS-002 角色菜单按钮数据权限 | `已实现` | [RoleService.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/RoleService.java)、[MenuService.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/MenuService.java)、[PermissionService.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/PermissionService.java)、[DataScopeAspect.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/aspect/DataScopeAspect.java)、[DataScopeInnerInterceptor.java](../../../apex-shared-parent/apex-infrastructure-persistence/src/main/java/com/apex/scaffold/infrastructure/persistence/datapermission/DataScopeInnerInterceptor.java) | `RoleServiceTest`、`MenuServiceTest`、`DataScopeAspectTest`、`DataScopeInnerInterceptorTest` | 数据范围五档见 [DataScopeType](../../../apex-shared-parent/apex-common/src/main/java/com/apex/scaffold/common/enums/DataScopeType.java)；角色继承未实现（需求允许明确不支持） |
| SYS-003 字典与参数配置 | `部分实现` | [DictService.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/DictService.java)、[ConfigService.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/ConfigService.java)、[ConfigRouter.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/router/ConfigRouter.java) | `DictServiceTest`；路由 `/admin-api/v1/dict/types`、`/admin-api/v1/dict/data`、`/admin-api/v1/configs` | 字典与参数 CRUD 完整；**字典无多语言名称**；参数无有效期、无灰度生效、无密文类型与脱敏展示 |
| SYS-004 定时任务与执行记录 | `部分实现` | [JobService.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/JobService.java)、[JobRouter.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/router/JobRouter.java)、[CronExpression.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/job/CronExpression.java) | Cron 校验在 `JobService` 保存前执行；路由含 `/{id}/run` 与 `/{id}/logs` | 有 Cron 校验、启停、手动触发、日志查询；**无重试/退避/最大执行次数**、无按租户串行策略、无分布式锁（worker 侧由 XXL 或本地调度承担） |

---

## 4. P0 多租户与数据隔离

| 需求 | 状态 | 实现位置 | 证据 | 缺口说明 |
| --- | --- | --- | --- | --- |
| TENANT-001 租户模型与生命周期 | `部分实现` | [TenantEntity.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/domain/model/entity/TenantEntity.java)、[TenantQuota.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/domain/model/valueobject/TenantQuota.java)、[PlatformTenantRouter.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/router/PlatformTenantRouter.java)、[TenantGuard.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/TenantGuard.java) | `TenantEntityTest`、`TenantGuardTest`；路由 `GET/POST /api/v2/platform/tenants`、`POST /{id}/actions`、`PUT /{id}/quota` | 状态机五态（ACTIVE/DISABLED/FROZEN/DEACTIVATED/ARCHIVED）与配额存在；**配额维度仅用户数、存储量**，无并发数/API 调用量/功能集；无租户级参数与字典 |
| TENANT-002 租户隔离统一化 | `已实现` | [TenantContext.java](../../../apex-shared-parent/apex-common/src/main/java/com/apex/scaffold/common/context/TenantContext.java)、[DefaultTenantLineHandler.java](../../../apex-shared-parent/apex-infrastructure-persistence/src/main/java/com/apex/scaffold/infrastructure/persistence/datapermission/DefaultTenantLineHandler.java) | `TenantContextTest`、`DefaultTenantLineHandlerTest`、[TenantIsolationArchTest](../../../apex-shared-parent/apex-infrastructure-persistence/src/test/java/com/apex/scaffold/arch/TenantIsolationArchTest.java) | 架构测试只覆盖 persistence 范围的 `@InterceptorIgnore`，不证明所有业务表与缓存键均已隔离 |

---

## 5. P1 业务开发效率

| 需求 | 状态 | 实现位置 | 证据 | 缺口说明 |
| --- | --- | --- | --- | --- |
| DEV-001 一键初始化 | `已实现` | [new-project.sh](../../../scripts/init/new-project.sh) | 脚本含参数解析、项目名/包名/端口/库名替换 | 仅替换标识与命名，不生成业务模块 |
| DEV-002 代码生成器增强 | `部分实现` | [CodeGenerator.java](../../../apex-tools-parent/apex-generator/src/main/java/com/apex/scaffold/generator/CodeGenerator.java)、[GeneratorWizardService.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/GeneratorWizardService.java)、[GeneratorAdminRouter.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/router/GeneratorAdminRouter.java) | `GeneratorWizardServiceTest`、`GeneratorTaskServiceTest`；路由含预检、预览、提交任务 | 有预检/预览/异步任务；**不含前端页面生成**，生成产物目录由 `APEX_GENERATOR_OUTPUT_ROOT` 指定 |
| DEV-003 接口契约与版本管理 | `部分实现` | [apex-api-contract](../../../apex-shared-parent/apex-api-contract/src/main/java/com/apex/scaffold/contract/)、[api-version-management.md](../../03-api/api-version-management.md) | `FeignContractSignatureTest`、`WorkflowEventContractTest`、`RouterOpenApiCoverageTest` | 有内部 Feign 契约与 `/api/v1`、`/api/v2` 并存；无自动兼容性检查与废弃流程工具化 |

---

## 6. P1 企业业务组件

| 需求 | 状态 | 实现位置 | 证据 | 缺口说明 |
| --- | --- | --- | --- | --- |
| COMP-001 通知中心 | `部分实现` | [NotificationRouter.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/router/NotificationRouter.java)、[SystemMessageRouter.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/router/SystemMessageRouter.java)、[OffMessageDeliveryEntity.java](../../../apex-components-parent/apex-office/src/main/java/com/apex/scaffold/office/off/domain/entity/OffMessageDeliveryEntity.java) | SSE `GET /api/v1/notifications/stream`；站内信收件箱/未读数；渠道 INBOX/FEISHU/DINGTALK/WECOM | 无统一通知编排与模板中心（模板 CRUD 在 office），无跨渠道重试策略 |
| COMP-002 文件与附件中心 | `已实现` | [FileShareRouter.java](../../../apex-services-bom/apex-file-service/src/main/java/com/apex/scaffold/fileservice/interfaces/router/FileShareRouter.java)、[FileRouter.java](../../../apex-services-bom/apex-file-service/src/main/java/com/apex/scaffold/fileservice/interfaces/router/FileRouter.java)、[StorageAutoConfiguration.java](../../../apex-starters-parent/apex-storage-spring-boot-starter/src/main/java/com/apex/scaffold/storage/autoconfigure/StorageAutoConfiguration.java) | `FileShareServiceTest`、`FileFlywayMigrationTest`；五后端存储 | 分享令牌为 32 字节随机值、库存 SHA-256；有效期 60 秒～7 天 |
| COMP-003 操作记录与业务审计 | `已实现` | [OperationLog.java](../../../apex-shared-parent/apex-common/src/main/java/com/apex/scaffold/common/annotation/OperationLog.java)、[ReactiveOperationLogAspect.java](../../../apex-shared-parent/apex-common/src/main/java/com/apex/scaffold/common/audit/ReactiveOperationLogAspect.java)、[AuditLogHandler.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/handler/AuditLogHandler.java) | `OperationLogTest`、`ReactiveOperationAuditTest` | 审计查询按域分布，无统一跨域审计检索 |
| COMP-004 唯一性与幂等中心 | `部分实现` | [IdempotentApi.java](../../../apex-shared-parent/apex-common/src/main/java/com/apex/scaffold/common/annotation/IdempotentApi.java)、[IdempotentService.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/IdempotentService.java)、[IdempotentApiAspect.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/aspect/IdempotentApiAspect.java) | `IdempotentApi` 注解定义与 Redis 键校验实现 | 为注解式接口幂等，**无业务唯一性中心**与幂等结果回放 |

---

## 7. P1 业务流程

| 需求 | 状态 | 实现位置 | 证据 | 缺口说明 |
| --- | --- | --- | --- | --- |
| FLOW-001 轻量业务流程编排 | `部分实现` | [WorkflowDefinitionGraph.java](../../../apex-components-parent/apex-workflow/src/main/java/com/apex/scaffold/workflow/application/validation/WorkflowDefinitionGraph.java)、[WorkflowDefinitionHandler.java](../../../apex-services-bom/apex-workflow-service/src/main/java/com/apex/scaffold/workflowservice/interfaces/handler/WorkflowDefinitionHandler.java) | `WorkflowDefinitionGraphTest`；路由 `/api/v2/workflow-definitions` 含创建/更新/校验 | **仅支持 START→APPROVAL→END 单链**，条件分支、并行、会签、驳回均不支持 |

---

## 8. P2 企业集成

| 需求 | 状态 | 证据 | 说明 |
| --- | --- | --- | --- |
| INT-001 企业身份集成 | `已实现` | [SysSsoProviderEntity.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/domain/model/entity/SysSsoProviderEntity.java)、[OidcAuthenticationSuccessHandler.java](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/security/OidcAuthenticationSuccessHandler.java) | 支持 SAML/CAS/LDAP/OIDC 配置模型与 Keycloak OIDC 登录 |
| INT-002 国产化适配 | `未实现` | — | 未发现达梦、人大金仓、openGauss、OceanBase 驱动或方言配置 |
| INT-003 企业消息与数据交换 | `部分实现` | RocketMQ 集成、Outbox | 有消息与 Outbox；无跨企业数据交换标准 |
| INT-004 规则引擎集成 | `未实现` | — | 未发现 Drools、EasyRules 或规则引擎接入 |

---

## 9. 交付版本边界

需求原文 §15 定义了三档版本应纳入的能力。按当前实现状态，三档的**实际**能力边界如下：

| 能力 | Basic | Standard | Enterprise | 依据 |
| --- | --- | --- | --- | --- |
| JWT 认证、统一响应、参数校验 | ✅ | ✅ | ✅ | `features.jwt-auth=true` |
| RBAC | ✕ | ✅ | ✅ | `features.rbac` |
| 多级缓存 | ✕ | ✅ | ✅ | `features.cache` |
| 监控 | ✕ | ✅ | ✅ | `features.monitoring` |
| RocketMQ | ✕ | ✕ | ✅ | `features.message-queue` |
| 链路追踪 | ✕ | ✕ | ✅ | `features.tracing` |
| Sentinel / 分布式锁 | ✕ | ✕ | ✅ | basic 档依赖裁剪 |
| 工作流 | — | — | — | 工作流库不参与档位裁剪，按服务部署决定 |

> 上表来自根 [pom.xml](../../../pom.xml) 的 profile 属性，与需求原文 §15 的“应纳入”清单存在差异：需求写的 Standard 应含“完整管理后台”，但管理后台不属于本仓库；需求写的 Enterprise 应含“国产化适配”，实际未实现。对外承诺按本表执行。

---

## 10. 追踪维护规则

1. 需求条目变更时，先更新 [功能增强需求方案](../function-enhancement-requirements.md)，再同步本表。
2. 状态从 `未实现` 变为 `已实现` 时，必须在“证据”列给出可运行的测试类或脚本路径。
3. 新增交付范围不得只写在计划文档；须同时登记到 [交付范围与基线](./delivery-scope.md)。
4. 每季度复核一次，重点检查“部分实现”项是否已收口或转为明确不做。

---

**维护者**: Apex Team
**下次核对**: 需求变更或每个交付版本发布前

# 限界上下文映射（Context Map）

本文件是 DDD 战略设计的现行基线（方案 M3 / 批次 C0）。代码结构与本文不一致时以代码为准，并应同步修订本文。

## 1. 上下文清单

| 上下文 | 模块 | 聚合根示例 | 职责 |
| --- | --- | --- | --- |
| 系统与租户治理 | `apex-system` | `UserEntity`、`RoleEntity`、`TenantEntity`、`TenantPackageEntity` | 用户/组织/角色/菜单、租户目录与生命周期、套餐与配额、平台配置 |
| 认证授权 | `apex-auth` | `UserEntity`（认证侧）、`TokenEntity`、`SessionEntity`、`OAuth2Authorization` | 登录、令牌签发/轮换/撤销、OAuth2 四种授权、consent、MFA |
| 工作流 | `apex-workflow` | `WorkflowDefinition`、`ProcessInstance`、`Task` | 流程定义（DAG）、实例推进、任务审批 |
| 文件 | `apex-file` | `DocumentEntity`、`FileEntity`、`FileChunkEntity`、`FileShareEntity` | 上传/分片/下载、分享、存储后端适配 |
| 办公协同 | `apex-office` | `OfficeMessage`、`NotifyTemplate`、`MeetingRoomEntity`、`OfficeOrganizationMapping` | 站内信与投递、办公平台同步、会议室 |
| 对外集成与设备 | `apex-integration` | 连接器配置、设备注册/影子/命令模型（connector 与 device 两个子域） | 出站连接器调用、租户凭据、入站 Webhook、IoT 设备接入（MQTT 由外部 EMQX 承载） |
| 网关（薄适配层） | `apex-gateway` | —（无业务聚合） | 路由、验签、限流、灰度、访问日志 |
| 查询聚合（只读侧车） | `apex-aggregation-service` | —（无本地聚合，无数据库） | 跨域只读聚合：统一待办 `GET /api/v2/todo`（经内部签名契约拉取 workflow 任务与 office 待审批/未读站内信）；个人维度数据不缓存、不合并 |

## 2. 上下文关系（合作模式）

```mermaid
graph LR
    GW[网关<br/>薄适配层] -->|HTTP 路由| SYS[系统与租户治理]
    GW --> AUTH[认证授权]
    GW --> WF[工作流]
    GW --> FILE[文件]
    GW --> OFFICE[办公协同]
    GW --> INT[对外集成与设备]

    SYS -->|防腐层 UnifiedAuthorizationGateway<br/>/internal/v1/**| AUTH
    OFFICE -->|防腐层 SystemOrgUserQueryAdapter<br/>内部组织/用户查询| SYS
    FILE -->|防腐层 TenantStatusPort<br/>租户状态| SYS
    AUTH -->|防腐层 TenantStatusPort<br/>登录前置租户校验| SYS
    WF -->|发布领域事件（Outbox + MQ）| CONTRACT[api-contract 事件契约]
    SYS -->|发布主数据事件<br/>UserChanged / TenantChanged| CONTRACT
    AUTH -->|订阅 TenantChanged| CONTRACT
    SYS -->|Overview 聚合 /internal/v1/*/stats| STATS[各域统计端点<br/>file / office / workflow / integration]
    AGG[查询聚合侧车<br/>apex-aggregation-service] -->|统一待办门面 /api/v2/todo<br/>内部签名 /internal/v1/*/todo| WFT[工作流待办端点]
    AGG -->|内部签名 /internal/v1/office/todo| OFT[office 待审批/未读端点]

    SHARED[共享内核<br/>apex-common / apex-domain] -.->|被全部上下文依赖| SYS
    SHARED -.-> AUTH
    SHARED -.-> WF
    SHARED -.-> FILE
    SHARED -.-> OFFICE
    SHARED -.-> INT
    SHARED -.-> GW
```

| 关系 | 模式 | 约束 |
| --- | --- | --- |
| 网关 → 业务上下文 | 客户-供应商（网关为调用方） | 网关不实现业务逻辑，仅路由与横切；`GatewayArchTest` 强制 |
| 系统 ← 认证/文件/办公 | **防腐层（ACL）** | 跨上下文只能经 `domain/gateway` 端口 + `/internal/v1/**` 内部契约（端点清单见 `docs/03-api/internal-api-contracts.md`）；不得直接读取对方库表。integration 不经 ACL 消费 system（其权限校验走 auth `/auth/v1` 公开契约，统计数据由 system 经 `/internal/v1/iot/devices/stats` 拉取） |
| 工作流 → 其他上下文 | **事件驱动（Outbox + MQ）** | 发布方经 `apex-api-contract` 事件契约类型化产出；2026-11-07 核实仓内暂无消费方接入，后续消费必须经同一契约，禁止同步回调 |
| 系统 → 认证 | **主数据事件（User/TenantChanged）** | 经 `apex-api-contract` 契约发布；消费方（auth 租户事件监听器）解析入站载荷并幂等失效本地缓存 |
| `apex-common` / `apex-domain` | **共享内核** | 只容纳跨域复用契约（上下文、结果、异常、基础实体、通用端口）；不得出现具体业务实体 |
| 所有上下文 | 独立数据库 + 独立 Flyway 链 | 不跨库事务；一致性经既有事件/补偿模式 |

## 3. 编码约束（ArchUnit 固化）

由 `LayeringArchTest`（system）、`GatewayArchTest`、`TenantIsolationArchTest`，以及 M3 新增的跨上下文守卫共同保证：

1. **分层**：domain 不依赖 interfaces/infrastructure；application 不依赖 interfaces/infrastructure（既有）。
2. **跨上下文**：业务库之间不得相互 `import` 对方的 `domain/application/infrastructure` 包；跨上下文只能经 `apex-domain` 的 gateway 契约、`apex-api-contract` 的 DTO/事件，或 HTTP 防腐层（M3 新增）。
3. **事件发布**：业务代码不得直接持有 MQ 客户端；领域事件必须经 `DomainEventPublisher` / `DomainEventBus` 端口（M3 新增）。
4. **租户隔离**：持久层禁用 `@InterceptorIgnore`（既有 `TenantIsolationArchTest`）。
5. **时间字段**：指定命名的时间字段类型受 `TimeFieldArchTest` 约束（既有）。

## 4. 聚合与值对象示范基线

每业务库选定一个核心聚合做示范深化（不推广为全仓重写）：

| 上下文 | 示范聚合 | 已体现的战术模式 |
| --- | --- | --- |
| 系统与租户治理 | `TenantEntity` + `TenantQuota` | 值对象封装（`TenantQuota` MB/用户数口径），不变量集中在聚合根（生命周期状态机 + 配额断言），领域异常（`TenantLifecycleException` / `TenantQuotaExceededException`） |
| 认证授权 | `TokenEntity` + `TokenPair` | 轮换族（`tokenFamily`/`previousJti`）、撤销状态机、值对象承载签发结果 |
| 文件 | `DocumentEntity` | `attachFile` 行为方法维护文件元数据不变量 |

后续新增聚合应参照以上基线：不变量放聚合根、口径封值对象、跨聚合一致用领域事件。

## 5. 演进方向

- C7（已交付）：六个业务库均具备组件入口（`autoconfigure` + `AutoConfiguration.imports` + `apex.component.<域>.enabled` 总开关）。协议层归属两种形态并存：auth/file/office/workflow 在运行模块（Phase 6 历史形态）；integration 为自含协议层组件试点（接口层在库内、`apex.component.integration.interfaces-enabled` 开关守护），使 boot-all 薄 jar 能提供 integration 域 HTTP；system 接口层维持业务库现状（D07 裁决，见下）。
- C8/C9（已交付）：事件通道抽象（`apex.events.channel` local/remote 自适应）与组合部署拓扑（`apex-boot-all` 按域子上下文装配）已上线。
- C4/D07（已裁决）：apex-system interfaces 层迁移经 2026-11-01 裁决维持现状不实施（记录见 `docs/08-project-management/plans/unified-auth-tenant-platform-remediation-plan.md` v3.2、`docs/08-project-management/plans/platform-closure-remediation-plan.md`）；包根命名空间差异如需变更须单独立项。

# 能力目录（中台能力运营）

本页是中台"能力目录"的**文档侧呈现**，与代码侧单一数据源
[CapabilityCatalog](../../apex-system/src/main/java/com/apex/scaffold/application/capability/CapabilityCatalog.java)
以契约测试对齐（`CapabilityCatalogDocContractTest`：本页能力表与代码登记的能力编码、
归属中心、提供组件、开放 scope 必须完全一致，任一侧变更未同步即测试失败）。

## 1. 能力清单

租户可订阅的能力以套餐 `features` JSON 声明（见租户套餐管理）；未声明 `features`
的套餐视为全量订阅。开放 scope 属技术授权范围，与套餐订阅同时生效（scope 允许 +
租户已订阅能力，调用才成功），见[开放平台接入契约](../03-api/open-platform-integration.md)。

| 能力编码 | 能力名称 | 归属中心 | 提供组件 | 开放 scope | 计量维度 | 说明 |
| --- | --- | --- | --- | --- | --- | --- |
| identity-auth | 统一认证与授权 | 认证授权中心 | apex-auth | open:user:read | 调用次数 | 登录、令牌签发/轮换/撤销、OAuth2 授权、consent、MFA |
| user-org | 用户与组织 | 用户组织中心 | apex-system | open:user:read | 调用次数 | 用户、部门、岗位、角色与菜单权限 |
| workflow | 工作流 | 工作流中心 | apex-workflow | open:workflow:read, open:workflow:write | 调用次数 | DAG 流程定义、实例推进、任务审批 |
| file-storage | 文件与对象存储 | 文件中心 | apex-file | open:file:read, open:file:write | 调用次数, 存储用量 | 上传/分片/下载、分享、五后端存储 |
| office-message | 统一消息与协同 | 消息中心 | apex-office | open:message:send | 调用次数, 消息量 | 站内信、办公平台 IM、通知模板与投递 |
| tenant-governance | 租户治理 | 租户治理中心 | apex-system | 无 | 调用次数 | 租户目录/生命周期/套餐/配额/租户级配置 |
| integration-connector | 系统集成连接器 | 集成中心 | apex-integration | 无 | 调用次数 | 外部系统连接器、凭据、出站重试、入站 webhook（M5 交付） |
| iot-device | 物联网设备接入 | 集成中心 | apex-integration | 无 | 调用次数, 设备数 | 设备注册/认证、遥测上行、命令下行、规则引擎（M5 交付） |

## 2. 计量维度口径

用量查询 API：`GET /api/v2/capability-usage`（权限码 `capability:usage:read`，
见 `CapabilityUsageHandler`）。各维度数据来源：

| 维度 | 来源 | 说明 |
| --- | --- | --- |
| 调用次数 | `sys_operation_log` 按 `request_uri` 前缀聚合 | 时间段指标；前缀映射见 `apex.capability-usage.mappings` |
| 存储用量 | file 内部端点 `/internal/v1/file/stats` | 存量指标（当前值，非时间段） |
| 设备数 | integration 内部端点 `/internal/v1/iot/devices/stats` | 存量指标；需显式 `tenantId` 参数 |
| 消息量 | office 内部端点 `/internal/v1/office/messages/stats` | 存量指标；需显式 `tenantId` 参数 |

- 存量维度仅在查询指定**单个租户**时填充；平台全量视角（不指定租户）仅返回调用次数。
- 存量维度数据源不可达时对应字段返回 `null`（按源降级，不影响调用次数）。
- 只读计量，不做计费（方案 D2/D4 明确不做项）。

## 3. 能力与组件的关系

- 每个能力由一个组件（业务库）承载，组件经 C7 组件化入口
  （`*ComponentAutoConfiguration` + `apex.component.<name>.enabled`）可拔插，
  组合部署与拆分部署拓扑见[部署架构](./architecture-overview.md)。
- 一个组件可承载多个能力（如 `apex-integration` 同时提供
  `integration-connector` 与 `iot-device`，两者可独立订阅）。
- 能力的开放 scope 登记在认证中心
  [OpenAppScopes](../../apex-auth/src/main/java/com/apex/scaffold/auth/openplatform/OpenAppScopes.java)；
  网关按路由元数据 `metadata.open-api.capability` 做租户订阅门禁，服务侧按同一
  `CapabilityGate` 判定做二次校验。

## 4. 变更流程

新增/调整能力时同步修改三处，契约测试保证一致：

1. 代码侧目录：`CapabilityCatalog#buildCatalog` 登记一行；
2. 本页 §1 表格同步一行；
3. 涉及开放能力时在 `OpenAppScopes` 登记对应 scope，并在网关
   `/open-api/**` 路由元数据声明 capability 与 read/write-scopes。

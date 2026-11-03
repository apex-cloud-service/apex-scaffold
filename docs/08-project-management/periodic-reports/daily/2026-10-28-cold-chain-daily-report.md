# 工作日报（2026-10-28）

**汇报人：** 后端开发  
**项目：** 冷链管理系统 V2.0  
**日期：** 2026 年 10 月 28 日

## 一、今日工作概览

今日以业务能力补齐与跨服务联调为主，同时推进工程可验证性建设。业务侧完成文件限时只读分享、办公站内信三通道投递与增量收件箱、办公组织同步到系统真实用户的跨服务解析，以及工作流定义 DAG 校验与超时处理收敛；工程侧把文档链接检查从内嵌脚本迁移为 Java 离线工具并接入周度运维与 CI 校验，补充数据脱敏、Seata、XXL-Job 等运维文档，并同步刷新架构图与模块结构图。

## 二、主要工作及完成情况

### 1. 完成文件限时只读分享能力

- file 域按 DDD 分层新增完整链路：领域层 `FileShareEntity`、`FileShareRepository`、`TenantStatusPort` 端口，应用层 `FileShareService` 与三个 DTO，基础设施层 `FileShareMapper` / `FileSharePO` / `FileSharePoConverter` / `FileShareRepositoryImpl`。
- 接口层新增 `FileShareRouter` / `FileShareHandler`：`POST /api/v2/files/{fileId}/shares` 创建、`DELETE /api/v2/file-shares/{shareId}` 撤销、`POST /api/v2/file-shares/exchange` 兑换、`POST /api/v2/file-shares/download` 下载；后两者为匿名入口，网关与服务安全配置只放行这两个精确路径。
- 安全设计：令牌为 32 字节随机值，库内只存 SHA-256 摘要，明文仅在创建响应返回一次；兑换时的租户与文件完全由服务端分享记录确定，不信任匿名请求携带的身份；仅 `STORED` 文件可分享，过期、撤销、文件删除或租户停用后拒绝兑换；有效期限制在 60 秒至 7 天。
- 配套 MySQL / PostgreSQL 双库迁移 `V6__create_file_share.sql`（唯一键 `token_hash`，文件、租户、创建人、过期四个索引），并新增 `FileShareServiceTest`、更新 `FileHandlerIntegrationTest` 与 `FileFlywayMigrationTest`。

### 2. 完成办公站内信渠道投递与增量收件箱

- 新增 `off_message_delivery` 渠道投递维度：`OffMessageDeliveryEntity` / `OffMessageDeliveryRepository` / PO / Mapper / Converter / 仓储实现，MySQL / PostgreSQL 双库 `V5__create_off_message_delivery.sql`，以 `message_id + recipient_id + channel + deleted` 作唯一键，已读归属接收记录表、送达与失败归属投递表，互不覆盖。
- `OffMessageService` 完成重构：发送侧改为单次原子保存消息与接收人，仅在本地事务提交后发布 `MessageCommittedEvent`，提示失败不改变已提交结果、由 HTTP 补偿；查询侧统一经 `TenantContext.withTenant` 注入租户，分页参数收敛为 1–100。
- 消息详情增加归属校验：非发件人必须存在同租户接收记录才可见；新增基于接收记录 ID 的游标分页 `getInboxCursor`，单次上限 100 条，规避深分页。
- 补充 `V6__office_sync_task_sync_type.sql` 同步类型字段，并新增 `OffMessageServiceTest`、`MessageOwnershipTest`、`MessageDatabaseWebSocketTest`、`MessageGatewayDatabaseIntegrationTest` 覆盖归属、断线与网关链路。

### 3. 完成办公组织同步到系统用户的跨服务解析

- system 侧新增组织目录内部接口 `POST /internal/v1/org/users/resolve`：`InternalOrganizationRouter`、`InternalOrganizationHandler`、`OrgUserResolveRequest` / `OrgUserResolveResponse`，按手机号、邮箱或登录名批量解析本租户真实用户，单次上限 200 条，`userId` 提供时以主键优先匹配。
- 该接口只登记在 `SystemConstants.INTERNAL_ORG_PATHS` 内部路径，由 `SecurityConfig` 的桥接过滤器把 `X-Tenant-Id` 写入 Reactor Context，仅服务集群内直连、不经网关也无需用户令牌。
- office 侧新增出站适配器 `SystemOrgUserQueryAdapter` 与 `SystemOrgUserProperties`，配置解析基址、`X-Tenant-Id` 与响应 512KB 解码上限；基址未配置、非成功响应、超时或远程异常时返回空列表并告警，只影响成员绑定、不影响部门与员工镜像写入。
- 新增 `OfficeOrgUserQueryPort`、`OfficeOrgUserRef`、`OfficeOrgUserSnapshot` 端口与 `OrganizationDirectoryApplicationService` 相关实现，补充 `OrganizationDirectoryApplicationServiceTest`、`OfficeOrganizationSyncServiceTest`。

### 4. 完成工作流定义校验与超时处理收敛

- 新增 `WorkflowDefinitionGraph`，对定义做 DAG 结构校验：首期只支持开始、审批、结束三类节点，要求自开始节点出发的单链结构，每个非结束节点恰有一条出边、链尾必须落到结束节点、所有节点可达；条件分支、未知节点类型、悬空边与环路明确拒绝，错误信息带节点或连线标识。
- 重构 `WorkflowTimeoutHandler`（约 279 行变更），收敛超时扫描的租户上下文与并发语义，并新增 `WorkflowTimeoutRaceIntegrationTest`、`WorkflowDefinitionGraphTest`、`WorkflowServiceTest`、`WorkflowAuthorizationTest`。
- 同步更新 `V65` 站内信同步向导权限种子（MySQL / PostgreSQL）与 office 同步向导处理器 `OfficeSyncWizardHandler`、`SyncPreviewRequest` / `SyncPreflightRequest`。

### 5. 完成文档链接检查 Python 迁移为 Java 离线工具

- 新增 `DocLinksChecker`、`DocSearchServer`、`HtmlEntities` 与 `html5-entities.txt` 资源，逐行对齐原 `check-doc-links.sh` 内嵌 Python 实现，契约保持不变：文件清单以 NUL 分隔、字节排序后作为第一参数传入，输出「坏链接（原因）: 文件:行号: 目标 -> 绝对路径」，存在断链时退出码 1。
- 校验项覆盖路径存在、大小写精确匹配、目标被 `.gitignore` 排除、Markdown 标题与 HTML 显式锚点，目录链接以其下 `README.md` 为锚点文档；配套 `DocLinksCheckerTest`、`DocSearchServerTest`、`HtmlEntitiesTest`。
- 周度运维脚本 `scripts/ops/maintenance/weekly-link-check.sh` 改为调用 Java 实现，移除原内嵌 Python 粗粒度复查；新增 GitHub Actions 工作流 `doc-link-check.yml`，并更新 `scripts/ci/check-doc-links.sh` 与 `start-doc-search.sh`。
- 今日实跑结果：679 个 Markdown 文件、3125 条链接、断裂链接 0，报告写入 `docs/reports/link-check-20261028.md`。

### 6. 完成运维文档补齐与架构图刷新

- 新增运维文档：`docs/05-operations/data-masking.md`、`in-app-messaging.md`、`object-storage-integration.md`、`seata-integration.md`、`xxl-job-integration.md`，以及架构演进方案 `jdk21-unified-auth-multitenancy-implementation-plan.md`。
- 新增交付与测试留档：`seata-integration-summary-report-20261026.md`、`seata-real-modes-test-report.md`、虚拟线程压测报告目录，以及基础设施模块化、服务增强、存储重构三份实施计划与对应交付报告。
- 重新生成 106 个架构图（`.mmd` + `.svg` 成对维护），同步修正模块依赖图、基础设施结构、认证中心结构、文件域 UML、安全防护与工作流审批流程等图示，使图示与当前模块边界一致。
- 更新 `AGENTS.md`、`.env.example`、`docs/05-operations/maintenance-scripts-guide.md` 等协作与运维说明。

## 三、今日成果数据

| 类别 | 完成情况 |
| --- | --- |
| 今日改动文件 | 129 个（不含 `docs/` 与构建产物），另有 189 个文档与 106 张图示更新 |
| 业务新增能力 | 文件限时只读分享（4 端点）、站内信渠道投递与游标收件箱、组织目录用户解析、工作流 DAG 校验 |
| 数据库迁移 | file `V6`、office `V5` / `V6`、system `V65`，MySQL / PostgreSQL 双库配对 |
| 工具迁移 | `DocLinksChecker` / `DocSearchServer` / `HtmlEntities` + 3 个测试类，脚本 Python 复查下线 |
| 文档链接检查 | 679 个文件、3125 条链接、断裂 0（`link-check-20261028.log`） |
| 运维文档 | 新增 5 篇运维文档 + 1 篇架构方案 + 3 份交付报告 |
| 架构图 | 106 个图示文件（`.mmd` 与 `.svg` 成对）刷新 |
| 工作区总规模 | 1031 个文件变更，新增 20795 行、删除 63395 行 |

## 四、风险与限制

1. 本轮未执行完整回归门禁 `mvnd -T4 -B clean verify`，今日新增的文件分享、站内信投递、组织解析与工作流校验仅有单元与局部集成测试覆盖，缺少全量门禁结论。
2. 文件分享为新增匿名入口，令牌摘要存储、租户停用后拒绝兑换、有效期上下限等分支需在集成环境做端到端验证；组织的内部解析接口只服务集群直连，容器网络与 `X-Tenant-Id` 注入未在真实部署下验证。
3. 站内信投递新增 `off_message_delivery` 表与事件发布语义，跨实例实时广播仍依赖内存票据与连接注册表，断线补偿依赖 HTTP 收件箱，多实例场景未做压测。
4. 文档链接检查由 Python 迁移为 Java 后契约对齐依赖测试保障，外部 URL 与非 Markdown 文件内部位置仍不在校验范围内。

## 五、明日计划

- 执行完整回归门禁 `mvnd -T4 -B clean verify`，补齐今日新增能力的全量验证结论。
- 在容器化环境验证文件分享匿名兑换、组织目录内部解析与站内信断线补偿的真实链路。
- 完成工作区 1031 个文件变更的分域复核，按模块整理可提交范围。
- 补充站内信多实例场景与文件分享下载链路的集成测试。

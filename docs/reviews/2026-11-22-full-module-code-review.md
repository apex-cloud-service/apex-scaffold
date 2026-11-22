# 全模块代码评审清单（BE-049，2026-11-22）

> 关联：`docs/tasks/project-tasks.md` BE-049；执行记录 [`docs/records/2026-11-22-code-review-pattern-spi-lambda.md`](../records/2026-11-22-code-review-pattern-spi-lambda.md)。
> 覆盖口径：每模块先列生产/测试文件数与内部依赖，再深读核心类，其余按包扫描。**未逐行通读全部生产文件**，各行的「覆盖方式」如实标注。

## 1. 模块覆盖与结论

| 模块 | 生产/测试 Java | 覆盖方式 | 结论 |
| --- | ---: | --- | --- |
| `apex-common` | 111 / 62 | 深读 scheduler、internal 验签、masking、util、result | 新增 `InternalEndpointErrors`；脱敏/日志工具已有委托，保留 |
| `apex-domain` | 19 / 1 | 深读 `DomainEventBus` 与端口 | BE-044 已修注释与返回语义，无新增 |
| `apex-api-contract` | 19 / 4 | 扫描 DTO 与事件契约 | 无变更 |
| `apex-infrastructure-persistence` | 15 / 14 | 深读 datapermission 全链路、MyBatis 配置 | 修复 WHERE 合并括号绕过；新增 2 条回归 |
| `apex-infrastructure-cache` | 37 / 20 | 深读多级缓存与失效 | 发现 L1/L2 TTL 与 stale 语义疑点，未复现，列入 §2 |
| `apex-infrastructure-messaging` | 41 / 26 | 深读自动配置、Redisson 条件、Outbox 执行器 | BE-044 已修让位；补排序场景建议列入 §2 |
| `apex-infrastructure-remote` | 22 / 8 | 扫描 WebClient 装配与重试 | 无变更 |
| `apex-infrastructure-integration` | 43 / 15 | 深读 SPI、短信/解密策略 | 无变更 |
| `apex-infrastructure`（兼容 JAR） | 0 / 0 | 仅 POM | 无变更 |
| `apex-system` | 870 / 277 | 深读 SSO、登录策略、搜索适配、认证过滤器、Handler 基类 | SSO 注册表、Meilisearch 资源注册表；其余包级扫描 |
| `apex-auth` | 191 / 66 | 深读 Token 仓储、撤销流、权限查询、数据源 | 撤销游标语义保持不变（回退尝试已撤销） |
| `apex-workflow` | 47 / 6 | 深读事件与领域端口 | 无变更 |
| `apex-file` | 78 / 10 | 深读存储端口、分享、审计填充 | 无变更 |
| `apex-office` | 211 / 19 | 深读通知渠道、日历端口、站内信 | 见 §2 分页过滤候选 |
| `apex-integration` | 117 / 28 | 深读连接器、设备规则、MQTT 条件 | BE-044 已修，无新增 |
| `apex-knowledge` | 69 / 18 | 深读摄取、分块、检索、RAG 编排 | BE-044 已修 embedding，无新增 |
| `apex-storage-spring-boot-starter` | 11 / 3 | 深读 SPI 加载、注册表、条件装配 | 新增真实描述符发现测试 |
| `apex-license-spring-boot-starter` | 15 / 3 | 深读指纹采集与校验 | 已是策略形态，无变更 |
| `apex-job-spring-boot-starter` | 7 / 3 | 深读执行器条件 | 无变更 |
| `apex-seata-spring-boot-starter` | 7 / 16 | 深读模式条件与数据源代理 | 装配时序变更风险高，不改 |
| `apex-gateway` | 38 / 24 | 深读 JWT 验签、撤销流、过滤器顺序 | 撤销保留期修正；JWKS 旧 kid 清理列入 §2 |
| `apex-system-service` | 2 / 2 | 深读启动与扫描 | 无变更 |
| `apex-auth-service` | 40 / 32 | 深读安全链、审计 Handler、内部端点 | `InternalEndpointErrors` 接入面核对；auth 自身映射不等价，保留 |
| `apex-workflow-service` | 34 / 21 | 深读内部端点、Outbox 派发、MQ 发布 | Outbox 抢占行数校验 |
| `apex-file-service` | 24 / 9 | 深读安全过滤器、内部 Handler、配置 | `InternalEndpointErrors` 接入 |
| `apex-office-service` | 92 / 43 | 深读 WebSocket 票据、内部 Handler、站内信 | `InternalEndpointErrors` 接入；见 §2 |
| `apex-integration-service` | 11 / 7 | 深读装配与安全 | 无变更 |
| `apex-aggregation-service` | 8 / 7 | 深读统一待办与认证 | 映射不等价，保留 |
| `apex-job-service` | 2 / 2 | 扫描 | 无变更 |
| `apex-masking-service` | 6 / 3 | 深读脱敏接口 | 无变更 |
| `apex-knowledge-service` | 20 / 11 | 深读 SSE、检索端点 | 无变更 |
| `apex-example` | 12 / 5 | 扫描 | 无变更 |
| `apex-boot-all` | 4 / 5 | 深读装配与上下文测试 | 无变更 |
| `apex-license-tools` | 1 / 0 | 扫描 | 无变更 |
| `apex-generator` | 7 / 1 | 深读类型映射与模板 | dbType/javaType 表驱动候选列入 §2 |
| `apex-tools-deployment` | 21 / 8 | 深读命令入口、文档检查、MySQL 客户端 | 新增 `TestReportSummary` 与子命令 |
| `apex-tools-build` | 0 / 6 | 深读 manifest 校验测试 | 无变更 |

45 个 reactor 项目全部有归属；8 个 POM 聚合项目仅核对 modules 与 parent，无源码。

## 2. 记录但不本轮修改的候选

以下均为**静态发现，未通过测试复现**，且改动会扩大契约/装配面，按「不改契约」边界留待独立任务评估。

| # | 位置 | 发现 | 处置 |
| --- | --- | --- | --- |
| C1 | `apex-gateway/.../security/GatewayJwtVerifierService`（JWKS 快照） | 拉取仅 `putAll` 不删除已下线 `kid` | 单独评估缓存淘汰语义，需与轮换窗口对齐 |
| C2 | `apex-infrastructure-cache` 多级缓存 | L1 TTL 长于 L2、stale 语义不可实现 | 需先确认真实部署配置再改 |
| C3 | `apex-office-service/.../OfficeInternalHandler.unreadMessageSection` | `getInbox(1, limit)` 后过滤未读，可能少取 | 需与 `OffMessageService` 增加未读查询端口，属契约变更 |
| C4 | 六个 `IdentityRelayAuthenticationFilter` | 验签内核同构但域分支不同（开放应用、HMAC 开关、文件访问上下文、WebSocket 票据） | 已确认不可整体合并；如抽取需完整差异矩阵与正反向测试 |
| C5 | `apex-office-service` 普通用户分支 | `verifyClaims` 与 `verify` 对同一令牌重复 RSA 验签 | 可局部复用 Claims；需确认无副作用 |
| C6 | `apex-auth-service/.../RequestHandlerUtil` | 技术异常原文回传客户端；未知业务码 400 | 涉及错误契约与信息暴露，需安全评审 |
| C7 | `apex-generator` `CodeGenerator` / `FieldDefinition` | dbType/javaType 映射为 switch | 表驱动收益有限，且属生成器输出稳定性范围 |
| C8 | `apex-tools-deployment/.../MysqlClient` | 写入阶段不受 90 秒硬超时约束 | 离线运维工具，影响面小，单独评估 |
| C9 | `apex-system/.../MeilisearchAdapter.maskPhone` | 非 11 位原样返回 | 与公共脱敏边界不同，改动影响索引内容，需确认产品口径 |
| C10 | `apex-auth` `InternalAuthService.revocationsSince` | `revoked_at` 为 MySQL 秒级 `TIMESTAMP`，查询用 `revoked_at > since`；若单批在末秒被 `limit` 截断，同秒剩余记录不会被后续批返回 | 本轮尝试「游标回退 1 秒」后导致游标无法推进、记录永久重放（既有测试失败），已回退。正确做法需引入 `(revoked_at, id)` 复合游标或时间戳改毫秒精度，属契约变更，须独立评估 |

## 3. 明确不改的项

- storage 五后端条件装配、connector 内置优先、seata 四模式 Condition：已落地且回归面大。
- `SensitiveDataUtil → DataMaskUtil`、`LoggingFilter → LogSafetyUtil`：已有正确委托，非重复。
- `HttpStatusUtil` 与内部端点映射：语义不同（未知码 500 vs 200），不互换。
- `LoginStrategyProvider` 默认首策略语义：向后兼容依赖，不改为抛异常。
- auth/workflow/aggregation 的内部端点错误映射：与 file/office 不等价，保持原实现。

## 4. 复核要点（供 R5）

1. 确认 SSO 工厂构造器签名与 Spring 注入路径未变。
2. 确认 `MeilisearchAdapter` 三处 `switch` 删除后，六类资源的过滤条件、排序、时间格式逐字未变。
3. 确认 `InternalEndpointErrors` 的状态码分派与原两处实现逐字等价。
4. 确认撤销保留期默认值与「游标 = 本批最大撤销时间、空批不回退」语义（游标回退尝试已撤销，风险见 C10）。
5. 确认数据权限仅在 `OR` 根节点加括号，其余 SQL 输出形态未变。
6. 确认报告解析器禁用 DOCTYPE、缺失报告不判通过、发行版脚本入口未改调用方式。
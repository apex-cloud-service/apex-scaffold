# 中台收尾方案：开放 API 闭环、注册表统一、boot-all 隔离与外部端到端验收

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

- 状态：**实施中**（D21/D22/D23/D24/D25 已按推荐方案采纳；F/G/H 批主要代码已落地，外部端到端验收仍待环境）
- 实施进展（2026-11-04）：F 批四域开放路由与机器身份、G 批 auth 单源注册与 system 代理/迁移导入、H 批分域子上下文与 file 域真实启动验证已完成；I 批脚本与真实外部联调未完成，见 §12
- 编制日期：2026-11-04
- 前置事实：本方案承接 [统一认证/多租户/中台收口方案](unified-auth-tenant-platform-remediation-plan.md) 收口后的遗留项，并假定其配套修复已入库——开放应用停用校验、内部契约 HMAC 签名（`apex.internal-auth.*`）、能力守卫路径化判定、工作流 Outbox 同事务落箱、能力计量零调用口径。上述基础改变了本方案若干批次的技术前提（如注册表同步可直接复用已签名的内部通道）。
- 关联文档：[开放平台接入契约](../../03-api/open-platform-integration.md)、[能力目录](../../02-architecture/capability-catalog.md)、[验收计划](../delivery/acceptance-plan.md)、[AGENTS.md](../../../AGENTS.md)

## 1. 遗留问题基线（本方案的事实起点）

| 编号 | 问题 | 现状证据 | 影响 |
| --- | --- | --- | --- |
| R-1 | 网关 `/open-api/**` 以原路径转发，四个业务域无承接路由，应用令牌到不了业务 Handler | 网关 [application.yml](../../../apex-services-bom/apex-gateway/src/main/resources/application.yml) 开放路由组仅配 CircuitBreaker；各服务 Router 无 `/open-api` 注册 | 开放平台不可用，D2/D4 端到端断裂 |
| R-2 | 业务服务身份过滤器只认个人 `ACCESS` 令牌（`userId+tenantId`），不识别应用令牌 | file/office/workflow 各自 `IdentityRelayAuthenticationFilter`；system 走 `JwtAuthenticationFilter` | 即使补路由，应用令牌仍被 401 |
| R-3 | 开放应用双注册表：system `oauth2_registered_client`（管理面写入）与 auth `auth_config`（签发面读取）互不相通 | system `OpenAppAdminService` 写前者；auth `OAuth2Service.client()` 读后者 | 经 `/api/v1/open-apps` 注册的应用根本无法取得令牌；`DISABLED` 已在签发侧拦截，但"注册即失效"仍在 |
| R-4 | `apex-boot-all` 非完整装配：入口仅扫 bootall 包，业务库组件入口不携带 MapperScan/数据源/迁移链，协议层在运行模块 | `BootAllApplication` + 各库 `*ComponentAutoConfiguration`；装配测试不启动上下文 | "任意组合拔插"（G3）无实证形态 |
| R-5 | 外部组件端到端未验证：真实 RocketMQ 投递/消费、EMQX 钩子、六服务全栈、告警通知、备份恢复演练 | 既有脚本仅覆盖 MQTT 基线（[run-mqtt-integration.sh](../../../scripts/test/run-mqtt-integration.sh)）；其余为部署阶段待办 | 不能宣称生产验收完成 |

明确不在本方案范围：数仓/ETL/计费（D13 已裁决不做）、C4 interfaces 迁移（已裁决维持现状）、多数据中台能力扩展。

## 2. 目标与非目标

**目标**

- G-A：第三方应用经网关完成"注册 → 取令牌 → 调用能力 → 审计计量"的真实闭环，四条开放路由至少各有一条可用端点。
- G-B：开放应用注册表单源化，注册/启停/轮换在签发面即时生效，存量数据完成一次性迁移。
- G-C：`apex-boot-all` 提供真实可启动的单进程多域形态（每域独立上下文、数据源、迁移链、端口），并保留拆分部署为生产默认。
- G-D：外部依赖端到端验收脚本化，结果可复现、可入档。

**非目标**

- 不引入第二授权服务器、不改变 OAuth2 对外协议。
- 不做开放平台计费与自定义 HMAC 请求签名（维持既有"明确不做"）。
- 不把 basic 档离线能力作为生产形态；boot-all 仍定位 basic/示例/演示档。

## 3. 关键决策点（须确认后进入实施）

| 编号 | 决策 | 推荐 | 备选与代价 |
| --- | --- | --- | --- |
| D21 | 应用机器身份模型 | **A：主体服务账号绑定**——注册时可选填 `openPlatform.subjectUserId`（须存在、活跃、同租户），签发令牌携带 `sub_uid` claim，网关注入 `X-Open-Api-Subject`；file/workflow 写操作以主体账号为属主/发起人，审计同时记录 `client_id` | B：仅租户级端点（file 不开放写）。实现小但开放能力缩水，第三方"代用户上传/发起"场景不覆盖 |
| D22 | 注册表单源 | **auth `auth_config` 为唯一签发源**；system 管理面经内部 API 代理写入，本地 `oauth2_registered_client` 对开放应用停止写入并弃用 | 双写过渡：实现期兼容但长期漂移风险高，需额外一致性巡检 |
| D23 | boot-all 装配形态 | **service adapter 分类产物 + 单 JVM 子上下文**：`*-service` 以 classifier 产出可复用装配 JAR（非 fat jar），boot-all 逐域 `SpringApplicationBuilder` 子上下文（独立端口/数据源/MapperScan/Flyway 位置），父上下文仅共享 common | 复制装配代码到 boot-all：维护两份、必然漂移，违背"仅装配不含业务实现" |
| D24 | file 开放写语义 | 依赖 D21：选 A 时开放上传/下载以主体账号为属主；选 B 时不开放 file 写 | 若 A 但不绑定主体，会出现无属主文件，破坏既有 `FileAccessContext.authorize` 不变量 |
| D25 | 存量迁移与兼容窗口 | 一次性同步脚本 + 只读比对期一个发布周期；期间 system 管理面写 auth 源、读本地仅作展示回退 | 直接切换无回退窗口；双写长期并存违背 D22 |

## 4. 批次 F：开放 API 闭环（R-1/R-2，依赖 D21/D24）

| 项 | 内容 |
| --- | --- |
| F1 机器身份 | auth `OAuth2Service.jwt()` 增写 `sub_uid`（D21-A 时取注册绑定的主体账号）；网关 `OpenApiScopeGatewayFilter` 校验后注入 `X-Open-Api-Subject`；`UserContextRelayFilter` 放行应用令牌的该头（伪造值仍由验签层剥离） |
| F2 服务端令牌语义 | file/office/workflow `IdentityRelayAuthenticationFilter` 与 system `JwtAuthenticationFilter` 增加"开放应用分支"：仅当路径前缀 `/open-api/` 且携带网关能力标记时，接受 `client_id+tenant_id+token_use=access` 令牌，构建应用主体（含 `sub_uid` 映射的用户身份，供属主校验复用）；普通业务路径维持只认个人令牌 |
| F3 开放路由 | 每域新增 `OpenApiRouter`：file `GET/POST /open-api/file|files`（D24 语义）、workflow `GET /open-api/workflow/definitions|instances` + `POST /open-api/workflow/{code}/instances`（发起人取主体账号）、user-org `GET /open-api/users`（租户级只读）、office `POST /open-api/messages`（租户级投递）。**不 RewritePath 到既有管理端点**——管理端点挂用户权限切面，与 scope 模型冲突；开放端点以"scope 断言 + 能力守卫"为唯一授权链 |
| F4 服务侧 scope 复核 | `OpenApiCapabilityGuardFilter` 扩展：按路径能力映射 + 方法族比对 `X-Client-Scopes`（与网关 `metadata.open-api` 同源配置），不一致即 403，防止网关元数据漂移 |
| F5 契约测试 | 每域反例矩阵 IT：个人令牌访问开放端点 403、缺 scope 403、未订阅 403、伪造 `X-Open-Api-Subject` 401、主体账号跨租户 403、停用应用签发 401（已具备） |
| F6 计量衔接 | 网关访问日志补 `X-Open-Api-Subject` 维度；`sys_operation_log` 聚合路径映射补齐四路由实际路径前缀（替换当前按 `/open-api/` 前缀的占位口径） |

## 5. 批次 G：注册表单源化（R-3，依赖 D22/D25）

| 项 | 内容 |
| --- | --- |
| G1 auth 内部 API | auth-service 新增签名保护的 `/internal/v1/open-apps` upsert / status / reset-secret（内部通道复用已上线的 `apex.internal-auth` 签名过滤器）；密钥只落 bcrypt 哈希，明文一次性返回 |
| G2 system 管理面代理 | `OpenAppAdminService` 改为调 G1 端点（`apex.open-app.registry=auth` 开关，回退值 `local` 保留一个发布周期）；响应模型不变，前端无感 |
| G3 存量迁移 | 一次性运维脚本（入 `scripts/utils/`，复用 [apex-tools-deployment](../../../apex-tools-parent/apex-tools-deployment/pom.xml) 的执行形态）：读 `oauth2_registered_client` 中带 `openPlatform.*` 的行，逐条 upsert 到 auth；输出比对报告（条数、哈希校验、失败清单），脚本可重复执行 |
| G4 弃用与收口 | system 侧开放应用写路径标记 `@Deprecated` 并在文档标注迁移窗口；`InternalOpenAppRouter` 限流档查询改读 auth 源（TTL 缓存不变）；[开放平台接入契约](../../03-api/open-platform-integration.md) 更新存储归属与迁移说明 |

## 6. 批次 H：boot-all 真实隔离（R-4，依赖 D23）

| 项 | 内容 |
| --- | --- |
| H1 adapter 产物 | `*-service` 配置 `spring-boot-maven-plugin` `<classifier>exec</classifier>`：可执行 fat jar 更名 `-exec.jar`，原名 plain jar 保留供依赖；**联动修改** [release.sh](../../../scripts/release/release.sh)（"仅一个 JAR"断言改为取 `-exec`）、CI 归档、Containerfile `SERVICE_MODULE` 构建、部署文档端口表 |
| H2 子上下文编排 | `BootAllApplication` 改为逐域 `SpringApplicationBuilder` 子上下文：每域独立 `Environment`（`spring.config.location=classpath:apex/domains/<domain>/`）、独立 DataSource/事务/MapperScan/`@EnableWebFluxSecurity` 链、独立业务端口（沿用拆分拓扑端口）；父上下文仅装配 common 横切（内部签名、JWKS、调度器） |
| H3 域限定资源 | 各 service 在 adapter 产物内提供 `apex/domains/<domain>/application.yml` 与 Flyway 位置映射（历史 SQL 字节不动，仅路径指向）；组件入口的 `interfaces-enabled` 开关由域配置接管 |
| H4 拓扑实证测试 | 替换"类名集合"测试：boot-all 全量 + 合法子集（如 auth+system+file）以 H2 真实启动，执行登录与一项跨域调用；非法拓扑（缺依赖域且未配远端）启动即失败；组件禁用（`interfaces-enabled=false`）路径行为回归 |
| H5 部署口径 | [podman-compose.yml](../../../podman-compose.yml) 增加 `--profile bootall` 单容器形态（可选）；文档明确单进程不等于单上下文、不承诺单端口 |

## 7. 批次 I：外部端到端验收（R-5）

| 项 | 内容 |
| --- | --- |
| I1 RocketMQ | 新增 `scripts/test/run-mq-integration.sh`（容器拉起 NameServer+Broker），执行：Outbox 重放投递、broker 确认后标记 DELIVERED、断网重试/DLQ、主数据 MQ 桥往返（信封 eventId/tenantId 守恒，消费幂等） |
| I2 EMQX | 扩展既有 MQTT 脚本至 ACL 模式联调记录：设备认证钩子、租户命名空间 ACL 拒绝越权订阅；同时验证内部签名豁免路径未被滥用（直连 stats 仍 401） |
| I3 六服务全栈 | `scripts/test/run-fullstack-smoke.sh`：Compose 拉起 MySQL/Redis/网关/六服务（H2 或 MySQL 档），覆盖登录 → 管理接口 → 开放 API 全链路（F 批交付后）→ 计量查询；boot-all 形态并行冒烟 |
| I4 告警与恢复 | Alertmanager 本地 webhook 接收器验证告警触达（规则沿用 `config/monitoring/prometheus/alert-rules.yml`）；备份脚本在一次性容器中真实还原并校验行数/抽样；业务 schema 回滚限制按既有文档口径标注 |
| I5 验收入档 | 结果按 [验收计划](../delivery/acceptance-plan.md) 追加记录：通过/跳过/未验逐项列明，外部环境缺失的跳过不得记为通过 |

## 8. 测试与验证门禁

- 每批次：`mvnd -T4 -B -pl <受影响模块> -am test`；批次收口跑对应 Failsafe IT（含双库迁移）。
- 全量：`mvnd -T4 -B clean verify`（Spotless/PMD/JaCoCo/Failsafe 全开，不跳过）；文档变更附 `bash scripts/ci/check-doc-links.sh`。
- 新增门禁：F4 的"网关元数据 ↔ 服务端 scope 映射"契约测试；H4 的真实启动拓扑测试；G3 迁移脚本的幂等与失败清单测试。

## 9. 风险与回滚

| 风险 | 缓解 | 回滚 |
| --- | --- | --- |
| 机器身份被滥用（主体账号权限过大） | 注册时强制最小 scope + 主体账号仅授予开放所需权限码；审计双维度（client_id + sub_uid）；限流已有 client 档位 | 停用应用即断（签发侧已拦截）；必要时收回主体账号角色 |
| D22 迁移期双源不一致 | 迁移脚本输出比对报告；只读比对期内告警不一致条目；`apex.open-app.registry` 开关可一键回退 local | 开关回退 + auth 侧残留行清理脚本 |
| H1 classifier 改动破坏发布链路 | 发布/CI/容器脚本同批修改并新增"产物清单契约测试"；`-exec` 命名在灰度分支先行验证 | classifier 配置还原即回到单 jar 形态 |
| boot-all 子上下文端口/Bean 冲突 | 域配置显式声明端口；拓扑测试真实启动矩阵；组件入口 opt-in（system 已改） | 移除 boot-all 模块即回退，不影响拆分部署 |
| 开放端点暴露面扩大 | 全链路仍经网关三道断言 + 服务侧能力/scope 复核 + 内部签名；开放端点不进 `/api/v1` 权限种子 | 网关路由下线即收口 |

## 10. 实施顺序与依赖

```text
D21/D22/D23/D24/D25 确认
        │
F1→F2→F3→F4→F5→F6（开放闭环，F1 依赖 D21）
        │
G1→G2→G3→G4（注册表统一，G1 复用内部签名通道；G2 与 F 批无强耦合可并行）
        │
H1→H2→H3→H4→H5（boot-all，H1 的发布脚本改动须先于 H4 落地）
        │
I1→I2→I3→I4→I5（外部验收，I3 依赖 F/G/H 收口）
```

## 11. 决策采纳记录

| 编号 | 采纳结论 | 落地情况 |
| --- | --- | --- |
| D21 | A：主体服务账号绑定（`sub_uid` + `X-Open-Api-Subject`） | 已实现：auth 令牌写 `sub_uid`、网关注入并剥离伪造头、四域过滤器识别应用令牌 |
| D22 | auth `auth_config` 为唯一签发源 | 已实现：`AuthOpenAppRegistryService` + `/internal/v1/open-apps`；system 默认代理，`local` 仅显式回退 |
| D23 | 分域子上下文，不合并单上下文 | 已实现：`BootAllDomains` + `BootAllDomainConfiguration`，为单域生成互斥开关 |
| D24 | file 开放写以主体为属主 | 已实现：`OpenFileHandler` 复用 `FileAccessContext`，未绑定主体即拒绝 |
| D25 | 一次性迁移 + 只读比对窗口 | 部分实现：`OpenAppMigrationService` 导出与 auth 幂等导入；比对报告与旧表下线脚本未完成 |

## 12. 未完成项与已知边界

| 项 | 现状 | 后续 |
| --- | --- | --- |
| 开放端点端到端 | 四域 Router/Handler 与令牌判定已落地并通过单元测试；未在真实六服务栈上跑通"取令牌→调用→审计" | 部署环境执行 I3 |
| 注册表迁移执行 | 导出/导入代码完成，未在真实存量库执行，也未产出比对报告 | 按 D25 在隔离库演练后执行 |
| boot-all 全量 | 仅 file 域在 H2 上真实启动；system 需 MySQL/Redis/License，其余域未逐一验证 | 逐域补齐启动矩阵 |
| 外部组件验收 | 真实 RocketMQ/EMQX/告警/备份恢复未执行 | 执行 I1/I2/I4 并记录 |

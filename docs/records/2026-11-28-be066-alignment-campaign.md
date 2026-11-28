# BE-066 全仓文档/脚本/配置/图表对齐与增强战役执行记录（2026-11-28）

> 任务卡：`docs/tasks/project-tasks.md` BE-066（R0/执行：ZCode，用户 2026-11-28 直接授权，串行单写者）。
> 方法：4 个只读评审子代理（串行，受并发上限约束）分片评审 + 图表切片由主会话直查；以**代码实现为唯一标准**逐项修复；图表按 AGENTS.md §0.3 只增不改，漂移图出 `_v2` 成对版本。
> 基线：`dev@1246f45a`；起始图表基线 svg=263 / mmd=84，收尾 svg=265 / mmd=86。

## 1. 目录复核结论（任务书第①项）

- `docs/` 25 个一级目录均有 README 且被总览覆盖（`check-doc-layout.sh` 通过）；`verify-docs.sh` 全项 OK。
- 根级散件均为已登记的刻意保留项：`aggregation-optimization-plan.md`、`architecture-review-report.md`（迁移转发指针）、`MEMORY-CARD.md`（AI 导航卡）；无新增散落。
- `scripts/` 根级 11 个散件 + `deploy/` 3 个同名件 + `office/` 1 个 + `dev/check/check-springdoc-annotations.sh` 逐个核实为**真转发壳**且目标齐全；`deploy/start.sh`/`stop.sh`/`assemble-topology.sh` 为真实实现（已在索引注明防误删）。
- 根级运行时目录（`_tmp/backups/logs/target/tools`）全部未跟踪且被忽略；`.gitignore` 私有目录块带注释、无裸 `docs/` 忽略规则。
- 未做任何目录搬迁（既有布局与治理一致，无需调整）。

## 2. 评审发现与处置（任务书第②③项）

### 2.1 切片 A：模块/架构文档（P0×2、P1×7、P2×8）

| 修复 | 文件 | 内容 |
| --- | --- | --- |
| P0 | `00-getting-started/examples-summary.md` | 3 处 `apex-infrastructure/...` 幻影路径改指 `apex-components-parent/apex-system/...` 实测路径，注明 `apex-infrastructure` 已为旧坐标兼容空聚合 |
| P0 | `02-architecture/architecture-usage-guide.md` | §apex-domain 重写（19 个类实测清单，删 interest/Elo 等幻影实体）；§apex-infrastructure 重写（空聚合 + 五能力 JAR 选型表）；§复制清单/决策树/错误示例/未来规划/issue 链接/`docs/ARCHITECTURE.md` 幽灵引用同步修正 |
| P1 | `02-architecture/architecture-overview.md` | services 10→11 个运行模块；子继承 13→14；business 行改"当前含 apex-knowledge"；`mvnd -pl` 统一冒号坐标 |
| P1 | `README.md` | `-pl apex-system-service` → `-pl :apex-system-service`（§"共享依赖"行经 pom 证实按生产口径**正确**，评审误报不改） |
| P1 | `00-getting-started/quickstart.md` | 示例接口改指 apex-example:8080 与真实目录结构（handler/config/dto）；jar 路径补模块前缀；克隆/Issue 地址改 `apex-cloud-service` |
| P1 | `00-getting-started/onboarding-guide.md` | 登录路径 `/api/v1/auth/login`→`/auth/v1/login`；V1-V26 改以各域实际为准；硬编码计数改口径；端口图补 8100/8099 |
| P1 | `02-architecture/architecture-index.md` | Reactor 3.6.13→"由 Boot BOM 管理（3.7.x）"；版本头 v2.8/2026-11-28 |
| P1 | `02-architecture/architecture-evolution.md` | 结论补可选运行模块（job/masking/knowledge）口径 |
| 增 | `02-architecture/context-map.md` | 上下文清单补「知识库（可选上下文）」行 |
| 横幅 | `02-architecture/multi-client-integration-plan.md` | 补历史快照横幅 |

### 2.2 切片 B：API 契约（P0×1、P1×9、P2×4）

| 修复 | 文件 | 内容 |
| --- | --- | --- |
| P0 | `03-api/API_ROUTES.md` §2.17 | 删除幻影行 `GET /api/v1/files(/generic)`（FileRouter 实测仅注册 POST generic） |
| P1 | 同上 §2.20 | 补 `GET /integration/v1/credentials/{id}`（CredentialHandler.get）、`POST /integration/v1/webhook/{connector}`（WebhookInboundHandler.accept），与 integration-iot-api.md 消除矛盾 |
| P1 | 同上 §2.16.1（新增） | 补登 `/open-api` 四 Router 共 7 条开放端点（OpenUser/OpenFile/OpenWorkflow/OpenMessage，方法名逐一实测） |
| P1 | 同上 §2.10.1（新增） | 补登现役 OAuth2 7 条（OauthTokenRouter/OauthEndpointRouter→OauthAuthorizationHandler/OauthMetadataRouter），历史表保持原口径 |
| P1 | 同上 §3.2 | 补 `GET /auth/v1/authorization/users/{userId}`（UnifiedAuthorizationRouter/UnifiedAuthorizationHandler.query） |
| P1 | 同上 §4.4 | 计数重算 410/330→469（§2=388 行、§3=81 行）并写明重算缘由 |
| P1 | `03-api/internal-api-contracts.md` §2 | `/internal/v1/interests/**` 行改标"死白名单待清理"→ 新工单 BE-067 |
| P1 | `03-api/api-path-corrections.md` | 补历史快照横幅；§4.2 括号表述修正（前缀已注册，仅 4 条具体路径不存在） |
| P1 | `03-api/api-version-management.md` | 整篇重写：删除虚构 `@ApiVersion` 注解机制与 v1/v2 Swagger 分组，改为 URL 路径版本约定（ApiVersionConfig javadoc 口径）+ 实际 SpringDoc 分组（default/auth/user/admin-system/admin-user） |
| P1 | `03-api/README.md` | 补登 4 篇现役契约文档 + api-routes.md「非权威清单」标注；统一响应补「业务码 0 ≠ HTTP 200」；更新日期 |
| P2 | `03-api/API_ROUTES.md` | §2.4 补 OrgSyncRouter 运行边界注记（office 域）；§5 办公路由补 `off/router/` 子包；§1 `/.well-known/**` 写具体两个路径 |
| 横幅 | `03-api/work-completion-report.md` | 补历史快照横幅 |

### 2.3 切片 C：配置/运维（P0×5、P1×9、P2×15）

| 修复 | 文件 | 内容 |
| --- | --- | --- |
| P0 | `05-operations/configuration.md` | §缓存重写为 `apex.cache.local/remote/double-delete` 实测键（删 `cache.caffeine/multi-level` 幻影键）；§RBAC 改 `apex.security.admin-role` + `apex.features.rbac`（公开路径由 `SystemConstants.PUBLIC_PATHS` 代码承载）；§限流整块替换为 `apex.security.user-rate-limit/ip-rate-limit` + `apex.gateway.rate-limit.*` 实测键；§Grafana 改 `grafana.enabled/url/explore-path` 并指明告警阈值在 `alert-rules.yml`（13 条） |
| P0 | `05-operations/deployment.md` §6.1 | 默认清单补 `apex-job-service`（无 profiles 默认启动 + 403 探针语义）；可选 profile 补 masking/xxl-job/seata/knowledge |
| P1 | `configuration.md` | Redis Lettuce 池 8/0/-1→16/8/2（gateway max-wait 2s）；storage base-path 改 `${STORAGE_LOCAL_BASE_PATH:/root/data/apex/uploads}` + compose 覆盖值注记；`apex.mq` 口径分层（yml 默认 true / 部署模板 false） |
| P1 | `deployment.md` | §7.6/§8 探针端口表补 integration 8097、aggregation 8098；§7.4 MQ 口径分层注记；MySQL 8.0+→9.0（含 V42 工具口径）；备份示例容器名 `apex-mysql`→实际 `apex-scaffold-mysql-1`/`apex-infra-mysql` |
| P1 | `configuration-inventory.md` | §2.3 Redis 池默认值校正；§5.1 覆盖率整节重算（317 个赋值；BAIDU/DINGDING/MAIL/REDIS_HOST 等均已收录，删过时"缺失"标注） |
| P1 | `config/nacos/README.md` | §一 namespace 默认 `public`（非留空）；§五 三开关默认 true（compose 拓扑），无 Nacos 部署须显式置 false |
| P1→工单 | `05-operations/deployment.md`/`configuration.md` | `aggregation.todo.*` 死键（TodoProperties 绑定 `apex.todo`）与 system prod 模板 8081 默认属**代码/配置层修复**→ 新工单 BE-068/BE-069 |
| P1 | `05-operations/reactor-netty-metrics-guide.md` | PromQL 标签改 `application="apex-gateway"`（无 module 标签） |
| P2 | `.env.example` | 补 `WECHAT_MINIAPP_APP_ID/SECRET`、`WECHAT_MP_APP_ID/SECRET`（prod yml 引用）；`MANAGEMENT_SERVER_PORT` 加按服务覆盖注释；XXL/Seata 自备文件缺失警告显化 |
| P2 | `configuration.md` | JWT 补 `access-token-renew-threshold` 并注 min-secret-length 无主配置键；springdoc/knife4j 块改实测键；MyBatis/MyBatis-Plus 按服务分块（system 用 `mybatis.`，office/workflow/knowledge 用 `mybatis-plus.`，均实测）；i18n 文件清单改实测四语言（无默认 messages.properties）；features 6→7 键（补 auth-center-permission 两层默认差异）；Zipkin 补网关变量口径（`ZIPKIN_ENDPOINT`/`TRACING_SAMPLING_PROBABILITY`）；bootstrap 示例注明网关与业务 service 默认差异；Druid 块重复嵌套排版修正；待办路由改 `/api/v2/todo` 单条 |
| P2→工单 | 根级 `monitoring/` | 与 `config/monitoring/` 重复且无引用→ 新工单 BE-070（删除属破坏性操作，独立确认后执行） |
| 横幅 | `02-architecture/architecture-usage-guide.md`、`00-getting-started/quickstart.md` | 修正 apex-example 无 springdoc 的文档页表述 |

### 2.4 切片 D：脚本索引与开发/测试文档（P0×1、P1×5、P2×7）

| 修复 | 文件 | 内容 |
| --- | --- | --- |
| P0 | `04-development/svg-maintenance-guide.md` | 全部命令路径补 `apex-components-parent/` 前缀；共享 repository 统计改指 `apex-shared-parent/apex-domain`；迁移版本改"以实际最大 V 号为准" |
| P1 | `04-development/codegen.md` | 迁移目录路径补分组前缀；"V90"改以实际为准 |
| P1 | `04-development/DATABASE_CONFIG_*.md` | 7 个无横幅孤儿历史件统一补历史快照横幅（其余 2 个已有） |
| P1 | `06-testing/middle-platform/20261110-completion/report.md` | 补历史快照横幅（根级路径已退役） |
| P1 | `scripts/README.md` | 总览补 `governance/` 分类；新增 governance 正文小节（6 脚本表）；兼容入口说明改为**点名清单**（16 个转发壳 + 3 个真实实现防误删）；补"索引覆盖说明"与高频入口（build/release/lib） |
| P1 | `scripts/test/run-dual-database-migration.sh` | 头注释"两个域"→"三个域"；system 版本上限改以实际为准；显式注明 knowledge 域由 Failsafe 全量覆盖不入本演练 |
| P2 | `04-development/build-tools-usage.md` | Failsafe 口径补 `*RemoteIT` |
| P2 | `01-guides/job-query-feature-guide.md`、`office-platform-integration-guide.md` | 显示文本/注释路径改嵌套后实测路径（OfficePlatform 实测于 apex-office domain） |
| P2 | `scripts/README.md` ci/ | 补 `check-springdoc-coverage.sh` 登记 |

### 2.5 切片 E：图表（主会话直查）

- 端口扫描：5 张含端口的 mmd 无 8083/workflow 类漂移；129/140/128 的"apex-application"命中为 `apex-applications-parent` 误匹配；93/94 为带横幅历史视图；96 的退役说明为合规注记。
- **漂移处置（§0.3 流程）**：`sequence/90_login_biometric_mfa`、`sequence/91_sso_keycloak_flow` 仍以退役模块名（apex-auth-center/apex-interfaces）为参与者，且 90 含幻影端点 `/auth/v1/register/fido/*`（全仓无注册）与幻影表 `login_history`（auth 迁移链无此表）。实测修订依据：
  - 90：主登录链 = `AuthenticationRouter`(/auth/v1/login、/auth/v1/mfa/verify) + `AuthenticationApplicationService`（apex-auth：UserRepository/PasswordEncoder/TotpService/SessionService/JwtKeyMaterialService/TenantStatusPort）+ `MfaService`（`/api/mfa/**` 由 MfaManagementHandler/MfaHandler 承载）；`BiometricAuthService`/`DeviceTrustService` 仅存在于 apex-auth 领域层且无生产调用方/HTTP 端点——如实标注。
  - 91：OIDC 客户端（`SecurityConfig`·oauth2Login·`OidcAuthenticationSuccessHandler`·`KeycloakConfig`·`KeycloakUserSyncService`）位于 `apex-system` interfaces 包（apex-system-service 装配，`keycloak.enabled` 条件启用）；令牌端点兜底为 auth-service 的 `OauthTokenHandler`/`OauthEndpointRouter`/`OauthMetadataRouter`（网关 `/oauth2/**`→auth-service）。
- 新增成对文件：`90_login_biometric_mfa_v2.mmd/.svg`、`91_sso_keycloak_flow_v2.mmd/.svg`（mmdc 12.0.0 + 系统 Chrome 渲染，puppeteer 配置 `/tmp/mermaid-puppeteer.json`）；v1 原图保留不改。
- 索引登记：`docs/diagrams/sequence/README.md` 90/91 两行扩为 v1（标注历史）+ v2 四行；`docs/diagrams/README.md:191` 为历史修复注记不涉及；主库无全局计数声明无需刷新。
- 受影响引用：全仓检索两张旧图仅 sequence/README.md 与 diagrams/README.md 引用，无其他活文档依赖。
- 基线变化：svg 263→265、mmd 84→86（`verify-docs.sh` 第 5/6 项基线校验通过）。

### 2.6 历史快照横幅台账（MF-01 修正：共 18 文件 + 4 行索引标注）

按 `git diff` 新增 `> **历史快照**` 行精确统计（2026-11-28 R5 复核后重算，R5 初测 17 系漏计 1 件）：

| # | 文件 | 备注 |
| --- | --- | --- |
| 1 | `02-architecture/multi-client-integration-plan.md` | 2026-10-06 基线 |
| 2 | `03-api/api-path-corrections.md` | 2026-09-10，横幅内含 §4.2 表述修正说明 |
| 3 | `03-api/work-completion-report.md` | 2026-09-19 |
| 4–10 | `04-development/DATABASE_CONFIG_{API_TEST_REPORT,BACKEND_IMPLEMENTATION,COMPLETE_VERIFICATION,MENU_MANUAL_EXECUTION,MENU_SQL_GUIDE,QUICK_REFERENCE,R2DBC_MIGRATION}.md` | 孤儿历史件批量补横幅（该系列另 2 件已有横幅） |
| 11 | `06-testing/middle-platform/20261110-completion/report.md` | 2026-11-10，横幅注明根级路径退役 |
| 12–14 | `07-commercial/{COMMERCIAL_OPTIMIZATION_PROPOSAL,COMMERCIAL_REFACTORING_PLAN,scaffold-refactoring-plan}.md` | 2026-08/09 方案件 |
| 15 | `08-project-management/DOCS_STRUCTURE_STANDARD.md` | 横幅为「已被取代」口径（指向 AGENTS.md §0.2） |
| 16–18 | `08-project-management/{p0-system-management,project-standardization-summary,scaffold-review-guide}.md` | 2026-08/09 时点件 |

索引标注 4 行（给链接读者口径提示，非横幅）：`docs/README.md` 2 行（脚手架审查、文档与脚本目录结构规范）+ `docs/08-project-management/README.md` 2 行（标准化总结、文档与脚本目录结构规范）。

## 3. 新登记任务卡

- **BE-067**（P3）：system interests 死白名单与死限流规则清理（`SecurityConfig`/`JwtAuthenticationFilter`/`SentinelConfig`）。
- **BE-068**（P2）：aggregation yml `aggregation.todo.*` 死键改 `apex.todo.*`。
- **BE-069**（P2）：system prod 模板 `MANAGEMENT_SERVER_PORT` 默认 8081 与 8083 惯例对齐（端口语义，须先规格评审）。
- **BE-070**（P3）：根级 `monitoring/` 重复目录清理（破坏性，独立确认）。

## 4. 门禁与验证

- `bash scripts/governance/verify-docs.sh`：**0 项问题**（多轮，`--strict` 终态 EXIT=0，快照 md=833 svg=265 mmd=86）。
- `bash scripts/ci/check-doc-links.sh`：**4089 链接 0 断裂**（多轮：编辑后、横幅后、索引后；R5 独立复跑 EXIT=0）。
- 修改脚本 `bash -n scripts/test/run-dual-database-migration.sh`：语法通过（仅注释变更）。
- `git diff --check`：无空白错误。
- 每处改写均附实现证据（文件:行 / pom / yml / Router 注册），两处评审误报经 pom/代码复核后**未采纳**（README 共享依赖行 cache=test scope；OfficePlatform 实际路径修正后再落笔）。

## 5. 未验证项与回滚

- 未运行 Java 构建/测试：本轮零 Java 代码改动（全部为 docs/scripts 注释/.env.example/图表），不触发构建门禁要求；`scripts/test/run-dual-database-migration.sh` 仅改注释，未重跑演练。
- 图表 SVG 渲染为静态导出，未在浏览器人工复核视觉布局。
- 回滚：全部改动为 `dev` 工作区未提交内容，`git checkout -- <path>` 可逐文件回滚；`.env.example`/脚本同口径。台账条目与任务卡随本记录同批提交。

## 6. 台账

记忆台账条目：`docs/reference/ai-knowledge/07-change-memory.md` #129（与本记录同批提交，提交信息含 `memory:` 标记）。

## 7. 独立 R5 复核结论与 MF/N 清零记录

**R5 结论（2026-11-28，独立子代理）**：`APPROVE_WITH_NITS`——抽样实证全部命中实现（API_ROUTES 补登/删除行逐条对照 Router 源码、configuration 改写键 8+ 项实测、转发壳 16 个 head 实证、BE-067/070 前提属实）；自跑 `verify-docs.sh --strict` EXIT=0、`check-doc-links.sh` 4089/0 EXIT=0；无越界改动（无 Java/pom/生产 yml）。

| 项 | 内容 | 清零处置 |
| --- | --- | --- |
| MF-01 | 记录/任务卡/台账横幅记账失实（漏列 7 文件、计数 9/3 对不上） | 本记录新增 §2.6 台账（18 文件 + 4 行索引标注，按 diff 精确重算）；任务卡 ④、台账 #129 同步修正（均为未提交状态，原位更正） |
| N-01 | `docs/README.md:297` 图表计数落后终态 | 改为 265 SVG / 86 MMD |
| N-02 | `91_sso_keycloak_flow_v2.mmd` UserRepository 误标「共享 domain 端口」（实际在 apex-system 自有 domain） | 参与方标签改「apex-system domain 端口」并重渲染 SVG（mmd↔svg 保持成对） |
| N-03 | `configuration-inventory.md` §5.1 赋值行计数 317 漏数字键名 | 改 335（基线 331 + 本轮 4，正则 `^[A-Z0-9_]+=`，R5 复算一致） |

**MF-01 与 N-01~N-03 已全部清零（2026-11-28）**；清零后复跑门禁见 §4。遗留：任务专属集成提交（待用户授权，分组预案见会话记录）。

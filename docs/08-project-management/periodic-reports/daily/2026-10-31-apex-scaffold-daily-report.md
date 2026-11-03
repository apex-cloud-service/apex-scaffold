# 工作日报（2026-10-31）

**项目**：apex-scaffold（后端；网关、五个业务服务、任务代理与脱敏服务）
**主线**：方案 v3.1 双线推进——42.33 需求评审收口（13 次提交已入库），批次 A1+A2 统一认证与 JWKS 闭环（工作区未提交）。

## 一、今日完成

**交付规模**：13 次提交；触及文件 174 个（去重）；文件变更 194 次（新增 65、修改 128、重命名 1）；涉及 14 个 `apex-*` 模块及 `docs`、`scripts`、`config`、根级配置；其中主源码 100 个、测试 29 个、Flyway 迁移 15 个、文档 10 个。另有未提交改动 48 项（修改 28、删除 5、未跟踪 15 项/展开 36 文件）。

1. **A1 双算法验签统一（未提交）**。`apex-common` 新增 `MultiKeyJwtVerifier`（按 `kid`/`alg` 在 RS256 公钥集与 HMAC 过渡密钥间选钥，含算法白名单防混淆）、`JwksFetcher`（启动即拉 + 周期刷新 + 失败保留快照）与 `JwksAutoConfiguration`；`apex-system` 新增 `TokenVerifier` 契约、`JwtVerifierSupplier`（静态公钥 ∪ JWKS 快照 ∪ HS256 过渡）、`CenterJwtKeyProperties`，`JwtAuthenticationFilter` 改为注入验签器。**解决的是**认证中心 RS256 令牌分支此前「不可达」、各服务只能校验单一静态密钥的缺陷，并将其余四个服务的 `IdentityRelayAuthenticationFilter` 统一到多 `kid` 模型（单键「忽略 kid」语义保留）。
2. **A2 JWKS 闭环（未提交）**。`OAuth2Service.jwt()` 改用 `JwtKeyMaterialService.sign()` 签发（`kid`/`iss`/`aud`/`jti`），`OAuth2MetadataService.jwks()` 由单密钥投影改为聚合输出（轮换活跃+宽限密钥 + 静态密钥过渡开关 `legacy-key-in-jwks`），网关 `JwksProperties` 增 `remote-url`/`remote-refresh-interval-seconds` 并周期拉取合并。**解决的是**轮换密钥无法被下游验证、JWKS 端点无消费者导致的密钥轮换断链，配套 `.env.example`、五个服务 `application.yml`、`config/nacos/apex-gateway.yaml` 与 `docs/05-operations/configuration.md` 同步。
3. **生成器异步链租约语义收口**（`6a4aad7a`，14 文件）。候选查询补 `attempts < maxAttempts`；`updateProgress`/`markCompleted`/`markFailed` 全部限定 `locked_by`，进度回写兼作租约心跳，0 行更新即停止处理；认领后按主键重读实体再执行；新增 `LeaseGuard` 在入口、每单元写盘前、历史落库前复检执行权；执行期重算输入摘要比对 `previewToken`；保留期清理改为「先查主键、后按主键删除」以兼容双方言。**解决的是**任务被接管后旧执行者仍写产物与历史的竞态，以及 MySQL 专属 `DELETE ... ORDER BY ... LIMIT` 阻断 PostgreSQL 部署。
4. **调度器租户上下文桥接**（`6a4aad7a`）。生成器任务、体检导出、体检保留期三条链路由仅 `contextWrite` 改为 `TenantContext.withTenant(Mono)` 桥接执行线程 ThreadLocal。**解决的是**企业档开启租户拦截器后任务表 SQL 被 `DefaultTenantLineHandler` 拒绝——这是方案文档未记录的缺陷，属本轮评审新发现。
5. **租户到期纳入写入门禁**（`ce8559ff`）。`TenantEntity.canCreateBusinessData()` 增加 `expireTime` 断言与 `expired()` 领域行为（null 视为长期有效），`assertCanCreateBusinessData` 按到期/状态区分错误消息。**解决的是**过期租户仍可创建业务数据的越权写入面；到期不改状态机、不清理既有数据，避免误伤历史数据。
6. **网关与流程定义解析容错**（`9a1000e2`、`55d2ff9d`）。Nacos `workflow-domain` 路由补 `/api/v2/workflow-definitions/**` 并扩展 `GatewayRouteProfileConfigTest` 对三配置源的断言；`convertToNodeDTO` 对 `position` 类型漂移仅降级该字段并告警、`timeoutHours` 容忍 Long/Double。**解决的是** v2 工作流定义接口经网关 404，以及单节点脏数据把整张定义图收敛为空的问题。
7. **管理端后端能力补齐**（`0e9dba36`，141 文件）。运维体检 O07b（历史快照、趋势、CSV 导出、保留期清理、签名校验）、Keycloak 管理端与用户同步、密码找回可信代理 IP 解析与短信投递配置校验（含四语言 i18n）、文件分片活跃唯一性（V7 双方言）、办公审批/消息/日程/会议室/同步向导路由对齐、`NotificationPublisher` 契约加固；配套迁移 V70–V75 双方言与菜单/权限种子。**解决的是**管理端页面背后缺端点、分片并发写入产生重复活跃块、通知发布缺少统一出口三类问题。
8. **文件服务静态多密钥验签**（`f7321a89`）。`IdentityRelayAuthenticationFilter` 支持 `jwt-public-keys-base64` 多键配置。**解决的是**多实例/多签发方场景下只能配置单公钥的运维约束。
9. **测试与质量工具**（`eb001262`、`3c3f7f40`、`f20f6c2b`、`ef2673f7`）。新增 `PasswordRecoveryHandlerIntegrationTest` 9 项（三端点响应形态、限流 429、短信不可用 503、校验 400、OpenAPI 契约、IP 透传）；`scan-javadoc-issues.sh` 修正类级/方法级判据误报；补齐内部授权与公共路由的 `@Operation` 注解；Office 迁移测试类头注释 15 表更正为 16 表。**解决的是**密码找回只有单元测试无 Handler 级证据、Javadoc 门禁误报掩盖真实缺口。
10. **周期报告归档体系归位（未提交）**。新建唯一归档根 `docs/08-project-management/periodic-reports/`（daily/weekly/monthly 各带索引），归位 18 份历史报告，删除 `weekly-reports/` 旧目录，`.gitignore` 的 `daily-report-*` 改为根锚定以免误伤归档。**解决的是**报告分散在三个目录、命名缺范围标识导致 10-27/28/29 三份冷链日报与 apex 日报易混的问题。
11. **评审收口记录**（`88b12447`）。方案文档 §42.33 落盘本轮评审结论、修复清单与验证结果，回写 O01–O08 机会点状态。**解决的是**方案文档声称与源码现实脱节、缺口无据可查。

## 二、验证

**未执行**。本次数据采集为只读，按约束未运行任何构建、测试或打包命令（全量 `clean verify` 耗时长且可能污染工作区）。以下为当日提交与文档中**已记录**的执行结果，非本次采集所产生：

- 计划书 §42.33 记录：受影响反应堆 `mvnd -T4 -B -pl apex-gateway,apex-system,apex-workflow,apex-office-service,apex-auth-service -am verify` 于 2026-10-31 12:30:37 退出 0、BUILD SUCCESS，20 个反应堆项目全部成功；Surefire 汇总 4944 项（失败 0/错误 0/跳过 10），Failsafe 汇总 227 项（失败 0/错误 0/跳过 10），Spotless、PMD/p3c、JaCoCo 检查通过，`git diff --check` 干净。
- `docs/06-testing/jwt-unification/20261031-a1-a2/report.md` §4（对应未提交的 A1+A2）：受影响 7 模块 `test` BUILD SUCCESS、`spotless:check` 通过、`git diff --check` 干净、本批文档链接 0 断裂。
- **未完成项**：同一报告 §4「clean verify 结果补记」正文仍为「待全量门禁完成后填写」，即 A1+A2 的全量门禁尚无结果记录。

## 三、风险与待办

1. **A1+A2 全量门禁空白**。报告 §4 的 `clean verify` 补记未填，验收前必须补齐模块数、BUILD 结果与测试汇总；未提交状态下不应视为已完成交付。
2. **未提交改动体量大**。48 项（含 5 个新增测试类、5 个新增主源码类与两份未入库文档）跨 `apex-common`、`apex-system`、五个服务的安全过滤器与网关配置，直接提交前建议按 A1/A2 拆分为两个提交，便于回溯。
3. **调度器租户桥接未做真实环境联调**。三条链路的修复仅通过单测，未在企业档 MySQL/PostgreSQL 双库开启租户拦截器的条件下验证，属最大剩余不确定项。
4. **游离文件已入库**。仓库根出现与提交主题无关的文件 `:-`（232 字节，内容为 macOS codesign requirement 片段），随 `f7321a89` 进入版本库，建议单列清理提交。
5. **归档改造未入库且删除了旧目录**。`periodic-reports/` 全量未跟踪，同时 `weekly-reports/` 5 个文件处于删除状态；若直接提交，外部引用旧路径的链接会断（当前 `check-doc-links.sh` 既有 15 条断链均位于 `.workbuddy/`，不在本批口径）。
6. **次日切口**：补 A1+A2 全量门禁 → 拆分提交 → 用户验收后启动 A3（撤销传播）。

# 02 · apex 后端现状（2026-11-19）

> 采集：2026-11-19，来源为源码实测 + 方案 §1 评审结论 + 各批测试报告。数字会漂移，**结论比数字寿命长**。

## 1. 形态

- **API 网关 + 六业务服务 + 只读查询聚合 + 任务代理 + 可选脱敏 + 可选知识库**，另有组合部署档 `apex-boot-all`：**13 个启动入口（12 个部署单元 + 组合档）**；Maven 根直接聚合 7 组、递归 44 个模块、含根 45 个 reactor 项目（2026-11-14 两级布局：37 个叶子已物理移入分组父目录）。
- 业务代码以**库**形式复用（`apex-system` / `apex-auth` / `apex-workflow` / `apex-file` / `apex-office` / `apex-integration` / `apex-knowledge`），运行模块只做启动与装配；C7 起业务库自带 `AutoConfiguration.imports` 组件入口。
- 规模：主源码 **2249** 个 Java 文件，测试类 **780** 个，迁移 **282** 个（MySQL 141 + PG 141；office 最高 V10、system V91、auth V65、knowledge V2）。
- `apex-system` 仍是**四层合一巨库**（interfaces/application/domain/infrastructure 同包，870 个主源码文件），是当前最大结构债（C4/D07 已裁决维持现状）。

## 2. 分层约束（ArchUnit 有门禁）

```text
interfaces → application → domain ← infrastructure
                 ↑                       │
                 └──── 端口/适配器 ───────┘
```

- domain **不得**依赖 interfaces / 具体 infrastructure / MyBatis-Plus。
- common、共享 domain/infra、api-contract **不得**反向依赖任何具体业务库。
- 网关不承载业务四层，不引 Servlet 栈。
- **组件化已落地（C7/C9）**：业务库提供组件入口自动装配，`apex-boot-all` 仅依赖组件库、**不做全根扫描**，用单进程装配验证「任意组合」；企业部署仍为五服务拆分。`apex-example` 保留全根扫描作为样例，不再代表组件边界。

## 3. 认证与授权：真实完成度

| 能力 | 状态 | 关键事实 |
| --- | --- | --- |
| 密码登录、授权码（强制 PKCE）、客户端模式、refresh 轮换、introspect/revoke | **真实实现** | 认证中心为主 AS |
| 令牌算法 | **已收口（A1）** | `MultiKeyJwtVerifier` 按 `kid` 选 RSA 公钥，保留 HMAC 双轨过渡 |
| JWKS 分发 | **已闭环（A2）** | `/.well-known/jwks.json` 为唯一公钥分发点；`JwksFetcher` + `JwksAutoConfiguration`（common），网关 `GatewayJwtVerifierService` 自动拉取并缓存 |
| 撤销传播 | **已实现（A3）** | 领域事件发布落地 + 增量撤销端点 + 网关拉取式撤销缓存 |
| 会话与 MFA | **已接线（A4）** | `SessionService.createSession` 有调用方；MFA 接入登录流程 |
| OAuth2 收口 | **已实现（A5）** | consent 流程统一，管理端配置与浏览器回调分离 |
| 统一授权 | **已实现（A7），运行期需显式开启** | `UnifiedAuthorizationService` 能力就绪，组件库缺省 true，但 `apex-system-service` 的 `application.yml` 以 `${APEX_AUTH_CENTER_PERMISSION_ENABLED:false}` 覆盖 → **实际缺省关闭**，切流要置环境变量 |

> 证据：`docs/06-testing/jwt-unification/20261031-a1-a2/report.md`、`20261031-a3-a7/report.md`（此前提「报告路径不存在」的问题已于 10-31 补交）。

## 4. 多租户：骨架真实，B 批已补齐运营层

- 已有：ThreadLocal + Reactor Context 传递、fail-closed `TenantLineHandler`（缺上下文直接抛 `IllegalStateException`，**不回退默认租户**）、ArchUnit 禁绕过、平台租户生命周期 API、配额字段。
- B 批后补齐：租户编码统一 `default`（V76）、主库 RBAC 租户化（V77）、套餐与功能裁剪（V78）、开通引导、配额真实执行、租户级配置（V79）、租户管理员自助、租户化缓存键（`TenantCacheKeys`）。
- **仍存在的边界**：
  - `apex.tenant.enabled` 缺省仍 **false**（仅 Enterprise 档开启），basic/standard 不注入租户拦截器；
  - `IGNORE_TABLES` 保留 `sys_menu` / `sys_permission` / `sys_dict*` / `sys_config` / `sys_tenant*` / `flyway_schema_history` / `scheduler_job*` / outbox（**`sys_role`、角色授权已在 B2 移出豁免**）；
  - 配额守卫与 `apex.tenant.enabled` 解耦（B5 起显式拼租户条件）。

## 5. 网关能力（已落地）

限流（Redis 令牌桶）、路由级熔断 + 统一 fallback、灰度标签负载均衡、结构化访问日志（可选 `apex_gateway` 库）、JWT 验签（含 JWKS 拉取）、`/internal/**` 对外 403。

## 6. 消息 / 事件 / 存储

- 事务性 Outbox + RocketMQ 事务处理器；站内信读侧闭环 + SSE 广播；office 另有 HTTP 游标收件箱 + WebSocket 在线提示（**单实例内存状态**，多实例需收件箱补偿，不做跨实例实时广播）。
- **消息门面已统一（D1）**：`NotifyApplicationService` + `NotifyChannelPort` 收敛为单一发放入口，通道实现 `ImNotifyChannel` / `InboxNotifyChannel` 由 `NotifyChannelConfiguration` 装配；模板渲染走 `NotifyTemplateRenderer`。
- 对象存储：五后端（Local/MinIO/火山 TOS/阿里云 OSS/七牛），默认 Local，无故障降级；C3 起 SPI 可扩展。

## 7. 可观测与部署

- 链路：SkyWalking（替换 Zipkin/Brave）；日志：ELK；指标：Micrometer + Prometheus + Grafana provisioning。
- 部署：Compose / K8s / 裸机三方式，详见 `docs/05-operations/deployment.md`；`apex-tools-deployment` 提供离线密钥校验、TOTP 迁移包、V42 结构恢复、文档链接检查。
- 服务增强默认关闭：XXL 执行器、Seata（`apex.seata.mode` = AT 缺省 / TCC / SAGA / XA；AT/SAGA/XA 要求同步单 JDBC 源，拒绝动态数据源与 Publisher 回调）。

## 8. 验证状态（必须区分口径）

| 口径 | 现状 |
| --- | --- |
| 全量构建 | 方案 §42.33 记录 2026-10-31 `mvnd -T4 -B clean verify` 全模块 BUILD SUCCESS；11-01 各批报告另行记录 |
| 各批测试报告 | A/B/C/D 均有独立报告目录（`docs/06-testing/jwt-unification/`），A1+A2 报告已于 10-31 补交 |
| P0 迁移验证 | **未准出**：`verified_union` 滚动覆盖未达 586/586，Java 21 正式切换结论未收口 |
| E1（`apex-integration`） | **已提交**（11-02 `b055c55f`），含 connector/device 双子域、`apex-integration-service`（8094）与 V1–V3 迁移 |
| 真实中间件端到端 | 已补 EMQX/HTTP/MQTT 真实验证（`20261102-live-verification`、`20261102-acl-and-fullstack`）；Seata 四模式在缺 TC/双库时仍跳过，**不能记为通过** |

## 9. 结构性缺口清单（用于排期，不用于宣传）

1. `apex-system` 四层合一 → C4/D07 迁移 interfaces 层至 `apex-system-service`（21 Router + 44 Handler 量级），M3 收官项。
2. 组件化缺口已补：starter JAR 级裁剪（C1）、api-contract 死代码清理（C2）、SPI 扩展点（C3）、业务库组件入口自动装配（C7）、事件通道自适应（C8）、`apex-boot-all` 组合拓扑（C9）。
3. 开放平台：`OpenAppAdminRouter` / `InternalOpenAppRouter` / `OpenUserRouter`（`apex-system`，11-07 起 auth 侧 `OpenAppRouter`/`OpenAppHandler` 已移除）+ `/open-api` 网关路由已接（11-02），能力目录与用量见 `CapabilityCatalog`。
4. 共享数据服务层（D5）已提交（11-02 `0d8cda0a`）。
5. 集成域与 IoT（E1–E4）已完成并提交：connector（连接器/凭据/webhook）与 device（注册/影子/遥测/命令/规则）双子域，MQTT 客户端 optional，生产依赖外部 EMQX。

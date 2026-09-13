# Apex Scaffold 脚手架优化评审方案

> 版本：v1.0（2026-08-27）
> 性质：评审方案（评审 + 建议，**待确认后执行**）
> 适用对象：作为**通用后端开发脚手架**售卖的 Apex Scaffold（Java 17 / Spring Boot 3.5 / WebFlux / DDD 五层 / 三档商业化）

---

## 一、评审结论总览

### 1.1 现状判断

Apex Scaffold 的技术底座显著强于主流商用脚手架（RuoYi / pig / JeecgBoot 等）：

| 维度 | 现状 | 评价 |
|------|------|------|
| 架构分层 | DDD 五层 + Port/Adapter 方向正确 | ✅ 优于多数脚手架 |
| 响应式 | 纯 WebFlux 函数式端点，无 MVC 残留 | ✅ 稀缺差异化 |
| 可观测性 | Prometheus/Grafana/Zipkin/告警齐全 | ✅ 完整闭环 |
| 中间件 | Redis 双缓存 / RocketMQ+Outbox / Redisson 锁 / 自研 Cron 调度 | ✅ 强 |
| 设计模式 | 模板方法/策略/工厂/观察者/装饰器/适配器/责任链均有规范应用 | ✅ 良好 |
| 商业化 | License 校验 + 三档 Profile + 双轨许可证 | ✅ 独有 |
| 示例与生成器 | apex-example 端到端 CRUD + apex-generator DDD 五层生成 | ✅ |

**结论：工程深度达标，短板在"通用脚手架的产品化广度"与"局部一致性治理"。** 本次评审聚焦三部分：
**A. 架构治理与设计模式重构**（已发现问题，直接可改）、**B. spring-boot-starter 模式改造**（脚手架形态升级）、**C. 功能补充**（产品化能力补全）。

### 1.2 问题汇总（按严重度）

| # | 问题 | 类别 | 严重度 |
|---|------|------|--------|
| A1 | Handler 层响应样板三套体系并存、CRUD 链路重复 | 设计模式/重构 | 🔴 高 |
| A2 | 分层违规：interfaces 直连 infrastructure 实现类 ×3、application 依赖 infrastructure | 架构 | 🔴 高 |
| A3 | UserHandler 内联响应绕过统一体系（硬编码中文、不走 i18n） | 一致性 | 🟡 中 |
| B1 | 无 `AutoConfiguration.imports`，License 等"伪自动配置"靠包扫描，不可独立裁剪 | Starter | 🔴 高 |
| B2 | 通用能力未 starter 化，买家无法按需启用/复用 | Starter | 🟡 中 |
| C1 | 无账号密码登录（仅微信/短信） | 功能 | 🔴 高 |
| C2 | 审计日志注解已备但落地不完整；数据权限/部门缺失 | 功能 | 🟡 中 |
| C3 | 多数据源主从仅框架就绪无示例；Excel/推送/邮件/多租户缺失 | 功能 | 🟢 低~中 |

---

## 二、Part A：架构治理与设计模式重构

### A1. Handler 样板收敛（高优先）

**现状证据**（已核实）：
- 12 个 Handler 全部 `extends BaseHandler`（含 `buildOk/handleAuthenticated/validateAndThrow` 模板方法），但 **7 个仍自带私有 `ok(T)`**（CaptchaHandler:93 / DictHandler:302 / ConfigHandler:182 / JobHandler:218 / LoginLogHandler:82 / MenuHandler:184 / AdminUserManagementHandler:224）—— 与 `BaseHandler.buildOk()`、`RequestHandlerUtil.buildSuccessResponse()` 形成**三套平行实现**。
- `StatusRequest` 内部类在 DictHandler:313 / ConfigHandler:193 / JobHandler:229 **重复定义**。
- `bodyToMono(...).switchIfEmpty("请求体不能为空").validateAndThrow().service().ok()` 链路在 Config/Dict 的 create/update/updateStatus **重复 6+ 次**。
- UserHandler 走 `ServerResponse.ok().bodyValue(Result.success(...))` 内联 + 硬编码"用户不存在"，与统一体系完全脱节。

**重构方案（模板方法 + 泛型 + 工具收敛）**：
1. **统一响应出口**：删除各 Handler 私有 `ok()`，全部改用 `BaseHandler.buildOk()`；`RequestHandlerUtil` 作为唯一低层构建器，BaseHandler 委托它。
2. **抽取泛型 `BaseCrudHandler<T, ID>`**（模板方法模式）：抽象 `create/update/delete/page/get` 骨架，子类只提供 `Service` 与请求体映射；`DictHandler/ConfigHandler/JobHandler/MenuHandler` 的重复链路收敛进骨架。
3. **请求体解析统一**：`bodyToMono().switchIfEmpty().validateAndThrow()` 链抽为 `BaseHandler.parseAndValidate(req, Class)` 泛型方法。
4. **`StatusRequest` 收敛**：提升为公共 DTO（`dto/StatusRequest`），三处删除。
5. **UserHandler 对齐**：改为继承 BaseHandler 统一出口 + 错误信息走 i18n（`messages.properties` 键，不再硬编码中文）。

**收益**：删除约 200+ 行重复；新 Handler 开发成本显著下降；与 apex-generator 生成的 Handler 模板联动（生成器模板同步更新，生成产物天然统一）。

### A2. 分层违规修复（高优先）

**现状证据**（已核实）：
- `interfaces/exception/GlobalExceptionHandler.java:9` → `infrastructure.remote.exception.RemoteApiException`
- `interfaces/filter/ApiGatewayFilter.java:3` → `infrastructure.circuitbreaker.CircuitBreakerService`
- `interfaces/aspect/UserRateLimitAspect.java:7` → `infrastructure.ratelimit.local.LocalRateLimiter`
- `apex-application/pom.xml` 直接依赖 `apex-infrastructure`（应用层依赖基础设施实现）

**重构方案（依赖倒置 DIP）**：
1. `RemoteApiException`：提升到 `apex-common`（异常体系本就属于 common），infrastructure 异常继承之。
2. `CircuitBreakerService`：在 `apex-application`（或 domain）定义接口 `CircuitBreakerPort`，infrastructure 实现，`ApiGatewayFilter` 面向接口。
3. `LocalRateLimiter`：定义 `RateLimiter` 接口（application 层），infrastructure 提供 Local/Adaptive 实现，Aspect 面向接口。
4. `apex-application` 对 infrastructure 的依赖：核查后改为依赖接口所在的 domain/application 内部抽象（若为应用编排需要实现，则通过 `-DskipTests` 级 Maven 依赖方向约束或 ArchUnit 规则强制：`application 不得依赖 infrastructure`）。

**配套**：新增/强化 **ArchUnit 分层规则**（`LayerArchTest` 扩展：interfaces → application → domain 单向，infrastructure 实现 domain 接口），把违规变成 CI 门禁。

### A3. 一致性治理（中优先）
- 响应出口、错误信息 i18n、日志格式统一（见 A1）；新增 `@OperationLog` 落库补全（见 C2）。

### A4. 其他可评估的重构点（建议，不阻塞）
| 点 | 模式 | 说明 |
|----|------|------|
| 认证方式（微信/短信/未来密码） | **策略模式** `AuthenticationStrategy` | 与 C1 联动：登录类型可插拔 |
| 存储选型（Local/MinIO/TOS） | **工厂 + 条件装配** | `StorageProvider` SPI 已有但无 `META-INF/services`（未启用），建议改用 Spring 条件装配（`@ConditionalOnProperty` 已在 infra 使用）替代 SPI |
| 异常→错误码映射 | **责任链/策略** | `GlobalExceptionHandler` 内多分支可抽 `ErrorCodeMapper` |
| 双缓存键策略 | **策略模式** | `MultiLevelCache` 的键生成/序列化策略可扩展 |
| 领域事件订阅 | **观察者** | `DomainEventBus` 已有，补订阅者自动注册机制 |

---

## 三、Part B：spring-boot-starter 模式改造

### B1. 自动配置机制规范化（高优先）

**现状**：全仓无 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`、无 `spring.factories`。`LicenseAutoConfiguration`（@Configuration + @ConditionalOnProperty(apex.license.enabled)）与 infra 的 `MinioConfig/VolcEngineConfig/DefaultStorageConfiguration/MultiLevelCacheConfig/MybatisPlusConfig/RemoteApiClientConfig` **全部靠 `@SpringBootApplication(scanBasePackages="com.apex.scaffold")` 包扫描生效**——即"伪自动配置"：不可独立裁剪、不可被外部项目按需引入。

**改造方案**：
1. 在 `apex-common` 与 `apex-infrastructure` 各建 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，将上述配置类注册为**自动配置**（替代包扫描）。
2. 每个自动配置类补齐 `@ConditionalOnXxx`（已有 OnProperty 的保留）+ `@AutoConfiguration(after = ...)` 排序；对外暴露 `@EnableXxx` 开关作为二选一入口。
3. **收益**：配置变为"开箱即用 + 按需关闭"（`spring.autoconfigure.exclude` 或属性开关），这是**脚手架对买家的核心价值**——拿到项目即可启动，裁剪无需改代码。

### B2. 通用能力 starter 化（中优先，分两档）

> 原则：项目本体是"整包售卖"，**不建议把全部能力拆成独立 Maven 模块**（增加构建复杂度、违背整包交付模式）；改为**「核心 2 个真 starter + 其余自动配置规范化」**，既示范 starter 开发范式，又不牺牲整包可用性。

**第一档：真 Starter（新建独立模块，示范价值）**
| Starter | 内容 | 依赖 |
|---------|------|------|
| `apex-license-spring-boot-starter` | License 校验 + FeatureGate 切面 + 发放 API（从 apex-common/license 抽出） | 无（自包含） |
| `apex-log-spring-boot-starter`（示例） | TraceId 贯穿 + 请求日志 + 审计注解 | 无 |

**第二档：自动配置规范化（不拆模块）**
- 安全过滤链（14 WebFilter + SecurityConfig）、限流、防重放、审计、缓存、MQ+Outbox、存储 —— 全部走 B1 的 imports 注册 + 属性开关，**保留在现有模块**，通过 `apex.security.* / apex.cache.* / apex.mq.*` 等前缀开关控制。

**收益**：买家可参照 `apex-license-spring-boot-starter` 学会"如何给 Apex 写自定义 starter"（可写进 CODEGEN.md 扩展章节）；License 模块独立后，三档授权逻辑与业务解耦更彻底。

---

## 四、Part C：功能补充（产品化能力，按优先级）

### P0（核心补全 —— 影响"通用脚手架"定位，建议本批次做）

| # | 功能 | 说明 | 落点 |
|---|------|------|------|
| C1 | **账号密码登录** | 补齐传统账密认证，与微信/短信统一为 `AuthenticationStrategy`（配合 A4 策略重构）；含密码加密（BCrypt 已有 util）、登录保护（已有 LoginProtectionProperties 直接复用） | AuthHandler + TokenServiceImpl |
| C2 | **操作审计日志落地** | `@OperationLog` 注解 + `OperationLogAspect` 已有（原方案所述 `@AuditLog` 落地方案最终统一采用 `@OperationLog` 体系），补齐 `sys_operation_log` 落库 + 查询 API + 管理后台页面 | infrastructure + interfaces |
| C3 | **数据权限与部门** | `sys_dept` 表 + `@DataScope` 注解 + 切面注入部门过滤（参考 RuoYi 的 DataScope 语义）；配合 `sys_user.dept_id` | infrastructure + common 注解 |
| C4 | **在线用户与会话管理** | 在线用户列表（Redis 会话索引）+ 强制下线（清除 token）+ 踢人接口 | infrastructure + interfaces |

### P1（增强 —— 中小企高频刚需）

| # | 功能 | 说明 |
|---|------|------|
| C5 | Excel 导入导出 | EasyExcel 封装 + 通用导出工具 + 示例（如用户导出） |
| C6 | WebSocket/SSE 消息推送 | 落地 `biz_system_message` 表：站内信 + SSE 推送 + 未读数 |
| C7 | 邮件发送 | 邮件服务封装（JavaMailSender）+ 模板（欢迎/重置密码）+ 配置 |
| C8 | 国际化全量覆盖 | 现有 4 语言错误码全覆盖（审计当前未覆盖键） |
| C9 | 多数据源主从示例落地 | `@DS` 注解 + 读写分离示例 + 文档（框架已就绪） |

### P2（可选 —— 高级/成本高，按需）

| # | 功能 | 说明 |
|---|------|------|
| C10 | 多租户 | 租户上下文 + 数据隔离（表级/行级策略） |
| C11 | 分布式 ID | 雪花/号段生成器（订单、消息等场景） |
| C12 | 前端 Vue3 工程 | 管理后台从静态 HTML 升级为独立 Vue3 工程（成本高，可选） |
| C13 | CI/CD 补全 | 现有 CI 已含测试+安全扫描，补发布流水线（三档构建 → 镜像 → 部署模板） |

---

## 五、实施路线图（建议顺序，确认后执行）

```
阶段 1 · 架构治理（低风险高收益，约 1 批提交）
   ├─ A1 Handler 样板收敛（模板方法/泛型 CRUD/响应出口统一）
   ├─ A2 分层违规修复（DIP + ArchUnit 门禁）
   └─ A3 UserHandler 对齐 + i18n
   验收：全量 1869+ 测试全绿；Handler 重复行数归零

阶段 2 · Starter 化（脚手架形态升级）
   ├─ B1 AutoConfiguration.imports 注册 + 条件装配规范化
   └─ B2 apex-license-spring-boot-starter 独立模块（示范）
   验收：`spring.autoconfigure.exclude` 可裁剪；License 模块独立可测

阶段 3 · P0 功能补全
   ├─ C1 账号密码登录（AuthenticationStrategy）
   ├─ C2 审计日志落库
   ├─ C3 数据权限/部门
   └─ C4 在线用户/会话管理
   验收：三档构建 + 全量测试 + 管理后台新页面可用

阶段 4 · P1 增强（按确认范围）
   └─ C5 Excel / C6 推送 / C7 邮件 / C8 i18n 全量 / C9 主从示例

阶段 5 · P2 可选（评估后决定）
   └─ C10 多租户 / C11 分布式 ID / C12 前端 / C13 CD
```

**风险提示**：
- A1/A2 重构面广，必须**每阶段全量 `mvn -o test` 回归**（当前基线 1869，mvnd flaky 约定仍适用：全量验证用 mvn 非 mvnd）。
- Handler 重构会触碰 apex-generator 模板 → 模板与生成产物需**同步升级**（codegen-expert 的接线清单加"模板对齐"项）。
- License starter 化涉及商业化机制，**必须先跑 License 全部测试**（9+ 用例）再动。
- 分层修复若影响 `apex-application` 依赖方向，可能触发现有 ArchUnit 规则变更，需同步更新测试断言。

---

## 六、待确认决策点

执行前需要确认以下范围（可全选/单选/组合）：

| 决策点 | 选项 |
|--------|------|
| **D1 执行范围** | A 仅架构治理 / B 仅 starter 化 / C 仅功能补充 / **A+B+C 全部（推荐）** |
| **D2 优先级** | 阶段 1→5 顺序执行（推荐）/ 指定某阶段先行 |
| **D3 starter 化深度** | 仅自动配置规范化（B1）/ B1+2 个真 starter（推荐）/ 全量拆模块 |
| **D4 P1 功能范围** | 全做 / 只做 C5+C8+C9 / 指定子集 |
| **D5 P2 是否纳入** | 暂不纳入（推荐，评估后再说）/ 纳入 C10+C11 |

---


---

## 七、实施状态（2026-08-27 更新）

> 本方案已按阶段全部实施完成（提交 97c6099 / b150360 / 6a36597 / 310234b / 7612cdf）。

| 阶段 | 内容 | 状态 |
|------|------|------|
| 1 架构治理 | A1 Handler 样板收敛 + A2 分层 DIP + A3 一致性 | ✅ 完成 |
| 2 Starter 化 | B1 AutoConfiguration 规范化 + B2 License Starter 模块 | ✅ 完成 |
| 3 P0 功能 | C1 账密登录 / C2 审计落库 / C3 部门数据权限 / C4 在线用户 | ✅ 完成 |
| 4 P1 增强 | C5 Excel / C6 SSE / C7 邮件 / C8 i18n 全量 / C9 主从示例 | ✅ 完成 |
| 5 P2 可选 | C10 多租户 / C11 分布式 ID / C13 发布流水线 | ✅ 完成 |

**未纳入**：C12 前端 Vue3 工程（独立前端立项、成本高，需单独评估后再实施）。

**基线**：全量 1880 测试全绿（common 443 / license 9 / domain 434 / infra 341 / application 329 / interfaces 318 / generator 6），bias 门禁零命中。

*本文档为评审方案；所有改动已分批实施完毕，验收标准见各阶段提交说明。*

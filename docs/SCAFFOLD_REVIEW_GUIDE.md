# Apex Scaffold 通用后端脚手架评审与改进指南

> **评审日期**: 2026-08-26
> **评审范围**: 当前仓库全部 6 个 Maven 模块（common/domain/application/infrastructure/interfaces/example）
> **评审基准**: 对照 RuoYi-Vue-Plus、JeecgBoot、pig、eladmin、mall 等主流商用脚手架通用能力
> **文档状态**: v1.0

---

## 目录

1. [评审概述](#1-评审概述)
2. [现状能力盘点](#2-现状能力盘点)
3. [总体评价](#3-总体评价)
4. [差距分析与改进建议（P0 优先）](#4-差距分析与改进建议p0-优先)
5. [改进建议详单（P1）](#5-改进建议详单p1)
6. [改进建议详单（P2）](#6-改进建议详单p2)
7. [落地路线图](#7-落地路线图)
8. [附录：主流脚手架功能对照表](#8-附录主流脚手架功能对照表)

---

## 1. 评审概述

### 1.1 评审方法

- 逐层盘点 6 个模块的 Handler、Service、基础设施组件、数据库表、文档资产；
- 以"一个采购方拿到脚手架后能否独立完成一个企业级管理系统"为验收视角；
- 对照主流商用脚手架（RuoYi 系、JeecgBoot、pig、eladmin）的通用能力清单逐项比对；
- 按影响售卖力度的优先级分级：**P0 阻塞售卖 / P1 影响售卖 / P2 增强竞争力**。

### 1.2 评审结论（摘要）

**当前脚手架技术底座扎实、工程质量优秀，但"产品完整度"不足**：

- ✅ **强项**：WebFlux 全响应式、DDD 五层、JWT+微信+短信三通道认证、RBAC、多级缓存、RocketMQ+Outbox、存储 SPI、限流/防重放/脱敏/审计、License 商业机制、双轨许可证、三档构建、CI/安全扫描。
- ❌ **核心短板（P0）**：**缺少系统管理能力（菜单/字典/参数/定时任务）与可用的管理后台**——这是所有企业级管理系统的地基，也是客户上手的第一道门槛。
- ❌ **主要短板（P1）**：代码生成器、Excel 导入导出、验证码、多租户、邮件、WebSocket 推送、文件管理后台、登录日志/在线用户管理等通用业务能力缺失。
- ⚠️ **文档与示例（P1）**：示例模块过于单薄（仅 CRUD + 一个 @FeatureGate 示例），缺部署指南/FAQ/升级指南，管理后台无系统管理页面。

---

## 2. 现状能力盘点

### 2.1 接口层（apex-interfaces）— 8 个通用 Handler

| 分组 | Handler | 能力 |
|---|---|---|
| 认证 | `AuthHandler` | 密码/短信/微信登录、刷新 Token、短信验证码、本机号校验、绑定手机号 |
| RBAC | `AuthorizationHandler` | 角色/权限管理、菜单权限查询（部分） |
| 用户 | `UserHandler` | 用户信息查询/更新、注销申请、同意管理 |
| 文件 | `FileHandler` | 通用上传/下载/删除/存在性检查、分片代理 |
| 健康 | `HealthHandler` | 健康检查、版本信息 |
| 基础 | `BaseHandler` | 通用基础接口 |
| 管理 | `AdminUserManagementHandler` | 管理员用户增删改查、角色分配、会话吊销、状态管理 |
| 存储 | `AbstractStorageProxyHandler` | 存储代理抽象 |

### 2.2 通用组件（apex-common）— 已有能力

**安全注解**：`@RequireRole`、`@RequirePermission`、`@RateLimiter`、`@UserRateLimit`、`@HotSpotRateLimit`、`@AntiReplay`、`@DataMask`、`@AuditLog`、`@OperationLog`、`@ApiVersion`、`@FeatureGate`、`@ConcurrencyControl`、`@OptimisticLockRetry`、`@Logical`、`@AggregateRoot`

**工具/基础**：`JsonUtil`、`DateUtil`、`JwtUtil`、`PasswordUtil`、`TraceIdUtil`、`DataMaskUtil`、`SensitiveDataUtil`、`RequestUtil`、`HttpStatusUtil`、`FileUrlUtil`、`ExpiringLocalCache`、统一响应 `Result`、错误码 `ErrorCode`/`ResultCode`、全局异常、领域事件基类、License 套件、SPI 存储接口、阻塞 IO 调度器

### 2.3 领域层（apex-domain）— 23 个实体 + 16 个服务端口

**实体**：用户、角色、权限、用户角色、角色权限、用户画像、用户合规、用户同意、操作日志、API 调用日志、文件、订阅、信用账户、信用流水、黑名单、举报、举报工单、政策版本、系统消息、问卷问题、引导答案、内容审核、样例

**服务端口**：TokenService、TokenBlacklistService、CacheService、CacheConsistencyPort、SmsCodeService、StorageService、DomainEventPublisher、CreditService、AccountDeletionPort、AdminReviewAuditPort、BusinessMetricsPort、ConsentCommandPort 等

### 2.4 基础设施层（apex-infrastructure）

| 能力 | 实现 |
|---|---|
| 多级缓存 | Caffeine + Redis（Redisson + Spring Data Redis）、缓存注解切面、一致性维护 |
| 消息队列 | RocketMQ 生产者/消费者封装、死信队列处理、领域事件发布 |
| 事件总线 | Outbox 模式可靠投递、重试、指标上报 |
| 存储 | Local / MinIO / TOS 三实现，SPI 可插拔 |
| 短信 | 创蓝短信网关（可插拔 SPI） |
| 内容审核 | 百度图文审核（可插拔） |
| 安全 | 网关过滤器（请求 ID/慢请求/指标）、限流（本地/自适应）、防重放、安全响应头、输入校验、登录保护 |
| 可观测 | Prometheus 指标、Micrometer、Zipkin 追踪、Trace 日志、进程内存指标 |
| 弹性 | Sentinel（系统规则/慢调用降级）、Resilience4j 熔断、优雅停机、线程池隔离 |
| 持久化 | MyBatis-Plus、多数据源（dynamic-datasource）、Druid 连接池、Flyway、p6spy |
| 运维 | 混沌注入（chaos 包）、异常监控、启动就绪日志、生产配置校验器 |

### 2.5 数据库（27 张通用表）

RBAC 六件套（sys_user/role/permission/user_role/role_permission）、sys_operation_log、sys_file、sys_api_call_log、sys_domain_event_outbox、sys_account_deletion_task、audit_log + 16 张 biz_* 通用业务表（画像/合规/同意/黑名单/订阅/信用/系统消息/政策/问卷/举报/审核等）

### 2.6 工程化与商业化

- 质量门禁：Spotless、PMD(P3C)、JaCoCo（90% 覆盖率）、ArchUnit 分层、CI（编译+单测+集成测试）、OWASP 依赖扫描
- 商业机制：License 强制启用（RSA 签名 + 容错降级）、`@FeatureGate`、三档 Maven Profile（产物可区分）、双轨许可证、发放/发布脚本
- 文档：17 篇（快速开始/架构/配置/最佳实践/API 版本/审计/SPI/JMH/License/商业版本/故障排查）

---

## 3. 总体评价

| 维度 | 评分（5 分制） | 说明 |
|---|---|---|
| 技术底座 | ⭐⭐⭐⭐⭐ | WebFlux 全响应式 + DDD 五层，工程质量高 |
| 安全能力 | ⭐⭐⭐⭐⭐ | 认证/授权/限流/防重放/脱敏/审计/内容审核全覆盖 |
| 可观测性 | ⭐⭐⭐⭐⭐ | 指标/追踪/日志/监控面板齐全 |
| 工程化 | ⭐⭐⭐⭐⭐ | 门禁/CI/覆盖率/架构测试完善 |
| 商业机制 | ⭐⭐⭐⭐ | License/三档/双轨许可证已落地 |
| **系统管理** | ⭐⭐ | 无菜单/字典/参数/定时任务管理 |
| **管理后台** | ⭐⭐ | 静态三件套，无系统管理页面 |
| **通用业务能力** | ⭐⭐ | 缺 Excel/验证码/邮件/多租户/代码生成器 |
| **示例与文档** | ⭐⭐⭐ | 示例单薄，缺部署/FAQ/升级指南 |

**综合定位**：一个"工程质量极佳的响应式内核"，但尚缺"让客户 30 分钟跑通一个完整管理系统"的产品化外衣。

---

## 4. 差距分析与改进建议（P0 优先）

> P0 = 不做就无法支撑企业级管理系统的售卖，直接阻塞大部分客户采购决策。

### P0-1 系统管理能力（菜单/字典/参数）

**现状**：无菜单表、无字典表、无参数表，管理后台无系统配置能力。

**差距**：所有主流脚手架（RuoYi/JeecgBoot/pig/eladmin）都标配"菜单管理 + 字典管理 + 参数配置"三件套，这是管理系统的标配地基。

**建议方案**：
1. 新增 3 张通用表（V2 迁移）：
   - `sys_menu`：菜单/按钮权限树（id、parent_id、name、type[M/C/F]、path、component、perms、icon、sort、visible、status）
   - `sys_dict_type` / `sys_dict_data`：字典类型与字典项（label、value、type、sort、status）
   - `sys_config`：参数配置（key、value、remark、built-in）
2. 新增 3 组通用 Handler：`SystemMenuHandler`、`SystemDictHandler`、`SystemConfigHandler`（含缓存刷新）
3. 菜单树与 RBAC 权限码联动（`AuthorizationHandler` 查询用户菜单树）
4. admin 后台增加"系统管理"页面：菜单树管理、字典管理、参数管理

**涉及模块**：apex-domain（实体/仓储）、apex-infrastructure（PO/Mapper）、apex-application（Service/DTO）、apex-interfaces（Handler/路由）、静态后台
**优先级**：P0 ｜ **工作量**：L（跨层新增）

---

### P0-2 定时任务管理（动态注册/启停/日志）

**现状**：仅 Spring `@Scheduled` 静态任务（SchedulingConfig + 启动日志），无任务管理 API、无任务执行日志、无法动态启停。

**差距**：主流脚手架提供"定时任务管理"（quartz 或 xxl-job 封装），支持在线建任务/改 cron/启停/查看执行日志。

**建议方案**：
1. 新增 `sys_job` 表（job_name、bean_name、method、cron、params、status、misfire_policy）+ `sys_job_log` 表
2. 基于 Spring `TaskScheduler` 的动态任务注册中心（`DynamicTaskRegistry`）：注册/取消/暂停/恢复
3. `SystemJobHandler`：任务 CRUD + 立即执行 + 执行日志查询
4. 可插拔：`JobInvoker` SPI（Bean 方法调用 / REST 调用），企业版可扩展 xxl-job

**涉及模块**：全部层 + V2 迁移
**优先级**：P0 ｜ **工作量**：L

---

### P0-3 管理后台完善（从"审核控制台"升级为"系统管理控制台"）

**现状**：`static/admin/` 静态三件套，仅含用户/角色/权限基础视图，无系统管理页面，无操作日志/API 日志/文件管理页。

**差距**：客户验收时首先打开管理后台 —— 当前后台无法完成"登录 → 建角色 → 配菜单权限 → 建用户 → 看日志"的完整闭环。

**建议方案**：
1. 保留现有静态技术栈（决策 D2 已定），新增页面：
   - 系统管理：菜单树、字典、参数、定时任务（对接 P0-1/P0-2）
   - 日志中心：操作日志查询（操作人/模块/时间范围）、API 调用日志查询
   - 文件管理：文件列表/预览/删除（对接 `FileHandler` + `sys_file` 表）
   - 用户管理完善：详情页（画像/角色/会话）
2. 补齐前端鉴权：菜单/按钮权限动态渲染
3. 对接 `AuthorizationHandler` / `AdminUserManagementHandler` / 新增系统管理 API

**涉及模块**：apex-interfaces 静态资源 + 后端 API
**优先级**：P0 ｜ **工作量**：L

---

### P0-4 登录安全增强（图形验证码 + 登录日志）

**现状**：有短信验证码与登录保护（IP/账号锁定），但**无图形验证码**、**无登录日志**（`sys_login_log` 表缺失，登录成功/失败不可追溯）。

**差距**：企业系统登录页标配图形验证码 + 登录日志（等保 2.0 要求登录行为审计）。

**建议方案**：
1. 新增图形验证码（Kaptcha 或自实现 SVG/Base64）：`/auth/captcha` 生成、Redis 存储、登录校验
2. 新增 `sys_login_log` 表 + 登录成功/失败异步落库（用户名、IP、UA、结果、失败原因）
3. `AuthService` 登录链路埋点：成功/失败（账号不存在/密码错误/锁定/验证码错误）
4. admin 后台"登录日志"查询页

**涉及模块**：apex-domain/application/interfaces + V2 迁移
**优先级**：P0 ｜ **工作量**：M

---

## 5. 改进建议详单（P1）

> P1 = 影响售卖的通用能力，客户会明确询问。

### P1-1 代码生成器（表 → CRUD 全栈代码）

**现状**：无（方案决策 D3 已排入二期）。

**建议方案**：
1. `apex-codegen` 独立模块：解析表结构（information_schema）→ 生成 Entity/PO/Mapper/Service/DTO/Handler/路由/前端页面/迁移脚本
2. 模板引擎：Velocity 或 FreeMarker，内置 DDD 五层模板
3. 管理后台"代码生成"页：选表 → 预览 → 下载/直接写入
4. 与 `sys_config` 联动可配置包名/模块名/前缀

**优先级**：P1 ｜ **工作量**：L（独立模块）

---

### P1-2 Excel 导入导出

**现状**：无 Excel 能力（README 承诺的标准版功能未实现）。

**建议方案**：
1. 引入 EasyExcel，封装 `ExcelUtil`：`export(list, clazz, sheetName)` / `import(file, clazz, validator)`
2. 注解 `@ExcelProperty` 兼容、错误行收集（返回导入失败明细）
3. 示例：用户列表导出、字典批量导入
4. 异步导出（大文件）：任务表 + 下载通知

**涉及模块**：apex-common + apex-example
**优先级**：P1 ｜ **工作量**：M

---

### P1-3 多租户支持

**现状**：无（决策 D7 二期）。SaaS 场景硬需求。

**建议方案**：
1. `TenantContext`（Reactor Context 传递租户 ID）+ MyBatis-Plus 租户插件（`TenantLineInnerInterceptor`）
2. 通用表增加 `tenant_id` 列规范（V2 迁移可加）
3. `@IgnoreTenant` 注解（登录/字典等全局表跳过）
4. 配置开关 `apex.tenant.enabled` + 文档

**优先级**：P1（SaaS 客户） ｜ **工作量**：L

---

### P1-4 邮件发送封装

**现状**：无邮件能力。

**建议方案**：
1. Spring Mail 封装 `MailService`：普通文本/HTML/附件/模板邮件（Thymeleaf）
2. 配置：`apex.mail.*`（host/port/username/password/from）
3. 示例：注册欢迎邮件、验证码邮件、告警通知

**涉及模块**：apex-infrastructure + apex-example
**优先级**：P1 ｜ **工作量**：S-M

---

### P1-5 实时消息推送（WebSocket）

**现状**：无 WebSocket；`biz_system_message` 表存在但无推送通道。

**建议方案**：
1. Spring WebFlux WebSocket 封装：连接管理、鉴权（JWT）、心跳
2. `NotificationService`：站内信持久化（biz_system_message）+ WebSocket 实时推送 + 未读数
3. 广播/点对点/按角色推送
4. admin 后台"消息管理"页 + 示例

**涉及模块**：apex-infrastructure + apex-interfaces + apex-example
**优先级**：P1 ｜ **工作量**：L

---

### P1-6 在线用户与会话管理

**现状**：`AdminUserManagementHandler.revokeSessions` 可踢人，但无"在线用户列表/当前会话列表"。

**建议方案**：
1. Redis 维护在线会话索引（`apex:online:*`，登录写入、心跳续期、踢出删除）
2. `OnlineUserHandler`：在线用户列表（用户名/IP/登录时间/最后活跃）、强制下线
3. admin 后台"在线用户"页

**优先级**：P1 ｜ **工作量**：M

---

### P1-7 文件管理完善（预览/统一访问/后台管理）

**现状**：上传/下载 API 有，`sys_file` 表有，但无：统一访问 URL 规则、图片缩略图、后台文件管理页。

**建议方案**：
1. `FileHandler` 补充：图片预览（尺寸裁剪可选）、公开/私有访问控制、防盗链
2. 后台文件管理页：列表/搜索/预览/删除（逻辑删 + 物理删策略）
3. 文件类型白名单 + 大小限制管理化（sys_config 驱动）

**优先级**：P1 ｜ **工作量**：M

---

### P1-8 示例模块增强

**现状**：apex-example 仅 CRUD + @FeatureGate 示例（8 个主文件）。

**建议方案**（对应方案 C4）：
1. JWT 登录完整示例（注册→登录→带 Token 调接口）
2. Redis 缓存示例（`@Cacheable` / CacheService 双写）
3. 文件上传/下载示例（Local/MinIO 切换）
4. RocketMQ 生产/消费示例（含死信）
5. i18n 示例（中英文切换）
6. 注解示例集：`@AuditLog`/`@DataMask`/`@RateLimiter`/`@AntiReplay`/`@ApiVersion`/`@FeatureGate`
7. 多数据源示例（主库/从库 `@DS`）
8. 每个示例配 README + 测试

**优先级**：P1 ｜ **工作量**：M-L

---

### P1-9 文档体系补齐

**现状**：17 篇文档，缺部署与运营类文档。

**建议方案**：
1. 部署指南：Docker Compose 全量部署 / K8s 部署 / 裸机部署（含 Nginx/HTTPS）
2. FAQ（常见问题 30+）
3. 版本升级指南（v1.0→v1.1 升级步骤）
4. 管理后台使用指南（登录→建角色→配权限→建用户→看日志全流程截图）
5. 三档功能差异白皮书（销售物料，见 P2-3）

**优先级**：P1 ｜ **工作量**：M

---

## 6. 改进建议详单（P2）

> P2 = 增强竞争力与差异化，可按销售反馈择机落地。

| # | 能力 | 现状 | 建议方案 | 工作量 |
|---|---|---|---|---|
| P2-1 | 灰度发布 | 无 | `k8s/canary-deployment.yaml` + `hpa.yaml`（方案 C7）+ 文档 | S |
| P2-2 | 备份恢复脚本 | 无 | `scripts/backup/db-backup.sh`/`db-restore.sh`（mysqldump + 保留策略，方案 C6） | S |
| P2-3 | 性能基准与容量文档 | 有 JMH 基础 | 扩展登录/CRUD/缓存/文件基准，输出容量估算报告（方案 C8） | M |
| P2-4 | 数据权限 | 仅按钮权限 | 数据范围注解 `@DataScope`（全部/本部门/本人）+ 部门表 | M |
| P2-5 | 部门/岗位管理 | 无 | `sys_dept`/`sys_post` 表 + 管理 API（配合 P2-4） | M |
| P2-6 | 通知公告 | 无 | `sys_notice` 表 + 公告管理（后台发布、前台轮播） | S-M |
| P2-7 | 登录/操作日志自动归档 | 无 | 日志表分区/归档任务（基于 P0-2 定时任务） | M |
| P2-8 | 国际化文案补全 | 4 语言 | 覆盖全部通用错误码与提示（th_TH 校验） | S |
| P2-9 | 分布式锁工具 | 有 Redisson | 抽出 `LockService`（tryLock 模板）供业务直接使用 + 示例 | S |
| P2-10 | 幂等提交 | 有 IdempotentService | 暴露 `@Idempotent` 注解 + Redis 幂等键 + 示例 | S |
| P2-11 | OpenAPI 增强 | 已完善 | 增加接口权限说明/分页参数默认文档、错误码汇总页 | S |
| P2-12 | 数据库健康/慢 SQL 治理 | Druid stat | 慢 SQL 告警（阈值化）+ 大表索引建议脚本 | M |
| P2-13 | 前端工程化 | 静态后台 | 评估 Vue3 工程（决策 D2 已定静态，保留为长期路线图） | L |
| P2-14 | 分库分表 | 无 | ShardingSphere 接入（企业版差异化卖点） | L |
| P2-15 | 工作流 | 无 | Flowable 集成（企业版差异化卖点，可 OEM） | L |

---

## 7. 落地路线图

### 第一阶段：系统管理地基（P0，约 2 周）
1. P0-1 系统管理三件套（菜单/字典/参数）—— 含 V2 迁移 + 3 组 Handler + admin 页面
2. P0-2 定时任务管理 —— 含 sys_job/sys_job_log + 动态注册中心
3. P0-3 管理后台升级 —— 系统管理 + 日志中心 + 文件管理页

### 第二阶段：安全与通用业务（P1，约 2-3 周）
4. P0-4 图形验证码 + 登录日志
5. P1-2 Excel 导入导出
6. P1-6 在线用户管理
7. P1-7 文件管理完善
8. P1-8 示例模块增强（登录/缓存/文件/MQ/i18n/注解/多数据源）

### 第三阶段：差异化能力（P1-P2，约 2-4 周）
9. P1-1 代码生成器（独立模块）
10. P1-3 多租户
11. P1-4 邮件 / P1-5 WebSocket 推送
12. P2 项按销售反馈择优

### 第四阶段：发布（P2）
13. P1-9 文档补齐（部署/FAQ/升级/后台指南）
14. P2-1/P2-2 灰度与备份脚本
15. 阶段 E 剩余项（营销物料深化、安全复核；v1.1.2/v1.1.3 已发布）

---

## 8. 附录：主流脚手架功能对照表

| 能力项 | Apex Scaffold | RuoYi-Vue-Plus | JeecgBoot | pig | 备注 |
|---|---|:---:|:---:|:---:|---|
| 技术栈 | WebFlux 响应式 | Spring MVC | Spring MVC | Spring MVC | 差异化卖点 |
| DDD 分层 | ✅ | ⚠️ | ❌ | ⚠️ | 卖点 |
| 认证（JWT/微信/短信） | ✅ | JWT | JWT+三方 | JWT | 超集 |
| RBAC 按钮权限 | ✅ | ✅ | ✅ | ✅ | 同级 |
| 菜单管理 | ❌ | ✅ | ✅ | ✅ | **P0-1** |
| 字典管理 | ❌ | ✅ | ✅ | ✅ | **P0-1** |
| 参数配置 | ❌ | ✅ | ✅ | ✅ | **P0-1** |
| 定时任务管理 | ❌ | ✅(quartz) | ✅(xxl-job) | ✅ | **P0-2** |
| 代码生成器 | ❌ | ✅ | ✅ | ✅ | **P1-1** |
| Excel 导入导出 | ❌ | ✅ | ✅ | ✅ | **P1-2** |
| 图形验证码 | ❌ | ✅ | ✅ | ✅ | **P0-4** |
| 登录日志 | ❌ | ✅ | ✅ | ✅ | **P0-4** |
| 多租户 | ❌ | ⚠️ | ✅ | ✅ | **P1-3** |
| 邮件发送 | ❌ | ✅ | ✅ | ✅ | **P1-4** |
| WebSocket 推送 | ❌ | ✅ | ✅ | ✅ | **P1-5** |
| 在线用户管理 | ⚠️ 踢人 | ✅ | ✅ | ✅ | **P1-6** |
| 部门/岗位/数据权限 | ❌ | ✅ | ✅ | ✅ | P2-4/5 |
| 操作日志 | ✅ | ✅ | ✅ | ✅ | 同级 |
| API 调用日志 | ✅ | ⚠️ | ⚠️ | ⚠️ | 超集 |
| 多级缓存 | ✅ | ✅ | ✅ | ✅ | 同级 |
| 消息队列 | ✅ RocketMQ | ✅ | ✅ | ✅ | 同级 |
| 限流/防重放/脱敏/审计 | ✅ | ⚠️ | ⚠️ | ⚠️ | 超集 |
| 内容审核 | ✅ 百度 | ❌ | ❌ | ❌ | 差异化 |
| 链路追踪/监控 | ✅ | ⚠️ | ✅ | ✅ | 同级 |
| License 商业保护 | ✅ | ❌ | ❌ | ❌ | **差异化卖点** |
| 三档版本 Profile | ✅ | ❌ | ❌ | ❌ | **差异化卖点** |
| 管理后台可用性 | ⚠️ 简陋 | ✅ 成熟 | ✅ 成熟 | ✅ 成熟 | **P0-3** |
| 示例完整度 | ⚠️ 单薄 | ✅ | ✅ | ✅ | **P1-8** |

**结论**：Apex Scaffold 在"响应式架构、安全纵深、可观测性、商业保护"上领先主流 MVC 脚手架；差距集中在"系统管理三件套、管理后台、代码生成器、Excel、验证码"等**产品化标配能力**。补齐 P0 四项即可具备与主流脚手架正面竞争的基础，P1 完成后可形成"响应式 + 安全 + 商业保护"的差异化优势。

---

**编制**: Apex Team
**下次评审**: 阶段 E 发布前

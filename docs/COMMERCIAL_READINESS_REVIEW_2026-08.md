# Apex Scaffold 商业化售卖就绪度评审方案（2026-08-29）

**评审基线**: 工作树当前状态（含未提交变更，最后提交 `13c23e9`，pom 版本 1.1.4 / CHANGELOG 1.2.0-alpha）
**评审方式**: 四路并行代码级审查（核心运行时 / 基础设施 / License 与生产力工具 / 交付工程化），以实际代码为准，不以文档宣称为准
**原始结论**: **功能完成度约 85%，具备产品雏形；但存在 6 项 P0 级问题（集中在商业保护形同虚设、版本矩阵与实现不符、敏感数据泄露），不修复不建议开卖**

> ### 🎯 v1.2.0 整改完成（2026-08-29，同日归档）
> 本次评审提出的 6 项 P0 / 4 项 P1 已按优先级全部闭环（除 License 机器指纹绑定 P0-1a 作为 v1.2.1 规划外）。
>
> | P# | 结论 | 整改证据 |
> |----|------|---------|
> | P0-1 License | ⚠️ 除机器指纹（v1.2.1）外均收编 | `apex.license.enabled=true` 默认装配；加固项 A/B 见 IMPROVEMENT_TASKS Task 2.4 |
> | P0-2 FeatureGate | ✅ | SecurityConfig / 多级缓存 / Sentinel / MQ / tracing 均用 `@ConditionalOnProperty(apex.features.*) + @ConditionalOnClass` |
> | P0-3 三档无差异 | ✅ | basic profile 真裁剪 + 条件化装配；COMMERCIAL_VERSIONS.md 矩阵已与实现对齐 |
> | P0-4 敏感数据仓库化 | ✅ | `backups/apex-mysql-full-20260828.sql`、`lr`（mvnd 构建日志）均删除 |
> | P0-5 微信支付验签 | ✅ | 微信支付整体下架（移除 `weixin-java-pay` 依赖 + 清除配置/注释残留） |
> | P0-6 发布状态混乱 | ✅ | pom = 1.2.0；CHANGELOG 新增 [1.2.0] 节；COMMERCIAL_IMPROVEMENT_TASKS.md 状态校准 |
> | P1-3 多租户 | ✅ | V14 迁移 + DefaultTenantLineHandler + MybatisPlusConfig 条件注册 + Enterprise 默认开 |
> | P1-6 i18n/品牌 | ✅ | zh_TW/th_TH 补齐至 193 键；所有 Apex.Mar 残留改为 Apex Scaffold |
> | P1-9 文档收敛 | ✅ | IMPROVEMENT_TASKS / GAP_ANALYSIS / VERSIONS 状态统一；本文档作为归档保留 |
> | P1-10 坏注释 | ✅ | 见对应整改提交 |
>
> **整改后综合就绪度**：92/100（v1.2.0 正式可售卖；剩余 P2 测试回归 + P0-1a 机器指纹绑定在 v1.2.1 收口）。
> **对应任务与交付清单**：见 `docs/COMMERCIAL_IMPROVEMENT_TASKS.md` 进度跟踪 v1.2.0 快照。

---

## 1. 总体就绪度评分

> 🔖 下表为 **评审基线时的原始评分**（v1.1.4 未提交态）。v1.2.0 发布态评分见上方整改完成声明。

| 维度 | 评分 | 结论 |
|------|------|------|
| 功能实现（管理端/横切能力/基础设施） | 90/100 | 完成度很高，几乎无 TODO 残留 |
| 安全体系 | 80/100 | 纵深完整，但微信支付验签有 P0 TODO |
| 商业保护（License/三档版本） | 35/100 | **验证机制可被一键绕过，三档无实质差异，与宣传不符** |
| 交付工程化（CI/部署/监控/文档） | 75/100 | 资产齐全，但文档状态漂移、仓库卫生有泄露风险 |
| 一致性（版本号/文档/矩阵 vs 实现） | 55/100 | 多处虚标与过期 |
| **综合** | **72/100** | 修复 P0 后可达 90+，具备售卖条件 |

---

## 2. 已具备的能力（真实落地，有测试佐证）

**管理端功能域**（WebFlux 函数式，`apex-interfaces/.../handler/`）：认证（多登录策略/双 Token/续期/黑名单）、RBAC、后台用户、部门、菜单、字典、参数、定时任务（自研 Reactor 调度器 + Cron 解析）、登录/审计/操作/调用日志、文件（Local/MinIO/火山 TOS 三实现）、验证码、在线用户、监控大盘、SSE 通知、健康检查。

**安全纵深**：JWT 双 Token + 设备绑定 + 启动密钥校验、登录防爆破（IP/用户/锁定升级验证码）、IP 滑窗限流 + 封禁、Sentinel 热点限流 + Nacos 动态规则、防重放、XSS/SQL 注入过滤、扫描器拦截、SecurityHeader；prod 配置强制安全项（`JWT_SECRET`/`REDIS_PASSWORD` 无默认值）。

**基础设施**：Redisson+Caffeine 二级缓存（响应式、Single-Flight 击穿合并、穿透空标记、写穿透）、RocketMQ（sync/async/delay + 死信监听 + 消费幂等）、Outbox 模式领域事件（表+轮询投递+指标）、动态数据源（master/slave 配置就绪）、数据权限 SQL 改写拦截器（5 种 scope）、乐观锁（@Version + 0 行更新转异常 + 重试切面）、远程调用（WebClient + R4j 熔断/重试/连接池/调用日志/混沌注入）、分布式锁切面、幂等（注解 + Service 两层）。

**工具与交付**：代码生成器（DDD 五层 + Vue3 前端 4 模板 + 迁移 + 测试骨架）、一键初始化脚本、Dockerfile 三档构建、Compose 应用栈 + 9 组件基础设施栈、k8s 部署 + 金丝雀、DB 备份/恢复脚本、压测脚本、Prometheus/告警/Grafana 大盘一键栈、CI 三 workflow（ci-cd/release/security-scan）、266 个测试文件、i18n 四语言、采购材料包（docs/sales/ 四份）。

---

## 3. 问题清单

### 🔴 P0 —— 售卖阻断项（必须修复）

| # | 问题 | 证据 | 风险 |
|---|------|------|------|
| P0-1 | **License 可一键绕过**：`apex.license.enabled=false`（`matchIfMissing=true` 条件装配整体 back off）→ FeatureGateAspect 不注册、商业功能全开；`LicenseGenerator`（gen-keys + gen-license）随源码分发，fork 者可自签永久 ENTERPRISE License；无机器指纹绑定；仅启动校验一次，运行期过期不失效；`checkUserLimit/checkConcurrentLimit` 无调用方 | `apex-license-spring-boot-starter/.../LicenseAutoConfiguration.java:98`、`RsaUtil.java`、`LicenseValidator.java` | 收入保护为零；`docs/LICENSE_MANAGEMENT.md` 宣称"不可被跳过"构成虚假承诺 |
| P0-2 | **商业功能无 FeatureGate 门控**：`@FeatureGate` 全仓库仅 example 演示端点 1 处；RocketMQ/Zipkin/Sentinel/多级缓存/RBAC 等付费组件无任何 License 联动，全靠 yml 开关（fork 随意打开） | `apex-example/.../ExampleUserHandler.java:165`；infrastructure 全部 `@ConditionalOnProperty` 只看业务开关 | 三档定价（¥9,999/¥49,999）没有技术强制力 |
| P0-3 | **三档版本无实质差异**：Maven Profile 不裁剪依赖（三档 jar 相同）；`apex.features.*` 仅被启动日志打印，不驱动任何 Bean；basic 档 `features.rbac=false` 但 `SecurityConfig` 无条件启用 RBAC；`COMMERCIAL_VERSIONS.md` 矩阵（Basic 无 RBAC/缓存、审计日志 Enterprise 专属等）与实现不符 | `pom.xml:599-652`、`FeatureProperties.java`、`application-basic.yml` | **版本对比页与实物不符 → 虚假宣传/合同纠纷风险** |
| P0-4 | **敏感数据入库**：`backups/apex-mysql-full-20260828.sql`（140K，13 张表真实数据 dump）被 git 跟踪；`config/license/private.key`（签发私钥！）在工作目录（已 ignore、无入库历史，但需确认打包/交付排除）；`lr`（mvnd 构建日志）被 git 跟踪 | `git ls-files backups/ lr` | 数据泄露 + 商业签发私钥失控 |
| P0-5 | **微信支付回调验签未实现**（`TODO P0-WX-1`） | `apex-infrastructure/.../wechat/config/WeChatPayProperties.java:96` | 支付安全漏洞；若不售卖微信支付模块则应整体移除而非带伤交付 |
| P0-6 | **发版状态混乱**：57 文件 +6661 行未提交（含 datapermission/datasource/监控大量新代码）；pom 1.1.4 vs CHANGELOG 1.2.0-alpha；`COMMERCIAL_IMPROVEMENT_TASKS.md` 多处虚标（机器指纹标 ✅ 实未做；灰度/备份标"待开始"实已完成） | `git status`、`git diff --stat` | 无法构建可信发布产物 |

### 🟡 P1 —— 首发质量项（强烈建议，开卖前完成）

| # | 问题 | 说明 |
|---|------|------|
| P1-1 | `@ApiVersion` 注解无任何消费者（虚标已完成）；版本控制全靠路由硬编码 `/v1` | 删除或实现，二选一 |
| P1-2 | 双错误码体系并存（`common/error/ErrorCode` 40 码 vs `common/result/ResultCode` 105 码） | 统一为一套 |
| P1-3 | 多租户仅 `TenantContext`+`TenantAspect`，无 `tenant_id` 字段、无 `TenantLineInnerInterceptor`；README 宣称适用 SaaS 平台 | 补齐或明确降级宣传 |
| P1-4 | RocketMQ 无事务消息；13 处事件监听器/TokenService 里 `block()`（响应式纯度短板）；MQ 切面（幂等/CacheLock）为阻塞式仅限 MQ 线程 | 文档需明确使用边界或改造 |
| P1-5 | 通知体系分散：MailService 82 行无模板/附件；无统一 Notification 门面；无钉钉通道（但 prod 配置里有 `DINGDING_SECRET` 残留键） | 统一门面或删残留配置 |
| P1-6 | i18n zh_TW/th_TH 比 zh_CN 少 51 键；i18n 文件头 "Apex.Mar" 品牌残留 | 补齐/清理 |
| P1-7 | `new-project.sh` 仅 BSD sed（Linux 全部替换失败，与自述"通用"矛盾）；不改 Maven 模块名/artifactId/groupId | 修复平台兼容 |
| P1-8 | 横切注解"框架完整、业务欠喂"：`@DataSource`/`@OptimisticLockRetry`/`@DataMask`/`@AntiReplay` 在售卖核心功能中 0~极少使用点，仅 example 演示；Excel 导出仅 1 个用户端点 | 在 admin 域补真实使用点，提升示范可信度 |
| P1-9 | 文档漂移：`COMMERCIAL_GAP_ANALYSIS`/`IMPROVEMENT_TASKS` 状态过期；`COMMERCIAL_VERSIONS.md` 矩阵需逐项核实；docs 内部多份评审文档结论互相矛盾 | 发布前统一收敛 |
| P1-10 | 生成式注释残留（`MqConsumerService`、`AbstractStorageService` 的 `/** destroy */`、`加载ad` 类坏注释）；`SensitiveDataUtil`/`ExpiringLocalCache` 等稀用工具待裁决 | 代码卫生 |

### 🟢 P2 —— 差异化路线项（沿 COMMERCIAL_OPTIMIZATION_PROPOSAL G10-G16，按需排期）

G10 DB 读降级 / G11 分布式 bulkhead / G12 质量门禁（jacoco 门槛+Checkstyle/SpotBugs）/ G13 工作流引擎（Flowable，决策点）/ G14 官网+在线 Demo / G15 信创数据库（达梦/金仓）/ G16 License 在线激活与机器指纹。

---

## 4. 执行方案（确认后实施）

### 阶段一：P0 清障（预计 5~7 个工作日）
1. **仓库卫生**（0.5 天）：`git rm --cached backups/ lr`，backups/ 入 .gitignore；审计打包链路确认 private.key 不进任何交付物；核销 .workbuddy。
2. **三档实质化**（2 天）：features.* → `@ConditionalOnProperty` 真实驱动 Bean 装配（RBAC/缓存/MQ/tracing 按档启用）；Maven Profile 做依赖裁剪（basic 不打包 RocketMQ/Zipkin 等）；修正 `COMMERCIAL_VERSIONS.md` 矩阵逐项对齐实现。
3. **License 加固**（2~3 天）：默认启用不可关（enabled 开关仅允许"更严格"）；商业组件统一挂 `@FeatureGate`（或 LicenseCondition 联动 ConditionalOnProperty）；License 运行期定时重检过期；机器指纹绑定（G16 提前）；生成器从发行源码剥离（仅保留内部私仓）；`LICENSE_MANAGEMENT.md` 修正措辞。
4. **微信支付处置**（0.5 天）：实现验签或整体移除微信支付模块（建议首发移除，作为后续版本增值）。
5. **版本收口**（0.5 天）：提交当前 57 文件变更、pom/CHANGELOG 版本对齐 1.2.0、历史任务清单状态校准。

### 阶段二：P1 质量收敛（预计 5~8 个工作日，可与售卖准备并行）
按 P1-1~P1-10 顺序处理；重点：错误码统一、多租户定位决策（补齐 or 改宣传）、new-project.sh Linux 兼容、admin 域补横切注解真实使用点、文档收敛为单一事实来源（过期评审文档归档）。

### 阶段三：P2 差异化（按销售线索驱动排期）
沿用 G10-G16 路线图；其中 G14（官网/Demo）与 G12（质量门禁）建议优先，直接服务转化与交付可信度。

---

## 5. 待确认决策点

1. **多租户**：补齐数据隔离（约 5 天）进入 Enterprise 卖点，还是维持现状并把 README 的 "SaaS 平台" 场景表述降级？（建议：补齐，SaaS 是核心买点）
2. **微信支付**：修复验签保留，还是首发移除？（建议：移除）
3. **三档定价策略**：保持 ¥9,999/¥49,999，还是基于"标准版含 RBAC/缓存"的实际差异重新定价？
4. **License 强度**：接受"源码交付 + 君子协定"现实（企业客户场景下法律合同约束为主，技术防护适度即可），还是投入做代码混淆/在线激活（G16 全量）？（建议：阶段一做中等加固，在线激活按需）
5. **工作流引擎 G13**：是否纳入 Enterprise 档路线？

---

**评审人**: ZCode（代码级四路并行审查）
**下一步**: 确认本方案与第 5 节决策点后，按阶段一开始执行

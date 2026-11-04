# 工作日报（2026-10-27）

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

**汇报人：** 后端开发  
**项目：** 冷链管理系统 V2.0  
**日期：** 2026 年 10 月 27 日

## 一、今日工作概览

今日围绕冷链管理系统 V2.0 的工程收口与质量验证两条线推进。工程侧完成基础设施模块的物理边界收敛、自助密码找回链路落地、数据脱敏与响应式操作审计补强、五个业务域 Seata undo_log 迁移与 V63–V65 权限种子登记，以及虚拟线程有界执行器实现；质量侧完成升级 JDK 21 与 MySQL 9.0 后的全量回归门禁、Seata 四模式真实环境专项验证，以及阻塞 I/O 虚拟线程压测评估。

## 二、主要工作及完成情况

### 1. 完成基础设施模块物理边界收敛

- 清理 `apex-infrastructure` 下 223 个残留实现与测试文件，实施源码已全部迁至 `apex-infrastructure-persistence`、`-cache`、`-messaging`、`-remote`、`-integration` 五个能力 JAR，旧坐标模块仅保留兼容定位，`apex-infrastructure/src` 当前为空目录。
- 同步清理临时修复脚本与废弃工具：`.fix_build.py`、`.fix_mono.py`、`scripts/utils/local_deployment.py`、`scripts/ci/java*`，以及 `scripts/vue-components-add-comments.sh` 中约 720 行历史逻辑。
- 删除 `.zcode/plans` 等一次性规划产物，仓库变更总量收敛为 1030 个文件（新增 251、删除 248、修改 796），净删除 43198 行。

### 2. 完成自助密码找回链路开发

- 在 auth 域按 DDD 分层新增完整链路：接口层 `PasswordRecoveryRouter` / `PasswordRecoveryHandler`，应用层 `PasswordRecoveryApplicationService` 与 6 个 DTO，领域层 `PasswordRecoveryEntity`、`PasswordRecoveryRepository`、`PasswordRecoveryAccountDirectory` 端口，基础设施层 Mapper / PO / 仓储实现。
- 采用 WebFlux 函数式 Router，注册三个匿名端点：`POST /auth/v2/password-recovery/requests`（发起申请）、`/verifications`（校验一次性验证码）、`/confirmations`（确认重设密码）；路径已在 `AuthCenterConfig` 精确匿名白名单中放行。
- 安全语义已落实：发起申请对账号是否存在保持同一响应形态，避免账号枚举；验证码校验失败累计次数，达到上限后恢复单作废；确认环节原子消费一次性令牌，执行密码策略后重设密码并撤销全部会话，撤销失败返回 `PENDING`。
- 配套 MySQL / PostgreSQL 双库迁移 `V59__add_auth_password_recovery.sql`，并补充 `PasswordRecoveryApplicationServiceTest` 单元测试。

### 3. 完成数据脱敏与响应式操作审计补强

- 新增 `DataMaskSerializer` 与 `JsonFieldMasker`，把脱敏能力从 `apex-common` 注解与工具下沉到序列化层，支持 JSON 字段级掩码；`apex-infrastructure` 中原 `SensitiveDataMasker` 同步下线，脱敏职责统一收口。
- 新增 `ReactiveOperationLogAspect` 与 `OperationAuditAutoConfiguration`，为 auth、file、gateway、masking 四个独立服务补齐响应式 `@OperationLog` 审计消费，仅输出白名单元数据，不采集身份、正文、地址与请求头，并对日志设施异常做隔离。
- 新增 `DataMaskJacksonTest`、`DataMaskSafetyTest`、`JsonFieldMaskerTest`、`OperationAuditWiringTest` 覆盖序列化脱敏与审计装配。

### 4. 完成五域 Seata undo_log 迁移与权限种子登记

- 为 Seata AT 模式在各业务库补齐 undo_log 表：system `V61`、auth `V57`、file `V3`、office `V3`、workflow `V2`，MySQL / PostgreSQL 全部配对维护。
- 补齐 auth 域 `V58__create_reactive_mfa_config.sql`，以及 system `V62__align_system_database_schema.sql`：在持久 DDL 前用临时表做 NULL 存量预检，再恢复 V40 遗失的 `NOT NULL` 约束、对齐 `off_sync_history.update_time` 并补建 4 个缺失索引。
- 新增三个权限种子迁移（MySQL / PostgreSQL 双份）：`V63` 租户管理 4 个权限码（`tenant:read/create/lifecycle/quota`，ID 2601–2604）、`V64` 流程定义 4 个权限码（`workflow:definition:read/edit/publish/lifecycle`，ID 2605–2608）、`V65` 站内信同步向导 2 个权限码（`office:sync:read/execute`，ID 2609–2610），并为 `ADMIN` 角色授权。
- 同步维护 zh_CN、zh_TW、en_US、th_TH 四套 i18n 资源。

### 5. 完成虚拟线程有界执行器实现

- 新增 `VirtualThreadBoundedExecutor`：以 `Semaphore` 作为并发闸门替代线程池上限，`maxConcurrency + queueCapacity` 组成有界准入并统计拒绝数，单条守护计时线程负责超时调度，`GatedScheduledFuture` 代理计时与执行结果。
- 配套 `ApexBlockingIoProperties` 与 `BlockingIoSchedulers` 扩展，使各阻塞 I/O 池可按 `apex.blocking-io.<pool>.virtual-threads` 独立启用，默认仍关闭。
- 提供 `maxConcurrency`、`queueCapacity`、`activeTasks`、`pendingTasks`、`completedTasks`、`rejectedTasks` 等观测口径，并补充 `-Djdk.tracePinnedThreads=full` 的 pinning 排查说明与单元测试。

### 6. 完成全量回归门禁与 Seata 四模式专项验证

- 按固定构建环境使用 Temurin JDK 21.0.13、mvnd 2.0.0-rc-3、MySQL 9.0.1 执行 `mvnd -T4 -B clean verify`，31 个聚合模块全部 `BUILD SUCCESS`，耗时 20:07。
- Surefire 5057 项、Failsafe 389 项，失败 0 项、错误 0 项，跳过项均为仓库既有显式跳过；Spotless、PMD/p3c、JaCoCo 随 verify 一并通过。
- 在真实 Seata Server 2.2.0 + MySQL 9.0 双独立库环境完成 AT/TCC/SAGA/XA 四模式验证：8 项集成测试 + 21 项单元测试全部通过，分模式 AT 1/1、TCC 4/4、SAGA 1/1、XA 2/2。
- 测试编排统一为 JUnit 扩展 `SeataRealCluster`，并修复两处真实环境问题：TC 就绪判定改为等待日志出现 `seata server started` 后再探测端口；MySQL 9.0 的 `XA RECOVER` 验证补充 `XA_RECOVER_ADMIN` 最小权限。

### 7. 完成阻塞 I/O 虚拟线程性能对比评估

- 针对 `GET /api/v1/health`，对 `event`、`cache` 池分别做平台线程与虚拟线程对比，使用 wrk 4.2、4 线程、30 秒多轮压测，通过 Prometheus 与 JFR 采集指标。
- 首轮 `-c200` 因普遍 socket timeout 不可用，降为 `-c32` 与 `-c64` 重测，最终取得 5 个有效 c=64 样本（另剔除 1 个与构建并发污染的样本）。
- 结果：逐轮配对吞吐变化 +12.9%、+18.2%、+29.9%、-6.2%、+47.7%，中位 **+18.2%**；按中位值汇总吞吐 187.27 → 211.43 req/s（+12.9%），平均延迟 373.69 → 337.17 ms（-9.8%），p99 1320 → 1080 ms（-18.2%）。
- 结论定位为方向性正收益：两侧轮间波动与配对差异同量级，样本量不足以支持修改默认线程配置，当前维持平台线程默认，虚拟线程按池显式开启。

## 三、今日成果数据

| 类别 | 完成情况 |
| --- | --- |
| 变更规模 | 1030 个文件，新增 251 / 删除 248 / 修改 796，净删除 43198 行 |
| 模块边界收敛 | `apex-infrastructure` 223 个残留文件清理完毕，`src` 已空 |
| 新增能力 | 密码找回三端点全链路、JSON 字段脱敏、四服务响应式审计、虚拟线程有界执行器 |
| 数据库迁移 | 5 域补齐 Seata undo_log；system V61–V65、auth V57–V59 等双库配对 |
| 权限种子 | 10 个新权限码（ID 2601–2610），四语言 i18n 同步 |
| 回归门禁 | 31 个聚合模块，Surefire 5057 + Failsafe 389，0 失败、0 错误，20:07 |
| 质量门禁 | Spotless、PMD/p3c、ArchUnit、JaCoCo 全部通过 |
| Seata 真实验证 | 4 种模式，8 项集成 + 21 项单元，全部通过 |
| 性能评估 | 5 个有效 c=64 样本，配对吞吐中位 +18.2%（中位值汇总 +12.9%），结论为方向性收益 |

## 四、风险与限制

1. Seata 已验证真实 TC、双 MySQL 库与四种事务模式，但 AT/TCC 分支服务及 SAGA 引擎仍运行在测试 JVM 内，不等同于五个业务服务全部容器化启动后的完整链路验证；TC DB 存储、多 TC 集群、故障切换与 PostgreSQL 下的 Seata 路径尚未覆盖。
2. 虚拟线程压测仅覆盖单机短阻塞健康检查接口，c=64 有效样本 5 轮（其中 1 轮负向），基线自身极差达 42.5%，不能直接外推到慢 SQL、同步远程调用或生产峰值流量。
3. 本轮变更体量大（1030 个文件）且含跨域迁移脚本，需按模块边界、配置注入与部署链路做最终差异审查；当前工作区改动尚未提交，也未做服务容器化启动验证。
4. 密码找回为新增匿名端点，账号枚举防护、验证码次数上限与会话撤销失败返回 `PENDING` 的分支需在集成环境补充端到端验证。

## 五、明日计划

- 完成本轮 1030 个文件变更的分域复核，按模块整理可提交范围，确认无遗漏配置、迁移脚本与运行时依赖。
- 在容器化环境启动五个业务服务，验证密码找回三端点、响应式操作审计与脱敏序列化在真实链路上的行为。
- 补充慢 SQL、同步远程调用等典型阻塞场景的虚拟线程对比，增加预热轮与至少 10 轮有效样本，并用 `-Djdk.tracePinnedThreads=full` 检查 pinning。
- 更新测试报告与运行时基线建议，输出上线前验证清单。

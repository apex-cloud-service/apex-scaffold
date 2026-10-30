# 质量与验证计划

> **版本**: v1.0
> **核对日期**: 2026-10-30
> **基线提交**: `b8e5733`
> **维护者**: Apex Team

本文档定义交付质量的判定方式：用哪些门禁、覆盖什么范围、以什么证据判定通过。**门禁通过不等于运行验收通过**，两类证据分别登记。

---

## 1. 质量目标

| 维度 | 目标 | 判定方式 |
| --- | --- | --- |
| 构建一致性 | 全反应堆 `BUILD SUCCESS` | `mvnd -T4 -B clean verify` 退出码 |
| 单元与集成测试 | `Failures: 0 / Errors: 0` | Surefire / Failsafe 报告 |
| 代码格式 | 零违规 | `spotless:check` |
| 静态规则 | 零阻断违规 | `pmd:check`（p3c 规则集） |
| 覆盖率 | 行 ≥ 60%、分支 ≥ 50%（父 POM 默认） | `jacoco:check` |
| 分层与边界 | 架构测试通过 | ArchUnit 测试类 |
| 数据库迁移 | MySQL / PostgreSQL 版本集合一致 | `FlywayMigrationVersionTest` |
| 接口文档一致性 | 路由与 OpenAPI 声明一致 | `RouterOpenApiCoverageTest` |
| 文档链接 | 零断链 | `scripts/ci/check-doc-links.sh` |

> 覆盖率阈值、排除项与模块生效范围以父 POM 的有效配置和实际报告为准，不以文档描述代替。

---

## 2. 构建环境要求

| 项 | 要求 | 依据 |
| --- | --- | --- |
| JDK | SDKMAN `21.0.13.0+9-tem` | [.sdkmanrc](../../../.sdkmanrc) |
| 构建工具 | Maven Daemon `2.0.0-rc-3` | 同上 |
| 本地仓库 | `~/.m2/repository` | 仓库约定 |
| 环境激活 | 先 `source scripts/build/init-build-env.sh` 或 `sdk env` | 同上 |

> 不得使用 `~/.sdkman/candidates/java/current`，该链接可能漂移到不兼容 JDK。不得回退到系统 `mvn`。

---

## 3. 分层门禁

### 3.1 快速验证（提交前）

| 命令 | 范围 | 用途 |
| --- | --- | --- |
| `mvnd -T4 -B -pl <模块> -am test` | 受影响模块及其依赖 | 快速确认改动未破坏上游 |
| `mvnd -T4 -B spotless:check` | 全仓 | 格式检查 |
| `git diff --check` | 变更文件 | 空白错误 |

### 3.2 完整门禁（发布前）

```bash
source scripts/build/init-build-env.sh
mvnd -T4 -B clean verify
```

绑定在 `verify` 阶段的检查：Surefire、Failsafe（`*IntegrationTest`、`*E2ETest`、`FlywayMigrationStartupTest`）、Spotless、PMD/p3c、JaCoCo。

**通过标准**：反应堆全部模块 `BUILD SUCCESS`，测试 `Failures: 0 / Errors: 0`。

### 3.3 文档门禁

| 命令 | 校验内容 |
| --- | --- |
| `bash scripts/ci/check-doc-links.sh` | 全仓 Markdown 本地路径、大小写、锚点 |
| `bash scripts/ci/check-doc-consistency.sh` | 版本号一致性、环境变量文档覆盖、Handler Javadoc、路由文档存在性、Flyway 命名规范 |
| `bash scripts/ci/check-springdoc-coverage.sh` | SpringDoc 注解覆盖率阈值 |

> 链接检查依赖 `apex-tools-deployment/target/local-deployment.jar`；JAR 缺失时脚本会自动构建。

---

## 4. 已有架构检查的实际覆盖

| 检查 | 模块 | 覆盖内容 | 不覆盖 |
| --- | --- | --- | --- |
| `LayeringArchTest` | apex-system | domain/application/infrastructure 分层；部分 Query/Statistics/LogService 例外 | 其他业务库 |
| `TenantIsolationArchTest` | apex-infrastructure-persistence | persistence 范围 `@InterceptorIgnore` 禁用 | 业务表字段完整性 |
| `TimeFieldArchTest` | apex-infrastructure-persistence | 指定命名时间字段类型 | 全部时间字段 |
| `GatewayArchTest` | apex-gateway | 网关包边界、业务层与 Servlet 依赖 | 路由正确性 |
| `NoBoundedElasticArchGuardTest` | apex-system | 指定目录直接使用 `boundedElastic` | 全仓调度器 |
| `SystemServiceBoundaryTest` | apex-system-service | 扫描声明与类可加载性 | 实际服务启动 |
| `FlywayMigrationVersionTest` | apex-system | MySQL/PostgreSQL 版本集合一致与连续性 | SQL 语义等价 |
| `PersistenceContractTest` | apex-auth | auth domain 与持久化边界 | 业务逻辑 |

> 这些检查只覆盖其声明范围。业务变更还需验证实际调用链、失败路径、事务回滚与权限行为。

---

## 5. 测试资产现状

| 模块 | 测试类数量 | 说明 |
| --- | --- | --- |
| `apex-system` | 244 | 主要业务域 |
| `apex-auth` | 61 | 认证域 |
| `apex-common` | 52 | 共享工具与注解 |
| `apex-office-service` | 39 | 办公服务与迁移 |
| `apex-auth-service` | 24 | 认证服务与集成测试 |
| `apex-infrastructure-messaging` | 22 | 消息与 Outbox |
| `apex-infrastructure-cache` | 18 | 缓存 |
| `apex-gateway` | 18 | 路由与熔断 |
| `apex-workflow-service` | 17 | 工作流服务 |
| `apex-infrastructure-integration` | 15 | 第三方集成 |
| `apex-infrastructure-persistence` | 14 | 持久化与数据权限 |
| `apex-office` | 13 | 办公域 |
| `apex-file` | 9 | 文件域 |
| `apex-infrastructure-remote` | 8 | 远程调用 |
| `apex-tools-deployment` | 8 | 运维工具 |
| `apex-file-service` | 7 | 文件服务 |
| `apex-api-contract` | 6 | 跨服务契约 |
| `apex-workflow` | 5 | 工作流域 |
| `apex-system-service` | 2 | 启动装配 |
| `apex-domain` | 1 | 基础实体 |
| `apex-example` | 4 | 示例与能力集成 |
| `apex-generator` | 1 | 代码生成 |
| `apex-seata-spring-boot-starter` | 7 | 分布式事务模式 |
| `apex-job-spring-boot-starter` | 3 | 任务执行器 |
| `apex-masking-service` | 3 | 脱敏服务 |
| `apex-job-service` | 2 | 任务代理 |
| `apex-license-spring-boot-starter` | 2 | 授权校验 |
| `apex-storage-spring-boot-starter` | 2 | 存储策略 |
| `apex-license-tools`、`apex-infrastructure` | 0 | 无测试 |

**合计**: 607 个测试类（2026-10-30 统计）。

> 数量为 `src/test` 下 `*Test.java` 文件数（2026-10-30 统计），不等于用例数，也不代表全部通过。通过与否以最近一次 `verify` 的实际报告为准。

---

## 6. 已归档的验证证据

| 证据 | 位置 | 结论 | 时效 |
| --- | --- | --- | --- |
| 完整回归门禁 | [虚拟线程压测报告](../../06-testing/virtual-thread-bench/20261027/report.md) | `mvnd -T4 -B clean verify` BUILD SUCCESS，31 模块，Surefire 5057 / Failsafe 389，`Failures: 0 / Errors: 0` | 2026-10-27，早于 `b8e5733` |
| 各模块测试报告 | [TEST_REPORT.md](../../06-testing/TEST_REPORT.md)、[gateway](../../06-testing/gateway-module-test-report.md)、[workflow](../../06-testing/workflow-module-test-report.md)、[contract](../../06-testing/contract-module-test-report.md)、[auth-center JWT/JWKS](../../06-testing/auth-center-jwt-jwks-test-report.md) | 模块级结论，范围见各报告 | 2026-10-29 前后 |
| 微服务验收 | [microservice-acceptance-checklist.md](../checklists/microservice-acceptance-checklist.md) | 离线项已满足，在线项待环境 | 2026-10-12 |
| Python→Java 工具迁移 | [迁移总结](../../06-testing/python-to-java-tools-migration-summary-20261021.md) | 链接检查器已 Java 化 | 2026-10-21 |

> **时效声明**：上述证据均在 `b8e5733` 之前或同期产出。工作区当前存在未提交的 V70～V72 迁移与代码改动（见 [风险登记册](./risk-register.md) R-01），这些改动**没有**对应的完整门禁记录。发布前必须重跑完整门禁。

---

## 7. 本次变更的验证范围

本次仅新增文档，未修改代码、配置或迁移。实际执行的检查：

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| 文档链接与锚点 | `bash scripts/ci/check-doc-links.sh` | 建立文档集后重跑，见文末记录 |
| 空白错误 | `git diff --check` | 通过 |
| 事实抽样复核 | 人工比对源码/配置 | 见各文档“证据”列 |

**未执行**：Java 编译、单元测试、Failsafe、Spotless、PMD、JaCoCo。本次不宣称代码回归通过。

---

## 8. 发布前门禁清单

发布候选版本必须逐项完成，缺一不可：

- [ ] `mvnd -T4 -B clean verify` 全反应堆通过（含未提交改动提交后）
- [ ] Spotless / PMD / JaCoCo 报告无阻断项
- [ ] MySQL 与 PostgreSQL 迁移在真实库执行成功
- [ ] 三档构建产物均可启动
- [ ] 服务间路由与鉴权联调通过
- [ ] 许可与授权路径验证（有效 License 放行、无效降级）
- [ ] 文档链接检查通过
- [ ] 交付物完整性校验（见 [交付物清单](./deliverables.md) §5）全部通过

---

## 9. 例外与豁免

| 场景 | 处理 | 批准 |
| --- | --- | --- |
| 真实 Redis/HTTPS 计时敏感测试偶发超时 | 分析日志后重跑或降低并行度，不放宽 SLA 断言 | 技术负责人 |
| 缺少 TC/双库导致 Seata 端到端测试跳过 | 单独披露为“未验证”，不计为通过 | 技术负责人 |
| 云桶测试缺少凭据 | 单独披露，不计为通过 | 技术负责人 |
| 覆盖率阈值调整 | 需说明理由并在本文件登记 | 架构负责人 |

---

**维护者**: Apex Team
**下次核对**: 每个发布候选版本

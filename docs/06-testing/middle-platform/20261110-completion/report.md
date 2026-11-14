# 中台架构整改完成测试报告（2026-11-10）

## 1. 结论

- 当前项目的中台主干已具备代码与静态契约层实现：35 个 Reactor 模块、网关 + 六个业务服务、八项能力目录、能力订阅门禁、服务侧二次校验、租户隔离、DDD 分层、双数据库 Flyway 与能力计量入口均有对应实现。
- 本轮完成中台评审报告所列 G1–G8 的代码与静态契约整改，详见[中台架构评审报告](../../../08-project-management/reports/middle-platform-architecture-review.md)第 9 节。
- 完整 `bash scripts/test/verify-middle-platform.sh --full` 已执行成功（脚本内部执行 `mvnd -T4 -B clean verify`）：35 个聚合模块 `BUILD SUCCESS`，Surefire 5,860 个测试、Failsafe 391 个测试，合计 6,251 个测试、0 失败、0 错误、33 个跳过，耗时 17 分 55 秒，完成时间 `2026-11-10T21:34:53+08:00`，详见第 6 节。
- **本报告仍不构成“完整中台生产验收通过”结论。** 真实 Nacos/MySQL/Redis、RocketMQ/EMQX、Seata TC、告警触达、备份恢复、真实第三方消费方与运营台账仍未验证；33 个跳过项也没有执行。
- 关闭口径：**中台主干代码、静态契约与完整 `clean verify` 已通过；完整中台生产验收未完成。** 静态路由、配置、架构契约测试与本地/容器化门禁不能替代部署环境中的端到端调用、故障注入和性能验收。

## 2. 中台架构判定

| 维度 | 当前判断 | 证据或限制 |
| --- | --- | --- |
| 能力目录 | 已实现 | `CapabilityCatalog` 登记八项能力，含 owner、版本、消费方、SLO、生命周期、开放 scope 与计量维度 |
| 多业务复用 | 已形成共享链路 | 网关、六个业务库/运行模块复用 common、domain、api-contract 与五项共享设施；真实外部消费方未取证 |
| 领域边界 | 代码边界已建立 | DDD 分层、system 专属代码在业务库、运行模块只装配；由 ArchUnit 与持久化契约测试部分约束 |
| 技术底座 | 已实现 | WebFlux、响应式/阻塞 I/O 隔离、MyBatis-Plus/R2DBC、Redis、MQ、对象存储、Seata 增强与统一配置 |
| 数据与权限 | 已实现主干 | 租户上下文、行级租户拦截、套餐能力、scope + capability 双重门禁、双数据库迁移；真实多租户数据隔离需环境验证 |
| 可观测与稳定性 | 部分实现 | 健康、指标、链路与日志入口存在；告警实际触达、容灾与备份恢复未验证 |
| 交付质量 | 代码/契约与完整门禁已闭环 | Maven 模块边界、构建注册表、架构守卫、Flyway 版本一致性；完整 `clean verify` 通过，真实环境验收另列 |
| 运营治理 | 字段与接口已补齐 | 能力 owner/SLO/生命周期已有登记；实际运营责任台账与下线流程未取得生产证据 |

## 3. 本轮整改范围

- G1：服务侧开放能力二次校验默认开启。
- G2/G3：auth 域分层倒置与演示残留收敛，权限查询经领域端口访问，缓存实现移入 infrastructure。
- G4：能力目录补齐治理字段，并由文档契约测试对齐。
- G5：网关与服务侧复用统一快照模型、解析与 TTL 缓存。
- G6：明确 `unbounded=true` 才表示全量订阅；空 `features` 不再隐式全量。
- G7：扩大架构守卫覆盖并明确边界测试的真实能力。
- G8：修复依赖表漂移，加入构建注册表一致性校验。
- 配套：开放 Router 路由契约、能力计量路径前缀、平台全量套餐 V90 回填迁移、固定环境验证脚本与文档。

## 4. 本轮改动文件

### 4.1 Java 实现与测试

| 文件 | 目的 |
| --- | --- |
| `apex-common/src/main/java/com/apex/scaffold/common/capability/TenantCapabilitySnapshot.java` | 统一能力快照模型与解析 |
| `apex-common/src/main/java/com/apex/scaffold/common/capability/TenantCapabilitySnapshotCache.java` | 统一 TTL 快照缓存 |
| `apex-common/src/main/java/com/apex/scaffold/common/capability/CapabilityGate.java` | 收紧空订阅语义 |
| `apex-common/src/main/java/com/apex/scaffold/common/openapi/OpenApiGuardProperties.java` | 服务侧门禁默认开启 |
| `apex-gateway/src/main/java/com/apex/scaffold/gateway/capability/TenantCapabilityClient.java` | 复用共享快照实现 |
| `apex-auth/src/main/java/com/apex/scaffold/auth/domain/repository/AuthorizationQueryPort.java` | 领域端口 |
| `apex-auth/src/main/java/com/apex/scaffold/auth/domain/service/ResourcePermissionStore.java` | 领域权限存储契约 |
| `apex-auth/src/main/java/com/apex/scaffold/auth/infrastructure/security/ResourcePermissionStoreImpl.java` | 租户维度缓存实现 |
| `apex-system/src/main/java/com/apex/scaffold/application/capability/CapabilityCatalog.java` | 八项能力治理元数据 |
| `apex-system/src/main/java/com/apex/scaffold/application/service/TenantPackageService.java` | 能力集合与全量语义分离 |
| `apex-system/src/main/java/com/apex/scaffold/infrastructure/persistence/repository/TenantPackageRepositoryImpl.java` | `features` 损坏/空值防御 |
| `apex-system/src/main/java/com/apex/scaffold/application/config/CapabilityUsageProperties.java` | 开放路径计量前缀 |
| `apex-file-service/src/main/java/com/apex/scaffold/fileservice/interfaces/router/OpenFileRouter.java` | 开放文件路由契约与注释 |
| `apex-workflow-service/src/main/java/com/apex/scaffold/workflowservice/interfaces/router/OpenWorkflowRouter.java` | 开放工作流路由契约与注释 |
| `apex-office-service/src/main/java/com/apex/scaffold/officeservice/interfaces/router/OpenMessageRouter.java` | 开放站内信路由契约与注释 |
| `apex-gateway/src/test/java/com/apex/scaffold/gateway/GatewayRouteProfileConfigTest.java` | 四个开放 Router 的网关路由/profile 静态契约 |
| `apex-common/src/test/java/com/apex/scaffold/common/capability/CapabilityGateTest.java` | 三种订阅语义测试 |
| `apex-gateway/src/test/java/com/apex/scaffold/gateway/capability/TenantCapabilityConsistencyTest.java` | 两端同快照判定一致性 |
| `apex-auth/src/test/java/com/apex/scaffold/auth/infrastructure/persistence/PersistenceContractTest.java` | domain 禁止反向依赖 application/Redis |
| `apex-system/src/test/java/com/apex/scaffold/arch/ContextBoundaryArchTest.java` | 六域上下文边界守卫 |
| `apex-system/src/test/java/com/apex/scaffold/application/capability/CapabilityCatalogTest.java` | 治理字段与生命周期校验 |
| `apex-system/src/test/java/com/apex/scaffold/application/capability/CapabilityCatalogDocContractTest.java` | 代码目录与文档表格一致 |
| `apex-system/src/test/java/com/apex/scaffold/application/config/CapabilityUsagePropertiesTest.java` | 无尾斜杠路径计量 |
| `apex-system/src/test/java/com/apex/scaffold/interfaces/test/FlywayMigrationVersionTest.java` | 双库版本集合与 V90 连续性 |
| `apex-system/src/test/java/com/apex/scaffold/interfaces/test/SystemDualDatabaseMigrationIntegrationTest.java` | V90 双库回填语义（受 Testcontainers 环境约束） |
| `apex-tools-build/src/test/java/com/apex/scaffold/tools/build/BuildManifestSyncTest.java` | POM、模块注册表、打包入口与文档一致 |

### 4.2 迁移、脚本与文档

| 文件 | 目的 |
| --- | --- |
| `apex-system/src/main/resources/db/migration/mysql/V90__backfill_platform_full_capability_features.sql` | 回填 `platform-full` 空能力集合 |
| `apex-system/src/main/resources/db/migration/postgresql/V90__backfill_platform_full_capability_features.sql` | PostgreSQL 等价回填 |
| `scripts/test/verify-middle-platform.sh` | 中台固定环境验证入口 |
| `docs/02-architecture/capability-catalog.md` | 能力目录代码/文档契约 |
| `docs/03-api/open-platform-integration.md` | 四个开放 Router 的实施状态 |
| `README.md`、`docs/README.md`、`docs/08-project-management/README.md` | 入口与索引同步 |

## 5. 验证命令与结果

### 5.1 定向测试

实际执行：

```bash
source scripts/build/init-build-env.sh
mvnd -T4 -B \
  -pl :apex-common,:apex-gateway,:apex-system,:apex-system-service,:apex-file-service,:apex-workflow-service,:apex-office-service \
  -am test
```

结果记录位置：`/tmp/apex-middle-platform-targeted-test-20261110.log`。

**结果：BUILD SUCCESS。** 反应堆结果与测试计数见 §5.3；该命令执行 Surefire 单元/架构测试，不执行 Failsafe 集成测试与 verify 质量门禁。

### 5.2 静态检查

```bash
git diff --check
# 对本轮修改和新增的全部 12 个 Bash 脚本逐一执行 bash -n
bash scripts/ci/check-doc-links.sh
bash scripts/test/test-check-javadoc-comments.sh
bash scripts/scan-javadoc-issues.sh
```

实际结果：

- `git diff --check`：通过，无空白错误。
- `bash -n`：本轮修改和新增的 12 个 Bash 脚本逐一检查通过，未发现语法错误。
- `bash scripts/ci/check-doc-links.sh`：通过；扫描 Markdown 745 个、HTML 17 个、本地链接 3,784 条，断裂链接 0 条。
- `bash scripts/test/test-check-javadoc-comments.sh`：输出 `PASS`，检查器能排除注释示例并识别真实缺失文档。
- `bash scripts/scan-javadoc-issues.sh`：扫描结果为缺少类级 JavaDoc 0 个、缺少 `@author` 0 个、缺少 `@since` 0 个、可能缺少方法级 JavaDoc 0 个，合计 0 个问题项。

### 5.3 测试计数

**定向测试（非完整门禁）**：本次命令共构建 21 个 Reactor 模块，按 Maven 各模块最终 `Tests run` 汇总为
4,094 个测试、0 失败、0 错误、10 跳过。各模块计数如下：

| 模块 | tests | failures | errors | skipped |
| --- | ---: | ---: | ---: | ---: |
| apex-api-contract | 19 | 0 | 0 | 0 |
| apex-common | 593 | 0 | 0 | 0 |
| apex-domain | 9 | 0 | 0 | 0 |
| apex-file | 84 | 0 | 0 | 0 |
| apex-file-service | 34 | 0 | 0 | 0 |
| apex-gateway | 166 | 0 | 0 | 0 |
| apex-infrastructure-cache | 135 | 0 | 0 | 0 |
| apex-infrastructure-integration | 124 | 0 | 0 | 0 |
| apex-infrastructure-messaging | 130 | 0 | 0 | 10 |
| apex-infrastructure-persistence | 83 | 0 | 0 | 0 |
| apex-infrastructure-remote | 84 | 0 | 0 | 0 |
| apex-job-spring-boot-starter | 11 | 0 | 0 | 0 |
| apex-license-spring-boot-starter | 19 | 0 | 0 | 0 |
| apex-office | 140 | 0 | 0 | 0 |
| apex-office-service | 401 | 0 | 0 | 0 |
| apex-seata-spring-boot-starter | 28 | 0 | 0 | 0 |
| apex-storage-spring-boot-starter | 15 | 0 | 0 | 0 |
| apex-system | 1,765 | 0 | 0 | 0 |
| apex-system-service | 4 | 0 | 0 | 0 |
| apex-workflow | 169 | 0 | 0 | 0 |
| apex-workflow-service | 81 | 0 | 0 | 0 |
| **合计** | **4,094** | **0** | **0** | **10** |

10 个跳过项全部来自 `apex-infrastructure-messaging`，原因是缺少环境变量 `OUTBOX_DUAL_DB`，相关
Outbox 双库方言用例没有执行，不得视为通过。该定向命令不运行 Failsafe，故不产生集成测试结论。

| 项目 | 结果 |
| --- | --- |
| 定向 Reactor | BUILD SUCCESS（21 个模块） |
| 定向 Surefire tests / failures / errors / skipped | 4,094 / 0 / 0 / 10 |
| 定向 Failsafe | 未执行（`test` 生命周期不绑定 `verify`） |
| 完整 `clean verify` | BUILD SUCCESS（见 §6） |
| Testcontainers 双库迁移 | 已执行并通过（见 §6） |

### 5.4 静态契约与真实 E2E 的区别

- `GatewayRouteProfileConfigTest` 校验 YAML 路由、Path predicate、scope 与 capability 元数据的静态一致性，不发送真实 HTTP 请求。
- `CapabilityCatalogDocContractTest` 校验代码能力表与 Markdown 表格一致，不验证运营台账真实性。
- `CapabilityUsagePropertiesTest` 校验 URI 前缀映射，不证明生产访问日志已按该前缀落库。
- `ContextBoundaryArchTest`、`PersistenceContractTest` 校验源码依赖边界，不启动完整服务。
- `SystemDualDatabaseMigrationIntegrationTest` 在本轮完整门禁中实际启动了 MySQL/PostgreSQL 容器，2 个用例通过、0 跳过，验证的是双数据库迁移语义，不是生产数据库、Nacos、MQ 或 Seata 环境的端到端验收。
- `GatewayMetricsConfigContractTest` 只解析 `application.yml`，验证 Reactor Netty 指标开关与 Prometheus Actuator 暴露声明；不启动网关，也不证明真实 `/actuator/prometheus` 已返回指标。
- `scripts/test/verify-monitoring-config.sh` 只做 Prometheus、Alertmanager、Grafana 与 Compose 配置静态检查；本机缺少 `promtool` 与 `amtool` 时返回 `3`（2 项 SKIP），不能记为官方工具校验或容器化运行验收通过。

### 5.5 收尾补充验证

以下验证在本报告主体统计完成后补跑，用于覆盖租户套餐 fail-closed 修复和运维脚本收尾，不改变第 8 节的生产验收结论：

- 租户套餐定向测试：实际执行 `mvnd -T4 -B -pl :apex-system -am -Dtest=TenantPackageServiceTest,TenantPackageRepositoryImplTest -Dsurefire.failIfNoSpecifiedTests=false test`，反应堆 12 个模块 `BUILD SUCCESS`；`:apex-system` 共 17 个测试、0 失败、0 错误、0 跳过，完成时间 `2026-11-10T21:10:56+08:00`，日志 `/tmp/:apex-tenant-package-test-20261110.log`。
- Javadoc 补强后的定向回归：实际执行 `mvnd -T4 -B -pl :apex-auth,:apex-system -am -Dtest=ResourcePermissionServiceTest,FlywayMigrationVersionTest -Dsurefire.failIfNoSpecifiedTests=false test`，13 个 Reactor 模块 `BUILD SUCCESS`；`ResourcePermissionServiceTest` 13 项、`FlywayMigrationVersionTest` 6 项，合计 19 个测试、0 失败、0 错误、0 跳过，完成时间 `2026-11-10T22:16:44+08:00`。
- Bash 语法检查：本轮修改和新增的 12 个脚本全部通过 `bash -n`。
- 文档链接检查：扫描 Markdown 745 个、HTML 17 个、本地链接 3,784 条，断裂 0 条。
- Javadoc 静态检查：`test-check-javadoc-comments.sh` 输出 `PASS`；`scan-javadoc-issues.sh` 报告缺类级 Javadoc、缺 `@author`、缺 `@since`、可能缺方法级 Javadoc 均为 0。
- 监控配置静态验证：`bash scripts/test/verify-monitoring-config.sh` 的 YAML 契约、抓取任务、大盘结构、退役指标和慢 SQL 指标检查全部 PASS；因本机缺少 `promtool`、`amtool`，2 项官方工具检查 SKIP，脚本退出码为 `3`。该结果只能证明静态配置一致性，不能记为官方工具校验或容器化监控运行验收通过。
- 备份/恢复脚本安全参数验证：使用有效 dummy 备份文件避免在参数校验前短路，验证缺少 `--yes`、演练库名不含 `_drill`、演练库等于源库、非法库名、非法端口和旧 `--pass` 参数均以非零状态拒绝；`db-backup.sh`、`db-restore.sh`、`pg-backup.sh`、`pg-restore.sh` 均拒绝 `--pass`，MySQL/PostgreSQL 备份脚本均拒绝权限为 `644` 的密码文件。输出见 `/private/tmp/apex-drill-*-20261110.out` 与 `/private/tmp/apex-backup-*-20261110.out`。该验证只覆盖参数与凭据文件校验，**不是真实 MySQL/PostgreSQL 备份恢复演练**。

## 6. 完整门禁与真实测试结果

### 6.1 完整 `clean verify`

实际执行：

```bash
source scripts/build/init-build-env.sh
bash scripts/test/verify-middle-platform.sh --full
# 脚本内部委托：mvnd -T4 -B clean verify
```

结果：

- **BUILD SUCCESS。** 35 个聚合模块全部成功，构建耗时 17 分 55 秒，完成时间 `2026-11-10T21:34:53+08:00`。
- Surefire：5,860 个测试、0 失败、0 错误、15 跳过。
- Failsafe：391 个测试、0 失败、0 错误、18 跳过。
- 合计：6,251 个测试、0 失败、0 错误、33 跳过。
- 计数采用 Maven 各模块最终 `Tests run` 汇总口径；不从 Surefire XML 文件直接求和，因为模块汇总与 XML 在聚合测试场景下存在差异。
- Spotless、PMD/p3c、JaCoCo 与 Failsafe 均执行完成。首次完整门禁因 6 个文件未通过 Spotless 而失败；执行 `spotless:apply` 仅格式化这 6 个文件后重跑，最终通过。
- 本次完整门禁已覆盖 `GatewayMetricsConfigContractTest` 及 `management.metrics.enable.reactor.netty=true` 配置。
- 定向测试日志：`/tmp/apex-middle-platform-targeted-test-20261110.log`；网关单独回归日志：`/tmp/apex-gateway-test-20261110.log`（168 个测试、0 失败、0 错误、0 跳过，完成时间 `2026-11-10T20:41:38+08:00`）。
- `/tmp/apex-full-verify.log` 是较早一次 `2026-11-10T19:18:46+08:00` 完成的门禁日志（当时为 5,858 / 391 / 6,249），**不作为本次 6,251 项统计的来源**；本次最新门禁由 `verify-middle-platform.sh --full` 会话执行，统计以其 Maven 汇总结果及 `target` 测试报告为准。

### 6.2 跳过项及原因

33 个跳过项均未执行，不能记为通过；其中 Surefire 15 项、Failsafe 18 项：

| 测试范围 | 数量 | 跳过原因 |
| --- | ---: | --- |
| Surefire：`apex-infrastructure-messaging` | 10 | 缺少环境变量 `OUTBOX_DUAL_DB` |
| Surefire：`apex-integration` | 5 | 外部 MQTT broker 或负载环境不可用 |
| Failsafe：`apex-infrastructure-messaging` | 3 | 缺少环境变量 `APEX_REAL_MQ_INFRA_IT` |
| Failsafe：`apex-system` | 5 | 缺少环境变量 `APEX_REAL_MQ_IT` |
| Failsafe：`apex-seata-spring-boot-starter` | 8 | 缺少环境变量 `APEX_SEATA_IT` |
| Failsafe：`apex-auth-service` | 1 | 缺少环境变量 `APEX_AUTH_MYSQL_TLS_IT` |
| Failsafe：`apex-office-service` | 1 | 缺少系统属性 `apex.office.real-db` |
| **合计** | **33** | — |

### 6.3 关键双数据库迁移验证

- `SystemDualDatabaseMigrationIntegrationTest`：2 个测试、0 失败、0 错误、0 跳过，耗时 91.10 秒；实际启动并验证 MySQL 9.0 与 PostgreSQL 15.19 迁移。
- `FlywayMigrationStartupTest`：6 个测试、0 失败、0 错误、0 跳过，耗时 48.47 秒。
- 上述结果证明对应 Flyway 脚本在容器化双数据库环境中可执行，不证明生产数据库、备份恢复、回滚或故障切换已完成验收。

## 7. 未验证项与残余风险

1. 真实 Nacos 配置中心/注册中心、MySQL、Redis 的连接、故障切换与配置下发未验证。
2. RocketMQ/EMQX 的真实消息投递、消费幂等、重试与死信未验证。
3. Seata TC 的 AT/TCC/SAGA/XA 真实全局事务未验证；缺少 TC 或双库时相关测试不能记为通过。
4. 告警通知实际触达、监控看板、日志检索与 SLO 报表未验证。
5. 数据库备份、恢复演练、迁移回滚与容量评估未验证。
6. 真实第三方开放平台消费方、应用注册、令牌获取、限流和审计链路未做端到端验证。
7. 能力 owner/SLO/生命周期目前是目录登记，尚无运营台账、责任人与下线评审证据。
8. 组织层级权限（`checkOrganizationPermission()` 一律 false）是否应实现需业务确认。
9. 已改为 fail-closed：只有平台默认租户显式获得全量范围；非平台租户在套餐未绑定、套餐不存在、套餐停用、JSON 配置损坏或租户不存在时均拒绝全部授权。该结论由 `TenantPackageServiceTest` 与 `TenantPackageRepositoryImplTest` 覆盖，仍不等同于生产租户快照已全部核对。

## 8. 最终判定

**中台主干代码、静态契约与完整 `clean verify`：已通过。完整中台生产验收：未完成。** 后续应在部署环境完成第 7 节所列验证，并对第 6 节列明的 33 个跳过项按对应环境条件补测后，再另行出具生产验收结论。

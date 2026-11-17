# 全仓 Javadoc 评审与补齐战役记录（BE-041）

- 日期：2026-11-18
- 任务：`docs/tasks/project-tasks.md` BE-041（台账 #52）
- 执行：ZCode（用户授权）

## 1. 范围

按 AGENTS.md 0.5.7 / 治理规范 B7 对全仓 `src/main/java`（45 模块、2249 个文件）的代码注释做评审与补齐。只改注释，零逻辑/契约改动；`src/test/java`（780 文件）按扫描器豁免口径与历史战役优先级（历史报告明确列为低价值）不纳入本轮。

## 2. 基线评审结论

两件工具先后审计，结论互补：

| 工具 | 检查项 | 基线结果 |
| --- | --- | --- |
| `scripts/dev/check/scan-javadoc-issues.sh`（注意：11-14 模块物理嵌套后需 `JAVADOC_SCAN_PATH='apex-*/apex-*/src/main/java'` 覆盖默认值，默认路径已失效） | 类级 Javadoc / @author / @since / 方法级候选 | 全部 **0 缺口**（历史战役已收口类级覆盖） |
| JDK javadoc doclint（`mvnd javadoc:javadoc -Ddoclint=missing`，实际启用 missing+syntax+reference 三组） | 缺失标签、畸形标签、失效引用 | `@param/@return/@throws` **0 缺失**；**78 条告警**（53 失效引用 + 24 invalid input + 1 异常类型找不到），分布于 53 个文件 |

告警分类（即本轮修复对象）：

1. **失效引用（53）**：`{@link}`/`{@see}` 指向不存在的符号——Lombok 生成方法（javadoc 不处理 Lombok）、跨模块反向引用（如 cache→remote、persistence→system）、错写包名/方法签名、指向已删除类（如 `EncryptUtil`、`UserService#deleteAccount`、office-service 的 `LocaleWebFilter`/`I18nConfig`）。
2. **invalid input（24）**：Javadoc 正文裸 `<`/`&`（HTML 解析非法）与嵌套花括号 `{@code Result<{ "x": N }>` 畸形内联标签。
3. **@throws 指向不存在类型（1）**：`WeWorkService` 未导入 `WeChatApiException`。
4. **注释归属错位（1 处结构性）**：`ExampleUserService` 的方法 Javadoc（含 @param/@return）错挂在常量字段上。

## 3. 改动清单

共 **53 个 Java 文件**，78 insertions / 85 deletions，全部为注释行（`git diff` 逐行核过，无任何代码行变更）。按批次：

- **批 1 `apex-shared-parent`（12 文件）**：`apex-common`（RedissonProperties 移除 2 个跨模块 @see、TenantCapabilityVerifier/DataScopeConfig 补 FQN 链接、BaseDomainEvent）、`apex-domain`（BaseEntity 移除 Lombok setter @see）、`infrastructure-cache`（DistributedBulkheadService、LocalIdempotentCache）、`infrastructure-remote`（CircuitBreakerService ×3、SensitiveDataMasker 裸字符）、`infrastructure-persistence`（SlowSqlMetricsInterceptor、DefaultTenantLineHandler）、`infrastructure-integration`（WeWorkService @throws FQN）。
- **批 2 `apex-components-parent`（21 文件）**：`apex-system` 16 文件（DataSourceMetricsResponse 6 处裸 `<`、TotpService 之外的 URL/特殊字符转义、Degrade 系列 5 文件 {@code 化、AuthHandler、KeycloakUserSyncService、UserRepositoryImpl 修正签名、OAuth2RegisteredClientRepositoryImpl single()→jdbc() 纠错、DataSourceSwitchAspect Order→Ordered、Gen 系列 FQN、CreditService/SendSmsCode 修正目标、2 个 AccountDeletion DTO 移除幽灵 @see）；`apex-auth`（TotpService otpauth URL {@code 包裹、ResourcePermissionService 内部类字段引用降级）；`apex-office`（OfficeUserSnapshot ×3、DepartmentDTO/EmployeeDTO @see 改指本模块 DTO）；`apex-workflow`（Flux FQN）；`apex-integration`（DeviceStatsResponse 畸形标签重写）。
- **批 3+4 `apex-services-bom` / starters / applications（17+3 文件）**：office-service（MessageUtils 6 处幽灵引用降级、OfficeInternalHandler 畸形标签）、file-service/workflow-service（FileInternalHandler、WorkflowInternalHandler、MqWorkflowEventPublisher FQN）、gateway ×4（TenantCapabilityVerifier 同源 FQN、ResilienceConfig 补方法签名、R2dbcAccessLogRepository FQN、OpenAppTierProperties {@code 化）、license starter（ScheduledExecutorService FQN）、example（FeatureGateAspect 包名纠错 + 常量/方法注释归属重排）。

修复手法统一：可解析的引用改 FQN `{@link}` 或修正目标（`jdbc()`、`Ordered`、`sendSmsCode`、office 本模块 DTO）；不可解析（Lombok 生成、跨模块反向、幽灵类）降级 `{@code}` 或移除陈旧 `@see`；裸 `<`/`&` 用 `{@code}` 或 `&amp;`；畸形内联标签重写为等价描述。

## 4. 验证证据

| 验证 | 结果 |
| --- | --- |
| 分批 doclint 复验（-pl 限定修复模块） | 三批均 BUILD SUCCESS、**0 告警** |
| 全反应堆 doclint 终验 | BUILD SUCCESS，**0 告警**（基线 78 → 0） |
| 唯一遗留：`apex-infrastructure-messaging` javadoc 目标因 `InMemoryDomainEventBus` 的 `@lombok.Getter(onMethod_ = @Override)` 报 error（Lombok 与 javadoc 工具既有不兼容，非注释缺陷，修复需改代码注解，超出本轮"只改注释"边界，登记为已知工具限制） | 工具限制，不处理 |
| `scan-javadoc-issues.sh` 复跑 | 四类清单全部 0 |
| 批 1 `mvnd test -pl :apex-common,...,:apex-infrastructure-remote -am` | 1028 用例 0 失败 BUILD SUCCESS |
| 批 2 `mvnd test -pl :apex-system,:apex-auth,:apex-office,:apex-workflow,:apex-integration -am` | 4848 用例 0 失败 BUILD SUCCESS |
| 批 3+4 `mvnd test -pl :apex-gateway,:apex-office-service,:apex-file-service,:apex-workflow-service,:apex-license-spring-boot-starter,:apex-example -am` | 4205 用例 0 失败 BUILD SUCCESS |
| 分批 `mvnd spotless:apply` | BUILD SUCCESS（palantirJavaFormat 对注释行空白重排随本批入库） |
| 全量 `mvnd -T4 -B clean verify`（PATH 补齐后第三次） | 反应堆 **43/45 模块 SUCCESS**（含 tools-deployment 的 podman Compose 用例全部通过），2 个失败均为环境性并已单独复核：① `apex-example` `ExampleDataSourceTimeoutTest` 计时敏感超时——`-T1` 单跑 BUILD SUCCESS；② `apex-boot-all` `SingleDomainContextBootTest` 端口冲突（`Failed to bind 0.0.0.0:8080`，本机有一个运行 8.5h 的 `apex-gateway-1.3.1.jar` 占用 8080；测试虽设 `server.port=0` 但 `SpringApplicationBuilder.properties()` 落在 defaultProperties，优先级低于 boot-all application.yml 的 8080）——`SERVER_PORT=0` 环境变量重跑 BUILD SUCCESS（15 用例 0 失败）。两处置均为既有环境依赖，与注释改动无关，未改任何测试/网关进程 |
| `git diff --check` / `verify-docs.sh` / 台账同步 | 见第 5 节收尾 |

## 5. 影响面

- 运行时行为：零变化（纯注释；`.class` 语义不变；无签名/注解/依赖改动）。
- 文档影响：IDE 悬停/`javadoc` 产物不再有不可解析引用与渲染畸形；`Result<...>` 类契约描述改为等价自然语言，语义不变。
- 顺带发现（不在本轮处置，供后续任务参考）：① `office-service` 的 `MessageUtils` 无任何消费方且其注释描述的 Locale 过滤器机制不存在，属死代码候选；② `LocalIdempotentCache` 生产代码零消费方；③ 扫描器 `scan-javadoc-issues.sh` 默认路径 `apex-*/src/main/java` 在 11-14 物理嵌套后已失效，本次以环境变量绕过，脚本默认值是否更新另立任务。

## 6. 回滚方式

纯注释改动、未提交：`git checkout -- <文件>` 逐文件回退，或 `git diff > /tmp/before.patch` 备份后整体还原；无迁移、无配置、无部署影响。

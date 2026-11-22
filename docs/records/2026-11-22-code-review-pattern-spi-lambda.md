# 全模块增量评审与兼容性优化（BE-049）

- 日期：2026-11-22
- 任务：`docs/tasks/project-tasks.md` BE-049
- 执行基线：`dev` `b81acf18bc5523ae3566e0aa594b8d4476aab2cf`
- R0 / 执行：ZCode（用户 2026-11-22 指定本会话为本轮 R0）
- 独立复核：R5 子代理（见 `docs/reviews/`）

## 1. 范围与协作边界

业主提出：①评审全部模块，用设计模式、SPI、lambda 优化；②架构如有优化空间进一步调整；③同步文档、配置、脚本、测试并重新生成测试报告。

本轮定位为 **BE-044 的增量复审**：BE-044 已完成 storage/connector SPI 加固、MQ 默认处理器让位、embedding 校验、设备规则隔离。本轮不重复改造已落地项，只处理当前源码中仍有证据的问题，且维持「不改 HTTP 契约、模块坐标、Flyway 迁移、安全与事务语义」的兼容性边界。

BE-043 仍登记执行中并写 `HOLDING`。本轮不替其关闭、不覆盖其成果，共享索引采用追加式写入。

## 2. 全模块覆盖

沿根 POM `<modules>` 递归得到 45 个 reactor 项目（8 POM + 37 JAR）。按分组列出生产/测试 Java 文件数（只读盘点，排除 `target/`）：

| 分组 | 模块数 | 生产 Java | 测试 Java | 说明 |
| --- | ---: | ---: | ---: | --- |
| `apex-shared-parent` | 9 叶子 | 307 | 150 | common/domain/api-contract/五个 infrastructure 能力 JAR + 旧坐标兼容 |
| `apex-components-parent` | 6 叶子 | 1514 | 406 | system/auth/workflow/file/office/integration |
| `apex-business-parent` | 1 叶子 | 69 | 18 | knowledge |
| `apex-starters-parent` | 4 叶子 | 40 | 25 | storage/license/job/seata |
| `apex-services-bom` | 11 叶子 | 257 | 157 | 11 个运行模块 |
| `apex-applications-parent` | 2 叶子 | 16 | 10 | example/boot-all |
| `apex-tools-parent` | 4 叶子 | 29 | 15 | license-tools/generator/tools-deployment/tools-build |

覆盖方式：每模块先列生产/测试文件清单与 POM 内部依赖，再深读策略、工厂、自动装配、安全、事件、缓存、持久化边界等核心类；其余文件按包做静态扫描。**未逐行通读全部约 2232 个生产 Java 文件**，各模块深读范围与仅扫描范围在下文与评审清单中分别标注。

## 3. 已核实的现状（不重复改造）

| 位置 | 现状 | 结论 |
| --- | --- | --- |
| `apex-storage-spring-boot-starter` | `ObjectStorage` 策略接口 + `StorageRegistry` + `StorageProviderLoader`（ServiceLoader）+ 条件装配 | 策略/SPI 已落地，仅补真实描述符测试 |
| `apex-integration` connector | `ConnectorLoader` 双注册（Spring Bean 优先 + SPI） | 已落地，不统一到通用 loader |
| `apex-seata-spring-boot-starter` | `apex.seata.mode` 枚举 + Spring `Condition` 分派 | 装配形态差异，不改 SPI |
| `apex-system` 登录 | `LoginStrategyProvider` List 注入 + EnumMap 自注册 | 已是最佳范本 |
| `apex-common` `SensitiveDataUtil` / `LoggingFilter` | 已委托 `DataMaskUtil` / `LogSafetyUtil` | 非重复实现，保留 |
| `apex-system` `MeilisearchAdapter.maskPhone` | 非 11 位原样返回，与公共脱敏边界不同 | 保留；不作为纯重构替换 |

## 4. 本轮改动

### 4.1 SSO 协议分派收敛

- `apex-system/.../application/support/sso/SsoProtocolAdapterFactory.java`：构造时把四个内置适配器登记为不可变 `Map`，`getAdapter()` 与 `isSupported()` 共用查表；移除对已装配 Bean 无实际作用的 `ConcurrentHashMap` 缓存；协议名按 `Locale.ROOT` 规范化。
- 保留原有 public 四参数构造器、`SsoProtocolAdapter` 接口、异常类型与消息格式；不宣称已具备 SPI 扩展。
- 新增 `SsoProtocolAdapterFactoryTest`：四协议、空白、未知、`tr-TR` 默认 Locale、同一实例复用。

### 4.2 搜索资源分派收敛

- `apex-system/.../infrastructure/search/MeilisearchAdapter.java`：六种资源的文档读取、全量 ID、增量 ID 由同一 `Map<String, ResourceQuery>` 注册表分派（方法引用接入既有私有方法），删除三处重复 `switch`；`ALL_RESOURCE_TYPES` 供重建索引复用。
- 索引文档结构、过滤条件、时间格式、租户、批次顺序、`jdbc()`/`remote()`/`general()` 调度边界均未改变。

### 4.3 内部端点错误响应去重

- 新增 `apex-common/.../common/result/InternalEndpointErrors.java`：内部端点错误映射（安全类业务码映射 HTTP 400/401/403，其余业务码 HTTP 200，非业务异常 HTTP 500）。
- `FileInternalHandler`、`OfficeInternalHandler` 改为调用该实现，各自保留原日志文案。
- 明确不替换为 `HttpStatusUtil`：后者未知业务码映射 500，与内部端点口径不同；auth、workflow、aggregation 的映射亦不等价，保持原实现。

### 4.4 网关撤销缓存保留期

- `RevocationProperties` 新增 `retentionSeconds`（默认 7200，覆盖访问令牌最长生命周期）。
- `RevocationCacheService` 保留窗口取 `max(retentionSeconds, 2 × 拉取间隔, 60s)`：此前仅约 60 秒，撤销标记会早于访问令牌过期被清理，而下游六个服务只验签不查撤销。
- 新增 `retentionMillisForTest()` 包内观测点与两条回归用例（默认覆盖生命周期、配置过小时被抬升）。
- **已回退**：曾尝试让 `revocationsSince` 游标按批最大撤销时间回退 1 秒以覆盖 MySQL 秒级 `revoked_at` 的批边界风险。实现后发现游标会停在 `max-1s`，空批时也回退，导致同一秒记录被永久重放且游标无法推进（既有 `TokenRevocationFeedTest` 断言失败）。已恢复原「游标 = 本批最大撤销时间」语义，该秒级边界风险登记为独立待评估项（见评审清单 §2）。

### 4.5 数据权限 WHERE 合并

- `DataScopeSqlRewriter`：原 `WHERE` 根节点为 `OR` 时先整体括起再追加 `AND`。此前 `a OR b` 会变成 `a OR b AND scope`，左侧分支绕过数据权限。
- 仅对 `OR` 根节点加括号，其余形态保持原 SQL 输出不变（不影响既有断言与下游 SQL 缓存）。
- 测试：新增 `shouldParenthesizeUnparenthesizedOrBeforeAppendingScope` 与括号已存在用例。

### 4.6 Outbox 领取行数校验

- `WorkflowEventOutboxDispatcher.claim()`：只有条件更新实际返回 1 行的记录才进入投递循环。此前忽略更新行数，重叠调度或多实例下会重复投递同一事件。
- 测试：新增「已被抢占的记录不重复投递」。

### 4.7 SPI 真实描述符发现

- 新增 `StorageProviderDiscoveryTest`：用临时目录构造真实 `META-INF/services/com.apex.scaffold.storage.ObjectStorage`，经 `URLClassLoader` 验证正常发现、构造失败跳过、缺失类跳过、无描述符返回空。此前的测试仅注入模拟迭代器。

### 4.8 测试报告生成链

原 `scripts/test/generate-test-report.sh`：单层 `*/target/surefire-reports` glob、硬编码 10 个模块名、仅统计 Surefire、`grep` 文本匹配属性。模块两次物理嵌套后该实现会漏统计绝大多数模块。

- 新增 `apex-tools-deployment/.../TestReportSummary.java`：沿 POM `<modules>` 递归发现模块；JDK XML 解析器并禁用 DOCTYPE 与外部实体；Surefire/Failsafe 分列；缺失报告标注；按 `classname#name` 去重后的 `testcase` 数与 `tests` 不符即非零退出（识别报告被截断）；仅存在重复 `testcase`（Surefire 重复执行/嵌套类的合法输出）时告警、不阻断；非法属性、畸形 XML 同样非零退出。
- `LocalDeployment` 新增 `test-report --root PATH --output DIR` 子命令。
- `scripts/test/generate-test-report.sh` 改为调用该子命令（保留 Markdown/JSON 输出路径），`generate-comprehensive-test-report.sh` 继续转发同一实现。
- 新增 `TestReportSummaryTest`（R5 复核后为 10 条）：嵌套发现、Surefire/Failsafe 分列、缺失标注、按 `classname#name` 去重后与 `tests` 不符即失败、仅重复 `testcase` 告警、畸形 XML、非法属性、DOCTYPE 拒绝、父 POM 坐标不被误读、输出与参数校验。
- 报告显式声明：只统计 XML 快照，不据此断言整次 `verify` 通过。

## 5. 测试基线

| 命令 | 结果 |
| --- | --- |
| `mvnd -T4 -B -pl :apex-infrastructure-persistence -am test` | BUILD SUCCESS（数据权限 85 用例 0 失败） |
| `mvnd -T4 -B -pl :apex-gateway,:apex-auth,:apex-system,:apex-tools-deployment -am test` | BUILD SUCCESS |
| `mvnd -T4 -B -pl :apex-storage-spring-boot-starter -am test` | BUILD SUCCESS（含新增 4 条真实描述符用例） |
| `mvnd -T4 -B -pl :apex-workflow-service,:apex-file-service,:apex-office-service -am test` | 首轮 office `MessageSocketLifecycleTest` 1 错误；`-T1` 单跑该用例 BUILD SUCCESS（156s → 12.5s），属并发计时抖动 |
| `mvnd -T4 -B clean verify` | 见 §6 |

## 6. 全量门禁与报告

| 项 | 结果 |
| --- | --- |
| `mvnd -T1 -B clean verify`（最终门禁） | **BUILD SUCCESS**，退出码 0，**45/45 项目 SUCCESS**，23:37 min（`-T4` 运行 15:15/15:28 同结论，仅计时敏感用例抖动） |
| 控制台 `Results:` 聚合（48 块） | Tests 6740 / Failures 0 / Errors 0 / Skipped 33 |
| XML 汇总（新报告生成器，最终构建生成） | 45 模块 / 单元 6271 / 集成 405 / 合计 6676 / 0 失败 / 0 错误 / 33 跳过 |
| 报告 | [docs/06-testing/2026-11-22-be049-full-verify-report.md](../06-testing/2026-11-22-be049-full-verify-report.md) |

门禁共执行七次：两次因 Spotless 格式、一次因撤销游标回退破坏既有语义、两次仅因 `ExampleDataSourceTimeoutTest` 在 `-T4` 下的计时抖动；第 2 次另因并发写入同一 `target/` 导致 `apex-system` class 损坏（已确认为操作并发冲突）。最终以 `-T1` 全量门禁 45/45 SUCCESS 收口。详见验证报告 §3。

治理检查：`verify-docs.sh --strict` 0 项、`check-doc-refs.sh --strict` 0 项、`audit-backend-rules.sh --strict` 无阻断、`git diff --check` 通过、`bash -n` 两脚本通过；`check-doc-links.sh` 11 条断链均为归档计划既有存量（未触碰该文件）。

## 7. R5 结论

独立 R5 三轮复核（均为与执行者不同的子代理实例，只读）：

| 轮次 | 结论 | 要点 |
| --- | --- | --- |
| 第 1 轮 | **REJECT** | 6 处代码等价性 PASS；阻断项集中在文档：台账条目错位、计数不一致、门禁未覆盖最新快照，并指出我删除了一条基线断言（`DataScopeSqlRewriterTest` 的「括号内 OR + 后续 AND」用例） |
| 第 2 轮 | REJECT | 代码等价性复述 PASS；确认 MF-01（台账错位）、MF-02~MF-04（计数/轮次/计划份数）、MF-05（门禁未覆盖 16:47 后改动）、MF-06（死代码与类头注释）需修 |
| 第 3 轮（窄范围终审） | **APPROVE_WITH_NITS** | 8 项定点核对中 7 项 PASS；仅剩台账 §4 表格被空行截断一处，以及 4 处单行文档计数（N-10~N-15） |

已按复核清零：
- MF-01：台账 #85/#86 移回 §3 登记表；上表所述的 §4 表头空行（N-10）已删除。
- MF-02：三处统一「报告解析器 10 条」（实测 `@Test` = 10），并修正 §4.8 旧口径。
- MF-03：`06-testing/README`、`docs/README`、验证报告统一 6740 / 6676 / 33 / 七次执行；验证报告 §3 第 7 行标注最终门禁。
- MF-04：计划归档 16 份，迁移明细补「2026-11-22（2 份，BE-045 与 BE-049）」，与实测 16 个文件一致。
- MF-05：`mvnd -T1 -B clean verify` BUILD SUCCESS（45/45、6740/0/0/33、23:37），日志 `/tmp/be049-final-verify-t1.log`；报告用该构建产物重生成。
- MF-06：删除 `scan()` 未使用的 `name`；类头 Javadoc 与退出码说明改为「去重后不符即失败、仅重复告警」。
- N-01/N-11：`config/nacos/apex-gateway.yaml` 补 `retention-seconds`，并在任务卡租约写入范围与本记录 §4.7 登记。
- N-12~N-15：验证报告 6738→6740、9 条→10 条；记录 §5 84→85 用例；「运行时修正」改四处并补 `Locale.ROOT`；增量归因按「数据权限 2 + 恢复基线 1 条」表述。

保留的残余项（R5 判定非阻断，建议另立任务）：`XorExpression` 未纳入括起分支、`Parenthesis` 可行但实测该版本构造器异常故保留字符串解析、`log.error` 变量格式串、workflow Outbox PROCESSING 无超时回收、网关保留期按入缓存时刻计算、脚本失败提示措辞、报告工具缺 `--require-reports` 严格模式。

## 8. 影响面

- HTTP 契约、DTO 字段、模块坐标、POM 聚合、Flyway 迁移、端口与部署拓扑：零变化。
- 运行时行为变化四处，且均为修正：
  1. 网关撤销记录保留期由约 60 秒延长到 2 小时（可配置）；撤销生效更可靠。
  2. SSO 协议名改用 `Locale.ROOT` 规范化：土耳其语等默认 Locale 下小写 `oidc` 不再被误判为不支持（有 `tr-TR` 用例覆盖）。
  3. 数据权限对 `OR` 根条件加括号：越权结果被消除，SQL 文本形态在该场景变化。
  4. Outbox 只投递成功抢占的记录：重叠调度不再重复发布。
- `SsoProtocolAdapterFactory` 构造器签名未变，Spring 注入路径不变。

## 9. 未验证项与限制

- 撤销流在 `revoked_at` 秒级精度下的批边界丢记录风险仅静态分析，未复现；已作为待评估项登记，本轮未改语义。
- 未在真实多实例部署下验证 Outbox 抢占，仅用 H2 模拟「已被抢占」状态。
- 未实测网关撤销流全链路（需认证中心 + 网关同时运行）。
- 全模块评审为深读核心 + 包级扫描，未逐行通读全部生产文件；未覆盖项见评审清单。

## 10. 回滚方式

- 未提交前按文件还原：`git checkout -- <path>`；建议先 `git diff > /tmp/be049-before.patch`。
- 已提交后按主题 `git revert <commit>`；禁止 `reset --hard`、`push -f`。
- 无迁移脚本、无配置键删除、无模块坐标变更，回滚不涉及数据修复。
# 全仓设计模式 / SPI / 注释复审与整改记录（BE-044）

- 日期：2026-11-22
- 任务：`docs/tasks/project-tasks.md` BE-044（台账 #73）
- 计划：[2026-11-22-code-quality-pattern-spi-comment-plan.md](../plans/2026-11-22-code-quality-pattern-spi-comment-plan.md)
- 基线：`6346c7a4`
- 执行：Trae（用户 2026-11-22 授权，串行单写者）

## 1. 范围

业主提出三项要求：①通读全部模块，用设计模式消除重复与硬编码；②能用 SPI 的地方改用 SPI；③修正与实现不符、缺 tag 的注释。

复审结论：本项目在关键位置（storage `ServiceLoader`、connector 注册表、登录策略族、限流/缓存工厂、`DomainEventBus`、office 通知集合注入、knowledge 端口 + Adapter）**已具备恰当的设计模式与 SPI，不重建抽象**。本轮只处理有证据的失真注释与真实健壮性缺陷，边界为**兼容性内部优化**：不改 HTTP 契约、模块坐标、Flyway 迁移、安全与事务语义，不为套模式新增抽象层。

## 2. 改动清单

### Wave A — 注释失真修正（纯注释，零行为变更）

| 文件 | 动作 |
| --- | --- |
| `apex-system` `AbstractPhoneLoginStrategy` | 类注释改为模板方法真实语义：已存在用户走 `postAuthentication` + 正式令牌对；不存在则注册准入 + `AuthTicketType.REGISTRATION` 一次性票据 + 同有效期临时 accessToken + `nextAction=REGISTER_REQUIRED`，明确本基类不创建用户；修正重复 `<h2>`；补构造器 4 参数 Javadoc |
| `apex-office` `NotifyChannelConfiguration` | 两个条件 Bean 注释写明 `apex.notify.sms.enabled` / `apex.notify.email.enabled`、装配层须提供发送函数、缺失时 `IllegalStateException` |
| `apex-integration` `RuleCondition` | `compareScalar` 如实描述 `-2` 哨兵（`NE/LT/LTE` 命中，`EQ/GT/GTE` 不命中）；`matchesAll` 说明空列表返回 `true` |
| `apex-domain` `DomainEventBus` | `publish`/`publishAll`/`publishSync` 补 `@return`；`publishSync` 说明同一调用链等待处理器执行完毕 |
| `apex-infrastructure-integration` `PhoneDecryptStrategy` | 改为「实现现状」：AES（`"0"`）已实现 `AesPhoneDecryptStrategy`，RSA（`"1"`）尚未实现；删除指向不存在类的示例，改为 `ChuanglanSmsConfig` 注册 + `ChuanglanApiService` 构造器注入 |

### Wave B — 存储 SPI 发现加固

- `apex-storage-spring-boot-starter` `StorageProviderLoader`：`@Slf4j`；新增包内 `collect(Iterator<ObjectStorage>, List<ObjectStorage>)`，把「迭代取下一个 / 实例化 / `type()` 校验」三段分别隔离——`hasNext()` 抛 `ServiceConfigurationError` 时保留已发现并停止，`next()` 抛 `ServiceConfigurationError`/`RuntimeException` 时告警跳过并继续，`type()` 为 null/空白或抛异常时告警跳过。保留原有严格口径（重复 `type()` 仍由 `StorageRegistry` 拒绝、显式启用但 SDK 缺失仍失败、未知默认类型仍拒绝）。
- `StorageProviderLoaderTest`：`StubStorage` 放开为可继承；新增 4 个用例（`type()` 抛异常跳过、空白 `type()` 跳过、`next()` 抛错后继续、描述符失败保留已发现）。

### Wave C — MQ 默认本地事务处理器让位

- `DefaultMqLocalTransactionHandler`：删除 `@Component` 与类级条件注解，改为纯实现（保留 `@Slf4j` + 构造器注入 + Javadoc，新增「注册方式」说明）。
- 新增 `MqLocalTransactionAutoConfiguration`：`@AutoConfiguration(after = RocketMQAutoConfiguration.class)` + `@ConditionalOnClass` + `@ConditionalOnBean(RocketMQTemplate.class)` + `@ConditionalOnProperty(apex.features.message-queue)`；`@Bean` 加 `@ConditionalOnMissingBean(MqLocalTransactionHandler.class)` + `@ConditionalOnBean(DomainEventOutboxStore.class)`。
- 登记 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。
- 新增 `MqLocalTransactionAutoConfigurationTest`（`ApplicationContextRunner`）：默认注册、无 Outbox 不注册、无模板不注册、开关关闭不注册、自定义处理器让位。

### Wave D — 连接器 SPI 诊断统一

- `apex-integration` `ConnectorLoader`：`@Slf4j` + 包内 `collect(...)`（与 Wave B 同款异常隔离）+ 私有 `accept` 诊断；`load()` 使用上下文类加载器（null 回退本类）；`merge()` **保持内置优先**语义不变（不改优先级，避免部署期行为跳变），同名 SPI 跳过并 `warn`；内置间重复仍抛 `IllegalStateException`。
- 新增 `ConnectorLoaderTest`：5 个用例（`type()` 抛异常跳过、空白跳过、`next()` 抛 `ServiceConfigurationError` 继续、`hasNext()` 中途失败停止且保留已发现、内置与同名 SPI 并存时内置优先且无异常）。

### Wave E — 知识库向量响应校验

- `apex-knowledge` `OpenAiCompatibleEmbeddingAdapter`：`embed` 空输入返回空；按 `batch-size` 切分后 `Mono.zip` 合并；`doOnNext(this::validateDimensions)` 在 `dimensions > 0` 时**逐条**校验维度。新增包内 `parseVectors(int expectedCount, List<Object> data)`：条目数不符、条目 null、`embedding` 缺失/空、元素非 `Number` 显式失败；条目带 `index` 时按 index 归位（越界或槽位冲突失败），缺失时回退响应顺序（同样检查槽位冲突）；最后检查所有槽位非空。类 Javadoc 同步更新。
- `OpenAiCompatibleEmbeddingAdapterTest`：新增 6 个用例（乱序 index 归位、无 index 回退顺序、重复 index 失败、越界 index 失败、第二个向量维度不符失败、非数值元素失败）。

### Wave F — 运行模块 / 工具复审（只出结论，无代码改动）

复审 `apex-services-bom` 全部运行模块、`apex-applications-parent`、`apex-tools-parent`：生产代码 `@Autowired` / `BeanUtils` / `PropertyUtils` / 手写 `LoggerFactory` 均为 0；脚本初筛的 5 处「疑似缺类注释」经逐条人工核对为多行注解续行误判；CLI 控制台输出属预期交互；未发现新的、有证据支撑的重复实现或硬编码。结论已回填计划 §5。

### 文档与索引

- 计划正文 `docs/plans/2026-11-22-code-quality-pattern-spi-comment-plan.md`（含 §5 Wave F 结论）。
- `docs/README.md`、`docs/plans/README.md`、`docs/tasks/project-tasks.md`（BE-044）、`docs/records/README.md`（本记录）登记。
- `docs/reference/ai-knowledge/07-change-memory.md` 第 #73 条。

## 3. 验证证据

| 验证 | 结果 |
| --- | --- |
| `mvnd -T4 -B -pl :apex-storage-spring-boot-starter -am test` | BUILD SUCCESS，8 用例 0 失败 |
| `mvnd -T4 -B -pl :apex-infrastructure-messaging -am test` | BUILD SUCCESS，138 用例 0 失败（含新增 5 个自动配置用例） |
| `mvnd -T4 -B -pl :apex-integration -am test` | BUILD SUCCESS，0 失败 |
| `mvnd -T4 -B -pl :apex-knowledge -am test` | BUILD SUCCESS，11 用例 0 失败（含新增 6 个） |
| `mvnd -T4 -B -pl :apex-system-service,:apex-auth-service -am test` | BUILD SUCCESS，0 失败（覆盖服务模块及依赖测试；不等同于真实服务启动验证） |
| `bash scripts/governance/verify-docs.sh --strict` | 0 项（见收尾复核） |
| `git diff --check` | 无空白错误 |
| Wave G：`mvnd -T4 -B -pl :apex-integration -am spotless:apply test` | BUILD SUCCESS，含三条规则引擎回归用例 |
| 收尾 `verify-docs.sh --strict` / `audit-backend-rules.sh` | 文档 0 项问题；后端扫描无阻断项，保留已登记例外与人工确认提示 |
| 收尾 `check-memory-sync.sh` | 无暂存改动，脚本跳过；不代表提交前台账校验通过 |
| 收尾 `scripts/ci/check-doc-links.sh` | 未通过：扫描 852 个 Markdown、17 个 HTML、3888 条链接，发现 11 条断链，均位于归档计划 `docs/plans/archived-ai-sessions/gateway-resilience-enhancement-plan.md`，指向旧模块路径；原始日志 `/tmp/be044-final-links.log`。该文件未在本轮修改，不扩大本任务修复范围 |
| 首轮全仓 `mvnd -T4 -B clean verify` | 44/45 项目成功，`apex-boot-all` 的 `SingleDomainContextBootTest.fileDomainStartsOnH2` 因缺少 `RedissonClient` 失败；整体未通过，原始日志 `/tmp/be044-full-verify.log` |
| R5 退回复核 | 首轮 REJECT：embedding index 截断/溢出/非数值静默回退、事件批量发布顺序注释失真两项 P2；已修正，独立 R5 再审 APPROVE；修复后定向测试通过，全量复验 BUILD SUCCESS（45/45 项目 SUCCESS，退出码 0） |

## 4. 影响面

- **HTTP 契约 / 模块坐标 / Flyway 迁移 / 安全与事务语义**：零变化。
- Wave B/D：SPI 发现对单条坏描述符由「中断整轮」改为「告警跳过」，已发现的后端/连接器保留；内置连接器优先级不变。
- Wave C：默认 `MqLocalTransactionHandler` 由组件扫描改为自动配置；无 `RocketMQTemplate` 的环境不再注册该 Bean（此前为孤立 Bean）；业主自定义 `MqLocalTransactionHandler` 存在时默认实现让位（此前两者共存会在监听器注入处抛 `NoUniqueBeanDefinitionException` 致启动失败）。`AutoConfigurationExcludeFilter` 保证不重复注册。
- Wave E：仅在响应条目带 `index` 时归位，缺失时保持旧顺序，兼容不返回 `index` 的本地推理服务；维度校验由「仅首个向量」变为逐条，响应畸形时显式失败而非静默写错。
- Wave G（用户追加授权后执行）：`DeviceRuleEngineService` 条件解析失败时返回 `Mono.empty()` 跳过当前规则，不再触发动作；非法 JSON 与解析器拒绝的空数组均 fail-closed，后续有效规则继续执行。保留 `RuleCondition.matchesAll` 自身语义。新增 `DeviceRuleEngineServiceTest` 三条用例，真实解析器验证事件发布行为；初轮测试复现非法条件发布事件，随后修正空数组测试预期，最终 `mvnd -T4 -B -pl :apex-integration -am spotless:apply test` BUILD SUCCESS。原始日志 `/tmp/be044-waveg-red.log`、`/tmp/be044-waveg-green.log`、`/tmp/be044-waveg-green2.log` 为本机会话证据，不入库。

## 5. R5 退回与全量门禁修复

- embedding 响应仅在 `index` 字段缺失时回退响应顺序；字段存在时必须为批内整数，先校验数值、整数性和批内范围，再转为 `int`。新增 7 个参数化用例覆盖正负小数、32/64 位溢出整数、字符串、null、布尔值；修复前 7 个用例均因错误返回向量而失败（`/tmp/be044-index-red.log`）。
- `DomainEventBus.publishAll` 注释更正为批量异步发布、不保证按列表顺序完成；保留 `flatMap` 实现。
- 首轮全量门禁暴露 MQ 消费者缺少 Redisson 前置条件；`NotificationConsumerAutoConfiguration` 增加 `RedissonClient` Bean 条件，显式排在 `RedissonAutoConfigurationV2` 之后。新增无 Redisson 的上下文测试，修复前复现缺依赖启动失败（`/tmp/be044-mq-red.log`）。
- 独立 R5（实例 `f14ce815-fe27-4a85-b487-fd5ada6e55ce`）再审结论 **APPROVE**：两个 P2 已收口；Redisson 条件与固定依赖 3.36.0 的自动配置入口一致；SPI、规则跳过及默认事务让位无阻断问题。审查为只读静态核对，不代表真实外部服务启动。非阻断建议：后续补自动配置排序场景测试。
- 修复后 `mvnd -T4 -B -pl :apex-knowledge,:apex-boot-all -am spotless:apply test` **BUILD SUCCESS**（原始日志 `/tmp/be044-return-green.log`）；修复后全量 `mvnd -T4 -B clean verify` **BUILD SUCCESS**（退出码 0，45/45 反应堆项目 SUCCESS，耗时 `13:24 min (Wall Clock)`，完成于 `2026-11-22T08:41:02+08:00`；原始日志 `/tmp/be044-postfix-full-verify.log`）；首轮失败记录保留。尚无集成提交，BE-044 保持 **待复核**。治理复检 0 项、后端红线无阻断、快速预检 `OK=14 WARN=4 FAIL=0`；memory-sync 因无暂存改动跳过，不记为提交同步通过。

## 6. 回滚方式

- 各 Wave 为独立主题，未提交前按文件回退：`git checkout -- <文件>`，或 `git diff > /tmp/before.patch` 备份后整体还原。
- 已提交后按波次 `git revert <commit>`；禁止 `reset --hard`、`push -f`。
- 无迁移、无配置、无部署脚本影响。
# 全仓设计模式 / SPI / 注释复审与整改计划（2026-11-22）

> 关联任务：`BE-044`（`docs/tasks/project-tasks.md`）。
> 关联背景：[设计模式 / Starter / SPI 优化方案](../08-project-management/plans/design-pattern-starter-spi-optimization-plan.md)（2026-10-28 评审快照，仅作背景引用，正文结论以本计划为准）。
> 边界：只做**兼容性内部优化**。不改 HTTP 契约、模块坐标、Flyway 迁移、安全与事务语义；不为套用设计模式而增加抽象层。

## 1. Context

业主提出三项要求：

1. 通读全部模块，用设计模式消除重复与硬编码；
2. 能用 SPI 的地方改用 SPI；
3. 修正与实现不符、缺 tag 的注释，使 Javadoc 完整专业。

已完成一次只读复审（shared / starters 346 生产文件 + 174 测试文件；components / business 1583 生产文件 + 422 测试文件；POM、`AGENTS.md`、`docs/` 索引）。结论是：**本项目在多数关键位置已经有恰当的设计模式与 SPI，不需要重建**。

## 2. 现状分析

### 2.1 已有且不应重建的设计

| 位置 | 现有做法 | 结论 |
| --- | --- | --- |
| `apex-storage-spring-boot-starter` | `ServiceLoader` 加载 `ObjectStorage`（`META-INF/services`） | SPI 已落地，仅需加固失败隔离 |
| `apex-integration` connector | `ConnectorLoader` + `StorageRegistry` 式注册表，内置 4 个描述符 | SPI 已落地，仅需统一诊断口径 |
| system 登录 | 策略族（`*LoginStrategy`）+ 模板方法 | 无需改动 |
| 限流 / 缓存 | 工厂 + 策略集合注入 | 无需改动 |
| 领域事件 | `DomainEventBus` 注册表分发 | 无需改动 |
| office 通知 | 集合注入 + 条件 Bean | 无需改动 |
| knowledge 检索 | 端口 + Adapter | 无需改动 |

### 2.2 复审发现的真实问题（有证据）

| # | 位置 | 问题 | 性质 |
| --- | --- | --- | --- |
| 1 | `StorageProviderLoader` | 只有 `type()` 校验在 try 内；`ServiceLoader` 迭代本身抛 `ServiceConfigurationError` 会中断整轮发现；注释承诺“逐条记录并跳过”但无任何日志 | 注释失真 + 健壮性缺陷 |
| 2 | `DefaultMqLocalTransactionHandler` + `MqTransactionListener` | 默认处理器是 `@Component` 无条件注册；监听器用 `ObjectProvider.getIfAvailable()`，当业主自定义处理器与默认同时存在时抛 `NoUniqueBeanDefinitionException`，**应用启动失败** | 共存缺陷 |
| 3 | `ConnectorLoader` | 注释称“重复 type 显式失败”，实际 `merge()` 对内置/SPI 同类型静默让内置优先；`load()` 异常无日志 | 注释失真 |
| 4 | `RuleCondition` | 注释称“类型不可比（含 null）翻译为不命中”，实际返回 `-2`，`NE/LT/LTE` 会命中 | 注释失真 |
| 5 | `OpenAiCompatibleEmbeddingAdapter` | 只校验首个向量维度，忽略响应 `index`；响应乱序会静默写错向量；注释宣称“维度不符即显式失败” | 缺陷 + 注释失真 |
| 6 | `AbstractPhoneLoginStrategy` | 类注释称“自动建号”，实际是签发注册票据 + 临时令牌，不创建账号 | 注释失真 |
| 7 | `NotifyChannelConfiguration` | 注释称“不注册 Bean”，实际是抛异常拒绝未知渠道 | 注释失真 |
| 8 | `DomainEventBus` | 三个返回 `Mono` 的接口方法缺 `@return`（红线 B7） | 注释缺失 |
| 9 | `PhoneDecryptStrategy` | 类注释称“支持 RSA”，方法实现标注“待实现” | 注释失真 |

### 2.3 Wave G — 条件解析失败隔离（用户追加授权）

用户明确要求处理此前未执行/待决项后，修复 `DeviceRuleEngineService`：条件解析失败时 `onErrorResume` 返回 `Mono.empty()`，直接跳过当前规则，不再把失败转换为空条件而触发动作。解析器原本就拒绝空条件数组，该失败同样跳过当前规则；`matchesAll` 自身的空列表语义不变，后续有效规则继续评估。新增真实 JSON 解析回归测试覆盖非法 JSON、后续有效规则继续执行、空条件数组拒绝三个场景；初轮测试中两条复现非法条件发布事件，另一个空数组预期经核对解析器后修正。该项改变非法条件的运行行为，不改 HTTP、数据结构与正常规则语义。

## 3. 分批改动

### Wave A — 注释失真修正（纯注释，零行为变更）

| 文件 | 动作 |
| --- | --- |
| `apex-system` `AbstractPhoneLoginStrategy` | 按真实实现改写类注释与方法注释（注册票据 + 临时令牌，不建号） |
| `apex-office` `NotifyChannelConfiguration` | 改为“未知渠道显式失败” |
| `apex-integration` `RuleCondition` | 如实描述 `-2` 哨兵与 `NE/LT/LTE` 命中语义 |
| `apex-shared` `DomainEventBus` | 补三个 `@return`，接口注释与实现一致 |
| `apex-infrastructure-integration` `PhoneDecryptStrategy` | 区分“已实现/待实现”能力 |
| `apex-knowledge` `OpenAiCompatibleEmbeddingAdapter` | 注释在 Wave E 随代码一并修正 |

验证：`git diff --check`；受影响模块 `mvnd -T4 -B -pl :<artifact> -am -DskipTests compile`（纯注释应零编译问题）。

### Wave B — 存储 SPI 发现加固

- `StorageProviderLoader`：改为显式迭代器循环，把“迭代取下一个 / 实例化 / `type()` 校验”三段分别隔离；`ServiceConfigurationError` 与 `RuntimeException` 均 `warn` 记录后跳过该条，不中断其余后端。
- 保留现有严格口径：重复 `type()` 仍由 `StorageRegistry` 拒绝；显式启用但 SDK 缺失仍失败；未知默认类型仍拒绝。
- 新增包内可见的 `collect(Iterator<ObjectStorage>)` 作为可测接缝，供单元测试注入异常迭代器。
- 测试：`StorageProviderLoaderTest` 增加“坏条目跳过、好条目保留”“type() 抛异常跳过”“迭代器抛 `ServiceConfigurationError` 不中断”。

验证：`mvnd -T4 -B -pl :apex-storage-spring-boot-starter -am test`。

### Wave C — MQ 默认本地事务处理器让位

- `DefaultMqLocalTransactionHandler` 去掉 `@Component` 与类级条件注解，改为纯实现（保留构造器与 Javadoc）。
- 新增 `MqLocalTransactionAutoConfiguration`：`@AutoConfiguration(after = RocketMQAutoConfiguration.class)` + `@ConditionalOnClass` + `@ConditionalOnProperty(apex.features.message-queue)` + `@ConditionalOnBean(RocketMQTemplate.class)`；`@Bean` 方法加 `@ConditionalOnMissingBean(MqLocalTransactionHandler.class)` + `@ConditionalOnBean(DomainEventOutboxStore.class)`。
- 登记到 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。
- 语义不变：`COMMIT` / `ROLLBACK` / `UNKNOWN` 判定与 Outbox 同事务前提完全保留；默认实现只在业主未提供自定义处理器时注册。
- 测试：新增自动配置测试（`ApplicationContextRunner`）：默认注册、自定义优先且默认让位、开关关闭不注册。

验证：`mvnd -T4 -B -pl :apex-infrastructure-messaging -am test`；再跑 `:apex-system-service` 与 `:apex-auth-service` 的 `-am test` 验证装配不受影响（`AutoConfigurationExcludeFilter` 已排除组件扫描重复注册）。

### Wave D — 连接器 SPI 诊断统一

- `ConnectorLoader.load()`：显式迭代器循环 + 逐条异常隔离 + `warn` 日志（与 Wave B 同款，但保留各自模块的日志实现）。
- `merge()`：保持“内置优先、SPI 同类型跳过”的既有行为（不改优先级，避免部署期行为跳变），但遇到跳过时 `warn` 记录，并把类与方法注释改成如实描述。
- 测试：现有 `ConnectorExecutionServiceTest` 校验内置重复拒绝与 SPI 类型集合；补充“内置与 SPI 同类型时内置生效且不抛异常”的断言。

验证：`mvnd -T4 -B -pl :apex-integration -am test`。

### Wave E — 知识库向量响应校验

- `embedBatch` 解析响应时按 `index` 归位；`index` 缺失时回退为响应顺序（保持对不返回 `index` 的本地推理服务兼容）；`index` 越界、重复，或条目数不符时显式失败。
- 维度校验从“首个向量”改为**逐条**校验（`config.dimensions > 0` 时）。
- 抽出包内可见的 `parseVectors(...)` 纯函数便于测试；修正类注释。
- 测试：`OpenAiCompatibleEmbeddingAdapterTest` 增加“乱序 index 归位”“重复 index 失败”“第二个向量维度不符失败”“无 index 回退顺序”。

验证：`mvnd -T4 -B -pl :apex-knowledge -am test`。

### Wave F — 运行模块 / 工具复审结论（只出结论）

对 `apex-services-bom` 各运行模块、`apex-applications-parent`、`apex-tools-parent`（generator / license-tools / tools-build / tools-deployment）做静态复审，结论写入本文 §5，**不产生代码改动**，除发现注释失真时并入 Wave A 口径。

## 4. 回滚

- 每波独立提交，提交信息按 `fix(...)` / `docs(...)` 区分；回滚使用 `git revert <commit>`。
- 禁止 `reset --hard`、`push -f`。

## 5. Wave F 复审结论

静态复审范围：`apex-services-bom` 全部运行模块 `src/main/java`、`apex-applications-parent`、`apex-tools-parent`（generator / license-tools / tools-build / tools-deployment）。

| 检查项 | 结论 |
| --- | --- |
| 生产代码 `@Autowired` / `BeanUtils` / `PropertyUtils` / 手写 `LoggerFactory` | 0 处；命中仅出现在测试文件的日志断言辅助 |
| 公开类注释 | 脚本初筛 5 处「疑似缺注释」逐条人工核对，均为多行注解续行导致的误判，类 Javadoc 完整 |
| CLI 工具控制台输出（generator / license-tools / tools-deployment） | 属命令行入口的预期交互，不改 |
| 设计模式 / SPI 缺口 | 未发现新的、有证据支撑的重复实现或硬编码；运行模块以装配为主，无重复框架代码 |

结论：Wave F **不产生代码改动**；除本表外无新增偏离项。

## 6. 风险与约束

| 风险 | 处置 |
| --- | --- |
| Wave C 改动装配方式，若某服务依赖组件扫描 | 已确认 `@SpringBootApplication` 的 `AutoConfigurationExcludeFilter` 会排除自动配置类，不会重复注册；改动后必须跑 system-service / auth-service `-am test` |
| Wave E 让响应顺序更严格 | 仅在 `index` 存在时归位；缺失时保持旧顺序，避免破坏本地推理服务 |
| 与 BE-043 并行写 `docs/README.md`、任务盘、台账 | 采用追加式写入，提交前重读目标段落，不整表覆盖 |
| 注释改动范围过大引入噪声 | 只改与实现不符或缺失 tag 的段落，不做全量重排 |
# BE-065 PMD 全仓规则违规修复

## 范围

基于 BE-064 的 Java 21 ASM 兼容修复，盘点并逐项处理 PMD 检出的代码违规；不改变质量规则、豁免清单或业务契约。

## 改动清单

执行中；按独立模块批次追加。

## 验证证据

`source scripts/build/init-build-env.sh && mvnd -T4 -B -Dpmd.outputDirectory=/tmp/be065-pmd pmd:pmd` 返回 BUILD SUCCESS。注意该插件实际仍将报告写入各模块 `target/pmd.xml`；这些被忽略的 XML 已由本次全仓分析刷新，确认涉及 24 模块共 377 条违规。汇总：UndefineMagicConstant 171、缺方法 Javadoc 76、复杂条件 27、CollectionInit 容量 24、NeedBrace 18、SwitchStatement 12、EqualsAvoidNull 11 等。各报告来自未提交工作树，不能视为基于干净 SHA 的历史基线。

## 影响面

源码与测试层，不改变外部 API。逐批复验并记录业务语义。

## 回滚方式

仅回滚 BE-065 明确列出的源码/测试改动；不得清理其他任务文件或回退全仓。

## 进展追加（2026-11-23）

- 更正初始盘点：曾记录的 377 条来源于异步/旧 XML 快照，不能作为准确总数。根目录 `source scripts/build/init-build-env.sh && mvnd -T4 -B pmd:pmd` 于 2026-11-23 15:20 完成后，37 份模块报告包含 339 条违规、分布于 23 个模块；所有报告均无 XML `<error>` 节点。common/auth-service/domain 后续修复后，当前 XML 快照为 327 条、21 个模块。P3C 控制台仍输出 XPath `maximum Iterations exceeded` 诊断；该全仓扫描仅用于定位，不表述为无诊断的完整规则覆盖。
- `apex-common`：违规从 32 条逐批降至 2 条后，保留公开 `VirtualThreadBoundedExecutor` API，通过内部调度契约类型避免 P3C 将自定义调度器误判为 Service/DAO；受影响 reactor 的单元测试、Spotless 与 PMD `check` 均通过，当前报告违规为 0。
- `apex-auth-service`：恢复 OAuth 端点实现到命名合规的 `OauthAuthorizationHandler`；将密码找回 OpenAPI 定义集中为组合注解，保留原单一 Bean 名 `passwordRecoveryRoutes`、三个请求体/响应模型、operationId 与语义描述。`AuthCenterOpenApiIntegrationTest` 8/8、`PasswordRecoveryHandlerIntegrationTest` 9/9、`AuthCenterPublicPathSecurityTest` 4/4 均通过；PMD `check`、Spotless 与定向构建成功，auth-service 当前快照无违规。过程中发现并修复拆 Bean 造成的测试装配问题。
- `apex-domain`：为 11 个领域接口方法补充准确的响应式完成语义 `@return`，未改变接口签名或执行语义。`-pl :apex-domain -am test spotless:check pmd:check` BUILD SUCCESS，PMD 违规归零。
- 本次 `pmd:pmd` 全仓扫描在 `apex-system`/`apex-auth` 输出 XPath 最大迭代次数诊断，虽然命令成功且 XML 未列 error。未修改规则或迭代阈值；需继续分模块处理并厘清受诊断影响的扫描完整性。
- 后续低风险批次补齐 file/integration/office/system/workflow 等领域端口与仓储接口的响应式完成信号 Javadoc；gateway 拆出开放 API 路由校验与常量并补齐访问日志仓储返回说明。gateway 及其依赖定向 `spotless:check pmd:check` 已通过。
- 后续补齐知识库领域的低风险大括号与枚举 Javadoc 后，最新 37 份 `target/pmd.xml` 汇总为 252 条、18 个模块，0 个 XML `<error>`；规则分布主要为魔法值 153、集合容量 24、复杂条件 22、switch 12。P3C 仍输出 `maximum Iterations exceeded` 诊断，且全仓 `verify` 尚未运行通过；BE-065 继续保持开放。

## 进展追加（2026-11-23 17:24）

- `TenantStatusSnapshotDTO` 将两个布尔契约字段改为包装类型，以满足 `PojoMustUsePrimitiveFieldRule` 的明确规则要求；通过显式无参构造和 Lombok builder 扩展继续保持缺省/显式 `null` 为 `false`，`RemoteTenantStatusAdapter` 使用 `Boolean.TRUE.equals` 防止空值传播。契约测试新增缺省、`null` 和 JSON 布尔形态断言。
- `apex-tools-deployment` 完成 74 条首批规则整改：静态常量、集合容量、空值安全 equals、复杂条件拆分、switch/线程池规则；文档链接检查改用 `CompletableFuture` 并发写入，不再手工创建线程；工具输出、路径安全和异常隐藏语义保持不变。定向测试、Spotless、PMD 均 `BUILD SUCCESS`，测试覆盖 8+9+6+9+10+13 项且无失败。
- 定向 `apex-api-contract` 编译、Spotless、PMD 已通过；全仓 `source scripts/build/init-build-env.sh && mvnd -T4 -B pmd:pmd` 于 17:24 完成，37 个 XML、176 条违规、0 个 XML `<error>`。剩余集中在 integration/system/auth/knowledge 等业务模块，主要为魔法值、复杂条件、集合容量、switch 与少量命名/Javadoc；P3C 仍输出 `maximum Iterations exceeded`，不能把扫描当作无诊断完整基线。
- 本轮尚未完成全仓 `verify`，未生成可宣称全仓通过的新测试报告；BE-065、BE-064、BE-063 继续执行中，未提交、推送、部署，独立 R5 仍待安排。
- `apex-infrastructure-remote` 首次全仓 verify 阻断的两个 `ClassMustHaveAuthor` 问题来自类 Javadoc 与注解间的孤立注释；移除孤立注释并保留配置语义，`-pl :apex-infrastructure-remote -am -DskipTests spotless:check pmd:check` BUILD SUCCESS。后续全仓 `pmd:pmd` 再刷新 37 个 XML、174 条违规、0 个 XML `<error>`；P3C XPath 最大迭代次数诊断仍在。
- 最新全仓 `mvnd -T4 -B verify` 已跨过本轮 api-contract、tools-deployment 修复，但在 `apex-infrastructure-remote` 两条违规处失败；该运行发生在远程设施源码修复之前，必须重跑才能验证后续阻断点。运行日志 `/private/tmp/apex-be065-full-verify-final.log`；未从混合模块旧报告生成新全仓测试报告。

## 进展追加（2026-11-24 integration 批次）

- 从 `apex-integration/target/pmd.xml` 旧快照读取到 48 条规则违规，覆盖连接器常量/集合容量、topic/身份解析、路由方法长度、Javadoc 与复杂条件。未更改规则或豁免清单；逐项以有语义名称的常量、按实际元素数量预设集合容量、提取条件及拆分路由职责整改。
- integration 改动覆盖 `CredentialReferenceResolver`、webhook/HTTP/创蓝/微信连接器、设备命令/规则服务、MQTT topic 策略/会话、IoT handlers 与 `IntegrationConnectorRouter`/`IotDeviceRouter`。两 Router 保留原 URI、HTTP 方法、Handler 绑定与 `@RouterOperation` 描述；IoT 路由拆为内部、设备管理、规则管理三个 RouterFunction Bean。HTTP 认证从 PMD 无法识别 default 的 switch 改为等价条件分支，未知认证类型仍抛 `PARAM_ERROR`。
- 一次 integration reactor 测试执行为 135 项、0 失败、0 错误、5 跳过，但当时 Spotless 检查未通过；格式修正后的运行再次执行 135 项时发现 `IotDeviceAutoConfigurationTest` 仍断言单个 RouterFunction Bean。已将断言改为检查 3 个子域路由；更新后的测试尚未复验。
- PMD 的最后一次已完成检查（路由拆分后、HTTP 认证类型常量收敛前）报告 3 条 `HttpConnector` 魔法值，已经提取为常量；IotDeviceRouter 方法长度违规已由拆 Bean 消除。该最后状态未能重新运行 PMD 确认归零。命令日志 `/private/tmp/apex-be065-integration-gates-final.log`；包含更新后的断言但在测试阶段失败的命令日志 `/private/tmp/apex-be065-integration-final-test2.log`。
- 重跑申请被自动审批服务以“Upstream access forbidden”拒绝且命令未执行；遵循审批约束，未改用别的执行路径。需审批通道恢复后重跑 `source scripts/build/init-build-env.sh && mvnd -T4 -B -pl :apex-integration -am test spotless:check pmd:check`。P3C 仍输出 `aktStatus is NULL: maximum Iterations exceeded`，诊断完整性待独立厘清。
- BE-065、BE-064、BE-063 继续开放；无全仓 `verify` 成功证据、无新全仓测试报告、无 R5 结论；未提交、推送或部署。

## 进展追加（2026-11-24 运行模块批次）

- 更正上节的后续状态：`/private/tmp/apex-be065-integration-resume.log` 及后续定向构建确认 integration 模块通过 `test spotless:check pmd:check`，135 项、0 失败、0 错误、5 跳过。office-service 与 workflow-service 的定向门禁亦已通过（`/private/tmp/apex-be065-wave-auth-service-pmd2.log`）。
- auth 库避开 macOS 大小写不敏感工作区的文件名与公开类型错配，统一 `OauthAuthorizationPort`、`OauthConsentPort`、`OauthConsentRepository` 与引用；在后续定向 reactor 内 auth 库 `SUCCESS`，1348 项、0 失败、0 错误、0 跳过。
- file-service、integration-service、knowledge-service、boot-all 按 13 条已定位报告项提取有语义名称的常量、补空 JWKS 枚举 Javadoc、按配置条目数预设公钥集合容量；不改变 JWT 验签行为、路由、配置格式或门禁。定向 reactor 通过前置模块（含 auth、integration、system），但 file-service 与 integration-service 各缺一处 Spotless 要求的空行，knowledge-service 和 boot-all 因依赖失败未执行；原命令日志 `/private/tmp/apex-be065-remaining-targeted.log`。两处空行已补，最终结论以 Wave E 隔离构建为准。
- 更正上文“XPath 诊断”的归因：本地 PMD 6.55.0 `pmd-core` JAR 的 `net.sourceforge.pmd.lang.dfa.SequenceChecker` 含该诊断字符串，属于旧数据流分析器。启用的 P3C 规则集中有八条 `dfa="true"` 声明，涉及事务与并发规则；日志未映射到具体源文件或规则，不能推断影响覆盖范围或误检/漏检数量。未静音日志、未改分析参数，后续独立复核保留这一风险。
- [Wave E](./2026-11-23-be063-wave-e-full-verify.md#2026-11-24-隔离全仓验证) 确认四个运行/装配模块全通过；首轮仅 auth-service 一处 Spotless 换行失败，修正后 19 模块续验全部通过。37 份 PMD XML 零违规、零 XML error；数据流诊断与独立复核仍是开放事项，不声明无诊断覆盖。

## 2026-11-24 独立复核与整改

- Hilbert 首轮 `REJECT`：MySQL 工具的公共线程池可能在子进程管道 I/O 下耗尽，OAuth 端口 record 与 IoT 路由 Bean 缺少 Javadoc。文档注释已补齐；虚拟线程工厂的初次整改经定向执行仍触发 P3C `ThreadPoolCreationRule`，没有增加豁免。
- Curie 首轮 `REJECT`：三个显式专用工人解决公共池争用，但 `Process.destroyForcibly()` 会同步关闭流，命令包装器的后代进程继承且不读 stdin 时可能拖住超时清理；OAuth 同意仓储类注释缺有效块标签。最终实现使用三个专用 daemon 工作者并发处理三路管道，超时由 `ProcessHandle` 发送终止信号，不在调用线程关闭流；补齐块标签。CLI 出错仍隐藏子进程输出。
- 固定工具链在 `/private/tmp/apex-wave-e-final-20261124.u4XOBS` 执行 `mvnd -T4 -B -pl :apex-tools-deployment -am test spotless:check pmd:check`：`BUILD SUCCESS`，89 项通过，无失败、错误或跳过；三个 MySQL 工具回归覆盖大双输出与大输入、失败不回显、包装进程持有 stdin 时按期返回。原始输出 `tools-final.log`，PMD 未报告阻断违规；旧数据流分析诊断保留。
- Curie 增量复核 `APPROVE_WITH_NITS`，无必改项。该回归只证明调用有界返回，不证明父进程先退出后所有被重新托管的后代均可跟踪和回收。另核实 HTTP 契约与 Java 构件兼容性是两项不同验收；新旧 JAR 不能混用，发布前必须按[规格](../specs/2026-11-23-be065-pmd-rule-repair.md#兼容性)落实版本区分与成套发布或提供兼容入口。本轮不发布。
- [最终单次全仓验证](./2026-11-23-be063-wave-e-full-verify.md#2026-11-24-最终单次全仓验证) 45/45 模块通过，37 份 PMD XML 零违规、零 XML error；旧数据流最大迭代诊断仍在。局部整改复核和本地门禁不替代整个 BE-065 改动的最终终审、构件兼容验收与集成。

## 2026-11-24 未关闭风险分诊

- 对最终隔离快照 `/private/tmp/apex-wave-e-accepted-20261124.65m9GF/verify.log` 复核，找到 518 行 `aktStatus is NULL: maximum Iterations exceeded`。本地 PMD 6.55.0 的 `SequenceChecker` 实现包含该诊断；仓库启用的 P3C 规则中有 8 条声明 `dfa="true"`。日志没有给出对应源文件或规则，故 37 份 XML 零违规不能证明受影响数据流规则已完整分析。它不阻断本次构建，但属于分析完整性风险，必须保留为 BE-065 未关闭项；不得静音、提高迭代上限或缩小规则范围来制造通过结论。
- 当前根 POM 与相关构件仍为 `1.3.1`。BE-065 变更包含 Java ABI 不兼容项；本地全仓编译只证明当前仓消费者可共同重编译，不证明旧二进制消费者兼容。由于尚未授权或执行发布，本轮不改版本号、不重写兼容层；任何发布前必须选择并验证规格中规定的可区分构件版本成套发布，或补旧 ABI 兼容入口及旧调用方回归。不得覆盖既有 `1.3.1` 制品。
- FE-030 候选没有生产采纳依据：既有报告显示登录页解码 JS 减少 12.5%，但认证页各多加载 9 个 JS 请求，当前证据不是目标用户旅程 p95。应在候选隔离副本、同一锁定构建和冷缓存/高延迟条件下测量“提交登录至工作台可交互”基线与候选 p95，再据数据决定；本轮不改前端仓库或提升候选。
- BE-063 的 D2 规格复核通过仅代表设计准入。真实集群蓝绿切流/回切、灰度放量、负载均衡及容量/SLO 尚未实测；需要独立隔离集群与事先登记演练租约，当前不操作集群、不宣称已验收。BE-043、BE-049、FE-029 遗留仍留原卡。
- **本轮调整结论**：上述问题均为发布/分析/运行验收前置项，而非应在本地盲目改源码的明确缺陷。唯一可安全处理的是把证据、判定和关闭门槛固化到现有任务记录；未改业务代码、PMD/P3C 配置、构件版本、前端候选或运行环境。

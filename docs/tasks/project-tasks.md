# 项目任务大盘 · apex-scaffold

> **唯一可信源**。仅本任务指定的协调智能体（R0，可由用户指定任意 AI 或人）可编辑；执行智能体只读，完成后以标准交接包回报 R0。未指定 R0 时不得启动并行写入。
> 优先级：`P0` 线上阻断 · `P1` 当期必做 · `P2` 后续排期 · `P3` 技术债/优化
> 状态：`待规划｜执行中｜待复核｜已完成｜搁置`
> 记录日期：2026-11-16（治理基线建立；BE-001 补导出后为 `docs/` 1302 文件 = 715 md + 247 svg + 68 mmd + 139 json + 133 其他，图表配对缺口 0）；2026-11-22 BE-045~BE-048 登记时复核：`docs/` 1387 文件 = 800 md + 247 svg + 68 mmd + 139 json + 133 其他，图表配对缺口 0；2026-11-28 BE-066 登记时复核：`docs/` 1451 文件 = 832 md + 263 svg + 84 mmd + 139 json + 133 其他，图表配对缺口 0

## 1. 活跃任务

| ID | 优先级 | 任务 | 状态 | 负责 | 验收标准 | 关联 |
| --- | --- | --- | --- | --- | --- | --- |
| BE-049 | P1 | 【P1】代码-全模块设计模式/SPI/lambda 增量复审与兼容性优化（含测试报告生成链修复） | 执行中 | R0/执行：Codex（用户 2026-11-23 授权接管；保留既有提交及 R5 结论，不覆盖、不关闭） | 全 45 个 reactor 项目按根 POM 递归覆盖评审（共享、组件/业务库、starter、运行模块、装配、工具）；只做有证据的兼容性优化：SSO 协议分派合并、Meilisearch 资源分派收敛、内部端点错误响应去重、SPI 真实描述符发现测试、网关撤销缓存保留期修正、数据权限 WHERE 括起、Outbox 领取行数校验；不改 HTTP 契约、模块坐标、Flyway、安全与事务语义；修复测试报告生成器旧路径/硬编码/仅 Surefire 缺陷并补测试；同步受影响文档/配置核对/台账并重生成报告；**既有结果**：SSO 协议注册表、Meilisearch 资源注册表、`InternalEndpointErrors` 抽取、网关撤销保留期 7200s、数据权限 OR 括起、Outbox 抢占校验、存储 SPI 真实描述符测试、报告生成链重写（45 模块递归+Surefire/Failsafe 分列+XXE 禁用）；历史全量 `clean verify` 45/45 SUCCESS、6740/0 失败/33 跳过；独立 R5 三轮复核 **APPROVE_WITH_NITS**、MF-01~MF-06 与 N-10~N-15 清零。**2026-11-24 接管核对**：核心实现 `9a59ee2e`、报告工具链 `c41f4fa1`、评审/报告 `4118155c` 均已核实在历史中；历史 R5 不覆盖接管后的最终 diff。多实例 Outbox 抢占、撤销流全链路及秒级批边界未验证，且需新独立 R5 绑定最终改动与任务范围后才能评估状态；保持执行中，不并入 BE-063 | [记录](../records/2026-11-22-code-review-pattern-spi-lambda.md)；[评审清单](../reviews/2026-11-22-full-module-code-review.md)；租约见下；原基线 `b81acf18`；接管基线 `90a2f783` |
| BE-063 | P1 | 【P1】全仓中台韧性、DDD 与发布能力分波次评审优化 | 执行中 | R0/执行：Codex（用户 2026-11-23 授权，Wave A 只读审计）；独立 R5：Pauli（子代理 `01a2066c-e436-7b43-b7e3-a076beab7ce5`，不参与执行） | 以当前 `dev` 全量模块为范围，先建立证据化架构/容量基线，再按批评审 DDD 边界与依赖、缓存一致性/失效、熔断重试/超时/隔离、限流与背压、基础设施可观测性、灰度/蓝绿/负载均衡；每项先登记风险、SLO/基线、回滚与定向测试，再决定是否改动；任何接口/模块/迁移/路由/运行拓扑变更先过规格评审；按受影响模块测试并重新生成报告，真实集群/压测不可用时明确标未验证，不得虚构结果 | [方案](../specs/2026-11-23-platform-resilience-ddd-release.md)；[计划](../plans/2026-11-23-platform-resilience-ddd-release.md)；[Wave A 基线评审](../reviews/2026-11-23-be063-wave-a-baseline.md)；旧任务 BE-043/BE-049 独立保留 |
| BE-064 | P1 | 【P1】构建门禁修复：PMD Java 21 字节码兼容与数据源超时测试稳定性 | 待复核 | R0/执行：当前 Codex 会话；独立 R5：Curie 已批准静态整改 | 插件专用 ASM 已兼容 Java 21，握手超时用例锁定 JDBC 配置和真实根因。定向验证、最终单次全仓验证和同轮报告均通过；Curie 批准握手测试整改增量（未覆盖整包最终 diff）。汇总发布提交 `4a0d6782` 包含相关改动，但尚无 BE-064 任务专属集成提交及整包最终 R5；保持待复核。 | [规格](../specs/2026-11-23-be064-build-gate-repair.md)；[执行记录](../records/2026-11-23-be064-build-gate-repair.md)；BE-063 Wave E |
| BE-066 | P1 | 【P1】对齐-全仓文档/脚本/配置目录复核与文档·图表一致性评审增强（2026-11-28 战役） | 已完成 | R0/执行：ZCode（用户 2026-11-28 直接授权，串行单写者）；独立 R5：独立子代理（2026-11-28 复核，不参与执行） | **结果**：①目录复核合规（根级仅登记转发壳/MEMORY-CARD，无搬迁必要）；②四切片（A 模块架构 2P0+7P1+8P2 / B API 契约 1P0+9P1+4P2 / C 配置运维 5P0+9P1+15P2 / D 脚本索引 1P0+5P1+7P2）采纳项全部修复，2 处评审误报经复核未采纳；③时序图 90/91 出 `_v2` 成对 + sequence/README 登记，基线 svg 263→265 / mmd 84→86；④18 个带日期历史件补横幅 + 4 行索引标注（记录 §2.6 台账）；⑤新工单 BE-067~070。**独立 R5：APPROVE_WITH_NITS**，MF-01 与 N-01~N-03 已全部清零。**集成提交（一类一提交，全部含 memory: #129）**：`a9b07470` 文档对齐 / `fa7fa6ad` 图表 v2 / `da67d7dc` 脚本 / `45079952` 配置 / `76dfb049` 治理收尾 / 本笔哈希回填。**关闭条件核验（§0.9.8）**：R5 结论✓、集成 commit✓、治理校验（verify-docs --strict 0 项、check-doc-links 4089/0）✓、records 记录及索引✓、台账 #129✓、回滚方式（git revert）✓ | [执行记录](../records/2026-11-28-be066-alignment-campaign.md)；[前次战役](../records/2026-11-22-alignment-campaign.md)；图表保护基线 svg=265/mmd=86 |
| BE-067 | P3 | 【P3】system-清理 interests 死白名单与死限流规则 | 已完成 | R0/执行：ZCode（用户 2026-11-28 授权，串行单写者；基线 `07a93cbf`） | 现状：全仓无 Router/Handler 注册 `/internal/v1/interests/**`，但 `SecurityConfig`/`JwtAuthenticationFilter` 安全链白名单与 `SentinelConfig` 的 `/api/v1/same-frequency/interests/feed` 限流规则仍按路径特判（BE-066 静态核实，见 internal-api-contracts.md §2）；验收：确认无外部契约依赖后删除死白名单与死规则并补安全链回归，或如属规划中能力则登记规格；不得在评估前直接改动。**结果（2026-11-28）**：两分支匹配器/forceFreshRoles 布尔链/interests-feed 规则已删，新增缓存反转回归；连带 Sentinel 模板 JSON/断言/README/configuration 计数 43→42；apex-system 1812/0/0 BUILD SUCCESS | [执行记录](../records/2026-11-28-be067-070-remediation.md)；[规格](../specs/2026-11-28-be067-070-followup-remediation.md)；internal-api-contracts §2 已更新 | 测试全绿；R5 **APPROVE_WITH_NITS**，MF/N 已清零（执行记录 §5）；集成提交 `659bd5d7`，§0.9.8 关闭条件齐备，**已关闭** |
| BE-068 | P2 | 【P2】aggregation-修正 todo 配置前缀死键 | 已完成 | R0/执行：ZCode（同 BE-067 租约） | 现状：`apex-aggregation-service/application.yml` §aggregation.todo 写在 `aggregation.todo.*` 前缀下，而 `TodoProperties` 绑定 `apex.todo`，该块配置不会被绑定（BE-066 静态核实），运行默认实际来自代码（sources 8093/8084、timeout 3000、bulkhead 8）；验收：yml 块改名为 `apex.todo.*`（或删除由代码默认兜底）并补绑定生效断言，`:apex-aggregation-service -am test` 0 失败。**结果（2026-11-28）**：todo 块迁至 apex.*，新增 TodoPropertiesYmlBindingTest（哨兵值+占位符解析，正反两断言）；28/0/0 BUILD SUCCESS | [执行记录](../records/2026-11-28-be067-070-remediation.md) | 测试全绿；R5 **APPROVE_WITH_NITS**，MF/N 已清零（执行记录 §5）；集成提交 `659bd5d7`，§0.9.8 关闭条件齐备，**已关闭** |
| BE-069 | P2 | 【P2】system-prod 模板管理端口默认与 8083 惯例对齐 | 已完成 | R0/执行：ZCode（同 BE-067 租约；规格已出） | 现状：共享 prod 模板 `apex-components-parent/apex-system/src/main/resources/application-prod.yml:443` 写 `management.server.port: ${MANAGEMENT_SERVER_PORT:8081}`，而 `apex-system-service/application.yml` 为 8083，compose healthcheck/k8s/文档均按 8083（BE-066 静态核实）；两层同名 application*.yml 的 classpath 取舍未在文档说明；验收：模板默认改 8083 或显式依赖服务模块配置，并在 configuration 文档标明实际生效来源与两层取舍；涉及端口语义，须先出规格评审。**结果（2026-11-28）**：规格先行（联合规格 §3）后模板默认改 8083+注释修正；连带 ProductionReadinessConfigurationTest 断言与 deployment.md §7.6 两层取舍说明；apex-system 1812/0/0 | [执行记录](../records/2026-11-28-be067-070-remediation.md) | 测试全绿；R5 **APPROVE_WITH_NITS**，MF/N 已清零（执行记录 §5）；集成提交 `659bd5d7`，§0.9.8 关闭条件齐备，**已关闭** |
| BE-070 | P3 | 【P3】仓库根-`monitoring/` 重复目录清理 | 已完成 | R0/执行：ZCode（同 BE-067 租约；用户 2026-11-28 指令即删除确认） | 现状：根级 `monitoring/`（prometheus/alertmanager/grafana/compose，已入库）与权威目录 `config/monitoring/` 内容重复且不一致，全部文档只引用后者（BE-066 全文检索 0 引用）；验收：确认无脚本/部署引用后 `git rm` 根级目录并留痕，或加 README 指向权威目录；删除属破坏性操作须独立确认。**结果（2026-11-28）**：复扫零引用后 git rm 7 文件，用户指令即确认 | [执行记录](../records/2026-11-28-be067-070-remediation.md) | 已执行；R5 **APPROVE_WITH_NITS**，MF-01 已清零（规格/记录口径修正 + 13 号图并入 BE-071）；集成提交 `14803981`，§0.9.8 关闭条件齐备，**已关闭** |
| BE-071 | P2 | 【P2】system-SentinelConfig 静态基线全量审计 | 待复核 | R0/执行：ZCode（用户 2026-11-28 授权继续处理后续，串行单写者；基线 `cd3e64b7`） | 现状：42 条流控规则中初步扫描显示大部分路径无路由注册（同频组其余 6 条、media/ranking/explore/future-partner/moderation/onboarding/profile 等退役业务域组），`/api/v1/auth/**` 组还指向 auth-service 现行前缀 `/auth/v1/**` 以外的旧前缀（BE-067 规格评审顺带发现，启发式扫描需逐条证实）；**范围扩展（R5 MF-01/MF-02）**：含 `docs/diagrams/13_docker_compose_local_dev.svg`（根级 monitoring 挂载路径引用已失效）与 `docs/diagrams/44_security_protection_layers.svg`（「43 条限流规则」计数残留）两张活图按 §0.3 出 `_v2` 成对修订，及计数终值确认后的活文档回写；验收：逐条对照现行网关路由表与各服务 Router 出证据清单后删改，并重写类头 Javadoc 分组表；不得在逐条证据前批量删除。**结果（2026-11-28）**：字面量精确判定流控 8 存活/34 死、降级 2/16；SentinelConfig 手术 + 模板 JSON 同步 + 计数终值 8/2 回写 5 处活文档 + 13/44 号图 `_v2` 成对；apex-system 1810/0/0 BUILD SUCCESS、check-doc-links 4096/0 | [联合规格](../specs/2026-11-28-be067-070-followup-remediation.md) §6；[R5 报告要点见执行记录](../records/2026-11-28-be067-070-remediation.md) | 启发式扫描，未逐条证实 |
| BE-065 | P1 | 【P1】PMD Java 21 兼容后暴露的全仓规则违规逐项修复 | 执行中 | R0/执行：当前 Codex 会话单写；独立 R5：Curie 完成局部整改复核 | 未禁用规则、未扩大排除清单；最终单次全仓验证 45/45，37 份 PMD XML 零违规、零 XML error。最大迭代诊断、Java 构件兼容、整包终审和集成仍需单独处理。 | [规格](../specs/2026-11-23-be065-pmd-rule-repair.md)；[记录](../records/2026-11-23-be065-pmd-rule-repair.md)；[Wave E](../records/2026-11-23-be063-wave-e-full-verify.md)；BE-064 |
| BE-050 | P2 | 【P2】安全-网关 JWKS 快照淘汰已下线 kid | 待规划 | R0 待指定 | 现状：`GatewayJwtVerifierService` 拉取 JWKS 仅 `putAll` 合并，从不清除已下线 `kid`，密钥轮换后旧公钥长期驻留内存；验收：确认真实轮换窗口后实现淘汰（保留启动兜底与拉取失败回退语义），补「旧 kid 被移除」与「拉取失败保留旧快照」正反向用例，`:apex-gateway -am test` 0 失败 | [评审 C1](../reviews/2026-11-22-full-module-code-review.md) | 静态发现，未复现 |
| BE-051 | P2 | 【P2】缓存-多级缓存 L1/L2 TTL 与 stale 语义评估 | 待规划 | R0 待指定 | 现状：`apex-infrastructure-cache` 的 L1 TTL 长于 L2，`stale` 语义不可实现（评审 C2）；验收：先取真实部署配置确认是否成立，给出结论文档；如需改行为须先出规格并附一致性回归用例，`:apex-infrastructure-cache -am test` 0 失败 | [评审 C2](../reviews/2026-11-22-full-module-code-review.md) | 静态发现，未复现 |
| BE-052 | P1 | 【P1】office-内部未读列表先过滤再分页 | 待规划 | R0 待指定 | 现状：`OfficeInternalHandler.unreadMessageSection` 用 `getInbox(userId, 1, limit)` 后过滤未读，未读集中在后面时返回条目少于 limit（评审 C3）；验收：为 `OffMessageService` 增加未读查询端口（属契约变更，须先出规格），改为服务端过滤分页；补「未读集中在尾页」「全未读/全已读」用例，`:apex-office` 与 `:apex-office-service -am test` 0 失败 | [评审 C3](../reviews/2026-11-22-full-module-code-review.md) | 需契约变更，须独立评估 |
| BE-053 | P3 | 【P3】认证-六份 IdentityRelayAuthenticationFilter 验签内核差异矩阵与抽取评估 | 待规划 | R0 待指定 | 现状：六份过滤器验签内核同构，但开放应用、HMAC 开关、文件访问上下文、office WebSocket 票据各域分支不同（评审 C4）；验收：先产出完整差异矩阵文档并确认无契约漂移，再决定是否抽取共享内核；抽则须补六份正反向回归 | [评审 C4](../reviews/2026-11-22-full-module-code-review.md) | 已确认不可整体合并 |
| BE-054 | P3 | 【P3】office-普通用户分支重复 RSA 验签消除 | 待规划 | R0 待指定 | 现状：`office-service` 的 `IdentityRelayAuthenticationFilter` 普通用户分支先 `verifyClaims(authorization)` 再 `verify(authorization)`，对同一令牌做两次 RSA 验签（评审 C5）；验收：局部复用已验 Claims 且保持令牌类型/租户校验语义，补往返用例，`:apex-office-service -am test` 0 失败 | [评审 C5](../reviews/2026-11-22-full-module-code-review.md) | 静态发现，收益为性能 |
| BE-055 | P1 | 【P1】auth-内部请求工具错误响应安全评估 | 待规划 | R0 待指定 | 现状：`auth-service` 的 `RequestHandlerUtil` 将技术异常原文回传客户端，未知业务码映射 400（评审 C6）；验收：安全评审后统一为不透出内部细节的错误响应，明确 HTTP 状态与业务码契约；须同步受影响契约测试与 `docs/03-api/` 说明 | [评审 C6](../reviews/2026-11-22-full-module-code-review.md) | 涉及错误契约与信息暴露 |
| BE-056 | P3 | 【P3】generator-dbType/javaType 映射表驱动化评估 | 待规划 | R0 待指定 | 现状：`apex-generator` 的 `CodeGenerator`/`FieldDefinition` 用 switch 做类型映射（评审 C7）；验收：确认表驱动对生成器输出稳定性无影响再改，改动须保证生成结果逐字节不变并补对照用例 | [评审 C7](../reviews/2026-11-22-full-module-code-review.md) | 收益有限，属输出稳定性范围 |
| BE-057 | P3 | 【P3】tools-MysqlClient 写入阶段超时约束 | 待规划 | R0 待指定 | 现状：`apex-tools-deployment` 的 `MysqlClient` 先写 SQL 再 `waitFor`，写入阶段不受 90 秒硬超时约束（评审 C8）；验收：为写入阶段补同等边界并补离线工具回归；仅影响离线运维，不涉服务运行期 | [评审 C8](../reviews/2026-11-22-full-module-code-review.md) | 离线工具，影响面小 |
| BE-058 | P3 | 【P3】search-索引手机号脱敏口径确认 | 待规划 | R0 待指定 | 现状：`MeilisearchAdapter` 私有 `maskPhone` 对非 11 位输入原样返回，与公共 `DataMaskUtil` 边界不同（评审 C9）；验收：确认产品口径后统一或显式保留；若改动须补索引内容断言，避免索引中出现未脱敏号码 | [评审 C9](../reviews/2026-11-22-full-module-code-review.md) | 需确认产品口径 |
| BE-059 | P2 | 【P2】auth-撤销流秒级 revoked_at 批边界丢记录 | 待规划 | R0 待指定 | 现状：`revoked_at` 为 MySQL 秒级 `TIMESTAMP`，撤销流用 `revoked_at > since`；单批被 `limit` 在末秒截断时，同秒剩余记录不会被后续批返回（评审 C10）；本轮「游标回退 1 秒」已被既有测试证伪并回退；验收：改用 `(revoked_at, id)` 复合游标或将时间戳升为毫秒精度（须先出规格并迁移），补同秒跨批用例，`:apex-auth` 与 `:apex-auth-service -am test` 0 失败 | [评审 C10](../reviews/2026-11-22-full-module-code-review.md) | 属契约/迁移变更，须独立评估 |
| BE-060 | P1 | 【P1】文档-全模块 Handler 注解补齐 | 待复核 | R0/执行：ZCode（用户 2026-11-22 授权，串行单写者） | 四档 100%（Router 77/77、Handler 516/516、DTO 212/212、写操作 60/60）。**2026-11-24 独立 R5 复核 REJECT**，两项必改已修复：写操作 `@RequestBody` 未纳入通过判定（可退化至 0% 而 CI 仍绿，已改为阻断）、`write_routes` 死代码；另修两处 bash 3.2 多字节变量崩溃并新增组合注解（元注解）识别，修复后实测四档 100%/`exit 0`。**遗留未关闭**：DTO 自动发现范围遗漏 5 个模块共 58 个 DTO、6 个 `handler::method` 路由不被第 4 项检查覆盖、`dto_missing<5` 容忍阈值；未运行全仓 verify、未核验实际 Swagger 渲染。仍缺任务专属集成提交 | [记录](../records/2026-11-22-handler-annotation-completion.md)；[模板](../03-api/springdoc-annotation-templates.md)；[审计口径](../03-api/springdoc-annotation-audit.md) | 基线 `71e39a4f`；R5 **REJECT**→必改已修；复核遗留登记于记录 §2026-11-24 |
| BE-061 | P3 | 【P3】office-`@IdempotentApi` 无切面失效评估 | 待规划 | R0 待指定 | 现状：`OfficeAdminHandler` 5 处、`OfficeMessageHandler` 3 处标注 `@IdempotentApi`，但 office 模块无对应切面且不依赖 `apex-system`，注解完全不生效（BE-060 静态核实）；验收：确认是否补 `IdempotentApiAspect`（会让写操作新增幂等键校验，属运行行为变更，须先出规格与回归）或移除失效注解；不得在评估前直接改动 | [记录](../records/2026-11-22-handler-annotation-completion.md) §6.2 | 静态发现，未复现 |
| BE-062 | P3 | 【P3】认证/文件/脱敏-`@RequirePermission` 无切面确认 | 待规划 | R0 待指定 | 现状：auth-service、file-service、masking-service、gateway 均无权限切面，走 `IdentityRelayAuthenticationFilter` 链路；在这些模块新增 `@RequirePermission` 不生效（BE-060 因此未新增该类注解）；验收：确认鉴权责任边界（网关 scope 断言 vs 服务侧切面）并形成结论文档，决定是否需要切面 | [记录](../records/2026-11-22-handler-annotation-completion.md) §6.3 | 静态发现，未复现 |
| BE-045 | P1 | 【P1】对齐-文档/脚本/配置/测试报告同步当前代码 | 已完成 | R0/执行：ZCode（用户 2026-11-22 授权，串行单写者） | P0 事实错误清零：`docs/03-api/next-steps-guide.md` 退役模块现役口吻、`docs/06-testing/TEST_REPORT.md` workflow 端口、`docs/01-guides/messaging/` 补 BE-044 MQ 本地事务处理器默认装配语义、SPI/设备规则指南补 ConnectorLoader 与 DeviceRuleEngineService 现行语义；P1：`TEST_EXECUTION_GUIDE.md` 工具链（JDK 21.0.13.0+9-tem/mvnd）与模块路径对齐、`06-testing/README.md` 历史报告过期标注并新增 2026-11-22 全量验证小节、带日期历史件加历史快照横幅、`docs/README.md` 页脚与任务盘基线注刷新；配置 `apex-system-service application.yml` Sentinel-Nacos 兜底地址与全局口径对齐（经用户确认）；门禁见租约；**结果**：指南两处语义补齐、06-testing 导航+全量验证小节、页脚/基线注刷新完成；四处旧口径经核验为已有横幅豁免；18848 经查为本地 infra 刻意约定不改；check-doc-refs 断链 0 | [记录](../records/2026-11-22-alignment-campaign.md)；集成提交 `acc23a31`；R5 APPROVE_WITH_NITS、MF-01~03 清零，治理校验全绿，**已关闭** | 基线 `8d8dc31c` |
| BE-046 | P2 | 【P2】图表-既有图核对与 `_v2` 修订 | 已完成 | R0/执行：ZCode（同 BE-045 租约，串行单写者） | ER 图 111/114/121（及 112/113 抽核）对照当前 Flyway 迁移核对，漂移者新增 `_v2` 源+导出成对并在 `docs/records/` 写差异与受影响引用；MQ 相关图核对 BE-044 语义；95/128/129/124/125/127 与根 POM/实现确认同步；`sequence/README.md` 补 90–95 六条登记；01/93/94 历史快照不改；**结果**：五张 ER 图/迁移一致（无 _v2）、MQ 图无冲突、95/128/129 已同步；README 计数注刷新（system 100/office 19/跨域 142）；sequence README 补 90–95 | [记录](../records/2026-11-22-alignment-campaign.md)；集成提交 `8035bbe4`；R5 APPROVE_WITH_NITS、MF-01~03 清零，治理校验全绿，**已关闭** | 基线 `8d8dc31c` |
| BE-047 | P1 | 【P1】图表-新增 16 张补缺图（130–145） | 已完成 | R0/执行：ZCode（同 BE-045 租约，串行单写者） | 新增：当前运行拓扑系统架构图 130；integration 功能/ER/设备时序/连接器流程 131–134；knowledge 功能/ER/摄取流程/RAG 问答时序 135–138；masking 架构 139；boot-all 装配 140；job 141；seata 模式 142；storage SPI 143；office 站内信流程 144 与审批编排时序 145；全部 `.mmd`+`.svg` 成对（mermaid-cli v10 渲染）、theme:base 四色；登记 `docs/diagrams/README.md`、`docs/02-architecture/architecture-index.md`、时序入 `sequence/README.md`；shared 五能力 JAR 与非 license starter 无业务 ER 豁免登记；**结果**：16 张 .mmd+.svg 全部渲染成功（mermaid-cli 10，含 2 处语法修复）；三处索引登记完成；SVG 247→263、MMD 68→84 | [记录](../records/2026-11-22-alignment-campaign.md)；集成提交 `d64a2255`；R5 APPROVE_WITH_NITS、MF-01~03 清零，治理校验全绿，**已关闭** | 基线 `8d8dc31c` |
| BE-048 | P1 | 【P1】注释与规则文档/扫描脚本修复 | 已完成 | R0/执行：ZCode（同 BE-045 租约，串行单写者） | `FileAuditFieldFillHandler.java` 类注释补 `@author`/`@since`（全仓 main Javadoc 扫描归零）；`AGENTS.md` 0.5-2 BE-004 措辞修正为例外实为 `@RestControllerAdvice`（附录 A 原文与哈希不动）；`docs/specs/project-governance.md` §6 B6（Logger 违规已清零）/B7（补验证口径）更新；`scripts/dev/check/scan-javadoc-issues.sh` 默认 `JAVADOC_SCAN_PATH` 改两级嵌套并同步 scripts/README 与 development-standards 用法说明；`github-branch-protection.md` 「无 CI 可挂」段落刷新；`check_javadoc.sh` 加废弃标注；`audit-backend-rules.sh --strict` 通过；**结果**：注释补齐后扫描四类 0 问题；AGENTS 0.5-2/B6/B7/脚本默认路径/CI 段/废弃标注全部落地；全量 clean verify BUILD SUCCESS 45/45、Tests 6716/0 失败/33 跳过 | [记录](../records/2026-11-22-alignment-campaign.md)；集成提交 `3964a8d3`+`23360fc3`；R5 APPROVE_WITH_NITS、MF-01~03 清零，治理校验全绿，**已关闭** | 基线 `8d8dc31c` |
| BE-044 | P2 | 【P2】全仓设计模式 / SPI / 注释复审与整改 | 已完成 | R0/执行：Trae（用户 2026-11-22 授权，串行单写者；与 BE-043 共用 `docs/README.md`、任务盘与台账，采用追加式写入） | Wave A–F 已落地（F 仅结论）；变更记录 [2026-11-22-code-quality-pattern-spi.md](../records/2026-11-22-code-quality-pattern-spi.md)、台账 #73 已登记；存储/消息/集成/知识库及 system-service、auth-service `-am test` 全绿；Wave G 按用户追加授权修复并通过 integration 测试。首轮 R5 `REJECT` 的两项必改已修正，独立 R5 再审 `APPROVE`；修复后全量 `mvnd -T4 -B clean verify` BUILD SUCCESS（45/45 项目，13:24）。代码任务提交 `43e33baf`（Refs: BE-044）；任务记录、索引与台账收尾提交 `8d8dc31c`；现已逐项核实记录、R5、验证、集成提交及分支历史。 | [计划](../plans/2026-11-22-code-quality-pattern-spi-comment-plan.md)；[记录](../records/2026-11-22-code-quality-pattern-spi.md)；BE-044 集成 `43e33baf`、治理收尾 `8d8dc31c`；R5 `APPROVE`；台账 #73/#79/#125 |
| BE-043 | P1 | 【P1】联调-全量 API 契约核验与 Podman Compose 真实链路 | 执行中 | R0/执行：Codex（用户 2026-11-23 授权接管；保持旧任务开放） | R3 方案评审 `integration_plan_review`：最终结论 APPROVE（2026-11-18，确认移除不存在的 integration profile、唯一 Compose 入口及执行边界）；与 FE-029 对照后端执行路由/DTO/权限/分页与真实响应；只修真实证据确认的缺陷并补定向测试；Podman 栈服务名/健康/网络反代可用；测试、spotless、治理检查和 R5 独立复核通过后关闭；用户授权仅接管责任与租约，不豁免 reader、知识库模型或独立 R5 等未完成验收 | [方案](../specs/2026-11-18-fullstack-integration.md)；旧基线 BE `b177605d1d1482470945b8a48183103655bf3d18` / FE `f03bb6883a0f05be9cb4fda836062db55f6e4460`；接管基线 BE `90a2f783` / FE `aec9c444`；FE-029 |
| BE-042 | P1 | 【P1】治理-落地多智能体并行协作与交接规则 | 已完成 | R0/执行：Codex（同一会话，串行单写者）；首轮 R5：Chandrasekhar；增量 R5：Erdos（独立子代理 `01a1ed8c-02cb-7300-a949-cca27d03f6fa`） | 主控/执行/复核分责、任务租约与认领、并行准入、单写者与写入范围、Worktree 隔离、标准交接包、合并冲突升级、异常接管与关闭条件均写入 `AGENTS.md`、治理正文、R0–R5 角色卡和任务入口；文档引用、后端红线、预检与记忆台账通过；`verify-docs.sh --strict` 0 项；附录 A 哈希不变；独立 R5 增量结论 `APPROVE_WITH_NITS`，MF-01–MF-05 清零；最终关闭提交及三支同步见本任务提交历史 | `docs/specs/project-governance.md` §2/§4；`docs/specs/ai-agents/`；`docs/records/2026-11-18-multi-agent-governance.md`；台账 #53/#54/#55；[R5 评审报告](../reviews/2026-11-18-be-042-r5-review.md) |
| BE-041 | P2 | 【P2】全仓 Javadoc 评审与补齐战役（红线 B7 收口） | 待复核 | ZCode（用户于 2026-11-18 授权） | 双工具评审：扫描器四类清单全 0（类级历史战役已收口）+ JDK doclint 全反应堆 78 告警→0（53 失效引用/24 裸字符/1 @throws/1 注释错挂，53 文件纯注释修复）；`@param/@return/@throws` 全量 0 缺失；零逻辑改动；spotless 通过；三批 `-am test` 1028/4848/4205 用例 0 失败；全量 `clean verify` 43/45 模块 SUCCESS（2 个环境性失败均 -T1 单跑复核为绿：example 计时抖动、boot-all 8080 被既有网关占用）；记录 [2026-11-18-javadoc-review-campaign](../records/2026-11-18-javadoc-review-campaign.md)；台账 #52。已知工具限制：messaging javadoc 目标因 Lombok `onMethod_` 不兼容；顺带发现扫描器默认路径已被 11-14 嵌套破坏（另立任务） | 治理规范 B7；历史战役（2026-09 javadoc-work-summary） |
| BE-040 | P1 | 【P1】根治 office/auth 的 FieldFill 失效（MetaObjectHandler 未装配） | 待复核 | ZCode（用户于 2026-11-17 授权） | 两模块各装配 `AuditFieldFillHandler`，office/auth 测试通过；真实环境验证日历创建时间由填充器写入。**2026-11-24 独立 R5 复核 REJECT**，MF-1/MF-2 已修复：office 填充器同时覆盖 `createTime/updateTime` 与 `createdAt/updatedAt` 两套命名（新增 2 条审批流程用例），且日历/文档/审批撤销三处更新路径显式刷新 `update_time`；`:apex-office` 204 项 + Spotless BUILD SUCCESS。**未修**：MF-3 规格属性名说明、MF-4 auth MFA/TOTP 本地 H2 持久化用例、台账 #51 提交列、handler 条件装配建议、测试显示名。保持待复核 | BE-039 遗留项；[规格](../specs/2026-11-17-meta-field-fill.md) §2026-11-24 |
| BE-039 | P1 | 【P1】office-平台基址可配置并完成日历写操作端到端验证 | 待复核 | ZCode（用户于 2026-11-17 授权） | 三平台客户端基址支持 `apex.office.platforms[*].base-url` 覆盖（缺省保持官方地址，不得改变生产行为）；用本地模拟平台完成 创建→详情→更新→删除→范围查询 全链路真实验证；回归用例覆盖基址覆盖与缺省；`:apex-office` 199 + `:apex-office-service` 406 用例 0 失败、spotless 通过；**本地模拟平台端到端 6/6**（创建/详情/更新/范围/删除/删除后为空）。过程中修复三个落库缺陷（create_time、update_time、逻辑删除未生效） | BE-037/BE-038 未验证项 |
| BE-038 | P1 | 【P1】office-扩展 CalendarPort 支持详情/更新/删除与组织者换算 | 待复核 | ZCode（用户于 2026-11-17 授权） | 端口方法补父资源标识（calendarId 或 organizerId），使飞书/钉钉详情、更新、删除可用；新增「系统用户 → 平台用户」换算（复用 office_organization_mapping），避免用系统 ID 调平台；保留 fail-closed 与租户隔离；新增回归用例（4 组）；`:apex-office` 198 用例 0 失败、spotless 通过；真实链路：无绑定→「未绑定 feishu 平台账号」，插入绑定→「飞书 appId未配置」，换算与路由已通；真实创建仍需平台凭据 | 前端 FE-026；BE-037；[复核清单](../reviews/2026-11-17-integration-review-checklist.md) |
| BE-037 | P1 | 【P1】office-补齐三平台日历客户端实现 | 待复核 | ZCode（用户于 2026-11-17 授权） | 实现 feishu/dingtalk/wecom 三个 CalendarClient（端口 9 方法：创建/详情/更新/取消/删除/范围列表/会议室可用性/平台标识）；Bean 名 `<platform>CalendarClient` 与 `OfficeCalendarService.getCalendarClient` 一致；未配置凭据时 fail-closed 且错误可读；真实创建链路可跑通或明确外部依赖；新增单测覆盖请求构建与错误映射（6 条）；spotless 与模块测试通过（195 用例 0 失败）；真实网关三平台均返回凭据未配置而非「不支持的办公平台」，证明 Bean 查找链路已通。**限制**：详情/更新端口签名缺组织者，已显式拒绝；真实创建需平台凭据，本地未闭环 | 前端 FE-026；记录 §7.2；[复核清单](../reviews/2026-11-17-integration-review-checklist.md) |
| BE-036 | P1 | 【P1】联调-修复快照兼容、菜单范围与审批租户传播 | 待复核 | ZCode（用户于 2026-11-17 授权为本项主控） | 验收：`:apex-system`/`:apex-office`（189 用例）/四个服务模块 `-am test` 均 BUILD SUCCESS 且 0 失败；二轮修审批响应 `processName`/`currentNode`/`createdAt` 恒 null（实体与 DTO 命名不一致致 MapStruct 静默丢字段），`:apex-office-service` 406 用例 0 失败，提交 `102fa274`；缺租户拒绝、JDBC 上下文恢复、空菜单全量、快照 roles/未知字段解码均有回归用例；真实环境租户隔离已验证（租户1 仅见自身记录、跨租户查询返回不存在）；最终重建环境后经网关 18 条真实接口 18/18 通过 | [方案](../specs/2026-11-17-integration-contract-repair.md)；前端 FE-026；[复核清单](../reviews/2026-11-17-integration-review-checklist.md) |

### 2026-11-24 工作区未完成项核查与治理修正租约

- **R0 / 执行者**：当前 Codex 会话，依据用户本轮“检查并继续完成”及实施方案授权，串行维护现有记录；独立复核由未参与修改的实例承担。
- **基线 / 环境 / 时限**：后端 `dev` `4a0d6782`、前端 `dev` `8a839cb`；2026-11-24 起至本轮交接，前端只读。
- **写入范围**：本任务盘、BE-043/049 接管记录、BE-044/064 既有记录、后端变更台账；本轮只修正文档门禁与集成状态口径。
- **禁止触碰 / 并行**：不改源码、门禁、版本、图表、凭据、运行环境；无并行写入，不覆盖历史台账。全局集成租约 `WAITING`，本轮不提交、推送或合并。
- **验收 / 交接 / 回滚**：严格文档、引用、红线、全仓链接、两仓预检及 `git diff --check`；追加真实结果和未完成项。仅撤回本轮文档增量，不回退既有集成提交；BE-044 已依既有任务提交与复核完成收口，BE-064 仍待整包 R5 与任务专属集成证据。

### BE-049 执行租约（2026-11-22）

- **R0 / 执行者**：ZCode（用户 2026-11-22 指定本会话为本轮 R0 并授权执行，串行单写者）。
- **独立复核者（R5）**：独立子代理实例，不参与实现，最终 diff 复核；执行者不得自审自放。
- **租约起始时间**：2026-11-22（Asia/Shanghai）。
- **执行基线**：`dev` `b81acf18bc5523ae3566e0aa594b8d4476aab2cf`（工作区干净）。
- **协作边界**：BE-043 仍登记执行中并写 `HOLDING`；本轮不替其关闭、不覆盖其成果、不修改其任务卡状态。共享 `docs/README.md`、任务盘与台账采用追加式写入，提交前重读目标段落；如确认 BE-043 仍活跃写入则暂停重叠路径改动。
- **写入范围**：`apex-shared-parent/`、`apex-components-parent/`、`apex-starters-parent/`、`apex-services-bom/`、`apex-business-parent/`、`apex-tools-parent/apex-tools-deployment`（兼容性优化与测试）；`scripts/test/`、`scripts/lib/common.sh`（报告生成链）；`config/nacos/apex-gateway.yaml`（`retention-seconds` 模板键镜像，R5 N-01/N-11 补登）、`.env.example`（同名变量样例）；`docs/tasks/project-tasks.md`、`docs/records/`、`docs/reviews/`、`docs/reference/ai-knowledge/07-change-memory.md`、`docs/06-testing/`、`docs/README.md` 及受影响主题文档索引；`docs/plans/`（如需计划正文）。
- **禁止触碰**：`AGENTS.md` 附录 A 原文（哈希锁定）、既有 SVG/MMD 的删除/移动/重绘、Flyway 历史迁移、对外 HTTP 契约与 DTO 字段、模块坐标与 POM 聚合、端口与部署拓扑、其他 AI 私有目录、他人未提交改动与 worktree。
- **并行边界**：本租约独占任务盘、`docs/records/`/`docs/reviews/` 索引与记忆台账；本轮无并行写入任务。
- **验收命令**：`source scripts/build/init-build-env.sh`；`export PATH="/opt/podman/bin:$HOME/.orbstack/bin:$PATH"`；按模块 `mvnd -T4 -B -pl :<受影响> -am test` 与 `spotless:check`；收尾 `mvnd -T4 -B clean verify`；`bash -n` 触及脚本；`bash scripts/test/generate-test-report.sh`；`verify-docs.sh --strict`、`check-doc-refs.sh --strict`、`check-doc-layout.sh`、`audit-backend-rules.sh --strict`、`scripts/ci/check-doc-links.sh`、`apex-preflight.sh --fast --strict`、`git diff --check`、`scan-javadoc-issues.sh`。
- **全局集成租约**：`WAITING`——未获提交授权；本轮交付工作区经复核改动，不执行 commit/push/合并。
- **交接物**：全模块覆盖与问题清单（`docs/reviews/`）、变更记录（`docs/records/`）、台账条目、新测试报告（`docs/06-testing/`）、真实命令输出与未验证项、回滚方式（按批次 `git checkout -- <path>` 或 `git revert`）。

### BE-049 租约接管追加（2026-11-23）

- **授权与责任**：用户明确授权当前 Codex R0 接管 BE-049 责任与租约；原 ZCode 执行期及其提交、测试报告和 R5 结论均保留为历史，不覆盖、不关闭旧任务。
- **接管基线 / 工作区**：`dev` `90a2f783e2c251a55f53b0fc1b845b11d537e951`；接管时工作区干净。原卡 `b81acf18` 基线与 Git 提交历史不一致，R0 先核对任务关联提交和未完成事项，不把历史“未提交”文字直接改成已完成。
- **2026-11-24 提交核对**：`9a59ee2e` 为核心修复、`c41f4fa1` 为报告汇总链、`4118155c` 为评审及报告；三者都在当前 `dev` 历史中，本地 `dev`、`master`、`test` 均包含 `4118155c`，未核验远端。真多实例 Outbox 抢占、撤销流全链路及秒级批边界仍未验证，留在 BE-049；状态保持执行中，历史 R5 结论不覆盖后续改动。
- **本轮范围**：只核实 BE-049 交接、证据与遗留；新一轮韧性/DDD/发布优化统一登记 BE-063，禁止混入 BE-049。
- **独立复核 / 并行**：已有 BE-049 R5 结论只覆盖其评审 diff；如有新变更，须另由非执行者 R5 复核。与 BE-063 的共享构建、任务盘、文档索引、记录索引和台账串行写入。
- **全局集成租约**：`WAITING`；此次仅更新任务责任记录，不提交、不推送、不合并。

### BE-043 租约接管追加（2026-11-23）

- **授权与责任**：用户明确授权当前 Codex R0 接管 BE-043；FE-029 同步由同一 R0 串行协调。旧任务仍为“执行中”，未完成验收项完整保留。
- **接管基线**：后端 `dev` `90a2f783e2c251a55f53b0fc1b845b11d537e951`；前端 `dev` `aec9c444dc113ce7b64f78c0956304b54d304727`。前端已有未提交工作区差异（API 审计脚本、契约矩阵及相关记录/索引），作为既有 FE-029 工作保护，不覆盖、不清理。
- **写入与互斥**：先完成旧任务交接清单与未完成项核对；BE-043/FE-029 继续共享契约串行推进。BE-063/FE-030 是独立新任务，不承接 reader/知识库、全量真实链路或旧任务 R5 遗留，也不改变旧任务状态。
- **独立复核**：既有 R3/R5 指派与历史结论保留；接管后的新增代码需独立 R5 绑定最终 diff/SHA。不得因责任接管将旧任务视为已复核或已关闭。
- **全局集成租约**：`HOLDING`，当前 Codex R0 接替持有并串行协调 FE-029/BE-043；本次授权不包含 commit/push、分支同步或任务关闭。

### BE-063 Wave A 执行租约（仅基线评审）

- **R0 / 执行者 / 环境**：当前 Codex 会话，`/Users/work/apex-scaffold` 的 `dev` 共享 checkout，串行单写者。独立 R5：Pauli（`01a2066c-e436-7b43-b7e3-a076beab7ce5`），只复核，不实现。
- **基线与时限**：`90a2f783e2c251a55f53b0fc1b845b11d537e951`；2026-11-23 11:22（Asia/Shanghai）起，一个工作日，或提前交接为止。
- **写入范围**：仅本卡、本波次评审、实施计划、文档及评审索引和记忆台账；生产源码、测试、POM、路由、配置、脚本、迁移和部署只读。
- **禁止触碰与互斥**：保留未提交日报、知识快照和前端联调差异；不修改旧任务的未完成项、图表、私有目录、运行环境或数据。BE-043/BE-049/FE-029/FE-030 与本波次不并行写入；完成本波复核后再登记下一波写入范围。
- **验收**：根 POM 递归清单和代表性实现对照；运行 `bash scripts/dev/apex-preflight.sh --fast --strict`、`bash scripts/governance/verify-docs.sh --strict`、`bash scripts/governance/check-doc-refs.sh --strict`、`bash scripts/governance/audit-backend-rules.sh --strict`、`git diff --check`，均无阻断。
- **交接物**：[Wave A 交接包](../records/2026-11-23-be063-wave-a-handoff.md) 包含基线矩阵、真实命令结果、待验证与回滚清单及独立复核。全局集成租约 `WAITING`；不暂存、提交、推送或合并；未提交的变更仅按本波新增内容逐项撤回。

- **Wave A 结果**：独立 R5 复审 `APPROVE_WITH_NITS`，首审两项必改均已解除；本波执行租约释放。结论不覆盖后续实现，不持有集成租约。

### BE-063 Wave B1 执行租约（L1 缓存生命周期）

- **R0 / 执行 / 独立复核**：当前 Codex 会话串行执行；Pauli（`01a2066c-e436-7b43-b7e3-a076beab7ce5`）仅负责 R5，不参与实现。
- **基线 / 环境 / 时间**：`90a2f783e2c251a55f53b0fc1b845b11d537e951`，`/Users/work/apex-scaffold` 的 `dev`；2026-11-23 当日，至本批交接为止。固定 JDK 21/mvnd。
- **范围**：`apex-infrastructure-cache` 的 `LocalCacheService.java` 及同包定向回归测试、BE-063 计划/交接/任务盘/索引和台账。让 TTL 元数据随 L1 条目淘汰，消除批写继承旧 TTL；保留 L1 缓存名称、容量、默认 TTL 和统计。禁止改 Redis 策略、生产配置、POM、外部契约、迁移或旧任务。与其他任务串行；全局集成租约 `WAITING`。
- **验收和交接**：固定环境执行 `mvnd -B -pl :apex-infrastructure-cache -am test spotless:check`，核验读写过期、批写替换、容量淘汰与删除/清空；本次 apex-common 593、apex-domain 9、apex-infrastructure-cache 138 项均 0 failure/error/skip，Spotless 全绿。R5 Pauli `APPROVE_WITH_NITS`、无必改项；建议补正 TTL 边界测试并统一异步缓存模式，记录为后续验证项。本波租约释放；不暂存、不提交、不部署。

### BE-063 Wave B2 执行租约（网关生产档熔断接线）

- **R0 / 执行 / 独立复核**：当前 Codex 会话串行执行；Pauli（`01a2066c-e436-7b43-b7e3-a076beab7ce5`）独立 R5，不参与实现。
- **基线 / 环境 / 时间**：`dev` `90a2f783e2c251a55f53b0fc1b845b11d537e951`；保留所有既有未提交差异；固定 JDK 21/mvnd；2026-11-23。
- **范围**：只核对并修复 gateway `application-prod.yml` 的 auth/workflow 路由熔断过滤器缺失；测试覆盖 base/prod/Nacos 三份路由接线；更新本专项规格、任务记录、实施计划、索引及台账。不改变 route id、URI、Path、熔断阈值、TimeLimiter、fallback 响应契约或 Nacos 配置。
- **禁止触碰**：不得修改灰度/负载均衡、超时和重试阈值、其他业务路由、接口契约、部署清单、POM、Flyway、BE-051 Redis 语义及旧任务未完成项；不暂存、提交、推送、部署。
- **独立复核与并行**：仅 Pauli R5 复核本波网关配置、测试和文档 diff；与其他任务串行，`WAITING` 全局集成租约。
- **验收命令**：固定环境 `mvnd -B -pl :apex-gateway -am test spotless:check`；`bash scripts/governance/verify-docs.sh --strict`、`bash scripts/governance/check-doc-refs.sh --strict`、`bash scripts/governance/audit-backend-rules.sh --strict`、`git diff --check`。
- **回滚 / 交接**：按本波精确撤回 production 两条过滤器与测试断言；记录 profile 静态契约结果，不将其描述为真实产线熔断演练。R5 `APPROVE_WITH_NITS`、无必改项；建议断言每条路由恰有一个 CircuitBreaker，作为非阻断测试加固项。本波执行租约释放。

### BE-063 Wave C1 执行租约（网关熔断配置唯一性测试）

- **R0 / 执行 / 独立复核**：当前 Codex 会话串行执行；Pauli（`01a2066c-e436-7b43-b7e3-a076beab7ce5`）独立 R5，不参与实现。
- **基线 / 环境 / 时间**：`dev` `90a2f783e2c251a55f53b0fc1b845b11d537e951`；固定 JDK `21.0.13.0+9-tem` / mvnd `2.0.0-rc-3`；2026-11-23，至 R5 交接。
- **写入范围**：仅 `GatewayRouteProfileConfigTest.java` 的测试断言，以及本卡、BE-063 计划、测试/记录/评审索引与 `07-change-memory.md`；不修改生产配置和业务代码。
- **禁止触碰**：不改路由配置、熔断阈值、TimeLimiter、fallback、服务发现、POM、接口、Kubernetes、旧任务/既有未提交改动；不暂存、提交、推送、部署。
- **验收**：固定环境 `mvnd -B -pl :apex-gateway -am test spotless:check`；三种配置源中 auth/workflow 路由分别且仅有一个 `CircuitBreaker`，实例名/fallback 参数保持契约；common 593、gateway 171 tests 均 0 failure/error/skip；严格治理检查及 `git diff --check` 通过。
- **交接 / 回滚**：交接记录 [Wave C1](../records/2026-11-23-be063-wave-c1-gateway-circuit-breaker-test.md)；R5 Pauli `APPROVE_WITH_NITS`、无必改项，建议未来补充双候选 auth route 同时出现时的数量约束。本波租约释放，全局集成租约 `WAITING`。

### BE-063 Wave D1 规格评审租约（Kubernetes 网关灰度隔离）

- **R0 / 执行 / 独立复核**：当前 Codex 会话；Pauli（`01a2066c-e436-7b43-b7e3-a076beab7ce5`）独立 R5，仅复核规格。
- **基线 / 环境 / 时间**：`dev` `90a2f783e2c251a55f53b0fc1b845b11d537e951`；2026-11-23，交规格 R5 后释放。
- **写入范围**：仅新增 Kubernetes 灰度隔离规格，以及本租约、计划、`docs/specs/README.md`、`docs/README.md` 和记忆台账。
- **禁止触碰**：本租约不修改任何 YAML、脚本、测试、Service/Deployment、Ingress、PDB 或集群状态；不暂存/提交/部署。
- **验收 / 交接**：核实稳定 Deployment selector 不变式、Service/PDB selector、Ingress 初始权重、apex.sh rollout 目标；规格定义迁移顺序、失败关闭、EndpointSlice 和回滚门槛。独立 R5 通过后，再另开实现租约；真实集群不可用时禁止宣称灰度实测。
- **复核 / 释放**：Pauli 增量 `APPROVE_WITH_NITS`，无必改项；将容量、排空与入口探测判据交实现阶段。规格租约释放；全局集成租约 `WAITING`。

### BE-063 Wave D1 实现租约（Kubernetes 网关灰度隔离）

- **R0 / 执行 / 独立复核**：当前 Codex 会话为单一写者，Pauli（`01a2066c-e436-7b43-b7e3-a076beab7ce5`）独立 R5。全局集成租约 `WAITING`，不并行写、不暂存/提交/推送。
- **基线 / 环境 / 时间**：`dev` `90a2f783e2c251a55f53b0fc1b845b11d537e951`；2026-11-23 12:56（上海）至当日 18:00 或提交独立复核时释放执行租约；固定 JDK/mvnd、隔离的模拟 kubectl，不执行集群命令。
- **写入范围**：`k8s/` 网关基础与灰度清单、使用说明；`apex-tools-build` 的 K8s CLI、端点校验、说明与契约测试；运维部署/构建工具/架构现行入口指引，本任务卡、规格/计划同步、交接记录/索引/记忆台账。
- **禁止触碰**：不改稳定 Deployment selector、稳定镜像、稳定 PDB 门槛、其他服务拓扑、业务/API/数据库、旧任务、无关既有差异；不访问或切换实际集群。不声明为蓝绿或自动化放量。
- **验收与交接**：分离灰度入口清单；旧 selector 由 CLI 非零拒绝直升，分阶段人工迁移写入手册；验证 rollout、EndpointSlice 中真实 Pod 归属/身份互斥、启用 PDB 与否均可、0% 入场门槛及中断重跑。固定环境 `mvnd -B -pl :apex-tools-build -am test spotless:check`、shell 语法检查和严格治理校验；记录真实结果、待集群验证和回滚方式，经独立 R5 后进入后续波次。
- **执行结果 / 租约释放**：实现复核首轮 `REJECT` 的 class/controller 与行为测试必改已完成；Pauli 增量 `APPROVE_WITH_NITS`。已再补 CLI 缺失/错误 IngressClass controller 的模拟集成路径并通过脚本回归。执行租约释放；建议性其余限制（容量/入口探测及真实集群）留待隔离环境波次，不属于本地可验证范围。

### BE-063 Wave D2 规格租约（网关蓝绿切换）

- **负责人 / 基线 / 时间**：当前 Codex R0 单写，独立 R5 Curie 只读；`dev` `90a2f783e2c251a55f53b0fc1b845b11d537e951` 加 D1 工作区改动，2026-11-24（Asia/Shanghai），至规格交接；全局集成租约 `WAITING`。
- **范围 / 互斥**：仅新增蓝绿规格并维护索引、计划和台账；现有 Kubernetes 资源、脚本、配置、认证和业务实现不改。不操作现有集群，不与灰度迁移或旧任务共用执行租约。
- **验收 / 回滚**：以独立复核和严格文档/引用检查验收，设计须覆盖固定入口、并发防护、端点身份、负载均衡和回切。仅撤回 D2 规格增量可回滚；实现与隔离集群演练分别授权和登记。
- **复核 / 释放**：首轮 `REJECT` 的预检后竞争入口缺口已补齐，Curie 对最终规格增量 `APPROVE`，无必改项，规格租约释放。实现前另选能提供专属控制器、入口地址及拒绝式准入的隔离拓扑，登记实施范围；不把规格放行当成集群验收。

### BE-063 Wave E 执行租约（全仓门禁与报告快照）

- **R0 / 执行 / 复核**：当前 Codex 会话单写；全仓门禁按验证工作执行，Pauli 已独立复核 D1 实现，不代表其复核 Wave E。
- **写入范围 / 禁止触碰**：仅运行构建/报告工具及登记本波命令结果；不运行 `clean`，不修改源码、质量门禁、超时断言、依赖或旧任务，不暂存/提交/部署。
- **执行结果 / 交接**：`mvnd -T4 -B verify` 退出码 1：auth-service PMD 6.55/ASM 不支持 Java 21 class major 65；同时 `ExampleDataSourceTimeoutTest` 12.667s 超 12s 断言。报告工具汇总 45 模块现存 XML 6683 tests、1 failure、0 errors、33 skipped、4 duplicate warnings。交接 [Wave E 记录](../records/2026-11-23-be063-wave-e-full-verify.md)。Wave E 保持未通过；需要独立工具兼容/测试稳定性任务，不放宽门禁。本次执行租约释放。
- **2026-11-24 隔离续验**：从当前 `dev` 工作树导出受 Git 管理的文件及未忽略的新增文件至 `/private/tmp`，不带 `.git`、忽略产物或已有 `target/`。同一 R0 在副本执行固定环境 `mvnd -T4 -B clean verify`；仅在通过后从该副本汇总 XML。原仓只写治理与结果记录，保留其他任务产物；独立 R5 待安排，集成 `WAITING`。
- **2026-11-24 续验结果**：首轮仅 auth-service 格式失败，修正后续验 `BUILD SUCCESS`。两段覆盖 45 个模块，XML 汇总 6682 项、33 跳过、0 失败或错误；非单次全量通过。报告与跳过边界见 [Wave E 记录](../records/2026-11-23-be063-wave-e-full-verify.md#2026-11-24-隔离全仓验证)。
- **最终单次验证 / 交接**：含 BE-064 最终断言和 BE-065 整改的隔离 `clean verify` 45/45 单次通过；从同次产物再生成报告，6685 项、33 跳过、0 失败或错误。Curie 接受[最终证据](../records/2026-11-23-be063-wave-e-full-verify.md#2026-11-24-最终单次全仓验证)作为本地门禁。隔离运行结束，集成 `WAITING`；不替代整包终审与真实环境演练。

### BE-064 执行租约（构建门禁修复）

- **R0 / 执行者**：当前 Codex 会话单写；独立 R5 待安排，不自审关闭。
- **基线 / 环境 / 时间**：`dev` `90a2f783e2c251a55f53b0fc1b845b11d537e951`；JDK 21.0.13.0+9-tem / mvnd 2.0.0-rc-3；2026-11-23。
- **写入范围**：根 `pom.xml` 的 Maven PMD 插件依赖，`ExampleDataSourceTimeoutTest.java`，本任务规格/记录/任务盘、必要索引及台账；不运行 clean，不覆盖其他任务未提交内容。
- **禁止触碰**：PMD/P3C 规则与失败门禁、生产代码/配置、旧任务状态、报告历史；不得提交、推送或部署。
- **验收命令**：定向执行 auth-service PMD verify 与 example 超时用例；后执行全仓 `mvnd -T4 -B verify`、`scripts/test/generate-test-report.sh`、严格文档与红线检查及 `git diff --check`。
- **全局集成租约 / 交接**：`WAITING`；提交动作未获授权。结果写入 [BE-064 记录](../records/2026-11-23-be064-build-gate-repair.md)，成功后请求独立 R5 绑定最终 diff。
- **2026-11-24 复核整改续租**：当前 Codex 串行单写，Curie 独立只读复核；同一基线上仅补 `ExampleDataSourceTimeoutTest.java` 的 JDBC 连接属性断言，不改 YAML、驱动或门禁。先在隔离副本验收定向用例、Spotless 和 PMD，再由 Wave E 对最终源码执行单次 `clean verify` 与同轮报告汇总。交接释放执行租约；回滚仅撤回本次断言增量和对应治理记录。
- **复核 / 交接**：定向 17 模块通过、目标测试 1/1；Curie 静态增量 `APPROVE`，无必改项。本次整改租约释放，全仓报告由 Wave E 填写；尚无集成提交。

### BE-065 执行租约（PMD 全仓规则修复）

- **R0 / 执行者 / 复核者**：当前 Codex 会话为 R0 与执行者；独立 R5 待安排，不自审关闭。
- **续作范围**：2026-11-23（Asia/Shanghai）串行单写；本轮处理 `apex-api-contract`、同仓消费者、`apex-tools-deployment` 和 `apex-infrastructure-remote`。定向门禁均通过；最新全仓刷新为 37 个 XML、174 条违规、0 个 XML `<error>`，其余业务模块仍需逐项修复。独立复核时交付定向构建日志、全仓扫描快照及未验证项；不与旧任务并行写入同一路径。
- **基线 / 环境 / 时间**：`dev` `90a2f783e2c251a55f53b0fc1b845b11d537e951`；固定 JDK 21.0.13.0+9-tem / mvnd 2.0.0-rc-3；2026-11-23。
- **写入范围**：经全仓 PMD 定位的违规源码、必要回归测试及本任务规格/记录/任务盘/索引/台账；保留现有业务行为与对外契约。
- **2026-11-24 续作**：仍由当前 Codex 串行单写，独立 R5 待安排。本批范围为 file-service、integration-service、knowledge-service、boot-all 的 PMD 定位项；验收先定向检查，再按 Wave E 全仓 `clean verify`，报告仅在取得完整构建结果后生成。全局集成租约继续 `WAITING`。
- **续作证据与环境隔离**：integration 定向测试、Spotless、PMD 已通过；剩余模块批次修复后见两处 Spotless 空行要求，已按差异修正。全仓 `clean verify` 只在独立临时副本执行，保留原工作区与其他任务的 `target/`。执行证据与待核项见 [BE-065 记录](../records/2026-11-23-be065-pmd-rule-repair.md)。
- **2026-11-24 R5 整改续租**：当前 Codex R0 串行写入，仅限 `MysqlClient.java`、`MysqlClientTest.java`、`OauthConsentRepository.java` 的类 Javadoc、BE-065 兼容性规格及 BE-064/065、Wave E 治理记录。独立 R5 Curie（`01a20bc5-e5c8-7ab0-8b55-c1dabcbb020e`）只读复核；上一轮 Hilbert 的结论保留。显式限定 CLI 管道执行器资源，定向验证子进程回归、Spotless、PMD，随后仅在隔离目录全量验证并生成报告。不改 SQL、发布脚本、门禁或历史报告；全局集成租约 `WAITING`，工作范围交接后释放；仅撤回本次明确的源码与测试增量可回滚。
- **禁止触碰**：禁用 PMD、放宽失败门槛、扩大 exclude 清单、改变无关模块/接口/迁移/生产拓扑；不清理其他任务产物，不暂存/提交/推送/部署。
- **验收与回滚**：受影响模块 `verify` + Spotless，继而全仓 `mvnd -T4 -B verify`；报告写到独立快照并说明同一代码基线。按任务范围逐批撤回，不恢复整个工作区。
- **全局集成租约**：`WAITING`；本轮尚未验证任务租约持有者外的共享写入活动，提交/集成待用户授权。
- **2026-11-24 未关闭项分诊**：最终隔离日志有 518 行旧 PMD `SequenceChecker` 最大迭代诊断；未映射到源文件/规则，零违规 XML 不等于 DFA 完整覆盖。Java ABI 变更未证明旧二进制兼容，发布前须落实可区分版本成套发布或兼容入口及旧调用方回归；不覆盖 `1.3.1`。不静音诊断、不提高迭代上限、不擅自改版本。详见[分诊记录](../records/2026-11-23-be065-pmd-rule-repair.md#2026-11-24-未关闭风险分诊)。

### BE-045~BE-048 执行租约（2026-11-22）

- **R0 / 执行者**：ZCode（用户 2026-11-22 授权「全仓对齐与图表补齐战役」，四卡同租约、串行单写者；唯一活跃执行租约）。
- **独立复核者（R5）**：收尾由独立子代理实例统一复核 BE-045~BE-048（不参与实现）；本轮无接口契约改动，R3 不适用。
- **租约起始时间**：2026-11-22（Asia/Shanghai）；持有至交接 R5。
- **执行基线**：`dev` `8d8dc31caf06148b908794fd9f3c61bf2c41ab12`（工作区干净）。
- **写入范围**：`docs/tasks/project-tasks.md`、`docs/records/`、`docs/reviews/`、`docs/reference/ai-knowledge/07-change-memory.md`、`docs/01-guides/**`、`docs/03-api/next-steps-guide.md`、`docs/04-development/**`、`docs/05-operations/github-branch-protection.md`、`docs/06-testing/**`、`docs/08-project-management/plans/microservice-migration-plan.md`、`docs/diagrams/**`、`docs/02-architecture/**`、`docs/README.md`、`docs/specs/project-governance.md`（BE-048 §6 B6/B7 更新；R5 MF-01 补登）、`docs/archived/ai-sessions/zcode-plans/**`（AGENTS.md 0.4 计划正文归档义务，随迁 1 份；R5 MF-01 补登）、`AGENTS.md` 第 0 章 0.5-2、`scripts/dev/check/scan-javadoc-issues.sh`、`scripts/dev/check/check_javadoc.sh`、`scripts/README.md`；代码仅两处：`apex-services-bom/apex-file-service/.../config/FileAuditFieldFillHandler.java`（仅类注释）与 `apex-services-bom/apex-system-service/src/main/resources/application.yml`（Sentinel-Nacos 兜底地址——经核验为本地 infra 栈刻意约定，**未改动**）。
- **禁止触碰**：`AGENTS.md` 附录 A 原文（哈希锁定）、既有 SVG/MMD 的删除/移动/重绘、Flyway 历史迁移、接口契约与业务逻辑、POM/模块边界、其他 AI 私有目录、他人未提交改动与 worktree。
- **并行边界**：本租约独占任务盘、`docs/records/`/`docs/reviews/` 索引与记忆台账；无并行写入任务，其他任务不得同时写共享 checkout。
- **验收命令**：`bash scripts/governance/verify-docs.sh --strict`；`bash scripts/ci/check-doc-links.sh`；`bash scripts/governance/check-doc-refs.sh --strict`；`bash scripts/governance/check-memory-sync.sh --strict`；`bash scripts/governance/audit-backend-rules.sh --strict`；`bash scripts/dev/apex-preflight.sh --fast --strict`；`JAVADOC_SCAN_PATH='apex-*/apex-*/src/main/java' JAVADOC_OUTPUT_DIR=/tmp/javadoc-scan bash scripts/dev/check/scan-javadoc-issues.sh`；收尾全量 `mvnd -T4 -B clean verify`（固定构建环境 + podman/orbstack PATH 前置）与触及模块 `spotless:check`、`git diff --check`。
- **全局集成租约**：`WAITING`——提交动作待用户明确授权；授权后按六组分批提交（任务卡+对齐 / 图表 _v2 / 新增图表 / 注释 / 规则文档与脚本 / 配置），提交信息含 `memory:` 标记并关联台账条目。
- **交接物**：`docs/records/2026-11-22-alignment-campaign.md`、台账 #80 起追加条目、R5 评审报告、16 张新图与 `_v2` 图及索引更新、`docs/06-testing/2026-11-22-full-verify-report.md`、真实命令输出与未验证项、回滚方式（按批次 git 分组还原）。
- **R5 复核结论（2026-11-22）**：独立子代理实例审计暂存区 54 文件，结论 **APPROVE_WITH_NITS**——附录 A 哈希不变、yml 零改动、既有图零删改、16 对新图与索引一一对应、事实抽查五项一致、治理校验独立复跑全绿；必改项 MF-01（租约写入范围补登 governance.md 与 zcode-plans 归档）/MF-02（zcode-plans README 计数 14→15）/MF-03（验证报告计数口径指引改指模块架构评审报告溯源段）均已由 R0 追加清零。报告：[2026-11-22-alignment-campaign-r5-review.md](../reviews/2026-11-22-alignment-campaign-r5-review.md)。
- **关闭状态（2026-11-22，用户授权提交后）**：四卡已随集成提交关闭——BE-045 `acc23a31`、BE-046 `8035bbe4`、BE-047 `d64a2255`、BE-048 `3964a8d3`+`23360fc3`；台账 #80–#84 已回填 SHA；全局集成租约释放。

### BE-043 执行租约

- **R0 / 执行者**：Codex（用户 2026-11-18 明确要求继续完成；当前串行单写者）。
- **独立 R3 / R5**：R3 `integration_plan_review`（最终 APPROVE，2026-11-18）；R5 `integration_final_reviewer`（已接受最终复核指派，不参与实现，结论绑定最终待复核 SHA）。
- **租约起始时间**：2026-11-18 继续执行（Asia/Shanghai）；R0 单写者持有至交接 R5。
- **执行基线**：后端 `dev` `b177605d1d1482470945b8a48183103655bf3d18`；前端 `dev` `f03bb6883a0f05be9cb4fda836062db55f6e4460`。
- **执行环境**：唯一 Compose 入口为本仓库 `podman-compose.yml`；显式项目名 `apex-scaffold`，网络名 `apex-scaffold_apex-network`。`.env` 从 `.env.example` 准备并配置本地必填安全密钥；前端镜像在独立仓库用 Podman 预构建，由本文件的 `apex-admin-web` 服务接入；不启动第二份前端 Compose。跨仓共享 checkout 串行单写者。
- **写入范围**：仅真实缺陷影响的后端源码/测试最小文件；`podman-compose.yml`（仅联调证明确有环境缺陷时）；`docs/03-api/**`、`docs/06-testing/**`、`docs/specs/**`、`docs/records/**`、`docs/README.md`、本任务卡及 `docs/reference/ai-knowledge/07-change-memory.md`。
- **禁止触碰**：无关服务、POM/模块边界、Flyway 迁移、外部生产配置、密钥与非测试数据；若必须修改，暂停并先增补评审方案。
- **依赖/并行边界**：依赖 FE-029 API 对照；两卡共用 R0，串行执行；共享 DTO/权限/迁移变更先经 R3 评审，禁止 FE/BE 并行修改。
- **验收命令**：`podman-compose -p apex-scaffold -f podman-compose.yml config`；固定构建环境后按受影响模块运行 `mvnd -B -pl :<artifactIds> -am test` 与 `spotless:check`；运行 `verify-docs.sh --strict`、`check-doc-refs.sh --strict`、`check-memory-sync.sh --strict`、`apex-preflight.sh --fast --strict`；校验 Podman Compose 配置及健康/网络探测。
- **全局集成租约**：`HOLDING`，由本 R0 串行持有；跨仓同步与最终集成不得并行。
- **交接物**：后端路由/DTO 差异和修复矩阵、定向测试原始结果、Podman 配置/容器/网络证据、记录/台账条目及 R5 结论。

| BE-035 | P2 | 根 target 额外构件副本定向清理与项目内本地仓库防再现守卫 | 已完成 | 主控 Trae | 守卫接入 build.sh/release.sh/apex.sh 并经 BuildPathContractTest 9/9 验证；preflight 仓库位置与副本双检通过（OK=15 WARN=3 FAIL=0）；`target/project-local-repo` 125 副本 1.4G + 44 .DS_Store 已定向清理；不改业务代码/POM、未提交 | `scripts/lib/common.sh`、`scripts/build/build.sh`、`scripts/release/release.sh`、`apex-tools-parent/apex-tools-build/bin/apex.sh`、`scripts/dev/apex-preflight.sh`、台账 #40/#41 |

### BE-042 执行租约（2026-11-18）

- **基线 SHA**：`5d17cac276c8fcb4b9855671a7240b14a669d455`（`dev`）。
- **R0 / 执行者**：Codex（同一用户会话，串行单写者）；该任务无并行写入，R0 与执行者可合并。
- **独立复核者**：首轮 Chandrasekhar（独立子代理 `01a1ed5c-98d3-72f2-9091-ac26530b2d12`，结论 REJECT）；增量复审 Erdos（独立子代理 `01a1ed8c-02cb-7300-a949-cca27d03f6fa`，与执行者不同实例/会话，结论 APPROVE_WITH_NITS）。
- **租约时间**：初次 `2026-11-18 14:28`；R5 首轮 `REJECT` 后返工租约 `2026-11-18 14:44`。
- **执行环境**：共享 checkout 串行单写者；返工仅改治理文档并处置用户授权的 ZCode 私有计划目录内容，不创建或改写业务代码。
- **写入范围**：`AGENTS.md`、`docs/README.md`、`docs/specs/README.md`、`docs/specs/project-governance.md`、`docs/specs/ai-agents/**`、`docs/tasks/README.md`、`docs/tasks/project-tasks.md`、`docs/records/**`、`docs/reviews/**`、`docs/archived/**`、`docs/reference/ai-knowledge/07-change-memory.md`，以及用户授权处置的 ZCode 私有计划目录内容。
- **禁止触碰**：Java/POM、构建与治理脚本、数据库迁移、接口契约、图表资产及其他智能体私有目录；ZCode 私有计划目录仅限本次计划正文迁出与短指针。
- **并行边界**：本任务独占任务盘、记录/评审索引、归档索引和记忆台账；无并行写入任务，其他任务不得同时写共享 checkout。
- **全局集成租约**：`NOT_REQUIRED`（单一 R0/执行者、无并行写入任务）；共享 `dev` 的 `master`/`test` 同步与推送仍由本任务 R0 串行执行。
- **验收命令**：`git diff --check`；附录 A 哈希；`check-doc-refs.sh --strict`；`check-memory-sync.sh --strict`；`audit-backend-rules.sh --strict`；`apex-preflight.sh --fast --strict`；`verify-docs.sh --strict`。
- **交接物**：变更记录 `docs/records/2026-11-18-multi-agent-governance.md`、台账 #53 与追加条目 #54/#55、[R5 评审报告](../reviews/2026-11-18-be-042-r5-review.md)、真实命令结果、未验证项与回滚方式。
- **历史事实**：内容提交 `61a7499f7cf87d52930b0dd3b2de7e69d91fe6d5`；证据回填/中间集成提交 `90c13b744be84b0bbd9dbab96c9d47fc55cbef22`。`90c13b74` 时 `dev`/`master`/`test` 与同名远端均为该 SHA，推送范围 `5d17cac2..90c13b74`；它**不是最终关闭 SHA**。
- **用户集成授权**：2026-11-18 用户明确要求提交并合并至 `dev`/`master`/`test`、推送所有分支；该指令覆盖默认“R5 放行后再集成”的执行顺序，但不构成 R5 复核结论。
- **当前状态**：`已完成`；R5 增量复审 `APPROVE_WITH_NITS`、MF-01–MF-05 无未解决必改项；最终关闭提交为本状态提交，完整 SHA 可由本任务所在分支的 HEAD 定位。

## 2. 本期已完成（治理基线）

| ID | 优先级 | 任务 | 状态 | 完成日期 | 产出 |
| --- | --- | --- | --- | --- | --- |
| BE-034 | P1 | 目录规范化：docs 目录总览 + 7 个一级目录 README + 目录结构自检 | 已完成 | 2026-11-16 | 验收：`verify-docs.sh` 9 项 0 问题、`check-doc-layout.sh` 4 条断言全 OK（25 个一级目录均有 README 且被总览覆盖）、`check-doc-refs.sh` 断链 0；产出：`docs/README.md`「🗂 目录总览」、`docs/{_archive,api,architecture,archived,fix-reports,migration,reference}/README.md`、`scripts/governance/check-doc-layout.sh`、规范 §3.7、`docs/plans/2026-11-16-directory-normalization-plan.md` |
| BE-010 | P1 | AGENTS.md 增补第 0 章治理规范（附录 A 原文逐字保留，SHA-256 已比对） | 已完成 | 2026-11-16 | `AGENTS.md` |
| BE-011 | P1 | 建立 `docs/{tasks,specs,plans,records,reviews,notes}` 标准目录与索引（既有编号目录不动） | 已完成 | 2026-11-16 | `docs/README.md`、各目录 README |
| BE-012 | P1 | 迁移 `.trae/documents` 4 份设计文档 → `docs/plans/archived-ai-sessions/`（逐字节一致后移除原件，留指针） | 已完成 | 2026-11-16 | `docs/records/2026-11-16-docs-governance.md` |
| BE-013 | P1 | 治理自检脚本与 pre-commit 钩子（警告模式，不阻断） | 已完成 | 2026-11-16 | `scripts/governance/`、`.githooks/` |
| BE-014 | P1 | 迁移 `.workbuddy/knowledge` 7 份知识库文档 → `docs/reference/ai-knowledge`（逐字节一致，仅修相对链接） | 已完成 | 2026-11-16 | `docs/reference/ai-knowledge/PROVENANCE.md` |
| BE-015 | P1 | 迁移 `.zcode/plans` 13 份会话计划 → `docs/archived/ai-sessions/zcode-plans`（2026-11-16；2026-11-18 BE-042 追加 1 份，当前 14 份） | 已完成 | 2026-11-16 | `docs/archived/ai-sessions/zcode-plans/README.md` |
| BE-016 | P1 | 迁移 `.workbuddy/reports` 3 份自动周期报告 → `docs/08-project-management/periodic-reports/{daily,weekly,monthly}`（内容唯一，避免丢失） | 已完成 | 2026-11-16 | `docs/records/2026-11-16-docs-governance.md` |
| BE-018 | P3 | 规范正文入仓并补工具目录/忽略规则条款 | 已完成 | 2026-11-16 | `docs/specs/project-governance.md`、`AGENTS.md` 0.1/0.4/0.7、`scripts/governance/README.md` |
| BE-019 | P2 | 他人未提交的 3 项（记忆卡基线、daily 索引、11-16 日报）按规范复核后纳入版本库 | 已完成 | 2026-11-16 | `MEMORY-CARD.md`（指针改指 `docs/`）、`daily/README.md`、`2026-11-16-all-projects-daily-report.md`（补后段补记） |
| BE-020 | P1 | 治理评审整改（RV-01~RV-14，批次 1–6） | 已完成 | 2026-11-16 | 验收：`verify-docs.sh` 通过（仅 BE-001 存量告警）、`check-doc-refs.sh` 0 项、图表 217/68 不变；产出：`docs/records/2026-11-16-governance-alignment-remediation.md` |
| BE-001 | P2 | 图表资产补导出：30 个 `.mmd` 补同名 `.svg` | 已完成 | 2026-11-16 | 验收：68/68 配对、SVG 217→247、`verify-docs.sh` 第 6 项 0 告警；产出：`docs/diagrams/README.md`（补导出记录）、`scripts/utils/render-mermaid-svg.sh` |
| BE-017 | P3 | `apex-common` 的 `VirtualThreadBoundedExecutor` 手写 `LoggerFactory` 改 `@Slf4j` | 已完成 | 2026-11-16 | 验收：`mvnd -pl :apex-common -am verify` BUILD SUCCESS（测试/覆盖率/failsafe 全绿）、日志变量名仍为 `log`；产出：`apex-shared-parent/apex-common/.../scheduler/VirtualThreadBoundedExecutor.java` |
| BE-021 | P3 | 5 张序列图激活盒恢复（`78/84/90/93/94`）：源文件激活盒嵌套不合法，已归一化为无语义损失的生命线 | 已完成 | 2026-11-16 | **决策①接受现状**（业主 2026-11-16 确认；如需还原按②人工重排或③`opt/alt` 替代另开任务）。见 `docs/diagrams/README.md`「2026-11-16 批量补导出记录」 |
| BE-003 | P2 | 根目录散落文档归位：`aggregation-optimization-plan.md` → `docs/plans/`、`architecture-review-report.md` → `docs/reviews/`（根目录留 3 行指针，SHA-256 迁移前后核对） | 已完成 | 主控 | 2026-11-16 |
| BE-022 | P1 | 变更记忆台账建立 + 规范 §5.4 强制登记（六字段、append-only） | 已完成 | 2026-11-16 | `docs/reference/ai-knowledge/07-change-memory.md`；验收：台账条目连续、提交带 `memory:` 标记（`check-memory-sync.sh` 0 项） |
| BE-023 | P1 | 台账自动校验脚本 + `pre-commit`/`commit-msg` 双钩子（默认警告，`--strict` 阻断） | 已完成 | 2026-11-16 | `.githooks/pre-commit`、`.githooks/commit-msg`、`scripts/governance/check-memory-sync.sh`；验收：钩子实测放行并打印 `[memory] OK` |
| BE-024 | P1 | 一键预检脚本 `apex-preflight.sh`（4 模式 / 10 类检查，两仓同款） | 已完成 | 2026-11-16 | `scripts/dev/apex-preflight.sh`、三个项目 Skill §1.5；验收：后端 `OK=19 WARN=1 FAIL=0`、前端 `OK=16 WARN=1 FAIL=0` |
| BE-025 | P1 | 分支模型 `dev`/`master`/`test` 建立并删除 `main`；`dev` 为日常开发分支 | 已完成 | 2026-11-16 | `docs/records/2026-11-16-github-remote-onboarding.md` §5；验收：远端仅三支、三支同点差 0/0、无 `main` |
| BE-026 | P1 | 许可统一 MIT（后端 MIT + `LICENSE-COMMERCIAL.md` 商业双轨） | 已完成 | 2026-11-16 | `LICENSE`、`LICENSE-COMMERCIAL.md`；验收：两仓 `LICENSE` 一致、台账 `05` 条可查 |
| BE-027 | P1 | 分支保护规则清单成文（`master`/`test` 档1：禁强推+禁删除） | 已完成 | 2026-11-16 | `docs/05-operations/github-branch-protection.md`；**规则开启待网页操作或 PAT**（匿名 API 403） |
| BE-028 | P1 | `dev` 分支一致性评审（D-01~D-10）与整改方案 B1–B6 | 已完成 | 2026-11-16 | `docs/reviews/2026-11-16-dev-branch-governance-audit.md`、`docs/plans/2026-11-16-dev-alignment-remediation-plan.md`；决策 1A/2档1/3① 已确认 |
| BE-029 | P1 | 整改 B1：`AGENTS.md` 0.8 + 规范 §5.3 口径 + 记忆卡/知识库口径纠偏 | 已完成 | 2026-11-16 | `AGENTS.md`、`docs/specs/project-governance.md`、`MEMORY-CARD.md`、`docs/reference/ai-knowledge/README.md`；验收：附录 A 哈希 `aa0c25fc…60ed` 不变 |
| BE-030 | P0 | 整改 B3：CI 触发分支对齐 `dev`/`master`/`test`（5 个 workflow + 镜像条件改 `refs/heads/master`） | 已完成 | 2026-11-16 | `.github/workflows/`；验收：无 `main` 残留、YAML 解析通过 |
| BE-031 | P1 | 整改 B4：SpringDoc 注解补齐至 100%（2 Router `@RouterOperations`、3 方法 `@Operation`） | 已完成 | 2026-11-16 | `apex-system/.../router/OpenUserRouter.java` 等 4 个类；验收：覆盖率 100%、`verify` BUILD SUCCESS |
| BE-032 | P2 | 整改 B5：`.gitignore` 去重为单一私有目录区块（含注释独占一行的补正） | 已完成 | 2026-11-16 | `.gitignore`；验收：`check-ignore` 命中、无未跟踪私有目录 |
| BE-033 | P1 | 整改 B6：GitHub 分支保护「档 1」 | 已完成 | 2026-11-16 | 转 public 后六条 `PUT /protection` 全部成功：两仓 `master`/`test`/`dev` 均 `force=false del=false`，`master`/`test` 另加 `conversation_resolution=true`；绕钩子实测删支/强推均被服务端 `protected branch hook declined` 拒绝，`test` 未被改写。保底保留 `.githooks/pre-push`。见 `docs/05-operations/github-branch-protection.md` §0.1 + §5 |
| BE-002 | P2 | 文档孤岛盘点：715 篇 md 中未被 `docs/README.md` 或任何目录索引引用的文件清单化（只盘点、不移动、不删除） | 已完成 | 2026-11-16 | `docs/notes/2026-11-16-doc-orphans.md`（527 篇孤岛 + 建议归属）；`scripts/governance/report-doc-orphans.sh`；验收：清单已生成、零文件移动 |
| BE-004 | P3 | 存量注解式异常处理例外评估：`apex-system` 的 `GlobalExceptionHandler`、`SentinelExceptionHandler`（`@RestControllerAdvice`）是否下沉为 `ErrorWebExceptionHandler` | 已完成 | 2026-11-16 | `docs/reviews/2026-11-16-exception-handler-evaluation.md`；验收：结论「维持现状」、契约零变化、未改代码 |
| BE-007 | P3 | AI 私有目录指针桩清理：确认 `.trae/documents` 的 `MIGRATED.md` 指针以外无残留正文、全仓无指向私有目录的链接后，移除桩文件 | 已完成 | 2026-11-16 | 移除 Trae 私有目录下的全部 5 个指针桩（`MIGRATED.md` + 4 个 3 行指针）；验收：全仓无指向私有目录的链接（历史叙述按 §3.6 豁免） |
| BE-005 | P3 | 周期报告（日报/周报/月报）流水件治理：`docs/08-project-management/periodic-reports/` 只保留索引与近期，历史按月归档 | 已完成 | 2026-11-16 | 日报 11 / 周报 6 / 月报 2 件移入 `_archive/2026-10/`，三个 README 索引改指归档并写入归档策略；验收：`check-doc-refs` 0 项 |
| BE-006 | P3 | `scripts/` 与 `docs/` 的脚本说明对齐：`scripts/` 各子目录是否都有 README 与用途说明 | 已完成 | 2026-11-16 | `scripts/` 19 个子目录新增 README（合计 22/22 齐备）；`scripts/governance/README.md` 登记盘点脚本；验收：每个目录有用途/用法/退出码约定 |

## 3. 阻塞与风险登记

| 风险 | 影响 | 处置 |
| --- | --- | --- |
| 图表被批量移动会导致报告/README 断链 | 文档可信度下降 | 第 0.3 节禁止批量移动；迁移必须附引用扫描 |
| `.workbuddy/memory`、`.trae/documents` 长期未入库 | 本地知识可能丢失 | 设计文档已迁入 `docs/`；`memory` 属工具私有记忆，按规范保留 |
| 工作区存在他人未提交改动（`MEMORY-CARD.md`、daily 报告） | 可能被误覆盖 | 首轮未触碰、按路径分批 add；经用户授权后于 BE-019 按规范复核（只改指针与索引登记，正文保留）并单独提交 |

## 4. 维护规则

1. 新任务先入表再动手；ID 采用 `BE-nnn` 连续编号，不复用。
2. 状态变更只由主控智能体执行；执行智能体提交「完成说明 + 验证命令输出」。
3. 任务关闭必须在 `docs/records/` 留变更记录（做了什么 / 验证方式 / 影响面）。
4. 每月复核：连续两期未动的 `P2/P3` 降级或标 `搁置` 并写原因。

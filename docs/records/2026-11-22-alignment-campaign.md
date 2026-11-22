# 2026-11-22 全仓对齐与图表补齐战役记录（BE-045~BE-048）

> 任务卡：[project-tasks.md](../tasks/project-tasks.md) BE-045 / BE-046 / BE-047 / BE-048（共用一份执行租约）。
> 执行基线：`dev` `8d8dc31caf06148b908794fd9f3c61bf2c41ab12`（工作区干净）。R0/执行：ZCode（用户 2026-11-22 授权，串行单写者）。
> 授权边界：**本轮默认只暂存不提交**（提交待用户明确授权；授权后按六组分批提交，提交信息含 `memory:` 标记）。

## 1. BE-045 文档/脚本/配置/测试报告对齐

### 1.1 已修复

| 文件 | 修复内容 |
| --- | --- |
| `docs/01-guides/message-middleware.md` | 「事务消息」段重写：对齐 BE-044 装配语义——`MqLocalTransactionAutoConfiguration` 三条件（message-queue 开启 / RocketMQTemplate 可用 / DomainEventOutboxStore 存在）自动注册 `DefaultMqLocalTransactionHandler`；按 `apex-message-id`（回退 `KEYS`）查 Outbox 返回 COMMIT/ROLLBACK/UNKNOWN；自定义 Bean 完全覆盖且不再共存冲突。删除「未提供处理器时框架返回 UNKNOWN」旧表述 |
| `docs/01-guides/spi-plugin-guide.md` | 新增「装载器异常隔离与优先级」与「设备规则条件解析失败语义」两小节：`StorageProviderLoader`/`ConnectorLoader` 告警跳过保留已发现、`ConnectorLoader` 内置优先与同名 SPI 跳过、`DeviceRuleEngineService` 解析失败跳过当前规则（fail-closed）——对齐 BE-044 Wave B/C/D/G |
| `docs/06-testing/README.md` | 新增「最近一次全量验证（2026-11-22）」小节（链接本轮收尾实测报告）；底部导航改为「历史报告导航」，为 6 个历史报告补日期与历史快照状态标注 |
| `docs/README.md` | 页脚「最后更新」2026-09-12 → 2026-11-22 并注明战役范围 |
| `docs/tasks/project-tasks.md` | L6 基线注：1302 文件 → 1387 文件（800 md + 247 svg + 68 mmd + 139 json + 133 其他，2026-11-22 实测） |

### 1.2 核验后豁免（不改正文）

排查发现的「现役口吻」引用经逐条核验均已被历史快照横幅覆盖，按治理口径不改正文：

- `docs/03-api/next-steps-guide.md`（L3 已有横幅；L215/494/500 的 `apex-interfaces` 为 2026-09-19 快照内容）。
- `docs/06-testing/TEST_REPORT.md`（L3 已有横幅；L379 workflow 8083 为 2026-10-10 当时事实，workflow 2026-11-07 才改 8093）。
- `docs/06-testing/TEST_EXECUTION_GUIDE.md`（2026-11-11 已加「历史快照（已失效）」强横幅，明示 `mvn`/Java 17 命令不可直接执行）。
- `docs/06-testing/python-to-java-tools-migration-summary-20261021.md`、`docs/08-project-management/plans/microservice-migration-plan.md`（均已有横幅；后者 8083 为方案日期当时事实）。

### 1.3 配置核对结论（澄清一处误报）

- 全仓唯一可验证「不一致」`apex-services-bom/apex-system-service/src/main/resources/application.yml:536` Sentinel-Nacos 兜底 `127.0.0.1:18848` **经查为本地 infra 栈刻意约定，不改**：`podman-compose.infra.yml:17/78/101` 明确注释「本机 8848/9848 已被其他项目 Nacos 占用，故映射 18848/19848」，且 `apex-components-parent/apex-system/src/main/resources/application.yml:778-779` 同款兜底值并带说明注释。与全局 Nacos 注册地址（8848）分属不同用途，非笔误。
- 其余抽查（.env 端口/JWKS/Nacos 三开关、configuration.md、gateway yml）一致。

## 2. BE-046 既有图核对（结论：无 `_v2` 需要）

### 2.1 ER 图与 Flyway 迁移核对

方法：对七域 mysql 迁移按 `CREATE TABLE` 去重统计（剔除 `undo_log`/`sys_domain_event_outbox`；`IF NOT EXISTS` 伪表名剔除）。实测：system 100（`audit_log` 已由 V12 删除，按 CREATE TABLE 口径计入）、auth 19、workflow 5、file 5、office 19、integration 10、knowledge 7，跨域去重 142 张。

| 图 | 图内实体 | 当前迁移 | 结论 |
| --- | --- | --- | --- |
| 111_er_core_tables（2026-11-07） | system 核心子集 | 11-07 后 system 无新增业务表（V81+ 均为权限/菜单种子） | 一致 |
| 112_er_auth_center_tables（2026-11-07） | auth 19 + system 库 OAuth2/JWT 8 表 | auth 19、8 张 system 侧表均在 | 一致 |
| 113_er_workflow_tables | 5 表 | 5 表 | 一致 |
| 114_er_office_tables（2026-11-13） | 19 实体（含 V9/V10 撤销编排表） | 19 表 | 一致 |
| 121_er_file_tables | 5 表 | 5 表 | 一致 |

过期的是 `docs/diagrams/README.md` 的计数注（原 system 101 / office 18 / 全仓 135），已更新为 2026-11-22 实测口径并链回本记录。

### 2.2 其余核对

- MQ 图（27 号手写 SVG 为历史设计；`sequence/95_message_mq_publish_consume`、`sequence/75` 仅描述收发流程）与 BE-044 装配语义无冲突。
- `95_module_dependency_graph`（37 JAR/109 边，2026-11-16）、`128`/`129` 与根 POM 递归清单一致；`124/125/127`（2026-11-13 聚合/待办）未受本轮改动影响。
- `docs/diagrams/sequence/README.md` 补 90–95 六条登记（文件早已存在但索引缺登）。
- 01/93/94 历史快照图按既有裁决不改，当前口径由 130 号承接。

## 3. BE-047 新增 16 张图（130–145）

- 双源：`.mmd` + `.svg`（`scripts/utils/render-mermaid-svg.sh`，mermaid-cli 10）；首次渲染 2 处语法问题已修复（136 的 `||--o||` → `||--o|`、141 子图内节点连自身子图形成环）。
- 事实来源：两轮只读代码采集（每条事实带文件路径证据），关键澄清：boot-all 为多上下文启动器（各域子上下文绑定 8081–8094，**非单一 8080**，薄 jar 不含 knowledge）；office 站内信的两条路由 env 占位符 `ROUTE_OFFICE_MESSAGE_URI`/`ROUTE_OFFICE_WS_URI` 定义在 gateway application.yml（非 Java 常量）；知识域无 rerank 配置；office 无平台审批事件回调入口（撤销为同步两步编排）。
- 清单与登记：见 `docs/diagrams/README.md`「130–145」节、`docs/02-architecture/architecture-index.md` 新增节、`docs/diagrams/sequence/README.md`（133/138/145）。
- 配色：`theme:base` + 企业四色扩展登记（integration/system 青、knowledge/office 绿、masking/job/seata/storage/boot-all 琥珀、拓扑靛蓝、ER 蓝）。
- 豁免：shared 五个 infrastructure 能力 JAR 与 job/seata/storage starter 无业务表，不设 ER 图。

## 4. BE-048 注释与规则文档/扫描脚本修复

| 项 | 内容 |
| --- | --- |
| `FileAuditFieldFillHandler.java` | BE-041 后唯一 Javadoc 回归（缺 `@author`/`@since`）：补全中文类注释（对齐 office/auth 同类处理器风格；`@since 1.3.2` 按模块下一发布版口径）。纯注释改动 |
| `AGENTS.md` 0.5-2 | BE-004 措辞修正：例外实为 2 处 `@RestControllerAdvice`（`GlobalExceptionHandler`/`SentinelExceptionHandler`），当前生产代码 0 个注解式控制器。仅改第 0 章，附录 A 原文与哈希不动 |
| `docs/specs/project-governance.md` §6 | B6 更新为「生产代码 0 命中（BE-017 已完成）」；B7 补完整 Javadoc 要求与 scan-javadoc 验证口径（含必须两级嵌套路径的说明） |
| `scripts/dev/check/scan-javadoc-issues.sh` | 默认 `JAVADOC_SCAN_PATH` 一级通配 → `apex-*/apex-*/src/main/java`（修复两级嵌套布局下匹配 0 目录的假绿）；同步 `scripts/README.md` 与 `docs/04-development/development-standards.md` 用法说明 |
| `docs/05-operations/github-branch-protection.md` | 「本仓当前没有可靠的 CI 状态检查」段落刷新：已有 6 个 workflow 入仓（2026-11-16 维护），启用 Require status checks 前须先确认 Actions 实际运行通过（尚无运行成功回读证据） |
| `scripts/dev/check/check_javadoc.sh` | 头部加废弃标注并指向 scan-javadoc-issues.sh（原脚本向 CWD 落 todo 文件、路径口径过期） |
| `docs/archived/ai-sessions/zcode-plans/`（追加迁入） | 本会话 ZCode 计划缓存 `plan-sess_96a81a21-*.md` 逐字节迁入为第 15 份归档件（SHA-256 比对一致），原私有目录（原文未入库）留短指针并更新 `MIGRATED.md`/README 计数——履行 AGENTS.md 0.4「计划正文须进 docs/archived/ai-sessions/」；治理自检的私有目录 WARN 随之清零（R5 MF-01 补记） |

验证：`JAVADOC_SCAN_PATH='apex-*/apex-*/src/main/java'` 扫描四类 **0 问题**；`mvnd -pl :apex-file-service spotless:check` 通过。

## 5. 验证证据

- Javadoc 扫描（修复后脚本 + 显式两级嵌套路径）：缺类级 0 / 缺 @author 0 / 缺 @since 0 / 方法级候选 0（输出目录 `/tmp/javadoc-scan-be048`，会话本机证据）。
- `mvnd -B -pl :apex-file-service spotless:check`：BUILD SUCCESS（exit 0）。
- 全量 `mvnd -T4 -B clean verify`：**BUILD SUCCESS，45/45 项目，12:40 min**（完成于 2026-11-22T10:20:27+08:00）；测试聚合（控制台 Results 块，surefire+failsafe）Tests 6716 / Failures 0 / Errors 0 / Skipped 33（环境依赖跳过：seata starter 8、messaging 13、integration 5、system 5、office-service 1、auth-service 1）；原始日志 `/tmp/be045-full-verify.log`（本机证据）。明细见 [2026-11-22 全量验证报告](../06-testing/2026-11-22-full-verify-report.md)。
- 治理门禁（复跑）：`verify-docs.sh --strict` 0 项；`check-doc-refs.sh --strict` 0 项；`check-memory-sync.sh --strict` 0 项；`audit-backend-rules.sh --strict` 无阻断；`apex-preflight.sh --fast --strict` OK=14 WARN=4 FAIL=0；`check-doc-links.sh` 3945 链接 / 11 断链均为既有归档计划存量（gateway-resilience-enhancement-plan.md，与台账 #78 口径一致，零新增）；`git diff --check` 通过。
- **独立 R5 复核（2026-11-22，独立子代理实例）**：结论 **APPROVE_WITH_NITS**（证据与逐项核对见 [评审报告](../reviews/2026-11-22-alignment-campaign-r5-review.md)）；必改项 MF-01/02/03（均为文档记账级）已由 R0 追加清零：租约写入范围补登 `docs/specs/project-governance.md` 与 `docs/archived/ai-sessions/zcode-plans/**`、zcode-plans README 计数 14→15、验证报告计数口径指引改指模块架构评审报告溯源段。

## 6. 未验证项与限制

- 全量 verify 前草拟的图表与文档改动不参与构建；本轮除 1 处注释外无生产代码改动，`-am test` 级别的业务回归不适用。
- RAG/MQ/Seata/storage 图依据代码静态事实绘制，未在运行环境逐端点复验（图注已声明口径与证据路径）。
- `BootAllApplication` javadoc「未声明时不启动任何域」与 `BootAllDomains.resolve` 空白输入返回全部域的代码行为不一致；`KnowledgeServiceApplication` javadoc 端口 8099 与 yml/manifest 8100 不一致。本轮零代码改动红线内未修，已在图注/记录中按代码口径标注，留给后续任务。

## 7. 回滚方式

- 未提交前按文件回退：`git checkout -- <文件>`；或整体 `git diff > /tmp/before-be045.patch` 备份后还原。
- 新增图表（16 对源+导出）删除即回滚，不影响既有资产（`verify-docs.sh` 第 5 项基线只增不减）。
- 提交授权后按六组分批提交（任务卡+对齐 / 图表核对 / 新增图表 / 注释 / 规则文档与脚本 / 无配置组——18848 经核验不改，撤销原计划组），各组独立 revert。

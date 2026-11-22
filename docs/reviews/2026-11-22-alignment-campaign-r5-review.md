# BE-045~BE-048 独立 R5 评审报告（全仓对齐与图表补齐战役）

> 评审对象：BE-045（文档/脚本/配置/测试报告对齐）、BE-046（既有图核对）、BE-047（新增 130–145 共 16 张图）、BE-048（注释与规则文档/扫描脚本修复），四卡共用同一执行租约。
>
> 评审角色：独立 R5（独立子代理实例，未参与本轮实现，不代表 R0，不执行返工）
>
> 评审日期：2026-11-22
>
> 执行基线 / 当前 HEAD：`8d8dc31caf06148b908794fd9f3c61bf2c41ab12`（`dev`，两者一致，暂存于其上）
>
> 复核对象：当前暂存区（`git diff --cached`，54 文件：35 新增 + 19 修改，无删除/重命名）

## 1. 结论

## 评审结论：APPROVE_WITH_NITS

四项任务的主体交付全部核实为真：写入范围合规（除两处租约清单缺登，见 MF-01）、附录 A 哈希不变、既有图表资产零删改、16 张新图与三处索引一一对应、五项事实抽查全部与代码一致、豁免判定成立、全部治理校验与 Javadoc 扫描由本 R5 独立复跑通过。

存在 3 个 P1 必须修项，**均为文档记账性质的一行级修正，不影响已交付内容的正确性**：租约写入范围清单与暂存实际的两处缺登及战役记录漏载（MF-01）、归档索引计数笔误（MF-02）、全量验证报告中一处证据指引失准（MF-03）。按 `docs/specs/project-governance.md:281`「R0 仅在 R5 `APPROVE` 或 `APPROVE_WITH_NITS` 且必须修项清零后集成」执行：R0 清零 MF-01~MF-03 后方可按批次集成本轮暂存改动；三项均为机械修正，修正留痕后无需重新全量评审，R0 可按治理流程决定是否追加增量 R5 确认。

本评审不修改任何被复核文件，仅新增本报告；`docs/reviews/README.md` 与 `docs/README.md` 的评审索引登记属 R0 后续动作。

## 2. 评审范围与已读材料

- `docs/tasks/project-tasks.md`：BE-045~BE-048 四张任务卡（L12–L15）与「BE-045~BE-048 执行租约（2026-11-22）」块（L26–L37）。
- `docs/records/2026-11-22-alignment-campaign.md`（战役记录 §1–§7）。
- `docs/reference/ai-knowledge/07-change-memory.md` 台账 #80–#83（暂存 diff）。
- `docs/06-testing/2026-11-22-full-verify-report.md`（新文件全文）。
- 暂存区全部 54 个文件的 diff（`git diff --cached [--stat|--name-status|<path>]`），其中 16 张新图逐一核对 `.mmd` 内容/主题色与 SVG 渲染产物有效性。
- 代码事实来源：`MqLocalTransactionAutoConfiguration`、`DefaultMqLocalTransactionHandler`、`MyMetaObjectHandler`、`FileAuditFieldFillHandler` 及 file-service POM/同类 `@since`、`scan-javadoc-issues.sh`、`check_javadoc.sh`、`JsonFieldMasker.Rule`、`MaskingProperties`/`MaskingRouter`/`MaskingSecurityConfig`、integration mysql V1–V3 迁移、gateway `application.yml`、`build-manifest.yaml`。
- 结构参照：`docs/reviews/2026-11-18-be-042-r5-review.md`。

## 3. 检查清单与证据

### 3.1 写入范围合规 — PASS（附 1 项缺登，见 MF-01）

- 暂存 54 文件逐路径对照租约写入范围（`docs/tasks/project-tasks.md:32`）：50 个文件落在清单内；`apex-services-bom/apex-system-service` 暂存 diff **为空**（yml 零改动，与记录 §1.3「18848 经查为本地 infra 刻意约定不改」及任务卡结果一致）；生产代码仅 `FileAuditFieldFillHandler.java` 一个文件。
- `AGENTS.md` 暂存 diff 仅 1 个 hunk（0.5-2 一行措辞），落在「仅第 0 章 0.5-2」授权内；新措辞「例外仅 2 处 `@RestControllerAdvice`（system 的 `GlobalExceptionHandler`、`SentinelExceptionHandler`）」经 `grep -rn -E "^\s*@(Rest)?Controller\b"` 全仓 main 源码核实：注解式控制器 0 处，两个 Advice 类均存在。
- 超出租约字面清单的暂存路径共 2 处（实质均属授权内容，缺登在租约/记录侧）：
  1. `docs/specs/project-governance.md`（§6 B6/B7 更新）——BE-048 任务卡验收标准明确要求该更新（任务卡即「任务卡写入范围」的一部分），租约「写入范围」行漏列 `docs/specs/**`；
  2. `docs/archived/ai-sessions/zcode-plans/`（README 更新 + 新增 `plan-sess_96a81a21-*.md`）——ZCode 会话计划按 `AGENTS.md` 0.4-5 必须迁入 `docs/archived/ai-sessions/`（BE-042 有同类先例且当时租约明确列入 `docs/archived/**`），本轮租约与任务卡均未列，且战役记录 §1–§7 对该迁入**零记载**。

### 3.2 附录 A 哈希不变 — PASS

- `tail -n 306 AGENTS.md | shasum -a 256` 与 `git show 8d8dc31c:AGENTS.md | tail -n 306 | shasum -a 256` 均为 `aa0c25fcc3d4b32a79981efb4e01534ae68449fb320d37037263d4668d8960ed`，逐字一致；`git diff --cached AGENTS.md` 仅第 0 章 0.5-2 一个 hunk，附录 A 区域零改动。
- `scripts/dev/apex-preflight.sh --fast --strict` 亦输出 `[OK] AGENTS.md 附录 A 未被改动`。

### 3.3 图表资产保护 — PASS

- `git diff --cached --name-status -- docs/diagrams`：32 个 `A`（16 对新图源+导出）+ 2 个 `M`（`docs/diagrams/README.md`、`docs/diagrams/sequence/README.md` 两个索引）；**无 `D`，既有 `.mmd`/`.svg` 零修改**。全暂存区 name-status 仅 A/M 两类，无删除。
- 新增编号与清单吻合：130/131/132/134–137/139–144 共 13 对在 `docs/diagrams/`，133/138/145 共 3 对在 `docs/diagrams/sequence/`，合计 16 对；与 BE-047 任务卡及记录 §3 一致。
- SVG 均为真实渲染产物（抽查 130/132/140/145 均含 `<svg>` 且 13KB–89KB，非占位文件）；`.mmd` 主题色与刷新后的配色表逐一吻合（130 靛蓝、131/133/134 青、132/136 蓝、135/137/138/144/145 绿、139–143 琥珀）。
- 计数：实测 `docs/` 下 svg=263、mmd=84，与「SVG 247→263、MMD 68→84」一致；`verify-docs.sh` 第 6 项「所有 MMD 均有同名 SVG」通过（配对缺口 0）。

### 3.4 注释修复正确性 — PASS

- `git diff --cached` 该文件：12 增 1 删，旧单行英文 Javadoc 替换为中文完整类注释，**类体零改动**（`insertFill`/`updateFill` 两个方法逐行未动）。
- 注释与实现一致：插入时 `strictInsertFill` 填 `createTime`/`updateTime`/`version(0)`，更新时 `strictUpdateFill` 填 `updateTime`；strict 系 API 只填 null，注释「仅填充 null 值，业务代码已显式赋值的字段不会被覆盖」成立；与共享 `MyMetaObjectHandler`（`apex-infrastructure-persistence`）同为 strict 填充语义（差异见 N-5）。
- `@since 1.3.2` 与模块口径一致：file-service POM 版本 `1.3.1`，1.3.2 为下一发布版；模块内既有 `FileShareHandler.java:44`、`OpenFileHandler.java:38`、`FileInternalHandler.java:38`、`OpenFileRouter.java:20` 等均用 `@since 1.3.2`，属既定约定。

### 3.5 扫描器修复正确性 — PASS

- `scripts/dev/check/scan-javadoc-issues.sh:17` 默认值已改为 `SCAN_PATH=${JAVADOC_SCAN_PATH:-"apex-*/apex-*/src/main/java"}`，头部注释说明 2026-11-14 起两级嵌套布局背景。
- 布局匹配实测：`ls -d apex-*/apex-*/src/main/java` 命中 **35** 个目录；旧一级通配 `apex-*/src/main/java` 命中 **0**（zsh 报 no matches found）——「假绿」表述属实。
- 同步文档落实：`scripts/README.md`（用法注释补 JAVADOC_SCAN_PATH/JAVADOC_OUTPUT_DIR）与 `docs/04-development/development-standards.md:595-596` 均已更新；`check_javadoc.sh` 头部新增废弃标注并指向新脚本，与记录 §4 一致。
- 独立复跑 `JAVADOC_SCAN_PATH='apex-*/apex-*/src/main/java' JAVADOC_OUTPUT_DIR=/tmp/javadoc-scan-r5 bash scripts/dev/check/scan-javadoc-issues.sh`：缺类级 0 / 缺 @author 0 / 缺 @since 0 / 方法级候选 0——「四类 0 问题」复现。

### 3.6 事实抽查 — 全部 PASS

| 项 | 图/文档声明 | 代码事实 | 判定 |
| --- | --- | --- | --- |
| a. 事务消息段 | 三条件（message-queue 开启缺省 true / RocketMQTemplate 可用 / DomainEventOutboxStore 存在）自动注册默认处理器；自定义 Bean 完全覆盖让位；按 `apex-message-id`（回退 `KEYS`）查 Outbox，存在→COMMIT、不存在→ROLLBACK、无法解析→UNKNOWN | `MqLocalTransactionAutoConfiguration`：`@ConditionalOnProperty(apex.features.message-queue, matchIfMissing=true)` + `@ConditionalOnBean(RocketMQTemplate.class)` + `@Bean` 方法 `@ConditionalOnMissingBean(MqLocalTransactionHandler.class)` + `@ConditionalOnBean(DomainEventOutboxStore.class)`；`DefaultMqLocalTransactionHandler.resolve()` 三态与 header 回退顺序逐条一致 | 一致 |
| b. 130 拓扑端口 | GW 8080；auth 8081；system 8082（管理 8083）；workflow 8093；office 8084；file 8085；integration 8094（管理 8097）；aggregation 8098；knowledge 8100；job 8095；masking 8096；XXL 宿主 8099 | `build-manifest.yaml` 逐条一致（gateway 8080、workflow 8093、file 8085、office 8084、system 8082、auth 8081、job 8095、masking 8096、knowledge 8100、integration 8094、aggregation 8098；管理端口特例注释 8083/8097） | 一致（11 项） |
| c. 132 ER 表清单 | 10 张业务表（不含 undo_log），`uk tenant_id+config_key/credential_key/device_key/device_key(影子)/command_id/rule_name/dedup_key`、影子 version 乐观锁 | integration mysql V1/V2 `CREATE TABLE` 去重恰为 10 张（int_connector_config/int_credential/int_outbound_call_log/int_inbound_event/int_webhook_dedup/iot_device/iot_device_shadow/iot_telemetry/iot_command/iot_rule），V3 仅 undo_log；7 处 UNIQUE KEY 与 ER 标注一致，`iot_device_shadow.version INT NOT NULL DEFAULT 0 COMMENT '影子版本（乐观锁…）'` 在 | 一致 |
| d. 144 office 路由占位符 | `ROUTE_OFFICE_WS_URI`（`/api/v1/messages/ws` + Upgrade 头）、`ROUTE_OFFICE_MESSAGE_URI`（`/api/v1/messages,/api/v1/messages/**`），缺省均 `lb://apex-office-service`；两变量须指向同一实例 | gateway `application.yml:142-150` 两条路由逐字一致；主路由 `ROUTE_OFFICE_URI`（:157/:267）与图中 R3 一致；yml 注释「两者必须指向同一单实例（本地票据与连接注册表不跨实例共享）」与图中 COMP 框一致 | 一致 |
| e. 139 masking 配置键 | `apex.masking.jwk-set-uri/issuer/audience`；`apex.masking.versions.<v>.tenants`、`fields.<f>.type`；Map.copyOf/Set.copyOf 不可变；无持久化/无热刷新/租户仅取 JWT `tenant_id`；端点 `POST /api/v1/masking/batch`、`GET /api/v1/masking/rules` | `MaskingProperties`（`@ConfigurationProperties("apex.masking")`，record `Policy(tenants, fields)`，紧凑构造器 `Map.copyOf`/`Set.copyOf`）；`JsonFieldMasker.Rule(MaskType type, String customRule)`；`MaskingRouter` javadoc 与两条 `@RouterOperation` 路径；`MaskingSecurityConfig` `NimbusReactiveJwtDecoder.withJwkSetUri(...)` + `oauth2ResourceServer` | 一致 |

### 3.7 文档一致性 — PASS（附 3 处小疵，见 MF-02/N-1/N-2）

- 三处索引登记文件名与实际文件一一对应：`docs/diagrams/README.md`「130–145 当前口径补缺图」16 行、`docs/02-architecture/architecture-index.md` 新增节 16 行、`docs/diagrams/sequence/README.md` 新增 133/138/145 三行 + 补登 90–95 六行（90–95 六对 `.mmd`/`.svg` 实测存在）。
- BE-046 计数注刷新与记录 §2.1、台账 #81 三方一致（system 100 / auth 19 / workflow 5 / file 5 / office 19 / integration 10 / knowledge 7 / 跨域去重 142）。
- 任务卡四行状态均为「待复核」，结果表述与记录 §5、台账 #83 一致（全量 verify 45/45、Tests 6716 / 0 失败 / 0 错误 / 33 跳过、12:40 min、完成于 2026-11-22T10:20:27+08:00；跳过明细 8+13+5+5+1+1=33 内部自洽）。
- 台账 #80–#83 六字段（日期/类型/摘要/提交/证据/影响面）完整，为纯追加（#79 及以前未动），与记录 §1–§4 无矛盾；`docs/records/README.md`、`docs/06-testing/README.md`、`docs/README.md` 页脚登记同步完成。

### 3.8 豁免判定抽查 — PASS

五个豁免文件逐一验证头部横幅存在，且被豁免的旧口径行确属历史快照内容：

- `docs/03-api/next-steps-guide.md`：L3 历史快照横幅在；L215/494/500 实测为 `apex-interfaces` 相关历史内容。
- `docs/06-testing/TEST_REPORT.md`：L3 横幅在；L379 实测「端口 8083」为 2026-10-10 当时事实（workflow 2026-11-07 才改 8093）。
- `docs/06-testing/TEST_EXECUTION_GUIDE.md`：头部为「历史快照（已失效，2026-11-11 复核）……正文命令不可直接执行」强横幅，明示 `mvn`/Java 17 不可执行。
- `docs/06-testing/python-to-java-tools-migration-summary-20261021.md`：横幅在。
- `docs/08-project-management/plans/microservice-migration-plan.md`：横幅在。

豁免判定（不改正文）符合「带日期历史快照不重写」治理口径；BE-045 任务卡所写 `docs/01-guides/messaging/` 的实际修复落在 `docs/01-guides/message-middleware.md`——经查陈旧表述（「未提供处理器时框架返回 UNKNOWN」）确在后者，`messaging/` 下 `rocketmq-auto-handler-*` 的 UNKNOWN 为死信原因分析、无事务消息陈旧表述，执行者落点正确（措辞偏差见 N-4）。

### 3.9 治理校验复跑 — PASS（全部由 R5 独立执行）

| 命令 | 结果 |
| --- | --- |
| `bash scripts/governance/verify-docs.sh --strict` | 0 项问题，exit 0；图表基线自动抬升至 svg=263/mmd=84，MMD↔SVG 配对缺口 0 |
| `bash scripts/governance/check-doc-refs.sh --strict` | 0 项（无私有路径残留、无断链），exit 0 |
| `bash scripts/governance/check-memory-sync.sh --strict` | `[memory] OK：本次提交已登记记忆台账`，0 项，exit 0 |
| `bash scripts/governance/audit-backend-rules.sh --strict` | 无阻断项，exit 0（既有非阻断提示项未扩大） |
| `bash scripts/ci/check-doc-links.sh` | 断裂 11 条，全部位于 `docs/plans/archived-ai-sessions/gateway-resilience-enhancement-plan.md`（本轮未触碰的归档计划既有断链，与台账 #78「归档计划中的 11 条既有断链，未扩大修复范围」披露一致，非本轮引入） |
| `bash scripts/dev/apex-preflight.sh --fast --strict` | `OK=14 WARN=4 FAIL=0`，exit 0；WARN 均为 fast 模式跳过项与 `target/project-local-repo` 可再生副本提示（BE-035 已知行为），无阻断 |
| `scan-javadoc-issues.sh`（显式两级嵌套路径） | 四类 0 问题 |
| `git diff --cached --check` / `git diff --check` | 均干净，exit 0 |

## 4. 必须修复项（集成前由 R0 清零）

### MF-01（P1）租约「写入范围」清单与暂存实际不符，且战役记录漏载 zcode-plans 迁入

**问题**：暂存包含 `docs/specs/project-governance.md`（BE-048 卡明确要求，租约清单漏列）与 `docs/archived/ai-sessions/zcode-plans/`（AGENTS.md 0.4-5 强制归档，租约与任务卡均未列，战役记录 §1–§7 对该迁入零记载）。执行内容本身合规，缺登在治理文档侧，但不补记将使后续审计看到「暂存超出租约」而无法自洽。

**必须修**：由 R0 以追加方式补记（不改已交付文件内容）：① 在 `docs/tasks/project-tasks.md` 租约块「写入范围」补列 `docs/specs/project-governance.md`（§6 B6/B7，BE-048 卡授权）与 `docs/archived/ai-sessions/zcode-plans/**`（AGENTS.md 0.4 归档义务），或在战役记录追加「租约范围增补」段说明两路径授权依据；② 战役记录补一段 `plan-sess_96a81a21-*.md` 迁入记载（迁入任务 BE-045、目录 15 份、`.zcode/plans/` 原位留短指针）。（私有目录原文未入库）

### MF-02（P1）zcode-plans 归档 README 计数未随第 15 份更新

**问题**：`docs/archived/ai-sessions/zcode-plans/README.md` 导语仍写「原 `.zcode/plans/` 的 **14 份**会话计划原文」，而本轮新增第 15 行清单（13+1+1=15），`.zcode/plans/MIGRATED.md` 已写「15 份」。该文件本轮已触碰，计数自相矛盾属本轮引入。（私有目录原文未入库）

**必须修**：导语「14 份」→「15 份」（一词改动）。

### MF-03（P1）全量验证报告「见记忆台账」指引失准

**问题**：`docs/06-testing/2026-11-22-full-verify-report.md:16` 写「apex-system 存在双 surefire 执行块的已知口径差异（见记忆台账）」，但 `docs/reference/ai-knowledge/07-change-memory.md` 全文无 surefire/执行块条目。事实本身属实且早有权威出处：`docs/08-project-management/reports/module-architecture-review-report.md:209`「测试计数口径结论（2026-11-08 溯源）」明确记载 apex-system 双 surefire 执行块与两种口径差异 64。

**必须修**：将该注的指引改为真实出处（如「见 `docs/08-project-management/reports/module-architecture-review-report.md` 的测试计数口径结论（2026-11-08 溯源）」），一词级修正。

## 5. 非阻断建议（NITS）

1. **N-1**：`docs/README.md:280` 活导航标题「### 图表资产（247 SVG / 68 MMD…）」未随 247→263/68→84 刷新（本轮该文件仅改页脚）。建议改为「基线 247/68（2026-11-16），现状 263/84（2026-11-22）」或直接刷新数字。注：`docs/diagrams/README.md:176` 的「仓库 SVG 总数 217 → 247」属 2026-11-16 BE-001 带日期历史段，不算失真。
2. **N-2**：`docs/diagrams/README.md:221`（114 行描述「…等 18 表」）与紧邻下方已刷新的计数注（office 19，含 V9/V10）及记录 §2.1（114 图 19 实体）不一致，建议同步为 19。
3. **N-3**：记录 §7「按六组分批提交（…/无配置组——18848 经核验不改，撤销原计划组）」实际有效组为 5 组，表述绕口；R0 授权提交时按实际分组执行并写明即可。
4. **N-4**：BE-045 任务卡「`docs/01-guides/messaging/` 补…」与实际落点 `docs/01-guides/message-middleware.md` 存在措辞偏差（落点正确，见 §3.8）；后续同类卡建议直接写实际文件路径。
5. **N-5**：`FileAuditFieldFillHandler` 注释「语义与共享 `MyMetaObjectHandler` 一致」——共享处理器插入时另填充 `deleted`（本处理器不填），「一致」指严格填充（仅填 null）语义而非字段集；现句因紧邻字段枚举尚可读，可选微调为「严格填充语义一致」。

## 6. 边界声明（静态复核不代表运行期验证）

- 本评审为静态复核：未重跑全量 `mvnd -T4 -B clean verify`（执行者报告 12:40 min BUILD SUCCESS，原始日志 `/tmp/be045-full-verify.log` 为会话本机证据，事后读者不可回验）；未重跑 `spotless:check`（执行者证据 exit 0，且本轮唯一代码改动为纯注释）；未启动任何服务，未复验运行期端点/网关转发/RAG/MQ/Seata 行为——130/135/137/138/140–143 等图依据代码静态事实绘制，其运行期口径由图注与记录 §6 声明。
- 新图 SVG 仅做了产物有效性抽查（`<svg>` 标签、字节数、非空内容），未逐张目检渲染版式。
- `plan-sess_96a81a21-*.md`「逐字节一致（SHA-256 已比对）」的主张无法在迁移后独立复验（`.zcode/plans/` 原位已替换为短指针，与 BE-042 处置模式一致）；归档正文与指针、MIGRATED.md 相互印证，未见矛盾。（私有目录原文未入库）
- `git diff --cached` 之外的工作区未纳入判定（当前无未暂存改动混入本轮主题）。

## 7. 回滚与 R0 后续动作

### 回滚

按记录 §7 执行：未提交前按文件 `git checkout -- <文件>` 或按六组分批还原；新增 32 个图表文件删除即回滚（`verify-docs.sh` 第 5 项基线只增不减，删除新图不违反既有保护基线）；提交授权后各组独立 `git revert`。禁止 `reset --hard` 与 `push -f`。

### R0 后续必须完成

1. 清零 MF-01~MF-03（三项均为文档记账/一词修正，可由执行者以追加或单词修正完成，不触碰其他已交付内容）。
2. 在 `docs/reviews/README.md` 与 `docs/README.md` 登记本评审报告索引（R5 仅新增本报告，不代改共享索引）。
3. 按治理 §0.9-6：必改项清零后方可持全局集成租约逐组集成；提交信息含 `memory:` 标记并关联台账 #80–#83。
4. 集成后重跑受影响治理脚本（`verify-docs.sh --strict`、`check-doc-refs.sh --strict`、`check-memory-sync.sh --strict`）并留痕。

## 8. 评审签署

- 评审人：独立 R5（独立子代理实例，未参与 BE-045~BE-048 实现）
- 执行基线 / 复核对象：`8d8dc31caf06148b908794fd9f3c61bf2c41ab12`（HEAD 与基线一致）+ 暂存区 54 文件
- 评审结论：**APPROVE_WITH_NITS**（MF-01~MF-03 清零后方可集成）
- 本轮新增文件：`docs/reviews/2026-11-22-alignment-campaign-r5-review.md`

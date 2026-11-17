# 项目任务大盘 · apex-scaffold

> **唯一可信源**。仅主控智能体（默认 Trae）可编辑；WorkBuddy / ZCode / Qoder / Codex 只读，完成后回报主控更新状态。
> 优先级：`P0` 线上阻断 · `P1` 当期必做 · `P2` 后续排期 · `P3` 技术债/优化
> 状态：`待规划｜执行中｜待复核｜已完成｜搁置`
> 记录日期：2026-11-16（治理基线建立；基线：`docs/` 1272 文件 = 715 md + 217 svg + 68 mmd + 139 json + 68 mmd…）

## 1. 活跃任务

| ID | 优先级 | 任务 | 状态 | 负责 | 验收标准 | 关联 |
| --- | --- | --- | --- | --- | --- | --- |
| BE-040 | P1 | 【P1】根治 office/auth 的 FieldFill 失效（MetaObjectHandler 未装配） | 待复核 | ZCode（用户于 2026-11-17 授权） | 定位所有 FieldFill 失效的 PO（office 6 个、auth 2 个）并给出根治方案；修复后 create_time/update_time/version/deleted 能自动填充；不破坏模块依赖边界与既有测试；新增 AuditFieldFillHandler（office/auth 各一，属性名分别对齐模块 PO 口径）；新增填充语义回归用例；`:apex-office` 202、`:apex-auth` 1348 用例 0 失败、spotless 通过；真实环境验证移除显式赋值后时间被填充器写入；auth MFA/TOTP 写路径未实测（需外部凭据） | BE-039 遗留项 |
| BE-039 | P1 | 【P1】office-平台基址可配置并完成日历写操作端到端验证 | 待复核 | ZCode（用户于 2026-11-17 授权） | 三平台客户端基址支持 `apex.office.platforms[*].base-url` 覆盖（缺省保持官方地址，不得改变生产行为）；用本地模拟平台完成 创建→详情→更新→删除→范围查询 全链路真实验证；回归用例覆盖基址覆盖与缺省；`:apex-office` 199 + `:apex-office-service` 406 用例 0 失败、spotless 通过；**本地模拟平台端到端 6/6**（创建/详情/更新/范围/删除/删除后为空）。过程中修复三个落库缺陷（create_time、update_time、逻辑删除未生效） | BE-037/BE-038 未验证项 |
| BE-038 | P1 | 【P1】office-扩展 CalendarPort 支持详情/更新/删除与组织者换算 | 待复核 | ZCode（用户于 2026-11-17 授权） | 端口方法补父资源标识（calendarId 或 organizerId），使飞书/钉钉详情、更新、删除可用；新增「系统用户 → 平台用户」换算（复用 office_organization_mapping），避免用系统 ID 调平台；保留 fail-closed 与租户隔离；新增回归用例（4 组）；`:apex-office` 198 用例 0 失败、spotless 通过；真实链路：无绑定→「未绑定 feishu 平台账号」，插入绑定→「飞书 appId未配置」，换算与路由已通；真实创建仍需平台凭据 | 前端 FE-026；BE-037；[复核清单](../reviews/2026-11-17-integration-review-checklist.md) |
| BE-037 | P1 | 【P1】office-补齐三平台日历客户端实现 | 待复核 | ZCode（用户于 2026-11-17 授权） | 实现 feishu/dingtalk/wecom 三个 CalendarClient（端口 9 方法：创建/详情/更新/取消/删除/范围列表/会议室可用性/平台标识）；Bean 名 `<platform>CalendarClient` 与 `OfficeCalendarService.getCalendarClient` 一致；未配置凭据时 fail-closed 且错误可读；真实创建链路可跑通或明确外部依赖；新增单测覆盖请求构建与错误映射（6 条）；spotless 与模块测试通过（195 用例 0 失败）；真实网关三平台均返回凭据未配置而非「不支持的办公平台」，证明 Bean 查找链路已通。**限制**：详情/更新端口签名缺组织者，已显式拒绝；真实创建需平台凭据，本地未闭环 | 前端 FE-026；记录 §7.2；[复核清单](../reviews/2026-11-17-integration-review-checklist.md) |
| BE-036 | P1 | 【P1】联调-修复快照兼容、菜单范围与审批租户传播 | 待复核 | ZCode（用户于 2026-11-17 授权为本项主控） | 验收：`:apex-system`/`:apex-office`（189 用例）/四个服务模块 `-am test` 均 BUILD SUCCESS 且 0 失败；二轮修审批响应 `processName`/`currentNode`/`createdAt` 恒 null（实体与 DTO 命名不一致致 MapStruct 静默丢字段），`:apex-office-service` 406 用例 0 失败，提交 `102fa274`；缺租户拒绝、JDBC 上下文恢复、空菜单全量、快照 roles/未知字段解码均有回归用例；真实环境租户隔离已验证（租户1 仅见自身记录、跨租户查询返回不存在）；最终重建环境后经网关 18 条真实接口 18/18 通过 | [方案](../specs/2026-11-17-integration-contract-repair.md)；前端 FE-026；[复核清单](../reviews/2026-11-17-integration-review-checklist.md) |

| BE-035 | P2 | 根 target 额外构件副本定向清理与项目内本地仓库防再现守卫 | 已完成 | 主控 Trae | 守卫接入 build.sh/release.sh/apex.sh 并经 BuildPathContractTest 9/9 验证；preflight 仓库位置与副本双检通过（OK=15 WARN=3 FAIL=0）；`target/project-local-repo` 125 副本 1.4G + 44 .DS_Store 已定向清理；不改业务代码/POM、未提交 | `scripts/lib/common.sh`、`scripts/build/build.sh`、`scripts/release/release.sh`、`apex-tools-parent/apex-tools-build/bin/apex.sh`、`scripts/dev/apex-preflight.sh`、台账 #40/#41 |

## 2. 本期已完成（治理基线）

| ID | 优先级 | 任务 | 状态 | 完成日期 | 产出 |
| --- | --- | --- | --- | --- | --- |
| BE-034 | P1 | 目录规范化：docs 目录总览 + 7 个一级目录 README + 目录结构自检 | 已完成 | 2026-11-16 | 验收：`verify-docs.sh` 9 项 0 问题、`check-doc-layout.sh` 4 条断言全 OK（25 个一级目录均有 README 且被总览覆盖）、`check-doc-refs.sh` 断链 0；产出：`docs/README.md`「🗂 目录总览」、`docs/{_archive,api,architecture,archived,fix-reports,migration,reference}/README.md`、`scripts/governance/check-doc-layout.sh`、规范 §3.7、`docs/plans/2026-11-16-directory-normalization-plan.md` |
| BE-010 | P1 | AGENTS.md 增补第 0 章治理规范（附录 A 原文逐字保留，SHA-256 已比对） | 已完成 | 2026-11-16 | `AGENTS.md` |
| BE-011 | P1 | 建立 `docs/{tasks,specs,plans,records,reviews,notes}` 标准目录与索引（既有编号目录不动） | 已完成 | 2026-11-16 | `docs/README.md`、各目录 README |
| BE-012 | P1 | 迁移 `.trae/documents` 4 份设计文档 → `docs/plans/archived-ai-sessions/`（逐字节一致后移除原件，留指针） | 已完成 | 2026-11-16 | `docs/records/2026-11-16-docs-governance.md` |
| BE-013 | P1 | 治理自检脚本与 pre-commit 钩子（警告模式，不阻断） | 已完成 | 2026-11-16 | `scripts/governance/`、`.githooks/` |
| BE-014 | P1 | 迁移 `.workbuddy/knowledge` 7 份知识库文档 → `docs/reference/ai-knowledge`（逐字节一致，仅修相对链接） | 已完成 | 2026-11-16 | `docs/reference/ai-knowledge/PROVENANCE.md` |
| BE-015 | P1 | 迁移 `.zcode/plans` 13 份会话计划 → `docs/archived/ai-sessions/zcode-plans`（逐字节一致） | 已完成 | 2026-11-16 | `docs/archived/ai-sessions/zcode-plans/README.md` |
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

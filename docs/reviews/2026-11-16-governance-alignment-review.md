# 治理规范对齐评审报告（前后端两仓 · 2026-11-16）

| 项 | 值 |
| --- | --- |
| 评审对象 | `apex-admin-web`（main `1455fbc`）、`apex-scaffold`（main `14f6bc2a`） |
| 评审基线 | 前端 `1455fbc`（35 文件治理提交）、后端 `14f6bc2a`（含 BE-019 纳入） |
| 评审依据 | 两仓 `docs/specs/project-governance.md` v1.1（后端 294 行、前端 289 行）+ `AGENTS.md` 第 0 章 |
| 评审方法 | 治理脚本 + 既有 CI 脚本 + 逐文件正则 + Markdown 链接解析 + 私有目录遍历 + git 状态核对（共 34 个检查点） |
| 评审人 | R5 评审角色（主控智能体代行，与实施者同一会话，属**有条件通过**，需业主复核签字） |
| 评审日期 | 2026-11-16 |
| 总体结论 | **有条件通过（CONDITIONAL APPROVE）**：结构与资产层全部达标；文档内容层有 P1 偏差 5 项、P2 5 项、P3 4 项，须按 `docs/plans/2026-11-16-governance-alignment-plan.md` 整改后复审 |

> 本报告只做判定与取证，**未改动任何项目文件**（除本报告自身）。整改动作全部写在配套方案文档中，待业主确认后执行。

---

## 1. 评审范围与方法

### 1.1 检查点设计（对齐规范条款）

| 规范条款 | 检查点 |
| --- | --- |
| §2 任务体系 | 大盘存在、ID 唯一不跳号、状态词合法（仅五值）、必填字段（验收标准） |
| §3.1 六目录 | 六目录存在 + 各 `README.md` + `docs/README.md` 登记 |
| §3.3 唯一正文 | 迁移后原目录是否仅剩指针、`MIGRATED.md` 是否齐备 |
| §3.4 命名 | 新文档命名是否符合 `YYYY-MM-DD-主题[-plan|-review].md` |
| §3.5 图表保护 | SVG/MMD 计数 ≥ 基线、`docs/diagrams` 主库完整、源/导出配对 |
| §4.1–4.2 私有目录 | 私有目录内是否残留业务正文、规则镜像是否与 `AGENTS.md` 一致 |
| §4.4 角色卡 | `docs/specs/ai-agents/` R1–R5 齐备 |
| §5.1 忽略规则 | `.gitignore` 无忽略 `docs` 规则、私有目录全覆盖、忽略项带注释 |
| §5.2–5.3 提交纪律 | 钩子启用、工作区干净、一类改动一提交、分支在 `main` |
| §6 红线 | `audit-*.sh` 阻断项与例外登记一致 |
| §7 变更纪律 | 每次改造在 `docs/records/` 有留痕、回滚命令可用 |
| §8 工具 | 脚本存在可执行、脚本说明与实现一致 |
| 交叉项 | 活文档指针可达性、Markdown 链接有效性、规范正文自述与现状一致 |

### 1.2 取证手段（可复现）

```bash
# 治理自检
cd /Users/work/apex-scaffold   && bash scripts/governance/verify-docs.sh
cd /Users/work/apex-admin-web   && bash scripts/governance/verify-docs.sh
# 红线扫描
cd /Users/work/apex-scaffold   && bash scripts/governance/audit-backend-rules.sh
cd /Users/work/apex-admin-web   && bash scripts/governance/audit-frontend-rules.sh
# 既有 CI 文档检查（后端）
cd /Users/work/apex-scaffold   && bash scripts/ci/check-doc-links.sh
cd /Users/work/apex-scaffold   && bash scripts/ci/check-doc-consistency.sh
# 资产与状态
find docs -name '*.svg' | wc -l ; find docs -name '*.mmd' | wc -l
git status --short ; git remote -v ; git config core.hooksPath
```

---

## 2. 规模快照（评审时实测）

| 指标 | apex-admin-web | apex-scaffold |
| --- | --- | --- |
| `docs/` 受版本控制文件 | 73 | 1279 |
| Markdown | 73 | 760 |
| SVG / MMD | 0 / 0（本仓无图表，规范 §9.3 已声明） | **217 / 68**（与基线一致） |
| 任务大盘条目 | 12（待规划 7 / 已完成 5，最大 FE-014） | 17（待规划 8 / 已完成 9，最大 BE-019） |
| `docs/records/` 文件 | 2（README + 2026-11-16） | 2（README + 2026-11-16） |
| `docs/reviews/` 文件 | 1（仅 README） | 1（仅 README，本报告为第 2 篇） |
| 分支 / 远端 | `main` / **0 个远端** | `main` / **0 个远端** |
| 工作区 | 干净 | 干净 |
| 钩子 | `core.hooksPath=.githooks` 已设 | 同 |

---

## 3. 对齐项（达标，逐条留证）

| # | 条款 | 检查结果 | 证据 |
| --- | --- | --- | --- |
| 1 | §3.1 六标准目录 | ✅ 两仓 `tasks/specs/plans/records/reviews/notes` 全部存在且各有 `README.md` | 目录遍历 |
| 2 | §3.1 `docs/README.md` 登记 | ✅ 六目录 + `project-governance.md` 均在索引内 | `grep` 命中 7/7 |
| 3 | §3.3 私有目录只留指针 | ✅ 后端 `.trae/documents` 4 份 = 3 行指针（各 256–262 字节）+ `MIGRATED.md`；`.workbuddy/knowledge`、`.workbuddy/reports`、`.zcode/plans` 均只剩 `MIGRATED.md` | `wc -c` + `head -1` |
| 4 | §3.3 迁移正文入库 | ✅ `docs/plans/archived-ai-sessions/` 4 份、`docs/reference/ai-knowledge/` 7 份、`docs/archived/ai-sessions/zcode-plans/` 13 份、周期报告 3 份 | 目录清单 |
| 5 | §3.5 图表保护 | ✅ 后端 SVG **217**、MMD **68**，与规范门槛一致；主库 `docs/diagrams` 未动 | `find` 计数 |
| 6 | §3.5 源/导出配对 | ⚠️ 已知存量 30 个 `.mmd` 缺同名 `.svg`，已登记 BE-001，未新增缺口 | `verify-docs.sh` 第 6 项 |
| 7 | §4.1 私有目录禁止业务正文 | ✅ `.trae`（5 md：4 指针 + MIGRATED + 40 行规则镜像）、`.workbuddy`（memory + `MIGRATED.md`）、`.zcode`（`MIGRATED.md`）无正文 | 遍历 + 字节数 |
| 8 | §4.4 角色卡 | ✅ 两仓 `docs/specs/ai-agents/` R1–R5 齐备（后端 r2-backend-implementer、前端 r2-frontend-implementer） | 文件清单 |
| 9 | §5.1 不忽略 `docs` | ✅ 两仓 `.gitignore` 无任何 `docs` 忽略规则；`git check-ignore docs/README.md` 返回未忽略 | `git check-ignore` |
| 10 | §5.1 私有目录整体忽略 | ✅ `.trae/.workbuddy/.zcode/.qoder/.codex/.claude/.agents/.ai` 8 项两仓均覆盖（前端另有 `.trae-html-share-packages/`） | `grep .gitignore` |
| 11 | §5.1 忽略项带注释 | ✅ 两仓均有「AI 私有目录（正文在 docs/）」标记块 | `.gitignore` 尾部 |
| 12 | §5.2 钩子 | ✅ 两仓 `core.hooksPath=.githooks`，`pre-commit` 存在且可执行、警告不阻断 | `git config` + 提交实跑 |
| 13 | §5.2 一类改动一提交 | ✅ 后端 `19731479`（治理）、`14f6bc2a`（3 项纳入）分离；前端 `145fbc` 单一治理提交 | `git log` |
| 14 | §5.3 分支 | ✅ 两仓均在 `main`，无游离分支 | `git branch` |
| 15 | §6 后端红线 | ⚠️ 1 项阻断：`VirtualThreadBoundedExecutor.java:72` 手写 Logger（登记 BE-017，属已知例外）；`@Autowired`、`@Controller`、`BeanUtils` 均 0 | `audit-backend-rules.sh` |
| 16 | §6 前端红线 | ✅ 0 阻断；`: any` 51 处登记 FE-005；越权 Axios 0、绕过 `auth.ts` 0 | `audit-frontend-rules.sh` |
| 17 | §7 留痕 | ✅ 两仓 `docs/records/2026-11-16-docs-governance.md` 含「做了什么 / 验证 / 回滚」；后端另有 §10 三次增补 | 文件内容 |
| 18 | §7 回滚命令 | ✅ 两仓 records 第 7 节给出 `git revert` 与自检复跑命令 | 同上 |
| 19 | §8 脚本与说明 | ✅ `verify-docs.sh`、`audit-*.sh`、`README.md` 齐备；两仓 `README` 描述与实现一致（含 `--strict` 语义、bash 3.2 注意项） | 脚本与 README 对读 |
| 20 | `AGENTS.md` 入口 | ✅ 两仓第 0 章齐备，附录 A 原文哈希未变（前端 `ae5e19db…53a` / 712 行；后端 `aa0c25fc…60ed` / 306 行） | `tail -n N \| shasum -a 256` |
| 21 | 命名规范 §3.4 | ✅ 新增文档命名合规（评审/计划/记录/角色卡/知识库） | 文件名清单 |
| 22 | 功能零影响 | ✅ 前端 `src/`、后端 `.java`/`pom.xml`/`.sdkmanrc` 全程 0 改动 | `git show --name-only` + `git status` |

---

## 4. 不符合项（分级登记）

### 4.1 P1 —— 规范自述与现状不一致、活文档指针失效（必须本迭代修）

| 编号 | 位置 | 现象 | 证据 | 影响 |
| --- | --- | --- | --- | --- |
| RV-01 | `apex-scaffold/docs/specs/project-governance.md:175` | 仍写「`MEMORY-CARD.md`、`daily/README.md`、`2026-11-16-all-projects-daily-report.md` 属他人工作，**全程未动、也未纳入提交**」；实际已在 `14f6bc2a` 按规范复核后纳入 | `grep -n "全程未动"` | 规范自相矛盾；后续智能体会据此「不敢提交」，或误判版本库状态 |
| RV-02 | `apex-scaffold/docs/specs/project-governance.md:229,251-256` | §9.1/§9.3 任务范围停在「BE-001~007、BE-010~013」，实际大盘已含 BE-014~019 | 任务盘 `grep` | 规范正文与大盘脱节 |
| RV-03 | `apex-admin-web/docs/specs/project-governance.md:230,248-252` | §9.1 写「FE-010~013」，实际大盘含 FE-014（规范正文入仓） | 任务盘 `grep` | 同上 |
| RV-04 | 后端 **25 篇活文档 / 56 行** 指向已迁移或不存在的私有路径（详见 §4.4 清单） | 例：`docs/01-guides/新对话启动文档.md`（5 行）、`docs/reference/ai-knowledge/README.md`（4 行，指向**跨仓** `apex-admin-web/.workbuddy/knowledge/`，该目录已迁）、`docs/08-project-management/periodic-reports/{README,daily,weekly,monthly}/README.md`（共 5 行，指向已迁的 `.workbuddy/knowledge/`） | 正则扫描 + `check-doc-links.sh` | 新会话按图索骥会走进 gitignore 目录或死链；违反 §3.3「其余位置用链接引用」的可达性前提 |
| RV-05 | `apex-scaffold/.trae/rules/project_rules.md`（40 行） | 规则镜像只覆盖构建环境（JDK/mvnd），**未含** §6 十条红线、§3 文档收口、§4 多智能体边界 | `grep -c '@Autowired\|@Controller'` = 0、`grep -c 'docs/'` = 0 | 违反 §4.2.3「镜像不得与 `AGENTS.md` 冲突」的实质（缺项即失真）；Trae 会话读到的规则比仓库旧 |

### 4.2 P2 —— 流程与工具缺口（排期修）

| 编号 | 位置 | 现象 | 证据 | 影响 |
| --- | --- | --- | --- | --- |
| RV-06 | `scripts/governance/verify-docs.sh`（两仓） | 自检 7 项均不检查**链接可达性**与**私有路径残留**；而后端已有 `scripts/ci/check-doc-links.sh`（可查断链与 gitignore 目标），治理自检未接入 | `grep -c '断链\|check-doc-links'` = 0 | RV-04 这类偏差只能靠人工评审发现，无法在提交时自动拦住 |
| RV-07 | 后端根目录 `aggregation-optimization-plan.md`、`architecture-review-report.md` | 规范 §9.3 已登记「未擅动，先出方案再迁移」，本轮仍留在根目录 | `ls *.md` | 违反 §3.1「新产出归位六目录」的边界感；根目录散落文档持续被引用会加深耦合 |
| RV-08 | 两仓 `docs/reviews/` | 只有 `README.md`，**零评审记录**；规范 §3.1 定义该目录承载评审，实际评审从未落盘（本报告为首篇） | `ls docs/reviews` | 违反 §2.5 DoD「复核人给出结论」的留痕要求；无评审历史可追溯 |
| RV-09 | 两仓 `docs/records/` | 仅 1 篇 2026-11-16 记录；BE-011/012/013/015/016/018 等卡号未逐条出现在记录中（由整体记录覆盖） | `grep -l "BE-01x"` = 0 | §2.5.5「每卡一条记录」与实操「一批一条」口径不一致，需在规范中定口径 |
| RV-10 | 两仓 git 配置 | `git remote` = **0 个** | `git remote -v` | 无异地副本，违反可追溯原则的物理前提；规范 §5.3 只写分支未写远端策略 |

### 4.3 P3 —— 历史与豁免口径（收尾修）

| 编号 | 位置 | 现象 | 影响 |
| --- | --- | --- | --- |
| RV-11 | 后端 15 条断链（`docs/_archive/reports/2026-09-03-documentation-migration.md` 12 条、`docs/reports/INTERNAL_LINK_FIX_REPORT.md` 3 条） | 全部位于历史归档；规范未写「历史归档断链豁免」口径 | 每次自检/评审需人工判断，易被误判为新增违规 |
| RV-12 | 前端 3 条断链（`docs/archived/project-reports/*.md` → `../../../../AGENTS.md`） | 相对层级多一层（应为 `../../../AGENTS.md`） | 归档报告里的「回 AGENTS.md」链接失效；属易修项 |
| RV-13 | `docs/reference/ai-knowledge/README.md:19,21,67,68` | 链接指向 `.workbuddy/rag/`、`.workbuddy/ops/`（gitignore 目标，本地存在但永不入库） | 换机器后必断；应改为「私有目录，见仓库外」的说明而非链接 |
| RV-14 | `CHANGELOG.md:76`、`docs/reports/javadoc-work-summary.md:1380`、`docs/reports/项目清理执行报告-2026-09-20.md`、`docs/reports/project-cleanup-execution-report-2026-09-20.md` | 历史记录引用已消失的 `.trae/documents/*` 文件 | 历史语义真实，宜保留原文并加「已迁至 docs/…」脚注，不删改正文 |

### 4.4 RV-04 明细清单（后端 25 篇活文档，按修正优先级排序）

| 档 | 文件 | 行 | 建议动作 |
| --- | --- | --- | --- |
| A（入口/规范类，先修） | `docs/01-guides/新对话启动文档.md` | 84, 94, 211, 235, 242 | 指针改指 `docs/` 实际路径 |
| A | `docs/reference/ai-knowledge/README.md` | 14, 15, 52, 53 | 跨仓指针改指 `apex-admin-web/docs/reference/ai-knowledge/` |
| A | `docs/reference/ai-knowledge/04-admin-web.md` | 4 | 同上 |
| A | `docs/reference/ai-knowledge/05-cold-chain.md` | 4 | 同上（冷链仓另行登记） |
| A | `docs/08-project-management/periodic-reports/README.md` | 90, 92 | `.workbuddy/knowledge/` → `docs/reference/ai-knowledge/` |
| A | `…/periodic-reports/daily/README.md` | 36 | 同上 |
| A | `…/periodic-reports/weekly/README.md` | 22 | 同上 |
| A | `…/periodic-reports/monthly/README.md` | 18 | 同上 |
| A | `docs/plans/archived-ai-sessions/microservice-phase5-6-extraction-design.md` | 5 | 同目录已有 `microservice-migration-execution-plan.md`，改指同目录文件 |
| A | `docs/08-project-management/plans/microservice-migration-plan.md` | 9 | 改指 `docs/plans/archived-ai-sessions/` |
| B（指南/清单类） | `docs/08-project-management/checklists/phase-2-3-delivery-checklist.md` | 229,232,235,238,421,422 | `.trae/documents/phase-2-3-*` 多数已不存在 → 改指 `docs/08-project-management/plans/` 或标「原文件未入库」 |
| B | `docs/08-project-management/phase-2-3-delivery-checklist.md` | 229,232,235,238,421,422,423 | 同上（与上表为重复副本，须同时修） |
| B | `docs/08-project-management/phase-2-3-quick-guide.md` | 437,438,439 | 同上 |
| B | `docs/08-project-management/quick-guides/phase-2-3-quick-guide.md` | 437,438,439 | 同上（重复副本） |
| B | `docs/08-project-management/checklists/microservice-acceptance-checklist.md` | 4 | 改指 `docs/plans/archived-ai-sessions/microservice-migration-execution-plan.md` |
| B | `docs/04-development/DATABASE_CONFIG_BACKEND_IMPLEMENTATION.md` | 276 | 绝对路径 `.trae/…` + 目标不存在 → 标「方案原文未入库」 |
| B | `docs/04-development/DATABASE_CONFIG_QUICK_REFERENCE.md` | 184 | 同上 |
| B | `docs/07-commercial/COMMERCIAL_OPTIMIZATION_PROPOSAL.md` | 6,13,14 | 3 个目标均不存在 → 标「原文件未入库，见 docs/07-commercial」 |
| B | `docs/08-project-management/plans/后续实施快速指南.md` | 148,287,293,299,303 | 逐条改指或标注 |
| B | `docs/08-project-management/plans/MeilisearchAdapter完善实施指南.md` | 71 | 同上 |
| B | `docs/08-project-management/plans/RAG知识库评审方案.md` | 52 | 同上 |
| B | `scripts/dev/check/QUICKREF-springdoc-check.md` | 161 | 改指 `docs/_archive/trae-documents/SpringDoc注解完善工作总结.md`（该文已归档） |
| C（历史报告） | `docs/reports/javadoc-work-summary.md` | 1380 | 加脚注「原文未入库」，不改结论 |
| C | `docs/reports/project-cleanup-execution-report-2026-09-20.md` | 233 | 同上 |
| C | `docs/reports/项目清理执行报告-2026-09-20.md` | 106,293,607 | 同上 |

> 前端侧仅 1 篇：`docs/features/database-config.md:153` 指向后端 `.trae/documents/前端配置数据库多适配方案.md`（不存在），应改为「方案原文未入库，联系后端负责人」或指向 `apex-scaffold/docs/07-commercial/`。

---

## 5. 需业主决策的 6 个开关

| # | 决策点 | 选项 | 默认建议 |
| --- | --- | --- | --- |
| D1 | RV-11/RV-14 历史归档断链 | ①规范登记豁免，不改内容 ②逐条修链接 | **①**（历史真实性优先） |
| D2 | RV-07 根目录 2 份文档 | ①迁入 `docs/plans/` 留指针 ②继续留在根目录并登记 | **①**（附 SHA 校验） |
| D3 | RV-06 自检脚本增强 | ①接入断链+私有路径扫描（带白名单） ②只加告警不介入 | **①**（`--strict` 才阻断） |
| D4 | RV-10 远端 | ①两仓各加一个远端并推送 ②保持本地并做定期离线归档 | 需提供仓库地址后选 **①** |
| D5 | RV-09 records 粒度 | ①明确「同批次可合并一条，须列全卡号」 ②每卡一条 | **①**（写入规范） |
| D6 | RV-05 `.trae` 规则镜像 | ①补齐红线与收口条款（约 60 行） ②改为纯指针指向 `AGENTS.md` | **①**（Trae 需自包含规则） |

---

## 6. 复审准入条件

1. D1–D6 全部有结论（默认方案视为通过）。
2. 批次 1、2 执行完毕：RV-01~RV-05 清零。
3. `verify-docs.sh`（两仓）通过；`audit-backend-rules.sh` 阻断项仍只有 BE-017（或已修）；`check-doc-links.sh` 断裂链接 ≤ 15 且新增为 0。
4. 图表 `svg=217 / mmd=68` 不变；`AGENTS.md` 附录 A 哈希不变。
5. 新增记录：`docs/records/2026-11-16-governance-alignment-remediation.md`。

---

## 7. 附录：关键原始输出（评审时）

```text
[apex-admin-web] verify-docs.sh        → ✅ 0 项问题
[apex-admin-web] audit-frontend-rules  → ✅ 无阻断（: any 51 处，FE-005）
[apex-scaffold]  verify-docs.sh        → ⚠️ 1 项问题（30 个 MMD 缺同名 SVG，存量 BE-001）
[apex-scaffold]  audit-backend-rules   → 1 项阻断（VirtualThreadBoundedExecutor.java:72，BE-017）
[apex-scaffold]  check-doc-links.sh    → Markdown 793 / 链接 3750 / 断裂 15
[apex-scaffold]  check-doc-consistency → 错误 0 / 警告 0
[apex-scaffold]  charts                → svg=217 mmd=68
[apex-admin-web] charts                → svg=0   mmd=0（本仓无图表）
两仓 git status → 干净；git remote → 0 个
```

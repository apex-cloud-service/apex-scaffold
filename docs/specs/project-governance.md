# 项目任务管理规范（apex-scaffold · 规范正文）

| 项 | 值 |
| --- | --- |
| 文档版本 | v1.2（2026-11-18 多智能体协作增补版） |
| 生效日期 | 2026-11-18 |
| 适用范围 | apex-scaffold 全部模块与目录、全部参与者（人 + 任意 AI 智能体） |
| 权威关系 | 本文件与 `AGENTS.md` 第 0 章同源：`AGENTS.md` 为入口索引，本文件为可独立阅读的完整正文；技术事实以 `AGENTS.md` 附录 A、`pom.xml`、`README.md` 与代码为准 |
| 维护人 | R0 协调智能体（由用户指定）；变更需在 `docs/records/` 与记忆台账留痕 |

---

## 1. 设计原则

1. **可追溯**：任何改动都能回答「哪个任务、谁批的、验收怎么过的」。
2. **不散落**：业务文档只有一个家 —— `docs/`；AI 私有目录只放工具自身的配置与缓存。
3. **不打架**：多个 AI 工具并存时，靠「目录边界 + 单一正文」避免各自一份口径。
4. **不破坏**：改造优先「新增 + 留指针」，禁止「移动 + 删原件」；**图表资产零删除**；功能改动与文档治理不同批提交。
5. **可并行**：只并行依赖已满足、写入范围互斥、验收相互独立的任务；共享文件与高风险契约默认串行。

---

## 2. 任务体系

### 2.1 优先级（四档，无第五档）

| 级别 | 判定标准 | 响应要求 |
| --- | --- | --- |
| **P0** | 服务不可用、鉴权/网关故障、构建失败、数据错误、安全漏洞 | 立即停止其他任务，当天修复 |
| **P1** | 阻塞他人任务、接口契约不兼容、踩编码红线（`@Controller`/`@Autowired`/手动 Logger） | 当期迭代内完成 |
| **P2** | 功能增强、性能优化、模块边界整改、技术债清理 | 排期执行 |
| **P3** | 文档完善、可读性、工具链与图表现有缺口补齐 | 有空档时执行 |

### 2.2 五状态流转

```text
待规划 → 执行中 → 待复核 → 已完成
                    ↘ 搁置（需写明搁置原因与复活条件）
```

- 状态值只有这五个，禁止自造。
- `待规划 → 执行中`：仅当 R0、执行者、复核者、基线 SHA、写入范围、并行边界和验收命令齐全，且租约已登记。
- `执行中 → 待复核`：仅当标准交接包完整、验证命令及结果可复现。
- `待复核 → 已完成`：仅当独立复核通过、R0 已完成集成、记录与记忆台账已落地。
- `待复核` 必须指定复核人（人或另一 AI 角色，见 §4.4）；复核者不得自审自放。
- `搁置` 必须写：原因、复活条件、最迟复核日期。

### 2.3 命名与拆卡

- 任务命名：`【Px】模块-动作对象｜关联单号（可选）`，例：`【P1】apex-system-补齐 Handler SpringDoc 注解`。
- **单任务单职责**：一个会话只承接一件独立交付任务。
- 拆卡规则：跨 `apex-components-parent` / `apex-services-bom` / `apex-shared-parent` / `apex-business-parent` 多个分组的改动，必须按模块拆卡。
- 禁止「顺手重构」：任何非任务目标的改动，登记为独立卡。

### 2.4 任务卡必填字段

| 字段 | 要求 |
| --- | --- |
| 编号 | `BE-nnn`，不复用、不跳号占位 |
| 优先级 | P0–P3 |
| 目标 | 一句话，动词开头，≤40 字 |
| 状态 | 五状态之一 |
| R0 协调者 | 唯一写入任务盘、调度和最终集成的责任人；用户指定优先，未指定不得并行写入 |
| 执行者 / 复核者 | 执行者负责落在写入范围内的交付；复核者必须与执行者为不同智能体实例/会话 |
| 基线版本 | 任务开工时的 `dev` commit SHA；不得用“最新代码”代替 |
| 写入范围 | 允许修改的模块/文件/目录清单，并列出禁止触碰的共享文件与高风险路径 |
| 并行边界 | 依赖任务、可并行任务、必须串行的共享文件；证明与所有活跃任务写范围互斥 |
| 执行环境 | 共享 checkout（串行单写者写入；只读审查）或独立 worktree/短分支（并行写任务） |
| 验收标准 | **可执行**（命令 + 期望结果），如 `mvnd -pl :模块 -am verify` 通过、`verify-docs.sh` 通过 |
| 影响面 | 模块/文件清单，标注是否触碰红线与契约 |
| 项目校验 | 明确回归范围（单模块 / 全量 / 仅文档） |
| 交接物 | 标准交接包与本任务应产出的记录/评审/测试证据 |
| 全局集成租约 | `NOT_REQUIRED`（串行单任务）、`HOLDING`（已持有）或 `WAITING`（等待其他 R0 释放）；未取得前不得写 `dev` |

> 单写者串行小任务可将 R0、执行者合并，但复核者仍须独立；并行任务禁止合并 R0、执行者和复核者。

### 2.5 完成判定（DoD，缺一不可）

1. 改动完成且仅覆盖任务目标。
2. 验证通过：`source scripts/build/init-build-env.sh` 后 `mvnd -pl :受影响模块 -am verify`（跨模块改动用全量 `mvnd -T4 -B clean verify`）。
3. 不违反第 6 章红线；红线违规必须显式登记例外（见 BE-004、BE-017）。
4. 文档与索引同步（新增文档已登记 `docs/README.md`）。
5. 在 `docs/records/` 留一条变更记录；**同批次一条记录须列全本批次涉及的全部任务卡号**（如批次 1–6 须列 BE-020 涉及的 RV-01~RV-14 与同批次其它卡号），不得只写首张卡。

### 2.6 大盘维护权与唯一协调者

- 每个任务必须有且只有一个 R0；R0 可由用户指定任意 AI 或人，不由工具名称自动获得权限。未指定 R0 时，任务保持 `待规划`，不得启动并行写入。
- `docs/tasks/project-tasks.md`、任务状态变更、`docs/reference/ai-knowledge/07-change-memory.md` 与最终集成权默认归各任务的 R0；同一时刻只有一个 R0 持有全局集成租约并写这些共享文件，执行者只读任务盘，完成后提交标准交接包。
- 不同任务可由不同 R0 负责；R0 对其任务卡负责，不得借任务级权限绕过共享文件与 `dev` 的全局串行租约（见 §4.10）。
- 同一写入范围同时只能有一个 `执行中` 写任务。R0 必须先串行化或拆分；不得以「先写盘者为准」放任并发覆盖。
- 执行者发现 R0 未给出写入范围、基线或复核者时，应停止写操作并回报缺口，不得自行补权或扩大范围。
- 并行只读分析可共享 checkout；并行写入必须满足 §4.7 的准入矩阵。

---

## 3. 文档收口

### 3.1 六个标准目录（新产出归位）

| 目录 | 用途 | 唯一入口 |
| --- | --- | --- |
| `docs/tasks/` | 任务大盘、迭代清单 | `docs/tasks/project-tasks.md` |
| `docs/specs/` | 需求规格、技术方案、契约设计、本规范正文 | `docs/specs/README.md` |
| `docs/plans/` | 实施计划、重构拆解、迁移计划 | `docs/plans/README.md` |
| `docs/records/` | 变更记录、改造留痕、缺陷复盘 | `docs/records/README.md` |
| `docs/reviews/` | 评审记录与检查清单结果 | `docs/reviews/README.md` |
| `docs/notes/` | 调研、对接备忘、风险纪要 | `docs/notes/README.md` |

### 3.2 既有目录继续有效（不搬迁、不改名、不改内容）

- 编号目录：`00-getting-started`、`01-guides`、`02-architecture`、`03-api`、`04-development`、`05-operations`、`06-testing`、`07-commercial`、`08-project-management`
- 专题目录：`architecture`、`api`、`cache`、`diagrams`、`migration`、`reports`、`fix-reports`、`_archive`
- 归属判断：已成体系的历史材料留在原编号目录；**新产出**按 §3.1 归位，属既有分类的放回既有分类。

### 3.3 唯一正文原则

- 同一结论只存一份正文，其余位置用链接引用。
- 迁移文档必须：先建新版 → SHA-256 校验一致 → 修引用链接 → 删原件 → 原目录留 `MIGRATED.md` 指针（历史引用多时保留同名短指针文件）。

### 3.4 命名规范

| 类型 | 命名 |
| --- | --- |
| 方案/规格 | `YYYY-MM-DD-主题.md` |
| 计划 | `YYYY-MM-DD-主题-plan.md` |
| 记录 | `YYYY-MM-DD-主题.md` |
| 评审 | `YYYY-MM-DD-主题-review.md` |
| 笔记 | `主题.md` |

### 3.5 图表资产保护（刚性，本仓重点）

1. 基线（2026-11-16，BE-001 补导出后）：`docs/` 共 1302 个文件，`.svg` **247**、`.mmd` **68**；`docs/diagrams/` 为图表主库（顶层 130 SVG + 38 MMD），`docs/diagrams/sequence/` 的 30 个 `.mmd` 于 2026-11-16 全部补齐同名 `.svg`。
2. **禁止**删除、覆盖、重绘既有 SVG/MMD/PNG/PDF 图表；**禁止**为「结构整齐」批量移动图表文件。
3. 更新图表：新增版本文件（如 `_v2`）并在 `docs/records/` 写明差异与受影响引用。
4. 源文件与导出件成对（`.mmd` ↔ `.svg`）；**新增图必须两者齐备**。
5. 存量缺口：**0 个**（原 30 个 `.mmd` 缺同名 `.svg` 已于 2026-11-16 用 mermaid-cli 10 补导出，见 `docs/records/2026-11-16-tech-debt-be001-be017.md`）；**禁止删除任何 `.mmd` 源文件**。`verify-docs.sh` 第 6 项继续守护配对。
6. 数量门槛写死在自检脚本（svg ≥ 247、mmd ≥ 68），低于基线即告警。

### 3.6 历史归档的旧路径与断链（豁免口径）

1. 适用范围：`docs/_archive/`（整体）、`docs/archived/` 下的历史记录、`docs/reports/` 下 2026-09 及更早的执行报告、`CHANGELOG.md` 历史条目。
2. 这些文档记录的是**当时真实路径与当时真实断链**，改动即篡改证据 → **不改正文**，只做两件事：
   - 在文首加一行「历史记录说明」，注明相关正文已迁入 `docs/`、未迁入部分「原文未入库」；
   - 登记到 `scripts/governance/doc-refs-allowlist.txt`（必须写明豁免理由）。
3. **活文档（`docs/` 下现行规范、索引、指南、方案、评审）不得豁免**，必须改到可达。
4. 豁免清单的准入门槛只有三类：历史归档 / 迁移说明与来源证明 / 评审取证；新增一类须先在规范中登记。
5. 自检口径：`check-doc-refs.sh` 只统计「未豁免且未标注」的问题；数字必须能收敛到 0，否则视为整改未完成。

---

### 3.7 目录总览与「一个主题一个家」（2026-11-16 增补）

| 规则 | 内容 |
| --- | --- |
| 唯一索引 | `docs/README.md` 的「🗂 目录总览」表是全部一级目录的唯一索引；新增目录必须先登记该表，再建目录 |
| 必带 README | `docs/` 下**每个**一级目录必须有 `README.md`，写清「用途 / 收什么 / 不收什么（新内容去哪） / 索引」 |
| 两级体系并存 | 编号体系（`00`–`08`）继续收新内容，是**架构/接口/测试报告/周期报告的权威落点**；英文专题目录（`architecture`、`api`、`reports` 等）作为**历史细分材料只读**，不再追加，避免同一主题两份口径 |
| 根目录洁净 | 仓库根目录只允许门面/许可文档（`README.md`、`CHANGELOG.md`、`CONTRIBUTING.md`、`CODE_OF_CONDUCT.md`、`LICENSE*`、`MEMORY-CARD.md`、`AGENTS.md`）与历史迁移指针文件；业务 Markdown 一律进 `docs/` |
| 机器校验 | `scripts/governance/check-doc-layout.sh`（接入 `verify-docs.sh` 第 9 项）断言上述四条，`--strict` 进 CI |

## 4. 多智能体协作

### 4.1 工具对应目录与允许内容

| 工具 | 私有目录 | 允许存放 | 禁止存放 |
| --- | --- | --- | --- |
| Trae | `.trae/` | 规则镜像（`rules/project_rules.md`）、技能、缓存 | 设计文档、方案、报告正文（已迁移的 4 份设计文档除外，仅留 3 行指针） |
| WorkBuddy | `.workbuddy/` | memory、automations、`rag/`、`ops/`、reports 临时输出 | 知识库正文（已迁至 `docs/reference/ai-knowledge/`） |
| ZCode | `.zcode/` | ignore 配置、会话计划缓存 | 计划正文（须进 `docs/plans/` 或 `docs/archived/ai-sessions/`） |
| Qoder | `.qoder/` | 配置、缓存 | 业务文档 |
| Codex | `.codex/` | 配置、缓存 | 业务文档 |
| Claude | `.claude/` | 配置、agents 定义 | 业务文档 |
| 其他/未来 | 新增隐藏目录 | 配置、缓存、规则镜像 | 业务文档 |

### 4.2 隔离规则

1. 每个智能体只读写**自身**私有目录；禁止跨 AI 修改他人私有目录（含删除、重命名）。
2. `docs/` 对所有智能体可读可写，但写入遵循 §2.6 与 §3 归位规则。
3. `.trae/rules/project_rules.md` 等镜像文件不得与 `AGENTS.md` 冲突，冲突以仓库文件为准。
4. 新增 AI 工具：只新增目录 + 规则镜像指针，不改本规范。

### 4.3 交接协议

执行者在会话结束或请求复核前必须提交 §4.9 的标准交接包，附真实证据、commit、未验证项与回滚方式；R0 验收交接包后再写任务状态。执行者不得直接改任务盘、记录索引或记忆台账。所有任务均须在 `docs/records/` 留一条记录，缺任一项视为未交付。

### 4.4 角色卡（跨工具通用）

| 角色 | 文件 | 职责 | 写入权限 |
| --- | --- | --- | --- |
| R0 主控编排 | `ai-agents/r0-orchestrator.md` | 建卡、拆依赖、分配租约与写入范围、调度、集成、关闭 | 任务盘、记忆台账、记录索引、最终集成分支 |
| R1 任务规划 | `ai-agents/r1-task-planner.md` | 需求 → BE 任务卡 + 验收标准 + 并行边界 | 只输出任务卡草案；任务盘由 R0 写入 |
| R2 后端实现 | `ai-agents/r2-backend-implementer.md` | 按 10 条红线写 Handler/Router/Service/Repository | 仅任务卡写入范围 |
| R3 契约守门 | `ai-agents/r3-contract-guard.md` | 审路由、DTO、错误码、兼容性、鉴权 | 代码只读；可写 R0 指定的评审记录 |
| R4 测试回归 | `ai-agents/r4-test-runner.md` | `mvnd` 分层验证、失败定位、测试缺口 | 默认只读；仅任务卡指定的测试文件可写 |
| R5 评审 | `ai-agents/r5-reviewer.md` | 独立评审 + 跑治理脚本 + 放行结论 | 代码只读；可写 R0 指定的评审记录 |

> 主链路：`R0 建卡/授权 → R1 拆解 → R2 实现 → R3/R4 独立取证 → R5 放行 → R0 集成/关闭`。小任务可合并规划/实现，但 R5 必须与实际执行者为不同智能体实例或会话；代码任务中 R2 与 R5 也不得相同；并行任务不得合并 R0、执行者和复核者；不得自审自放。

### 4.5 职责分离与单写者

1. **R0 唯一协调**：各任务的任务盘行、状态、租约、记忆台账条目和最终 `dev` 集成由该任务 R0 负责；跨任务共享写入与 `dev` 集成仍须遵守 §4.10 的全局租约。执行者不得直接改这些共享写入物。
2. **执行者范围实现**：执行者只改任务卡列明的写入范围；发现相邻缺陷、公共依赖或契约变化时登记新卡，不得顺手扩展。
3. **R3/R5 代码只读**：契约守门和评审只输出问题清单与结论；返工由原执行者执行，修复后复核增量范围。
4. **R4 证据优先**：测试执行者必须报告环境、命令、结果、失败摘要和复现步骤；不得用“已验证”代替原始证据。
5. **人负责最终风险**：P0、安全、数据迁移、模块坐标、parent 继承、运行拓扑和契约破坏性变更，必须由用户或指定决策人确认。

### 4.6 任务租约与开工门禁

R0 在把任务置为 `执行中` 前，必须在任务卡或关联执行块写全：

```text
任务：BE-nnn
R0：<智能体/人>
执行：<智能体/人> @ <worktree> / <branch>
复核：<与执行不同的智能体/人>
基线：<dev commit SHA>
写入范围：<允许修改的路径>
禁止触碰：<共享文件、迁移、契约、他人工作区>
依赖/并行边界：<前置、不可并行对象>
验收命令：<可执行命令 + 期望结果>
交接物：<记录、测试证据、commit、回滚方式>
全局集成租约：<NOT_REQUIRED / HOLDING / WAITING>
租约开始：<YYYY-MM-DD HH:mm>
```

- 同一任务同一时刻只能有一个活跃执行租约；重复认领视为冲突，后到者停止并回报 R0。
- 租约默认 1 个工作日。无进展时 R0 先核验提交、工作区和阻塞项，再选择续租、回退或改派；接管不得覆盖原执行者未提交成果。
- P0 可启用紧急通道：R0 可先指定单一写者并冻结并行合并，但须在恢复后 24 小时内补齐任务卡、记录和记忆台账。

### 4.7 并行准入矩阵

| 改动类型 | 可并行 | 约束 |
| --- | --- | --- |
| 不同模块、写入文件互斥、验收独立 | 是 | 各自 worktree/短分支；R0 分别集成 |
| 同一模块但文件完全互斥 | 有条件 | R0 显式拆分写入范围；共享测试/配置归一人 |
| 根 `pom.xml`、parent、模块注册表、构建脚本 | 否 | 单 owner 串行，避免模块坐标与 reactor 冲突 |
| 接口契约、DTO、错误码、网关路由 | 否 | 先 R3 审契约，再单 owner 实现；契约冻结期间禁止并行改同契约 |
| Flyway/数据库迁移、数据修复 | 否 | 单 owner 串行，版本号和执行顺序由 R0 统一 |
| 任务盘、`docs/README.md`、记忆台账、记录索引 | 否 | R0 独占；执行者通过交接包回报 |
| 图表同一源文件/导出件 | 否 | 一图一 owner；禁止删除、覆盖、批量移动 |
| 只读分析、代码定位、文档评审 | 是 | 不写工作区；不清理他人 `target/`、缓存或进程 |

**并行开工判据（全部满足才允许）**：依赖已完成；写入路径互斥；验收命令互不干扰；数据库、端口、测试资源和缓存可隔离；R5 能独立复核；R0 能分别回滚。任一项不满足则串行。

### 4.8 Worktree 与工作区隔离

1. 共享 checkout 是**单写者**环境；同一时刻只允许一个智能体在其中提交或修改文件。
2. 并行写任务从 `dev` 建立独立 Git worktree 与短分支，建议：

```bash
git fetch origin dev
git worktree add ../apex-worktrees/BE-xxx -b codex/BE-xxx origin/dev
```

3. 每个 worktree 只承载一个任务；不得复用带有他人未提交改动的 worktree，不得在同一 worktree 切换任务分支。
4. 执行者不得删除、移动、切换或覆盖其他智能体的 worktree、未提交改动、运行中服务、端口占用或 `target/`。只读任务可共享主 checkout，但不得写入。
5. 完成前由持有全局集成租约的 R0 将任务 worktree 的提交按单任务 rebase/cherry-pick 到 `dev`；未取得租约的 R0 不得在共享 checkout 写入或集成。禁止把多个任务混成一个提交，禁止 `push -f`、`reset --hard` 或整表覆盖。

### 4.9 标准交接包

执行者在请求复核前必须提交以下内容；缺项不得进入 `待复核`：

```markdown
## BE-xxx 交接包
- R0 / 执行 / 复核：
- 基线 SHA / worktree / 分支：
- 写入范围 / 实际改动：
- 验收命令 / 原始结果：
- 新增或变更的测试：
- 未验证项 / 外部依赖 / 已知风险：
- commit / 回滚命令：
- 下一步建议：
```

交接证据必须来自真实执行，不能只转述其他会话结论。所有任务均须在 `docs/records/` 留一条记录；简单任务可精简，但必须包含范围、证据、影响面和回滚方式。任务盘关联列同时保留交接包或记录指针。

### 4.10 集成、复核与关闭

1. R2 完成后先做自检，但自检不等于放行；R4 提供回归证据，R3 审契约，R5 做独立终审。
2. R5 必须核对：任务卡范围、基线差异、写入范围、命令证据、红线、文档/记忆台账、回滚方式。
3. R0 仅在 R5 `APPROVE` 或 `APPROVE_WITH_NITS` 且必须修项清零后集成到 `dev`；集成后重跑受影响范围与治理脚本。
4. 合并顺序按任务依赖和 BE 编号；每次只合一个任务。冲突由写入范围最小的 owner 处理，R0 决定保留语义。
5. 关闭条件：R5 结论、集成 commit、治理校验、记录/索引、记忆台账全部可定位，才可将任务改为 `已完成`。

**全局集成租约**：当多个任务由不同 R0 负责时，`dev` 仍是共享集成分支。R0 必须在任务盘或 `docs/records/` 登记全局集成租约（持有者、开始时间、待集任务、释放时间）后才能执行 rebase/cherry-pick、合并、复验和推送；同一时刻只允许一个持有者。租约按任务依赖、BE 编号和用户指定优先级排序；持有者异常失联时，由用户或指定决策人核验工作区后接管，不得并行“抢合”。任务级 R0 可并行准备 worktree 和复核，但不得并行写入 `dev`。

### 4.11 冲突、阻塞与异常接管

| 场景 | 处置 |
| --- | --- |
| 两个活跃任务写同一路径 | 立即暂停后到任务；R0 拆分写入范围或串行化，不得自动“先写者胜” |
| 多个任务需要合并到 `dev` | 只允许一个全局集成租约；按依赖/BE 顺序逐任务集成，禁止并行抢合 |
| 契约/模块边界/迁移顺序冲突 | 冻结相关合并，升级用户或 R3/指定决策人；先在 `docs/specs/` 留方案 |
| 执行者失联或超租约 | R0 核验提交与工作区后决定续租/改派；保留原成果，禁止覆盖 |
| 构建环境/端口/数据库互相干扰 | 为并行任务分配独立端口、库名和 worktree；不能隔离则串行 |
| 证据缺失或命令失败 | 任务回到 `执行中`；不得用历史结果、推断结果或“应该通过”替代 |
| P0 紧急故障 | 冻结并行合并，指定单一写入者和唯一 R0；恢复后补卡、补记录、补台账 |

---

## 5. 版本控制与忽略规则

### 5.1 `.gitignore` 三条硬规则

1. **`docs/` 必须全量入库**，禁止出现任何忽略 `docs` 的规则（含通配写法）。
2. AI 私有目录整体忽略：`.workbuddy/`、`.trae/`、`.zcode/`、`.qoder/`、`.codex/`、`.claude/`、`.agents/`、`.ai/`。
3. 忽略项必须带注释说明「为什么忽略、正文在哪」，禁止裸 `*.md` 模糊规则（防误伤 `docs/` 下归档件）。

### 5.2 提交纪律

- 一次提交只做一类改动：`docs(governance): …` / `chore(scripts): …` / `feat(...)` / `fix(...)`。
- 提交前钩子：`git config core.hooksPath .githooks`（每人一次），`.githooks/pre-commit` **只警告不阻断**。
- 禁止 `git reset --hard`、`git push -f`；回滚用 `git revert`。
- **他人未提交改动默认不纳入**：`git status` 中非本人产物的改动，不清理、不覆盖、不擅自提交。
- 确需纳入时：先经业主明确授权 → 按 §3.3/§3.4 只做指针与索引对齐（**正文一字不改**）→ 单独提交 → 在 `docs/records/` 留痕。示例：BE-019 将 `MEMORY-CARD.md`（指针改指 `docs/reference/ai-knowledge/`）、`daily/README.md`（索引登记）、`daily/2026-11-16-all-projects-daily-report.md`（补后段补记）复核后提交为 `14f6bc2a`。

### 5.3 分支与远端

主线 **`master`**（2026-11-16 由 `main` 重命名）；所有日常开发、技术债整改与文档治理一律在 `dev` 提交，`master` 只做发布合入与紧急热修。远端默认分支**已**设为 `master`（2026-11-16，两仓均已切换并删除 `main`）；如需再次变更，按 GitHub Settings → General → Default branch 操作，变更后同步本条与 `docs/records/2026-11-16-github-remote-onboarding.md` §5。

**分支模型（长期分支三个，2026-11-16 建立 `dev` / `test`）**：

| 分支 | 用途 | 允许直接提交 | 说明 |
| --- | --- | --- | --- |
| `dev` | **默认工作分支**，承载全部日常开发与文档治理提交 | **是（默认）** | 所有新增/修改/整改都在此提交，是唯一的日常入口 |
| `master` | 生产基线，可发布状态 | 否（仅接受从 `dev` 合入，或紧急热修） | 每条提交都应可发布；**禁止 `push -f`** |
| `test` | 测试/验收环境分支 | 否（仅由 `dev` 合入） | 供测试环境部署与联调 |
| 短分支 | 单个任务（建议 `codex/BE-nnn`） | — | 自 `dev` 拉出，完成后合并回 `dev` 并删除 |

**默认工作分支为 `dev`（2026-11-16 起，两仓本地已切换）**：日常开发、技术债整改、文档治理一律在 `dev` 上进行；`master` 只做发布与合入，`test` 只做验收。开工前先确认与对齐：

```bash
cd /Users/work/apex-scaffold
git checkout dev                 # 默认工作分支（已切换，长期停留于此）
git pull --ff-only               # 开工前必须与 origin/dev 对齐
git rev-parse --abbrev-ref HEAD  # 应输出 dev
```

合入规则：`dev` 验证通过（`verify-docs.sh` / `check-doc-refs.sh` / `audit-backend-rules.sh` 全绿）→ `git checkout master && git merge --ff-only dev && git push` → `git checkout test && git merge --ff-only dev && git push` → 回到 `dev` 继续开发。**禁止在 `master` 上直接改代码或文档**（紧急热修除外，且须当日回合 `dev`）。

建立与推送（已执行，两仓同款）：

```bash
git branch dev  master && git push -u origin dev
git branch test master && git push -u origin test
```

首建时三支指向同一提交（后端 `ee244f89`、前端 `88ca2d1`），历史零变化。旧远端 `main` 已于远端默认分支切换为 `master` 后删除（`git push origin --delete main` + `git fetch --prune`）。

**分支保护**：`master`/`test` 应开「Require PR + 线性历史 + 禁强推 + 禁删除」，`dev` 只禁强推与删除。完整配置表、网页步骤、`gh api` 命令、开启后的 PR 流程与 8 条验收清单见 `docs/05-operations/github-branch-protection.md`。**注意**：保护开启后本地 `merge --ff-only && push` 会被拒，须改走 PR + Rebase and merge（生成新哈希，内容一致）。

**远端与离线归档（`origin` = `git@github.com:apex-cloud-service/apex-scaffold.git`，SSH 走 443）**：

1. 未配置远端（或推送受阻）时，全部历史只存在于本机 → **每周做一次离线归档**（用 `git archive` 只打受控文件，避免把 `target/`、`.workbuddy/` 等打进包）：
   ```bash
   cd /Users/work/apex-scaffold
   ts=$(date +%Y%m%d); mkdir -p /Users/work/backup
   git archive --format=tar.gz --prefix=apex-scaffold/ -o /Users/work/backup/apex-scaffold-$ts.tar.gz master
   git bundle create /Users/work/backup/apex-scaffold-$ts.bundle master
   cd /Users/work/backup && shasum -a 256 apex-scaffold-$ts.tar.gz >> SHA256SUMS.txt
   ```
   **不要**用 `tar --exclude=.git .`：根目录 `target/`（本仓实测 1.4GB）、`.workbuddy/` 会被打进去（实测一次 2.7GB）。
2. 归档件（tar.gz + bundle）必须异地/异盘保存至少一份；`SHA256SUMS.txt` 用于校验完整性。
3. 推送纪律：`git push -u origin master`；**禁止 `push -f`**。本机 22 端口被网络阻断，仓库内已固定走 443：
   ```bash
   git config core.sshCommand "ssh -o Hostname=ssh.github.com -p 443"
   ssh -T -p 443 git@ssh.github.com      # 期望回 Hi <账号>! You've successfully authenticated...
   ```
   私钥若带口令，先 `ssh-add --apple-use-keychain ~/.ssh/<key>`（非交互环境无法解密带口令私钥）。
4. 远端若已有另一条历史（如 GitHub 建仓时的 `Initial commit`），**先把远端内容读清楚再合并**：
   ```bash
   git fetch origin && git ls-tree -r --name-only origin/master
   git merge --allow-unrelated-histories origin/master    # 冲突保留本地完整版
   ```
   默认保留本地完整版（本仓 2026-11-16 即按此合并远端仅含 `LICENSE`/`README` 桩的 Initial commit，LICENSE 维持项目既有的 MIT、不采用 GitHub 模板的 Apache 2.0）。
5. 评审/整改若涉及「合并到 `master`」：有远端即「提交 + push」；推送受阻时等价于「在 `master` 上提交 + 生成 bundle 归档」（见 `docs/records/2026-11-16-github-remote-onboarding.md`）。

---

### 5.4 记忆文档同步（强制）

**规则**：**任何改动都必须写入本仓的记忆文档**，与改动同一次提交完成，不允许「先提交、以后再补」。

**记忆文档清单（本仓）**

| 文件 | 角色 | 何时必须更新 |
| --- | --- | --- |
| `docs/reference/ai-knowledge/07-change-memory.md` | **变更流水台账**（append-only） | **每一次改动**都追加一条 |
| `MEMORY-CARD.md` | 一页式记忆卡（结论 + 指针） | 结构性变化（模块/端口/库/分支/许可/构建命令变化） |
| `docs/reference/ai-knowledge/01`–`06` | 现状 / 地图 / 陷阱 / 交付快照 | 该篇描述的结论发生变化时 |
| `docs/records/<日期>-<主题>.md` | 事件级完整复盘 | 复杂改动（跨模块、跨仓、有踩坑或决策） |

**台账一条的必填六字段**：`日期` · `类型`（代码/技术债/文档/脚本/图表/许可/分支/远端/治理）· `摘要`（做了什么 + 为什么）· `提交`（短哈希）· `证据`（实测命令与输出、校验脚本结果）· `影响面`（是否影响功能/接口/构建）。

**提交时的强制动作**

1. 提交信息末行写 `memory: 07-change-memory.md#<条目编号>`；无记忆更新的纯机械改动（如格式化、改错字）写 `memory: n/a（原因）`。
2. 提交前对照下表自检，缺项即视为未完成：

| 改动类型 | 除台账外还要动的记忆文档 |
| --- | --- |
| 新增/删除模块、改包结构、改端口或库 | `MEMORY-CARD.md` §3/§6、`02-apex-scaffold-current-state.md` |
| 改构建命令、JDK/mvnd 口径、CI 矩阵 | `MEMORY-CARD.md` §2、`06-delivery-status-and-next.md` |
| 改编码红线 / 治理脚本 / 分支策略 | 本篇（`docs/specs/project-governance.md`）+ 台账 |
| 改图表基线（`.mmd`/`.svg` 数量） | `docs/diagrams/README.md`、`03-conventions-and-pitfalls.md`、`verify-docs.sh` 门槛 |
| 改许可 / 远端 / 分支结构 | `MEMORY-CARD.md` 基线行、台账 |

3. 周期校验（每周一次，可放进周报）：
   ```bash
   python3 .workbuddy/ops/maintain.py status      # 检测口径漂移（私有运维脚本，不入库）
   bash scripts/governance/verify-docs.sh          # 结构与索引可达性
   git log --oneline -10 | grep -c "memory:"       # 最近 10 条是否都带记忆标记
   ```

**禁止**：① 把私有目录（`.workbuddy/`、`.zcode/`、`.trae/`）内容抄进受控记忆文档；② 只写「已完成」不写证据；③ 删除或改写历史条目——记错时追加 `更正 #n` 条目。

## 6. 编码红线（后端，10 条）

| 编号 | 红线 | 现状与例外 |
| --- | --- | --- |
| B1 | 接口一律 `RouterFunction` + `Handler`，返回 `Mono`/`Flux` | 115 个文件使用 `RouterFunction`，合规 |
| B2 | 禁 `@Controller`/`@RestController` 注解式控制器 | 0 命中；例外 `GlobalExceptionHandler`、`SentinelExceptionHandler` 属 `@RestControllerAdvice`，登记 BE-004，评估前禁改 |
| B3 | 禁 `@Autowired`，统一构造器注入 | 0 命中（仓库自带 ArchUnit `NoAutowiredArchTest` 守护） |
| B4 | POJO/DTO/VO/BO/Query 统一 Lombok，禁手写存取方法 | 抽样 10 处为不可变载体/`@ConfigurationProperties`，合法场景 |
| B5 | 对象转换统一 MapStruct，禁 `BeanUtils`/手写逐字段拷贝 | 0 命中 |
| B6 | 日志统一 `@Slf4j`，禁 `LoggerFactory.getLogger()` | 生产代码 1 处违规：`apex-common` 的 `VirtualThreadBoundedExecutor:72`，登记 BE-017；测试代码可保留 |
| B7 | 类/接口/枚举/常量/公开方法必须有完整 Javadoc | — |
| B8 | Handler 必须有 SpringDoc OpenAPI3 注解（`@Tag`/`@Operation`），DTO 用 `@Schema` 保证请求体/响应体完整可见 | — |
| B9 | 构建固定 JDK `21.0.13.0+9-tem` + `mvnd 2.0.0-rc-3`，禁系统 `mvn`、禁其他 JDK | `.sdkmanrc` 已固定 |
| B10 | 模块边界：新模块只在所属分组 `<modules>` 登记一次；不复制安全/数据源/缓存/消息/日志/调度框架到业务库 | — |

补充：响应式链禁止阻塞调用与手动 `.subscribe()`；JDBC、同步缓存/Redis、文件/存储 SDK、同步 HTTP 等阻塞调用必须延迟到订阅时并隔离到对应 `BlockingIoSchedulers`，不得用 `Schedulers.boundedElastic()` 绕过专用池。异常处理走既有 WebFlux 异常边界。

---

## 7. 变更纪律与回滚

1. 先登记任务再动手；改造可评审、可回滚、可分批。
2. 涉及接口契约、模块坐标、parent 继承、运行模块清单、数据库迁移、网关路由的改动，先在 `docs/specs/` 出方案。
3. 文档治理与功能改动禁止同批提交。
4. 回滚：`git revert <sha>` → 跑 `verify-docs.sh` + 受影响模块验证 → 在 `docs/records/` 记录原因。
5. 每次改造必须能在 `docs/records/` 找到「改了哪些文件、怎么验的、怎么回滚」。

---

## 8. 工具与脚本

| 脚本 | 作用 | 用法 |
| --- | --- | --- |
| `scripts/governance/verify-docs.sh` | 六目录/索引/任务大盘/私有目录边界/**图表数量门槛 247/68**/`.mmd`↔`.svg` 配对/归档索引/**目录结构（第 9 项）** | `bash scripts/governance/verify-docs.sh` |
| `scripts/governance/check-doc-layout.sh` | 一级目录 README、总览表覆盖、目录白名单、根目录散落 | `bash scripts/governance/check-doc-layout.sh [--strict]` |
| `scripts/governance/audit-backend-rules.sh` | 行首精确注解匹配：`@Autowired`、`@Controller`、`@RestControllerAdvice`（例外）、`BeanUtils`、生产代码手写 Logger、`.sdkmanrc`/WebFlux/MVC starter | `bash scripts/governance/audit-backend-rules.sh [--strict]` |
| `scripts/governance/check-doc-refs.sh` | 活文档私有路径残留 + Markdown 断链（含白名单豁免） | `bash scripts/governance/check-doc-refs.sh [--strict]` |
| `.githooks/pre-commit` | 治理范围改动时跑自检 + 图表删除告警 + 大文件提醒 | `git config core.hooksPath .githooks` |

脚本说明见 `scripts/governance/README.md`；`--strict` 才产生非零退出码，默认只警告。

---

## 9. 本次执行记录（2026-11-16）

### 9.1 做了什么

1. `AGENTS.md`：前置新增第 0 章（含 0.3 图表保护、0.4 多智能体、0.5 十条红线），原文下移为附录 A（`tail -n 306` 哈希 `aa0c25fc…60ed` 与原文件一致）。
2. 新建六标准目录 + 各 `README.md` + 任务大盘（BE-001~008 待规划、BE-010~013 治理完成、BE-014~016 迁移完成、BE-017 技术债、BE-018 规范正文入仓已完成、BE-019 他人 3 项改动纳入已完成、BE-020 治理评审整改待规划）。
3. 私有目录正文迁入 `docs/`（逐文件 SHA-256 一致）：
   - `.trae/documents` 4 份 → `docs/plans/archived-ai-sessions/`（原路径留指针，修 3 处引用）
   - `.workbuddy/knowledge` 7 份 → `docs/reference/ai-knowledge/`（修 rag/ops 相对链接）
   - `.zcode/plans` 13 份（2026-11-16）→ `docs/archived/ai-sessions/zcode-plans/`；2026-11-18 BE-042 追加 1 份后当前 14 份
   - `.workbuddy/reports` 3 份 → `docs/08-project-management/periodic-reports/{daily,weekly,monthly}/`
4. 新建治理脚本与 pre-commit（警告模式）、`docs/specs/ai-agents/` 五张角色卡、`scripts/governance/README.md`。
5. `.gitignore` 补私有目录标记块，明确「docs 全量入库」。
6. 本文档（规范正文）与 `docs/records/2026-11-16-docs-governance.md`（执行留痕）入仓。

### 9.2 回归证据

| 项 | 结果 |
| --- | --- |
| 基线 1272 个 docs 文件 | 1269 个逐字节一致；3 个为纯追加（`docs/README.md` 40+0）或单行链接替换（`01+01`），零删除 |
| 图表 | SVG 217、MMD 68，**一个没少**（2026-11-16 BE-001 补导出至 SVG 247、MMD 68） |
| AGENTS.md 原文 | 306 行 / `aa0c25fcc3d4b32a79981efb4e01534ae68449fb320d37037263d4668d8960ed` 一致 |
| Java 代码 / POM / `.sdkmanrc` | 0 文件改动 |
| 治理自检 | `verify-docs.sh` 通过（0 项告警，30 个 MMD 缺口已于 BE-001 补齐）；`audit-backend-rules.sh` 0 阻断（BE-017 已改 `@Slf4j`） |

### 9.3 未完成与后续

| 项 | 状态 |
| --- | --- |
| BE-001 30 个 MMD 补 SVG | 已完成（2026-11-16，68/68 配对，SVG 247） |
| BE-004 异常处理器评估 | 待规划（禁止先改） |
| BE-017 `VirtualThreadBoundedExecutor` 改 `@Slf4j` | 已完成（2026-11-16，`:apex-common` verify 通过） |
| BE-018 规范正文入仓（v1.1） | 已完成（2026-11-16） |
| BE-019 他人 3 项改动按规范复核纳入 | 已完成（`14f6bc2a`） |
| BE-020 治理评审整改（RV-01~RV-14） | 已完成（2026-11-16，见 `docs/records/2026-11-16-governance-alignment-remediation.md`） |
| 根目录散落文档（`aggregation-optimization-plan.md`、`architecture-review-report.md`） | 已迁入 `docs/plans/`、`docs/reviews/`，根目录留 3 行指针 |

---

## 10. 验收清单（复制即用）

```markdown
- [ ] 任务已在大盘登记，优先级 P0–P3，验收标准可执行
- [ ] 改动只覆盖任务目标，未跨模块顺手重构
- [ ] 新文档归位六标准目录并登记 docs/README.md
- [ ] 未删除/覆盖任何既有文档与图表；SVG ≥ 247、MMD ≥ 68
- [ ] 私有目录未新增业务正文
- [ ] .gitignore 未出现忽略 docs 的规则
- [ ] bash scripts/governance/verify-docs.sh 通过
- [ ] bash scripts/governance/audit-backend-rules.sh 无新增阻断
- [ ] source scripts/build/init-build-env.sh 后受影响模块 mvnd verify 通过
- [ ] 他人未提交改动未被触碰
- [ ] docs/records/ 已留变更记录
- [ ] R0、执行者、复核者已指定，且 R5 与实际执行者不是同一实例/会话；代码任务中 R2 与 R5 也不是同一实例/会话
- [ ] 基线 SHA、写入范围、并行边界和租约已登记
- [ ] 并行写任务使用独立 worktree，未与其他活跃任务写同一路径
- [ ] 标准交接包含真实命令与结果，未把“已完成”当作证据
- [ ] R0 已完成集成，记录/索引/记忆台账可定位
- [ ] 复核人（R5）已给出结论
```

---

## 11. 附：目录样例

```text
apex-scaffold/
├── AGENTS.md                          # 入口索引：第 0 章 + 附录 A（原文）
├── docs/
│   ├── tasks/project-tasks.md         # 任务大盘（唯一写入口）
│   ├── specs/project-governance.md    # 本文件（规范正文）
│   ├── specs/ai-agents/               # R0–R5 角色卡
│   ├── plans/archived-ai-sessions/    # .trae 迁入的设计文档 + 指针
│   ├── archived/ai-sessions/zcode-plans/  # .zcode 迁入的 14 份计划
│   ├── reference/ai-knowledge/        # .workbuddy 迁入的 7 份知识库
│   └── diagrams/                      # 图表主库（247 SVG / 68 MMD，禁动）
├── scripts/governance/                # 自检脚本 + README
└── .githooks/pre-commit               # 警告模式钩子
```


---

## 12. 2026-11-18 多智能体协作增补记录

本次在 v1.1 基础上增加多智能体并行协作规则，不改变既有任务优先级、五状态、文档归位、分支模型和后端红线：

1. 新增 R0 主控编排角色，明确任务盘、租约、最终集成和记忆台账的唯一协调责任。
2. 将“先写盘者为准”改为“同一写入范围只允许一个活跃写任务，由 R0 串行化或拆分”。
3. 增加任务租约、并行准入矩阵、独立 worktree、单写者、标准交接包、冲突升级和异常接管规则。
4. 明确 R5 必须与实际执行者不同，代码任务中 R2 与 R5 也不得是同一智能体实例/会话；复核证据必须来自真实执行。
5. 修正角色卡与 `AGENTS.md` 0.5 的 B1–B10 编号漂移、构建初始化命令和图表基线。

完整执行证据见 `docs/records/2026-11-18-multi-agent-governance.md`，记忆台账追加条目 #53。

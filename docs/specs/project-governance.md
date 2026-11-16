# 项目任务管理规范（apex-scaffold · 规范正文）

| 项 | 值 |
| --- | --- |
| 文档版本 | v1.1（2026-11-16 增补版） |
| 生效日期 | 2026-11-16 |
| 适用范围 | apex-scaffold 全部模块与目录、全部参与者（人 + 任意 AI 智能体） |
| 权威关系 | 本文件与 `AGENTS.md` 第 0 章同源：`AGENTS.md` 为入口索引，本文件为可独立阅读的完整正文；技术事实以 `AGENTS.md` 附录 A、`pom.xml`、`README.md` 与代码为准 |
| 维护人 | 主控智能体；变更需在 `docs/records/` 留痕 |

---

## 1. 设计原则

1. **可追溯**：任何改动都能回答「哪个任务、谁批的、验收怎么过的」。
2. **不散落**：业务文档只有一个家 —— `docs/`；AI 私有目录只放工具自身的配置与缓存。
3. **不打架**：多个 AI 工具并存时，靠「目录边界 + 单一正文」避免各自一份口径。
4. **不破坏**：改造优先「新增 + 留指针」，禁止「移动 + 删原件」；**图表资产零删除**；功能改动与文档治理不同批提交。

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
- `待复核` 必须指定复核人（人或另一 AI 角色，见 §4.4）。
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
| 验收标准 | **可执行**（命令 + 期望结果），如 `mvnd -pl :模块 -am verify` 通过、`verify-docs.sh` 通过 |
| 影响面 | 模块/文件清单，标注是否触碰红线与契约 |
| 项目校验 | 明确回归范围（单模块 / 全量 / 仅文档） |

### 2.5 完成判定（DoD，缺一不可）

1. 改动完成且仅覆盖任务目标。
2. 验证通过：`source scripts/build/init-build-env.sh` 后 `mvnd -pl :受影响模块 -am verify`（跨模块改动用全量 `mvnd -T4 -B clean verify`）。
3. 不违反第 6 章红线；红线违规必须显式登记例外（见 BE-004、BE-017）。
4. 文档与索引同步（新增文档已登记 `docs/README.md`）。
5. 在 `docs/records/` 留一条变更记录；**同批次一条记录须列全本批次涉及的全部任务卡号**（如批次 1–6 须列 BE-020 涉及的 RV-01~RV-14 与同批次其它卡号），不得只写首张卡。

### 2.6 大盘维护权

- `docs/tasks/project-tasks.md` 只由主控智能体写入；其余智能体只读，完成后回报主控更新状态。
- 并行会话冲突：以「先写盘者为准」，后者 rebase 自身变更，禁止整表覆盖。

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

会话结束前必须交付：① 更新的任务状态；② `docs/records/` 一条记录；③ 「下一步建议」。缺一视为未交付。

### 4.4 角色卡（跨工具通用）

| 角色 | 文件 | 职责 |
| --- | --- | --- |
| R1 任务规划 | `ai-agents/r1-task-planner.md` | 需求 → BE 任务卡 + 验收标准 |
| R2 后端实现 | `ai-agents/r2-backend-implementer.md` | 按 10 条红线写 Handler/Router/Service/Repository |
| R3 契约守门 | `ai-agents/r3-contract-guard.md` | 审路由、DTO、错误码、兼容性、鉴权 |
| R4 测试回归 | `ai-agents/r4-test-runner.md` | `mvnd` 分层验证、失败定位 |
| R5 评审 | `ai-agents/r5-reviewer.md` | 提交前评审 + 跑治理脚本 |

> 复核人默认由 R5 承担；不得自审自放。

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

主线 **`master`**（2026-11-16 由 `main` 重命名）；改动走短分支后合并；文档治理可在 `master` 小步提交。远端默认分支**已**设为 `master`（2026-11-16，两仓均已切换并删除 `main`）；如需再次变更，按 GitHub Settings → General → Default branch 操作，变更后同步本条与 `docs/records/2026-11-16-github-remote-onboarding.md` §5。

**分支模型（长期分支三个，2026-11-16 建立 `dev` / `test`）**：

| 分支 | 用途 | 允许直接提交 | 说明 |
| --- | --- | --- | --- |
| `dev` | **默认工作分支**，承载全部日常开发与文档治理提交 | **是（默认）** | 所有新增/修改/整改都在此提交，是唯一的日常入口 |
| `master` | 生产基线，可发布状态 | 否（仅接受从 `dev` 合入，或紧急热修） | 每条提交都应可发布；**禁止 `push -f`** |
| `test` | 测试/验收环境分支 | 否（仅由 `dev` 合入） | 供测试环境部署与联调 |
| 短分支 | 单个任务（如 `feat/BE-021`、`fix/FE-018`） | — | 自 `dev` 拉出，完成后合并回 `dev` 并删除 |

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

补充：响应式链禁止阻塞调用（`.block()`），阻塞库调用须 `boundedElastic` 包装；异常处理走既有 `ErrorWebExceptionHandler` 体系。

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
   - `.zcode/plans` 13 份 → `docs/archived/ai-sessions/zcode-plans/`
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
│   ├── specs/ai-agents/               # R1–R5 角色卡
│   ├── plans/archived-ai-sessions/    # .trae 迁入的设计文档 + 指针
│   ├── archived/ai-sessions/zcode-plans/  # .zcode 迁入的 13 份计划
│   ├── reference/ai-knowledge/        # .workbuddy 迁入的 7 份知识库
│   └── diagrams/                      # 图表主库（247 SVG / 68 MMD，禁动）
├── scripts/governance/                # 自检脚本 + README
└── .githooks/pre-commit               # 警告模式钩子
```

# `dev` 分支治理一致性评审报告

- **评审对象**：`apex-scaffold`（后端）/ `apex-admin-web`（前端）两仓 `dev` 分支
- **评审时间**：2026-11-16
- **评审基线**：后端 `dev` = `d4311202`，前端 `dev` = `d957e6f`（两仓 `master`/`test` 同点、差 0/0、工作区干净）
- **判定依据**：`AGENTS.md` 第 0 章（协作约束唯一权威）、`docs/specs/project-governance.md`（规范正文 §1–§9）、`scripts/governance/README.md`（自检判据与白名单）
- **本报告性质**：只评审、不整改。整改动作见配套方案 `docs/plans/2026-11-16-dev-alignment-remediation-plan.md`，**待业主确认后执行**。
- **唯一正文**：本文件为跨仓唯一正文；前端仓 `docs/reviews/2026-11-16-dev-branch-governance-audit.md` 只登记前端专属偏差并指回本文件。

---

## 0. 评审方法

### 0.1 判定口径

| 判定 | 含义 |
| --- | --- |
| 达标 | 有可复现证据表明与规范一致 |
| 允许差异 | 规范明文允许（§3.2 既有目录继续有效、仓库类型差异），**不算偏差** |
| 偏差 P0 | 门禁/安全失效，或规范硬规则被违反，会导致治理"空转" |
| 偏差 P1 | 规则与实际脱节、执行依据歧义、应做未做 |
| 偏差 P2 | 可维护性问题，无功能影响 |
| 偏差 P3 | 表述/口径滞后 |
| 未验证 | 受凭据或成本限制未取到证据，不计入达标 |

### 0.2 证据采集命令（可复现）

```bash
# 分支与工作区
cd /Users/work/apex-scaffold  && git rev-parse --abbrev-ref HEAD && git status --porcelain | wc -l
cd /Users/work/apex-admin-web && git rev-parse --abbrev-ref HEAD && git status --porcelain | wc -l
# 文档治理自检
bash scripts/governance/verify-docs.sh      # 后端/前端各跑
bash scripts/governance/check-doc-refs.sh
# 编码红线
bash scripts/governance/audit-backend-rules.sh      # 后端
bash scripts/governance/audit-frontend-rules.sh     # 前端
# 接口文档注解覆盖（后端）
bash scripts/ci/check-springdoc-coverage.sh
bash scripts/dev/check/check-springdoc-annotations.sh --verbose
# CI 分支绑定
grep -rn "branches:" -A2 .github/workflows/
grep -rn "refs/heads/" .github/workflows/
ls .github/workflows            # 前端仓应为不存在
# 忽略规则
grep -nE "\.workbuddy|\.zcode|\.trae|\.qoder|\.codex|\.claude|\.agents|\.ai" .gitignore
grep -c "^\.workbuddy/$" .gitignore
# 私有目录边界
git ls-files | grep -E "^\.(workbuddy|zcode|trae)/"      # 应输出为空
# 记忆台账
grep -c "^| [0-9]" docs/reference/ai-knowledge/07-change-memory.md   # 后端
git log --format='%B%x00' <台账建立提交>^..HEAD | awk 'BEGIN{RS="\0"} /^memory:/{c++} END{print c+0}'
# 图表基线
find docs -name '*.mmd' | wc -l ; find docs -name '*.svg' | wc -l    # 68 / 247
```

---

## 1. 结论摘要

| 维度 | 后端 | 前端 | 判定 |
| --- | --- | --- | --- |
| 分支模型（`dev` 开发、`master` 生产、`test` 验收） | `dev`/`master`/`test` 三支同点，本地在 `dev` | 同左 | 达标 |
| 文档标准目录与索引（§3.1） | 六标准目录齐备，含九个编号目录 | 六标准目录齐备（缺编号目录，属 §3.2 允许差异） | 达标 |
| 唯一正文与指针（§3.3） | 迁移留 `MIGRATED.md`，无重复正文 | 同左 | 达标 |
| 文档自检 | `verify-docs` **0 项**、`check-doc-refs` **0 项** | `verify-docs` **0 项**、`check-doc-refs` **0 项** | 达标 |
| 私有目录边界（§5.1 第 2 条） | 8 类私有目录均已忽略、均未入库 | 同左 | 达标 |
| 忽略规则（§5.1 三条） | `docs/` 未被忽略；8 类私有目录各有规则 | 同左 | 达标（存在重复条目，见 D-06） |
| 提交纪律与钩子（§5.2） | `core.hooksPath=.githooks`，钩子只警告 | 同左 | 达标 |
| 记忆台账与强制校验（§5.4） | 台账 **22 条**；台账建立后 6 次提交**全部**带 `memory:` 标记 | 台账 **16 条**；建立后 5 次提交**全部**带标记 | 达标 |
| 编码红线 | `audit-backend-rules` 无阻断项（B1–B10 达标） | `audit-frontend-rules` 无阻断项 | 达标 |
| 接口文档注解（红线 6） | **98% < 100%**（3 处 `@Operation`、2 个 Router 缺 `@RouterOperations`） | 不适用 | **偏差 D-05** |
| 图表资产基线 | `.mmd` 68 / `.svg` 247 | 无 `.mmd` | 达标 |
| CI 分支绑定 | 4 个 workflow 仍绑 `main`/`develop` | 无任何 CI | **偏差 D-01 / D-02** |
| 权威文件同步 | `AGENTS.md` 第 0 章**未**含台账/分支/许可/预检条款 | 同左 | **偏差 D-03** |
| 任务大盘一致性（§2） | 缺本轮条目；`BE-021` 状态自相矛盾 | 缺 `FE-016` 起条目 | **偏差 D-04** |
| 分支保护生效（§5.3） | 规则未开启（匿名 API 403，无法核实） | 同左 | **偏差 D-07** |

**总判定：有条件通过。** 文档与脚本层显著优于基线（自检全部 0 项、台账与钩子已闭环），但存在 **1 项 P0、6 项 P1、2 项 P2、1 项 P3**，其中 3 项会使治理机制"空转"（CI 不触发、权威文件未同步、任务盘缺失本轮工作），应在下一轮整改关闭。

| 等级 | 数量 | 编号 |
| --- | --- | --- |
| P0 | 1 | D-01 |
| P1 | 6 | D-02 D-03 D-04 D-05 D-07 D-08 |
| P2 | 2 | D-06 D-09 |
| P3 | 1 | D-10 |

---

## 2. 达标项证据（正面清单）

1. **分支模型闭环**：两仓本地 `HEAD` 与 `origin/dev` 一致，`master`/`test` 与 `dev` 同点（`git rev-list --count master..dev` = 0）。推送实测 `ab104ef8..d4311202 dev -> dev`、`3afe885..d957e6f dev -> dev`，无强推。
2. **文档自检**：两仓 `verify-docs.sh` 输出「✅ 文档治理自检通过（0 项问题）」；`check-doc-refs.sh`「结果：0 项」，无断链、无指向私有目录的活链接。
3. **唯一正文**：`_archive`/`archived`、`diagrams` 等既有目录保留原文件；改址的文档在原位置留 `MIGRATED.md` 指针。
4. **忽略规则硬约束**：`docs/` 未被忽略（两仓 `.gitignore` 仅有解释性注释，无生效规则）；8 类私有目录逐条覆盖，实测 `.workbuddy/.trae/.zcode/.qoder/.codex/.claude/.agents/.ai` 各 ≥1 条规则。
5. **私有目录边界**：`git ls-files | grep '^.(workbuddy|zcode|trae)/'` 两仓均为空；私有目录内 md（后端 `.workbuddy` 41、`.trae` 6、`.zcode` 1）全部未入库，符合「正文只放 `docs/`」。
6. **提交纪律**：`core.hooksPath` 两仓均为 `.githooks`；实测 pre-commit 输出「`[memory] OK：本次提交已登记记忆台账`」「`✅ 文档治理自检通过（0 项问题）`」，且**未阻断**提交（符合"只警告不阻断"）。
7. **记忆台账**：后端 `07-change-memory.md` 22 条、前端 `04-change-memory.md` 16 条；自台账建立提交起，两仓后续提交 **100%** 带 `memory:` 标记（后 6/6、前 5/5），钩子第 0 步与 commit-msg 均生效。
8. **编码红线**：后端 `audit-backend-rules.sh`「✅ 后端红线扫描无阻断项」，含「根 POM 无 MVC starter」；前端 `audit-frontend-rules.sh`「✅ 前端红线扫描无阻断项」。
9. **接口 DTO 文档**：`DTO @Schema 覆盖率 185/185 = 100%`。
10. **图表资产**：`.mmd` 68、`.svg` 247，未跌破基线；`BE-001` 30 张补导出已在台账登记。
11. **许可**：后端 `LICENSE` MIT + 商用条款；前端 `LICENSE` MIT 且 `package.json` 声明 `"license": "MIT"`，一致。
12. **Skill/Agent**：三个项目级 Skill（后端/前端/治理）与两仓 `.workbuddy/agents/` 定义已落盘，均在台账 `08/05-agent-tooling.md` 有清单。

---

## 3. 偏差详表

### D-01 [P0] 后端仓 CI 全部绑在已删除的 `main` 分支，门禁实际不触发

- **证据**
  - `.github/workflows/ci-cd.yml:5` `branches: [ main, develop ]`（push）、`:7` `branches: [ main ]`（pull_request）
  - `ci-cd.yml:199` 注释「构建镜像(仅main分支)」、`:206` `if: github.ref == 'refs/heads/main' && …`
  - `.github/workflows/frontend-ci.yml:5,9` `branches: [ main, develop ]`
  - `.github/workflows/doc-consistency-check.yml:6,10` `- main`
  - `.github/workflows/doc-link-check.yml:5-7,15-16` `main` / `develop`
  - `.github/workflows/security-scan.yml:5,7` `branches: [ main, develop ]` / `[ main ]`
- **规范依据**：§5.3 分支模型（远端只余 `dev`/`master`/`test`，`main` 已于 2026-11-16 删除）；§6 门禁要求「文档自检与红线扫描在 CI 内可复现」
- **影响**：任何 `dev`/`master`/`test` 推送都**不会**触发构建、测试、文档一致性检查、安全检查；镜像构建条件永不成立。治理脚本在本地可用，但**没有自动化门禁兜底**，这是当前最严重的"规则存在但机制空转"。
- **归属**：后端仓
- **修复要点**：四个 workflow 的触发分支改为 `[ master, test, dev ]`（`pull_request` 用 `[ master, test ]`），加 `workflow_dispatch` 便于手动验证；镜像条件改 `refs/heads/master`。保留 `main` 的兼容分支属于无意义残留（远端已无 `main`）。

### D-02 [P1] 前端仓完全没有 CI

- **证据**：`ls -d /Users/work/apex-admin-web/.github` → 不存在；`ls .github/workflows` 无输出。
- **规范依据**：§6 门禁（`verify-docs`、红线扫描、`npm run build`/`test`）；§5.3 分支保护清单中「阶段二：Require status checks」需要 job 存在。
- **影响**：前端治理脚本（`verify-docs`、`check-doc-refs`、`audit-frontend-rules`、`check-memory-sync`）只在本地跑；分支保护无法挂 status check；`master` 保护只能停在"阶段一"。
- **归属**：前端仓
- **修复要点**：新增最小 workflow（job：`verify-docs` + `check-doc-refs` + `audit-frontend-rules` + `npm ci && npm run build`），触发 `[ master, test, dev ]`；不引入额外服务，不需要密钥。

### D-03 [P1] `AGENTS.md` 第 0 章未同步本轮新增规则，与"两处同步维护"的自我承诺相冲突

- **证据**：两仓 `grep -n "记忆台账|07-change-memory|04-change-memory|dev 分支|master 分支" AGENTS.md` → **无匹配**。而 `AGENTS.md` 第 0 章 0.1 第 4 条明写「本文件第 0 章为规范入口索引，两者同步维护，冲突以本文件为准」。
- **未同步的四类内容**：① §5.4 变更记忆台账（六字段 + `memory:` 标记 + 钩子强制）；② 分支模型 `dev`(开发) / `master`(生产) / `test`(验收)；③ 许可 MIT（`LICENSE` + `package.json`）；④ `scripts/dev/apex-preflight.sh` 预检与 `docs/05-operations/github-branch-protection.md` 保护清单。
- **影响**：新接入的智能体/新会话只读 `AGENTS.md` 时，**看不到台账义务**，也不会知道当前应在 `dev` 上作业 —— 直接影响治理可执行性。这是"权威文件落后于规范正文"的典型断层。
- **归属**：两仓
- **修复要点**：在第 0 章末尾新增 `### 0.8 记忆台账、分支模型与工具链`（约 15 行），**只追加、不改动附录 A**；验收 `tail -n 306 | shasum -a 256` 保持 后端 `aa0c25fc…60ed` / 前端 `ae5e19db…53a` 不变。

### D-04 [P1] 任务大盘未登记本轮工作；`BE-021` 状态自相矛盾

- **证据**
  - 后端 `docs/tasks/project-tasks.md` 最新条目到 `BE-021`，本轮 12 类改动（台账、钩子、预检、保护清单、许可统一、分支模型、Skill/Agent 安装、三支快进）**均无任务条目**。
  - `BE-021`（第 34 行）状态列写「已完成（登记）」，但完成日期列写「待定」，且处置仍「三选一」——状态与事实不一致。
  - 前端 `docs/tasks/project-tasks.md` 最新条目到 `FE-015`，本轮（MIT 许可、`dev`/`master`/`test`、台账、工具安装、预检）缺 `FE-016` 起条目。
- **规范依据**：§2「`docs/tasks/project-tasks.md` 是任务唯一大盘，状态与产出必须一致」；§5.4「每次改动登记台账」与任务盘登记是两条并行义务。
- **影响**：任务盘失去"唯一真相"地位；后续接手者无法判断哪些治理项已完成、哪些待裁决。
- **归属**：两仓
- **修复要点**：后端补 `BE-022`–`BE-027`（台账/钩子/预检/保护/许可/分支模型），`BE-021` 状态改「待裁决」并给出三选一决策人；前端补 `FE-016`–`FE-019`。

### D-05 [P1] 接口文档注解未达 100%（红线 6 实质性缺口）

- **证据**（`bash scripts/ci/check-springdoc-coverage.sh` + `check-springdoc-annotations.sh --verbose`）
  - `✗ Handler @Operation 覆盖率不达标: 98% < 100%`
  - `✗ OpenAppMigrationRouter 缺少 @RouterOperations 注解`
  - `✗ OpenUserRouter 缺少 @RouterOperations 注解`
  - `[WARN] OpenUserHandler (方法:1, @Operation:0, 缺失:1)`、`[WARN] OpenAppMigrationHandler (方法:2, @Operation:0, 缺失:2)` → 合计 3 处 `@Operation` 缺失
  - `[INFO] Router 类统计: 总数=30, 已注解=28, 缺失=2`
  - 对照：`DTO @Schema 185/185 = 100%` 达标
- **规范依据**：后端红线 6 / 规范 §6「所有 handler 接口方法必须带完整 SpringDoc 注解，能在文档看到完整请求体与响应体」。
- **影响**：`/v3/api-docs` 中这 3 个接口无摘要与响应说明，前端对接需读源码；与"规范 100%"承诺不符。
- **归属**：后端仓
- **修复要点**：为 2 个 Router 补 `@RouterOperations`（或逐方法 `@Operation`），为 3 个 handler 方法补 `@Operation(operationId/summary/description + responses)`。**纯注解改动，不动方法体与路由**；验收脚本回到 100%。

### D-06 [P2] 两仓 `.gitignore` 存在成组重复条目

- **证据**
  - 后端：`.workbuddy/` 出现在 97、103、131 行；`.trae/` 102、132；`.zcode/` 104、133；`dist/` 8、109。
  - 前端：`node_modules/` 2、`dist/` 6、`dist-ssr/` 7、`dist-electron/` 8；`.workbuddy/` 71/86、`.trae/` 72/87、`.trae-html-share-packages/` 73/88、`.zcode/` 74/89。
- **规范依据**：§5.1 第 3 条要求忽略项带「为什么忽略、正文在哪」的注释且来源唯一；重复条目属同一条规则的多份副本，后续改一处漏一处。
- **影响**：无功能影响，纯可维护性；但与"忽略规则单一来源"的治理目标不符。
- **修复要点**：合并为单一「AI 私有目录」区块（8 类齐全 + 每类一句注释），删除重复段；`.gitignore` 不参与构建，改动零风险。

### D-07 [P1] 分支保护规则未生效，当前仍可直推 `master`、可强推、可删除分支

- **证据**：`gh auth status` 未登录；无 `GITHUB_TOKEN`/`GH_TOKEN`；匿名 `GET /repos/apex-cloud-service/apex-scaffold/branches/master/protection` → **403**。因此**无法确认规则是否已开**，但结合本轮所有推送均为直推 `master`/`test`（`ab104ef8..d4311202 master -> master`）可判定：规则**未生效**。
- **规范依据**：§5.3 + `docs/05-operations/github-branch-protection.md`（规则表与 8 条验收清单）。
- **影响**：`dev` 未评审即可直推生产；强推与误删分支无服务端拦截；与"`master` 只接受 PR"的设计不符。
- **归属**：两仓（GitHub 侧）
- **修复要点**：二选一 —— ①网页按文档 §2 勾选；②提供 PAT，按文档 §3 `gh api` 命令批量配置。**建议先只开「禁强推 + 禁删除」**（对现有 `--ff-only` 发布零影响），多人协作与 CI 就绪后再加「Require PR + 线性历史」。

### D-08 [P1] `ai-knowledge/README.md` 仍称"手工快照、不会自动更新"，与 §5.4 强制登记直接冲突

- **证据**：`docs/reference/ai-knowledge/README.md:21`「本目录与三张记忆卡都是**手工快照，不会随代码自动更新**。开工前跑 `python3 .workbuddy/ops/maintain.py status`…」；前端 `05` 系列同源表述。
- **规范依据**：§5.4（"每次改动强制登记 + `check-memory-sync.sh` 钩子校验"）。
- **影响**：两处指令矛盾——一处说"手工、靠自己记得更新"，一处说"钩子强制校验"。执行者会按前者跳过登记，而 `--strict` 模式会阻断提交，形成"规则打架"。
- **修复要点**：改成「台账 `07-change-memory.md` 由 §5.4 强制登记并由 `.githooks` 校验；三张记忆卡与 MEMORY-CARD 是**派生视图**，只做摘要，不作验收依据」。前端同改。

### D-09 [P2] `MEMORY-CARD` 要求同日同步私有目录 `MEMORY.md`，该动作不可复核

- **证据**：`MEMORY-CARD.md` §9「层级契约：每次改动同日同步 `.workbuddy/memory/MEMORY.md`」；而 `.workbuddy/` 按 §5.1 第 2 条**整体忽略、不入库**。
- **影响**：该要求既无法被 `verify-docs` 检查，也无法在他人机器复现；与"私有目录不得留存正文"存在张力。执行者无法自证合规。
- **修复要点**：降级为「可选镜像（本地便利），**不作为验收项**」；验收只看 `07-change-memory.md` 条目 + 提交 `memory:` 标记。

### D-10 [P3] 规范 §5.3 默认分支表述滞后于既成事实

- **证据**：`docs/specs/project-governance.md:190`「远端默认分支**需**在 GitHub Settings → General → Default branch 设为 `master`」。
- **现状**：默认分支已是 `master`，`main` 已删除（第 219 行已记录该事实）。
- **影响**：读者会以为还需手动操作。
- **修复要点**：改为「远端默认分支**已**设为 `master`（2026-11-16）；如需变更按以下路径…」。

---

## 4. 允许差异（判定为合规，不整改）

| 项 | 说明 | 依据 |
| --- | --- | --- |
| 前端无九个编号目录 | 前端 `docs` 为 `api/architecture/archived/features/guides/notes/plans/records/reference/reviews/specs/tasks`，自成体系 | §3.2「既有目录继续有效（不搬迁、不改名、不改内容）」 |
| 前端运维类文档在 `docs/guides/` 而非 `05-operations/` | 保护清单、部署说明统一放 `guides` | 同上 |
| 后端 `.workbuddy` 下有 41 个 md | 全是智能体私有工作记录，未入库 | §5.1 第 2 条允许 |
| `release.yml` 用 tag 触发 | 不绑分支，天然不受 `main` 影响 | §5.3 发布走 tag |

---

## 5. 未验证项（不计入达标，也不计偏差）

| 项 | 原因 | 建议的补验方式 |
| --- | --- | --- |
| 分支保护实际状态 | 匿名 API 403、`gh` 未登录、无 token | 网页查看或提供 PAT 后 `gh api` 查询 |
| 全量构建与测试（`mvnd verify` / `npm run test:coverage`） | 避免在工作区产生 `target/`、`coverage/` 噪声，且耗时 | 整改批次内单独跑一次并留 report |
| 覆盖率阈值实际值 | 同上 | 与上一条同批执行 |

---

## 6. 对"不影响功能"的承诺核对

本报告涉及的全部整改动作分类如下，**均不触达业务逻辑**：

| 类型 | 涉及偏差 | 是否改动业务代码 |
| --- | --- | --- |
| CI 配置（YAML 触发分支、job 列表） | D-01 D-02 | 否 |
| Markdown 文档（AGENTS 第 0 章、规范 §5.3、ai-knowledge README、MEMORY-CARD、任务盘） | D-03 D-04 D-08 D-09 D-10 | 否 |
| `.gitignore`（仅删除重复行、归并注释） | D-06 | 否（不参与构建与打包） |
| 注解（`@Operation`、`@RouterOperations`） | D-05 | 仅注解，方法体、路由、DTO 不动 |
| GitHub 仓库设置 | D-07 | 否 |

**附加约束**（整改执行时必须遵守）：不执行 `git reset --hard` / `git push -f`；不用 `git add -A`；不清理他人未提交改动；`docs/` 全量入库；每批一次提交。

---

## 7. 复现附录：本轮实测输出摘录

```
# 后端
✅ 后端红线扫描无阻断项
✅ 文档治理自检通过（0 项问题）  / check-doc-refs：0 项
✗ Handler @Operation 覆盖率不达标: 98% < 100%
✓ DTO @Schema 覆盖率达标: 100% >= 100%
Router 类统计: 总数=30, 已注解=28, 缺失=2
SVG=247  MMD=68   hooksPath=.githooks
git log 台账建立后：带 memory 标记=6 / 6

# 前端
✅ 文档治理自检通过（0 项问题）  / check-doc-refs：0 项
✅ 前端红线扫描无阻断项
hooksPath=.githooks
git log 台账建立后：带 memory 标记=5 / 5
.github 不存在（无 CI）
```

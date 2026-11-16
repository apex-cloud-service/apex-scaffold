# `dev` 分支一致性整改方案（待业主确认后执行）

- **配套评审**：`docs/reviews/2026-11-16-dev-branch-governance-audit.md`（偏差编号 D-01 ~ D-10）
- **基线**：后端 `dev` `d4311202`、前端 `dev` `d957e6f`；`master`/`test` 与 `dev` 同点；工作区干净
- **状态**：**方案待确认，尚未执行任何整改**
- **总原则**：只改「CI 配置 / Markdown 文档 / `.gitignore` / 注解」四类，**零业务逻辑改动**；每批一次提交、可单独 `git revert`；全程不 `reset --hard`、不 `push -f`、不用 `git add -A`

---

## 0. 执行前置（每次开工必做）

```bash
# 确认在 dev 且干净
cd /Users/work/apex-scaffold  && git rev-parse --abbrev-ref HEAD && git status --porcelain
cd /Users/work/apex-admin-web && git rev-parse --abbrev-ref HEAD && git status --porcelain
# 一键预检（应 OK=n WARN≤1 FAIL=0）
bash scripts/dev/apex-preflight.sh --fast
```

**基线快照（改动前记录，用于事后比对"不影响功能"）**

| 快照项 | 后端 | 前端 |
| --- | --- | --- |
| `HEAD` | `d4311202` | `d957e6f` |
| `AGENTS.md` 附录 A 哈希 | `tail -n 306 \| shasum -a 256` → `aa0c25fc…60ed` | `tail -n 712` → `ae5e19db…53a` |
| `.mmd` / `.svg` | 68 / 247 | — |
| `verify-docs` | 0 项 | 0 项 |
| `check-doc-refs` | 0 项 | 0 项 |
| 红线扫描 | 无阻断 | 无阻断 |
| 台账条目 | 22 | 16 |

> 附录 A 哈希是"原文一字未改"的唯一硬证据，**每批改完都要重算一次**。

---

## 1. 批次总览

| 批次 | 关闭偏差 | 内容 | 改动类型 | 预计工时 | 风险 |
| --- | --- | --- | --- | --- | --- |
| **B1** | D-03 D-08 D-09 D-10 | 权威文件与规范口径同步 | Markdown | 25 min | 极低（附录 A 不动） |
| **B2** | D-04 | 任务大盘补齐与状态纠偏 | Markdown | 20 min | 无 |
| **B3** | D-01 D-02 | CI 分支对齐 + 前端最小 CI | YAML | 30 min | 低（不影响运行时代码） |
| **B4** | D-05 | SpringDoc 注解补齐至 100% | Java 注解 | 30 min | 低（不动方法体） |
| **B5** | D-06 | `.gitignore` 去重与注释归并 | 纯配置 | 10 min | 极低 |
| **B6** | D-07 | 分支保护规则开启 | GitHub 设置 | 15 min | 中（改变推送方式，需决策） |

**执行顺序建议**：B1 → B2 → B5 → B3 → B4 → B6。
理由：B1/B2 是"规则自洽"，先让文档说真话；B5 零风险顺手做；B3 与 B4 会产生可观察的门禁输出，放在规则对齐之后；B6 最后做，因为它改变发布动作。

**每批统一动作**：改 → 自检 → 台账登记 → 一次提交 → 推送 `dev`。发布时 `master`/`test` 走 `git merge --ff-only dev && git push`（B6 完成后改为 PR）。

---

## 2. B1 · 权威文件与口径同步（D-03 / D-08 / D-09 / D-10）

### 2.1 `AGENTS.md` 第 0 章新增 `### 0.8`（两仓）

- **位置**：插在第 0 章末尾、`## 附录 A` 之前（后端现第 100 行前；前端现第 97 行前）。**只插不删**。
- **插入内容（后端版，约 16 行；前端把路径换成前端对应文件）**

```markdown
### 0.8 记忆台账、分支模型与工具链（2026-11-16 增补）

1. **变更记忆台账（强制）**：任何改动必须写入 `docs/reference/ai-knowledge/07-change-memory.md`（append-only，六字段）。提交信息必须含 `memory:` 标记（指向条目）。校验：`bash scripts/governance/check-memory-sync.sh`（`--strict` 才阻断），已接入 `.githooks/pre-commit` 第 0 步与 `.githooks/commit-msg`。
2. **分支模型**：`dev` 为日常开发分支（**所有改动在 `dev` 提交**）；`master` 为生产、`test` 为验收，仅在发布时以 `--ff-only` 从 `dev` 合入；远端只保留 `dev`/`master`/`test`，`main` 已删除。
3. **许可**：MIT（根 `LICENSE`；前端另有 `package.json` `"license": "MIT"`）。分发与二次开发按 MIT 条款。
4. **工具链**：开工先跑 `bash scripts/dev/apex-preflight.sh`（4 模式 / 10 类检查，FAIL>0 不得提交）。
5. **分支保护**：`master`/`test` 目标规则与开启步骤见 `docs/05-operations/github-branch-protection.md`（前端：`docs/guides/github-branch-protection.md`）。
6. 本节与 `docs/specs/project-governance.md` §5.3/§5.4 同步维护；冲突以本文件为准。
```

- **验收**

```bash
tail -n 306 AGENTS.md | shasum -a 256   # 后端须仍为 aa0c25fc…60ed
tail -n 712 AGENTS.md | shasum -a 256   # 前端须仍为 ae5e19db…53a
grep -c "0.8 记忆台账" AGENTS.md          # 1
```

### 2.2 规范 §5.3 默认分支表述纠偏（D-10）

`docs/specs/project-governance.md:190` 原文：

> 远端默认分支需在 GitHub Settings → General → Default branch 设为 `master`。

改为：

> 远端默认分支**已**设为 `master`（2026-11-16，两仓均已切换并删除 `main`）。如需再次变更，按 GitHub Settings → General → Default branch 操作，变更后同步本条与 `docs/records/2026-11-16-github-remote-onboarding.md` §5。

前端同名文件同改。

### 2.3 `ai-knowledge/README.md` 口径纠偏（D-08）

后端 `docs/reference/ai-knowledge/README.md:21` 改为：

> **维护入口**：变更台账 `07-change-memory.md` 由 §5.4 **强制登记**，并由 `.githooks/pre-commit`（第 0 步）与 `commit-msg` 校验（`check-memory-sync.sh`，默认警告、`--strict` 阻断）。本目录的 `MEMORY-CARD.md` 与三张记忆卡是**派生视图**，只做摘要与检索便利，**不作为验收依据**；验收以台账条目与提交 `memory:` 标记为准。

前端 `05` 系列同改（指向 `04-change-memory.md`）。

### 2.4 `MEMORY-CARD.md` 层级契约降级（D-09）

后端 `MEMORY-CARD.md` §9 现行「每次改动同日同步 `.workbuddy/memory/MEMORY.md`」改为：

> **层级契约**（可选）：可在同日顺手同步 `.workbuddy/memory/MEMORY.md` 作为本地便利镜像。该文件位于 §5.1 允许忽略的 AI 私有目录，**不纳入版本库、不作验收项**；验收只看 `docs/reference/ai-knowledge/07-change-memory.md` 条目与提交 `memory:` 标记。

### 2.5 提交

```bash
git add AGENTS.md docs/specs/project-governance.md docs/reference/ai-knowledge/README.md docs/reference/ai-knowledge/MEMORY-CARD.md docs/reference/ai-knowledge/07-change-memory.md
git commit -m "docs(governance): 第0章补 0.8 台账/分支/许可/工具链，纠偏默认分支与快照口径

memory: 07-change-memory.md#23"
git push
```

---

## 3. B2 · 任务大盘补齐与状态纠偏（D-04）

### 3.1 后端 `docs/tasks/project-tasks.md` 新增 6 条

| ID | 优先级 | 任务 | 状态 | 日期 | 产出 |
| --- | --- | --- | --- | --- | --- |
| BE-022 | P1 | 变更记忆台账建立 + §5.4 强制登记规则 | 已完成 | 2026-11-16 | `docs/reference/ai-knowledge/07-change-memory.md`、规范 §5.4 |
| BE-023 | P1 | 台账自动校验脚本 + `pre-commit`/`commit-msg` 钩子（警告模式） | 已完成 | 2026-11-16 | `scripts/governance/check-memory-sync.sh`、`.githooks/` |
| BE-024 | P1 | 一键预检 `scripts/dev/apex-preflight.sh`（4 模式 / 10 类检查） | 已完成 | 2026-11-16 | 脚本 + 三个 Skill 入口 |
| BE-025 | P1 | 分支模型 `dev`/`master`/`test` 建立，删除 `main` | 已完成 | 2026-11-16 | 远端三支；`docs/records/2026-11-16-github-remote-onboarding.md` §5 |
| BE-026 | P1 | 许可统一 MIT（后端 MIT + 商用条款） | 已完成 | 2026-11-16 | `LICENSE`、台账 `05` 条 |
| BE-027 | P1 | 分支保护规则清单成文，规则**待开启** | 进行中 | 2026-11-16 | `docs/05-operations/github-branch-protection.md` |

同时把 `BE-021` 整行改为：

> | BE-021 | P3 | 5 张序列图激活盒恢复（`78/84/90/93/94`）：已归一化为无语义损失的默认生命线 | **待裁决** | — | 三选一：①接受现状 ②人工重排 ③改写为 `opt/alt` 片段 | 见 `docs/diagrams/README.md`「2026-11-16 批量补导出记录」 |

### 3.2 前端 `docs/tasks/project-tasks.md` 新增 4 条

| ID | 优先级 | 任务 | 状态 | 日期 |
| --- | --- | --- | --- | --- |
| FE-016 | P1 | 许可统一 MIT（`LICENSE` + `package.json`） | 已完成 | 2026-11-16 |
| FE-017 | P1 | 变更记忆台账 `04-change-memory.md` + §5.4 强制登记 | 已完成 | 2026-11-16 |
| FE-018 | P1 | 台账校验脚本与钩子 + 一键预检脚本 | 已完成 | 2026-11-16 |
| FE-019 | P1 | 分支模型 `dev`/`master`/`test` + 保护清单（待开启） | 进行中 | 2026-11-16 |

### 3.3 验收

```bash
grep -c "^| BE-02" docs/tasks/project-tasks.md   # 后端 ≥6
grep -n "BE-021" docs/tasks/project-tasks.md     # 状态列为「待裁决」
grep -c "^| FE-01" docs/tasks/project-tasks.md   # 前端 ≥4（FE-016~019）
```

提交：`docs(tasks): 补齐本轮治理条目并纠偏 BE-021 状态`，带 `memory:` 标记。

---

## 4. B5 · `.gitignore` 去重与注释归并（D-06）

> 先做零风险项，可与 B1 同批推送前分开提交。

### 4.1 后端 `apex-scaffold/.gitignore`

现状重复：`.workbuddy/` 97/103/131、`.trae/` 102/132、`.zcode/` 104/133、`dist/` 8/109。

**目标形态**（只保留一处 `dist/` 与一处私有目录区块）：

```gitignore
# ---- 构建产物 ----
target/
**/target/
dist/            # 前端产物目录，后端仓内不会出现，防误提交

# ---- AI 智能体私有目录（正文一律写 docs/，此处只放工作记录）----
.workbuddy/      # WorkBuddy 会话/记忆（正文见 docs/reference/ai-knowledge/）
.trae/           # Trae 会话与文档草稿（正文见 docs/）
.zcode/          # ZCode 计划/会话（正文见 docs/plans/）
.qoder/          # Qoder 工作区
.codex/          # Codex 工作区
.claude/         # Claude 工作区
.agents/         # 通用 Agent 定义与缓存
.ai/             # 其他 AI 工具目录
```

### 4.2 前端 `apex-admin-web/.gitignore`

现状重复：`node_modules/` 2、`dist/` 6、`dist-ssr/` 7、`dist-electron/` 8；私有目录 71–74 与 86–89 各一遍。

**目标形态**：`node_modules/`、`dist/`、`dist-ssr/`、`dist-electron/` 各保留一处；私有目录合并为单一区块（含 `.trae-html-share-packages/`，带注释「Trae 分享包缓存，正文见 docs/」）。

### 4.3 操作方式（安全，不整文件重写）

```bash
# 1) 先看重复项
grep -nE "^\.(workbuddy|trae|zcode)/$|^dist/$" .gitignore
# 2) 打开编辑器按上面目标形态合并（保留解释性注释，删除重复行）
# 3) 结构校验：每条规则只出现一次
awk -F: '{print $1}' <(grep -oE "^[^#].+" .gitignore) | sort | uniq -d   # 期望输出为空
# 4) 确认 docs 未被忽略
grep -nE "^docs/|^/docs" .gitignore    # 期望：无匹配
```

### 4.4 验收

```bash
git status --porcelain          # 仅 .gitignore 变更
git check-ignore -v docs/README.md   # 无输出（未被忽略）
bash scripts/governance/verify-docs.sh | tail -1   # 仍 0 项
```

提交：`chore(gitignore): 合并重复忽略项为单一私有目录区块`（带 `memory:` 标记）。

---

## 5. B3 · CI 分支对齐 + 前端最小 CI（D-01 / D-02）

### 5.1 方案选择

| 方案 | 做法 | 优点 | 缺点 |
| --- | --- | --- | --- |
| **A（推荐）** | 触发分支改 `[ master, test, dev ]`，PR 改 `[ master, test ]`，加 `workflow_dispatch` | 改动最小、意图清晰、无额外逻辑 | 新增分支要记得同步 |
| B | `push` 全分支触发，job 内 `if` 判断 ref | 永不漏触发 | 单次推送触发 job 多，免费额度消耗快 |

**推荐 A**，并在每个 workflow 顶部加一句注释「分支模型变更时同步此列表」。

### 5.2 后端四个 workflow 的具体改法

| 文件 | 行 | 现状 | 改为 |
| --- | --- | --- | --- |
| `.github/workflows/ci-cd.yml` | 5 | `branches: [ main, develop ]` | `branches: [ master, test, dev ]` |
| 同上 | 7 | `branches: [ main ]`（pull_request） | `branches: [ master, test ]` |
| 同上 | 199 | `# 构建镜像(仅main分支)` | `# 构建镜像(仅 master 分支)` |
| 同上 | 206 | `github.ref == 'refs/heads/main'` | `github.ref == 'refs/heads/master'` |
| `.github/workflows/frontend-ci.yml` | 5, 9 | `[ main, develop ]` | `[ master, test, dev ]`（PR：`[ master, test ]`） |
| `.github/workflows/doc-consistency-check.yml` | 6, 10 | `- main` | `- master`（并加 `- dev`、`- test`，让文档门禁在开发分支就跑） |
| `.github/workflows/doc-link-check.yml` | 5–7, 15–16 | `main` / `develop` | `master` / `test` / `dev` |
| `.github/workflows/security-scan.yml` | 5, 7 | `[ main, develop ]` / `[ main ]` | `[ master, test, dev ]` / `[ master ]` |
| 全部四个 | 顶部 | — | 加 `workflow_dispatch:` 便于手动验证 |

**保留不改**：`security-scan.yml:40` `dependency-check/Dependency-Check_Action@main` —— 这是外部 Action 的版本引用，与分支模型无关。

**示例片段（ci-cd.yml 头部目标形态）**

```yaml
name: CI/CD
# 分支模型：dev（开发）→ test（验收）→ master（生产）；改动本文件请同步此列表
on:
  push:
    branches: [ master, test, dev ]
  pull_request:
    branches: [ master, test ]
  workflow_dispatch:
```

### 5.3 前端新增最小 CI（`apex-admin-web/.github/workflows/ci.yml`）

```yaml
name: ci
# 分支模型：dev（开发）→ test（验收）→ master（生产）
on:
  push:
    branches: [ master, test, dev ]
  pull_request:
    branches: [ master, test ]
  workflow_dispatch:

jobs:
  docs-and-rules:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: 文档自检
        run: bash scripts/governance/verify-docs.sh
      - name: 断链检查
        run: bash scripts/governance/check-doc-refs.sh
      - name: 前端红线扫描
        run: bash scripts/governance/audit-frontend-rules.sh
      - name: 记忆台账校验
        run: bash scripts/governance/check-memory-sync.sh

  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: 20
          cache: npm
      - run: npm ci
      - run: npm run build
```

> 说明：前端治理脚本是纯 bash，Ubuntu runner 可直接跑；`npm ci` 需要仓库有 `package-lock.json`（本仓已有）。**不引入 secrets，不需要 PAT。**

### 5.4 验收

```bash
# 1) YAML 结构合法性（本地即可验，不必等 CI）
python3 -c "import yaml,sys;[yaml.safe_load(open(f)) for f in sys.argv[1:]];print('YAML OK')" .github/workflows/*.yml
# 2) 分支引用无残留
grep -rn "refs/heads/main\|branches: \[ main" .github/workflows/ && echo "仍有 main 残留" || echo "无 main 残留"
# 3) 实际触发验证（推 dev 后看 Actions 页签，或 gh run list）
# 前端：本地预演 CI 脚本
bash scripts/governance/verify-docs.sh && bash scripts/governance/check-doc-refs.sh && bash scripts/governance/audit-frontend-rules.sh && bash scripts/governance/check-memory-sync.sh
```

提交：`ci(workflows): 触发分支对齐 dev/master/test，新增前端最小 CI`（带 `memory:` 标记；后端、前端各一次）。

**风险与回滚**：CI 失败不影响代码运行；回滚 `git revert <sha>` 即可。

---

## 6. B4 · SpringDoc 注解补齐至 100%（D-05）

### 6.1 缺口清单（实测）

| 位置 | 缺口 |
| --- | --- |
| `OpenAppMigrationRouter` | 缺 `@RouterOperations`（类级），其 handler 2 个方法缺 `@Operation` |
| `OpenUserRouter` | 缺 `@RouterOperations`（类级），其 handler 1 个方法缺 `@Operation` |

### 6.2 改法（两种，任选其一，推荐按类统一）

**方式 1（推荐，与现有 28 个 Router 一致）：类上加 `@RouterOperations`**

```java
@RouterOperations({
    @RouterOperation(
        path = "/open/app-migration/xxx", method = RequestMethod.POST,
        beanClass = OpenAppMigrationHandler.class, beanMethod = "xxx",
        operation = @Operation(
            operationId = "openAppMigrationXxx",
            summary = "……",
            description = "……",
            tags = {"open-app-migration"},
            requestBody = @RequestBody(required = true,
                content = @Content(schema = @Schema(implementation = XxxRequest.class))),
            responses = {
                @ApiResponse(responseCode = "200", description = "成功",
                    content = @Content(schema = @Schema(implementation = XxxResponse.class))),
                @ApiResponse(responseCode = "400", description = "参数错误"),
                @ApiResponse(responseCode = "500", description = "服务端异常")
            }
        )
    )
})
```

**方式 2：保留类级注解为空，直接在 handler 方法上加 `@Operation`**（本项目 handler 层已普遍使用该写法，二者择一即可，**不要重复定义**）。

### 6.3 硬约束

1. **只加注解**：方法体、签名、路由 `RouterFunction` 的 `path/method` 不改；
2. 请求体/响应体必须落到具体 DTO（`@Schema(implementation = …)`），保证 `/v3/api-docs` 能看到结构；
3. 不新增依赖（`springdoc-openapi` 已在 `pom.xml`）；
4. 中文摘要与项目风格一致，不写"TODO"。

### 6.4 验收

```bash
bash scripts/ci/check-springdoc-coverage.sh              # 期望：Handler @Operation 100%、DTO @Schema 100%
bash scripts/dev/check/check-springdoc-annotations.sh --verbose | tail -5   # 期望：缺失=0
# 编译与单模块回归（先跑受影响模块，再跑全量）
mvnd -pl :apex-applications-parent:apex-example -am -q verify
# 接口文档可生成（示例模块内的 SpringBoot 测试或本地启动）
```

> 若模块坐标与上面不同，先 `grep -rn "OpenUserHandler" --include=pom.xml .` 定位所属模块，再把 `-pl` 换成实际 artifactId。

提交：`docs(openapi): 补齐 OpenUser/OpenAppMigration 路由与 handler 的 SpringDoc 注解`（带 `memory:` 标记）。

**风险与回滚**：注解不影响运行行为；若 `verify` 因注解引用类名写错失败，`git revert` 或修正后重提。

---

## 7. B6 · 分支保护开启（D-07）

### 7.1 强度两档（需业主裁定）

| 档位 | `master` / `test` | `dev` | 对现有流程的影响 |
| --- | --- | --- | --- |
| **档 1（推荐先开）** | 禁强推 + 禁删除 + 要求对话解决 | 禁强推 + 禁删除 | **零影响**：`merge --ff-only && push` 照旧可用 |
| 档 2（协作就绪后） | 档 1 + Require PR（1 审批）+ Require linear history + Require status checks（挂 CI job） | 禁强推 + 禁删除 | 直推被拒，改为 `gh pr create --base master --head dev` + **Rebase and merge**；`master` 与 `dev` 不再同点，哈希会变（内容一致） |

### 7.2 操作路径（二选一）

**① 网页**（无需凭据，2 分钟/仓）

```
https://github.com/apex-cloud-service/apex-scaffold/settings/branches
https://github.com/apex-cloud-service/apex-admin-web/settings/branches
```

对 `master`、`test`、`dev` 分别点 `Add branch protection rule`，按档位勾选。细则见 `docs/05-operations/github-branch-protection.md` §2。

**② 命令**（需 PAT，`repo` 权限）

```bash
export GH_TOKEN=<pat>
for repo in apex-scaffold apex-admin-web; do
  for br in master test; do
    curl -sS -X PUT \
      -H "Authorization: Bearer $GH_TOKEN" \
      -H "Accept: application/vnd.github+json" \
      "https://api.github.com/repos/apex-cloud-service/$repo/branches/$br/protection" \
      -d '{"required_status_checks":null,"enforce_admins":false,
           "required_pull_request_reviews":null,"restrictions":null,
           "allow_force_pushes":false,"allow_deletions":false,
           "required_conversation_resolution":true}'
  done
  curl -sS -X PUT -H "Authorization: Bearer $GH_TOKEN" \
    "https://api.github.com/repos/apex-cloud-service/$repo/branches/dev/protection" \
    -d '{"required_status_checks":null,"enforce_admins":false,
         "required_pull_request_reviews":null,"restrictions":null,
         "allow_force_pushes":false,"allow_deletions":false}'
done
```

### 7.3 验收（8 条，逐条可查）

```bash
# 1) 保护已开
curl -sS -H "Authorization: Bearer $GH_TOKEN" \
  https://api.github.com/repos/apex-cloud-service/apex-scaffold/branches/master/protection | head -20
# 2) 强推被拒（预期 remote rejected）
git push --force-with-lease origin master 2>&1 | tail -2
# 3) 删除被拒
git push origin --delete test 2>&1 | tail -2
# 4) 正常 ff 推送仍可用（档 1）
# 5) 文档保护清单 8 条逐项打勾并回填
# 6) 台账登记（类型=治理）
# 7) AGENTS 0.8 第 5 条指向的文档可达
# 8) 前端 `docs/guides/` 指针与本条一致
```

### 7.4 回滚

网页 `Delete` 规则即可；命令方式 `curl -X DELETE .../protection`。**不涉及代码，可逆。**

---

## 8. 总验收清单（整改完成后一次性执行）

```bash
# —— 后端 ——
cd /Users/work/apex-scaffold
bash scripts/dev/apex-preflight.sh                  # 期望 FAIL=0
bash scripts/governance/verify-docs.sh | tail -1    # 0 项
bash scripts/governance/check-doc-refs.sh | tail -1 # 0 项
bash scripts/governance/audit-backend-rules.sh | tail -1
bash scripts/ci/check-springdoc-coverage.sh | grep -E "Handler|DTO"   # 双 100%
tail -n 306 AGENTS.md | shasum -a 256               # aa0c25fc…60ed 不变
find docs -name '*.mmd' | wc -l; find docs -name '*.svg' | wc -l      # 68 / 247
grep -rn "refs/heads/main\|\[ main" .github/workflows/ | wc -l        # 0
python3 -c "import yaml,glob;[yaml.safe_load(open(f)) for f in glob.glob('.github/workflows/*.yml')];print('YAML OK')"

# —— 前端 ——
cd /Users/work/apex-admin-web
bash scripts/dev/apex-preflight.sh
bash scripts/governance/verify-docs.sh | tail -1
bash scripts/governance/check-doc-refs.sh | tail -1
bash scripts/governance/audit-frontend-rules.sh | tail -1
tail -n 712 AGENTS.md | shasum -a 256                # ae5e19db…53a 不变
python3 -c "import yaml,glob;[yaml.safe_load(open(f)) for f in glob.glob('.github/workflows/*.yml')];print('YAML OK')"
npm run build                                        # 构建不受影响（CI 新增前先本地确认）

# —— 两仓共性 ——
git status --porcelain        # 干净
git rev-parse --short HEAD    # 记录新基线
```

**通过标准**：以上全部为期望输出，且 `docs/` 下 `.svg`/`.mmd` 数量只增不减，附录 A 哈希不变。

---

## 9. 明确不做的事

1. 不改任何业务代码逻辑、接口路径、DTO 结构、SQL、配置项；
2. 不删除、不移动、不改写任何既有文档正文（只追加/纠偏指定段落）；
3. 不执行 `git reset --hard`、`git push -f`、`git add -A`；
4. 不清除他人未提交改动（当前工作区干净，若执行期间出现他人改动，单独 `stash` 或跳过该文件）；
5. 不为"过 CI"而放宽门禁阈值（图表基线 68/247 只增不减，SpringDoc 必须 100%）；
6. 不在本轮处理 `BE-021`（激活盒三选一）与 `BE-001` 剩余项，除非业主另行指定。

---

## 10. 待业主确认的 3 个决策点

| # | 决策 | 选项 | 建议 |
| --- | --- | --- | --- |
| 1 | CI 触发方案 | A 显式列 `[master,test,dev]` / B 全分支 + ref 判断 | **A** |
| 2 | 分支保护强度 | 档 1（禁强推/禁删除） / 档 2（+Require PR+线性历史+status checks） | 先 **档 1**，CI 绿了再升档 2 |
| 3 | `BE-021` 5 张序列图激活盒 | ①接受现状 ②人工重排 ③`opt/alt` 替代 | ①（无语义损失，成本最低） |

**确认方式**：回复「按方案执行 + 决策点 1A / 2 档1 / 3①」即可；任一批次也可单独授权，例如「先做 B1、B2、B5」。

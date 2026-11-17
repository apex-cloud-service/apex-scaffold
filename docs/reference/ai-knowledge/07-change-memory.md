# 07 · 变更记忆（流水台账）

> **定位**：本仓所有「已落地改动」的事实登记处，append-only（只追加、不改写历史条目）。
> 与 `01`–`06` 的关系：`01`–`06` 是**时点快照**（现状、地图、陷阱、交付），本篇是**时间轴流水**——快照回答「现在是什么样」，本篇回答「谁在什么时候改了什么、凭什么证据」。
> **强制写入**：任何改动都必须在本篇登记一条，规则见 `docs/specs/project-governance.md` §5.4「记忆文档同步（强制）」。
> **基线**：2026-11-16 · 后端 `apex-scaffold` · 默认工作分支 `dev`

---

## 1. 写入规则

| 项 | 要求 |
| --- | --- |
| **何时写** | 任何改变仓库内容或仓库状态的动作：代码、配置、文档、脚本、图表、许可、`.gitignore`、分支结构、远端关联。**在提交同一回合完成**，不允许「先提交、以后补」 |
| **写在哪** | 只写本篇 §3 的登记表；现状类结论变化时**同时**更新对应快照篇（`01`/`02`/`06`）与本卡 `MEMORY-CARD.md` |
| **必填字段** | 日期 · 类型 · 摘要（做了什么、为什么）· 提交（短哈希，未提交写 `未提交`）· 证据（实测命令与输出、校验脚本结果）· 影响面（是否影响功能/接口/构建） |
| **禁止** | ① 把私有目录（`.workbuddy/`、`.zcode/`、`.trae/`）内容抄进本篇；② 只写「已完成」不写证据；③ 用「若干」「优化了」这类无信息量措辞；④ 删除或改写历史条目（发现记错时**追加更正条目**并注明 `更正 #n`） |
| **提交带记忆** | 提交信息末行写 `memory: 07-change-memory.md#<条目编号>`；纯机械改动（如格式化）也要写 `memory: n/a（原因）` |

**类型枚举**（登记时只取其一）：`代码` / `技术债` / `文档` / `脚本` / `图表` / `许可` / `分支` / `远端` / `治理`。

---

## 2. 当前基线条目（随改动更新，最新在前）

| 事实 | 值 | 采样日 | 证据 |
| --- | --- | --- | --- |
| 默认工作分支 | **`dev`**（本地长期停留；日常开发/文档/整改都提交在 `dev`） | 2026-11-16 | `git rev-parse --abbrev-ref HEAD` → `dev` |
| 长期分支 | `master`（生产基线）/ `dev`（默认工作）/ `test`（验收） | 2026-11-16 | `git ls-remote origin` 仅三类 heads，`main` 已删除 |
| 远端 | `origin` = `git@github.com:apex-cloud-service/apex-scaffold.git`，固定 SSH 443 | 2026-11-16 | `git config core.sshCommand`、`git ls-remote origin` |
| 许可 | **MIT**（`LICENSE`）+ 标准版/企业版商业授权（`LICENSE-COMMERCIAL.md`） | 2026-11-16 | `head -2 LICENSE`、`docs/07-commercial/COMMERCIAL_VERSIONS.md:238` |
| 图表基线 | `.mmd` **68** / `.svg` **247**（68/68 同名配对） | 2026-11-16 | `find docs -name '*.mmd' \| wc -l`、`bash scripts/governance/verify-docs.sh` |
| 自检基线 | `verify-docs.sh` **0** / `check-doc-refs.sh` **0** / `audit-backend-rules.sh` **0 阻断** | 2026-11-16 | 三脚本实测输出 |
| 归档方式 | `git archive` + `git bundle`（禁用 `tar .`） | 2026-11-16 | 规范 §5.3、`/Users/work/backup/` |

---

## 3. 变更登记表

### 3.1 2026-11-16

| # | 日期 | 类型 | 摘要 | 提交 | 证据 | 影响面 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 2026-11-16 | 技术债 | **BE-017** `VirtualThreadBoundedExecutor` 删手写 `LoggerFactory` 字段，改 `@Slf4j`（变量名仍为 `log`，日志格式与字段不变） | `9cf1bfbb` | `mvnd -pl :apex-common -am -B verify` BUILD SUCCESS（单测/覆盖率/failsafe 全绿）；`audit-backend-rules.sh` 阻断 1→0。不改线程池行为 | 不改线程池行为；日志变量名仍为 `log`，日志字段与格式不变 |
| 2 | 2026-11-16 | 图表 | **BE-001** 30 个 `.mmd` 补齐同名 `.svg`（`.svg` 217→247，`.mmd` 零删除，68/68 配对） | `9cf1bfbb` | `find docs -name '*.svg' \| wc -l` → 247；源文件未删改（除第 3 条归一化） | 仅文档/图表资产，不改运行代码 |
| 3 | 2026-11-16 | 图表 | 6 个历史源文件（`78/84/90/93/94/95`）做**语法归一化**：删除非法的 `activate/deactivate` 嵌套、去掉箭头 `+`/`-` 标记、`95` 的 2 处 `&lt;=` 改 `≤`；参与者与消息文本一字未改 | `9cf1bfbb` | mermaid-cli **10** 渲染通过；这 5 张图因此无激活盒，登记 **BE-021** 待裁决 | 仅图表源文件语法归一化，参与者与消息文本未变 |
| 4 | 2026-11-16 | 脚本 | 新增 `scripts/utils/render-mermaid-svg.sh`：无参补缺导出 / `-f` 全量 / `-f <file>` 单图；默认 `MMDC_VERSION=10`，自动探测 Chrome，`PUPPETEER_EXECUTABLE_PATH` 可覆盖 | `9cf1bfbb` | 三模式实测；`bash scripts/utils/render-mermaid-svg.sh` 幂等 | 仅新增脚本，不改运行代码 |
| 5 | 2026-11-16 | 治理 | 图表门槛同步：规范 §3.5 基线 217→**247**、`scripts/governance/verify-docs.sh` 门槛 247/68、`docs/diagrams/README.md` 新增「2026-11-16 批量补导出记录（BE-001）」含踩坑 | `9cf1bfbb` | `verify-docs.sh` 0 项 | 仅规范与门槛脚本，不改运行代码 |
| 6 | 2026-11-16 | 文档 | 技术债记录 `docs/records/2026-11-16-tech-debt-be001-be017.md`（结果表、工具链命令、踩坑、verify 实测输出） | `9cf1bfbb` | 文件存在且被索引引用 | 仅新增文档 |
| 7 | 2026-11-16 | 治理 | **§5.3 离线归档命令修正**：`tar --exclude=.git .` → `git archive --format=tar.gz --prefix=<repo>/ -o ... master` + `git bundle create` + `shasum -a 256`；写明「不要用 `tar .`」的原因 | `171a9668` | 实测 `tar .` 把根目录 1.4GB `target/` 打进包（产物 2.7GB）；改后后端 tar 32M / bundle 33M | 仅规范正文，不改代码 |
| 8 | 2026-11-16 | 远端 | **GitHub 远端接入**：地址 `anqli@outlook.com:...` 被 git 解析成登录 outlook.com 主机 → 改 `git@github.com:apex-cloud-service/<repo>.git`；本机 22 端口被拦 → `core.sshCommand "ssh -o Hostname=ssh.github.com -p 443"`；带口令私钥 → `ssh-add --apple-use-keychain ~/.ssh/edu2519` 后 `ssh -T -p 443 git@ssh.github.com` 回 `Hi anqli8341!` | `28cfd7cd` | 三障碍逐条实测；写入 `docs/records/2026-11-16-github-remote-onboarding.md` | 仅远端配置，不改本地代码 |
| 9 | 2026-11-16 | 远端 | 远端 `Initial commit`（`93f249e8`，仅 `LICENSE`+`README` 桩）与本地无共同祖先 → `git merge --allow-unrelated-histories`；`README.md` 保留本地 303 行、`LICENSE` 保留本地 MIT | `28cfd7cd` | 合并提交、文件行数与内容核对；**未使用 `push -f`** | 仅远端初始化提交（LICENSE/README），不改本地代码 |
| 10 | 2026-11-16 | 治理 | 规范 §5.3 同步远端现状（origin、443、口令私钥处理、远端已有历史合并口径、`push -f` 仍禁止） | `1a5c8525` | 规范 §5.3 全文；`verify-docs.sh` 0 项 | 仅规范正文，不改代码 |
| 11 | 2026-11-16 | 文档 | 补推送实测结果：`93f249e8..1a5c8525  main -> main`；并在记录中写明许可差异 | `c4f57a48` | `git ls-remote origin` 与本地 HEAD 一致 | 仅文档补记 |
| 12 | 2026-11-16 | 许可 | **许可证统一为 MIT**：后端保持既有 `LICENSE`(MIT)+`LICENSE-COMMERCIAL.md` 双轨不动（`COMMERCIAL_VERSIONS.md:238` 明确基础版 MIT）；同步修正 §5.3 主线口径与 `docs/05-operations/ci-cd-y2038-integration.md:147`（仅 `master` 分支）；记录新增 §5（分支重命名）与 §6（MIT vs Apache 2.0 对比与选型依据） | `ee244f89` | `head -2 LICENSE` → `MIT License`；商业文档未改；两仓许可已一致（前端另见其 04 篇） | 仅许可文件，不改代码 |
| 13 | 2026-11-16 | 分支 | `main` → `master`：`git branch -m main master` + `git push -u origin master`；远端旧 `main` 因是默认分支先被拒删，默认分支切到 `master` 后 `git push origin --delete main` 成功 + `git fetch --prune` | `f14c9b85` | `git ls-remote origin` 现仅 `dev/master/test`；本地 `main` 已不存在；历史零改写 | 仅分支改名与远端状态，不改代码 |
| 14 | 2026-11-16 | 分支 | **新建 `dev` / `test`**（自 `master`），推送并建立跟踪，三支同点 | `f14c9b85` | `git branch -vv` → `dev=origin/dev`、`test=origin/test`、`master=origin/master` | 仅新建并推送分支，不改代码 |
| 15 | 2026-11-16 | 治理 | **`dev` 设为默认工作分支**：规范 §5.3 分支模型表改为 `dev` 默认、`master` 仅接受从 `dev` 合入、`test` 仅由 `dev` 合入；写明开工三条自检与 `dev→master→test` 合入命令；本地切 `dev` | `1b5f0851` | `git checkout dev` + `git pull --ff-only`（Already up to date）；该规则更新本身即首个 `dev` 提交 | 仅规范正文，不改代码 |
| 17 | 2026-11-16 | 脚本 | 新增 `scripts/governance/check-memory-sync.sh`（校验暂存改动是否含台账、提交信息是否带 `memory:` 标记；默认只告警，`--strict` 才 exit 1），接入 `.githooks/pre-commit` 第 0 步与新增的 `.githooks/commit-msg`；`scripts/governance/README.md` 增 §6 说明 | 本条提交 | `bash scripts/governance/check-memory-sync.sh` 实测输出；钩子为警告模式，不影响任何功能与提交 | 仅新增校验脚本 |
| 16 | 2026-11-16 | 文档 | 本篇 `07-change-memory.md` 建立 + 规范 §5.4「记忆文档同步（强制）」+ `MEMORY-CARD.md`/`README.md` 索引同步 | 本条提交 | 本文件；`grep -n "5.4 记忆文档同步" docs/specs/project-governance.md` | 仅新增台账文档 |
| 18 | 2026-11-16 | 脚本 | **更正 #17**：`check-memory-sync.sh` 首版在 macOS bash 3.2 下报 `MEM_NAME: unbound variable` —— 变量紧跟中文全角括号 `（` 时被并入变量名；改为 `${MEM_NAME}` 后复测通过。**结论：本仓脚本里任何变量后接非 ASCII 字符，一律写 `${VAR}`** | 本条提交 | 修复后实测两种路径均正常：有改动未登记 → 告警；已登记 → `OK：本次提交已登记记忆台账（07-change-memory.md）` | 仅脚本更正 |
| 19 | 2026-11-16 | 分支 | **`master` / `test` 快进到 `dev`**：`git checkout master && git merge --ff-only dev && git push`、`test` 同款、随后回 `dev`；三支最终同指一个提交，内容零差异 | 本条提交 | `git rev-list --count master..dev` → 0；`git rev-parse dev master test` 三者相同；推送为 `--ff-only` 快进（非强推、未 `reset --hard`） | 仅分支快进，内容零差异 |
| 20 | 2026-11-16 | 治理 | **安装项目专属 Skill 3 个**（`apex-scaffold-backend`、`apex-admin-web-frontend`、`apex-governance-ops`，落在 `/Users/eric/DoubaoWork/skills/`，用户级不入库）+ **Agent 定义新增 2 个**（`.workbuddy/agents/doc-governance-auditor.md`、`release-guardian.md`）；新增受控文档 `08-agent-tooling.md` 登记清单与重建要点；插件市场 `find_plugin` 无匹配，改用仓库内脚本 | 本条提交 | `ls /Users/eric/DoubaoWork/skills/*/SKILL.md` 三份 frontmatter 完整；`.workbuddy/agents/` 6 个定义；`find_plugin` 返回空。不影响构建与接口 | 仅用户级 Skill 安装（不入库），不改项目文件 |
| 21 | 2026-11-16 | 脚本 | **新增一键预检 `scripts/dev/apex-preflight.sh`**（两仓同款）：4 种模式（默认全量 / `--fast` / `--commit` / `--strict`），10 类检查（仓类型、分支=dev、工作区、远端对齐、附录 A 哈希、origin 与 SSH 443、记忆台账、治理脚本及其自检、图表基线、JDK+mvnd / node+build、三支一致性）；三个项目 Skill 同步加入入口 | 本条提交 | 后端实测 `OK=19 WARN=1 FAIL=0`、前端 `OK=16 WARN=1 FAIL=0`（WARN 均为「有未跟踪新文件」）。脚本只读，不改任何文件 | 仅新增预检脚本 |
| 22 | 2026-11-16 | 治理 | **分支保护清单** `docs/05-operations/github-branch-protection.md`：`master`/`test` 目标规则（Require PR + 线性历史 + 禁强推 + 禁删除）、`dev` 只禁强推与删除、网页步骤、`gh api` 命令、开启后改走 PR + Rebase and merge、8 条验收清单；规范 §5.3 增指引（前端副本在 `docs/guides/`） | 本条提交 | 现状实测：匿名 `GET .../branches/master/protection` → **403**（无 token，无法核实/开启），需网页或 PAT | 仅新增文档 |
| 23 | 2026-11-16 | 治理 | **`dev` 分支治理一致性评审 + 整改方案**：跨仓评审报告（判定口径、12 项达标证据、10 条偏差 D-01~D-10、允许差异、未验证项、功能无关性核对）与整改方案（B1–B6 批次、逐文件行号、命令、验收、回滚、3 个决策点）落盘 `docs/reviews/`、`docs/plans/` | 本条提交 | 关键实测：CI 4 个 workflow 仍绑 `main/develop`（D-01/P0）；SpringDoc Handler 98%（3 处 `@Operation` + 2 Router 缺 `@RouterOperations`）；两仓 `AGENTS.md` 第 0 章无台账/分支条款；前端无任何 CI；保护规则匿名 API 403 不可核实。**方案未执行，待确认** | 仅新增评审与方案文档 |
| 24 | 2026-11-16 | 治理 | **整改 B1+B2（关闭 D-03/D-08/D-09/D-10/D-04）**：`AGENTS.md` 第 0 章新增 `0.8 记忆台账、分支模型与工具链`；规范 §5.3 默认分支改为既成事实；`ai-knowledge/README.md` 维护口径改为「台账强制登记 + 记忆卡为派生视图」；`MEMORY-CARD.md` 层级契约降级为可选；任务盘补 BE-022~BE-029 并把 BE-021 定为「决策①接受现状」 | 本条提交 | 验收：`tail -n 306 AGENTS.md \| shasum -a 256` = `aa0c25fc…60ed` **不变**（附录 A 零改动）；任务盘 6 列表格对齐（`awk` 校验 NF=8） | 仅 AGENTS.md/规范/知识库/任务盘；附录 A 哈希不变，不改代码 |
| 25 | 2026-11-16 | 脚本 | **整改 B5（关闭 D-06）**：`.gitignore` 合并重复忽略项为单一「AI 私有目录」区块（`.workbuddy/.trae/.zcode/.qoder/.codex/.claude/.agents/.ai/.freebuff` 各 1 条并带用途注释），移除重复的 `dist/`、`dist/` 二次声明与工具链段重复目录 | 本条提交 | 验收：`grep -vE '^# \| ^$' .gitignore \| sort \| uniq -d` 为空；`git check-ignore docs/README.md` 无命中；`verify-docs` 仍 0 项 | 仅 `.gitignore` 规则合并，不影响已跟踪文件 |
| 26 | 2026-11-16 | 治理 | **整改 B3（关闭 D-01）**：5 个 workflow 触发分支由 `main/develop` 改为 `master/test/dev`（PR 为 `master/test`），全部加 `workflow_dispatch`；`ci-cd.yml` 镜像构建条件改 `refs/heads/master`、注释同步 | 本条提交 | 验收：`grep -rn 'refs/heads/main\|branches: \[ main\|- main' .github/workflows/` 为空；`ruby -ryaml` 解析 6 个 yml 全部通过；推 `dev` 将实际触发 CI（本轮推送即验证） | 仅 CI 触发分支，不改业务代码 |
| 27 | 2026-11-16 | 脚本 | **B5 补正**：`.gitignore` 行尾注释不被 git 识别（`.workbuddy/  # 注释` 会被当作含 `#` 的规则，导致私有目录未被忽略、`git status` 出现 `?? .workbuddy/`），改为「注释独占一行 + 规则单列」 | 本条提交 | 验收：`git check-ignore -v .workbuddy` 命中第 125 行；`git status` 不再出现私有目录未跟踪项 | 仅 `.gitignore` 注释位置修正，规则语义不变 |
| 28 | 2026-11-16 | 功能 | **整改 B4（关闭 D-05）**：`OpenUserRouter`/`OpenAppMigrationRouter` 补 `@RouterOperations`+`@RouterOperation`（含 parameters/requestBody 数组 schema/responses）；`OpenUserHandler`/`OpenAppMigrationHandler` 补 `@Tag`+`@Operation`+`@ApiResponses`/`@RequestBody` | 本条提交 | 验收：`check-springdoc-coverage.sh` **Handler 100% / DTO 100%**（原 98%）；`audit-backend-rules` 无阻断；`mvnd -pl :apex-system -am -DskipTests verify` **BUILD SUCCESS**（含 spotless/checkstyle/PMD）；仅注解，方法体与路由未改 | 仅补 SpringDoc/OpenAPI 注解，请求/响应契约与运行行为不变，接口文档更完整 |
| 29 | 2026-11-16 | 治理 | 任务盘登记整改批次 B3/B4/B5 完成与 B6 待执行（BE-030~BE-033），B6 阻塞原因写入验收列 | 本条提交 | 与代码/文档提交一一对应，状态列与实际一致 | 仅任务盘状态登记 |
| 30 | 2026-11-16 | 治理 | 评审报告与整改方案回填执行结果（D-01~D-06、D-08~D-10 关闭；D-07 待凭据）；方案状态改「已执行」 | 本条提交 | 与任务盘 BE-030~BE-033 一致；收尾自检 `FAIL=0` | 仅评审/方案回填文档 |
| 31 | 2026-11-16 | 治理 | **B6 落地**：完成 `gh` 设备授权（`anqli8341`/`repo`）后实测六条 `PUT /branches/{master,test,dev}/protection` 全部 **403**（private + Free 计划不支持服务端保护）；改为本地补偿 `.githooks/pre-push`（拒绝强推/删除三支，快进与普通分支放行）；保护正文新增 §0.1 平台限制、两条解锁路径与能力边界 | 本条提交 | 钩子 4 用例实测：非快进=1（拒）、删除=1（拒）、快进=0、普通分支=0；`gh auth status` 已登录；两仓钩子同款已 `chmod +x` | 仅 `.githooks`/保护文档/任务盘/台账，不改代码 |
| 32 | 2026-11-16 | 治理 | **B6 闭环**：两仓转 public 后同一批 `PUT /branches/{master,test,dev}/protection` 六条全部成功（档 1：禁强推+禁删除，master/test 加对话解决）；绕本地钩子实测服务端 `protected branch hook declined`（删支/强推均拒，`test` 仍 11302e84）；§0.1 由「平台限制」改写为「处理经过 + 最终结论」，§5 验收 8 条逐项回填 | 本条提交 | 六支 `GET /protection` 均 200 且 `force=false del=false`；`dev` 普通推送成功（钩子与服务端双防线） | 仅保护文档/任务盘/台账，不改代码 |
| 33 | 2026-11-16 | 治理 | **对齐修复 G1–G5**：台账补 `日期` 列（32 条回填，`摘要`/`证据` 原文未改）、任务盘「本期已完成」表 3 行错列修正（多插的「负责」列并入验收/产出）、`BE-033` 状态去加粗、`.gitignore` 补 `.trae-html-share-packages/`（内含他人报告副本，正文不入库） | 本条提交 | 表格列一致性校验：`project-tasks.md`/`07-change-memory.md` 异常 0；`verify-docs.sh` 0 项；`check-doc-refs.sh` 0 项；`git check-ignore` 命中 | 仅文档与忽略规则，不改代码 |
| 34 | 2026-11-16 | 治理 | **更正 #33**：新增条目误把「证据」与「影响面」写在同一格（6 格），已拆为 7 格，与表头一致 | 本条提交 | 校验：`07-change-memory.md` 条目列数异常 0 | 仅文档，不改代码 |
| 35 | 2026-11-16 | 脚本 | **BE-006**：`scripts/` 下 19 个缺说明的子目录各生成 README（标题+一句用途+`脚本|用途|用法`表+约定：路径解析/退出码/新增脚本须先立卡），合计 22/22 齐备；抽描述时跳过 shebang 与 `=`/`*`/`Copyright`/`SPDX` 行，避免把 `#!/usr/bin/env bash` 当成描述 | 本条提交 | `for d in scripts/*/; do test -f $d/README.md || echo $d; done` 输出为空；`scripts/utils/README.md` 为唯一手填描述 | 仅新增脚本目录说明，不改脚本逻辑 |
| 36 | 2026-11-16 | 治理 | **BE-002/004/005/007 收口**：①新增 `scripts/governance/report-doc-orphans.sh` 与孤岛清单 `docs/notes/2026-11-16-doc-orphans.md`（md 772 / 索引引用 374 / 孤岛 527）；②周期报告按「只留当期」归档 19 件到 `_archive/2026-10/`，三份 README 索引改指归档并写入归档策略；③`docs/reviews/2026-11-16-exception-handler-evaluation.md` 结论维持现状（`@RestControllerAdvice` 在 WebFlux 正常，20 分支迁移会改错误契约，未改代码）；④移除 Trae 私有目录（`.trae`）下 5 个指针桩；5 张卡状态改为已完成并回填产出 | 本条提交 | `check-doc-refs.sh` 0 项、`verify-docs.sh` 0 项、`apex-preflight.sh --fast` `FAIL=0`；`ls .trae` 的 `documents/` 为空 | 仅文档、脚本与归档，不改业务代码 |
| 37 | 2026-11-16 | 文档 | **技术债卡复核与口径更正**：复核 `BE-001`（68/68 `.mmd` ↔ `.svg` 配对、缺失 0）与 `BE-017`（主代码手写 Logger 0 处、`@Slf4j` 482 文件、残余仅在测试用 logback `ListAppender` 做断言），两卡均**早已闭合**，上一轮「本轮未动」表述更正；同时记录 mvnd 守护进程故障（`aktStatus is NULL`）与 `mvn` 回退路径 | 本条提交 | `mvn -pl :apex-common -am test -DskipITs` → BUILD SUCCESS（26.272s，jacoco 覆盖率检查通过）；`bash scripts/governance/verify-docs.sh` 0 项 | 仅新增 `docs/records/2026-11-16-tech-debt-reverify.md` 与索引，不改代码 |
| 38 | 2026-11-16 | 文档 | **目录规范化（BE-034）**：`docs/README.md` 新增「🗂 目录总览」覆盖 **25 个一级目录**（内容/性质/是否收新/入口）+ 三条归置规则；补齐 7 个缺失 README（`_archive`、`api`、`architecture`、`archived`、`fix-reports`、`migration`、`reference`）；新增只读自检 `scripts/governance/check-doc-layout.sh`（4 条断言）并接入 `verify-docs.sh` 第 9 项；规范新增 §3.7；方案 `docs/plans/2026-11-16-directory-normalization-plan.md` | 本条提交 | `check-doc-layout.sh` 4/4 OK、`verify-docs.sh` 9 项 0 问题、`check-doc-refs.sh` 0 项 | 无代码/配置改动；历史专题目录按 §3.2 不搬迁，仅以「权威落点映射」消除口径冲突 |
| 39 | 2026-11-16 | 结构 | **目录规范化补丁**：`docs/archived/README.md` 的 `ai-sessions/` 链接指向不存在的 README（该目录仅 `zcode-plans/`），改为目录链接并注明「目录内无 README」，`check-doc-refs.sh` 回 0 断链 | 本条提交 | `verify-docs.sh` 9 项 0 问题、`check-doc-layout.sh` 4/4 | 纯文档修正，无代码改动 |
| 40 | 2026-11-16 | 脚本 | **BE-035**：`common.sh` 新增只读守卫 `apex_check_local_repository`（Python 解析仓库配置全优先级：5 个 JVM/Maven 环境变量、`.mvn` 的 maven.config/jvm.config/maven.properties/mvnd.properties、`MVND_PROPERTIES_PATH`、安装级/用户级/项目级 settings.xml 与 `-s/-gs/-ps`，含 `${}`/`env.` 展开、properties `${includes}` 与 symlink 规范化；不调用 Maven、不打印凭据），接入 `build.sh`/`release.sh`/`apex.sh`（`mvnd_offline_first`、`cmd_test`、`cmd_verify`）在最终 mvnd 参数定型前拒绝项目内仓库且不进入离线重试；`apex-preflight.sh` 新增只读报告项（仓库位置 + 根 target 副本）；`BuildPathContractTest` 新增 6 个守卫用例。**查明根 target 副本来源**：Maven 4 reactor 每次构建自动 `Copying ... to project local repository`（非 maven.repo.local 配置），125 文件 1.4G 可再生；定向清理因 Finder 回收站服务繁忙（AppleScript -15260）三次受阻暂缓 | 未提交（工作区待业主提交） | `mvnd -T4 -B -pl :apex-tools-build -am -Dtest=BuildPathContractTest test` BUILD SUCCESS（9/9）；守卫冒烟：默认仓库放行 `~/.m2/repository`、`MAVEN_ARGS=-Dmaven.repo.local=target/bad-repo` 被拒 exit=1；preflight `OK=14 WARN=4 FAIL=0`；`verify-docs.sh` 0 项；`git diff --check` 通过 | 仅构建脚本守卫/预检报告/回归测试，不改业务代码与 POM、不提交、不清空 `~/.m2/repository` |
| 41 | 2026-11-16 | 脚本 | **BE-035 收口（承接 #40 暂缓项）**：定位 -15260 根因为 Finder 卡住的「倾倒废纸篓」模态确认弹窗（经 System Events 读取 AXModal 弹窗证实），业主确认后清空废纸篓解除阻塞；定向清理完成——`target/project-local-repo` 125 个 reactor 副本（88 POM/37 JAR，1.4G）+ 清理过程中 Finder 新写入的 44 个 `.DS_Store`（352K）全部经回收站删除，空目录一并移除 | 本条提交 | 清理后 `test -d target/project-local-repo` 为否；preflight `OK=15 WARN=3 FAIL=0`（本地仓库位于仓库外、无副本两项均 OK）；`git diff --check` 通过 | 仅清理可再生构建缓存与记录更新，不改业务代码/POM、不提交 |
| 42 | 2026-11-16 | 脚本 | **BE-035 提交与三支同步**：将 #40/#41 的成果提交到 `dev`——`common.sh` 只读守卫 `apex_check_local_repository`、`build.sh`/`release.sh`/`apex.sh` 三处接入、`apex-preflight.sh` 两项只读报告、`BuildPathContractTest` 6 个守卫用例；随后以 `--ff-only` 快进同步 `master`/`test`。工作区他人 3 项（日报 2 件、`01-project-map.md`）改动未纳入，原样保留 | 本条提交 | `mvnd -pl :apex-tools-build -am test` 9/9；整仓 `mvnd -T4 -B clean verify` 45/45（`apex-boot-all` 因 8080 被常驻服务占用曾失败，停服务后单跑 15/15 通过）；`verify-docs.sh` 0 项；`git diff --check` 通过 | 仅构建脚本守卫/预检报告/回归测试与记录，不改业务代码与 POM |
| 43 | 2026-11-16 | 文档 | **日报与项目地图口径更新（业主授权全量提交）**：将工作区剩余的 3 项文档改动一并提交并同步 `master`/`test`——`2026-11-16-all-projects-daily-report.md` 主题由「认证加固与知识库清理」改写为「治理规范落地与仓库规范化」；`daily/README.md` 索引描述同步；`01-project-map.md` 采集日期 11-14→11-16、`docs/` Markdown 计数 656→726。此前按「他人未提交改动不纳入」原则保留，本轮经业主明确指示「提交所有代码和文件」后纳入 | 本条提交 | `verify-docs.sh` 0 项；`git diff --check` 通过；三支 hash 一致 | 仅文档口径更新，不含代码/POM 改动 |

| 44 | 2026-11-17 | 代码 | **BE-036 联调后端修复（含独立复核）**：①`ApprovalProcessRepositoryImpl` 新增 `withTenantScoped` 统一入口——租户从 Reactor Context 解析（`currentTenantIdReactive`），在 JDBC callable 内 `TenantContext.callWithTenant` 安装并在成功/异常后恢复；显式入参租户须与可信租户一致，`findById` 增加租户限定，`save` 校验实体租户，无租户一律拒绝。②`MenuService#buildScopedTree` 修正空 `menuIds` 语义为「该维度全量」（原实现按空清单裁剪导致菜单为空），受限菜单父链补全与 denied 语义不变。③`Office/Integration/Workflow/Knowledge` 四个 `PermissionClient` 的 `Snapshot` 加 `@JsonIgnoreProperties(ignoreUnknown = true)`，容忍认证中心新增 `roles` 等字段（严格反序列化曾把授权查询整体判为拒绝）；tenant/user/permission 校验不变 | 未提交 | 复核代理报告：`apex-office` 187/0/0/0、`office-service` 405/0/0/0、`workflow-service` 85/0/0/0、`integration-service` 40/0/0/0、`knowledge-service` 72/0/0/0；新增审批租户传播/恢复/错配、菜单 null-empty/父链、快照 roles/未知字段解码用例；spotless 对本次文件 BUILD SUCCESS；system 套件最后一个夹具问题已修但**最终全量重跑未完成** | 仅内部租户上下文与授权快照解码、菜单范围语义；HTTP 路径/响应契约未变；未部署、未提交 |

| 45 | 2026-11-17 | 代码 | **BE-036 第二轮：审批响应字段透传修复**：真实联调插入合法数据后发现 `GET /api/office/approvals/pending` 的 `processName`/`currentNode`/`createdAt` 恒为 null（库中 `process_name`/`current_node`/`created_at` 有值）。根因：领域实体 `ApprovalProcess` 用 `title`/`initiateTime`/`createTime` 且缺 `currentNode`/`processDefinitionId`/`completedAt`/`updatedAt`，PO 与响应 DTO 用平台侧命名，`unmappedTargetPolicy=IGNORE` 使 MapStruct 静默留空。修复：实体补齐 4 个透传字段；`ApprovalProcessConverter` 与 `ApprovalProcessAssembler` 增加 `title↔processName`、`createTime↔createdAt` 显式映射；新增装配器回归用例 | 未提交 | `:apex-office` 189 用例、`:apex-office-service` 406 用例 0 失败 BUILD SUCCESS；`spotless:check` 两模块 BUILD SUCCESS；真实环境重启后 `pending` 返回 `processName=读路径校验单-1`、`currentNode=主管审批`、`createdAt=2026-11-17T16:56:30`（修复前均为 null） | 仅实体字段与映射；HTTP 契约不变，前端无需改动 |

| 46 | 2026-11-17 | 文档 | **11-17 快照与日报收口**：`MEMORY-CARD.md`、`01-project-map.md`、`02-apex-scaffold-current-state.md`、`06-delivery-status-and-next.md` 采集口径更新到 2026-11-17；新增 `08-project-management/periodic-reports/daily/2026-11-17-all-projects-daily-report.md` 并在 daily README 登记，其中风险段落已按实际提交状态更正（两仓已提交并同步三支，日历写操作仍缺 `*CalendarClient` 实现） | 未提交 | `verify-docs.sh` 通过；日报风险描述与实际 git 状态一致（后端 `102fa274`） | 仅文档快照与日报，不改代码/契约 |

| 47 | 2026-11-17 | 台账 | **BE-036 收口**：真实环境验证租户隔离（租户1 仅见自身记录、跨租户 processInstanceId 查询返回「审批流程不存在」）；环境完全重建后经网关 18 条真实接口 18/18 通过；任务卡 BE-036 置「已完成」 | 未提交 | `:apex-office` 189、`:apex-office-service` 406 用例 0 失败 BUILD SUCCESS；18/18 实测；spotless 通过 | 仅记录与任务状态；日历客户端缺口（无 `*CalendarClient` 实现）已列入前端记录 §7.2 需另立任务 |



> **表列与 §1「必填字段」一一对应**：`日期` · `类型` · `摘要` · `提交` · `证据` · `影响面`（`#` 仅序号，不计入字段）。2026-11-16 分列重排：`摘要`/`证据` 原文一字未改，`影响面` 按当次改动范围回填。

---

## 4. 按类型检索

| 想找什么 | 看哪几行 |
| --- | --- |
| 构建/脚本改动 | #4、#7（归档命令）、#5（门槛） |
| 代码级改动 | #1（`@Slf4j`）、#3（图源归一化） |
| 图表基线变化 | #2、#3、#5 |
| 远端与凭据 | #8、#9、#11、#13 |
| 许可与合规 | #12 |
| 分支策略 | #13、#14、#15 |

---

## 5. 与其它文档的分工

| 文档 | 回答什么 | 更新频率 |
| --- | --- | --- |
| 本篇 `07-change-memory.md` | 时间轴上发生了什么、证据是什么 | **每次改动** |
| `MEMORY-CARD.md` | 三分钟上手要点 + 指针 | 结构性变化时 |
| `01`–`06` | 现状/地图/陷阱/交付状态快照 | 结构性变化时 |
| `docs/records/*.md` | 单个事件的完整复盘（含踩坑与决策） | 事件级 |
| `docs/05-operations/*`、`docs/specs/*` | 规范原文（口径唯一权威） | 口径变化时 |

> 冲突时以源码与规范原文为准，本篇只作事实记录；发现本篇与源码冲突，**追加更正条目**并把正确结论回写到快照篇。

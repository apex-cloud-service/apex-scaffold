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

| # | 类型 | 摘要 | 提交 | 证据 / 影响面 |
| --- | --- | --- | --- | --- |
| 1 | 技术债 | **BE-017** `VirtualThreadBoundedExecutor` 删手写 `LoggerFactory` 字段，改 `@Slf4j`（变量名仍为 `log`，日志格式与字段不变） | `9cf1bfbb` | `mvnd -pl :apex-common -am -B verify` BUILD SUCCESS（单测/覆盖率/failsafe 全绿）；`audit-backend-rules.sh` 阻断 1→0。不改线程池行为 |
| 2 | 图表 | **BE-001** 30 个 `.mmd` 补齐同名 `.svg`（`.svg` 217→247，`.mmd` 零删除，68/68 配对） | `9cf1bfbb` | `find docs -name '*.svg' \| wc -l` → 247；源文件未删改（除第 3 条归一化） |
| 3 | 图表 | 6 个历史源文件（`78/84/90/93/94/95`）做**语法归一化**：删除非法的 `activate/deactivate` 嵌套、去掉箭头 `+`/`-` 标记、`95` 的 2 处 `&lt;=` 改 `≤`；参与者与消息文本一字未改 | `9cf1bfbb` | mermaid-cli **10** 渲染通过；这 5 张图因此无激活盒，登记 **BE-021** 待裁决 |
| 4 | 脚本 | 新增 `scripts/utils/render-mermaid-svg.sh`：无参补缺导出 / `-f` 全量 / `-f <file>` 单图；默认 `MMDC_VERSION=10`，自动探测 Chrome，`PUPPETEER_EXECUTABLE_PATH` 可覆盖 | `9cf1bfbb` | 三模式实测；`bash scripts/utils/render-mermaid-svg.sh` 幂等 |
| 5 | 治理 | 图表门槛同步：规范 §3.5 基线 217→**247**、`scripts/governance/verify-docs.sh` 门槛 247/68、`docs/diagrams/README.md` 新增「2026-11-16 批量补导出记录（BE-001）」含踩坑 | `9cf1bfbb` | `verify-docs.sh` 0 项 |
| 6 | 文档 | 技术债记录 `docs/records/2026-11-16-tech-debt-be001-be017.md`（结果表、工具链命令、踩坑、verify 实测输出） | `9cf1bfbb` | 文件存在且被索引引用 |
| 7 | 治理 | **§5.3 离线归档命令修正**：`tar --exclude=.git .` → `git archive --format=tar.gz --prefix=<repo>/ -o ... master` + `git bundle create` + `shasum -a 256`；写明「不要用 `tar .`」的原因 | `171a9668` | 实测 `tar .` 把根目录 1.4GB `target/` 打进包（产物 2.7GB）；改后后端 tar 32M / bundle 33M |
| 8 | 远端 | **GitHub 远端接入**：地址 `anqli@outlook.com:...` 被 git 解析成登录 outlook.com 主机 → 改 `git@github.com:apex-cloud-service/<repo>.git`；本机 22 端口被拦 → `core.sshCommand "ssh -o Hostname=ssh.github.com -p 443"`；带口令私钥 → `ssh-add --apple-use-keychain ~/.ssh/edu2519` 后 `ssh -T -p 443 git@ssh.github.com` 回 `Hi anqli8341!` | `28cfd7cd` | 三障碍逐条实测；写入 `docs/records/2026-11-16-github-remote-onboarding.md` |
| 9 | 远端 | 远端 `Initial commit`（`93f249e8`，仅 `LICENSE`+`README` 桩）与本地无共同祖先 → `git merge --allow-unrelated-histories`；`README.md` 保留本地 303 行、`LICENSE` 保留本地 MIT | `28cfd7cd` | 合并提交、文件行数与内容核对；**未使用 `push -f`** |
| 10 | 治理 | 规范 §5.3 同步远端现状（origin、443、口令私钥处理、远端已有历史合并口径、`push -f` 仍禁止） | `1a5c8525` | 规范 §5.3 全文；`verify-docs.sh` 0 项 |
| 11 | 文档 | 补推送实测结果：`93f249e8..1a5c8525  main -> main`；并在记录中写明许可差异 | `c4f57a48` | `git ls-remote origin` 与本地 HEAD 一致 |
| 12 | 许可 | **许可证统一为 MIT**：后端保持既有 `LICENSE`(MIT)+`LICENSE-COMMERCIAL.md` 双轨不动（`COMMERCIAL_VERSIONS.md:238` 明确基础版 MIT）；同步修正 §5.3 主线口径与 `docs/05-operations/ci-cd-y2038-integration.md:147`（仅 `master` 分支）；记录新增 §5（分支重命名）与 §6（MIT vs Apache 2.0 对比与选型依据） | `ee244f89` | `head -2 LICENSE` → `MIT License`；商业文档未改；两仓许可已一致（前端另见其 04 篇） |
| 13 | 分支 | `main` → `master`：`git branch -m main master` + `git push -u origin master`；远端旧 `main` 因是默认分支先被拒删，默认分支切到 `master` 后 `git push origin --delete main` 成功 + `git fetch --prune` | `f14c9b85` | `git ls-remote origin` 现仅 `dev/master/test`；本地 `main` 已不存在；历史零改写 |
| 14 | 分支 | **新建 `dev` / `test`**（自 `master`），推送并建立跟踪，三支同点 | `f14c9b85` | `git branch -vv` → `dev=origin/dev`、`test=origin/test`、`master=origin/master` |
| 15 | 治理 | **`dev` 设为默认工作分支**：规范 §5.3 分支模型表改为 `dev` 默认、`master` 仅接受从 `dev` 合入、`test` 仅由 `dev` 合入；写明开工三条自检与 `dev→master→test` 合入命令；本地切 `dev` | `1b5f0851` | `git checkout dev` + `git pull --ff-only`（Already up to date）；该规则更新本身即首个 `dev` 提交 |
| 17 | 脚本 | 新增 `scripts/governance/check-memory-sync.sh`（校验暂存改动是否含台账、提交信息是否带 `memory:` 标记；默认只告警，`--strict` 才 exit 1），接入 `.githooks/pre-commit` 第 0 步与新增的 `.githooks/commit-msg`；`scripts/governance/README.md` 增 §6 说明 | 本条提交 | `bash scripts/governance/check-memory-sync.sh` 实测输出；钩子为警告模式，不影响任何功能与提交 |
| 16 | 文档 | 本篇 `07-change-memory.md` 建立 + 规范 §5.4「记忆文档同步（强制）」+ `MEMORY-CARD.md`/`README.md` 索引同步 | 本条提交 | 本文件；`grep -n "5.4 记忆文档同步" docs/specs/project-governance.md` |
| 18 | 脚本 | **更正 #17**：`check-memory-sync.sh` 首版在 macOS bash 3.2 下报 `MEM_NAME: unbound variable` —— 变量紧跟中文全角括号 `（` 时被并入变量名；改为 `${MEM_NAME}` 后复测通过。**结论：本仓脚本里任何变量后接非 ASCII 字符，一律写 `${VAR}`** | 本条提交 | 修复后实测两种路径均正常：有改动未登记 → 告警；已登记 → `OK：本次提交已登记记忆台账（07-change-memory.md）` |
| 19 | 分支 | **`master` / `test` 快进到 `dev`**：`git checkout master && git merge --ff-only dev && git push`、`test` 同款、随后回 `dev`；三支最终同指一个提交，内容零差异 | 本条提交 | `git rev-list --count master..dev` → 0；`git rev-parse dev master test` 三者相同；推送为 `--ff-only` 快进（非强推、未 `reset --hard`） |
| 20 | 治理 | **安装项目专属 Skill 3 个**（`apex-scaffold-backend`、`apex-admin-web-frontend`、`apex-governance-ops`，落在 `/Users/eric/DoubaoWork/skills/`，用户级不入库）+ **Agent 定义新增 2 个**（`.workbuddy/agents/doc-governance-auditor.md`、`release-guardian.md`）；新增受控文档 `08-agent-tooling.md` 登记清单与重建要点；插件市场 `find_plugin` 无匹配，改用仓库内脚本 | 本条提交 | `ls /Users/eric/DoubaoWork/skills/*/SKILL.md` 三份 frontmatter 完整；`.workbuddy/agents/` 6 个定义；`find_plugin` 返回空。不影响构建与接口 |
| 21 | 脚本 | **新增一键预检 `scripts/dev/apex-preflight.sh`**（两仓同款）：4 种模式（默认全量 / `--fast` / `--commit` / `--strict`），10 类检查（仓类型、分支=dev、工作区、远端对齐、附录 A 哈希、origin 与 SSH 443、记忆台账、治理脚本及其自检、图表基线、JDK+mvnd / node+build、三支一致性）；三个项目 Skill 同步加入入口 | 本条提交 | 后端实测 `OK=19 WARN=1 FAIL=0`、前端 `OK=16 WARN=1 FAIL=0`（WARN 均为「有未跟踪新文件」）。脚本只读，不改任何文件 |
| 22 | 治理 | **分支保护清单** `docs/05-operations/github-branch-protection.md`：`master`/`test` 目标规则（Require PR + 线性历史 + 禁强推 + 禁删除）、`dev` 只禁强推与删除、网页步骤、`gh api` 命令、开启后改走 PR + Rebase and merge、8 条验收清单；规范 §5.3 增指引（前端副本在 `docs/guides/`） | 本条提交 | 现状实测：匿名 `GET .../branches/master/protection` → **403**（无 token，无法核实/开启），需网页或 PAT |
| 23 | 治理 | **`dev` 分支治理一致性评审 + 整改方案**：跨仓评审报告（判定口径、12 项达标证据、10 条偏差 D-01~D-10、允许差异、未验证项、功能无关性核对）与整改方案（B1–B6 批次、逐文件行号、命令、验收、回滚、3 个决策点）落盘 `docs/reviews/`、`docs/plans/` | 本条提交 | 关键实测：CI 4 个 workflow 仍绑 `main/develop`（D-01/P0）；SpringDoc Handler 98%（3 处 `@Operation` + 2 Router 缺 `@RouterOperations`）；两仓 `AGENTS.md` 第 0 章无台账/分支条款；前端无任何 CI；保护规则匿名 API 403 不可核实。**方案未执行，待确认** |
| 24 | 治理 | **整改 B1+B2（关闭 D-03/D-08/D-09/D-10/D-04）**：`AGENTS.md` 第 0 章新增 `0.8 记忆台账、分支模型与工具链`；规范 §5.3 默认分支改为既成事实；`ai-knowledge/README.md` 维护口径改为「台账强制登记 + 记忆卡为派生视图」；`MEMORY-CARD.md` 层级契约降级为可选；任务盘补 BE-022~BE-029 并把 BE-021 定为「决策①接受现状」 | 本条提交 | 验收：`tail -n 306 AGENTS.md \| shasum -a 256` = `aa0c25fc…60ed` **不变**（附录 A 零改动）；任务盘 6 列表格对齐（`awk` 校验 NF=8） |
| 25 | 脚本 | **整改 B5（关闭 D-06）**：`.gitignore` 合并重复忽略项为单一「AI 私有目录」区块（`.workbuddy/.trae/.zcode/.qoder/.codex/.claude/.agents/.ai/.freebuff` 各 1 条并带用途注释），移除重复的 `dist/`、`dist/` 二次声明与工具链段重复目录 | 本条提交 | 验收：`grep -vE '^#|^$' .gitignore \| sort \| uniq -d` 为空；`git check-ignore docs/README.md` 无命中；`verify-docs` 仍 0 项 |
| 26 | 治理 | **整改 B3（关闭 D-01）**：5 个 workflow 触发分支由 `main/develop` 改为 `master/test/dev`（PR 为 `master/test`），全部加 `workflow_dispatch`；`ci-cd.yml` 镜像构建条件改 `refs/heads/master`、注释同步 | 本条提交 | 验收：`grep -rn 'refs/heads/main\|branches: \[ main\|- main' .github/workflows/` 为空；`ruby -ryaml` 解析 6 个 yml 全部通过；推 `dev` 将实际触发 CI（本轮推送即验证） |
| 27 | 脚本 | **B5 补正**：`.gitignore` 行尾注释不被 git 识别（`.workbuddy/  # 注释` 会被当作含 `#` 的规则，导致私有目录未被忽略、`git status` 出现 `?? .workbuddy/`），改为「注释独占一行 + 规则单列」 | 本条提交 | 验收：`git check-ignore -v .workbuddy` 命中第 125 行；`git status` 不再出现私有目录未跟踪项 |
| 28 | 功能 | **整改 B4（关闭 D-05）**：`OpenUserRouter`/`OpenAppMigrationRouter` 补 `@RouterOperations`+`@RouterOperation`（含 parameters/requestBody 数组 schema/responses）；`OpenUserHandler`/`OpenAppMigrationHandler` 补 `@Tag`+`@Operation`+`@ApiResponses`/`@RequestBody` | 本条提交 | 验收：`check-springdoc-coverage.sh` **Handler 100% / DTO 100%**（原 98%）；`audit-backend-rules` 无阻断；`mvnd -pl :apex-system -am -DskipTests verify` **BUILD SUCCESS**（含 spotless/checkstyle/PMD）；仅注解，方法体与路由未改 |
| 29 | 治理 | 任务盘登记整改批次 B3/B4/B5 完成与 B6 待执行（BE-030~BE-033），B6 阻塞原因写入验收列 | 本条提交 | 与代码/文档提交一一对应，状态列与实际一致 |
| 30 | 治理 | 评审报告与整改方案回填执行结果（D-01~D-06、D-08~D-10 关闭；D-07 待凭据）；方案状态改「已执行」 | 本条提交 | 与任务盘 BE-030~BE-033 一致；收尾自检 `FAIL=0` |

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

# 治理脚本说明（apex-scaffold）

本目录脚本是「项目任务管理规范」的执行工具，**只读扫描、不改代码、默认只警告**。
规范正文见 `docs/specs/project-governance.md`。

## 1. verify-docs.sh — 文档治理自检

```bash
bash scripts/governance/verify-docs.sh            # 警告模式，永远 exit 0
bash scripts/governance/verify-docs.sh --strict   # 有问题 exit 1（可接 CI）
```

检查项：

| # | 检查 | 判据 |
| --- | --- | --- |
| 1 | 六标准目录与索引 | `docs/{tasks,specs,plans,records,reviews,notes}` 及各自 `README.md` 存在 |
| 2 | 任务大盘口径 | `docs/tasks/project-tasks.md` 含 `P0–P3`、`待规划`、`BE-` |
| 3 | 治理规范 | `AGENTS.md` 含第 0 章与图表保护条款；`docs/README.md` 含治理索引 |
| 4 | 私有目录边界 | `.trae/documents`、`.workbuddy/knowledge`、`.zcode/plans` 等不得存放 >12 行的业务正文（`MIGRATED.md` 除外） |
| 5 | 文档与图表规模 | 输出 `docs` 文件数、md/svg/mmd 数量；**门槛 svg ≥ 217、mmd ≥ 68**（低于基线即告警） |
| 6 | MMD ↔ SVG 配对 | 列出缺同名 `.svg` 的 `.mmd`（存量 30 个，见 BE-001） |
| 7 | 迁入索引可达 | `docs/plans/archived-ai-sessions/README.md` 存在 |

## 2. audit-backend-rules.sh — 后端红线扫描

```bash
bash scripts/governance/audit-backend-rules.sh            # 默认警告
bash scripts/governance/audit-backend-rules.sh --strict    # 有阻断项 exit 1
```

检查项（**行首精确注解匹配**，避免把 Javadoc 里的说明文字误报）：

| # | 检查 | 说明 |
| --- | --- | --- |
| 1 | `@Autowired` | `^[[:space:]]*@(org.springframework...)?Autowired`；0 命中即合规 |
| 2 | `@Controller` / `@RestController` | 仅匹配带 `(` 的真实注解；`@RestControllerAdvice` 不算 |
| 3 | `@RestControllerAdvice` | 例外清单，仅展示不阻断（BE-004） |
| 4 | `BeanUtils.copyProperties` | 违规即阻断 |
| 5 | 生产代码手写 Logger | `private static final Logger ...` 且路径不含 `/test/`；测试单列展示不阻断 |
| 6 | Lombok 覆盖抽样 | 手写 getter/setter 抽样前 10，人工确认 |
| 7 | 构建环境 | `.sdkmanrc` 存在、根 POM 有 WebFlux、无 MVC starter |

已知结论（2026-11-16）：`@Autowired` 0、注解式控制器 0、`BeanUtils` 0；真实阻断项仅
`apex-shared-parent/apex-common/.../VirtualThreadBoundedExecutor.java:72`（BE-017）。

## 3. 与 pre-commit 的关系

`.githooks/pre-commit` 在提交涉及 `docs/`、`AGENTS.md`、`scripts/governance/`、`.githooks/` 时自动跑 `verify-docs.sh`，
另做「图表删除告警」与「>5MB 大文件提醒」。**永不阻断提交**。

```bash
git config core.hooksPath .githooks   # 每人执行一次
git config --unset core.hooksPath     # 临时关闭
```

## 4. 约定

1. 脚本不得修改仓库文件；只允许读与打印。
2. 新增检查项必须同步更新本 README 与 `docs/specs/project-governance.md` §8。
3. macOS 自带 bash 3.2：脚本内避免 `case ... ;;` 嵌在命令替换里，变量紧跟中文标点时用 `${VAR}`。
## 5. `check-doc-refs.sh`（活文档指针自检，2026-11-16 新增）

| 项 | 说明 |
| --- | --- |
| 检查 1 | 受控 Markdown 中是否仍指向 AI 私有目录（`.workbuddy/knowledge|reports/`、`.zcode/plans/`、`.trae/documents/`） |
| 检查 2 | Markdown 相对链接是否可达（目标是 `.md` 文件的相对链接） |
| 豁免 | ① `docs/_archive/` 前缀整体豁免（历史归档不改正文）② `doc-refs-allowlist.txt` 列出的文件 ③ 该行显式标注「原文未入库」 |
| 接入 | `verify-docs.sh` 第 8 项，结果计入 FAIL（默认仍只告警） |
| 严格模式 | `bash scripts/governance/check-doc-refs.sh --strict` 有问题是退出码 1 |

```bash
bash scripts/governance/check-doc-refs.sh            # 明细 + 结果计数
bash scripts/governance/check-doc-refs.sh --strict   # CI / 复审用
```

**为什么新增**：治理评审（2026-11-16）发现 25 篇活文档在迁移后仍指向私有目录，旧自检无法发现。
白名单只允许「历史归档 + 迁移说明 + 评审取证」三类，**不得豁免 `docs/` 下的现行规范与索引**。


## 6. `check-memory-sync.sh`（记忆台账同步校验，2026-11-16 新增）

| 项 | 说明 |
| --- | --- |
| 目的 | 强制「任何改动都写入记忆台账」，落实规范 §5.4 |
| 查什么 | ① 本次暂存改动里是否包含记忆台账 `docs/reference/ai-knowledge/07-change-memory.md`（前端仓为 `04-change-memory.md`，脚本自动探测）② 提交信息是否带 `memory:` 标记 |
| 阻断 | 默认**只告警**（exit 0），`--strict` 才 exit 1 |
| 接入点 | `.githooks/pre-commit` 第 0 步（所有提交都跑）+ `.githooks/commit-msg`（校验提交信息） |
| 例外 | 纯机械改动（格式化、改错字）在提交信息写 `memory: n/a（原因）` |

```bash
bash scripts/governance/check-memory-sync.sh                     # 检查已暂存改动
bash scripts/governance/check-memory-sync.sh --msg "$(cat .git/COMMIT_EDITMSG)"   # 连提交信息一起查
bash scripts/governance/check-memory-sync.sh --strict            # CI 用，缺登记即失败
```

输出示例：

```text
[memory] ⚠️ 本次提交共 3 个文件改动，但未暂存记忆台账 07-change-memory.md
[memory]    规则（规范 §5.4）：任何改动都要在台账追加一条，六字段 = 日期/类型/摘要/提交/证据/影响面
[memory] 结果：警告（不阻断提交）
```

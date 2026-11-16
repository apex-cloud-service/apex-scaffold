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

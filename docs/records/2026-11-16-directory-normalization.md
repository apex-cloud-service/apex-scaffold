# 执行记录：目录规范化（apex-scaffold，2026-11-16）

> 任务卡 BE-034 · 分支 `dev` · 方案见 [`docs/plans/2026-11-16-directory-normalization-plan.md`](../plans/2026-11-16-directory-normalization-plan.md)
> 结论：**未搬迁任何历史正文，靠「总览表 + 每个目录 README + 机器校验」把目录口径收敛到唯一入口**。

## 1. 体检结果（改动前实测）

| 项 | 实测 | 处置 |
| --- | --- | --- |
| `docs/` 一级目录 | 25 个 | 全部纳入总览表 + 白名单 |
| 缺 README 的一级目录 | 7 个（`_archive`、`api`、`architecture`、`archived`、`fix-reports`、`migration`、`reference`） | 逐个人工撰写（用途/收什么/不收什么·新内容去哪/索引） |
| 总览表未覆盖 | `fix-reports`、`reference` | 新增「🗂 目录总览」一次性覆盖 25 个 |
| 根目录业务 Markdown | 2 个（均为 3 行指针文件，BE-003 遗留） | 白名单保留，脚本里显式列出 |
| `scripts/` 根级 11 个 `.sh` | 全部是向后兼容转发壳（`exec` 到分类目录） | 不动；在方案里写清理由，避免误删 |
| 智能体目录 | `.trae`、`.trae-html-share-packages`、`.workbuddy`、`.zcode`、`.freebuff`、`.cloud-attachments` | **均未跟踪**、已被 `.gitignore` 覆盖；按规范 §4.2 不改他人私有目录 |
| `config/`（49 文件，已跟踪） | 真实配置 | 保持原位 |
| `logs/`（162M）、`backups/`（4.6M）、`data/` | 未跟踪运行产物 | 已忽略，不入库 |

## 2. 改动清单

| # | 文件 | 动作 |
| --- | --- | --- |
| 1 | `docs/README.md` | 新增「🗂 目录总览（全部一级目录）」：25 行 × 5 列 + 三条归置规则（新产出只进"收新=是"、一个主题一个权威落点、归档不改正文） |
| 2 | `docs/_archive/README.md` | 新建：只读归档说明 + 18 个子目录按篇数分组 |
| 3 | `docs/api/README.md` | 新建：早期单接口文档，新接口进 `03-api/` |
| 4 | `docs/architecture/README.md` | 新建：分层架构四件套分组 + 29 篇全量清单 + 与 `02-architecture/` 的分工 |
| 5 | `docs/archived/README.md` | 新建：AI 会话归档（`ai-sessions/`） |
| 6 | `docs/fix-reports/README.md` | 新建：Bug 修复历史快照，新记录进 `records/` |
| 7 | `docs/migration/README.md` | 新建：迁移记录，新方案进 `plans/` |
| 8 | `docs/reference/README.md` | 新建：受控参考（`ai-knowledge/`），强调台账强制 |
| 9 | `scripts/governance/check-doc-layout.sh` | 新建：4 条断言（README 齐全 / 白名单一致 / 总览覆盖 / 根目录洁净） |
| 10 | `scripts/governance/verify-docs.sh` | 新增第 9 项调用上述脚本 |
| 11 | `scripts/governance/README.md` | 新增 §8 说明（断言表 + 用法） |
| 12 | `docs/specs/project-governance.md` | 新增 §3.7「目录总览与一个主题一个家」；§8 工具表登记新脚本 |
| 13 | `docs/plans/2026-11-16-directory-normalization-plan.md` | 新建：目标结构、权威落点映射、未搬迁项与理由 |
| 14 | `docs/plans/README.md`、`docs/tasks/project-tasks.md` | 登记方案与任务卡 BE-034 |

## 3. 验收证据（可复现）

```bash
cd /Users/work/apex-scaffold
bash scripts/governance/check-doc-layout.sh
# == 1. docs 一级目录都有 README ==        [OK] 全部一级目录有 README
# == 2. 未登记的一级目录 ==                [OK] 一级目录与登记表一致（25 个）
# == 3. 目录总览表覆盖 ==                  [OK] docs/README.md 总览已覆盖全部一级目录
# == 4. 根目录无散落业务 Markdown ==       [OK] 根目录仅保留门面文档与既有指针文件
#   目录结构自检通过（0 项）

bash scripts/governance/verify-docs.sh     # 9 项，✅ 0 项问题
bash scripts/governance/check-doc-refs.sh  # 断链 [OK] 无断链，0 项
```

## 4. 未做与原因（如实记录）

| 未做 | 原因 |
| --- | --- |
| 把 `docs/architecture`（29 篇）/`docs/reports`（39 篇）/`docs/cache`（11 篇）并入编号目录 | 规范 §3.2 明确「既有目录继续有效（不搬迁、不改名、不改内容）」；物理搬迁会产生上百处链接改写，且破坏历史链接的可追溯性。改用「总览表 + 权威落点映射」消除口径冲突 |
| 删除根目录 2 个指针文件 | BE-003 的验收条件就是保留指针；删掉会让外部/历史引用失效 |
| 删除 `scripts/` 根级 11 个转发壳 | 它们是旧命令的兼容入口，删掉会让历史文档里的命令失效；已在方案中标注"不要按文件名判断是否冗余" |
| 迁移智能体私有目录 | 未跟踪、已忽略，且规范禁止跨 AI 改他人目录 |

## 5. 影响面

- 代码、构建、部署、CI：**零改动**（只新增一个只读自检脚本 + 文档）。
- 链接：新增文档全部使用相对路径，`check-doc-refs.sh` 归零；未改动任何既有文档正文。

# 目录规范化方案（apex-scaffold，2026-11-16）

> 目标：把「智能体目录 / 文档 / 脚本 / 配置 / 测试报告」的保存位置一次性说清、可机器校验。
> 原则：**不删内容、不搬历史正文、不破链接**；缺口用「索引 + README + 校验脚本」补齐，而不是大规模搬迁。

## 1. 现状体检（执行前实测）

| 对象 | 现状 | 判定 |
| --- | --- | --- |
| `docs/` 一级目录 | 25 个（编号 9 + 规范六目录 6 + 专题历史 7 + `reference`/`diagrams`/`archived`） | 两套体系并存，**无总览表**，`api`/`reference` 未登记 |
| 一级目录 README | 18 有 / **7 缺**（`_archive`、`api`、`architecture`、`archived`、`fix-reports`、`migration`、`reference`） | 新人进目录无入口 |
| 根目录业务 Markdown | `aggregation-optimization-plan.md`、`architecture-review-report.md` 均为 3 行**指针文件**（BE-003 已迁移） | 合规，保留 |
| `scripts/` | 22 个子目录 + 11 个根级 `.sh` | **根级 11 个全是向后兼容转发壳**（`exec` 到分类目录），分类已完成 |
| 智能体目录 | `.trae`（8K）、`.trae-html-share-packages`（476K）、`.workbuddy`（448M）、`.zcode`（4K）、`.freebuff`、`.cloud-attachments` | **均未被 git 跟踪**，已被 `.gitignore` 覆盖；无需入库、无需迁移 |
| `config/` | 49 个文件（nacos/license/monitoring/pmd 等）**已跟踪** | 属真实配置，保持在 `config/` |
| `logs/`（162M）、`backups/`（4.6M）、`data/` | 未跟踪 | 已忽略，属运行产物 |

## 2. 目标结构（文档）

```
docs/
├── README.md                # 唯一目录入口：导航 + 🗂 目录总览（25 行）+ 归置三规则
├── 00-getting-started/ … 08-project-management/   # 编号体系：收新内容（权威落点）
├── tasks/ specs/ plans/ records/ reviews/ notes/  # 规范六目录：新产出必归此
├── reference/ai-knowledge/  # 受控参考（记忆台账）
├── diagrams/                # 图表资产 .mmd + 同名 .svg
├── architecture/ api/ cache/ reports/ fix-reports/ migration/   # 专题历史（只读）
└── archived/ _archive/      # 归档（只读，正文不改）
```

**权威落点映射**（同一主题只有一个家）

| 主题 | 权威落点 | 历史目录（只读） |
| --- | --- | --- |
| 架构总览/演进/能力目录 | `02-architecture/` | `architecture/`（分层细节 29 篇） |
| 接口文档 | `03-api/` | `api/`（1 篇早期单接口） |
| 测试报告 | `06-testing/` | `reports/`、`_archive/test-reports/` |
| 周期报告 | `08-project-management/periodic-reports/` | `_archive/reports/` |
| 评审/评估 | `reviews/` | `fix-reports/`、`cache/` 内的评审稿 |
| 执行记录/验收证据 | `records/` | `_archive/` 全部 |
| 方案/计划 | `plans/` | `migration/`（迁移执行记录） |

## 3. 本轮实际改动

| # | 动作 | 产物 |
| --- | --- | --- |
| 1 | `docs/README.md` 新增「🗂 目录总览」：25 行 × 5 列（目录/内容/性质/收新内容/入口）+ 三条归置规则 | `docs/README.md` |
| 2 | 补齐 7 个缺失 README（每个都写「收什么 / 不收什么、新内容去哪 / 索引」） | `docs/{_archive,api,architecture,archived,fix-reports,migration,reference}/README.md` |
| 3 | 新增目录结构自检脚本并接入 `verify-docs.sh` 第 9 项 | `scripts/governance/check-doc-layout.sh` |
| 4 | 规范新增 §3.7「目录总览与一个主题一个家」，§8 工具表登记新脚本 | `docs/specs/project-governance.md` |
| 5 | 本文（方案 + 映射表 + 未搬迁理由） | `docs/plans/2026-11-16-directory-normalization-plan.md` |

## 4. 明确「不做」的项与理由

| 未做 | 理由 |
| --- | --- |
| 把 `architecture/`（29 篇）并入 `02-architecture/` | 规范 §3.2「既有目录继续有效（不搬迁、不改名、不改内容）」；41 个文件搬迁将产生上百处链接改写，收益低于风险。改由「总览表 + 权威落点」消除口径冲突 |
| 把 `docs/reports`（39 篇）、`cache`（11 篇）、`fix-reports`（1 篇）合并 | 同上，且这些是历史快照，合并会破坏历史链接（§3.6 豁免只保护"不改"，不保护"乱搬"） |
| 删除根目录 2 个指针文件 | BE-003 验收要求保留指针，外部/历史引用仍指向根路径 |
| 迁移 `.workbuddy`/`.trae`/`.zcode` 等智能体目录 | 均未被跟踪且已忽略；规范 §4.2 要求「他人私有目录不得改动」，本轮只做边界校验（`verify-docs` 第 4 项） |
| 删除 `scripts/` 根级 11 个转发壳 | 它们是**向后兼容入口**（`exec` 到分类目录），删掉会让旧文档/旧习惯中的命令失效 |

## 5. 校验（复制即用）

```bash
cd /Users/work/apex-scaffold
bash scripts/governance/check-doc-layout.sh          # 4 项断言，全 OK
bash scripts/governance/verify-docs.sh               # 9 项，0 问题
bash scripts/governance/check-doc-refs.sh            # 断链 0 项
bash scripts/governance/check-doc-layout.sh --strict # CI 用
```

## 6. 后续（如确需物理收敛）

若将来要把专题历史目录并入编号目录，必须按 §3.3 流程执行：先建新版 → SHA-256 校验 → `git mv` + 全仓链接改写 → `check-doc-refs.sh` 归零 → 保留 `MIGRATED.md` 指针 → 单独任务卡验收。**本轮不做**。

# 治理评审整改执行记录（BE-020 / FE-015）

| 项 | 值 |
| --- | --- |
| 任务卡 | **BE-020**（apex-scaffold）、**FE-015**（apex-admin-web）；覆盖评审偏差 **RV-01 ~ RV-14** |
| 依据 | `docs/reviews/2026-11-16-governance-alignment-review.md`（评审报告）、`docs/plans/2026-11-16-governance-alignment-plan.md`（整改方案） |
| 基线提交 | 前端 `1455fbc`、后端 `14f6bc2a` |
| 执行日期 | 2026-11-16 |
| 决策口径 | D1 历史断链豁免不改正文；D2 根目录 2 份迁入 `docs/` 留指针；D3 新增自检接入（默认告警）；D4 远端暂缓（无地址）；D5 记录须列全卡号；D6 `.trae` 规则镜像补条款 |
| 功能影响 | **零**。改动仅限 Markdown、`scripts/governance/`、`.githooks`、`.trae`（不入库）；未触碰任何 Java / Vue / 配置代码 |

## 1. 批次与提交

| 批次 | 内容 | 对应偏差 | 后端提交 | 前端提交 |
| --- | --- | --- | --- | --- |
| 1 | 规范自述对齐：§5.2 他人改动口径改写、§9 任务编号范围更新、评审/方案入仓、索引登记 | RV-01、RV-02、RV-03 | `9f4ed0a0` | `eca8dc9` |
| 2 | 活文档指针修正：`.trae/documents/*`、`.workbuddy/knowledge/*` 指向 `docs/` 实际位置；不存在者标注「原文未入库」 | RV-04、RV-05 | `511ecc25` | `eee0225` |
| 3 | `.trae/rules/project_rules.md` 40 → 82 行，补治理四节（红线速查/文档收口/智能体边界/提交纪律）；私有目录不入库 | RV-06 | 未入库（私有） | 未入库（私有） |
| 4 | 新增 `scripts/governance/check-doc-refs.{sh,py}` + 白名单，接入 `verify-docs.sh` 第 8 项 | RV-07、RV-08 | `45732809` | `8bbb2f7` |
| 5 | 后端根目录 2 份文档迁入 `docs/`；前端归档报告相对层级修正 | RV-09、RV-10 | `7bd25936` + `db97f70b` | `4eac00f` |
| 6 | 口径入规范（§2.5 记录粒度、§3.6 豁免、§5.3 远端与离线归档）、RV-13/RV-14 收尾、任务盘状态、本记录 | RV-11 ~ RV-14 | 本提交 | 本提交 |

## 2. 逐项结果（RV-01 ~ RV-14）

| 偏差 | 级别 | 处置 | 证据 |
| --- | --- | --- | --- |
| RV-01 后端 §5.2 仍写「3 项他人改动全程未动、未纳入提交」 | P1 | 改写为「默认不纳入；授权后只做指针/索引对齐，正文一字不改」 | `docs/specs/project-governance.md` §5.2 |
| RV-02 两仓 §9.1/§9.3 任务编号范围过期 | P1 | 更新至 BE-020 / FE-015 | 两仓规范 §9 |
| RV-03 `docs/reviews/` 零评审记录 | P1 | 评审报告 + 方案入仓，索引登记 | `docs/reviews/README.md`、`docs/plans/README.md` |
| RV-04 后端 25 篇 / 56 行活文档指向已迁走的私有目录 | P1 | 23 篇 51 行批量解析修正，其余 2 篇属豁免或双仓共用 | `git show 511ecc25 --stat` |
| RV-05 前端 1 处死指针 | P1 | `docs/features/database-config.md:153` 改为跨仓指针 + 标注原文未入库 | `eee0225` |
| RV-06 `.trae/rules/project_rules.md` 仅 40 行、无红线与收口条款 | P2 | 补到 82 行（五节），备份 `/tmp/project_rules.bak.md` | 私有目录，不入库 |
| RV-07 自检不查链接可达性与私有路径残留（后端已有 `check-doc-links.sh` 未接入） | P2 | 新增 `check-doc-refs.sh`（Python 实现，双检查）并入第 8 项 | `scripts/governance/README.md` §5 |
| RV-08 断链清单 15 条无豁免口径 | P2 | 白名单三类豁免 + 规范 §3.6 明文口径 | 规范 §3.6、`doc-refs-allowlist.txt` |
| RV-09 后端根目录 2 份散落文档 | P2 | `git mv` 迁入 `docs/plans/`、`docs/reviews/`，根目录留 3 行指针 | `7bd25936` |
| RV-10 前端 `docs/archived/project-reports/` 3 条相对层级错 | P2 | `../../../../AGENTS.md` → `../../../AGENTS.md` | `4eac00f` |
| RV-11 两仓 `git remote` = 0，无备份机制 | P3 | 规范 §5.3 写入「无远端时每周 tar + bundle + SHA-256」离线归档 | 两仓规范 §5.3 |
| RV-12 records 未列全卡号 | P3 | 规范 §2.5 增补「同批次一条记录须列全本批次卡号」；本记录即按此口径 | 两仓规范 §2.5 / 本文件 |
| RV-13 `docs/reference/ai-knowledge/README.md` 4 处指向 `.workbuddy/rag|ops` | P3 | 改为文字说明 + 标注原文未入库 | `docs/reference/ai-knowledge/README.md` |
| RV-14 `CHANGELOG.md` 与 3 份历史报告保留旧私有路径 | P3 | 文首加「历史记录说明」+ 登记白名单（历史归档不改正文） | `doc-refs-allowlist.txt` |

## 3. 迁移哈希核对

| 文件 | 迁移前 | 迁移后 | 结论 |
| --- | --- | --- | --- |
| `aggregation-optimization-plan.md` → `docs/plans/` | `38d3724d…`（252 行） | `d521ac27…`（252 行） | 仅 3 处相对链接由根路径改为 `../05-operations/…`，逐行 `diff` 确认无其它差异 |
| `architecture-review-report.md` → `docs/reviews/` | `5624dca1…` | `5624dca1…` | 逐字一致 |

## 4. 回归证据（全部实跑）

| 检查 | 后端 | 前端 |
| --- | --- | --- |
| `bash scripts/governance/verify-docs.sh` | 通过，仅 1 项存量告警（30 个 MMD 缺同名 SVG = BE-001） | 通过，0 项 |
| `bash scripts/governance/check-doc-refs.sh` | `结果：0 项` | `结果：0 项` |
| `bash scripts/governance/audit-backend-rules.sh` | 阻断 1 处（`VirtualThreadBoundedExecutor:72` 手写 Logger = BE-017，既有，未动） | — |
| `bash scripts/governance/audit-frontend-rules.sh` | — | 0 阻断（`: any` 51 处 = FE-005） |
| 图表基线 | `svg=217`、`mmd=68` **不变** | 本仓无图表 |
| `AGENTS.md` 附录 A | `tail -n 306 \| shasum -a 256` = `aa0c25fc…60ed` **不变** | `tail -n 712` = `ae5e19db…53a` **不变** |

## 5. 未完成 / 后续

| 项 | 说明 |
| --- | --- |
| D4 远端 | 两仓仍 `remote=0`；方案要求「先 push 再谈清理」，需业主提供仓库地址 |
| BE-001 | 30 个 `.mmd` 缺同名 `.svg`，按批次补导出（禁止删源） |
| BE-017 | `apex-common` 手写 Logger 改 `@Slf4j`，需单模块验证日志字段兼容 |
| 复审 | 本记录 §4 即复审输入；评审报告 §6 的复审结论随本提交追加（见该文件末尾「复审结论」） |

## 6. 回滚方式

按批次回退，不跨批混回：

```bash
# 单批次回退（示例：后端批次 5）
cd /Users/work/apex-scaffold && git revert --no-edit 7bd25936 db97f70b
# 前端批次 5
cd /Users/work/apex-admin-web && git revert --no-edit 4eac00f
```

**禁止** `git reset --hard`、`push -f`（规范 §5.2、§7）。

# 工作周报

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

按 ISO 周归档的工作周报，文件名格式为 `YYYY-Www-all-projects-weekly-report.md`。命名、内容要素与数据口径见 [上级 README](../README.md)。

2026-11-03 起为全项目单周报口径：每周一份，覆盖工作区全部项目（约 300 字，向领导汇报口径），不再按仓库拆分；此前按 `<范围>` 拆分的历史周报保留为存档。

## 索引

| 周期 | 范围 | 文档 |
| --- | --- | --- |
| 2026 年第 43 周（10-19 ~ 10-25） | Apex-Scaffold 本仓库 | [2026-W43-apex-scaffold-weekly-report.md](./2026-W43-apex-scaffold-weekly-report.md) |
| 2026 年第 43 周（10-19 ~ 10-25） | 冷链管理系统 V2.0 / 园区管理系统 / 供应链对接 | [2026-W43-cold-chain-park-supply-weekly-report.md](./2026-W43-cold-chain-park-supply-weekly-report.md)（另有 [.doc](./2026-W43-cold-chain-park-supply-weekly-report.doc) / [.docx](./2026-W43-cold-chain-park-supply-weekly-report.docx) 交付版） |
| 2026 年第 44 周（10-26 ~ 11-01） | Apex-Scaffold 后端 + apex-admin-web 前端 | [2026-W44-apex-scaffold-weekly-report.md](./2026-W44-apex-scaffold-weekly-report.md)（另有 [.docx](./2026-W44-apex-scaffold-weekly-report.docx) 交付版，内容为上一版，未含本次验证范围与测试计数的修正） |

## 生成与补写约定

- **自动生成**：每周六 17:00 由工作区定时任务产出当周周报，覆盖周一至周六，落盘为 `.md` 并回写本索引。
- **数据来源**：本周 `git log --since="<周一>" --until="now"`、本目录当周全部日报、`.workbuddy/memory/`、`.workbuddy/knowledge/`。周报必须与同期日报的结论一致，出现矛盾以实际提交与验证结果为准并注明差异。
- **周期口径**：ISO 周，周一为起始日；跨年周按 ISO 8601 归属年份，例如 2027-01-01 若属上一年第 53 周，则记为 `2026-W53`。
- **补写**：漏报周期按同一格式补齐，插入索引时保持周期升序。
- **去重**：同一周期同一范围只保留一份，二次生成直接覆盖。
- 周报为过程性记录，不参与技术文档索引。

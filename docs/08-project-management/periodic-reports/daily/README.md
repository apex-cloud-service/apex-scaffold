# 工作日报

按自然日归档的工作日报，文件名格式为 `YYYY-MM-DD-<范围>-daily-report.md`。命名、内容要素与数据口径见 [上级 README](../README.md)。

本工作区同时承载多个项目，同一日期可能出现多份日报，以 `<范围>` 区分，不合并、不覆盖。

## 索引

| 日期 | 范围 | 主题 | 文档 |
| --- | --- | --- | --- |
| 2026-10-27 | 冷链管理系统 V2.0 | 后端功能开发与接口联调 | [2026-10-27-cold-chain-daily-report.md](./2026-10-27-cold-chain-daily-report.md)（另有 [.html](./2026-10-27-cold-chain-daily-report.html) / [.docx](./2026-10-27-cold-chain-daily-report.docx) 交付副本） |
| 2026-10-28 | 冷链管理系统 V2.0 | 后端功能开发与接口联调 | [2026-10-28-cold-chain-daily-report.md](./2026-10-28-cold-chain-daily-report.md)（另有 [.html](./2026-10-28-cold-chain-daily-report.html) / [.docx](./2026-10-28-cold-chain-daily-report.docx) 交付副本） |
| 2026-10-29 | 冷链管理系统 V2.0 | 28 改 / 23 增文件，功能收口 | [2026-10-29-cold-chain-daily-report.md](./2026-10-29-cold-chain-daily-report.md)（另有 [.html](./2026-10-29-cold-chain-daily-report.html) 交付副本） |
| 2026-10-31 | apex-scaffold | 42.33 评审收口（13 提交）与 A1+A2 统一认证/JWKS 闭环（未提交） | [2026-10-31-apex-scaffold-daily-report.md](./2026-10-31-apex-scaffold-daily-report.md) |
| 2026-10-31 | apex-admin-web | 对齐后端 v2 契约并新增生成向导/游标收件箱/公开页 | [2026-10-31-apex-admin-web-daily-report.md](./2026-10-31-apex-admin-web-daily-report.md) |
| 2026-11-01 | apex-scaffold | B 批多租户收官、C 批组件化贯通（除 C4）、D 批中台前四项（10 提交 / 89 文件） | [2026-11-01-apex-scaffold-daily-report.md](./2026-11-01-apex-scaffold-daily-report.md) |
| 2026-11-02 | apex-scaffold | E 批集成与 IoT 全面收口（E1–E4）、D 批 D5 数据服务层与开放路由门禁收官（5 提交 / 303 文件） | [2026-11-02-apex-scaffold-daily-report.md](./2026-11-02-apex-scaffold-daily-report.md) |

## 生成与补写约定

- **自动生成**：每日 17:00 前（16:30 触发）由工作区定时任务产出当日日报，落盘为 `.md` 并回写本索引。
- **数据来源**：当日 `git log --since="00:00" --until="now"`、`git status`、`.workbuddy/memory/YYYY-MM-DD.md`、`.workbuddy/knowledge/`。多项目场景按仓库分别统计并在报告头部标注「项目」字段。
- **补写**：漏报日期按同一格式补齐，插入索引时保持日期升序；不得用「无变动」以外的空话占位，确实无提交时写明「无代码变更」并说明当日工作性质（如评审、调研）。
- **去重**：同一日期同一范围只保留一份，当日二次生成直接覆盖，不产生 `v2`、`final` 等副本。
- 日报为过程性记录，不参与技术文档索引。

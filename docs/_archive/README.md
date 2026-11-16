# docs/_archive（下线文档总仓 · 只读）

## 用途与边界

- 收录：已下线的历史文档（235 篇），按来源分组保留，**内容一律不改**。
- **只读**：不新增、不追加、不修正口径。需要"现行结论"请去对应的权威目录（见 [`docs/README.md`](../README.md) 目录总览）。
- 断链豁免：本目录内以及指向本目录的相对链接，按规范 §3.6 享受历史路径豁免；`scripts/governance/doc-refs-allowlist.txt` 另有逐文件白名单。

## 分组（按篇数）

| 子目录 | 篇数 | 来源/内容 |
| --- | --- | --- |
| [`reports/`](./reports/) | 77 | 早期阶段总结、优化报告 |
| [`trae-reports/`](./trae-reports/) | 38 | Trae 工具产出的报告 |
| [`trae-documents/`](./trae-documents/) | 25 | Trae 工具产出的文档（`.trae/documents` 迁移而来） |
| [`integration/`](./integration/) | 23 | 第三方集成过程记录 |
| [`operations/`](./operations/) | 10 | 早期运维记录 |
| [`root-reports/`](./root-reports/) | 8 | 曾散落在仓库根目录的报告 |
| [`technical/`](./technical/) | 7 | 技术专题旧稿 |
| [`test-reports/`](./test-reports/) | 7 | 早期测试报告 |
| [`optimization/`](./optimization/) | 4 | 性能/结构优化旧稿 |
| [`ppt/`](./ppt/) | 4 | 汇报材料 |
| [`svg-fix-2025/`](./svg-fix-2025/) | 3 | SVG 修复专项记录 |
| [`api/`](./api/) | 2 | 旧接口文档 |
| [`postman/`](./postman/) | 2 | Postman 相关说明 |
| [`superpowers/`](./superpowers/) | 2 | 工具产物 |
| [`troubleshooting/`](./troubleshooting/) | 2 | 排障记录 |
| [`changes/`](./changes/) | 1 | 变更说明 |
| [`testing/`](./testing/) | 1 | 测试说明 |
| `recovery/` | 0 | 预留（空目录，未入库） |
| 根级散篇 | 15 | 早期总结报告（如 `项目完成总结报告.md`） |

## 图表资产

本目录下的 `.mmd`/`.svg` 仍受规范 §3.5 保护（**不得删除/替换**），`verify-docs.sh` 的配对检查覆盖全 `docs/`，包含本目录。

## 维护约定

1. 任何删除、移动都需要单独任务卡 + 用户确认；本轮不动。
2. 想引用历史结论，请在新文档里"引用并注明快照时间"，不要把旧文正文复制回活文档。

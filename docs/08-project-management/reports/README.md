# 技术报告

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

本目录收录**非周期性**的技术报告：交付报告、专项审计、实施总结与验证记录。周期性过程记录（日报 / 周报 / 月报）不在本目录，统一归档于 [周期报告](../periodic-reports/README.md)。

## 约定

- 按主题命名，文件名使用小写短横线（`kebab-case`），不按时间建子目录（例外：`2026-10/` 已随周期报告迁出而移除）。同一主题只保留最新版本，更新即覆盖，不产生 `v2`、`final` 副本。
- 报告须写明结论与验证依据；未执行的验证显式标注「未执行」，不得以推断替代。
- 已有交付件的二进制副本（`.docx` 等）与原 `.md` 同置，`.md` 为权威源。

## 索引

### 交付与实施

| 文档 | 主题 |
| --- | --- |
| [infrastructure-websocket-delivery-report.md](./infrastructure-websocket-delivery-report.md) | 基础设施 WebSocket 交付 |
| [service-enhancement-delivery-report.md](./service-enhancement-delivery-report.md) | 服务增强交付（文件清单、真实检查结果与运行限制） |
| [storage-refactoring-delivery-report.md](./storage-refactoring-delivery-report.md) | 对象存储重构交付 |
| [storage-refactoring-tests/](./storage-refactoring-tests/) | 对象存储重构测试记录 |

### 专项审计与收口

| 文档 | 主题 |
| --- | --- |
| [springdoc-annotation-completion-report.md](./springdoc-annotation-completion-report.md) | SpringDoc 注解补齐完成报告 |
| [springdoc-annotation-final-summary-report.md](./springdoc-annotation-final-summary-report.md) | SpringDoc 注解最终汇总 |
| [springdoc-follow-up-optimization-checklist.md](./springdoc-follow-up-optimization-checklist.md) | SpringDoc 后续优化清单 |

---

**维护者**: Apex Team
**最后更新**: 2026-10-31

# AI 会话设计文档归档（docs/plans/archived-ai-sessions）

本目录保存原 `.trae/documents/` 中的设计、迁移、增强方案原文，用于追溯「当时为什么这么设计」。

- **迁移日期**：2026-11-16（任务 BE-012）
- **内容改动**：无，逐字节一致（SHA-256 已比对，共 1742 行）
- **原目录**：`.trae/documents/`（被 `.gitignore` 忽略，未入库），已保留同名指针文件 + `MIGRATED.md`

## 文件清单

| 文件 | 标题 | 体量 |
| --- | --- | --- |
| `apex-scaffold-docs-and-diagrams-alignment.md` | 文档/脚本/测试对齐与图表体系完善计划 | 206 行 |
| `gateway-resilience-enhancement-plan.md` | 平台韧性、消息、事务与可观测性增强实现方案 | 1022 行 |
| `microservice-migration-execution-plan.md` | 微服务改造执行计划 | 315 行 |
| `microservice-phase5-6-extraction-design.md` | 微服务化 Phase 5/6 抽取设计与可行性结论 | 199 行 |

## 使用约定

1. 归档件是**历史快照**，不作为当前事实依据；与代码冲突时以代码、`pom.xml` 与 `AGENTS.md` 为准。
2. 其中仍有效的结论，应提炼进 `docs/specs/`（方案）或 `docs/02-architecture/`（架构说明）。
3. 归档件只增不改；后续结论用新增文件表达。
4. 迁移后已修正 3 处活跃引用（`apex-common/README.md`、`docs/06-testing/gateway-module-test-report.md`、`docs/architecture/service-governance-solution.md`）指向本目录；历史归档文档中的旧路径引用保持原样，不做批量改写。

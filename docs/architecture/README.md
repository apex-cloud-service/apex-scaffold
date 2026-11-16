# docs/architecture（分层架构详解 · 专题历史）

## 用途与边界

- 收录：分层架构的**深度展开**——逻辑/物理/技术架构各自的「决策 / 细节 / 快速参考 / 业务价值」四件套，以及数据流、数据库设计、部署架构、业务流程、技术栈等专题（共 29 篇 + `_templates/`、`advanced-features/`）。
- **新架构文档的权威落点**是 [`docs/02-architecture/`](../02-architecture/README.md)（总览、演进、能力目录、上下文图）。本目录作为**细分深度材料**继续有效，二者互补而非替代。
- 判断口径：**讲"是什么/为什么这么定"** → `02-architecture/`；**讲"某一层的完整细节"** → 本目录。

## 分组

| 组 | 文件 |
| --- | --- |
| 逻辑架构 | `01-logical-architecture-*.md`（business-value / decision / detail / quick-ref） |
| 物理架构 | `02-physical-architecture-*.md` |
| 技术架构 | `03-technical-architecture-*.md` |
| 专题 | `backend-technology-stack.md`、`data-flow-architecture.md`、`database-design.md`、`deployment-architecture.md`、`business-flows.md`、`core-tech-diagrams-index.md` 等 |
| 模板 | [`_templates/`](./_templates/) ——新增架构文档的骨架 |
| 进阶特性 | [`advanced-features/`](./advanced-features/) |

## 全量清单（29 篇）

| 文件 | 备注 |
| --- | --- |
| [01-logical-architecture-business-value.md](./01-logical-architecture-business-value.md) | — |
| [01-logical-architecture-decision.md](./01-logical-architecture-decision.md) | — |
| [01-logical-architecture-detail.md](./01-logical-architecture-detail.md) | — |
| [01-logical-architecture-quick-ref.md](./01-logical-architecture-quick-ref.md) | — |
| [02-physical-architecture-business-value.md](./02-physical-architecture-business-value.md) | — |
| [02-physical-architecture-decision.md](./02-physical-architecture-decision.md) | — |
| [02-physical-architecture-detail.md](./02-physical-architecture-detail.md) | — |
| [02-physical-architecture-quick-ref.md](./02-physical-architecture-quick-ref.md) | — |
| [03-technical-architecture-business-value.md](./03-technical-architecture-business-value.md) | — |
| [03-technical-architecture-decision.md](./03-technical-architecture-decision.md) | — |
| [03-technical-architecture-detail.md](./03-technical-architecture-detail.md) | — |
| [03-technical-architecture-quick-ref.md](./03-technical-architecture-quick-ref.md) | — |
| [backend-technology-stack.md](./backend-technology-stack.md) | — |
| [business-flows.md](./business-flows.md) | — |
| [data-flow-architecture.md](./data-flow-architecture.md) | — |
| [database-design.md](./database-design.md) | — |
| [deployment-architecture.md](./deployment-architecture.md) | — |
| [frontend-technology-stack.md](./frontend-technology-stack.md) | — |
| [infrastructure-components.md](./infrastructure-components.md) | — |
| [mq-architecture.md](./mq-architecture.md) | — |
| [multi-tenant-isolation-architecture.md](./multi-tenant-isolation-architecture.md) | — |
| [network-architecture.md](./network-architecture.md) | — |
| [reactive-programming-guide.md](./reactive-programming-guide.md) | — |
| [redis-watchdog-mechanism.md](./redis-watchdog-mechanism.md) | — |
| [service-governance-implementation.md](./service-governance-implementation.md) | — |
| [service-governance-solution.md](./service-governance-solution.md) | — |
| [strategy-pattern-guide.md](./strategy-pattern-guide.md) | — |
| [system-architecture.md](./system-architecture.md) | — |

## 维护约定

1. 新增分层细节文档放本目录，并在上表登记；**架构总览/演进类**放 `02-architecture/`。
2. 图表遵循规范 §3.5：`.mmd` 源 + 同名 `.svg` 成对，源放 `docs/diagrams/`（见 [`docs/diagrams/README.md`](../diagrams/README.md)）。

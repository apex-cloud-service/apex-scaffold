# 工作区知识库

> **定位**：跨三个活跃仓库的「事实层 + 导航层」，不是又一套需求文档。
> **边界**：不复制既有权威文档的正文，只做**结论、现状、指针、陷阱**；细节一律回指原文档或源码。
> **建立日期**：2026-10-31 · **最近校准**：**2026-11-16** · **基线**：apex v1.3.1（根直接 7 组 / 递归 44 模块 / 含根 45 reactor）/ web v1.2.0 / cold 未打 tag

---

## 〇、三个入口（先看这里）

| 仓 | 一页式记忆卡 | 本仓知识库 |
| --- | --- | --- |
| apex-scaffold | `MEMORY-CARD.md` | 本目录（01–06） |
| apex-admin-web | `MEMORY-CARD.md` | `apex-admin-web/docs/reference/ai-knowledge/` |
| cold-chain | `MEMORY-CARD.md` | `cold-chain/docs/reference/ai-knowledge/` |

跨仓索引：`.workbuddy/memory/CARDS.md`。**本目录的 01–06 以 apex 为主、兼顾跨仓导航**；web/cold 的细节已下沉到各自仓的知识库，避免两处维护。

**检索入口**：不确定结论在哪一篇时，先用 AI 私有目录下的 RAG 检索（覆盖三仓文档与源码，返回路径+行号），再回到本目录读结论。RAG 的说明文档位于 AI 工作目录 `rag/README.md`，**按规范不入库**（原文未入库），需要时向维护人索取。

**维护入口**：本目录与三张记忆卡都是**手工快照，不会随代码自动更新**。开工前跑 `python3 .workbuddy/ops/maintain.py status` 检测口径漂移，改完文档跑 `sync` 刷新索引，`--accept` 收口基线；日报/周报/月报用同目录 `maintain.py daily|weekly|monthly`。详见 [ops/README.md](../../../.workbuddy/ops/README.md)。

## 一、知识库解决什么问题

三个仓库各有文档，但**没有一处能回答**以下问题：

1. 我手上到底有几个在跑的项目，它们怎么连在一起？
2. 后端现在的真实形态是什么（多少模块、几个服务、哪个库、哪套迁移）？
3. 哪些约定违反了就要返工？哪些坑踩过一次不能再踩？
4. 现在主线做到哪一步，下一批做什么，验收缺什么证据？

本库按这四问组织，读完约 20 分钟。

## 二、权威优先级

发生冲突时按此顺序采信，**越靠前越可信**：

1. **源码与构建产物**（`pom.xml`、`*.java`、`db/migration`、`git log`）
2. `/Users/work/apex-scaffold/AGENTS.md`（协作约束唯一权威）
3. 仓库内主题文档（`docs/` 下按编号分节）
4. 本知识库（是对 1–3 的提炼，会滞后）
5. `.workbuddy/memory/` 与本文档的「状态」章节（时效性最强，权威性最低）

> 本库所有数字都带采集日期。看到与源码不一致，**以源码为准并回来修订本库**。

## 三、阅读路径

| 你是 | 先读 | 再读 |
| --- | --- | --- |
| 新人接手 / 换机器 | [01 项目地图](01-project-map.md) | [02 后端现状](02-apex-scaffold-current-state.md) → [03 约定与陷阱](03-conventions-and-pitfalls.md) |
| 要改后端代码 | [03 约定与陷阱](03-conventions-and-pitfalls.md) | [02 后端现状](02-apex-scaffold-current-state.md) |
| 要改前端页面 | `apex-admin-web/MEMORY-CARD.md` | [04 前端工程](04-admin-web.md) → 该仓 `docs/reference/ai-knowledge/` |
| 接冷链客户项目 | `cold-chain/MEMORY-CARD.md` | [05 冷链项目](05-cold-chain.md) → 该仓 `docs/reference/ai-knowledge/` 与 `docs/memory/` |
| 汇报 / 排期 | [06 交付状态](06-delivery-status-and-next.md) | `.workbuddy/memory/PROJECTS.md` |
| 只记得关键词、不知道在哪 | `.workbuddy/rag/`（`rag.py search "..."` 或 `serve` 界面） | 命中结果里的路径与行号 |

## 四、文件索引

| 文件 | 内容 | 更新时机 |
| --- | --- | --- |
| [01-project-map.md](01-project-map.md) | 三仓清单、依赖关系、服务/端口/库/迁移归属、模块地图、文档入口 | 新增仓库或服务时 |
| [02-apex-scaffold-current-state.md](02-apex-scaffold-current-state.md) | 后端架构现状、认证/租户/网关/可观测的真实完成度与缺口 | 每批次交付后 |
| [03-conventions-and-pitfalls.md](03-conventions-and-pitfalls.md) | 硬约束、构建口径、已踩过的坑、提交前自检清单 | 新增约束或踩坑后 |
| [04-admin-web.md](04-admin-web.md) | 前端技术栈、页面模块、契约前缀、工程约定、风险（**摘要**；细节见该仓知识库） | 前端重大改版后 |
| [05-cold-chain.md](05-cold-chain.md) | 冷链项目定位、17 模块、关键业务口径、当前进度、风险（**摘要**；细节见该仓记忆与知识库） | 客户项目节点后 |
| [06-delivery-status-and-next.md](06-delivery-status-and-next.md) | 主线批次 A–E 状态、下一步、证据缺口、跨项目待办 | 每工作单元 |
| AI 私有目录 `rag/README.md`（原文未入库） | RAG 索引范围、检索原理、评测指标、HTTP API、LLM 接入 | 检索逻辑或索引范围变更时 |
| AI 私有目录 `ops/README.md`（原文未入库） | 漂移检测原理、`sync`/`status` 用法、日报周报月报口径 | 维护流程或报告口径变更时 |

## 五、维护规则

1. **状态类信息必须带日期**，格式 `（YYYY-MM-DD 实测）`；无日期的数字视为不可信。
2. 只写**结论与指针**：能一句话说清的不要写三段；细节给相对路径链接。
3. 结构性变化（新增服务、新增库、迁移归属变更、批次推进）**当天**更新对应文件，并在 `.workbuddy/memory/YYYY-MM-DD.md` 追加一条。
4. 删除优于堆积：被证伪或过期的段落直接重写，不留「已废弃」注释（除口径级纠偏需保留对照）。
5. 不放密钥、内网口令、客户敏感数据；`config/` 下证书与私钥一律只写路径。

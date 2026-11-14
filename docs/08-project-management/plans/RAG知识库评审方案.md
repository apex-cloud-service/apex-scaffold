# RAG 知识库与索引库评审报告

> **方案状态**：待评审（本文档仅评审与建议，未改动任何代码）
> **创建时间**：2026-11-14
> **评审范围**：apex-scaffold（后端）与 apex-admin-web（管理后台前端）
> **结论摘要**：建议**有条件实现、分两期**。一期为「知识库管理 + 语义/混合检索」（不强依赖大模型），二期为「RAG 智能问答」（依赖模型供应商决策）。开工前需确认文末 D1–D8 决策点；配套实施细节见 [RAG知识库实施方案](RAG知识库实施方案.md)。

## 一、评审目的与方法

1. 目的：判断本项目是否需要实现 RAG 知识库与向量索引库；若需要，明确功能边界、技术落点与实施代价。
2. 方法：对两仓做代码级事实核查（关键类、POM、配置、路由逐项定位），不臆造需求；候选场景逐项给出价值与依赖，结论附决策点清单交业务确认。
3. 本报告引用的类名、配置键均为当前代码实测；历史方案文档（如《全文搜索与运行日志查询实施方案》）仅作背景，不作为现状依据。

## 二、现状盘点（证据）

### 2.1 后端：可复用能力

| 能力 | 现状 | 关键证据 |
| --- | --- | --- |
| 全文检索 | Meilisearch 已完整集成：单索引承载用户/角色/菜单/部门/字典/配置六类资源，强制租户过滤；默认关闭（`meilisearch.enabled`，dev 开启），关闭时空实现兜底；每日凌晨校准 Job + 手动重建端点 | `apex-infrastructure-integration/.../config/MeilisearchConfig.java`（提供 SDK Client Bean）、`apex-system/.../infrastructure/search/MeilisearchAdapter.java`、`infrastructure/search/SearchDisabledConfiguration.java`、`application/job/SearchIndexReconciliationJob.java` |
| 搜索 HTTP 接口 | `GET /admin-api/v1/search`（权限码 `system:search:query`）、`POST /admin-api/v1/search/rebuild`（`system:search:rebuild`） | `apex-system/.../interfaces/router/SearchRouter.java`、`interfaces/handler/UnifiedSearchHandler.java` |
| 事件驱动索引链路 | `ResourceIndexEvent` + 监听器已接线，但全仓无业务生产者，事件链路空转；实际一致性靠校准 Job | `apex-system/.../application/event/ResourceIndexEvent.java`、`application/listener/ResourceIndexEventListener.java` |
| 文件存取 | 五后端存储（local/minio/火山 TOS/阿里 OSS/七牛）+ SPI 扩展；分片上传/断点续传；限时分享下载；`file_record` 表含 `tenant_id`、`sha256`（字段存在但未启用内容去重） | `apex-storage-spring-boot-starter`（`storage.default-type` 选择）、`apex-file/.../persistence/po/FilePO.java`、`apex-file-service` 的 `ChunkRouter`/`FileShareRouter` |
| 文档实体 | file 域已有 `file_document` 表：标题/类型/分类/正文 `content`/附件 URL/标签/状态/租户 | `apex-file/.../persistence/po/DocumentPO.java` |
| 异步与事件 | RocketMQ 唯一 MQ；双 Outbox（领域事件 Outbox + 消息表 Outbox）+ `TransactionalOutboxExecutor` 同事务落箱；跨服务事件经 `DomainEventMqBridge` 发 topic | `apex-infrastructure-messaging/.../event/*`、`outbox/TransactionalOutboxExecutor.java` |
| 租户与权限 | 多租户行级拦截器默认开启（basic 档除外）；`@RequirePermission` + Flyway 双库权限/菜单种子；WebFlux 函数式 Router + Handler 规范 | 仓库根 AGENTS.md「持久化迁移与租户隔离」「Router 与 Handler」 |
| 降级模式 | `SearchDisabledConfiguration` 的 `@ConditionalOnMissingBean` 空实现兜底模式，可直接复制到新能力 | `apex-system/.../infrastructure/search/SearchDisabledConfiguration.java` |
| 办公集成 | 飞书/钉钉/企微三平台（消息/审批/日历/会议室/云文档元数据代理），无本地文档预览转换 | `apex-office/.../domain/OfficePlatform.java`、`apex-office-service` 路由清单 |

### 2.2 后端：RAG 必需缺口

| 缺口 | 说明 |
| --- | --- |
| LLM / Embedding 接入 | 全仓无任何模型调用代码与依赖；关键字 `embedding/openai/qwen/milvus/spring-ai/向量` 的命中均为 AES-GCM「初始向量」注释等误报 |
| 文本提取 | 无 Tika/PDFBox/POI 依赖（仅 EasyExcel）；文件服务只做字节流存取，无 `extractText` 类调用 |
| 向量检索 | Meilisearch 当前仅词法全文；`MeilisearchAdapter` 的 `highlightSnippet`/`score` 为占位（固定空串/1.0）；Java SDK `meilisearch-java 0.17.1` 对 hybrid/embedders 参数的支持未验证 |
| 知识库域模型 | 全仓 Java 源码「knowledge/知识库」零命中，无表、实体、接口、迁移 |
| 对话能力 | 无会话/消息模型；前端无对话组件（现有 SSE 仅用于通知流） |
| 索引服务部署 | 监控 compose（`config/monitoring/podman-compose.yml`）未包含 Meilisearch 服务，其部署形态以各环境部署配置为准，未随交付物固化 |

### 2.3 前端 apex-admin-web 现状

- 技术栈：Vue 3.5.13 + Element Plus 2.9.1 + Vite 6 + Pinia + axios；约 4.7 万行、211 个源文件；无 i18n 框架（文案硬编码中文）。
- 搜索：顶栏 `src/components/GlobalSearch/index.vue` → `src/api/search.ts` → `GET /admin-api/v1/search`，仅覆盖六类管理资源；无独立搜索页。
- 文件：`src/components/FileUploadEnhanced.vue` 支持拖拽/粘贴/压缩/分片上传；预览仅图片（el-image），无 pdf/docx/office 预览组件。
- AI / 知识库：**无任何页面、组件、API 模块**（`AI/chat/知识/知识库/机器人/问答/rag/embedding/openai` 等关键词全查无真实命中，命中项均为测试代码 `clearAllMocks` 等误报）。
- 可复用基建：菜单驱动动态路由 + `VIEW_REGISTRY`（`src/router/dynamic.ts`，48 个注册项）、`v-permission` 按钮权限指令、`src/utils/notification-stream.ts` SSE 封装、`src/utils/request.ts` 统一 `Result{code,msg,data,traceId}` 解包与 401 刷新重放。

### 2.4 需求侧记录

- 全仓产品/交付文档无 RAG、知识库需求条目；`docs/08-project-management/delivery/delivery-scope.md` 等交付范围未列该能力。
- 文档中「知识库」字样命中均为 AI 协作笔记目录（`.workbuddy/knowledge/`）与运维报告表述，非产品功能。

## 三、候选场景与价值判定

| 编号 | 场景 | 内容 | 价值判断 | 主要依赖 |
| --- | --- | --- | --- | --- |
| S1 | 跨资源语义检索 | 管理数据 + 知识文档统一语义/混合搜索，解决「词法搜不到」的同义表述问题 | 中（六类管理资源词法检索已覆盖） | Embedding 供应商 |
| S2 | 企业知识库管理 | 知识空间/文档上传/解析/分块/索引/检索，租户隔离 | 高（管理后台常见诉求，当前完全缺失） | 文本解析、向量索引 |
| S3 | 智能问答（RAG） | 基于知识库的对话问答，答案带引用来源，SSE 流式 | 高，但强依赖模型接入与数据合规确认 | LLM 供应商、S2 先行 |
| S4 | 面向最终用户的帮助中心问答 | 公开门户形态 | 另立项，不在本期 | 前端门户形态 |

## 四、技术选型评估

### 4.1 索引 / 向量库

| 选项 | 优势 | 代价与风险 | 结论 |
| --- | --- | --- | --- |
| Meilisearch hybrid（v1.8+ embedders 机制） | 已在技术栈；可选部署模式成熟（`meilisearch.enabled` + 空实现兜底）；运维简单 | 适用中小规模；SDK 0.17.1 对 hybrid 参数支持未验证，需 WebClient 直调 REST 兜底；索引服务本体尚未进入交付 compose | **推荐（一期）**，以端口抽象隔离 |
| pgvector | 检索质量与生态好 | 需为知识库固定 PostgreSQL 链路，与「MySQL/PG 双库配对」策略复杂化；新增扩展运维 | 备选（规模上升后） |
| Milvus | 大规模 ANN、成熟 | 新增重量级组件与运维成本 | 超大规模再引入 |
| Elasticsearch/OpenSearch | 全栈检索生态 | 部署重，与现有 Meilisearch 能力重复 | 不采纳 |

### 4.2 模型接入

- 统一 OpenAI 兼容协议（`/v1/embeddings`、`/v1/chat/completions`）：Dashscope（通义）/ DeepSeek / OpenAI / 本地 Ollama、vLLM 均可经 base-url + api-key + model 配置切换，兼容云 API 与离线本地化部署两种形态。
- 自建轻量端口 `EmbeddingProvider` / `ChatProvider`，以 WebClient 实现适配器；**不引入 Spring AI**（依赖面大，与既有 DDD 端口风格、WebFlux 约束的磨合成本高于收益）。
- 供应商关闭时自动降级：无 embedding → 仅词法检索；无 chat → 问答入口不可用但检索可用。降级逻辑与 `SearchDisabledConfiguration` 模式一致。

### 4.3 文本解析

- Apache Tika 一站式解析 pdf/office/txt/md/html；解析为同步阻塞调用，隔离到 `BlockingIoSchedulers` 专用池（`storage()`/`general()`），不进请求线程。
- 扫描件 OCR、多模态解析不在本期。

## 五、结论

1. **判定：建议实现，分两期。**
   - 一期（知识库 + 检索）：S2 + S1。新增知识库域模型与摄取管线（上传→解析→分块→向量化→索引），检索走 Meilisearch hybrid；embedding 供应商关闭时自动退化为词法检索，知识文档管理不受影响。
   - 二期（RAG 问答）：S3。在决策点 D3（模型供应商）与 D4（合规边界）确认后实施，答案带引用来源、SSE 流式。
2. **不投入的分支**：若业务确认短期内无私有知识检索/问答诉求，可整体延后，维持现有 Meilisearch 六类资源全文检索；一期方案无沉没成本（复用的都是既有设施）。
3. 本期明确不做：OCR、多模态 embedding、Agent/工具调用、跨实例会话实时广播、面向公众的门户站点。

## 六、决策点清单（开工前必须确认）

| 编号 | 决策点 | 待确认内容 | 建议默认值 |
| --- | --- | --- | --- |
| D1 | 业务范围 | 一期/二期裁剪，是否含 S1 | 一期 S2+S1，二期 S3 |
| D2 | 运行形态 | 新建 `apex-knowledge` 业务库 + `apex-knowledge-service` 可选运行模块（对齐 job/masking 可选模式）；或并入 file/system 域；或自含组件形态（对齐 integration C7 试点） | 新建独立可选运行模块 |
| D3 | 模型供应商 | 云 API（Dashscope/DeepSeek/OpenAI）还是本地化（Ollama/vLLM）；是否必须支持完全离线部署；base-url 是否租户级可配 | 全局配置 + OpenAI 兼容协议双轨 |
| D4 | 数据边界与合规 | 哪些文件允许入库；embedding 外发的租户数据合规口径；脱敏沿用策略 | 默认仅租户主动上传内容入库 |
| D5 | 规模预估 | 文档数/分块数/检索与问答并发 | 中小规模（≤ 数十万分块） |
| D6 | 前端范围 | 知识库管理页、检索页、问答页清单；是否顺带补 pdf/docx 文档预览 | 三页 + 分块文本预览，不做文档渲染预览 |
| D7 | 许可档位 | 知识库/问答能力归属 basic/standard/enterprise 哪一档 | standard 及以上可选启用 |
| D8 | 里程碑与排期 | 开工窗口、验收口径 | 按实施方案第六章分步推进 |

## 七、风险与依赖

| 风险/依赖 | 影响 | 应对 |
| --- | --- | --- |
| 模型供应商不可用/费用/合规 | embedding 或问答失败 | 端口抽象 + 显式降级（词法检索兜底）；供应商可配置可关闭；密钥沿用生产模板值拒绝校验 |
| SDK 能力缺口（meilisearch-java 0.17.1） | hybrid 参数可能不可用 | 开工前做能力 spike；不支持则 WebClient 直调 Meilisearch REST `/indexes/{index}/search` |
| 文档解析质量（复杂版式/扫描件） | 分块噪声影响召回 | 一期限定可解析类型清单，解析失败显式标记（`parse_status=FAILED`），OCR 不承诺 |
| 租户数据越权 | 跨租户知识泄漏 | 检索强制 tenantId 过滤（对齐 `SearchRepository` 口径，fail-closed）；索引文档带租户过滤字段；写入与更新同样校验 |
| 成本控制 | embedding 调用量膨胀 | 批量向量化 + 内容 hash 缓存复用 + 解析去重 |
| 部署形态分散 | 索引服务未固化进交付物 | 随一期补充 compose profile / K8s 可选清单与配置文档 |

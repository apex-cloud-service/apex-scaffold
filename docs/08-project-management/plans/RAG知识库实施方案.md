# RAG 知识库实施方案

> **方案状态**：已按本文实施（2026-11-14 落地完成；落地偏差见文末「落地偏差记录」）
> **创建时间**：2026-11-14
> **前置条件**：[RAG知识库评审方案](RAG知识库评审方案.md) 中 D1–D8 决策点确认；本文按「一期 S2+S1、二期 S3、新建独立可选运行模块」的推荐默认值编写，若决策不同需先调整本文再实施。
> **技术底座**：Java 21 + Spring Boot 3.5.15 + WebFlux + MyBatis-Plus + Flyway（MySQL/PostgreSQL 双库）+ Meilisearch 0.17.1 Java SDK（`meilisearch-java`）+ RocketMQ Outbox + Vue 3 / Element Plus 管理后台。

## 一、总体架构

```
管理后台 apex-admin-web（Vue3 / Element Plus）
   │  菜单驱动路由 + v-permission
   ▼
apex-gateway（lb://apex-knowledge-service；关闭 Nacos 时显式 HTTP 地址）
   ▼
apex-knowledge-service（可选运行模块；业务端口建议 8099 / 管理端口建议 8100，开工前核对端口清单）
   └── apex-knowledge 业务库（DDD 四层：interfaces / application / domain / infrastructure）
         ├── domain      知识空间、文档、分块、会话领域模型 + 端口
         ├── application 摄取编排、检索用例、问答用例
         ├── infrastructure 存取/解析/向量化/索引适配器 + Mapper/PO
         └── interfaces  函数式 Router + Handler（协议层在业务库内，对齐 integration 自含形态？否——独立运行模块，协议层随 service，见 2.1）
   外部依赖：
     ├── 存储对象   apex-storage-spring-boot-starter（local/minio/TOS/OSS/七牛，复用既有选择机制）
     ├── 解析       Apache Tika（新增依赖，阻塞隔离 BlockingIoSchedulers）
     ├── 模型       EmbeddingProvider / ChatProvider（OpenAI 兼容协议，WebClient 自建适配器）
     ├── 索引       Meilisearch（词法 + hybrid 向量；关闭时空实现兜底）
     ├── 数据库     MySQL / PostgreSQL（Flyway 独立版本链，apex-knowledge/src/main/resources/db/migration/{mysql,postgresql}/）
     └── 异步摄取   RocketMQ + TransactionalOutboxExecutor（业务写 + Outbox 落箱同事务）
```

关键原则：

1. **可降级**：无 Meilisearch → 检索返回空并提示未启用（复制 `SearchDisabledConfiguration` 模式）；无 embedding 供应商 → 仅词法索引与检索；无 chat 供应商 → 问答入口关闭、检索不受影响。
2. **可选部署**：默认关闭（`apex.knowledge.enabled:false`），不进入八服务标准发布清单，以第三个可选运行模块对齐 `apex-job-service` / `apex-masking-service` 模式；不进 basic 档。
3. **租户隔离**：所有表带 `tenant_id` 并纳入多租户拦截器；索引文档带租户过滤字段；检索、更新、删除全部校验租户与可见性，fail-closed。
4. **阻塞隔离**：Tika 解析、Meilisearch 同步 SDK 调用、同步文件 I/O 分别隔离到 `BlockingIoSchedulers.storage()/remote()/jdbc()`；模型调用走异步 WebClient。
5. **不越界**：不写 file 域表（`file_record`、`file_document` 只读不写或完全不碰）；不扩展聚合服务；不在网关承载业务逻辑。

## 二、模块归属与机械同步项

### 2.1 模块与包结构

- 新建业务库 `apex-knowledge`：parent 为 `apex-business-parent`，聚合登记至 `apex-business-parent` 的 `<modules>`；包根 `com.apex.scaffold.knowledge`。
- 新建运行模块 `apex-knowledge-service`：聚合与继承均在 `apex-services-bom`（第三个可选运行模块）；负责启动装配，不复制业务实现。
- 协议层归属：知识库为新增服务，按 AGENTS.md「新增服务默认采用运行模块形态」，Router/Handler 放 `apex-knowledge-service` 的 `interfaces/` 包，**不**采用 integration 的自含组件开关形态。
- 依赖关系（直接内部生产依赖）：`apex-knowledge` → common、domain、storage-spring-boot-starter；`apex-knowledge-service` → common、domain、knowledge、job-spring-boot-starter、seata-spring-boot-starter（对齐六个业务 service 的既有模式）。不依赖 infrastructure-persistence（不引入共享 Mapper 体系，持久化辅助沿用 common 通用持久化助手），如实施中发现需要再单独评审。

```
apex-knowledge/src/main/java/com/apex/scaffold/knowledge/
├── domain/          知识空间/文档/分块/会话实体、领域服务、端口（KnowledgeSearchPort、EmbeddingPort、ChatPort、DocumentParserPort）
├── application/     用例与编排（IngestionAppService、KnowledgeSearchAppService、ChatAppService）、DTO、Assembler
└── infrastructure/  Tika 解析器、OpenAI 兼容模型适配器、Meilisearch 适配器、Mapper/PO、降级空实现
```

### 2.2 开工机械同步清单（每步合入前核对）

| 项 | 动作 |
| --- | --- |
| Maven 聚合 | 根 POM 不动；`apex-business-parent` 增加 `apex-knowledge`、`apex-services-bom` 增加 `apex-knowledge-service`，各登记一次 |
| 版本管理 | Tika、（如需）jtokkit 等新第三方版本进根 POM `dependencyManagement` |
| 注册表 | `apex-tools-build` 的 `build-manifest.yaml` 新增两条目，`BuildManifestSyncTest` 保持通过（递归 modules 集合对齐） |
| 网关 | 新增 `lb://apex-knowledge-service` 路由及关闭 Nacos 的显式地址配置；`GatewayArchTest` 约束不变 |
| 种子数据 | 菜单 + 权限种子双库（MySQL/PostgreSQL 配对），权限编码与 `@RequirePermission` 一致 |
| 发布 | `release.sh` SERVICES 清单**不**含 knowledge-service（可选模块不进标准发布包），manifest 打包字段对齐 masking-service 口径 |
| 部署 | `.env.example` 新增知识库与模型供应商变量；Compose 新增可选 profile；K8s 可选清单另行确认；配置文档同步 |
| 文档 | README 模块分组表、AGENTS.md 模块职责与依赖表新增行；如出现 `/internal/v1/**` 端点则登记 `docs/03-api/internal-api-contracts.md`（一期无内部端点则不登记） |
| 端口 | 业务 8099 / 管理 8100 为建议值，落地前与全量端口清单核对后写入配置 |

## 三、领域模型与迁移

### 3.1 表设计（`apex-knowledge/src/main/resources/db/migration/{mysql,postgresql}/`，独立版本链从 V1 起，双库配对）

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `kb_space` | `id`、`name`、`description`、`visibility`（TENANT/PUBLIC）、`status`、`tenant_id` | 知识空间；MyBatis-Plus 乐观锁/逻辑删除/审计字段沿用 `BasePO` 习惯 |
| `kb_document` | `id`、`space_id`、`title`、`source_type`（UPLOAD/MANUAL）、`content_type`、`object_key`、`file_size`、`content_text`（解析产物）、`parse_status`（PENDING/PARSED/FAILED）、`parse_error`、`chunk_count`、`index_status`（NONE/PENDING/INDEXED/FAILED）、`tenant_id` | 文档主档；解析与索引状态机 |
| `kb_chunk` | `id`、`document_id`、`seq`、`content`、`content_hash`（缓存复用键）、`token_estimate`、`tenant_id` | 分块表；向量本体不落库（存 Meilisearch 文档 `_vectors`） |
| `kb_conversation` | `id`、`title`、`user_id`、`space_ids`（JSON）、`tenant_id` | 二期问答会话 |
| `kb_message` | `id`、`conversation_id`、`role`（USER/ASSISTANT）、`content`、`citations`（JSON：chunkId/documentId/spaceId/score）、`tenant_id` | 二期问答消息，含引用溯源 |

约束与规范：

- MySQL 业务时间字段 `DATETIME`，PostgreSQL 用对应 `TIMESTAMP`，Java 侧 `LocalDateTime`；禁用 32 位秒时间戳与远期占位日期。
- `kb_document.space_id`、`kb_chunk.document_id` 建索引；`tenant_id` 参与联合索引；多租户拦截器顺序沿用现有持久化配置。
- 权限种子迁移（V2）双库配对：`kb:space:create/update/delete/list`、`kb:doc:upload/list/read/delete/reindex`、`kb:search:query`、`kb:chat:ask`；菜单种子组件名与前端 `VIEW_REGISTRY` 注册键一致。

### 3.2 领域端口（先端口后实现，依赖方向受架构测试约束）

| 端口 | 职责 | 降级实现 |
| --- | --- | --- |
| `DocumentParserPort` | 字节流 → 纯文本（Tika） | 仅支持纯文本类型，其余返回不可解析 |
| `ChunkingService` | 文本 → 分块列表（领域服务，非端口） | — |
| `EmbeddingPort` | 批量文本 → 向量 | 无供应商时抛「未启用」由编排层降级 |
| `KnowledgeSearchPort` | 混合/词法检索（keyword、spaceIds、tenantId、topK） | 空实现返回空页 + 未启用标记 |
| `ChatPort` | 流式对话（SSE） | 无供应商时问答用例直接拒绝并给出明确错误码 |

## 四、摄取管线（一期核心）

```
上传（multipart，经 storage starter 落对象存储，登记 kb_document，parse_status=PENDING）
   → 同事务 Outbox 落箱 KbDocumentIngestedEvent（TransactionalOutboxExecutor）
   → Outbox Dispatcher 重放 → 进程内监听器编排摄取
        1. Tika 解析（BlockingIoSchedulers.storage()；失败 → parse_status=FAILED + parse_error）
        2. 分块（默认 800 字符滑窗、重叠 120，段落感知切分；`apex.knowledge.ingest.chunk-size/overlap` 可配）
        3. 批量向量化（EmbeddingPort；content_hash 缓存复用；batch 上限可配；失败重试后标记 index_status=FAILED）
        4. 写 Meilisearch 索引 kb_chunks（embedders 配置；文档含 tenantId/spaceId 过滤字段与状态）
   → 校准兜底：每日 Job 按更新时间比对 DB 与索引（对齐 SearchIndexReconciliationJob 模式）+ 手动重建端点
```

- 无 embedding 供应商时跳过步骤 3，仍写词法索引（Meilisearch 无 embedders 亦可全文检索），`index_status=INDEXED_LEXICAL`。
- 上传支持两种来源：直接 multipart（kb 自管对象，`object_key` 记录）；引用既有 `file_record`（后续扩展，一期不做，避免跨域写）。
- 支持类型一期清单：`txt/md/pdf/docx/doc/xlsx/pptx/html`（以 Tika 实测为准）；超限大小（默认 50MB，可配）直接拒绝。

## 五、检索服务（一期）

- HTTP（WebFlux 函数式 Router + Handler，`@RouterOperation`/`@Schema` 元数据齐全）：
  - 业务端：`GET /api/v1/kb/search`（keyword、spaceIds、page、size）——登录即可，租户 + 可见性过滤。
  - 管理端：`POST/PUT/DELETE/GET /admin-api/v1/kb/spaces...`、`POST /admin-api/v1/kb/spaces/{spaceId}/documents`、`GET /admin-api/v1/kb/documents`（分页/状态过滤）、`GET /admin-api/v1/kb/documents/{docId}`（含分块预览）、`POST /admin-api/v1/kb/documents/{docId}/reindex`。
- 权限：管理端全套 `@RequirePermission("kb:...")`；业务端检索 `kb:search:query`；写操作 `@OperationLog`。
- 混合检索：Meilisearch `/indexes/{index}/search` 携带 `hybrid`（embedder + semanticRatio 可配）；**开工前先做 SDK 能力 spike**——`meilisearch-java 0.17.1` 不支持则统一经 WebClient 直调 REST，SDK 仅用于索引管理。
- 降级链：hybrid → 词法 → 空实现（未启用）；`highlightSnippet`/`score` 真实回填（修复既有占位模式在新索引上的表现，`MeilisearchAdapter` 本身不在本期改动范围）。

## 六、RAG 问答链路（二期）

1. 会话接口：`POST /api/v1/kb/chat/conversations`、`GET /api/v1/kb/chat/conversations/{id}/messages`、`POST /api/v1/kb/chat/ask`（SSE 流式应答）。
2. 编排：问题改写（可选，同 ChatPort）→ 检索 top-k（强制租户过滤）→ 组装上下文（引用标注 `[1][2]`，超长截断）→ 流式应答 → `kb_message` 落库（含 citations JSON）。
3. 安全约束：
   - 系统提示词固定模板 + 检索内容以分隔标记包裹，降低 prompt 注入面；检索内容不含系统配置值。
   - 会话/消息强制租户与属主校验；日志不含密钥与完整用户输入（沿用现有日志脱敏口径）。
   - 问答为读操作不加 `@OperationLog`；会话管理写操作按业务语义加审计。
   - 前端复用 `src/utils/notification-stream.ts` 的 SSE 封装模式新增对话流，不引入 AI SDK。

## 七、配置设计

```yaml
apex:
  knowledge:
    enabled: ${KNOWLEDGE_ENABLED:false}          # 总开关，默认关闭
    ingest:
      chunk-size: 800
      chunk-overlap: 120
      max-upload-size-mb: 50
      supported-types: txt,md,pdf,docx,doc,xlsx,pptx,html
    embedding:
      enabled: ${KNOWLEDGE_EMBEDDING_ENABLED:false}
      base-url: ${KNOWLEDGE_EMBEDDING_BASE_URL:}   # OpenAI 兼容，如 Dashscope/Ollama
      api-key: ${KNOWLEDGE_EMBEDDING_API_KEY:}     # 生产注入，模板值拒绝
      model: text-embedding-v3
      dimensions: 1024                              # 启动校验与 embedder 配置一致
      batch-size: 32
    chat:
      enabled: ${KNOWLEDGE_CHAT_ENABLED:false}
      base-url: ${KNOWLEDGE_CHAT_BASE_URL:}
      api-key: ${KNOWLEDGE_CHAT_API_KEY:}
      model: qwen-plus
      timeout-seconds: 60
    search:
      hybrid-semantic-ratio: 0.6
      top-k: 5
```

- 生产校验沿用既有密钥校验模式：必填模板值拒绝、长度/格式校验；`enabled=true` 而 base-url 缺失时启动失败（fail-fast）。
- Meilisearch 客户端复用 `meilisearch.*` 既有配置块与 `MeilisearchConfig` 提供的 Client Bean；kb_chunks 索引复用 `index-prefix`。

## 八、前端方案（apex-admin-web）

| 项 | 内容 |
| --- | --- |
| API 模块 | 新增 `src/api/knowledge.ts`（get/post/put/del + 类型），头部注释集中列出端点契约 |
| 页面（菜单驱动 + VIEW_REGISTRY） | ① 知识库空间管理（CRUD + 可见性）② 文档管理（上传/解析状态/分块预览/重建索引）③ 知识检索页 ④ 二期：问答页（SSE 流式 + 引用来源展示） |
| 权限 | 菜单种子经 `/admin-api/v1/menus` 下发；按钮用 `v-permission="'kb:doc:upload'"` 等 |
| 上传 | 复用 `FileUploadEnhanced` 分片能力视需要裁剪；一期先走普通 multipart，超 50MB 拒绝 |
| 预览 | 仅分块文本预览；不做 pdf/docx 渲染预览（不引入 pdfjs/docx-preview，决策点 D6 可推翻） |
| i18n | 前端无 i18n 框架，沿用硬编码中文；后端新增用户可见消息同步维护 `apex-knowledge/src/main/resources/i18n/` |

## 九、测试与验收

- 单测/架构测试：沿用各模块既有模式（分层守护、调度器隔离检查可复制 `NoBoundedElasticArchGuardTest` 思路）。
- 迁移测试：知识库域双库迁移回归对齐 `*DualDatabaseMigrationIntegrationTest` 模式（Testcontainers MySQL 9 + PostgreSQL 15），注意 `-T4` 并发容器竞争的单模块重跑口径。
- 降级矩阵：{Meilisearch 启用/关闭} × {embedding 启用/关闭} × {chat 启用/关闭} 八象限至少覆盖关键四象限（全关、仅词法、词法+向量、全开）。
- 真实模型调用测试放独立 profile（对齐 remote-it 思路），不混入默认门禁；计时敏感用例不得改弱 SLA 掩盖问题。
- 验证命令：
  - `mvnd -T4 -B -pl :apex-knowledge-service -am test`
  - `mvnd -T4 -B spotless:check`
  - `mvnd -T4 -B clean verify`（完整门禁：反应堆全部模块 BUILD SUCCESS，测试 Failures 0 / Errors 0）
  - `git diff --check`
- 前端：Vitest + Playwright 既有体系，新增 API 模块与组件配对测试。

## 十、实施步骤与里程碑

| 阶段 | 内容 | 预估 | 出口条件 |
| --- | --- | --- | --- |
| P0 决策与 spike | D1–D8 确认；SDK hybrid 能力验证（不支持则定 REST 直调）；供应商连通性验证 | 2–3 人日 | 决策点签字 + spike 结论 |
| P1 骨架 | 新建两模块 + 聚合/manifest/网关路由/端口 + 空实现降级装配 + V1 迁移双库 | 4–5 人日 | `clean verify` 全绿；边界/注册表测试通过 |
| P2 摄取管线 | 解析/分块/Outbox 异步编排/校准 Job/管理端 CRUD API + 权限种子 | 6–8 人日 | 双库迁移 IT 通过；摄取失败路径有测试 |
| P3 检索 | EmbeddingPort + hybrid 检索 + 降级矩阵 + 业务端检索 API + 校准 | 4–6 人日 | 降级矩阵测试通过；检索接口契约测试通过 |
| P4 前端一期 | 三个页面 + API 模块 + 菜单/权限联调 | 5–7 人日 | Vitest/Playwright 通过；菜单驱动可达 |
| P5 问答（二期） | ChatPort + SSE 编排 + 会话持久化 + 前端问答页 + 引用溯源 | 6–8 人日 | D3/D4 确认后实施；引用与租户隔离测试通过 |
| P6 收口 | 配置文档/README/AGENTS/契约文档同步 + compose profile + `.env.example` + 全量门禁 + 压测（可选） | 3–5 人日 | `check-doc-links` 全绿；`clean verify` 全绿 |

合计约 30–42 人日（一期 21–29，二期 6–8，收口 3–5）；并行开发可压缩，串行保守按上限估。

## 十一、风险与应对

| 风险 | 应对 |
| --- | --- |
| 模型供应商不可用/费用失控 | 端口抽象 + 显式降级；batch + hash 缓存；`embedding.enabled` 独立开关 |
| SDK hybrid 缺口 | P0 spike 先行；WebClient 直调 REST 兜底方案已内置 |
| 解析质量噪声 | 限定类型清单；解析失败显式状态与重试；分块参数可配 |
| 租户越权 | 索引文档带租户过滤字段；检索/更新/删除全链 fail-closed；纳入架构与单测 |
| 发布清单膨胀 | 可选运行模块不进标准发布包与 SERVICES 清单，仅 manifest 登记 |
| 与既有搜索混淆 | kb_chunks 独立索引，不写入 `apex_system_resources`；`MeilisearchAdapter`/六类资源检索不改动 |

## 十二、落地偏差记录（2026-11-14 实施后回写）

实施与本文的差异（代码为唯一事实来源，此处仅为对齐说明）：

| 项 | 本文原方案 | 实际落地 | 原因 |
| --- | --- | --- | --- |
| 端口 | 业务 8099 / 管理 8100 | 业务与管理合一 8100 | 宿主 8099 已被 Compose 的 XXL 控制台映射占用；可选服务管理端口不分立（对齐 job/masking） |
| 摄取触发 | 同事务 Outbox 落箱 + Dispatcher 重放 | 注册完成后进程内触发摄取管线 + 每日校准 Job 兜底 | 摄取为服务内闭环（无跨服务消费方），引入 Outbox/RocketMQ 依赖对可选服务成本大于收益；重启遗留的 PENDING 文档由 `KnowledgeIndexReconciliationJob` 收敛，一致性语义不变 |
| Meilisearch 配置 | 复用既有 `meilisearch.*` 配置块与 Client Bean | 独立 `apex.knowledge.meilisearch.*` 与本地 Client Bean | 复用需引入 `apex-infrastructure-integration`（携带 Redis/微信/邮件 SDK），且同类型 Client Bean 在 boot-all 组合拓扑下会冲突 |
| 权限执行 | 未细化 | 本地 `KnowledgeSecurityAspect` + `KnowledgePermissionClient`（授权中心快照，fail-closed），对齐 office/workflow 模式 | common 的 OperationLog 切面 pointcut 不含 knowledgeservice，且 @RequirePermission 在 common 仅有注解无公共切面 |
| job/seata starter | knowledge-service 对齐六业务 service 引入 | 未引入 | 可选模块（对齐 job/masking），无 XXL 任务与分布式事务诉求 |
| hybrid 检索 | SDK 能力 spike 后定 SDK 或 REST | 直接以 REST 直调检索 + SDK 写入索引 | `meilisearch-java 0.17.1` 的 SearchRequest 未完整暴露 hybrid 参数，REST 直调即 spike 兜底结论；检索失败自动降级词法 |
| 问答事件协议 | SSE citations/delta/done | citations/delta/done/error 四事件 | 补充显式 error 事件，保证前端总能收到失败信号 |

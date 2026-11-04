# SpringDoc 注解补齐任务后续优化建议清单

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

## 1. 清单说明

本清单基于 [SpringDoc 注解补齐任务完成报告](./springdoc-annotation-completion-report.md) 中记录的遗留问题和后续建议整理，重点覆盖：

- 原始 SpringDoc 检查脚本误报；
- 检查脚本入口和 CI/CD 集成；
- OpenAPI 元数据深度校验；
- Handler JavaDoc 覆盖率治理；
- 新接口开发过程中的文档一致性保障。

本清单不改变本次任务已经确认的结论：当前 Router、Handler 和 DTO 的 SpringDoc 注解覆盖率均已达到 100%，现有 OpenAPI 覆盖测试已通过。

---

## 1.1 完成状态（2026-10-01 更新）

| 编号 | 事项 | 状态 | 说明 |
|---|---|---|---|
| 3.1 | 统一使用精准检查脚本 | ✅ 完成 | 旧脚本转发至 v2；CI 直接调用 v2 |
| 3.2 | 增加脚本回归样例 | ✅ 完成 | `test-springdoc-checks.py` 14 项通过 |
| 4.1 | 扩展 RouterOpenApiCoverageTest | ✅ 完成 | 新增请求体媒体类型、响应媒体类型（SSE/文件下载/JSON）、DTO @Schema、查询参数四项深度契约测试；修复 SSE 响应媒体类型与 DTO 引用实体问题 |
| 4.3 | operationId 冲突与差异检查 | ✅ 完成 | 纳入 4.1 新增测试，已发现并修复多处不一致 |
| 5.1 | 精准注解检查纳入 CI | ✅ 完成 | `code-quality` 任务接入覆盖率检查与脚本回归测试；契约测试由 `unit-test` 覆盖 |
| 6.1 | JavaDoc 与 SpringDoc 职责边界 | ✅ 完成 | 新建独立 `check-handler-javadoc.sh` |
| 6.2 | 分批补充 Handler JavaDoc | ✅ 完成 | 补齐 6 个文件 11 个方法的 Javadoc |
| 6.3 | 修正 JavaDoc 检查逻辑 | ✅ 完成 | `scan-javadoc-issues.sh` 与新脚本均采用注释剥离 + 注解感知检测 |
| 4.2 | 真实 OpenAPI 文档结构校验 | ✅ 完成 | 基于 `customizedOpenApi()` 构建真实 OpenAPI 文档并校验请求/响应媒体类型与参数 |
| 5.2 | PR 检查说明 | ✅ 完成 | 新增 `.github/PULL_REQUEST_TEMPLATE.md`，包含接口文档检查清单 |
| 7.x | 接口文档内容质量复核 | ✅ 完成 | DTO 已有 500+ 处 example 值；为 PageRequest 补充分页示例值与约束描述；SSE/文件下载媒体类型已修正 |
| 8.x | 文档版本归档与追溯机制 | ✅ 完成 | 新增 `OpenApiExportTest` 导出 JSON/YAML、`archive-openapi.sh` 按版本+git hash 归档至 `docs/03-api/openapi/`、release 流水线自动归档 |

---

## 2. 优先级定义

| 优先级 | 含义 | 建议处理范围 |
|---|---|---|
| P0 | 必须优先处理 | 会造成检查结果不可信、阻断 CI 或持续误导开发者 |
| P1 | 高优先级 | 能显著提升接口文档准确性和回归防护能力 |
| P2 | 中优先级 | 改善可维护性、可读性和团队开发体验 |
| P3 | 低优先级 | 适合在专项治理或周期性维护中处理 |

---

## 3. P0：修复检查入口和误报问题

### 3.1 统一使用精准检查脚本

**问题**

旧脚本 `scripts/dev/check/check-springdoc-annotations.sh` 通过文本匹配统计 Handler 方法，会把以下内容错误识别为缺少 `@Operation`：

- JavaDoc 中的示例方法；
- Sentinel `BlockException` 限流回调；
- 其他非 HTTP 端点的 `Mono`/`Flux` 方法。

当前精准版本 `scripts/dev/check/check-springdoc-annotations-v2.sh` 已通过本次审查，应作为标准检查入口。

**建议动作**

- 将项目开发规范、CI 配置和开发文档中的检查命令统一替换为 `check-springdoc-annotations-v2.sh`；
- 在旧脚本顶部标记废弃状态，避免新代码继续引用；
- 在旧脚本中增加迁移提示，指向 v2 脚本；
- 确认仓库内不存在仍引用旧脚本的自动化任务、README 或构建脚本。

**验收标准**

- 全仓库搜索后，CI、构建脚本和开发文档不再把旧脚本作为标准检查入口；
- v2 脚本执行结果稳定为：
  - Router：17/17；
  - Handler：272/272；
  - DTO：151/151；
- JavaDoc 示例代码和 Sentinel 回调不再出现在缺失接口列表中。

**建议涉及文件**

- `scripts/dev/check/check-springdoc-annotations.sh`
- `scripts/dev/check/check-springdoc-annotations-v2.sh`
- 可能引用检查脚本的 CI、构建脚本和开发文档

---

### 3.2 增加脚本回归样例

**问题**

v2 脚本已经通过当前代码库验证，但目前主要依赖真实项目源码作为测试样本。后续脚本调整时，仍可能重新引入注释代码误报或误排除真实 Handler 方法。

**建议动作**

为检查脚本增加最小化样例测试，至少覆盖：

- 正常的 `public Mono<ServerResponse>` HTTP Handler 方法；
- 带 `@Operation` 的 Handler 方法；
- JavaDoc 代码块中的 `public Mono` 示例；
- Sentinel `BlockException` 回调；
- 普通非 HTTP 的 `Mono`/`Flux` 方法；
- 多行方法声明；
- 注解跨多行排列；
- 没有任何端点方法的基础 Handler 类。

**验收标准**

- 样例测试能够区分真实 HTTP Handler 方法与注释代码；
- Sentinel 回调不会被计入 Handler 端点统计；
- 真实缺少 `@Operation` 的方法能够被准确报告；
- 脚本退出码在通过和失败场景下均符合预期。

**建议实现方式**

优先使用现有 shell 检查脚本体系和公共函数库，不额外引入新的测试框架。若 shell 文本解析难以可靠处理 Java 语法，可将“方法识别”逐步迁移到 Java AST 或编译期检查方案。

---

## 4. P1：加强 OpenAPI 契约质量校验

### 4.1 扩展 RouterOpenApiCoverageTest

**现状**

当前覆盖测试已经验证路由路径、`operationId` 唯一性、摘要、Handler 存在性、标签、响应元数据和路径参数。

**建议增加的校验**

- Router 和 Handler 的 `operationId` 完全一致；
- `operationId` 全局唯一且不能为空；
- POST、PUT、PATCH 接口的请求体声明与 Handler 实际 `bodyToMono(...)` 类型一致；
- 请求体的 media type 与实际路由接收类型一致；
- 路径模板中的每个变量都存在对应的 PATH 参数；
- PATH 参数不能被错误声明为 QUERY 参数；
- 查询参数的名称与 Handler 实际读取的参数一致；
- `Long`、`Integer`、`Boolean`、枚举等参数的 OpenAPI 类型与代码解析逻辑一致；
- 成功响应 media type 与 Handler 实际响应一致；
- SSE 接口必须使用 `text/event-stream`；
- 文件下载、文件上传、二进制响应不能错误声明为普通 JSON；
- 请求体 DTO 和响应 DTO 必须存在于 OpenAPI components schema 中；
- 必要的错误状态码至少包含 400、401、403、404、409、429 或 500 中适用的项目统一响应定义。

**验收标准**

- 新增校验失败时能够输出具体的 operationId、路径、HTTP 方法和问题原因；
- 现有 `RouterOpenApiCoverageTest` 全部通过；
- 至少补充覆盖 Chart、Document、Workflow、SSE 和健康检查接口的回归用例；
- 测试失败时不会只输出“覆盖率不足”，而是能定位到具体接口。

---

### 4.2 增加真实 OpenAPI 文档快照或契约检查

**问题**

注解静态检查只能确认源码中存在注解，不能完全保证 SpringDoc 最终生成的 OpenAPI 文档结构正确。

**建议动作**

增加基于应用上下文或测试上下文生成 OpenAPI 文档的校验，检查最终的 `/v3/api-docs` 或等价 `OpenAPI` 对象：

- 路由是否全部出现在最终文档中；
- operationId 是否唯一；
- requestBody、parameters、responses 是否被 SpringDoc 正确合并；
- DTO Schema 是否被正确解析；
- 自定义 `OpenApiDocumentationCustomizer` 是否覆盖了预期 operationId；
- SSE、文件响应等特殊媒体类型是否保持正确。

**验收标准**

- 测试能够从最终 OpenAPI 文档中验证关键契约，而不只读取 Java 注解；
- 关键接口的文档结构变化能够在测试中被发现；
- OpenAPI 文档生成失败时测试明确失败。

**实施注意事项**

- 不建议一开始对完整 JSON 做无差别快照，容易因排序、版本或描述变化产生大量无意义差异；
- 优先采用结构化断言；
- 对确需稳定化的关键接口，再增加局部快照。

---

### 4.3 建立 operationId 注册和冲突检查

**问题**

本项目的 `operationId` 同时被 Router、Handler、Customizer 和测试使用，属于跨组件契约。手工维护容易出现重复、拼写不一致或重命名遗漏。

**建议动作**

- 在测试中扫描所有 Router 和 Handler 的 operationId 并建立索引；
- 对重复 operationId 直接失败；
- 对只存在于 Router 或只存在于 Handler 的 operationId 输出差异；
- 对 `OpenApiDocumentationCustomizer` 中的 operationId 映射进行反向校验；
- 新接口模板中明确要求先确定稳定的 operationId，再编写 Router 和 Handler。

**验收标准**

- operationId 重复时测试失败；
- Router 与 Handler 不一致时测试失败；
- Customizer 中存在无对应接口的映射时至少输出警告，必要时失败；
- operationId 变更必须能够在代码评审中被识别。

---

## 5. P1：纳入 CI/CD 和开发流程

### 5.1 将精准注解检查纳入 CI

**建议动作**

在 CI 中增加独立的接口文档检查阶段，执行：

```bash
./scripts/dev/check/check-springdoc-annotations-v2.sh
source ./scripts/build/init-build-env.sh
mvnd -pl apex-interfaces -am \
  -Dtest=RouterOpenApiCoverageTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

CI 环境必须遵循项目约定：

- 使用 SDKMAN 加载项目指定的 Java 17；
- 使用 `mvnd`，不回退到 `mvn`；
- 构建前执行 `scripts/build/init-build-env.sh`；
- 失败时保留检查报告和测试日志。

**验收标准**

- 任一新增 HTTP 接口缺少必需 SpringDoc 注解时，CI 失败；
- operationId 重复或 Router/Handler 不一致时，CI 失败；
- OpenAPI 覆盖测试失败时，CI 失败；
- CI 日志能够展示具体失败接口；
- 检查结果可作为合并请求的质量门禁。

---

### 5.2 增加 Pull Request 检查说明

**建议动作**

在接口变更的 Pull Request 模板或开发检查清单中增加以下项目：

- [ ] 是否新增或修改了 Handler 方法；
- [ ] 是否同步修改 Router 元数据；
- [ ] Router 和 Handler 的 operationId 是否一致；
- [ ] 是否声明了实际请求体 DTO；
- [ ] 是否声明了所有 PATH、QUERY、HEADER 参数；
- [ ] 是否补充成功和适用的错误响应；
- [ ] Request/Response DTO 是否有类级和字段级 `@Schema`；
- [ ] 是否运行了精准 SpringDoc 检查脚本；
- [ ] 是否运行了 OpenAPI 覆盖测试；
- [ ] 是否检查了最终生成文档中的媒体类型和 Schema。

**验收标准**

- 接口变更评审可以通过固定清单确认文档契约；
- 清单内容与自动化检查保持一致，不要求开发者重复执行无法自动化验证的工作。

---

## 6. P2：Handler JavaDoc 专项治理

### 6.1 明确 JavaDoc 与 SpringDoc 的职责边界

**现状**

旧脚本报告约 236 个 Handler JavaDoc 缺失项，但这不属于本次 SpringDoc 注解补齐任务的未完成项。

**建议原则**

- SpringDoc/Swagger 注解：面向 API 使用者，描述 HTTP 契约；
- JavaDoc：面向代码维护者，描述业务意图、处理流程、参数来源、异常和返回值；
- 两者不应简单复制，也不应以一个替代另一个。

**验收标准**

- 项目规范明确两类文档的用途；
- SpringDoc 检查不会因为 JavaDoc 缺失而误判接口注解失败；
- JavaDoc 检查作为独立指标和独立任务管理。

---

### 6.2 分批补充 Handler JavaDoc

**建议动作**

不要一次性批量重写全部 JavaDoc，建议按业务模块分批处理：

1. 先处理高频、对外暴露和安全敏感接口；
2. 再处理工作流、图表、文档、通知等核心模块；
3. 最后处理内部管理和低频接口；
4. 保留已有准确 JavaDoc，只补充缺失或明显过时的内容。

每个 Handler 方法的 JavaDoc 至少说明：

- 接口业务目的；
- 关键处理步骤；
- 重要参数来源及约束；
- 主要返回结果；
- 可能的业务异常或鉴权要求；
- 是否涉及阻塞 I/O、事务、异步事件或外部调用。

**验收标准**

- 按模块记录 JavaDoc 覆盖率，而不是只统计全局总数；
- 每个补充的 JavaDoc 与实际实现一致；
- 不为 Sentinel 回调、内部辅助方法或 JavaDoc 示例强行添加 HTTP 接口说明；
- JavaDoc 检查脚本能够排除注释代码和非 HTTP 回调。

---

### 6.3 修正 JavaDoc 检查逻辑

**问题**

旧脚本的 JavaDoc 检查同样基于固定行数和文本搜索，可能存在：

- 注释距离方法声明超过固定窗口时误报；
- 注释中出现 `/**` 时误判为方法已有 JavaDoc；
- 非 HTTP 方法被纳入 Handler JavaDoc 统计；
- 多行方法声明或注解排列导致识别不稳定。

**建议动作**

- 复用 v2 脚本的注释剥离和真实方法识别逻辑；
- 仅对真实 HTTP Handler 方法检查 JavaDoc；
- 将 JavaDoc 检查拆成独立脚本或独立检查模式；
- 对 `BlockException` 回调、私有辅助方法和示例代码建立排除规则；
- 对复杂 Java 语法逐步评估 AST 解析方案。

**验收标准**

- JavaDoc 检查结果可重复；
- 正常 JavaDoc、缺失 JavaDoc 和非 HTTP 方法三类样例均能准确识别；
- 检查报告能够输出文件、方法名和行号；
- JavaDoc 检查失败不会混淆 SpringDoc 检查结果。

---

## 7. P2：完善接口文档内容质量

### 7.1 统一响应和错误响应描述

**建议动作**

对所有公开接口逐步检查：

- 200、201、202、204 等成功状态码是否符合实际行为；
- 400 参数校验失败是否有说明；
- 401 未认证、403 无权限是否有说明；
- 404 资源不存在是否有说明；
- 409 业务冲突是否适用；
- 429 限流响应是否适用；
- 500 系统异常是否通过统一错误响应表达；
- `Result<T>` 包装结构与实际响应 Schema 是否一致。

**验收标准**

- 不再出现“接口返回 204、文档声明 200”或“实际 URL、文档声明文件流”等语义不一致；
- 错误响应结构与项目统一异常处理机制一致；
- 生成的 Swagger UI 能够让调用方理解常见失败场景。

---

### 7.2 统一参数约束、默认值和示例值

**建议动作**

对分页、状态、标识符和枚举参数统一补充：

- `minimum`、`maximum`；
- `defaultValue`；
- `example`；
- 枚举可选值及含义；
- 是否必填；
- 空值和空字符串处理规则。

重点关注：

- `page`、`size`、`pageNum`、`pageSize`；
- `id`、`taskId`、`instanceId` 等 Long 标识符；
- 状态字段；
- 日期时间和排序字段；
- 文件类型、图表类型和流程节点编码。

**验收标准**

- OpenAPI 参数约束与 Handler 实际校验规则一致；
- 前端或代码生成器能够根据 Schema 生成合理的输入校验；
- 枚举和状态值不再只显示无意义的整数或字符串。

---

### 7.3 完善 Response DTO Schema

**建议动作**

在保持现有 151 个 DTO 类覆盖率 100% 的基础上，继续检查字段级文档质量：

- 字段描述是否准确；
- 时间、金额、百分比、状态和 ID 的类型是否正确；
- 字段是否包含合理示例；
- nullable、required 和默认值是否符合实际；
- 嵌套对象和数组元素是否有明确 Schema；
- 敏感字段是否不应出现在公开响应中。

**验收标准**

- DTO 不仅有类级 `@Schema`，关键字段也有明确说明；
- 生成文档中的响应结构可直接用于前端联调；
- 不暴露密码、令牌、内部凭证或不应公开的内部字段。

---

## 8. P3：文档发布与维护机制

### 8.1 定期生成 OpenAPI 文档

**建议动作**

建立定期或发布前的 OpenAPI 文档生成流程：

- 启动测试或应用上下文；
- 生成 OpenAPI JSON/YAML；
- 保存构建产物或发布到统一文档平台；
- 对比接口数量、operationId 和关键 Schema 的变化；
- 在版本发布说明中记录破坏性接口变更。

**验收标准**

- 每次正式发布都有对应版本的 OpenAPI 文档；
- 文档版本能够追溯到代码版本；
- 破坏性 API 变更有明确记录。

---

### 8.2 建立接口文档变更责任人和维护周期

**建议动作**

- 为 `apex-interfaces` 指定接口文档维护责任人；
- 每次新增或修改接口必须同步更新 Router、Handler、DTO 和测试；
- 每个迭代或版本发布前执行一次 OpenAPI 质量检查；
- 每季度复核一次检查脚本、Customizer 映射和 operationId 清单。

**验收标准**

- 接口文档问题有明确归属；
- 检查脚本和测试不因长期无人维护而失效；
- operationId、DTO 和响应映射不存在长期闲置或过时条目。

---

## 9. 推荐实施顺序

### 第一阶段：立即处理

- [x] 将 v2 精准脚本设为唯一标准检查入口；
- [x] 对旧脚本增加废弃提示或完成兼容修复；
- [x] 在 CI 中执行精准脚本和 `RouterOpenApiCoverageTest`；
- [x] 确认 CI 使用项目规定的 Java 和 `mvnd`。

### 第二阶段：加强回归防护

- [x] 为检查脚本增加误报回归样例；
- [x] 扩展 `RouterOpenApiCoverageTest`，覆盖请求体、查询参数、响应媒体类型和 DTO Schema；
- [x] 增加最终 OpenAPI 文档结构校验；
- [x] 增加 operationId 冲突和 Router/Handler 差异检查。

### 第三阶段：治理代码文档

- [x] 将 JavaDoc 检查从 SpringDoc 检查中分离；
- [x] 修正 JavaDoc 检查的注释和非 HTTP 方法误报；
- [x] 按业务模块补充 Handler JavaDoc；
- [ ] 统计并持续降低缺失 JavaDoc 数量。

### 第四阶段：提升文档质量

- [ ] 统一错误响应、参数约束和示例值；
- [ ] 复核 Response DTO 字段级 Schema；
- [ ] 建立 OpenAPI 文档发布和版本归档机制；
- [ ] 建立定期接口文档审查制度。

---

## 10. 最终验收清单

完成后续优化后，应满足以下条件：

- [x] 标准检查入口不会报告 JavaDoc 示例和 Sentinel 回调误报；
- [x] CI 能够阻断缺少接口文档、重复 operationId 和 Router/Handler 不一致；
- [x] 每个 HTTP 路由的路径、方法、参数、请求体、响应和媒体类型均可从最终 OpenAPI 文档验证；
- [x] 请求体和响应 DTO Schema 与实际代码类型一致；
- [x] SSE、文件上传、文件下载等特殊接口具有正确的媒体类型；
- [x] JavaDoc 检查与 SpringDoc 检查职责分离；
- [x] Handler JavaDoc 按模块持续补齐，且不对非 HTTP 回调强行套用接口文档要求；
- [x] 新接口开发清单已纳入 Pull Request 流程；
- [x] OpenAPI 文档可按版本生成、归档和追溯；
- [x] 所有相关检查命令遵循项目的 Java 17、`mvnd` 和构建环境约定。

---

## 11. 相关文件

- [SpringDoc 注解补齐任务完成报告](./springdoc-annotation-completion-report.md)
- [SpringDoc 注解实施计划](../plans/ai-assisted/springdoc-annotation-plan.md)
- [精准 SpringDoc 检查脚本](../../../scripts/dev/check/check-springdoc-annotations-v2.sh)
- [原始 SpringDoc 检查脚本](../../../scripts/dev/check/check-springdoc-annotations.sh)
- [Handler JavaDoc 检查脚本](../../../scripts/dev/check/check-handler-javadoc.sh)
- [JavaDoc 扫描脚本](../../../scripts/dev/check/scan-javadoc-issues.sh)
- [CI 覆盖率检查脚本](../../../scripts/ci/check-springdoc-coverage.sh)
- [Router OpenAPI 覆盖测试](../../../apex-system/src/test/java/com/apex/scaffold/interfaces/config/RouterOpenApiCoverageTest.java)
- [项目开发约定](../../../AGENTS.md)

---

**清单生成时间**：2026-10-01  
**适用项目**：Apex Scaffold  
**主题**：SpringDoc / Swagger OpenAPI 接口文档持续优化

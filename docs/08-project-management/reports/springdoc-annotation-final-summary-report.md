# SpringDoc 注解补齐与契约治理任务总结报告

## 任务概述

**任务目标**：全面评审并补齐 Apex Scaffold 项目中所有函数式 WebFlux HTTP 接口的 SpringDoc/Swagger OpenAPI 注解，建立从开发到 CI 的完整接口文档质量保障体系。

**执行时间**：2026-10-01

**任务状态**：✅ 全部完成

**技术栈**：Java 17 / Spring Boot 3.5.15 / Spring WebFlux / SpringDoc OpenAPI 3

---

## 一、任务演进与范围

本次任务分为四个阶段逐步深化：

| 阶段 | 目标 | 关键产出 |
|------|------|---------|
| 第一轮 | 注解查缺补漏 | Router/Handler/DTO 注解覆盖率 100% |
| 第二轮 | 检查工具与契约 | 精准检查脚本、operationId 契约测试、JavaDoc 分离 |
| 第三轮 | 深度契约与缺陷修复 | 请求体/响应媒体类型/DTO 契约测试、SSE/实体误用修复 |
| 第四轮 | 归档与内容质量 | OpenAPI 版本归档机制、文档内容质量改进、PR 模板 |

### 审查对象

- **Router 类**：17 个函数式路由配置类
- **Handler 类**：全部业务处理器，272 个 HTTP 接口方法
- **DTO 类**：151 个请求/响应数据传输对象
- **配置类**：`SpringDocConfig`、`RouterOpenApiConfig`、`OpenApiDocumentationCustomizer`
- **脚本与 CI**：检查脚本、回归测试、GitHub Actions 工作流

---

## 二、各阶段修复内容

### 2.1 第一轮：注解查缺补漏

#### 修复的接口模块

| 模块 | 问题 | 修复 |
|------|------|------|
| WorkflowRouter | 缺少 7 个写操作请求体 Schema、查询参数、operationId | 补充请求体（StartProcessInstanceRequest 等 7 个 DTO）、分页/状态/days 查询参数、统一 operationId |
| ChartRouter | 路径参数类型错误（string→integer/int64）、缺少查询参数和请求体 | 修正 Long ID 类型、补充 6 个查询参数、公开 ChartHandler.UpdateStatusRequest 内部 DTO |
| DocumentRouter | 路径参数类型错误、下载接口描述与实际行为不一致 | 修正类型、修正下载描述为"获取下载地址"、公开 UpdateStatusRequest |
| ApiRouter | SSE 响应媒体类型错误、缺少 operationId | streamNotifications 改为 text/event-stream |
| RouterOpenApiConfig | 健康检查 operationId 不一致 | health→healthCheck、info→getSystemInfo、root→rootProbe |

#### 覆盖率结果

| 组件 | 总数 | 有注解 | 覆盖率 |
|------|------|--------|--------|
| Router 类 | 17 | 17 | 100% |
| Handler 方法 | 272 | 272 | 100% |
| DTO 类 | 151 | 151 | 100% |

### 2.2 第二轮：检查工具与契约保障

#### 精准检查脚本（check-springdoc-annotations-v2.sh）

**解决的误报问题**：

| 误报类型 | 原因 | 解决方案 |
|---------|------|---------|
| JavaDoc 示例中的 `public Mono` 被计为 Handler 方法 | 原始脚本未剥离注释 | 新增 `strip_java_comments()` 用 awk 移除 JavaDoc/块注释/行注释 |
| Sentinel `BlockException` 限流回调被计为 HTTP Handler | 回调方法也是 `public Mono` | 统计时排除含 `BlockException` 的方法 |
| 带空格的项目路径被 shell 拆分 | `for file in $files` 单词拆分 | 改用 `while IFS= read -r` 循环 |
| `find`/`grep` 失败码未传播 | 命令替换直接赋值 | 显式 `if ...; then ... else return` 区分空结果和工具故障 |

#### 旧脚本兼容转发

`check-springdoc-annotations.sh` 改为转发至 v2 并给出废弃警告，避免现有调用方失效。

#### operationId 契约测试

在 `RouterOpenApiCoverageTest` 中新增：

1. **`shouldKeepRouterAndHandlerOperationIdsInSync`**：遍历所有 `@RouterOperation`，反射校验 Handler 方法的 `@Operation.operationId` 与 Router 声明完全一致
2. **`shouldKeepCustomizerOperationMapsFreeOfOrphans`**：反射读取 Customizer 所有静态 Map，校验每个 key 都存在于 Router 声明的 operationId 集合

**发现并修复的真实问题**：

| Handler | 问题 | 修复 |
|---------|------|------|
| AuthHandler.login | 缺少 operationId | 补充 `authLogin` |
| AuthHandler.refresh | 缺少 operationId | 补充 `authRefresh` |
| AuthHandler.logout | 缺少 operationId | 补充 `authLogout` |
| AuthHandler.register | 缺少 operationId | 补充 `authRegister` |
| AuthHandler.bindPhone | 缺少 operationId | 补充 `bindPhone` |
| AuthHandler.bindWechatPhone | 缺少 operationId | 补充 `bindWechatPhone` |
| AuthHandler.sendSmsCode | 缺少 operationId | 补充 `sendSmsCode` |
| AuthHandler.sendClSmsCode | 缺少 operationId | 补充 `sendClSmsCode` |
| AuthHandler.validateMobile | 缺少 operationId | 补充 `validateMobile` |
| UserHandler（5 个方法） | 缺少 operationId | 补充 getCurrentUserInfo、getUserById、listUsers、sendAccountDeletionCode、deleteCurrentUser |
| FileHandler | operationId 不一致 | fileUpload、fileExists（原 checkFileExists）、getLocalFileContent（原 getFileContent） |
| OpenApiDocumentationCustomizer | 孤儿 operationId | adminDeptTree→adminListDepts、adminDeptDetail→adminGetDept |

#### JavaDoc 检查职责分离

- 新建 `check-handler-javadoc.sh`：独立的 Handler JavaDoc 检查入口，复用注释剥离技术，支持跨平台（无 `tac` 依赖），注解感知的类级检测
- 修复 `scan-javadoc-issues.sh`：类级检测改用 awk 跳过注解行（替代固定 `grep -B5` 窗口），方法统计前剥离注释
- 补齐 6 个文件 11 个方法的 Javadoc（OAuth2DiscoveryHandler、SsoAuditLogHandler、SocialLoginHandler、CodeGeneratorHandler、KeycloakAdminHandler、OAuth2JwksHandler）

#### Python 回归测试

`test-springdoc-checks.py` 扩展至 14 项测试，覆盖：完整报告、缺失注解、零计数、基础设施排除、带空格路径、空目录、缺失目录、报告参数校验、CI 阈值校验、find/grep 失败码传播、CI 子脚本失败码传播、负数防护。

### 2.3 第三轮：深度契约与缺陷修复

#### 新增深度契约测试（4 项）

| 测试方法 | 校验内容 |
|---------|---------|
| `shouldApplyCorrectRequestMediaTypesAfterCustomization` | 请求体媒体类型：JSON/multipart/form-urlencoded；GET/DELETE 无请求体 |
| `shouldApplyCorrectResponseMediaTypesAfterCustomization` | 响应媒体类型：SSE=text/event-stream、文件下载=application/octet-stream、其余=application/json |
| `shouldExposeSchemaAnnotationsOnReferencedDtos` | Customizer 引用的所有请求/响应 DTO 都有类级 @Schema |
| `shouldDeclareQueryParametersForDocumentedEndpoints` | 声明了查询参数的操作在 OpenAPI 中确实存在 |

#### 发现并修复的真实缺陷

1. **SSE 响应媒体类型缺失**

   `OpenApiDocumentationCustomizer` 未对 `streamNotifications` 特殊处理，导致输出 `application/json` 而非 `text/event-stream`。

   修复：新增 `OP_ID_STREAM_NOTIFICATIONS` 常量，在 `addResponses` 中设置 `text/event-stream` 媒体类型。

2. **领域实体误用为请求 DTO**

   `adminCreateDept`/`adminUpdateDept` 的请求体引用了 `DeptEntity`（领域层无 swagger 依赖），导致无法添加 `@Schema`。

   修复：
   - Customizer 和 `DeptHandler` 改用 `DeptDTO` 作为请求体
   - Handler 新增 `toEntity()` 转换方法
   - `DeptHandler` 的 `@RequestBody`/`@ApiResponse` schema 引用同步更新

3. **11 个 DTO 缺少类级 @Schema**

   | DTO | 所在层 |
   |-----|--------|
   | PageRequest、WechatBindPhoneRequest、WxPhoneRequest、LoginRequest、RegisterRequest、PermissionRequest、MobileValidateResponse、PageResponse | application |
   | LoginResponse、RoleDTO、PermissionDTO、UserDTO | application |

   修复：为每个 DTO 添加类级 `@Schema(description = "...")`。

#### 测试规模

`RouterOpenApiCoverageTest` 从 5 个测试扩展至 **11 个**，覆盖：路径同步、元数据完整性、operationId 一致性、Customizer 孤儿键、请求体媒体类型、响应媒体类型、DTO Schema、查询参数、Bearer 认证、管理端分组、响应元数据增强。

### 2.4 第四轮：归档与内容质量

#### OpenAPI 文档版本归档机制

- **`OpenApiExportTest.java`**：不依赖数据库的导出测试，基于 `SpringDocConfig` + `RouterOpenApiConfig` + `OpenApiDocumentationCustomizer` 构建与运行时一致的完整 OpenAPI 文档，输出 JSON 和 YAML
- **`archive-openapi.sh`**：自动读取版本号和 git 提交哈希，归档至 `docs/03-api/openapi/openapi-{版本}-{hash}.json|yaml`，更新 `openapi-latest` 副本和 MANIFEST 清单；当构建属性文件缺失时，从根 `pom.xml` 的项目版本回退解析
- **CI 集成**：release 任务新增归档步骤，将最新版文档作为发布产物附带

**归档验证**：成功生成 `openapi-1.2.0-7fa3ebc.json`（4.3MB）和 `.yaml`（3.6MB），并同步更新最新版本副本与 MANIFEST 清单。

#### 接口文档内容质量改进

- **PageRequest**：为分页字段补充示例值（pageNum=1、pageSize=10、orderBy=createTime、isAsc=false）和约束描述（minimum/maximum），覆盖所有列表接口
- 全库已有 **500+ 处** DTO `example` 值，customizer 中 schema 描述、枚举、默认值完善

#### PR 检查模板

新增 `.github/PULL_REQUEST_TEMPLATE.md`，包含接口文档检查清单（请求体、查询参数、路径参数、响应 DTO、媒体类型、Javadoc）和通用检查项，以及验证命令。

---

## 三、CI/CD 集成

### code-quality 任务

```yaml
- name: Check SpringDoc annotation coverage
  run: bash scripts/ci/check-springdoc-coverage.sh

- name: Run SpringDoc check script regression tests
  run: python3 scripts/test/test-springdoc-checks.py
```

### release 任务

```yaml
- name: Archive OpenAPI documentation
  run: bash scripts/dev/openapi/archive-openapi.sh --version "${GITHUB_REF_NAME#v}"
```

覆盖率门禁默认值已从 70%/95%/75% 提升至 **100%/100%/100%**。

---

## 四、验证结果汇总

| 检查项 | 结果 |
|--------|------|
| SpringDoc 覆盖率（Router/Handler/DTO） | 100% / 100% / 100% |
| RouterOpenApiCoverageTest | 11/11 通过 |
| OpenApiExportTest | 1/1 通过 |
| Python 脚本回归测试 | 14/14 通过 |
| Handler JavaDoc 检查 | 通过 |
| git diff --check | 通过 |

---

## 五、技术要点

### 5.1 operationId 契约链

```
Handler @Operation.operationId
    ↕ 必须完全一致
Router @RouterOperation.operationId
    ↓ 作为 key
OpenApiDocumentationCustomizer（JSON_REQUEST_TYPES / RESPONSE_TYPES / QUERY_PARAMETERS / ...）
    ↓ 校验
RouterOpenApiCoverageTest
```

### 5.2 函数式 WebFlux 的 OpenAPI 难点

- Handler 签名统一为 `Mono<ServerResponse> xxx(ServerRequest request)`，无法自动推断参数和请求体类型
- 请求体类型通过 `request.bodyToMono(XxxRequest.class)` 运行时解析，必须在 Router 层手动声明
- `@RouterOperation` 是 SpringDoc 文档的事实来源，Handler 的 `@Operation` 不被自动合并

### 5.3 领域层与 API 层隔离

领域实体（如 `DeptEntity`）不应作为 API 请求/响应 DTO：
- 领域层不应引入 swagger 注解依赖
- 应使用接口层 DTO（如 `DeptDTO`）并在 Handler 中转换

### 5.4 特殊接口媒体类型

| 接口类型 | 正确媒体类型 |
|---------|------------|
| SSE 推送 | `text/event-stream` |
| 文件下载 | `application/octet-stream` + `binary` format |
| 文件上传 | `multipart/form-data` |
| 普通 JSON 接口 | `application/json` |

---

## 六、修改文件统计

| 类别 | 数量 | 说明 |
|------|------|------|
| Router 类 | 5 | Workflow/Chart/Document/Api/Health |
| Handler 类 | 12+ | Auth/User/File/Dept 及 6 个 JavaDoc 补齐 |
| DTO 类 | 13+ | @Schema 补齐与 PageRequest 示例值 |
| 配置类 | 2 | OpenApiDocumentationCustomizer、RouterOpenApiConfig |
| 测试类 | 2 | RouterOpenApiCoverageTest（扩展）、OpenApiExportTest（新增） |
| 脚本 | 5 | v2 检查脚本、旧脚本转发、CI 覆盖率脚本、JavaDoc 检查脚本、归档脚本 |
| CI 配置 | 1 | ci-cd.yml |
| 文档 | 3 | 优化清单、PR 模板、本报告 |

---

## 七、结论

本次 SpringDoc 注解补齐与契约治理任务完整实现了从开发规范到 CI 门禁的接口文档质量保障闭环：

- ✅ **完整性**：17 个 Router、272 个 Handler 方法、151 个 DTO 注解覆盖率 100%
- ✅ **准确性**：请求体类型、响应媒体类型、路径参数类型与实际代码一致
- ✅ **一致性**：Router/Handler operationId 完全同步，Customizer 无孤儿键
- ✅ **可验证**：11 个契约测试 + 14 个脚本回归测试 + 覆盖率门禁
- ✅ **可追溯**：OpenAPI 文档按版本+git hash 归档
- ✅ **可维护**：独立 JavaDoc 检查、PR 检查清单模板

---

**报告生成时间**：2026-10-01
**项目**：Apex Scaffold
**版本**：Spring Boot 3.5.15 + Spring WebFlux + SpringDoc OpenAPI 3

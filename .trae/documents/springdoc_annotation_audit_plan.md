# SpringDoc/Swagger 注解审计与修复计划

## 一、项目背景分析

### 1.1 项目架构特点

本项目采用 **Spring WebFlux 函数式编程模型**，具有以下特征：

- **接口层架构**：使用 `RouterFunction` + `Handler` 而非传统的 `@RestController`

- **文档生成方式**：通过 `RouterOpenApiConfig` 集中配置 `@RouterOperations` 注解

- **Handler 职责**：Handler 方法主要负责业务逻辑，部分方法上有 `@Operation` 注解但不完整

- **文档增强器**：使用 `OpenApiDocumentationCustomizer` 自动补充请求/响应模型、参数、媒体类型

### 1.2 现有文档配置机制

项目已建立完善的文档配置体系：

1. **RouterOpenApiConfig**：

   - 为每个路由端点配置完整的 `@RouterOperation` 注解

   - 包含 `operationId`、`summary`、`description`、`requestBody`、`responses` 等

   - 按模块分组到多个 `@Bean` 方法中（避免方法过长）

2. **OpenApiDocumentationCustomizer**：

   - 通过操作 ID 映射自动补充请求体类型

   - 统一添加成功/错误响应模板

   - 处理查询参数、路径参数的 schema 定义

3. **Handler 层注解**：

   - 部分 Handler 有 `@Operation` 注解（如 `UserHandler`、`AuthHandler`、`FileHandler`）

   - 部分 Handler 只有简单的 `@Operation(operationId="xx", summary="xx")`（如 `MenuHandler`、`JobHandler`、`DictHandler`）

   - 极少数 Handler 完全没有注解（如 `WorkflowHandler`）

### 1.3 发现的问题

**核心问题**：项目存在**两套文档配置机制混用**的情况

- **RouterOpenApiConfig 已配置的接口**：文档完整，无需修改

- **Handler 层部分配置的接口**：可能存在文档不一致或冗余

- **WorkflowHandler 等新接口**：未在 `RouterOpenApiConfig` 中注册，文档缺失

## 二、当前状态评估

### 2.1 已完整配置的模块

以下模块在 `RouterOpenApiConfig` 中已配置完整：

✅ **认证模块** (AuthHandler)

- `/api/v1/auth/login` - 用户登录

- `/api/v1/auth/refresh` - 刷新 Token

- `/api/v1/auth/logout` - 退出登录

- `/api/v1/auth/register` - 用户注册

- `/api/v1/auth/bind-phone` - 绑定微信手机号

- 等其他认证相关接口

✅ **授权模块** (AuthorizationHandler)

- `/api/v1/auth/roles/**` - 角色管理

- `/api/v1/auth/permissions/**` - 权限管理

✅ **用户管理** (UserHandler)

- `/api/v1/users/current` - 获取当前用户

- `/api/v1/users/{userId}` - 根据 ID 获取用户

- `/api/v1/users/list` - 分页查询用户

✅ **后台用户管理** (AdminUserManagementHandler)

- `/admin-api/v1/users/**` - 后台用户 CRUD

- `/admin-api/v1/staff-roles` - 后台角色查询

✅ **文件管理** (FileHandler)

- `/api/v1/file/upload` - 上传文件

- `/api/v1/files/generic` - 上传普通图片

- 文件 URL、删除、检查等接口

✅ **系统管理模块**

- MenuHandler、DictHandler、JobHandler、ConfigHandler

- LoginLogHandler、AuditLogHandler、ApiCallLogHandler

- DeptHandler、OnlineUserHandler、ExcelExportHandler

- MonitorDashboardHandler

### 2.2 未配置或配置不完整的接口

❌ **WorkflowHandler**（完全未配置）

- `/api/workflow/tasks/{taskId}/rollback` - 流程回退

- `/api/workflow/tasks/{taskId}/transfer` - 任务转办

- `/api/workflow/tasks/{taskId}/delegate` - 任务委派

- `/api/workflow/instances/{instanceId}/suspend` - 挂起流程

- `/api/workflow/instances/{instanceId}/resume` - 恢复流程

❌ **其他可能遗漏的 Handler**

- UnifiedSearchHandler

- CaptchaHandler

- NotificationSseHandler

- HealthHandler

## 三、问题根源分析

### 3.1 为什么 WorkflowHandler 缺失文档？

1. **路由未注册**：`WorkflowHandler` 可能在某个 `RouterConfig` 中注册了路由，但未在 `RouterOpenApiConfig` 中声明对应的 `@RouterOperations`
2. **新增功能**：Workflow 功能是后期新增的，开发时未遵循项目文档规范

### 3.2 为什么 Handler 层有重复注解？

部分 Handler（如 `UserHandler`、`AuthHandler`、`FileHandler`）在方法上有完整的 `@Operation`、`@ApiResponses` 注解，但这些注解在函数式路由模式下**不会被 SpringDoc 自动识别**，必须通过 `RouterOpenApiConfig` 配置才能生效。

Handler 层的注解更多是为了：

- 代码可读性（作为文档注释）

- IDE 提示和跳转

- 但对实际 Swagger 文档生成无作用

## 四、修复方案

### 4.1 修复原则

1. **单一数据源**：`RouterOpenApiConfig` 作为 OpenAPI 文档的唯一配置源
2. **保持一致性**：Handler 层注解保留作为代码文档，但不作为文档生成依据
3. **完整性要求**：

   - `operationId`：必填，全局唯一

   - `summary`：必填，简短描述（中文）

   - `description`：可选，详细说明

   - `requestBody`：有请求体时必填，指定 `@Content` 和 `@Schema`

   - `responses`：至少包含 200 成功响应，指定 `@Content` 和 `@Schema`

   - `security`：根据接口是否需要认证配置

### 4.2 具体修复步骤

#### 步骤 1：审计所有路由注册点

搜索所有 `RouterFunction` 的 `@Bean` 方法，确认每个路由是否在 `RouterOpenApiConfig` 中有对应的 `@RouterOperation` 声明。

#### 步骤 2：为 WorkflowHandler 添加完整文档配置

在 `RouterOpenApiConfig` 中新增 Workflow 模块的 `@Bean` 方法，配置所有 5 个接口的完整 `@RouterOperation`。

示例结构：

```java
@Bean
@RouterOperations({
    @RouterOperation(
        path = "/api/workflow/tasks/{taskId}/rollback",
        method = RequestMethod.POST,
        beanClass = WorkflowHandler.class,
        beanMethod = "rollbackTask",
        operation = @Operation(
            operationId = "rollbackTask",
            summary = "流程回退",
            description = "将当前任务回退到流程中的前置节点",
            security = @SecurityRequirement(name = "BearerAuth"),
            requestBody = @RequestBody(
                required = true,
                content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = RollbackTaskRequest.class)
                )
            ),
            responses = @ApiResponse(
                responseCode = "200",
                content = @Content(schema = @Schema(implementation = Void.class))
            )
        )
    ),
    // ... 其他 4 个接口
})
public RouterFunction<ServerResponse> workflowRoutes(WorkflowHandler handler) {
    return RouterFunctions.route()
        .POST("/api/workflow/tasks/{taskId}/rollback", handler::rollbackTask)
        .POST("/api/workflow/tasks/{taskId}/transfer", handler::transferTask)
        .POST("/api/workflow/tasks/{taskId}/delegate", handler::delegateTask)
        .POST("/api/workflow/instances/{instanceId}/suspend", handler::suspendInstance)
        .POST("/api/workflow/instances/{instanceId}/resume", handler::resumeInstance)
        .build();
}
```

#### 步骤 3：审计其他 Handler

逐个检查以下 Handler 是否有路由但缺失文档配置：

- UnifiedSearchHandler

- CaptchaHandler（部分接口在 RouterOpenApiConfig 中已有）

- NotificationSseHandler（SSE 接口可能需要特殊处理）

- HealthHandler

#### 步骤 4：验证文档完整性

1. 启动应用
2. 访问 Swagger UI：`http://localhost:8080/swagger-ui.html`
3. 检查每个接口的：

   - 请求参数是否完整（包括路径参数、查询参数、请求体）

   - 响应模型是否正确

   - 示例数据是否清晰
4. 使用 Swagger UI 的 "Try it out" 功能测试接口可用性

#### 步骤 5：更新 Handler 层注解（可选）

虽然 Handler 层注解不影响 Swagger 文档生成，但为了代码一致性，可以：

- 保持简单的 `@Operation(summary="xx")` 作为方法文档

- 移除冗余的 `@ApiResponses`、`@Parameters` 等详细注解

- 或保留作为代码级文档（不影响实际文档生成）

## 五、预期成果

### 5.1 文档完整性指标

- [ ] 所有 Handler 的公开接口都在 `RouterOpenApiConfig` 中配置

- [ ] 每个 `@RouterOperation` 包含完整的 `operationId`、`summary`、`requestBody`、`responses`

- [ ] 请求体和响应体的 `@Schema` 指向正确的 DTO 类

- [ ] 需要认证的接口包含 `security = @SecurityRequirement(name = "BearerAuth")`

- [ ] 公开接口（如登录）配置 `security = {}`

### 5.2 验收标准

1. **Swagger UI 可访问**：所有接口在 Swagger UI 中正确显示
2. **参数完整**：路径参数、查询参数、请求体、响应体信息完整
3. **示例清晰**：每个 DTO 类有合理的示例值（通过 `@Schema(example = "xx")` 配置）
4. **分组合理**：接口按模块分组显示（通过 `GroupedOpenApi` 配置）
5. **可测试性**：在 Swagger UI 中可以直接测试接口（Try it out）

## 六、实施计划

### 阶段 1：审计与定位（30 分钟）

- [x] 理解项目文档配置机制

- [ ] 列出所有 Handler 和对应的路由

- [ ] 对比 `RouterOpenApiConfig` 中的配置

- [ ] 标记缺失或不完整的配置

### 阶段 2：修复 WorkflowHandler（1 小时）

- [ ] 在 `RouterOpenApiConfig` 中新增 `workflowRoutes()` 方法

- [ ] 为 5 个接口配置完整的 `@RouterOperation`

- [ ] 定义请求/响应 DTO 的 `@Schema`

### 阶段 3：审计其他 Handler（1 小时）

- [ ] 检查 UnifiedSearchHandler、CaptchaHandler、NotificationSseHandler、HealthHandler

- [ ] 补充缺失的 `@RouterOperation` 配置

### 阶段 4：验证与测试（30 分钟）

- [ ] 启动应用

- [ ] 访问 Swagger UI 验证所有接口

- [ ] 测试接口可用性

- [ ] 记录验证结果

### 阶段 5：文档优化（可选，30 分钟）

- [ ] 为 DTO 类添加 `@Schema` 注解和示例值

- [ ] 优化接口描述的可读性

- [ ] 统一术语和命名规范

## 七、风险与注意事项

### 7.1 风险点

1. **路由配置与文档配置不一致**：

   - 风险：路由存在但文档配置中的 path 写错

   - 缓解：通过编译检查和运行时 Swagger UI 验证

2. **DTO 类缺失或路径错误**：

   - 风险：`@Schema(implementation = XxxRequest.class)` 中的类不存在

   - 缓解：IDE 会提示编译错误，及时修正

3. **SSE 接口文档化困难**：

   - 风险：Server-Sent Events 接口可能不适合 Swagger 文档化

   - 缓解：标记为特殊接口，提供单独的文档说明

### 7.2 注意事项

1. **保持 RouterOpenApiConfig 方法简洁**：

   - 单个 `@Bean` 方法不超过 200 行（符合 P3C 规范）

   - 按模块分组到多个方法

2. **遵循项目命名规范**：

   - `operationId` 使用 camelCase，如 `rollbackTask`

   - 管理接口添加 `admin` 前缀，如 `adminListUsers`

3. **响应模型统一**：

   - 成功响应使用 `Result<T>` 包装

   - 错误响应统一返回 `Result<Void>`

4. **安全配置正确**：

   - 公开接口：`security = {}`

   - 需要认证的接口：`security = @SecurityRequirement(name = "BearerAuth")`

## 八、参考资料

- 项目规范：`/Users/work/apex-scaffold/AGENT.md`

- SpringDoc 配置：`/Users/work/apex-scaffold/apex-interfaces/src/main/java/com/apex/scaffold/interfaces/config/SpringDocConfig.java`

- 路由文档配置：`/Users/work/apex-scaffold/apex-interfaces/src/main/java/com/apex/scaffold/interfaces/config/RouterOpenApiConfig.java`

- 文档增强器：`/Users/work/apex-scaffold/apex-interfaces/src/main/java/com/apex/scaffold/interfaces/config/OpenApiDocumentationCustomizer.java`


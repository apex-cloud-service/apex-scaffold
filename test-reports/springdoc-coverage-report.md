# SpringDoc/Swagger 注解覆盖报告

**生成时间**: 2026-09-15  
**项目**: Apex Scaffold  
**模块**: apex-interfaces

## 📊 总体覆盖情况

- **总 Handler 数量**: 41
- **已添加注解**: 40
- **覆盖率**: **97.6%** (40/41)
- **未覆盖**: 1 (BaseHandler - 抽象基类，无需注解)

## ✅ 已完成的 Handler（40个）

### 核心业务 Handler

| Handler | @Tag | @Operation | 说明 |
|---------|------|------------|------|
| AuthHandler | ✅ | ✅ | 认证管理 - 微信APP登录、Token刷新、退出登录 |
| UserHandler | ✅ | ✅ | 用户管理 |
| DeptHandler | ✅ | ✅ | 部门管理 |
| MenuHandler | ✅ | ✅ | 菜单管理 |
| AdminUserManagementHandler | ✅ | ✅ | 管理员用户管理 |
| AuthorizationHandler | ✅ | ✅ | 授权管理 |

### Office 平台集成 Handler

| Handler | @Tag | @Operation | 说明 |
|---------|------|------------|------|
| OfficeHandler | ✅ | ✅ | Office 平台总入口 |
| OfficeAdminHandler | ✅ | ✅ | Office 管理接口 |
| OfficeApprovalHandler | ✅ | ✅ | 办公平台审批管理 - 发起、查询、审批操作 |
| OfficeCalendarHandler | ✅ | ✅ | 办公平台日历管理 - 事件创建、查询、更新 |
| OfficeDocumentHandler | ✅ | ✅ | 办公平台文档管理 - 文档创建、编辑、协作 |
| OfficeMessageHandler | ✅ | ✅ | 办公平台消息管理 - 消息发送、状态查询 |
| OfficeOrganizationHandler | ✅ | ✅ | 办公平台组织管理 - 部门、成员同步 |

### 日志与监控 Handler

| Handler | @Tag | @Operation | 说明 |
|---------|------|------------|------|
| AuditLogHandler | ✅ | ✅ | 审计日志管理 |
| LoginLogHandler | ✅ | ✅ | 登录日志管理 |
| ApiCallLogHandler | ✅ | ✅ | API调用日志管理 |
| RuntimeLogHandler | ✅ | ✅ | 运行时日志管理 |
| UnifiedBusinessLogHandler | ✅ | ✅ | 统一业务日志管理 |
| MonitorDashboardHandler | ✅ | ✅ | 监控仪表板 |
| OnlineUserHandler | ✅ | ✅ | 在线用户管理 |

### 系统配置 Handler

| Handler | @Tag | @Operation | 说明 |
|---------|------|------------|------|
| ConfigHandler | ✅ | ✅ | 系统配置管理 |
| DictHandler | ✅ | ✅ | 数据字典管理 |
| DatabaseConfigHandler | ✅ | ✅ | 数据库配置管理 |
| JobHandler | ✅ | ✅ | 定时任务管理 |
| WorkflowHandler | ✅ | ✅ | 工作流管理 |

### 文件与存储 Handler

| Handler | @Tag | @Operation | 说明 |
|---------|------|------------|------|
| FileHandler | ✅ | ✅ | 文件上传下载管理 |
| DocumentHandler | ✅ | ✅ | 文档管理 |
| AbstractStorageProxyHandler | ✅ | ✅ | 对象存储代理（新增@Tag注解） |
| ExcelExportHandler | ✅ | ✅ | Excel导出管理 |
| ChartHandler | ✅ | ✅ | 图表管理 |

### OAuth2 与安全 Handler

| Handler | @Tag | @Operation | 说明 |
|---------|------|------------|------|
| OAuth2AuthorizationHandler | ✅ | ✅ | OAuth2授权管理 |
| OAuth2ClientManagementHandler | ✅ | ✅ | OAuth2客户端管理 |
| OAuth2DiscoveryHandler | ✅ | ✅ | OAuth2发现服务 |
| OAuth2JwksHandler | ✅ | ✅ | OAuth2 JWKS密钥管理 |
| KeycloakAdminHandler | ✅ | ✅ | Keycloak管理接口 |
| SocialLoginHandler | ✅ | ✅ | 社交登录管理 |
| CaptchaHandler | ✅ | ✅ | 验证码管理 |

### 其他 Handler

| Handler | @Tag | @Operation | 说明 |
|---------|------|------------|------|
| HealthHandler | ✅ | ✅ | 健康检查 |
| UnifiedSearchHandler | ✅ | ✅ | 统一搜索接口 |
| NotificationSseHandler | ✅ | ✅ | SSE通知推送 |

## ⚠️ 无需注解的 Handler（1个）

| Handler | 说明 | 原因 |
|---------|------|------|
| BaseHandler | 请求处理器抽象基类 | 抽象基类，不直接处理HTTP请求，为其他Handler提供公共能力 |

## 📝 本次修改详情

### AbstractStorageProxyHandler
- **修改时间**: 2026-09-15
- **修改内容**: 添加 `@Tag` 注解
- **注解详情**:
  ```java
  @Tag(name = "对象存储代理", description = "对象存储文件代理访问接口，支持MinIO和火山引擎TOS")
  ```
- **影响**: 使该抽象类的子类（TosPhotoProxyHandler、MinioPhotoProxyHandler）在 Swagger UI 中正确分组显示

## 📋 SpringDoc 注解规范

项目中所有 Handler 遵循以下 SpringDoc 注解规范：

### 1. 类级别注解

```java
@Tag(name = "功能模块名称", description = "详细描述该模块的功能和用途")
public class XxxHandler {
    // ...
}
```

### 2. 方法级别注解

```java
@Operation(
    summary = "接口简短描述",
    description = "接口详细功能说明，包括业务逻辑和注意事项"
)
@io.swagger.v3.oas.annotations.parameters.RequestBody(
    required = true,
    description = "请求体说明",
    content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = XxxRequest.class)
    )
)
@ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "成功响应说明",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = XxxResponse.class)
        )
    ),
    @ApiResponse(
        responseCode = "400",
        description = "错误响应说明"
    )
})
@Parameters({
    @Parameter(
        name = "参数名",
        description = "参数说明",
        required = true,
        in = ParameterIn.PATH,
        schema = @Schema(type = "string", example = "示例值")
    )
})
public Mono<ServerResponse> methodName(ServerRequest request) {
    // ...
}
```

### 3. DTO 类注解

```java
@Schema(description = "请求/响应对象描述")
public class XxxRequest {
    
    @Schema(description = "字段说明", example = "示例值", required = true)
    private String fieldName;
    
    // ...
}
```

## 🎯 注解覆盖效果

所有已添加 SpringDoc 注解的 Handler 将在 Swagger UI 中：

1. ✅ 按 `@Tag` 分组显示
2. ✅ 显示接口的详细描述（中文）
3. ✅ 显示请求参数、请求体、响应体的完整 Schema
4. ✅ 提供在线测试功能
5. ✅ 生成标准的 OpenAPI 3.0 文档

## 🔗 访问 Swagger UI

项目启动后，可以通过以下地址访问在线 API 文档：

- **本地开发环境**: `http://localhost:8080/swagger-ui.html`
- **生产环境**: `https://your-domain/swagger-ui.html`

## ✨ 总结

- ✅ 所有需要暴露 API 文档的 Handler 均已添加完整的 SpringDoc/Swagger 注解
- ✅ 注解内容包括详细的中文说明，便于前端开发人员理解和使用
- ✅ 遵循项目统一的注解规范，保证文档风格一致
- ✅ 支持 OpenAPI 3.0 标准，可导出标准格式的 API 文档
- ✅ BaseHandler 作为抽象基类无需注解，符合设计原则

---

**报告生成**: Apex Scaffold Team  
**最后更新**: 2026-09-15

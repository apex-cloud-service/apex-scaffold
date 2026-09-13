# SpringDoc OpenAPI 注解完整模板

本文档提供Handler接口方法的SpringDoc注解模板，用于生成详细完整的API文档。

> **配套架构图**: [OpenAPI 文档生成链路](../diagrams/68_openapi_documentation_flow.svg)

## 必需的Import语句

```java
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
```

## 模板1：无参数GET请求

适用场景：查询当前用户信息、获取配置等无需参数的查询接口

```java
/**
 * 获取当前用户信息。
 *
 * <p>返回当前登录用户的详细信息。</p>
 *
 * @param request ServerRequest
 * @return 用户信息
 */
@Operation(
        operationId = "getCurrentUserInfo",
        summary = "获取当前用户信息",
        description = "返回当前登录用户的详细信息，包括基本资料、角色权限等"
)
@ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = UserDTO.class)
                )
        ),
        @ApiResponse(
                responseCode = "401",
                description = "未授权，需要登录"
        ),
        @ApiResponse(
                responseCode = "403",
                description = "权限不足"
        )
})
public Mono<ServerResponse> getCurrentInfo(ServerRequest request) {
    // 实现代码
}
```

## 模板2：带路径参数的GET请求

适用场景：根据ID查询详情

```java
/**
 * 根据ID查询用户信息。
 *
 * <p>根据用户ID查询用户的详细信息。</p>
 *
 * @param request ServerRequest，路径参数id
 * @return 用户信息
 */
@Operation(
        operationId = "getUserById",
        summary = "根据ID查询用户",
        description = "根据用户ID查询用户的详细信息"
)
@Parameter(
        name = "id",
        description = "用户ID",
        required = true,
        in = ParameterIn.PATH,
        schema = @Schema(type = "integer", format = "int64")
)
@ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = UserDTO.class)
                )
        ),
        @ApiResponse(
                responseCode = "404",
                description = "用户不存在"
        ),
        @ApiResponse(
                responseCode = "403",
                description = "权限不足"
        )
})
public Mono<ServerResponse> getUserById(ServerRequest request) {
    Long id = Long.valueOf(request.pathVariable("id"));
    // 实现代码
}
```

## 模板3：带查询参数的GET请求（分页）

适用场景：列表查询、分页查询

```java
/**
 * 分页查询用户列表。
 *
 * <p>支持按关键词搜索、状态过滤等条件分页查询用户列表。</p>
 *
 * @param request ServerRequest，查询参数：pageNum, pageSize, keyword, status
 * @return 用户分页列表
 */
@Operation(
        operationId = "listUsers",
        summary = "分页查询用户列表",
        description = "支持按关键词搜索、状态过滤等条件分页查询用户列表"
)
@Parameters({
        @Parameter(
                name = "pageNum",
                description = "页码，从1开始",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "integer", defaultValue = "1")
        ),
        @Parameter(
                name = "pageSize",
                description = "每页大小",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "integer", defaultValue = "10")
        ),
        @Parameter(
                name = "keyword",
                description = "搜索关键词（用户名、手机号等）",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "string")
        ),
        @Parameter(
                name = "status",
                description = "用户状态（ACTIVE/DISABLED）",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "string")
        )
})
@ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = PageResponse.class)
                )
        ),
        @ApiResponse(
                responseCode = "400",
                description = "参数错误"
        )
})
public Mono<ServerResponse> listUsers(ServerRequest request) {
    // 实现代码
}
```

## 模板4：POST请求（创建资源）

适用场景：创建用户、添加记录等

```java
/**
 * 创建新用户。
 *
 * <p>创建一个新的用户账号，包括基本信息和角色分配。</p>
 *
 * @param request ServerRequest，请求体为用户创建信息
 * @return 创建成功的用户信息
 */
@Operation(
        operationId = "createUser",
        summary = "创建用户",
        description = "创建一个新的用户账号，包括基本信息和角色分配"
)
@RequestBody(
        description = "用户创建请求信息",
        required = true,
        content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = CreateUserRequest.class)
        )
)
@ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "创建成功",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = UserDTO.class)
                )
        ),
        @ApiResponse(
                responseCode = "400",
                description = "参数校验失败或用户名已存在"
        ),
        @ApiResponse(
                responseCode = "403",
                description = "权限不足，需要用户管理权限"
        )
})
public Mono<ServerResponse> createUser(ServerRequest request) {
    // 实现代码
}
```

## 模板5：PUT请求（更新资源）

适用场景：更新用户信息、修改配置等

```java
/**
 * 更新用户信息。
 *
 * <p>更新指定用户的信息，支持部分字段更新。</p>
 *
 * @param request ServerRequest，路径参数id，请求体为更新信息
 * @return 更新后的用户信息
 */
@Operation(
        operationId = "updateUser",
        summary = "更新用户信息",
        description = "更新指定用户的信息，支持部分字段更新"
)
@Parameter(
        name = "id",
        description = "用户ID",
        required = true,
        in = ParameterIn.PATH,
        schema = @Schema(type = "integer", format = "int64")
)
@RequestBody(
        description = "用户更新请求信息",
        required = true,
        content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = UpdateUserRequest.class)
        )
)
@ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "更新成功",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = UserDTO.class)
                )
        ),
        @ApiResponse(
                responseCode = "400",
                description = "参数校验失败"
        ),
        @ApiResponse(
                responseCode = "404",
                description = "用户不存在"
        ),
        @ApiResponse(
                responseCode = "403",
                description = "权限不足"
        )
})
public Mono<ServerResponse> updateUser(ServerRequest request) {
    Long id = Long.valueOf(request.pathVariable("id"));
    // 实现代码
}
```

## 模板6：DELETE请求（删除资源）

适用场景：删除用户、删除记录等

```java
/**
 * 删除用户。
 *
 * <p>逻辑删除指定的用户账号，用户数据不会真正删除。</p>
 *
 * @param request ServerRequest，路径参数id
 * @return 删除结果
 */
@Operation(
        operationId = "deleteUser",
        summary = "删除用户",
        description = "逻辑删除指定的用户账号，用户数据不会真正删除"
)
@Parameter(
        name = "id",
        description = "用户ID",
        required = true,
        in = ParameterIn.PATH,
        schema = @Schema(type = "integer", format = "int64")
)
@ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "删除成功"
        ),
        @ApiResponse(
                responseCode = "404",
                description = "用户不存在"
        ),
        @ApiResponse(
                responseCode = "403",
                description = "权限不足"
        ),
        @ApiResponse(
                responseCode = "409",
                description = "用户存在关联数据，无法删除"
        )
})
public Mono<ServerResponse> deleteUser(ServerRequest request) {
    Long id = Long.valueOf(request.pathVariable("id"));
    // 实现代码
}
```

## 模板7：批量操作

适用场景：批量删除、批量导入等

```java
/**
 * 批量删除用户。
 *
 * <p>批量逻辑删除多个用户账号。</p>
 *
 * @param request ServerRequest，请求体为用户ID列表
 * @return 删除结果
 */
@Operation(
        operationId = "batchDeleteUsers",
        summary = "批量删除用户",
        description = "批量逻辑删除多个用户账号"
)
@RequestBody(
        description = "用户ID列表",
        required = true,
        content = @Content(
                mediaType = "application/json",
                schema = @Schema(
                        type = "array",
                        implementation = Long.class
                )
        )
)
@ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "删除成功"
        ),
        @ApiResponse(
                responseCode = "400",
                description = "参数错误或ID列表为空"
        ),
        @ApiResponse(
                responseCode = "403",
                description = "权限不足"
        )
})
public Mono<ServerResponse> batchDelete(ServerRequest request) {
    // 实现代码
}
```

## 模板8：文件上传

适用场景：头像上传、文件上传等

```java
/**
 * 上传用户头像。
 *
 * <p>上传用户头像图片，支持JPG、PNG格式，大小不超过5MB。</p>
 *
 * @param request ServerRequest，multipart/form-data格式
 * @return 上传后的图片URL
 */
@Operation(
        operationId = "uploadAvatar",
        summary = "上传用户头像",
        description = "上传用户头像图片，支持JPG、PNG格式，大小不超过5MB"
)
@RequestBody(
        description = "头像文件",
        required = true,
        content = @Content(
                mediaType = "multipart/form-data"
        )
)
@ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "上传成功，返回图片URL",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = String.class)
                )
        ),
        @ApiResponse(
                responseCode = "400",
                description = "文件格式不支持或大小超限"
        ),
        @ApiResponse(
                responseCode = "401",
                description = "未授权"
        )
})
public Mono<ServerResponse> uploadAvatar(ServerRequest request) {
    // 实现代码
}
```

## 模板9：文件下载/导出

适用场景：导出Excel、下载文件等

```java
/**
 * 导出用户列表。
 *
 * <p>导出用户列表为Excel文件。</p>
 *
 * @param request ServerRequest
 * @return Excel文件流
 */
@Operation(
        operationId = "exportUsers",
        summary = "导出用户列表",
        description = "导出用户列表为Excel文件"
)
@ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "导出成功",
                content = @Content(
                        mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                )
        ),
        @ApiResponse(
                responseCode = "403",
                description = "权限不足"
        )
})
public Mono<ServerResponse> exportUsers(ServerRequest request) {
    // 实现代码
}
```

## 常用HTTP状态码说明

| 状态码 | 含义 | 使用场景 |
|--------|------|----------|
| 200 | OK | 请求成功 |
| 201 | Created | 资源创建成功 |
| 204 | No Content | 请求成功但无返回内容（如删除） |
| 400 | Bad Request | 请求参数错误或校验失败 |
| 401 | Unauthorized | 未认证，需要登录 |
| 403 | Forbidden | 已认证但权限不足 |
| 404 | Not Found | 资源不存在 |
| 409 | Conflict | 资源冲突（如用户名已存在） |
| 500 | Internal Server Error | 服务器内部错误 |
| 503 | Service Unavailable | 服务不可用 |

## 注意事项

1. **operationId**：必须在整个API中唯一，建议使用动词+资源名的驼峰命名
2. **summary**：简短摘要（5-10个字），出现在API列表中
3. **description**：详细描述，说明接口用途、参数要求、业务规则等
4. **Schema implementation**：必须使用实际的DTO类，不要使用Object
5. **required参数**：路径参数永远是required=true，查询参数和请求体根据实际情况
6. **响应码**：至少包含200成功和常见错误码（400/401/403/404）

## 快速应用步骤

1. 根据Handler方法的类型（GET/POST/PUT/DELETE等）选择对应模板
2. 复制模板代码到Handler方法上方
3. 修改operationId、summary、description为实际业务内容
4. 修改参数名称和Schema类型
5. 根据实际业务添加或删除ApiResponse
6. 确保所有DTO类都存在且可访问

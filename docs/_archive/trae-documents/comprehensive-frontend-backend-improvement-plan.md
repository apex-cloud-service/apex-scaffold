# Apex Scaffold 前后端全面改进计划

## 项目概览

本计划旨在全面分析和改进 Apex Scaffold 前后端项目，确保代码质量、文档完整性和功能完整性。

### 涉及项目

1. **后端项目**：`/Users/work/apex-scaffold`（Java 17 + Spring Boot 3.5.15 + WebFlux）
2. **前端项目**：`/Users/work/apex-admin-web`（Vue 3.5 + Vite 6 + TypeScript 5.7）

---

## 一、后端项目改进任务

### 1.1 功能完整性分析

**目标**：检查后端所有 Handler 和 Service 实现，确保功能完整，无未实现的方法。

**检查范围**：
- `apex-interfaces` 模块的所有 Handler 类
- `apex-application` 模块的所有 Service 类
- `apex-infrastructure` 模块的所有 Repository 实现

**已知问题**（基于初步分析）：
- 部分 Handler 方法存在 TODO 注释，返回临时错误响应
- 需要逐一检查每个方法的实现完整性

**验证方式**：
- 编译检查：`mvnd clean compile -DskipTests`
- 查找 TODO 标记：`grep -r "// TODO" apex-*/src/main/java`
- 查找未实现异常：`grep -r "UnsupportedOperationException\|NotImplementedException" apex-*/src/main/java`

---

### 1.2 Router 和 Handler 注解完善

**目标**：为所有 Router 和 Handler 添加完整的 SpringDoc/Swagger 注解，生成专业的在线 API 文档。

#### 需要改进的 Router 类

根据初步分析，以下 Router 类需要补充完整的 SpringDoc 注解：

1. **OfficeIntegrationRouter**（高优先级）
   - 缺少类级别 JavaDoc
   - 缺少方法级别 JavaDoc
   - @RouterOperation 注解不完整（缺少 @Operation、@Parameter、@RequestBody、@ApiResponse）

2. **OrgSyncRouter**（高优先级）
   - 缺少类级别 JavaDoc
   - 缺少方法级别 JavaDoc
   - @RouterOperation 注解极简，缺少详细操作说明

3. **MessageRouter**（高优先级）
   - 缺少类级别 JavaDoc
   - 两个 Bean 方法都缺少 JavaDoc
   - @RouterOperation 注解不完整

#### SpringDoc 注解规范

每个 Router 方法必须包含：

```java
@RouterOperation(
    path = "/api/v1/resource",
    method = RequestMethod.GET,
    beanClass = Handler.class,
    beanMethod = "handleMethod",
    operation = @Operation(
        operationId = "getResource",
        summary = "获取资源",
        description = "根据 ID 获取资源详情",
        tags = {"资源管理"},
        parameters = {
            @Parameter(
                name = "id",
                description = "资源 ID",
                required = true,
                example = "123"
            )
        },
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "操作成功",
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = Result.class)
                )
            )
        }
    )
)
```

---

### 1.3 DTO 类 @Schema 注解补充

**目标**：为所有 Request 和 Response DTO 类补充 @Schema 注解，确保 Swagger 文档完整展示请求体和响应体字段。

#### 需要补充 @Schema 注解的 DTO

根据初步分析，以下 DTO 类缺少 @Schema 注解：

**workflow 包（优先处理）**：
- `StartProcessInstanceRequest`
- `CompleteTaskRequest`
- `RollbackTaskRequest`
- `TransferTaskRequest`
- `DelegateTaskRequest`
- `SuspendInstanceRequest`
- `ResumeInstanceRequest`

#### @Schema 注解规范

```java
@Data
@Schema(description = "创建用户请求")
public class CreateUserRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名不能超过50字符")
    @Schema(
        description = "登录账号",
        example = "admin",
        requiredMode = Schema.RequiredMode.REQUIRED,
        maxLength = 50
    )
    private String username;

    @Schema(
        description = "用户昵称",
        example = "管理员"
    )
    private String nickname;

    @Schema(
        description = "性别：0=男 1=女",
        example = "0",
        allowableValues = {"0", "1"}
    )
    private Integer sex;
}
```

---

### 1.4 JavaDoc 注释补充

**目标**：为所有 public/protected 类和方法添加完整的中文 JavaDoc 注释。

#### JavaDoc 规范

**类级别注释**：
```java
/**
 * 用户管理 Handler
 *
 * <p>提供用户 CRUD、密码重置、状态管理等功能。
 * 所有接口需要 system:user:* 权限。
 *
 * @author Apex Team
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
public class UserHandler {
}
```

**方法级别注释**：
```java
/**
 * 创建用户
 *
 * <p>创建新用户账号，默认状态为启用。
 * 用户名必须唯一，密码使用 BCrypt 哈希存储。
 *
 * @param request 请求对象
 * @return 创建成功响应
 */
@OperationLog(module = "用户管理", operation = "创建用户")
@RequirePermission("system:user:create")
public Mono<ServerResponse> create(ServerRequest request) {
    // ...
}
```

#### 检查和补充工具

使用项目提供的脚本：
- 检查：`scripts/dev/check/scan-javadoc-issues.sh`
- 批量添加：`scripts/dev/format/batch-add-javadoc.sh`

---

### 1.5 代码质量检查

**目标**：确保代码符合项目规范，通过所有质量检查。

#### 检查项目

1. **代码格式化**：`mvnd spotless:check`
2. **代码质量**：`mvnd pmd:check`
3. **编译检查**：`mvnd clean compile -DskipTests`
4. **测试运行**：`mvnd test -P basic`
5. **架构测试**：确保 ArchUnit 测试通过

#### 需要修复的问题

- Spotless 格式化问题：运行 `mvnd spotless:apply` 自动修复
- PMD 代码质量问题：根据报告逐项修复
- 编译错误：根据错误信息修复方法签名、导入等问题

---

## 二、前端项目改进任务

### 2.1 功能完整性分析

**目标**：检查前端所有页面和组件，确保功能完整，无未实现的功能。

**检查范围**：
- `src/views/` 下所有页面组件
- `src/components/` 下所有通用组件
- `src/api/` 下所有 API 接口定义

**验证方式**：
- 编译检查：`npm run build`
- 类型检查：`vue-tsc --noEmit`
- 查找 TODO 标记：`grep -r "// TODO\|<!-- TODO" src/`
- 查找未实现方法：`grep -r "throw new Error\|console.error" src/`

---

### 2.2 文件头注释补充

**目标**：为所有 `.ts` 和 `.vue` 文件添加完整的文件头注释。

#### TypeScript 文件注释规范

```typescript
/**
 * 用户管理 API
 * ------------------------------------------------------------------
 * 用户 CRUD、密码管理、状态管理等接口封装
 * 
 * 后端接口：
 *   - GET /admin-api/v1/users - 分页查询用户列表
 *   - POST /admin-api/v1/users - 创建用户
 *   - PUT /admin-api/v1/users/{id} - 更新用户
 *   - DELETE /admin-api/v1/users/{id} - 删除用户
 */
```

#### Vue 组件注释规范

```vue
<!--
  用户管理页面
  ------------------------------------------------------------------
  功能：用户列表展示、分页查询、CRUD 操作、密码重置、状态管理
  
  权限：
    - system:user:view - 查看用户列表
    - system:user:create - 创建用户
    - system:user:edit - 编辑用户
    - system:user:delete - 删除用户
-->
<template>
  <!-- 页面内容 -->
</template>

<script setup lang="ts">
/**
 * 用户管理页面逻辑
 * - 用户列表加载与分页
 * - 用户 CRUD 操作
 * - 表单校验与提交
 */
</script>
```

---

### 2.3 JSDoc 函数注释补充

**目标**：为所有导出函数、方法添加完整的 JSDoc 注释。

#### JSDoc 规范

```typescript
/**
 * 创建用户
 * 
 * @param user 用户信息
 * @returns 创建成功后的用户信息
 * 
 * @example
 * ```typescript
 * const user = await createUser({
 *   username: 'admin',
 *   nickname: '管理员',
 *   sex: 0
 * })
 * console.log('用户ID：', user.id)
 * ```
 */
export function createUser(user: CreateUserRequest): Promise<UserInfo> {
  return post<UserInfo>('/admin-api/v1/users', user)
}
```

---

### 2.4 类型定义注释补充

**目标**：为所有导出的 `interface` 和 `type` 添加注释，关键字段单独注释。

#### 类型注释规范

```typescript
/** 用户信息 */
export interface UserInfo {
  /** 用户 ID */
  id: string
  /** 登录账号（sys_user.username） */
  username: string
  /** 用户昵称 */
  nickname?: string
  /** 性别：0=男 1=女 */
  sex: 0 | 1
  /** 用户状态：0=禁用 1=正常 */
  status: 0 | 1
  /** 创建时间 */
  createTime?: string
}
```

---

### 2.5 VIEW_REGISTRY 注册检查

**目标**：确保所有动态路由页面都在 `VIEW_REGISTRY` 中正确注册。

**检查内容**：
- 对比后端菜单表的 `component` 字段与 `VIEW_REGISTRY` 的键名
- 确保所有菜单对应的视图文件都存在
- 检查视图文件路径与注册路径是否一致

**修复方式**：
- 在 `src/router/dynamic.ts` 中补充缺失的注册项
- 创建缺失的视图文件（如需要）

---

### 2.6 代码质量检查

**目标**：确保代码符合 TypeScript 严格类型检查和项目规范。

#### 检查项目

1. **类型检查**：`npm run build`（包含 `vue-tsc --noEmit`）
2. **格式检查**：确保代码风格一致
3. **运行时测试**：`npm run dev` 启动开发服务器，手动测试关键功能

#### 需要修复的问题

- TypeScript 类型错误：根据编译错误逐项修复
- 未使用的变量和导入：清理无用代码
- 组件 props 类型定义：使用 TypeScript 类型定义替代运行时定义

---

## 三、前后端联调验证

### 3.1 接口联调

**目标**：验证前后端接口对接正确，数据契约一致。

**验证内容**：
- 前端 API 定义的 URL、请求方法、参数与后端 Router 一致
- 前端 DTO 类型定义与后端 DTO 字段一致（字段名、类型、必填性）
- 分页口径统一（`PageQuery{page, size}` + `PageResponse`）
- 响应结构统一（`Result{code, msg, data, traceId}`）

**验证方式**：
- 启动后端服务：`mvnd -pl apex-interfaces -am spring-boot:run -P basic`
- 启动前端服务：`npm run dev`
- 访问 Swagger 文档：`http://localhost:8080/swagger-ui.html`
- 前端测试关键功能：登录、用户管理、菜单管理等

---

### 3.2 Swagger 文档验证

**目标**：确保 Swagger 在线文档完整、准确、专业。

**验证内容**：
- 所有接口都在 Swagger 文档中展示
- 接口描述清晰完整（中文）
- 请求参数说明完整（包含类型、必填性、示例值）
- 请求体展示完整（所有字段都有说明）
- 响应体展示完整（所有字段都有说明）
- 错误码说明完整

**验证方式**：
- 访问 `http://localhost:8080/swagger-ui.html`
- 逐个接口检查文档完整性
- 使用 Swagger Try it out 功能测试接口

---

## 四、实施步骤

### Phase 1：后端功能完整性分析（优先）

1. 编译所有模块，检查编译错误
2. 查找所有 TODO 标记和未实现方法
3. 逐一检查 Handler 方法实现
4. 修复未实现的方法或移除临时代码

### Phase 2：后端 SpringDoc 注解补充

1. 为 OfficeIntegrationRouter、OrgSyncRouter、MessageRouter 补充完整注解
2. 为 workflow 包下的所有 DTO 补充 @Schema 注解
3. 检查其他 Router 和 DTO 的注解完整性
4. 运行 Swagger 文档验证

### Phase 3：后端 JavaDoc 注释补充

1. 运行 `scripts/dev/check/scan-javadoc-issues.sh` 扫描缺失的 JavaDoc
2. 使用 `scripts/dev/format/batch-add-javadoc.sh` 批量添加类级别注释
3. 手动补充方法级别 JavaDoc（特别是 Handler 和 Service 方法）
4. 确保所有 public/protected 类和方法都有完整注释

### Phase 4：后端代码质量检查

1. 运行 `mvnd spotless:apply` 自动格式化
2. 运行 `mvnd spotless:check` 确认格式化通过
3. 运行 `mvnd pmd:check` 检查代码质量
4. 运行 `mvnd clean compile -DskipTests` 确认编译通过
5. 运行 `mvnd test -P basic` 确认测试通过

### Phase 5：前端功能完整性分析

1. 运行 `npm run build` 检查编译错误
2. 查找所有 TODO 标记和未实现功能
3. 检查 VIEW_REGISTRY 注册完整性
4. 修复缺失的功能或移除临时代码

### Phase 6：前端注释补充

1. 为所有 `.ts` 文件添加文件头注释
2. 为所有 `.vue` 文件添加文件头注释
3. 为所有导出函数添加 JSDoc 注释
4. 为所有类型定义添加注释

### Phase 7：前端类型安全检查

1. 运行 `npm run build` 确认类型检查通过
2. 修复所有 TypeScript 类型错误
3. 清理未使用的变量和导入
4. 确保所有组件 props 使用 TypeScript 类型定义

### Phase 8：前后端联调验证

1. 启动后端服务（`mvnd -pl apex-interfaces -am spring-boot:run -P basic`）
2. 启动前端服务（`npm run dev`）
3. 验证 Swagger 文档完整性（`http://localhost:8080/swagger-ui.html`）
4. 测试关键功能（登录、用户管理、菜单管理等）
5. 确认前后端数据契约一致

---

## 五、验收标准

### 后端验收标准

- [ ] 所有模块编译通过（`mvnd clean compile -DskipTests`）
- [ ] 所有 Router 类有完整的类级别和方法级别 JavaDoc
- [ ] 所有 Router 方法有完整的 SpringDoc 注解（@RouterOperation + @Operation + @Parameter + @RequestBody + @ApiResponse）
- [ ] 所有 DTO 类有完整的 @Schema 注解（类级别和字段级别）
- [ ] 所有 public/protected 类和方法有完整的 JavaDoc 注释
- [ ] 代码格式化通过（`mvnd spotless:check`）
- [ ] 代码质量检查通过（`mvnd pmd:check`）
- [ ] 基础测试通过（`mvnd test -P basic`）
- [ ] Swagger 文档完整展示所有接口、请求体、响应体
- [ ] 无未实现的 TODO 方法或临时错误响应

### 前端验收标准

- [ ] 编译通过（`npm run build`）
- [ ] 类型检查通过（`vue-tsc --noEmit`）
- [ ] 所有 `.ts` 文件有文件头注释
- [ ] 所有 `.vue` 文件有文件头注释
- [ ] 所有导出函数有 JSDoc 注释（含 @param、@returns、@example）
- [ ] 所有类型定义有注释（interface/type + 关键字段）
- [ ] VIEW_REGISTRY 注册完整，所有动态路由页面都能正常访问
- [ ] 前端 DTO 类型与后端 DTO 字段一致
- [ ] 无未使用的变量和导入
- [ ] 无 TypeScript any 类型（除特殊情况）

### 联调验收标准

- [ ] 后端服务启动成功（端口 8080）
- [ ] 前端服务启动成功（端口 5173）
- [ ] Swagger 文档访问正常（`http://localhost:8080/swagger-ui.html`）
- [ ] 前端登录功能正常
- [ ] 前端菜单加载正常
- [ ] 前端主要 CRUD 功能正常（用户、角色、菜单、部门等）
- [ ] 前后端数据契约一致（字段名、类型、必填性）
- [ ] 分页口径统一
- [ ] 响应结构统一（`Result{code, msg, data, traceId}`）

---

## 六、工作量估算

### 后端工作量

- **功能完整性分析**：2-3 小时
- **SpringDoc 注解补充**：4-6 小时
- **JavaDoc 注释补充**：6-8 小时
- **代码质量修复**：2-4 小时
- **合计**：14-21 小时

### 前端工作量

- **功能完整性分析**：2-3 小时
- **文件头注释补充**：3-4 小时
- **JSDoc 注释补充**：4-6 小时
- **类型定义注释补充**：2-3 小时
- **类型安全修复**：2-4 小时
- **合计**：13-20 小时

### 联调验证工作量

- **接口联调验证**：2-3 小时
- **Swagger 文档验证**：1-2 小时
- **合计**：3-5 小时

### 总工作量

- **总计**：30-46 小时

---

## 七、风险与注意事项

### 风险点

1. **未实现的功能**：部分 Handler 方法可能依赖尚未实现的 Service 方法，需要判断是继续实现还是暂时保留 TODO
2. **编译错误**：补充注解和注释可能引入新的编译错误，需要逐步验证
3. **类型不一致**：前后端 DTO 字段可能存在不一致，需要确认以哪一方为准
4. **测试覆盖**：修改代码后可能影响现有测试，需要运行测试确保不破坏现有功能

### 注意事项

1. **遵循项目规范**：严格遵循 AGENTS.md 中定义的编码规范和注释规范
2. **增量提交**：不要一次性修改大量文件，建议按模块或功能分批提交
3. **保留历史**：对于有疑问的代码，先添加注释说明，不要直接删除
4. **验证先行**：每次修改后都要运行编译和测试，确保不破坏现有功能
5. **文档优先**：Swagger 文档的完整性和准确性比代码注释更重要，优先完善 Swagger 注解

---

## 八、参考资料

### 后端参考

- 项目规范：[/Users/work/apex-scaffold/AGENTS.md](../../../AGENTS.md)
- 最佳实践示例：[OfficeApprovalRouter](../../../apex-office-service/src/main/java/com/apex/scaffold/officeservice/interfaces/router/office/OfficeApprovalRouter.java)
- SpringDoc 配置：[apex-interfaces/src/main/resources/application.yml](../../../apex-system/src/main/resources/application.yml)

### 前端参考

- 项目规范：[/Users/work/apex-admin-web/AGENTS.md](../../../AGENTS.md)
- 注释规范：AGENTS.md § 4（注释与代码风格规范）
- 类型安全检查：AGENTS.md § 7（类型安全检查清单）

---

**文档版本**：v1.0  
**创建日期**：2026-09-23  
**最后更新**：2026-09-23

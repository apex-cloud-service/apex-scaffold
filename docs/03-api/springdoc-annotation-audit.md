# SpringDoc 注解审计

本记录依据当前工作区源码进行静态核对，范围包括主应用 `apex-interfaces` 和独立认证中心 `apex-auth-center`。代码中存在元数据不等于运行时 OpenAPI 已正确生成；本轮尚未启动两个服务验证 `/v3/api-docs`。

## 核对口径

- Handler 方法统计匹配 `public Mono<ServerResponse>`，注解统计匹配源码中的 `@Operation(`；这是文本统计，不能证明逐方法绑定或参数、响应 Schema 的完整性。
- 函数式接口的元数据可以声明在 Handler，也可以通过 Router 的 `@RouterOperation` / `@RouterOperations` 提供。Handler 缺少方法级 `@Operation` 不能直接判定接口缺少 OpenAPI 文档。
- `@OperationLog` 属于业务审计，不属于 SpringDoc 覆盖率；其存在也不证明日志已实际入库。
- 实际检查应逐路由核对 HTTP 方法、路径、beanClass、beanMethod、operationId、参数约束、安全要求和响应类型，再检查运行时生成结果。

## 主应用范围

主应用源码路径为 `apex-interfaces/src/main/java/com/apex/scaffold/interfaces/handler/` 与同层 `router/`。旧记录中的“24 个 Handler、110+ 方法、100% 完整”已不适用于当前源码，不能作为验证依据。

当前静态核对发现 `AuthHandler` 有 10 个上述签名的方法和 9 个 `@Operation` 文本命中；这只提示需要结合路由继续核对，不能据此宣称运行时存在一个文档缺口。主应用登录、刷新和登出路径以 `ApiRouter` 中 `/api/v1/auth/*` 为准。

仓库检查入口为：

```bash
bash scripts/ci/check-springdoc-coverage.sh
```

该入口调用 `scripts/dev/check/check-springdoc-annotations-v2.sh`，扫描主应用 Router、Handler 和 DTO；默认阈值均为 100%。脚本的覆盖率只代表其文本扫描规则，不能代替认证中心审计或运行时验证。

## 认证中心 Handler 静态统计

以下为当前源码文本统计，非接口验收结果。

| Handler | 方法数 | @Operation | @OperationLog |
| --- | ---: | ---: | ---: |
| AuditLogHandler | 10 | 10 | 10 |
| AuthConfigHandler | 7 | 7 | 7 |
| AuthenticationHandler | 3 | 3 | 3 |
| IpAccessControlHandler | 11 | 11 | 11 |
| MfaHandler | 6 | 6 | 6 |
| MfaManagementHandler | 12 | 12 | 12 |
| OAuth2EndpointHandler | 3 | 0 | 0 |
| OAuth2TokenHandler | 1 | 0 | 0 |
| PermissionHandler | 6 | 6 | 6 |
| RoleHandler | 7 | 7 | 7 |
| SessionHandler | 6 | 6 | 6 |
| UnifiedAuthorizationHandler | 1 | 0 | 0 |

OAuth2 端点和 Token 接口的元数据位于 `OAuth2EndpointRouter`、`OAuth2TokenRouter`；OAuth2 元数据路由另见 `OAuth2MetadataRouter`。统一授权接口元数据位于 `UnifiedAuthorizationRouter`，不能仅按 Handler 上注解数量判断覆盖。

## 统一授权接口契约

`UnifiedAuthorizationRouter` 静态声明并注册：

| 项目 | 当前源码 |
| --- | --- |
| 方法与路径 | `GET /auth/v1/authorization/users/{userId}` |
| Handler | `UnifiedAuthorizationHandler.query` |
| operationId | `queryUnifiedAuthorization` |
| 标签 | 统一授权 |
| 安全要求 | `bearerAuth`，专用授权读取账号 ACCESS Token |
| 路径参数 | 必填 `integer/int64`，minimum 为 `1` |
| 成功响应 | 200，`UnifiedAuthorizationResponse.UnifiedAuthorizationResult` Schema，即 `Result` 封装的快照 |
| 错误响应 | 400 参数非法、401 未认证、403 禁止查询 |

授权读取账号由 `AUTH_CENTER_AUTHORIZATION_READER_USER_ID` 指定，不要求管理员角色。中心从认证上下文取得租户，检查读取账号和目标账号同租户、启用、未锁定且未软删除。完整响应和主应用映射要求见[认证中心 README](../../apex-auth-center/README.md)与[配置说明](../05-operations/configuration.md#认证中心统一授权)。

认证中心登录和刷新直接返回 DTO，登出成功为 HTTP 200 空响应体；不能把其他接口的 `Result` 契约套用于这些入口。

## 验证状态与待验证项

本记录已核对静态源码中的声明位置和统一授权路由契约。检查脚本的实际执行结果应以本次命令输出为准，不以此文档中的预期命令作为通过证据。

尚未验证主应用与认证中心运行时 `/v3/api-docs` 的路径注册、operationId 唯一性、Bearer security scheme、参数约束与响应 Schema，也未完成服务联调、部署或数据库迁移验证。应分别启动两个独立 ApplicationContext 后核对，不能用一个服务的 OpenAPI 输出代表另一个服务。

## 参考

- [SpringDoc 注解模板](springdoc-annotation-templates.md)
- [认证中心验证指南](../../apex-auth-center/TESTING.md)
- [OpenAPI 规范](https://swagger.io/specification/)

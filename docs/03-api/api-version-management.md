# API版本管理使用指南

> 配套图表：[56 API 版本演进图](../diagrams/56_api_version_evolution.svg)

## 功能说明

本工程采用 **URL 路径版本控制**，版本由各业务库的 Router（函数式路由）直接注册实现，**没有 `@ApiVersion` 注解或拦截器机制**（见 `apex-system` 的 `ApiVersionConfig` 类注释：该类仅是基于 `PathMatchConfigurer` 的说明性配置载体，javadoc 明言「URL 路径版本由各 Router 直接注册」）。约定形式：

- `/api/v1/...` — 第一代稳定契约（多数业务路由）
- `/api/v2/...` — 第二代契约（如统一待办 `GET /api/v2/todo`、文件分享 `/api/v2/file-shares/**`、平台治理 `/api/v2/operations|platform|tenant-packages|capabilities/**`）
- `/auth/v1/...`、`/auth/v2/...` — 认证中心独立前缀（`/auth/v2/**` 用于密码找回）
- `/integration/v1/...`、`/admin-api/v1/...`、`/open-api/...`、`/internal/v1/...` — 按域/受众划分的前缀族

版本共存即「新旧 Router 同时注册、网关同时放行」，便于向后兼容和灰度发布。

## 快速开始：新增一个 v2 端点

本仓 Web 层一律 WebFlux 函数式 Router + Handler（禁用 `@Controller`/`@RestController`）。在所属业务库（或运行模块）新增/扩展 Router 注册即可：

```java
// 在既有 RouterConfig / Router 中追加 v2 路由注册（示意，风格对齐 apex-example 的 ExampleRouterConfig）
@Bean
public RouterFunction<ServerResponse> productV2Routes(ProductV2Handler handler) {
    return RouterFunctions.route()
            .GET("/api/v2/products/{id}", handler::getProduct)
            .build();
}
```

要点：

1. 新版本端点必须与旧版本**路径不同**（v1 继续服务，不改动旧 Router）。
2. 网关 `application.yml` 需显式放行新增前缀（参考 `/api/v2/**` 各路由组的写法）。
3. Handler 方法上的 SpringDoc 注解（`@Operation`/`@Schema`）与路径、方法、响应保持一致（见 [springdoc-annotation-templates.md](springdoc-annotation-templates.md)）。

### 版本演进策略

#### 兼容性原则

1. **新增字段**: 可以在新版本中添加,不影响旧版本
2. **删除字段**: 不应直接删除,先在文档与 `@Schema` 中标注 deprecated,保留至少一个版本周期
3. **修改行为**: 创建新版本,保持旧版本行为不变
4. **错误码变更**: 新版本可以使用新错误码,但需文档说明

#### 版本生命周期

```
V1 (活跃) → V2 (活跃) → V1 (废弃) → V1 (下线)
   ↓           ↓           ↓
  新功能     增强功能    停止维护    移除路由
```

#### 灰度发布

版本流量分配属于部署层能力：可通过网关路由权重、负载均衡器或部署侧（Compose/K8s）控制。本仓默认网关按路径前缀转发，不按版本做请求级分流。

## Swagger 文档分组

system-service 的 SpringDoc 分组以 `SpringDocConfig` 实际装配为准，当前分组为：

| 分组 | 覆盖 |
| --- | --- |
| `default` | 默认业务路由组 |
| `auth` | 认证域路由组 |
| `user` | 用户域路由组 |
| `admin-system` | 系统管理域 |
| `admin-user` | 用户管理域 |

**没有** 按 URL 版本划分的 v1/v2 分组；新增版本族如需独立分组，应在 `SpringDocConfig` 增配 `GroupedOpenApi`（`pathsToMatch` 指向对应前缀），不得在文档中虚构分组。

访问地址：http://localhost:8082/swagger-ui.html（分组切换在页面右上角下拉）。

## 最佳实践

### 1. 版本号规范

- 使用语义化版本: v1, v2, v3...
- 避免使用小数点: 不使用v1.1, v1.2
- 保持简洁: 不超过3个活跃版本

### 2. 文档同步

- 每个版本必须在 `API_ROUTES.md` 登记路由行
- 废弃版本需在文档中明确标注
- 提供版本迁移指南

### 3. 监控告警

- 监控各版本的调用量
- 当旧版本调用量低于阈值时,考虑下线
- 设置废弃版本的告警提醒

### 4. 客户端适配

- 客户端应支持版本协商机制
- 优先使用最新版本,降级到兼容版本
- 记录客户端使用的版本,便于分析

## 注意事项

1. **数据库兼容**: 新版本可能需要数据库schema变更,使用Flyway管理（各服务独立版本链）
2. **缓存隔离**: 不同版本的缓存key应隔离,避免冲突
3. **日志区分**: 日志中应包含版本信息,便于问题排查
4. **测试覆盖**: 每个版本都需要完整的集成测试
5. **性能影响**: 多版本共存会增加维护成本,及时清理废弃版本

## 示例场景

### 场景1: 新增可选字段

V1返回:
```json
{
  "id": 1,
  "username": "zhangsan"
}
```

V2返回(新增email字段):
```json
{
  "id": 1,
  "username": "zhangsan",
  "email": "zhangsan@example.com"
}
```

### 场景2: 接口行为变更（真实案例）

统一待办从多域分散查询收敛为聚合服务只读统一入口：

- 旧口径：各域自行查询任务/站内信
- 新口径：`GET /api/v2/todo`（`apex-aggregation-service` 的 `UnifiedTodoRouter`，经内部签名契约拉取 workflow 与 office 数据，无本地数据库）

### 场景3: 错误码变更

V1错误码:
```json
{
  "code": 1001,
  "msg": "参数错误"
}
```

V2错误码(更精确):
```json
{
  "code": 1002,
  "msg": "必填参数缺失: username"
}
```

## 规划项（未实现，登记于此避免与现状混淆）

- [ ] 基于Header的版本控制(备选方案，当前为纯 URL 路径约定)
- [ ] 版本兼容性检查拦截器
- [ ] 自动生成版本差异报告
- [ ] 按版本族的 Swagger 分组（当前按域分组）

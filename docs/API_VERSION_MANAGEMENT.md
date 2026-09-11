# API版本管理使用指南

> 配套图表：[56 API 版本演进图](diagrams/56_api_version_evolution.svg)

## 功能说明

API版本管理支持多版本共存,便于向后兼容和灰度发布。通过URL路径区分不同版本:
- `/api/v1/users` - V1版本
- `/api/v2/users` - V2版本

## 快速开始

### 1. 创建版本化Controller

```java
@RestController
@RequestMapping("/api/v1/users")
@ApiVersion("v1")
public class UserControllerV1 {
    
    @GetMapping
    public Result<List<UserVO>> listUsers() {
        // V1实现
    }
}

@RestController
@RequestMapping("/api/v2/users")
@ApiVersion(value = "v2", deprecated = false)
public class UserControllerV2 {
    
    @GetMapping
    public Result<PageResult<UserVO>> listUsers(
            @RequestParam int page,
            @RequestParam int size) {
        // V2实现: 支持分页
    }
}
```

### 2. 注解参数说明

| 参数 | 类型 | 必填 | 说明 | 示例 |
|------|------|------|------|------|
| value | String | 是 | 版本号 | "v1", "v2" |
| deprecated | boolean | 否 | 是否废弃 | true/false |
| deprecationNote | String | 否 | 废弃说明 | "请使用V2版本" |
| minVersion | String | 否 | 最低支持版本 | "v1" |

### 3. 废弃版本标记

对于需要逐步淘汰的旧版本:

```java
@RestController
@RequestMapping("/api/v1/legacy")
@ApiVersion(
    value = "v1", 
    deprecated = true,
    deprecationNote = "此版本将于2024-12-31停止支持,请迁移到V2版本"
)
public class LegacyController {
    // ...
}
```

## 版本演进策略

### 兼容性原则

1. **新增字段**: 可以在新版本中添加,不影响旧版本
2. **删除字段**: 不应直接删除,先标记为deprecated,保留至少一个版本周期
3. **修改行为**: 创建新版本,保持旧版本行为不变
4. **错误码变更**: 新版本可以使用新错误码,但需文档说明

### 版本生命周期

```
V1 (活跃) → V2 (活跃) → V1 (废弃) → V1 (下线)
   ↓           ↓           ↓
  新功能     增强功能    停止维护    移除代码
```

### 灰度发布

可以通过网关或负载均衡器控制流量分配:

```nginx
# Nginx配置示例
location /api/v1/ {
    proxy_pass http://backend_v1;
}

location /api/v2/ {
    proxy_pass http://backend_v2;
}
```

## Swagger文档集成

在Swagger配置中区分不同版本:

```java
@Bean
public GroupedOpenApi v1Api() {
    return GroupedOpenApi.builder()
        .group("v1")
        .pathsToMatch("/api/v1/**")
        .build();
}

@Bean
public GroupedOpenApi v2Api() {
    return GroupedOpenApi.builder()
        .group("v2")
        .pathsToMatch("/api/v2/**")
        .build();
}
```

访问地址:
- V1文档: http://localhost:8080/swagger-ui.html?group=v1
- V2文档: http://localhost:8080/swagger-ui.html?group=v2

## 最佳实践

### 1. 版本号规范

- 使用语义化版本: v1, v2, v3...
- 避免使用小数点: 不使用v1.1, v1.2
- 保持简洁: 不超过3个活跃版本

### 2. 文档同步

- 每个版本必须有独立的API文档
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

1. **数据库兼容**: 新版本可能需要数据库schema变更,使用Flyway管理
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

### 场景2: 接口行为变更

V1: 不分页,返回全部数据
```java
@GetMapping
public Result<List<UserVO>> listUsers() {
    return Result.success(userService.list());
}
```

V2: 支持分页
```java
@GetMapping
public Result<PageResult<UserVO>> listUsers(
        @RequestParam int page,
        @RequestParam int size) {
    return Result.success(userService.page(page, size));
}
```

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

## TODO

- [ ] 实现基于Header的版本控制(备选方案)
- [ ] 添加版本兼容性检查拦截器
- [ ] 自动生成版本差异报告
- [ ] 集成API网关进行版本路由

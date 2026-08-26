# 操作审计日志使用指南

## 功能说明

操作审计日志用于记录用户的关键操作,满足安全合规要求。通过`@AuditLog`注解标记需要审计的方法,AOP切面会自动记录操作信息到数据库。

## 快速开始

### 1. 在Controller或Service方法上添加注解

```java
import com.apex.scaffold.common.annotation.AuditLog;
import com.apex.scaffold.common.annotation.OperationType;

@RestController
@RequestMapping("/users")
public class UserController {

    @PostMapping
    @AuditLog(
        module = "用户管理",
        type = OperationType.CREATE,
        description = "创建用户: #{#user.username}",
        ignoreParams = {"password"}  // 忽略密码字段
    )
    public Result<UserVO> createUser(@RequestBody UserDTO user) {
        // 业务逻辑
        return Result.success(userService.create(user));
    }
    
    @PutMapping("/{id}")
    @AuditLog(
        module = "用户管理",
        type = OperationType.UPDATE,
        description = "修改用户信息"
    )
    public Result<Void> updateUser(@PathVariable Long id, @RequestBody UserDTO user) {
        userService.update(id, user);
        return Result.success();
    }
    
    @DeleteMapping("/{id}")
    @AuditLog(
        module = "用户管理",
        type = OperationType.DELETE,
        description = "删除用户ID: #{#id}"
    )
    public Result<Void> deleteUser(@PathVariable Long id) {
        userService.delete(id);
        return Result.success();
    }
}
```

### 2. 配置项说明

| 参数 | 类型 | 必填 | 说明 | 示例 |
|------|------|------|------|------|
| module | String | 否 | 操作模块 | "用户管理" |
| type | OperationType | 否 | 操作类型 | CREATE/UPDATE/DELETE/QUERY |
| description | String | 否 | 操作描述(支持SpEL) | "创建用户: #{#user.username}" |
| recordParams | boolean | 否 | 是否记录请求参数(默认true) | true |
| recordResult | boolean | 否 | 是否记录响应结果(默认false) | false |
| ignoreParams | String[] | 否 | 忽略的参数名 | {"password", "token"} |

### 3. 操作类型枚举

- `CREATE` - 新增
- `UPDATE` - 修改
- `DELETE` - 删除
- `QUERY` - 查询
- `EXPORT` - 导出
- `IMPORT` - 导入
- `LOGIN` - 登录
- `LOGOUT` - 登出
- `AUTHORIZE` - 授权
- `OTHER` - 其他

## 高级用法

### SpEL表达式支持

在description中可以使用SpEL表达式引用方法参数:

```java
@AuditLog(
    module = "订单管理",
    type = OperationType.CREATE,
    description = "创建订单: 商品ID=#{#order.productId}, 数量=#{#order.quantity}"
)
public Result<OrderVO> createOrder(@RequestBody OrderDTO order) {
    // ...
}
```

### 敏感参数过滤

对于密码、token等敏感信息,使用ignoreParams排除:

```java
@AuditLog(
    module = "用户管理",
    type = OperationType.CREATE,
    ignoreParams = {"password", "confirmPassword", "token"}
)
public Result<Void> register(@RequestBody RegisterDTO dto) {
    // ...
}
```

### 大数据量接口优化

对于返回大量数据的接口,关闭响应结果记录:

```java
@AuditLog(
    module = "报表管理",
    type = OperationType.EXPORT,
    recordResult = false  // 不记录响应结果,避免存储过大
)
public Result<List<DataVO>> exportData(@RequestBody QueryDTO query) {
    // ...
}
```

## 数据库表结构

审计日志存储在`audit_log`表中,主要字段:

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键ID |
| operator_id | VARCHAR(64) | 操作人ID |
| operator_name | VARCHAR(100) | 操作人姓名 |
| operator_ip | VARCHAR(50) | 操作人IP |
| module | VARCHAR(100) | 操作模块 |
| operation_type | VARCHAR(20) | 操作类型 |
| description | VARCHAR(500) | 操作描述 |
| request_url | VARCHAR(500) | 请求URL |
| request_params | TEXT | 请求参数(JSON) |
| response_result | TEXT | 响应结果(JSON) |
| duration | BIGINT | 执行时长(ms) |
| status | VARCHAR(20) | 状态(SUCCESS/FAIL) |
| error_msg | VARCHAR(1000) | 错误信息 |
| create_time | DATETIME | 创建时间 |

## 注意事项

1. **性能影响**: 审计日志采用异步保存,对主业务流程影响极小
2. **存储空间**: 建议定期归档或删除历史数据(如保留90天)
3. **敏感信息**: 务必使用ignoreParams过滤密码、token等敏感字段
4. **异常处理**: 审计日志保存失败不会影响主业务流程,仅记录错误日志
5. **操作人获取**: 当前版本使用默认值,生产环境需从SecurityContext或Token中获取真实操作人

## TODO

- [ ] 集成Spring Security获取真实操作人信息
- [ ] 实现真正的异步保存(@Async或消息队列)
- [ ] 添加审计日志查询API
- [ ] 支持数据脱敏(手机号、身份证等)
- [ ] 定期归档策略

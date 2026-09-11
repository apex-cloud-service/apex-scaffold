# 操作审计日志使用指南

> **配套架构图**: [审计日志时序图](diagrams/42_audit_log_sequence.svg)

## 功能说明

操作审计日志用于记录用户的关键操作（登录、增删改、权限变更、文件操作等），满足安全合规要求。
通过 `@OperationLog` 注解标记需要审计的方法，`OperationLogAspect` 切面会自动记录操作信息，
异步落库到 `sys_operation_log` 表；管理后台通过 `GET /admin-api/v1/audit-logs` 查询展示。

> 说明：本项目为 WebFlux 纯函数式端点架构（无 @RestController），审计写入由
> `OperationLogAspect`（响应式适配：支持 Mono 返回值、ReactiveSecurityContextHolder 取操作人、
> 异步批量入队）完成，查询链路与之**写读同源**，保证审计页面可查到实际操作记录。

## 快速开始

### 1. 在 Handler 方法上添加注解（函数式端点）

```java
import com.apex.scaffold.common.annotation.OperationLog;

@Component
public class UserHandler extends BaseHandler {

    @OperationLog(
        module = "用户管理",
        type = OperationLog.OperationType.INSERT,
        description = "创建用户: #{#request}"
    )
    public Mono<ServerResponse> create(ServerRequest request) {
        // 业务逻辑
    }

    @OperationLog(
        module = "用户管理",
        type = OperationLog.OperationType.UPDATE,
        description = "修改用户信息"
    )
    public Mono<ServerResponse> update(ServerRequest request) {
        // 业务逻辑
    }

    @OperationLog(
        module = "用户管理",
        type = OperationLog.OperationType.DELETE,
        description = "删除用户"
    )
    public Mono<ServerResponse> delete(ServerRequest request) {
        // 业务逻辑
    }
}
```

### 2. 配置项说明

| 参数 | 类型 | 必填 | 说明 | 示例 |
|------|------|------|------|------|
| module | String | 否 | 操作模块 | "用户管理" |
| type | OperationType | 否 | 操作类型 | INSERT/UPDATE/DELETE/SELECT |
| description | String | 否 | 操作描述 | "创建用户" |
| saveRequest | boolean | 否 | 是否保存请求参数(默认true) | true |
| saveResponse | boolean | 否 | 是否保存响应结果(默认true) | false |
| excludeRequestFields | String[] | 否 | 排除的请求参数字段(默认排除密码/token) | {"password", "token"} |
| excludeResponseFields | String[] | 否 | 排除的响应字段 | {} |

### 3. 操作类型枚举（OperationLog.OperationType）

- `INSERT` - 新增
- `UPDATE` - 修改
- `DELETE` - 删除
- `SELECT` - 查询
- `LOGIN` - 登录
- `LOGOUT` - 登出
- `UPLOAD` - 上传
- `DOWNLOAD` - 下载
- `EXPORT` - 导出
- `IMPORT` - 导入
- `OTHER` - 其他

### 4. 全局开关（application.yml）

```yaml
operation-log:
  enabled: true          # 总开关，false 时切面直接放行不记录
  log-params: true       # 是否记录请求参数内容
  log-result: true       # 是否记录响应结果内容
  max-param-length: 2000 # 请求参数最大截断长度
  max-result-length: 2000 # 响应结果最大截断长度
```

## 查询 API

- `GET /admin-api/v1/audit-logs` - 分页查询审计日志（需要权限 `system:audit-log:list`）
  - 查询参数：`page`（页码，默认 1）、`size`（每页大小，默认 20，上限 100）、
    `module`（操作模块，模糊）、`operatorName`（操作人姓名，模糊）
  - 结果按主键倒序（最新优先）

## 数据库表结构（sys_operation_log）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键ID(雪花算法) |
| module | VARCHAR(100) | 操作模块 |
| operation_type | VARCHAR(50) | 操作类型 |
| description | VARCHAR(255) | 操作描述 |
| request_method | VARCHAR(10) | 请求方法 |
| request_uri | VARCHAR(500) | 请求URI |
| request_params | TEXT | 请求参数(JSON) |
| response_result | TEXT | 响应结果(JSON) |
| status | INT(1) | 操作状态(0-成功,1-失败) |
| error_msg | TEXT | 错误信息 |
| execution_time | BIGINT | 执行时间(毫秒) |
| ip | VARCHAR(50) | 操作IP |
| location | VARCHAR(100) | 操作地点 |
| user_id | BIGINT | 操作用户ID |
| user_name | VARCHAR(50) | 操作用户名 |
| operation_time | DATETIME | 操作时间 |
| device_type / browser / os | VARCHAR | 设备/浏览器/系统 |
| trace_id | VARCHAR(100) | 追踪ID |
| create_time / update_time | DATETIME | 创建/更新时间 |

## 注意事项

1. **性能影响**：日志通过阻塞队列异步批量写入（生产者-消费者模式），不阻塞主业务流程
2. **存储空间**：建议定期归档或删除历史数据（如保留 90 天），可配合定时任务清理
3. **敏感信息**：`excludeRequestFields` 默认排除 password/oldPassword/newPassword/token，
   业务侧对自定义敏感字段需显式声明
4. **异常处理**：日志保存失败仅记录错误日志，不影响主业务流程
5. **操作人获取**：响应式路径通过 `ReactiveSecurityContextHolder` 解析 JWT 中的 openId，
   再查询用户表补全 userId/昵称；未登录请求记为匿名

## 审计注解覆盖约定

写操作（INSERT/UPDATE/DELETE/UPLOAD/IMPORT/授权变更等）与关键查询（登录/登出/导出）必须标注
`@OperationLog`；纯读接口（列表/详情查询）可按需标注，健康检查等系统探针接口不标注。

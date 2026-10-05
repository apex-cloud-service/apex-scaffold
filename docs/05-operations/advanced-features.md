# 高级功能使用指南（C5-C9）

> 版本：v1.3.1（2026-08-27）
> 覆盖：Excel 导入导出 / SSE 通知推送 / 邮件发送 / 国际化补全 / 读写分离示例

---

## 1. Excel 导入导出（C5）

### 能力
- `ExcelExportService`（apex-infrastructure/excel）：基于 EasyExcel 将任意 DTO 列表导出为 xlsx 字节流。
- 示例端点：`GET /admin-api/v1/export/users`（导出用户列表，需 `system:user:list` 权限）。

### 用法
```java
// 1. 定义表头 DTO（@ExcelProperty 标注列名与顺序）
@ExcelProperty("用户名") private String username;

// 2. 调用导出服务
byte[] bytes = excelExportService.export("用户列表", UserExportDTO.class, rows);
```

### 扩展
- 新增导出：参照 `ExcelExportHandler.exportUsers`，在 `ApiRouter` 注册新路由并同步 `RouterOpenApiConfig` 文档声明（覆盖测试强制）。
- 导入：EasyExcel 的 `read()` 监听器模式接入即可，脚手架未内置导入端点（按业务需要扩展）。

---

## 2. SSE 站内通知推送（C6）

### 能力
- `NotificationService`（apex-infrastructure/notification）：基于 Reactor `Sinks` 的进程内发布-订阅。
- 端点：`GET /api/v1/notifications/stream`（SSE，按认证 Principal 定向推送，30s 心跳保活）。

### 用法
```java
// 业务方发布通知（如订单状态变更、审核结果）
notificationService.publish(userOpenId, "您的订单已发货");

// 客户端订阅（EventSource）
const es = new EventSource('/api/v1/notifications/stream');
es.addEventListener('notification', e => console.log(e.data));
```

### 设计说明
- 单实例进程内广播；多实例横向扩展需将 `NotificationService.publish` 桥接到 RocketMQ 广播（`biz_system_message` 表已预留）。
- 未认证请求返回 401。

---

## 3. 邮件发送（C7）

### 能力
- `MailService`（apex-infrastructure/mail）：`sendSimple`（文本）/ `sendHtml`（富文本）。
- 配置：`application.yml` → `spring.mail.*`（host/port/username/password 环境变量注入）。

### 用法
```java
mailService.sendSimple("user@example.com", "欢迎注册", "您的账号已创建");
mailService.sendHtml("user@example.com", "重置密码", "<h1>点击链接重置密码</h1>");
```

### 配置（application.yml）
```yaml
spring:
  mail:
    host: ${MAIL_HOST:}
    port: ${MAIL_PORT:465}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
    properties:
      mail.smtp.ssl.enable: true
```

### 说明
- SMTP 未配置时发送调用抛异常，业务方按需捕获降级（如告警邮件失败仅记日志）。
- 465 用 SSL、587 用 STARTTLS（`mail.smtp.ssl.enable` 相应调整）。

---

## 4. 国际化全量覆盖（C8）

- ResultCode 全部消息键（102 个）已覆盖 **4 语言**（zh_CN / zh_TW / en_US / th_TH），零缺失。
- 新增错误码必须同步 4 语言文件（`i18n/messages_*.properties`），否则 `MessageUtils` 回退默认文案。
- 本次清理：删除已移除业务域死枚举 `GOLDEN_EYE_TRIGGERED(4203)`（无引用）。

---

## 5. 多数据源读写分离示例（C9）

### 现状
- `dynamic-datasource` 已集成，`application-prod.yml` 主库（master，Druid）配置完整。
- 从库（slave）配置以注释形式提供（`DB_SLAVE_URL/USERNAME/PASSWORD` 环境变量）。

### 启用步骤
1. 取消 `application-prod.yml` 中 `slave` 数据源块注释，注入真实从库地址。
2. 在只读方法上标注 `@DS("slave")`（如报表查询）：
```java
@DS("slave")
public Mono<List<OrderDTO>> listReport() {
    return orderRepository.findReport();
}
```
3. 未配置 slave 时 `strict=false` 自动回退 primary（安全兜底）。

### 注意事项
- **主从延迟**：读多写少、对一致性不敏感的场景（报表/列表）才建议使用。
- 写操作禁止走 slave；事务方法内 @DS 以方法级为准。
- 本地/测试环境无 slave，示例默认关闭。

---


---

## 6. 自动重试与可靠消息

### 自动重试

使用 `@AutoRetry` 为适合重复执行的同步方法、`Mono` 或 `Flux` 增加统一重试策略：

```java
@AutoRetry(maxAttempts = 3, retryDelayMs = 200, maxDelayMs = 5000,
        backoffMultiplier = 2.0, retryOn = {TransientDataAccessException.class})
public Mono<Result> refreshCache(String key) {
    return cacheClient.refresh(key);
}
```

注解参数包括最大尝试次数、初始和最大延迟、指数退避倍率以及可重试异常白名单。只对幂等操作使用，HTTP 4xx 和业务校验异常不应加入白名单。

### RocketMQ 幂等和确认

生产者为消息复用或生成稳定 message ID，并写入 header 与 RocketMQ `KEYS`。消费者使用 Redis token 竞争处理权，只有业务副作用完成并由当前 token 确认成功后才返回消费成功；失败抛异常以触发 RocketMQ 重消费，重复成功消息不会再次执行 handler。

### Domain Event Outbox

领域事件通过 Outbox 保证业务提交与消息投递解耦。worker 领取 `PENDING` 记录后进入 `PROCESSING`，RocketMQ callback 成功后进入 `DELIVERED`；失败按退避策略重试，超过 `OUTBOX_MAX_ATTEMPTS` 进入 `DEAD`，并按保留配置清理历史记录。

关键配置通过环境变量注入：`MQ_IDEMPOTENT_TTL_MINUTES`、`OUTBOX_BATCH_SIZE`、`OUTBOX_MAX_ATTEMPTS`、`OUTBOX_LOCK_TIMEOUT_SECONDS`、`OUTBOX_PARALLELISM` 及 `OUTBOX_RETENTION_*`。

---

## 7. 分布式 ID 生成器（C11）

### 能力
- `SnowflakeIdGenerator`（apex-common/util/id）：标准雪花算法（41 位毫秒时间戳 + 5 位数据中心 + 5 位机器 + 12 位序列），单机每毫秒 4096 个，跨实例全局唯一。
- 特性：线程安全（synchronized）、毫秒内序列自旋、时钟回拨超 5ms 抛异常防重复。
- 用法：`new SnowflakeIdGenerator(datacenterId, workerId).nextId()`。
- 说明：项目数据表主键注释约定「雪花算法」，可与 MyBatis-Plus `IdType.INPUT` 配合作为主键分配器。

---

## 7. 多租户支持（C10）

### 能力
- `TenantContext`（ThreadLocal 租户上下文）+ `@TenantAware` 注解 + `TenantAspect` 切面。
- 机制：`@TenantAware` 方法从请求头 `X-Tenant-Id` 装配租户 ID 到上下文，业务查询按租户隔离；请求结束自动释放（防线程复用泄漏）。

### 使用示例
```java
@TenantAware
public Mono<PageResponse<OrderDTO>> listOrders(ServerRequest request) {
    String tenantId = TenantContext.currentTenantId().orElseThrow(() -> new BusinessException(ResultCode.PARAM_ERROR, "缺少租户标识"));
    return orderRepository.findByTenant(tenantId, pageNum, pageSize);
}
```

### 隔离策略
- 表级隔离：业务表加 `tenant_id` 列，查询自动过滤（可扩展 MyBatis-Plus `TenantLineInnerInterceptor` 消费 `TenantContext`）。
- 分库隔离：按租户路由数据源（扩展 dynamic-datasource）。

---

## 8. 发布流水线（C13）

- 新增 `.github/workflows/release.yml`：推送 `v*` 标签时自动三档构建（basic/standard/enterprise）并上传 GitHub Release 产物。
- 与既有 `ci-cd.yml`（质量检查）分工：CI 管合并前验证，CD 管打标后发布。
- 发布流程：`git tag -a v1.x.x -m "..." && git push origin v1.x.x`。

## 关联文档
- 架构分层与端口规范：[ARCHITECTURE_USAGE_GUIDE.md](../02-architecture/architecture-usage-guide.md)
- 配置符号规范与键说明：[CONFIGURATION.md](CONFIGURATION.md)
- 接口文档：启动后访问 `/doc.html`（Knife4j）

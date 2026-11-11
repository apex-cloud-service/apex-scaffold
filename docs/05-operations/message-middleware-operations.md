# 消息中间件运维手册

> **版本**: v2.0  
> **最后更新**: 2026-09-18  
> **适用版本**: Apex Scaffold 1.3.1+

---

## 一、架构概览

### 1.1 核心组件

```
┌──────────────────────────────────────────────────────────┐
│                    业务服务层                              │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐         │
│  │OrderService│  │UserService │  │NotifyService│         │
│  └─────┬──────┘  └─────┬──────┘  └─────┬──────┘         │
│        │                │                │                 │
└────────┼────────────────┼────────────────┼────────────────┘
         │                │                │
         ▼                ▼                ▼
┌──────────────────────────────────────────────────────────┐
│              MqProducerService (生产者)                    │
│  • sendSyncWithOutbox()    事务消息                       │
│  • sendAsyncWithFallback() 异步+降级                      │
│  • sendOrderly()           顺序消息                        │
│  • sendTransaction()       分布式事务                      │
└─────┬─────────────────────────────────┬──────────────────┘
      │ (写入)                           │ (发送)
      ▼                                  ▼
┌──────────────────┐            ┌─────────────────┐
│ MessageOutbox表  │            │   RocketMQ      │
│  (本地消息表)     │            │   Broker        │
└─────┬────────────┘            └────────┬────────┘
      │                                   │
      │ (定时扫描)                        │ (拉取)
      ▼                                   ▼
┌──────────────────┐            ┌─────────────────┐
│ Outbox Dispatcher│            │  Consumer       │
│  (每5秒调度)      │            │  (消费者)        │
└─────┬────────────┘            └────────┬────────┘
      │                                   │
      └───────────────┬───────────────────┘
                      │ (补偿重试)
                      ▼
              ┌─────────────────┐
              │ DeadLetter Queue│
              │   (死信队列)     │
              └─────────────────┘
```

### 1.2 数据流

**正常流程**:
1. 业务服务调用 `sendSyncWithOutbox()` 发送消息
2. 消息持久化到本地 `sys_message_outbox` 表
3. 同步发送到 RocketMQ Broker
4. 发送成功标记为 DELIVERED
5. 消费者拉取消息并处理
6. 幂等检查 → 业务处理 → ACK

**故障恢复流程**:
1. 消息发送失败(网络抖动/Broker不可用)
2. 消息保留在 Outbox 表,状态为 PENDING
3. Outbox Dispatcher 定时扫描待投递消息
4. 竞争分布式锁,防止重复投递
5. 重新发送到 RocketMQ
6. 最多重试5次,失败进入 DEAD 状态

---

## 二、部署配置

### 2.1 基础配置

在 `application.yml` 中配置:

```yaml
rocketmq:
  name-server: ${ROCKETMQ_NAMESERVER:127.0.0.1:9876}
  producer:
    group: apex-producer-group
    send-message-timeout: 30000
    retry-times-when-send-failed: 2

# 消息 Outbox 调度参数：仅 enabled/fixed-delay-ms 为配置键；
# 批量大小硬编码 100（MessageOutboxDispatcher），重试上限/延迟来自
# sys_message_outbox 表列 max_attempts（默认 5）与 retry_delay_seconds（默认 10，见 V45 迁移）。
apex:
  outbox:
    dispatcher:
      enabled: true
      fixed-delay-ms: 5000

mq:
  consumer:
    idempotent-ttl-minutes: 30
    local-idempotent-max-size: 10000
    # 注意：当前仅本地缓存容量键存在；Redis 不可用时自动降级 LocalIdempotentCache 的
    # 策略开关（idempotent-fallback-strategy）无实现，勿按降级行为设计依赖。
```

### 2.2 数据库初始化

执行 Flyway 迁移脚本:

**MySQL**:
```sql
source apex-system/src/main/resources/db/migration/mysql/V45__add_message_outbox.sql
```

**PostgreSQL**:
```sql
\i apex-system/src/main/resources/db/migration/postgresql/V45__add_message_outbox.sql
```

验证表创建:
```sql
DESC sys_message_outbox;
```

---

## 三、监控指标

### 3.1 核心指标

指标名以实际注册为准（`OutboxMetricsReporter`、`MqConsumerMetrics`、`MqPublishMetricsAspect`）：

| 指标名 | 类型 | 说明 | 告警阈值 |
|--------|------|------|----------|
| `apex.outbox.records{status="PENDING"}` | Gauge | 待投递消息数 | > 1000 |
| `apex.outbox.records{status="PROCESSING"}` | Gauge | 投递中消息数 | > 500 |
| `apex.outbox.records{status="DEAD"}` | Gauge | 死信消息数 | > 0 |
| `apex.outbox.metrics.up` | Gauge | Outbox 指标采集存活标志 | = 0 |
| `apex.mq.publish.count` | Counter | 消息发送计数 | - |
| `mq.consumer.consume.failed` | Counter | 消费失败计数 | > 50/5min |
| `mq.consumer.consume.success` | Counter | 消费成功计数 | - |
| `mq.consumer.duplicate.blocked` | Counter | 重复消息拦截数 | - |
| `mq.consumer.dlq.messages` | Counter | 死信队列消息数 | > 0 |
| `mq.consumer.lag` | Gauge | 消费积压 | 持续增长 |

### 3.2 监控查询

**查询Outbox待投递消息**:
```sql
SELECT status, COUNT(*) as count 
FROM sys_message_outbox 
WHERE status IN ('PENDING', 'PROCESSING')
GROUP BY status;
```

**查询死信消息**:
```sql
SELECT message_id, topic, tag, attempts, error_message, created_at
FROM sys_message_outbox
WHERE status = 'DEAD'
ORDER BY created_at DESC
LIMIT 20;
```

**查询处理时长统计**:
```sql
SELECT 
  topic,
  AVG(TIMESTAMPDIFF(SECOND, created_at, delivered_at)) as avg_latency_sec,
  MAX(TIMESTAMPDIFF(SECOND, created_at, delivered_at)) as max_latency_sec
FROM sys_message_outbox
WHERE status = 'DELIVERED'
  AND delivered_at >= DATE_SUB(NOW(), INTERVAL 1 HOUR)
GROUP BY topic;
```

---

## 四、故障排查

### 4.1 消息堆积

**现象**: `apex.outbox.pending.count` 持续上升

**排查步骤**:

1. 检查 RocketMQ Broker 状态
```bash
sh mqadmin clusterList -n 127.0.0.1:9876
```

2. 检查 Outbox Dispatcher 是否运行
```sql
SELECT locked_by, locked_at, COUNT(*) 
FROM sys_message_outbox 
WHERE status = 'PROCESSING'
GROUP BY locked_by, locked_at;
```

3. 查看应用日志
```bash
grep "MessageOutboxDispatcher" apex-scaffold.log | tail -50
```

**解决方案**:
- Broker 不可用 → 重启 Broker
- Dispatcher 未启动 → 检查配置 `apex.outbox.dispatcher.enabled`
- 网络问题 → 检查防火墙和网络连接
- 锁超时 → 释放过期锁: `UPDATE sys_message_outbox SET locked_by = NULL WHERE locked_at < DATE_SUB(NOW(), INTERVAL 5 MINUTE)`

### 4.2 消息重复消费

**现象**: 相同消息被处理多次

**排查步骤**:

1. 检查Redis连接
```bash
redis-cli PING
```

2. 检查幂等键
```bash
redis-cli KEYS "mq:idempotent:*"
```

3. 查看消费日志
```bash
grep "重复消息" apex-scaffold.log
```

**解决方案**:
- Redis 不可用 → 自动降级到本地内存幂等(`LocalIdempotentCache`)
- 幂等键过期 → 调整 TTL: `mq.consumer.idempotent-ttl-minutes`
- 业务幂等失效 → 检查业务逻辑的幂等性设计

### 4.3 消息丢失

**现象**: 消息发送后未被消费

**排查步骤**:

1. 检查 Outbox 表
```sql
SELECT * FROM sys_message_outbox WHERE message_id = 'xxx';
```

2. 检查 RocketMQ 消息轨迹
```bash
sh mqadmin queryMsgById -n 127.0.0.1:9876 -i "消息ID"
```

3. 检查消费者状态
```bash
sh mqadmin consumerProgress -n 127.0.0.1:9876 -g apex-user-consumer
```

**解决方案**:
- Outbox 未投递 → 手动触发: `UPDATE sys_message_outbox SET status='PENDING' WHERE message_id='xxx'`
- Broker 未收到 → 检查网络和Broker日志
- 消费者未拉取 → 检查消费者是否在线、订阅关系是否正确

### 4.4 死信消息处理

**现象**: 消息消费重试3次后进入死信队列

**排查步骤**:

1. 查询死信队列
```bash
sh mqadmin queryMsgByKey -n 127.0.0.1:9876 -t %DLQ%apex-user-consumer -k "消息Key"
```

2. 查看死信处理日志
```bash
grep "DeadLetterQueueProcessor" apex-scaffold.log
```

3. 检查 UserMessageDlqHandler
```sql
SELECT * FROM sys_message_outbox 
WHERE message_id LIKE '%:dlq-retry' 
ORDER BY created_at DESC;
```

**解决方案**:
- 已自动重投递 → 等待 Outbox 调度器处理
- 重投递失败 → 查看 `error_message` 字段,修复业务问题后手动重试
- 无法修复 → 标记为已处理: `UPDATE sys_message_outbox SET status='DELIVERED' WHERE message_id='xxx'`

---

## 五、性能调优

### 5.1 Outbox 调优

**调度频率**（`batch-size` 为 `MessageOutboxDispatcher` 内固定值 100，不可配置）:
```yaml
apex:
  outbox:
    dispatcher:
      fixed-delay-ms: 3000
```
- 降低 `fixed-delay-ms` 减少投递延迟

**重试策略**（消息 Outbox 无 `apex.outbox.retry.*` 配置键；重试上限与延迟由
`sys_message_outbox` 表列 `max_attempts`（默认 5）、`retry_delay_seconds`（默认 10）按行控制。
领域事件 Outbox 使用独立键 `apex.events.outbox.max-attempts`，默认 8）:
- 提高单行 `max_attempts` 增加重试次数
- 降低 `retry_delay_seconds` 加快重试速度

### 5.2 幂等性调优

**可用配置**（幂等记录走 Redis；`LocalIdempotentCache` 组件存在但当前无消费方接线，
`idempotent-fallback-strategy` 键无实现，勿按 LOCAL/REJECT/ALLOW 三策略设计依赖）:
```yaml
mq:
  consumer:
    local-idempotent-max-size: 20000
    idempotent-ttl-minutes: 60
```

### 5.3 消费者调优

当前 `rocketmq.consumer` 仅配置 `enabled` 开关；以下 RocketMQ 客户端参数项目未配置，
如需调整请在部署层显式添加并自行验证（以 rocketmq-spring 默认值运行）：
`consume-thread-min`、`consume-thread-max`、`pull-batch-size`、`consume-message-batch-max-size`。

---

## 六、运维脚本

### 6.1 清理历史消息

**定期清理已投递消息**(保留7天):
```sql
DELETE FROM sys_message_outbox 
WHERE status = 'DELIVERED' 
AND delivered_at < DATE_SUB(NOW(), INTERVAL 7 DAY)
LIMIT 1000;
```

建议设置定时任务(crontab):
```bash
0 2 * * * mysql -u root -p'password' apex_db -e "DELETE FROM sys_message_outbox WHERE status = 'DELIVERED' AND delivered_at < DATE_SUB(NOW(), INTERVAL 7 DAY) LIMIT 1000;"
```

### 6.2 释放过期锁

**释放超过5分钟的锁**:
```sql
UPDATE sys_message_outbox 
SET locked_by = NULL, 
    locked_at = NULL, 
    status = 'PENDING'
WHERE status = 'PROCESSING' 
AND locked_at < DATE_SUB(NOW(), INTERVAL 5 MINUTE);
```

### 6.3 手动重试死信消息

**重置死信消息为待投递**:
```sql
UPDATE sys_message_outbox 
SET status = 'PENDING',
    attempts = 0,
    error_message = NULL,
    next_attempt_at = NOW()
WHERE message_id = '指定消息ID';
```

---

## 七、容量规划

### 7.1 Outbox 表容量

**预估公式**:
```
日消息量 = TPS × 86400
保留天数 = 7
表行数 = 日消息量 × 保留天数 × (1 - 投递成功率)
```

**示例**(TPS=1000,保留7天,成功率99%):
```
表行数 = 1000 × 86400 × 7 × 0.01 = 604.8万行
存储空间 ≈ 600万 × 2KB = 12GB
```

**优化建议**:
- 使用分区表按月分区
- 定期清理历史数据
- 考虑使用时序数据库

### 7.2 Redis 容量

**幂等键容量**:
```
幂等键大小 = key长度 + value长度 ≈ 100字节
并发消息数 = TPS × TTL(秒)
Redis内存 = 并发消息数 × 100字节
```

**示例**(TPS=1000,TTL=30分钟):
```
并发消息数 = 1000 × 1800 = 180万
Redis内存 = 180万 × 100 = 180MB
```

---

## 八、最佳实践

### 8.1 消息发送

1. **优先使用 sendSyncWithOutbox()**
   - 保证消息与业务事务原子性
   - 自动持久化和重试

2. **顺序消息使用稳定的 hashKey**
   - 用订单号、用户ID等业务唯一标识
   - 避免使用时间戳等不稳定值

3. **合理设置超时时间**
   - 同步发送: 30秒
   - 异步发送: 10秒
   - 事务消息: 60秒

### 8.2 消息消费

1. **保持幂等性**
   - 使用消息ID作为幂等键
   - 在业务层面也要实现幂等

2. **快速失败**
   - 消费失败立即返回,触发重试
   - 避免长时间阻塞

3. **异常处理**
   - 业务异常: 返回失败,触发重试
   - 技术异常: 记录日志,人工介入

### 8.3 监控告警

1. **设置合理的告警阈值**
   - 待投递消息 > 1000
   - 死信消息 > 0
   - 消费失败率 > 5%

2. **定期巡检**
   - 每天检查 Outbox 表积压情况
   - 每周清理历史数据
   - 每月复盘性能指标

---

## 九、FAQ

### Q1: Outbox 表会不会无限增长?

**A**: 需要定期清理。建议:
1. 设置定时任务每天清理7天前的 DELIVERED 消息
2. 使用分区表按月分区,定期删除旧分区
3. 监控表大小,及时调整清理策略

### Q2: Redis 不可用时会怎样?

**A**: 
1. 自动降级到 `LocalIdempotentCache` 本地内存缓存
2. 仅保证单实例幂等性
3. 触发告警通知运维修复 Redis
4. Redis 恢复后自动切回分布式幂等

### Q3: 如何保证消息不丢失?

**A**: 三层保障:
1. **生产者侧**: 消息先持久化到本地数据库
2. **Broker侧**: 配置同步刷盘和主从复制
3. **消费者侧**: ACK机制+死信重试

### Q4: 顺序消息如何保证不乱序?

**A**:
1. 发送端使用 `sendOrderly()` 传入稳定的 hashKey
2. 消费端配置 `ConsumeMode.ORDERLY`
3. 使用 `processOrderlyMessage()` 基于分布式锁串行处理

---

**维护团队**: Apex Team  
**技术支持**: apex-support@example.com  
**文档版本**: v2.0

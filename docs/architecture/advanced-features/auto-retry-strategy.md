# 自动重试策略

## 一、概述

Apex Scaffold 提供了完善的自动重试机制，涵盖 **应用层重试（@AutoRetry）**、**响应式重试（Reactor Retry）**、**消息队列重试（RocketMQ）** 和 **领域事件重试（Outbox）**，通过指数退避、异常分类、幂等控制等策略，确保系统在临时性故障下的自动恢复能力。

### 1.1 架构设计目标

- **自动恢复**：临时性故障（网络抖动、超时）自动重试
- **避免放大**：非幂等操作禁止重试，防止业务错误
- **指数退避**：避免瞬间重试导致雪崩
- **异常分类**：区分可重试异常和不可重试异常
- **可观测性**：记录重试次数、成功率、失败原因

### 1.2 重试体系

| 层级 | 机制 | 适用场景 | 重试策略 |
|------|------|---------|---------|
| **应用层** | `@AutoRetry` 注解 | 幂等业务方法、缓存刷新 | 指数退避，最多 3 次 |
| **响应式** | Reactor `retry()` | 外部 API 调用、DB 查询 | 退避 + 抖动，最多 5 次 |
| **消息队列** | RocketMQ 重消费 | 消息处理失败 | 延迟队列，最多 16 次 |
| **领域事件** | Outbox 轮询重试 | 事件发送失败 | 指数退避，最多 10 次 |
| **乐观锁** | 版本冲突重试 | 并发更新 | 快速重试，最多 3 次 |

### 1.3 适用场景

| 场景 | 重试策略 | 幂等保障 | 说明 |
|------|---------|---------|------|
| **外部 API 调用** | Reactor retry | 幂等 Key | 网络抖动、超时可重试 |
| **缓存刷新** | @AutoRetry | 天然幂等 | 刷新失败重试 |
| **消息发送** | Outbox | Message ID | 保证至少一次投递 |
| **乐观锁更新** | 版本重试 | 版本号 | 并发冲突重试 |
| **支付回调** | 不重试 | - | 4xx 错误不应重试 |
| **订单创建** | 不重试 | - | 非幂等操作禁止重试 |

---

## 二、架构设计

### 2.1 整体架构

```
┌───────────────────────────────────────────────────────────┐
│                      应用层重试                            │
│  ┌──────────────────────────────────────────────────────┐ │
│  │  @AutoRetry 注解 + AOP                               │ │
│  │  ├─ 指数退避（200ms → 400ms → 800ms）                │ │
│  │  ├─ 异常白名单（TransientException）                  │ │
│  │  └─ 最大尝试次数（maxAttempts=3）                     │ │
│  └──────────────────────────────────────────────────────┘ │
└────────────────────┬──────────────────────────────────────┘
                     ↓
┌───────────────────────────────────────────────────────────┐
│                    响应式重试                              │
│  ┌──────────────────────────────────────────────────────┐ │
│  │  Reactor retry() / retryWhen()                       │ │
│  │  ├─ Retry.backoff(5, Duration.ofMillis(100))        │ │
│  │  ├─ 抖动（jitter=0.5）                               │ │
│  │  └─ 条件过滤（filter non-retryable）                 │ │
│  └──────────────────────────────────────────────────────┘ │
└────────────────────┬──────────────────────────────────────┘
                     ↓
┌───────────────────────────────────────────────────────────┐
│                   消息队列重试                             │
│  ┌──────────────────────────────────────────────────────┐ │
│  │  RocketMQ 重消费机制                                  │ │
│  │  ├─ 延迟等级（1s 5s 10s 30s ... 2h）                 │ │
│  │  ├─ 最大重试 16 次                                    │ │
│  │  └─ 死信队列（DLQ）                                   │ │
│  └──────────────────────────────────────────────────────┘ │
└────────────────────┬──────────────────────────────────────┘
                     ↓
┌───────────────────────────────────────────────────────────┐
│                  领域事件重试                              │
│  ┌──────────────────────────────────────────────────────┐ │
│  │  Outbox Pattern                                      │ │
│  │  ├─ 状态机（PENDING → PROCESSING → DELIVERED）        │ │
│  │  ├─ Worker 轮询 + 分布式锁                            │ │
│  │  └─ 最大重试 10 次 → DEAD                            │ │
│  └──────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────┘
```

### 2.2 指数退避算法

```java
delay = min(
    initialDelay × (backoffMultiplier ^ attempt),
    maxDelay
)

示例：
- initialDelay = 200ms
- backoffMultiplier = 2.0
- maxDelay = 5000ms

重试序列：
1st: 200ms
2nd: 400ms  (200 × 2^1)
3rd: 800ms  (200 × 2^2)
4th: 1600ms (200 × 2^3)
5th: 3200ms (200 × 2^4)
6th: 5000ms (达到 maxDelay 上限)
```

### 2.3 异常分类

#### 可重试异常（Transient）

```java
// 网络异常
- ConnectException
- SocketTimeoutException
- UnknownHostException

// 数据库临时异常
- TransientDataAccessException
- QueryTimeoutException
- CannotAcquireLockException

// Redis 临时异常
- RedisConnectionException
- JedisConnectionException

// 外部服务临时异常
- HttpServerErrorException (5xx)
- ServiceUnavailableException
```

#### 不可重试异常（Non-Transient）

```java
// 业务异常
- BusinessException
- IllegalArgumentException
- ValidationException

// 客户端错误
- HttpClientErrorException (4xx)
- BadRequestException
- UnauthorizedException

// 数据异常
- DataIntegrityViolationException
- DuplicateKeyException
```

---

## 三、配置与使用

### 3.1 应用层重试（@AutoRetry）

#### 基本用法

```java
@Service
@RequiredArgsConstructor
public class CacheService {
    
    private final RedisClient redisClient;
    
    @AutoRetry(
        maxAttempts = 3,              // 最大尝试次数
        retryDelayMs = 200,           // 初始延迟 200ms
        maxDelayMs = 5000,            // 最大延迟 5s
        backoffMultiplier = 2.0,      // 指数退避倍率
        retryOn = {                   // 可重试异常白名单
            TransientDataAccessException.class,
            RedisConnectionException.class
        }
    )
    public Mono<String> refreshCache(String key) {
        return redisClient.get(key)
            .flatMap(value -> 
                // 缓存刷新逻辑
                dbRepository.findByKey(key)
                    .flatMap(newValue -> redisClient.set(key, newValue))
            );
    }
}
```

#### 注解参数说明

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `maxAttempts` | int | 3 | 最大尝试次数（包括首次） |
| `retryDelayMs` | long | 200 | 初始重试延迟（毫秒） |
| `maxDelayMs` | long | 5000 | 最大重试延迟（毫秒） |
| `backoffMultiplier` | double | 2.0 | 指数退避倍率 |
| `retryOn` | Class[] | {} | 可重试异常白名单 |

#### 使用限制

⚠️ **仅用于幂等操作**

```java
// ✅ 可以使用 @AutoRetry
@AutoRetry
public Mono<Void> refreshCache(String key) { ... }  // 幂等

@AutoRetry
public Mono<User> getUserById(Long id) { ... }     // 幂等

// ❌ 禁止使用 @AutoRetry
@AutoRetry
public Mono<Order> createOrder(OrderRequest req) { ... }  // 非幂等，会重复下单

@AutoRetry
public Mono<Void> deductBalance(Long userId, BigDecimal amount) { ... }  // 非幂等，会重复扣款
```

### 3.2 响应式重试（Reactor）

#### 基本 retry()

```java
public Mono<ApiResponse> callExternalApi(ApiRequest request) {
    return webClient.post()
        .uri("/api/endpoint")
        .bodyValue(request)
        .retrieve()
        .bodyToMono(ApiResponse.class)
        .retry(3);  // 简单重试 3 次，无延迟
}
```

#### 指数退避 retryWhen()

```java
public Mono<ApiResponse> callExternalApiWithBackoff(ApiRequest request) {
    return webClient.post()
        .uri("/api/endpoint")
        .bodyValue(request)
        .retrieve()
        .bodyToMono(ApiResponse.class)
        .retryWhen(Retry.backoff(5, Duration.ofMillis(100))  // 最多 5 次，初始 100ms
            .maxBackoff(Duration.ofSeconds(10))              // 最大延迟 10s
            .jitter(0.5)                                     // 抖动系数 50%
            .filter(ex -> ex instanceof TransientException)  // 只重试临时异常
            .onRetryExhaustedThrow((spec, signal) -> 
                new RetryExhaustedException("重试耗尽", signal.failure())
            )
        );
}
```

#### 条件重试

```java
public Mono<Result> processWithConditionalRetry() {
    return businessService.process()
        .retryWhen(Retry.max(3)
            .filter(ex -> {
                // 只重试 5xx 错误
                if (ex instanceof HttpServerErrorException) {
                    HttpServerErrorException httpEx = (HttpServerErrorException) ex;
                    return httpEx.getStatusCode().is5xxServerError();
                }
                return ex instanceof TransientException;
            })
            .doBeforeRetry(signal -> 
                log.warn("Retry attempt {}: {}", signal.totalRetries(), signal.failure().getMessage())
            )
        );
}
```

### 3.3 消息队列重试（RocketMQ）

#### 消费者幂等处理

```java
@Service
@RocketMQMessageListener(
    topic = "order-topic",
    consumerGroup = "order-consumer-group"
)
public class OrderMessageListener implements RocketMQListener<OrderMessage> {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final OrderService orderService;
    
    @Override
    public void onMessage(OrderMessage message) {
        String messageId = message.getMessageId();
        String idempotentKey = "mq:idempotent:" + messageId;
        
        // 幂等检查
        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(idempotentKey, "1", Duration.ofMinutes(30));
        
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("消息重复，跳过处理: {}", messageId);
            return;  // 返回 CONSUME_SUCCESS，不再重试
        }
        
        try {
            // 业务处理
            orderService.processOrder(message.getOrderId()).block();
            
            log.info("消息处理成功: {}", messageId);
        } catch (Exception ex) {
            log.error("消息处理失败，将触发重试: {}", messageId, ex);
            
            // 删除幂等 Key，允许重试
            redisTemplate.delete(idempotentKey);
            
            // 抛出异常，触发 RocketMQ 重消费
            throw new RuntimeException("消息处理失败", ex);
        }
    }
}
```

#### RocketMQ 重试配置

```yaml
rocketmq:
  consumer:
    max-reconsume-times: 16           # 最大重试次数
```

**重试延迟等级**：
```
1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
```

**重试流程**：
```
1st: 立即消费
2nd: 延迟 10s
3rd: 延迟 30s
4th: 延迟 1m
...
16th: 延迟 2h
17th: 进入死信队列（DLQ）
```

### 3.4 领域事件重试（Outbox）

#### Outbox 模式

```java
@Service
@RequiredArgsConstructor
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    
    @Transactional
    public Mono<Order> createOrder(OrderCreateRequest request) {
        return orderRepository.save(Order.from(request))
            .flatMap(order -> {
                // 写入 Outbox
                OutboxRecord record = OutboxRecord.builder()
                    .aggregateType("Order")
                    .aggregateId(order.getId().toString())
                    .eventType("OrderCreated")
                    .payload(JsonUtil.toJson(order))
                    .status(OutboxStatus.PENDING)
                    .build();
                
                return outboxRepository.save(record)
                    .thenReturn(order);
            });
    }
}
```

#### Outbox Worker

```java
@Component
@RequiredArgsConstructor
public class OutboxWorker {
    
    private final OutboxRepository outboxRepository;
    private final RocketMQTemplate rocketMQTemplate;
    
    @Scheduled(fixedDelay = 5000)  // 每 5 秒轮询一次
    public void processOutbox() {
        outboxRepository.findPendingRecords(100)  // 批量获取 100 条
            .flatMap(record -> {
                // 尝试获取分布式锁
                return lockService.tryLock("outbox:" + record.getId())
                    .flatMap(locked -> {
                        if (!locked) {
                            return Mono.empty();
                        }
                        
                        // 更新为 PROCESSING
                        return outboxRepository.updateStatus(record.getId(), OutboxStatus.PROCESSING)
                            .then(sendMessage(record))
                            .flatMap(success -> {
                                if (success) {
                                    // 发送成功 → DELIVERED
                                    return outboxRepository.updateStatus(record.getId(), OutboxStatus.DELIVERED);
                                } else {
                                    // 发送失败 → 增加重试次数
                                    return outboxRepository.incrementRetryCount(record.getId())
                                        .flatMap(retryCount -> {
                                            if (retryCount >= 10) {
                                                // 超过最大重试次数 → DEAD
                                                return outboxRepository.updateStatus(record.getId(), OutboxStatus.DEAD);
                                            } else {
                                                // 恢复为 PENDING，等待下次重试
                                                return outboxRepository.updateStatus(record.getId(), OutboxStatus.PENDING);
                                            }
                                        });
                                }
                            })
                            .doFinally(signal -> lockService.unlock("outbox:" + record.getId()));
                    });
            })
            .subscribe();
    }
}
```

#### Outbox 配置

```yaml
apex:
  outbox:
    batch-size: 100                    # 批量处理大小
    max-attempts: 10                   # 最大重试次数
    lock-timeout-seconds: 60           # 分布式锁超时时间
    parallelism: 4                     # 并行度
    retention-delivered-days: 7        # DELIVERED 保留天数
    retention-dead-days: 30            # DEAD 保留天数
```

### 3.5 乐观锁重试

```java
@Service
@RequiredArgsConstructor
public class AccountService {
    
    private final AccountRepository accountRepository;
    
    public Mono<Account> deductBalance(Long accountId, BigDecimal amount) {
        return Mono.defer(() -> 
            accountRepository.findById(accountId)
                .flatMap(account -> {
                    account.deductBalance(amount);  // 业务逻辑
                    return accountRepository.save(account);
                })
        )
        .retryWhen(Retry.max(3)
            .filter(ex -> ex instanceof OptimisticLockingFailureException)
            .doBeforeRetry(signal -> 
                log.warn("乐观锁冲突，重试: attempt={}", signal.totalRetries())
            )
        );
    }
}
```

---

## 四、最佳实践

### 4.1 幂等性保障

#### 方案 1：唯一 Key（推荐）

```java
public Mono<PaymentResult> processPayment(PaymentRequest request) {
    String idempotentKey = "payment:" + request.getOrderId();
    
    return redisTemplate.opsForValue()
        .setIfAbsent(idempotentKey, "processing", Duration.ofMinutes(5))
        .flatMap(acquired -> {
            if (!Boolean.TRUE.equals(acquired)) {
                return Mono.error(new DuplicateRequestException());
            }
            
            return paymentGateway.pay(request)
                .flatMap(result -> 
                    redisTemplate.opsForValue()
                        .set(idempotentKey, JsonUtil.toJson(result), Duration.ofHours(24))
                        .thenReturn(result)
                );
        });
}
```

#### 方案 2：数据库唯一约束

```sql
CREATE TABLE `payment` (
  `id` BIGINT PRIMARY KEY,
  `order_id` VARCHAR(64) UNIQUE NOT NULL,  -- 唯一约束
  `amount` DECIMAL(10,2)
);
```

#### 方案 3：Token 机制

```java
// 1. 前端请求 Token
public Mono<String> getIdempotentToken() {
    String token = UUID.randomUUID().toString();
    return redisTemplate.opsForValue()
        .set("token:" + token, "1", Duration.ofMinutes(5))
        .thenReturn(token);
}

// 2. 业务处理时消费 Token
public Mono<Result> processWithToken(String token, Request request) {
    return redisTemplate.delete("token:" + token)
        .flatMap(deleted -> {
            if (deleted == 0) {
                return Mono.error(new TokenInvalidException());
            }
            
            return businessService.process(request);
        });
}
```

### 4.2 重试策略选择

| 场景 | 推荐策略 | 理由 |
|------|---------|------|
| **外部 API（5xx）** | Reactor retry + 指数退避 | 网络抖动，快速恢复 |
| **DB 查询超时** | @AutoRetry 或 Reactor retry | 临时性能问题 |
| **乐观锁冲突** | 快速重试（3次，无延迟） | 并发冲突，立即重试可能成功 |
| **消息处理失败** | RocketMQ 重消费 | 延迟重试，避免瞬间压力 |
| **领域事件投递** | Outbox 轮询 | 保证至少一次，可靠性优先 |
| **非幂等操作** | 不重试 | 避免业务错误放大 |

### 4.3 退避策略对比

```java
// 线性退避（不推荐）
delay = initialDelay × attempt
// 100ms, 200ms, 300ms, 400ms, 500ms

// 指数退避（推荐）
delay = initialDelay × (2 ^ attempt)
// 100ms, 200ms, 400ms, 800ms, 1600ms

// 指数退避 + 抖动（最佳）
delay = (initialDelay × (2 ^ attempt)) × (1 + random(-0.5, 0.5))
// 150ms, 300ms, 600ms, 1200ms, 2400ms (±50% 抖动)
```

**抖动的作用**：
- 避免多个实例同时重试（雷击效应）
- 降低下游服务瞬间压力
- 提高整体成功率

---

## 五、监控与运维

### 5.1 Prometheus 指标

```prometheus
# 重试次数
apex_retry_total{method="refreshCache",result="success"} 125
apex_retry_total{method="refreshCache",result="failure"} 5

# Outbox 状态
apex_outbox_records{status="PENDING"} 10
apex_outbox_records{status="PROCESSING"} 2
apex_outbox_records{status="DELIVERED"} 9850
apex_outbox_records{status="DEAD"} 3
```

### 5.2 Grafana 面板

```yaml
- title: "重试成功率"
  expr: |
    sum(apex_retry_total{result="success"}) 
    / 
    sum(apex_retry_total)

- title: "Outbox 积压"
  expr: apex_outbox_records{status="PENDING"}
```

### 5.3 告警规则

```yaml
- alert: HighRetryFailureRate
  expr: |
    rate(apex_retry_total{result="failure"}[5m]) 
    / 
    rate(apex_retry_total[5m]) > 0.2
  for: 10m
  labels:
    severity: warning

- alert: OutboxBacklog
  expr: apex_outbox_records{status="PENDING"} > 1000
  for: 5m
  labels:
    severity: critical
```

---

## 六、相关文档

- [高级功能使用指南](../../05-operations/advanced-features.md)
- [安全防护全景评估](../../05-operations/security-protections.md)
- [MQ 重试配置加固计划](../../_archive/superpowers/plans/2026-08-30-mq-retry-configuration-hardening.md)
- [配置项详解](../../05-operations/configuration.md)

---

**版本**: v1.0.0  
**最后更新**: 2026-09-04

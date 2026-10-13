> **专题架构图**：[Redis Watchdog 分布式锁](../diagrams/64_redis_watchdog_lock.svg)

# Redis Watchdog（看门狗）机制详解

## 📋 概述

Redisson 的 Watchdog（看门狗）机制是一种自动续期机制，用于防止分布式锁在业务执行过程中提前释放。本文档详细介绍 Watchdog 的工作原理、配置方法、使用场景和常见问题排查。

---

## 🔧 工作原理

### 1. 核心机制

Watchdog 是 Redisson 分布式锁的自动续期机制，当满足以下条件时自动启用：

1. **触发条件**：获取锁时 `leaseTime` 参数设置为 `-1`
2. **续期间隔**：每隔 `lockWatchdogTimeout / 3` 时间自动续期一次
3. **续期时长**：每次续期将锁的过期时间延长 `lockWatchdogTimeout` 毫秒
4. **停止条件**：业务方法执行完毕主动释放锁，或进程崩溃无法续期

### 2. 时序图

```
客户端线程              Redisson Watchdog           Redis Server
   |                         |                         |
   |-- tryLock(waitTime, -1, TimeUnit.SECONDS) ------>|
   |<-------- 获取锁成功 (key: lock:user:123) --------|
   |                         |                         |
   |-- 执行业务逻辑 -------->|                         |
   |                         |                         |
   |                    [10秒后]                       |
   |                         |-- EXPIRE lock:user:123 30000 -->|
   |                         |<------ OK ----------------|
   |                         |                         |
   |                    [再过10秒]                     |
   |                         |-- EXPIRE lock:user:123 30000 -->|
   |                         |<------ OK ----------------|
   |                         |                         |
   |-- 业务执行完毕 -------->|                         |
   |-- unlock() ------------>|-- DEL lock:user:123 --->|
   |<-------- OK ------------|<------ OK ----------------|
```

### 3. 实现细节

Redisson 在内部使用一个守护线程池（`nettyThreads`）执行续期任务：

```java
// Redisson 内部伪代码
private void scheduleExpirationRenewal(long threadId) {
    ExpirationEntry entry = new ExpirationEntry();
    ExpirationEntry oldEntry = EXPIRATION_RENEWAL_MAP.putIfAbsent(getEntryName(), entry);
    
    if (oldEntry == null) {
        renewExpiration(); // 启动续期任务
    }
}

private void renewExpiration() {
    // 每隔 internalLockLeaseTime / 3 执行一次
    timeout = commandExecutor.getConnectionManager().newTimeout(task -> {
        // 续期逻辑：EXPIRE key internalLockLeaseTime
        renewExpirationAsync(threadId);
    }, internalLockLeaseTime / 3, TimeUnit.MILLISECONDS);
}
```

---

## ⚙️ 配置说明

### 1. 配置参数

在 `application.yml` 中配置：

```yaml
apex:
  redis:
    # 看门狗超时时间（毫秒）：默认 30000（30 秒）
    # 续期间隔 = lockWatchdogTimeout / 3 = 10 秒
    lock-watchdog-timeout: 30000
    
    # Redis 命令超时时间（毫秒）：默认 3000（3 秒）
    command-timeout: 3000
    
    # 命令失败重试次数：默认 3 次
    retry-attempts: 3
    
    # 重试间隔（毫秒）：默认 1500（1.5 秒）
    retry-interval: 1500
```

### 2. Java 配置类

参考 `RedissonConfig.java` 和 `RedissonProperties.java`：

```java
@Configuration
@EnableConfigurationProperties(RedissonProperties.class)
public class RedissonConfig {
    
    @Bean
    public RedissonClient redissonClient(RedissonProperties properties) {
        Config config = new Config();
        config.useSingleServer()
              .setAddress("redis://" + host + ":" + port)
              .setTimeout(properties.getCommandTimeout())
              .setRetryAttempts(properties.getRetryAttempts())
              .setRetryInterval(properties.getRetryInterval());
        
        // 配置看门狗超时时间
        config.setLockWatchdogTimeout(properties.getLockWatchdogTimeout());
        
        return Redisson.create(config);
    }
}
```

---

## 💡 使用场景和最佳实践

### 1. 适用场景

#### ✅ 应该使用 Watchdog 的场景

1. **执行时间不确定的任务**
   ```java
   @CacheLock(prefix = "import", key = "#fileId", leaseTime = -1)
   public void importLargeFile(Long fileId) {
       // 文件大小不确定，执行时间可能从几秒到几分钟
       processFile(fileId);
   }
   ```

2. **需要长时间持有锁的场景**
   ```java
   @CacheLock(prefix = "report", key = "#date", leaseTime = -1)
   public void generateMonthlyReport(LocalDate date) {
       // 月度报告生成可能需要 1-5 分钟
       generateReport(date);
   }
   ```

3. **防止缓存击穿**
   ```java
   @CacheLock(prefix = "cache:rebuild", key = "#key", leaseTime = -1)
   public UserEntity rebuildUserCache(String key) {
       // 从数据库查询并重建缓存
       return userRepository.findByKey(key);
   }
   ```

#### ❌ 不应该使用 Watchdog 的场景

1. **执行时间可预期且较短的任务**
   ```java
   // 错误示例：简单查询不需要 Watchdog
   @CacheLock(prefix = "user", key = "#userId", leaseTime = 5, timeUnit = TimeUnit.SECONDS)
   public UserEntity getUser(Long userId) {
       return userRepository.findById(userId);
   }
   ```

2. **对锁释放时间有严格要求的场景**
   ```java
   // 错误示例：库存扣减应该设置固定 leaseTime
   @CacheLock(prefix = "stock", key = "#productId", leaseTime = 3, timeUnit = TimeUnit.SECONDS)
   public boolean deductStock(Long productId, int quantity) {
       return stockService.deduct(productId, quantity);
   }
   ```

### 2. 最佳实践

#### 实践 1：合理设置 lockWatchdogTimeout

```yaml
apex:
  redis:
    # 生产环境推荐值：30 秒（默认）
    # 续期间隔 = 10 秒，足够应对大部分场景
    lock-watchdog-timeout: 30000
```

**原则**：
- 太短（如 10 秒）：续期频繁，增加 Redis 压力
- 太长（如 60 秒）：进程崩溃后锁释放延迟过长
- 推荐值：**30 秒**（Redisson 默认值）

#### 实践 2：显式释放锁

```java
// CacheLockAspect 已自动处理 unlock
@Around("@annotation(cacheLock)")
public Object around(ProceedingJoinPoint joinPoint, CacheLock cacheLock) throws Throwable {
    RLock lock = redissonClient.getLock(lockKey);
    boolean acquired = false;
    
    try {
        acquired = lock.tryLock(cacheLock.waitTime(), cacheLock.leaseTime(), cacheLock.timeUnit());
        if (!acquired) {
            return handleFallback(joinPoint, method, args, cacheLock);
        }
        return joinPoint.proceed();
    } finally {
        if (acquired && lock.isHeldByCurrentThread()) {
            lock.unlock(); // 显式释放锁，停止 Watchdog
        }
    }
}
```

#### 实践 3：监控 Watchdog 续期

在 Prometheus 中监控锁持有时间：

```java
@Component
public class RedisLockMetrics {
    
    private final Counter lockAcquiredCounter;
    private final Timer lockHoldTimeTimer;
    
    public void recordLockAcquired(String lockKey) {
        lockAcquiredCounter.labels(lockKey).inc();
    }
    
    public void recordLockReleased(String lockKey, long holdTimeMs) {
        lockHoldTimeTimer.record(holdTimeMs, TimeUnit.MILLISECONDS);
    }
}
```

Grafana 查询：

```promql
# 锁持有时间 P99
histogram_quantile(0.99, 
  rate(apex_redis_lock_hold_time_seconds_bucket[5m])
)

# 超过 30 秒的锁数量
sum(rate(apex_redis_lock_hold_time_seconds_bucket{le="30"}[5m]))
```

---

## 🐛 常见问题排查

### 问题 1：锁未自动续期，业务执行到一半锁就释放了

**现象**：
```
2026-09-04 10:15:23.456 WARN  [reactor-http-nio-3] c.a.s.i.c.a.CacheLockAspect : 释放锁异常（可能已自动过期）: cache:lock:import:12345
java.lang.IllegalMonitorStateException: attempt to unlock lock, not locked by current thread
```

**原因**：
1. `leaseTime` 未设置为 `-1`，Watchdog 未启用
2. 业务执行时间超过 `lockWatchdogTimeout`，续期失败

**解决方案**：
```java
// 确保 leaseTime = -1
@CacheLock(prefix = "import", key = "#fileId", 
           waitTime = 3000, leaseTime = -1, timeUnit = TimeUnit.MILLISECONDS)
public void importFile(Long fileId) {
    // 业务逻辑
}
```

### 问题 2：Watchdog 续期失败

**现象**：
```
2026-09-04 10:20:15.789 ERROR [redisson-netty-1] o.r.c.c.CommandAsyncService : Can't execute EXPIRE command
io.netty.channel.ConnectTimeoutException: connection timed out: /127.0.0.1:6379
```

**原因**：
1. Redis 连接超时或网络抖动
2. Redis 服务负载过高，响应慢

**解决方案**：
```yaml
apex:
  redis:
    # 增加命令超时时间
    command-timeout: 5000  # 从 3 秒增加到 5 秒
    
    # 增加重试次数
    retry-attempts: 5      # 从 3 次增加到 5 次
```

### 问题 3：进程崩溃后锁未释放

**现象**：
```
2026-09-04 10:25:30.123 WARN  [reactor-http-nio-5] c.a.s.i.c.a.CacheLockAspect : 获取分布式锁失败，执行降级策略: cache:lock:import:12345
```

**原因**：
进程崩溃后 Watchdog 停止续期，但锁在 `lockWatchdogTimeout` 时间内仍然有效

**解决方案**：
1. **等待锁自动过期**（推荐）：等待 `lockWatchdogTimeout` 后锁自动释放
2. **手动删除锁**（紧急情况）：
   ```bash
   # 连接 Redis
   redis-cli
   
   # 查找锁 key
   KEYS cache:lock:import:*
   
   # 删除锁（谨慎操作！）
   DEL cache:lock:import:12345
   ```

### 问题 4：锁持有时间过长

**现象**：
```promql
# Grafana 告警：锁持有时间超过 5 分钟
apex_redis_lock_hold_time_seconds > 300
```

**原因**：
1. 业务逻辑执行时间过长
2. 存在死循环或阻塞操作

**解决方案**：
```java
// 添加超时保护
@CacheLock(prefix = "import", key = "#fileId", waitTime = 3000, leaseTime = -1)
public void importFileWithTimeout(Long fileId) {
    // 使用 Reactor 的 timeout 保护
    Mono.fromCallable(() -> processFile(fileId))
        .timeout(Duration.ofMinutes(5))  // 5 分钟超时
        .subscribeOn(Schedulers.boundedElastic())
        .block();
}
```

---

## 📊 监控指标

### 1. Prometheus 指标

| 指标名称 | 类型 | 说明 |
|---------|------|------|
| `apex.redis.lock.active` | Gauge | 当前活跃锁数量 |
| `apex.redis.lock.acquired.total` | Counter | 累计获取锁次数 |
| `apex.redis.lock.failed.total` | Counter | 获取锁失败次数 |
| `apex.redis.lock.hold.time.seconds` | Histogram | 锁持有时间分布 |
| `apex.redis.watchdog.renewal.total` | Counter | Watchdog 续期次数 |
| `apex.redis.watchdog.renewal.failed.total` | Counter | Watchdog 续期失败次数 |

### 2. Grafana 面板

```json
{
  "title": "Redis 分布式锁监控",
  "panels": [
    {
      "title": "当前活跃锁数量",
      "targets": [{
        "expr": "apex_redis_lock_active"
      }]
    },
    {
      "title": "锁持有时间 P99",
      "targets": [{
        "expr": "histogram_quantile(0.99, rate(apex_redis_lock_hold_time_seconds_bucket[5m]))"
      }]
    },
    {
      "title": "Watchdog 续期成功率",
      "targets": [{
        "expr": "rate(apex_redis_watchdog_renewal_total[5m]) / (rate(apex_redis_watchdog_renewal_total[5m]) + rate(apex_redis_watchdog_renewal_failed_total[5m]))"
      }]
    }
  ]
}
```

---

## 🔗 参考资料

### 官方文档
- [Redisson 分布式锁](https://github.com/redisson/redisson/wiki/8.-Distributed-locks-and-synchronizers)
- [Redisson 配置说明](https://github.com/redisson/redisson/wiki/2.-Configuration#lockwatchdogtimeout)

### 项目文档
- [多级缓存架构](../architecture/advanced-features/multi-level-cache-architecture.md)
- [后端开发规范](../04-development/development-standards.md)
- [缓存锁注解使用](../../apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/cache/annotation/CacheLock.java)

### 相关代码
- 配置类：`RedissonConfig.java`
- 属性类：`RedissonProperties.java`
- 切面类：`CacheLockAspect.java`
- 注解：`@CacheLock`

---

**文档版本**: v1.0.0  
**创建日期**: 2026-09-04  
**维护团队**: Apex Team  
**最后更新**: 2026-09-04

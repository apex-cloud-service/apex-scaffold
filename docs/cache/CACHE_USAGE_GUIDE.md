# 缓存使用指南

本文档介绍 Apex Scaffold 缓存优化功能的使用方法和最佳实践。

## 核心功能

### 1. TTL 抖动（缓存雪崩防护）

**功能**：为缓存 TTL 添加 ±10% 随机抖动，防止大量缓存同时过期导致雪崩。

**实现位置**：`MultiLevelCacheService`

**使用方式**：无需额外配置，自动启用。

```java
multiLevelCacheService.set(key, value, Duration.ofMinutes(30));
```

### 2. 布隆过滤器（缓存穿透防护）

**功能**：使用 Redisson 布隆过滤器快速判断 key 是否存在，防止大量不存在的 key 穿透到数据库。

**配置**：

```yaml
apex:
  cache:
    bloom-filter:
      enabled: true
      expected-insertions: 1000000
      fpp: 0.01
      redis-key-prefix: "apex:cache:bloom:"
```

**使用示例**：

```java
@Autowired
private BloomFilterService bloomFilterService;

public Mono<User> getUser(Long userId) {
    String filterName = "users";
    String key = "user:" + userId;
    
    return bloomFilterService.mightContain(filterName, key)
        .flatMap(exists -> {
            if (!exists) {
                return Mono.empty();
            }
            return multiLevelCacheService.getWithLoader(
                key, User.class, 
                () -> userRepository.findById(userId),
                Duration.ofMinutes(30)
            );
        })
        .doOnSuccess(user -> {
            if (user != null) {
                bloomFilterService.add(filterName, key).subscribe();
            }
        });
}
```

### 3. 分布式锁（缓存击穿防护）

**功能**：使用 Redisson 分布式锁防止热点 key 失效时的并发击穿。

**配置**：

```yaml
apex:
  cache:
    breakdown:
      enabled: true
      lock-wait-time-millis: 3000
      lock-lease-time-millis: 10000
      retry-times: 3
      retry-interval-millis: 100
      logical-expiry-enabled: false
      logical-expiry-refresh-threshold-seconds: 300
```

**使用示例**：

```java
@CacheLock(
    prefix = "user", 
    key = "#userId", 
    waitTime = 3000,
    fallbackStrategy = FallbackStrategy.RETURN_NULL
)
public Mono<User> loadUserFromDb(Long userId) {
    return userRepository.findById(userId);
}
```

### 4. 逻辑过期（热点 key 异步刷新）

**功能**：使用逻辑过期时间，在数据过期时返回旧数据并异步刷新，避免热点 key 缓存击穿。

**使用示例**：

```java
LogicalExpiryWrapper<User> wrapper = LogicalExpiryWrapper.of(user, 3600L);
multiLevelCacheService.set(key, wrapper, Duration.ofHours(2));

public Mono<User> getUserWithLogicalExpiry(Long userId) {
    String key = "user:" + userId;
    return multiLevelCacheService.get(key, LogicalExpiryWrapper.class)
        .flatMap(wrapper -> {
            if (wrapper == null) {
                return loadAndCache(userId);
            }
            if (wrapper.isExpired()) {
                asyncRefresh(userId);
            }
            return Mono.just(wrapper.getData());
        });
}

private void asyncRefresh(Long userId) {
    loadUserFromDb(userId)
        .flatMap(user -> {
            LogicalExpiryWrapper<User> wrapper = LogicalExpiryWrapper.of(user, 3600L);
            return multiLevelCacheService.set("user:" + userId, wrapper, Duration.ofHours(2));
        })
        .subscribeOn(Schedulers.boundedElastic())
        .subscribe();
}
```

### 5. 缓存预热

**功能**：应用启动时自动预热热点数据到缓存。

**配置**：

```yaml
apex:
  cache:
    prewarm:
      enabled: true
      startup-delay-seconds: 30
      batch-size: 100
      batch-interval-millis: 100
```

**实现预热策略**：

```java
@Service
@ConditionalOnProperty(prefix = "apex.cache.prewarm", name = "enabled", havingValue = "true")
public class UserCachePrewarmStrategy implements CachePrewarmStrategy {

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private MultiLevelCacheService cacheService;

    @Override
    public String getName() {
        return "user-cache";
    }

    @Override
    public Mono<Integer> prewarm() {
        return userRepository.findHotUsers()
            .flatMap(user -> cacheService.set(
                "user:" + user.getId(), 
                user, 
                Duration.ofMinutes(30)
            ))
            .count()
            .map(Long::intValue);
    }
}
```

## 最佳实践

### 1. 缓存键设计

```java
private String buildCacheKey(String bizType, Object... params) {
    String tenantId = TenantContext.currentTenantId();
    String paramsStr = String.join(":", 
        Arrays.stream(params).map(String::valueOf).toArray(String[]::new)
    );
    return String.format("%s:%s:%s", tenantId, bizType, paramsStr);
}
```

### 2. 租户隔离

所有缓存 key 必须包含租户 ID，确保租户数据隔离：

```java
String key = TenantContext.currentTenantId() + ":user:" + userId;
```

### 3. TTL 设置建议

- **热点数据**：5-15 分钟
- **普通数据**：30-60 分钟
- **冷数据**：2-6 小时

### 4. 空值标记

使用 `MultiLevelCacheService.getWithLoader()` 自动处理空值标记，防止缓存穿透：

```java
multiLevelCacheService.getWithLoader(
    key, User.class,
    () -> userRepository.findById(userId),
    Duration.ofMinutes(30)
);
```

### 5. 监控指标

关注以下监控指标：

- `apex_cache_read_degradation_total`：读降级次数
- Redis 命中率
- 缓存穿透次数
- 缓存击穿次数

## 配置参考

完整配置示例：

```yaml
apex:
  cache:
    bloom-filter:
      enabled: true
      expected-insertions: 1000000
      fpp: 0.01
      redis-key-prefix: "apex:cache:bloom:"
    
    breakdown:
      enabled: true
      lock-wait-time-millis: 3000
      lock-lease-time-millis: 10000
      retry-times: 3
      retry-interval-millis: 100
      logical-expiry-enabled: false
      logical-expiry-refresh-threshold-seconds: 300
    
    prewarm:
      enabled: true
      startup-delay-seconds: 30
      batch-size: 100
      batch-interval-millis: 100
    
    read-degradation:
      enabled: false
      max-stale-seconds: 3600
```

<a id="故障排查"></a>

## 故障排查

### 布隆过滤器误判

布隆过滤器存在误判率（默认 1%），可能导致已删除的 key 仍然被认为存在。解决方案：

1. 降低误判率（调整 `fpp` 参数）
2. 定期重建布隆过滤器
3. 结合空值标记使用

### 缓存击穿

如果仍然出现缓存击穿，检查：

1. 分布式锁是否正常工作
2. 锁超时配置是否合理
3. 是否启用了逻辑过期

### 缓存雪崩

如果出现缓存雪崩，检查：

1. TTL jitter 是否生效
2. 缓存预热是否正常执行
3. Redis 是否发生故障

## 相关文档

- [缓存优化方案](CACHE_OPTIMIZATION_SOLUTION.md)
- [架构设计文档](../architecture/)
- [部署指南](../05-operations/deployment.md)

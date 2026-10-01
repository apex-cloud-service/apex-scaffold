# Apex Scaffold 缓存穿透 / 击穿 / 雪崩 完整解决方案

> **文档定位**：基于现有 L1 Caffeine + L2 Redis 多级缓存架构，补全缓存三大经典问题的防护方案设计与实施步骤。
> **适用范围**：apex-infrastructure 缓存模块及其调用方（apex-application / apex-interfaces）。
> **状态**：方案待评审；评审通过后按 Phase 1 → Phase 7 顺序实施。
> **作者**：Apex Team · 最后更新 2026-09-16

---

## 目录

- [一、任务背景与目标](#一任务背景与目标)
- [二、现状分析](#二现状分析)
- [三、总体架构设计](#三总体架构设计)
- [四、缓存穿透解决方案](#四缓存穿透解决方案)
- [五、缓存击穿解决方案](#缓存击穿解决方案)
- [六、缓存雪崩解决方案](#六缓存雪崩解决方案)
- [七、监控告警体系](#七监控告警体系)
- [八、实施步骤](#八实施步骤)
- [九、验证方案](#九验证方案)
- [十、关键决策与约束](#十关键决策与约束)
- [十一、风险评估与回滚](#十一风险评估与回滚)

---

## 一、任务背景与目标

### 1.1 问题背景

Apex Scaffold 采用 Spring WebFlux + Caffeine + Redisson 的多级缓存架构。当前已实现基础的 L1+L2 读写、Single-Flight 击穿防护、空值标记穿透防护、TTL jitter 雪崩防护，但防护覆盖不完整，存在以下关键缺口：

1. **缓存穿透**：缺少布隆过滤器前置拦截；`MultiLevelCacheService` 空值标记 TTL 未加 jitter
2. **缓存击穿**：Single-Flight 仅进程内合并，多实例部署下每个实例仍各自回源一次；缺少逻辑过期 + 异步刷新机制
3. **缓存雪崩**：`MultiLevelCacheService.set()` 未应用 TTL jitter（关键缺陷）；缺少缓存预热；缺少 Redis 故障下的降级链路
4. **监控告警**：缺少命中率、回源、穿透拦截、击穿锁等关键指标

### 1.2 设计目标

| 目标 | 衡量标准 |
|---|---|
| 防护完整性 | 穿透/击穿/雪崩三大问题均有 ≥2 道防护机制 |
| 多实例兼容 | 多实例部署下同一 key 回源 DB 仅 1 次 |
| 配置化 | 所有阈值/开关通过 `apex.cache.*` 配置，生产可动态调整 |
| 可观测 | 命中率、回源、降级等指标全部接入 Prometheus |
| 不破坏契约 | `CacheService`、`CacheConsistencyPort` 接口签名兼容 |
| 阻塞隔离 | 所有 Redis 阻塞操作经 `BlockingIoSchedulers.cache()` 隔离 |

---

## 二、现状分析

### 2.1 现有缓存架构组件

| 组件 | 路径 | 职责 |
|---|---|---|
| `MultiLevelCacheService` | [apex-infrastructure/.../cache/service/MultiLevelCacheService.java](../../apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/cache/service/MultiLevelCacheService.java) | 多级缓存核心服务：L1+L2 读写、回源、空值标记、Single-Flight |
| `LocalCacheService` | [apex-infrastructure/.../cache/service/LocalCacheService.java](../../apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/cache/service/LocalCacheService.java) | L1 Caffeine 实现，带独立 TTL 跟踪 |
| `CacheServiceImpl` | [apex-infrastructure/.../cache/service/CacheServiceImpl.java](../../apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/cache/service/CacheServiceImpl.java) | L2 Redis 单层缓存（实现 `CacheService` 接口） |
| `CacheConsistencyService` | [apex-infrastructure/.../cache/service/CacheConsistencyService.java](../../apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/cache/service/CacheConsistencyService.java) | 一致性服务，带 TTL jitter、空值标记、Single-Flight |
| `InFlightRequestMerge` | [apex-infrastructure/.../cache/support/InFlightRequestMerge.java](../../apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/cache/support/InFlightRequestMerge.java) | Single-Flight 请求合并器（进程内） |
| `CacheLockAspect` + `@CacheLock` | [apex-infrastructure/.../cache/aspect/CacheLockAspect.java](../../apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/cache/aspect/CacheLockAspect.java) | 基于 Redisson 的分布式锁切面 |
| `MultiLevelCacheConfig` | [apex-infrastructure/.../cache/config/MultiLevelCacheConfig.java](../../apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/cache/config/MultiLevelCacheConfig.java) | Caffeine CacheManager 配置 |
| `CacheProperties` | [apex-infrastructure/.../cache/config/CacheProperties.java](../../apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/cache/config/CacheProperties.java) | 读降级配置（`apex.cache.read-degradation`） |

### 2.2 现有防护机制评估

| 防护维度 | 现状 | 不足 |
|---|---|---|
| **缓存穿透** | `MultiLevelCacheService` 与 `CacheConsistencyService` 均实现 `__EMPTY__` 空值标记 + 5 分钟 TTL | ① 无布隆过滤器前置拦截 ② 空值标记 TTL 固定未加 jitter，可能引发"空值雪崩" ③ `MultiLevelCacheService.setEmptyMarker()` 未应用 jitter |
| **缓存击穿** | `InFlightRequestMerge` 进程内 Single-Flight；`@CacheLock` 分布式锁可选叠加 | ① Single-Flight 仅进程内，多实例下每实例各自回源一次 ② 缺少"逻辑过期 + 异步刷新"策略 ③ 缺少永不过期 + 后台刷新机制 |
| **缓存雪崩** | `CacheConsistencyService` 中 L2 TTL 与空值标记 TTL 应用 ±10% jitter | ① `MultiLevelCacheService.set()` 未应用 jitter（关键缺陷） ② 缺少缓存预热机制 ③ 缺少 Redis 集群故障下的本地缓存降级 ④ 缺少批量预热工具 |
| **监控告警** | Caffeine 有 `CaffeineCacheMetricsBinder`；`MultiLevelCacheService` 有读降级计数器 | ① 缺少命中率/未命中率/回源次数统一指标 ② 缺少穿透/击穿/雪崩事件计数器与告警 ③ L2 Redis 命中/未命中无指标 |

### 2.3 关键配置参数

| 配置键 | 默认值 | 说明 |
|---|---|---|
| `apex.cache.local.max-size` | 10000 | Caffeine 最大条目数 |
| `apex.cache.local.expire-after-write` | 300 秒 | L1 写后过期（5 分钟） |
| `apex.cache.local.initial-capacity` | 500 | Caffeine 初始容量 |
| `apex.cache.remote.default-ttl` | 3600 秒 | L2 默认 TTL（1 小时） |
| `apex.cache.remote.batch-size` | 100 | 批量操作每批条目数 |
| `apex.cache.read-degradation.enabled` | false | DB 故障读降级开关 |
| `apex.cache.read-degradation.max-stale-seconds` | 3600 | 降级最大过期窗口 |
| `apex.blocking-io.cache.threads` | 4 | 缓存阻塞线程池大小 |

### 2.4 关键缺陷定位

#### 缺陷 1：`MultiLevelCacheService.set()` 未应用 TTL jitter

```java
// 当前实现（MultiLevelCacheService.java#L210-L213）
return Mono.fromCallable(() -> {
    RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
    bucket.set(toStoreValue(value), ttl);  // ← ttl 未做 jitter
    return true;
})
```

**影响**：批量写入相同 TTL 的 key 时，所有 key 在同一时刻过期，可能引发缓存雪崩。

**对比**：`CacheConsistencyService.put()` 已应用 ±10% jitter（[CacheConsistencyService.java#L118](../../apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/cache/service/CacheConsistencyService.java#L118)），但 `MultiLevelCacheService` 未对齐。

#### 缺陷 2：`MultiLevelCacheService.setEmptyMarker()` 未应用 jitter

```java
// 当前实现（MultiLevelCacheService.java#L358-L361）
private Mono<Void> setEmptyMarker(String key) {
    return Mono.fromCallable(() -> {
        RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
        bucket.set(EMPTY_VALUE, EMPTY_TTL);  // ← EMPTY_TTL 固定 5 分钟，未做 jitter
        return true;
    })
}
```

**影响**：攻击者批量探测不存在的 key，5 分钟后所有空值标记同时过期，再次穿透 DB。

#### 缺陷 3：Single-Flight 仅进程内

```java
// InFlightRequestMerge.java#L42-L45
public final class InFlightRequestMerge {
    private final ConcurrentHashMap<String, Mono<?>> inFlight = new ConcurrentHashMap<>();
    // 进程内 Map，不跨实例共享
}
```

**影响**：N 个实例同时缓存未命中，各自触发一次回源，DB 收到 N 次相同查询。

---

## 三、总体架构设计

### 3.1 防护链路全景

```
请求入口
   │
   ▼
[1. 布隆过滤器] ─── 不存在 ──► 直接返回 null（穿透防护第一道）
   │ 存在（可能误判）
   ▼
[2. L1 Caffeine 本地缓存] ─── 命中 ──► 返回
   │ 未命中
   ▼
[3. L2 Redis 分布式缓存] ─── 命中 ──► 回填 L1 ──► 返回
   │ 未命中
   │                    [Redis 故障] ──► 切 L1-only 模式（雪崩降级）
   ▼
[4. Single-Flight + 分布式锁] ─── 同 key 并发合并（击穿防护）
   │ 单个请求穿透
   ▼
[5. DB Loader 回源] ─── DB 故障 ──► 读降级返回过期缓存（雪崩兜底）
   │ 成功
   ▼
[6. 回填缓存（带 TTL jitter）] ─── 写入 L2 + 失效 L1（雪崩防护）
   │
   ▼
[7. 异步刷新（逻辑过期场景）] ─── 后台刷新缓存（击穿防护增强）
```

### 3.2 防护机制矩阵

| 防护维度 | 第一道 | 第二道 | 第三道 |
|---|---|---|---|
| 缓存穿透 | 布隆过滤器（Redisson RBloomFilter） | 空值标记（带 jitter） | DB Loader 回源兜底 |
| 缓存击穿 | Single-Flight（进程内） | 分布式锁（Redisson RLock） | 逻辑过期 + 异步刷新 |
| 缓存雪崩 | TTL jitter（±10%） | 缓存预热（启动后异步） | Redis 故障 L1 降级 + DB 读降级 |

### 3.3 场景分流策略

| 场景 | 命中特征 | 推荐策略 |
|---|---|---|
| 普通热数据（用户、字典、配置） | 中频读、低频写 | Single-Flight + 分布式锁 + TTL jitter |
| 超热点 key（首页推荐、热门商品） | 超高频读 | 逻辑过期 + 异步刷新 + 永不物理过期 |
| 低频数据（历史归档） | 低频读 | 直接回源，无需特殊防护 |
| 不存在的 key（攻击/误查） | 高频读 0 值 | 布隆过滤器 + 空值标记 |
| 启动期冷数据 | 启动瞬间高并发读 | 缓存预热 + 限流回源 |

---

## 四、缓存穿透解决方案

### 4.1 引入 Redisson 布隆过滤器

#### 4.1.1 设计要点

- 使用 `RedissonClient` 的 `RBloomFilter`，存储于 Redis 实现跨实例共享
- 按业务域创建多个布隆过滤器：`apex:bloom:user`、`apex:bloom:menu`、`apex:bloom:dict` 等
- 容量参数：`expectedInsertions = 1_000_000`、`falseProbability = 0.001`（误判率 0.1%）
- 启动时由 `BloomFilterInitializer` 从 DB 加载全量 ID 注册到过滤器
- DB 数据新增/删除时同步更新布隆过滤器（通过领域事件或显式调用）

#### 4.1.2 新增组件

**`BloomFilterService`** — 布隆过滤器服务

```java
// 路径：apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/cache/bloom/BloomFilterService.java
/**
 * 布隆过滤器服务（缓存穿透前置防护）
 *
 * <p>基于 Redisson RBloomFilter 实现跨实例共享的布隆过滤器，
 * 在请求到达 DB 之前拦截确定不存在的 key。
 *
 * @author Apex Team
 * @since 1.4.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BloomFilterService {

    private final RedissonClient redissonClient;
    private final CacheProperties cacheProperties;
    private final MeterRegistry meterRegistry;

    private final Counter bloomRejectCounter;
    private final Counter bloomFalsePositiveCounter;

    /**
     * 检查 key 是否可能存在
     *
     * @param filterName 过滤器名称（业务域）
     * @param key        待检查的 key
     * @return true=可能存在（需继续查缓存/DB），false=一定不存在（直接拦截）
     */
    public Mono<Boolean> mightContain(String filterName, String key) {
        if (!cacheProperties.getBloom().isEnabled()) {
            return Mono.just(true); // 布隆未启用，放行
        }
        return Mono.fromCallable(() -> {
                    RBloomFilter<String> filter = redissonClient.getBloomFilter(filterName);
                    return filter.isExists() && filter.contains(key);
                })
                .subscribeOn(BlockingIoSchedulers.cache())
                .onErrorResume(ex -> {
                    log.warn("布隆过滤器检查异常, filter: {}, key: {}", filterName, key, ex);
                    return Mono.just(true); // 异常时放行，保证可用性
                });
    }

    /**
     * 注册 key 到布隆过滤器
     */
    public Mono<Boolean> add(String filterName, String key) {
        return Mono.fromCallable(() -> {
                    RBloomFilter<String> filter = getOrCreateFilter(filterName);
                    return filter.add(key);
                })
                .subscribeOn(BlockingIoSchedulers.cache());
    }

    /**
     * 批量注册 key
     */
    public Mono<Boolean> addAll(String filterName, Collection<String> keys) {
        // ... 批量写入
    }

    /**
     * 重建过滤器（数据迁移/重置场景）
     */
    public Mono<Boolean> rebuild(String filterName, Collection<String> keys) {
        // ... 删除旧过滤器，重新初始化
    }

    private RBloomFilter<String> getOrCreateFilter(String filterName) {
        RBloomFilter<String> filter = redissonClient.getBloomFilter(filterName);
        if (!filter.isExists()) {
            BloomProperties.BloomDomain domainConfig = cacheProperties.getBloom().getDomains().get(filterName);
            long expectedInsertions = domainConfig != null
                    ? domainConfig.getExpectedInsertions()
                    : cacheProperties.getBloom().getDefaultExpectedInsertions();
            double falseProbability = domainConfig != null
                    ? domainConfig.getFalseProbability()
                    : cacheProperties.getBloom().getDefaultFalseProbability();
            filter.tryInit(expectedInsertions, falseProbability);
        }
        return filter;
    }
}
```

**`BloomFilterInitializer`** — 启动期预热器

```java
// 路径：apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/cache/bloom/BloomFilterInitializer.java
/**
 * 布隆过滤器启动期预热器
 *
 * <p>应用启动后异步从 DB 加载全量业务 key 注册到布隆过滤器。
 * 不阻塞启动；预热失败仅记录 WARN，不影响应用启动。
 *
 * @author Apex Team
 * @since 1.4.0
 */
@Component
@ConditionalOnProperty(prefix = "apex.cache.bloom", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class BloomFilterInitializer implements ApplicationRunner {

    private final BloomFilterService bloomFilterService;
    private final List<BloomFilterDataProvider> dataProviders; // 各业务域数据提供者

    @Override
    public void run(ApplicationArguments args) {
        if (!cacheProperties.getBloom().isEnabled()) {
            return;
        }
        // 异步预热，不阻塞启动
        Mono.fromCallable(() -> prewarmAll())
                .subscribeOn(BlockingIoSchedulers.cache())
                .subscribe(
                        count -> log.info("布隆过滤器预热完成, 总 key 数: {}", count),
                        ex -> log.error("布隆过滤器预热失败", ex)
                );
    }
    // ...
}
```

#### 4.1.3 数据提供者扩展点

```java
/**
 * 布隆过滤器数据提供者接口
 *
 * <p>各业务域实现此接口，提供需要注册到布隆过滤器的全量 key。
 *
 * @author Apex Team
 * @since 1.4.0
 */
public interface BloomFilterDataProvider {
    /** 业务域名称（与配置 apex.cache.bloom.domains.{name} 对应） */
    String domain();
    /** 加载全量 key */
    Mono<Collection<String>> loadAllKeys();
}
```

示例实现：用户域

```java
@Component
@RequiredArgsConstructor
public class UserBloomDataProvider implements BloomFilterDataProvider {

    private final UserMapper userMapper;

    @Override
    public String domain() { return "apex:bloom:user"; }

    @Override
    public Mono<Collection<String>> loadAllKeys() {
        return Mono.fromCallable(() -> userMapper.selectAllUserIds().stream()
                .map(id -> "user:" + id)
                .toList());
    }
}
```

### 4.2 空值标记增强

#### 4.2.1 问题描述

当前 `MultiLevelCacheService.setEmptyMarker()` 写入的空值标记 TTL 固定 5 分钟，未应用 jitter：

```java
// 当前实现
private static final Duration EMPTY_TTL = Duration.ofMinutes(5);

private Mono<Void> setEmptyMarker(String key) {
    return Mono.fromCallable(() -> {
        RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
        bucket.set(EMPTY_VALUE, EMPTY_TTL);  // ← 固定 TTL
        return true;
    })...
}
```

攻击者批量探测不存在的 key，5 分钟后所有空值标记同时过期，再次穿透 DB，形成"空值雪崩"。

#### 4.2.2 改进方案

- 抽取 `EMPTY_TTL` 与 `EMPTY_JITTER_FACTOR` 到 `CacheProperties`
- `setEmptyMarker()` 应用 ±20% jitter（空值标记 TTL 短，需要更大抖动比例）
- 在布隆过滤器误判（即 DB 中确实不存在）时写入空值标记

```java
// 改造后的 MultiLevelCacheService.setEmptyMarker
private Mono<Void> setEmptyMarker(String key) {
    Duration jitteredTtl = jitter(
        cacheProperties.getEmpty().getTtl(),
        cacheProperties.getEmpty().getJitterFactor()
    );
    return Mono.fromCallable(() -> {
                RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
                bucket.set(EMPTY_VALUE, jitteredTtl);
                emptyMarkerCounter.increment();
                return true;
            })
            .subscribeOn(BlockingIoSchedulers.cache())
            .then();
}

/**
 * 对 TTL 添加随机抖动（防雪崩）
 *
 * @param duration     原始 TTL
 * @param jitterFactor 抖动因子（如 0.2 表示 ±20%）
 * @return 抖动后的 TTL
 */
private Duration jitter(Duration duration, double jitterFactor) {
    if (duration == null || duration.isZero() || duration.isNegative()) {
        return duration;
    }
    double factor = 1.0 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 2 * jitterFactor;
    long jitteredMillis = (long) (duration.toMillis() * factor);
    return Duration.ofMillis(Math.max(1, jitteredMillis));
}
```

### 4.3 防穿透链路集成

```java
// MultiLevelCacheService.getWithLoader 改造
public <T> Mono<T> getWithLoader(String key, Class<T> clazz,
                                   Supplier<Mono<T>> loader, Duration ttl) {
    if (key == null) {
        return Mono.empty();
    }

    // 第一道：布隆过滤器前置拦截
    return bloomFilterService.mightContain(resolveFilterName(key), key)
            .flatMap(mightExist -> {
                if (!mightExist) {
                    // 布隆判定一定不存在，直接返回空（穿透防护第一道）
                    bloomRejectCounter.increment();
                    return Mono.empty();
                }
                // 可能存在，继续走多级缓存 + 回源
                return getWithLoaderInternal(key, clazz, loader, ttl);
            });
}

/**
 * 从缓存键推导布隆过滤器名称
 *
 * <p>缓存键格式：apex:{tenantId}:{domain}:{bizKey}
 * 提取 domain 部分映射到对应的布隆过滤器
 */
private String resolveFilterName(String key) {
    // 例：apex:tenant1:user:123 → apex:bloom:user
    String[] parts = key.split(":");
    if (parts.length >= 3) {
        return "apex:bloom:" + parts[2];
    }
    return "apex:bloom:default";
}
```

---

## 五、缓存击穿解决方案

### 5.1 现有 Single-Flight 保留

保留 `InFlightRequestMerge` 的进程内合并能力，作为本地实例的快速防护。该组件已实现：

- 同 key 并发回源合并为一次
- 终态语义明确（成功/空值/错误）
- `doFinally` 精确移除条目，避免误删

详见 [InFlightRequestMerge.java](../../apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/cache/support/InFlightRequestMerge.java)。

### 5.2 分布式锁增强（多实例场景）

#### 5.2.1 问题分析

多实例部署下，每个实例的 Single-Flight 各自合并，仍然有 N 个实例同时回源 DB：

```
Instance A: Single-Flight 合并 100 并发 → 1 次回源
Instance B: Single-Flight 合并 100 并发 → 1 次回源
Instance C: Single-Flight 合并 100 并发 → 1 次回源
                                        ↓
                                DB 收到 3 次相同查询
```

#### 5.2.2 方案设计

在 `getWithLoader()` 回源段叠加 Redisson 分布式锁，作为多实例互斥兜底：

- 锁粒度：`cache:lock:reload:{key}`
- waitTime：200ms（短等待，避免阻塞请求线程）
- leaseTime：30s（覆盖 DB 查询最长耗时）
- 获取锁失败：自旋重试读缓存（其他实例回源后会写入），最多重试 3 次，每次间隔 50ms

```java
/**
 * Single-Flight + 分布式锁的组合回源
 *
 * <p>策略：
 * <ol>
 *   <li>进程内 Single-Flight 合并本实例并发（第一道）</li>
 *   <li>分布式锁保证多实例只有一个回源（第二道）</li>
 *   <li>获取锁失败时自旋重试读缓存（其他实例回源后会写入）</li>
 * </ol>
 */
private <T> Mono<T> loadWithDistributedLock(String key, Class<T> clazz,
                                             Supplier<Mono<T>> loader, Duration ttl) {
    String lockKey = "cache:lock:reload:" + key;
    long waitTimeMs = cacheProperties.getLock().getWaitTimeMs();
    long leaseTimeMs = cacheProperties.getLock().getLeaseTimeMs();

    return inFlight.join(key, () ->
        Mono.fromCallable(() -> redissonClient.getLock(lockKey))
            .subscribeOn(BlockingIoSchedulers.cache())
            .flatMap(lock -> tryLockAndLoad(lock, key, clazz, loader, ttl, waitTimeMs, leaseTimeMs))
    );
}

private <T> Mono<T> tryLockAndLoad(RLock lock, String key, Class<T> clazz,
                                     Supplier<Mono<T>> loader, Duration ttl,
                                     long waitTimeMs, long leaseTimeMs) {
    return Mono.fromCallable(() -> lock.tryLock(waitTimeMs, leaseTimeMs, TimeUnit.MILLISECONDS))
            .subscribeOn(BlockingIoSchedulers.cache())
            .flatMap(acquired -> {
                if (acquired) {
                    lockAcquireCounter.increment("success");
                    return doLoadAndRelease(lock, key, clazz, loader, ttl);
                }
                // 获取锁失败，自旋重试读缓存
                lockAcquireCounter.increment("fail");
                return spinRetryReadCache(key, clazz, loader, ttl, 3);
            });
}

/**
 * 自旋重试读缓存
 *
 * <p>获取分布式锁失败说明其他实例正在回源，
 * 短暂等待后重试读缓存（其他实例回源后会写入缓存）
 */
private <T> Mono<T> spinRetryReadCache(String key, Class<T> clazz,
                                         Supplier<Mono<T>> loader, Duration ttl,
                                         int retryCount) {
    if (retryCount <= 0) {
        // 重试次数耗尽，直接回源（兜底）
        return loader.get().flatMap(dbValue -> set(key, dbValue, ttl).thenReturn(dbValue));
    }
    return Mono.delay(Duration.ofMillis(50))
            .then(get(key, clazz))
            .switchIfEmpty(spinRetryReadCache(key, clazz, loader, ttl, retryCount - 1));
}
```

#### 5.2.3 与现有 `@CacheLock` 的关系

| 场景 | 使用方式 |
|---|---|
| 业务方法级锁（防重复提交） | `@CacheLock` 注解（已有，业务方显式标注） |
| 缓存回源互斥（自动） | `MultiLevelCacheService` 内部直接调用 `RLock`（本次新增） |

两者不冲突：`@CacheLock` 是业务侧显式控制，`MultiLevelCacheService` 内部锁是缓存层自动控制。

### 5.3 逻辑过期 + 异步刷新

#### 5.3.1 适用场景

对超热点 key（首页推荐、热门商品、热搜词），物理 TTL 设为较长（如 24 小时），但在 value 中嵌入逻辑过期时间（如 5 分钟）。读取时若逻辑过期，立即返回旧值并触发异步刷新。

**优势**：
- 物理缓存永不穿透（24 小时内始终命中）
- 逻辑过期后短暂返回旧值，可用性优先
- 异步刷新由一个请求触发，不阻塞业务

#### 5.3.2 设计实现

**`LogicalExpiryWrapper`** — 逻辑过期包装器

```java
// 路径：apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/cache/support/LogicalExpiryWrapper.java
/**
 * 逻辑过期包装器
 *
 * <p>包装缓存值并附加逻辑过期时间戳，用于超热点 key 的逻辑过期 + 异步刷新策略。
 * 物理过期时间由 Redis TTL 控制（较长，如 24 小时），
 * 逻辑过期时间用于触发异步刷新（较短，如 5 分钟）。
 *
 * @author Apex Team
 * @since 1.4.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LogicalExpiryWrapper<T> {

    /** 实际缓存值 */
    private T value;

    /** 逻辑过期时间戳（毫秒） */
    private long logicalExpireAt;

    /** 创建时间戳（毫秒） */
    private long createdAt;

    /**
     * 是否已逻辑过期
     */
    public boolean isLogicalExpired() {
        return System.currentTimeMillis() >= logicalExpireAt;
    }

    /**
     * 构造包装器
     *
     * @param value         缓存值
     * @param logicalTtl    逻辑过期时长
     * @return 包装器实例
     */
    public static <T> LogicalExpiryWrapper<T> of(T value, Duration logicalTtl) {
        long now = System.currentTimeMillis();
        return new LogicalExpiryWrapper<>(value, now + logicalTtl.toMillis(), now);
    }
}
```

**`HotKeyCacheService`** — 超热点 key 服务

```java
// 路径：apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/cache/service/HotKeyCacheService.java
/**
 * 超热点 key 缓存服务
 *
 * <p>针对首页推荐、热门商品等超高频访问数据，采用逻辑过期 + 异步刷新策略：
 * <ul>
 *   <li>物理 TTL 较长（如 24 小时），保证物理缓存始终命中</li>
 *   <li>逻辑 TTL 较短（如 5 分钟），过期后返回旧值 + 异步刷新</li>
 *   <li>异步刷新由分布式锁保证只有一个请求执行</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.4.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HotKeyCacheService {

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final CacheProperties cacheProperties;
    private final MeterRegistry meterRegistry;

    private final Counter logicalRefreshCounter;

    /**
     * 读取超热点 key
     *
     * @param key           缓存键
     * @param clazz         值类型
     * @param loader        回源加载器
     * @param logicalTtl    逻辑过期时长
     * @param physicalTtl   物理过期时长
     * @return 缓存值或回源值
     */
    public <T> Mono<T> get(String key, Class<T> clazz,
                            Supplier<Mono<T>> loader, Duration logicalTtl, Duration physicalTtl) {
        return readLogicalWrapper(key, clazz)
                .flatMap(wrapper -> {
                    if (wrapper == null) {
                        // 缓存未命中，首次加载
                        return loadAndWrap(key, clazz, loader, logicalTtl, physicalTtl);
                    }
                    if (!wrapper.isLogicalExpired()) {
                        // 逻辑未过期，直接返回
                        return Mono.just(wrapper.getValue());
                    }
                    // 逻辑过期，返回旧值 + 异步刷新
                    triggerAsyncRefresh(key, clazz, loader, logicalTtl, physicalTtl);
                    return Mono.just(wrapper.getValue());
                });
    }

    /**
     * 异步触发刷新（仅一个请求执行，通过分布式锁保证）
     */
    private <T> void triggerAsyncRefresh(String key, Class<T> clazz,
                                           Supplier<Mono<T>> loader,
                                           Duration logicalTtl, Duration physicalTtl) {
        String lockKey = "cache:lock:refresh:" + key;
        Mono.fromCallable(() -> {
                    RLock lock = redissonClient.getLock(lockKey);
                    return lock.tryLock(0, 30_000, TimeUnit.MILLISECONDS); // 不等待
                })
                .subscribeOn(BlockingIoSchedulers.cache())
                .flatMap(acquired -> {
                    if (acquired) {
                        logicalRefreshCounter.increment();
                        return loadAndWrap(key, clazz, loader, logicalTtl, physicalTtl)
                                .doFinally(s -> releaseLock(lockKey));
                    }
                    return Mono.empty(); // 其他请求正在刷新，跳过
                })
                .subscribe(
                        v -> {},
                        ex -> log.error("异步刷新失败, key: {}", key, ex)
                );
    }
    // ...
}
```

### 5.4 击穿防护策略矩阵

| 场景 | Single-Flight | 分布式锁 | 逻辑过期 |
|---|---|---|---|
| 普通热数据（用户、字典） | ✅ | ✅ | ❌ |
| 超热点 key（首页推荐） | ✅ | ✅ | ✅ |
| 低频数据 | ❌ | ❌ | ❌ |
| 启动期冷数据 | ❌ | ✅（预热时） | ❌ |

---

## 六、缓存雪崩解决方案

### 6.1 TTL Jitter 全面应用（关键修复）

#### 6.1.1 问题定位

`MultiLevelCacheService.set()` 写入 L2 时未应用 jitter：

```java
// 当前实现（缺陷）
public <T> Mono<Boolean> set(String key, T value, Duration ttl) {
    return Mono.fromCallable(() -> {
        RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
        bucket.set(toStoreValue(value), ttl);  // ← ttl 未做 jitter
        return true;
    })...
}
```

批量写入相同 TTL 的 key 时，所有 key 在同一时刻过期，可能引发缓存雪崩。

#### 6.1.2 修复方案

```java
// 改造后的 MultiLevelCacheService.set
public <T> Mono<Boolean> set(String key, T value, Duration ttl) {
    if (key == null) {
        return Mono.just(false);
    }
    // 应用 TTL jitter（防雪崩）
    Duration jitteredTtl = jitter(ttl, cacheProperties.getJitter().getFactor());
    return Mono.fromCallable(() -> {
                RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
                bucket.set(toStoreValue(value), jitteredTtl);
                return true;
            })
            .subscribeOn(BlockingIoSchedulers.cache())
            .flatMap(success -> {
                if (success) {
                    return localCacheService.delete(key).thenReturn(true);
                }
                return Mono.just(false);
            });
}
```

#### 6.1.3 Jitter 配置化

抽取 jitter factor 到 `CacheProperties`，支持按业务场景配置：

```yaml
# application.yml 新增配置
apex:
  cache:
    jitter:
      # TTL 抖动因子（0.1 = ±10%）
      factor: 0.1
    empty:
      # 空值标记 TTL
      ttl: 5m
      # 空值标记抖动因子（0.2 = ±20%，TTL 短需更大抖动）
      jitter-factor: 0.2
```

### 6.2 缓存预热机制

#### 6.2.1 设计目的

服务启动或缓存清空后，主动加载热点数据到缓存，避免首批请求全部回源。

#### 6.2.2 核心组件

**`CachePrewarmService`** — 预热服务

```java
// 路径：apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/cache/prewarmer/CachePrewarmService.java
/**
 * 缓存预热服务
 *
 * <p>服务启动或缓存清空后，主动加载热点数据到缓存，避免首批请求全部回源。
 * 预热限流受 TokenBucket 控制（默认 100 QPS），避免冲击 DB。
 *
 * @author Apex Team
 * @since 1.4.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CachePrewarmService {

    private final List<CachePrewarmDataProvider> dataProviders;
    private final MultiLevelCacheService cacheService;
    private final CacheProperties cacheProperties;
    private final MeterRegistry meterRegistry;

    /** 预热限流令牌桶 */
    private final TokenBucket prewarmBucket;

    private final Counter prewarmCounter;

    /**
     * 按业务域预热
     */
    public Mono<Long> prewarm(String domain) {
        return dataProviders.stream()
                .filter(p -> p.domain().equals(domain))
                .findFirst()
                .map(this::prewarmDomain)
                .orElseGet(() -> {
                    log.warn("未找到预热域: {}", domain);
                    return Mono.just(0L);
                });
    }

    /**
     * 全量预热
     */
    public Mono<Long> prewarmAll() {
        return Flux.fromIterable(dataProviders)
                .concatMap(this::prewarmDomain)
                .reduce(0L, Long::sum);
    }

    /**
     * 定时增量预热（每小时扫描新增数据）
     */
    @Scheduled(fixedDelay = 3600_000) // 每小时
    public Mono<Long> incrementalPrewarm() {
        if (!cacheProperties.getPrewarm().isIncrementalEnabled()) {
            return Mono.just(0L);
        }
        return Flux.fromIterable(dataProviders)
                .filter(CachePrewarmDataProvider::supportsIncremental)
                .concatMap(this::prewarmIncremental)
                .reduce(0L, Long::sum);
    }

    private Mono<Long> prewarmDomain(CachePrewarmDataProvider provider) {
        return provider.loadHotKeys()
                .flatMap(keys -> Flux.fromIterable(keys)
                        .concatMap(key -> prewarmSingle(provider, key))
                        .count());
    }

    private Mono<Boolean> prewarmSingle(CachePrewarmDataProvider provider, String key) {
        if (!prewarmBucket.tryAcquire()) {
            return Mono.delay(Duration.ofMillis(10)).then(prewarmSingle(provider, key));
        }
        return provider.loadValue(key)
                .flatMap(value -> cacheService.set(
                        provider.cacheKey(key),
                        value,
                        provider.ttl()
                ))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(100)).maxAttempts(2))
                .doOnError(ex -> {
                    prewarmCounter.increment(provider.domain(), "fail");
                    log.warn("预热失败, domain: {}, key: {}", provider.domain(), key, ex);
                })
                .doOnSuccess(v -> prewarmCounter.increment(provider.domain(), "success"));
    }
}
```

**`CachePrewarmRunner`** — 启动后预热触发器

```java
// 路径：apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/cache/prewarmer/CachePrewarmRunner.java
/**
 * 启动后缓存预热触发器
 *
 * <p>应用启动后异步触发缓存预热，不阻塞启动。
 * 预热失败仅记录 WARN，不影响应用启动。
 *
 * @author Apex Team
 * @since 1.4.0
 */
@Component
@ConditionalOnProperty(prefix = "apex.cache.prewarm", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class CachePrewarmRunner implements ApplicationRunner {

    private final CachePrewarmService prewarmService;
    private final CacheProperties cacheProperties;

    @Override
    public void run(ApplicationArguments args) {
        // 延迟 30 秒启动预热，等待应用完全就绪
        Mono.delay(Duration.ofSeconds(cacheProperties.getPrewarm().getInitialDelaySeconds()))
                .then(prewarmService.prewarmAll())
                .subscribe(
                        count -> log.info("缓存预热完成, 总条目: {}", count),
                        ex -> log.error("缓存预热失败", ex)
                );
    }
}
```

#### 6.2.3 预热策略

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `apex.cache.prewarm.enabled` | true | 预热总开关 |
| `apex.cache.prewarm.initial-delay-seconds` | 30 | 启动后延迟预热时间 |
| `apex.cache.prewarm.qps-limit` | 100 | 预热回源 QPS 限流 |
| `apex.cache.prewarm.incremental-enabled` | true | 定时增量预热开关 |
| `apex.cache.prewarm.retry-attempts` | 2 | 单 key 预热失败重试次数 |

### 6.3 多级降级策略

#### 6.3.1 降级链路

```
正常：L1 命中 → 返回
   │
   ▼ L1 未命中
正常：L2 命中 → 回填 L1 → 返回
   │
   ▼ L2 未命中 或 L2 故障
降级1：L1-only 模式（Redis 故障时，L1 继续服务）
   │
   ▼ L1 也未命中
降级2：回源 DB（受 BlockingIoSchedulers 限流）
   │
   ▼ DB 故障
降级3：读降级返回过期缓存（已有 tryReadStaleCache）
   │
   ▼ 缓存也没有
降级4：返回降级响应（配合 @Fallback）
```

#### 6.3.2 实现要点

```java
// MultiLevelCacheService 改造：Redis 故障降级
private <T> Mono<T> queryL2AndBackfillL1(String key, Class<T> clazz) {
    return Mono.fromCallable(() -> {
                RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
                String value = bucket.get();
                if (value == null) return null;
                if (EMPTY_VALUE.equals(value)) return null;
                if (clazz == String.class) return clazz.cast(value);
                return objectMapper.readValue(value, clazz);
            })
            .subscribeOn(BlockingIoSchedulers.cache())
            .flatMap(result -> {
                if (result != null) {
                    return localCacheService.set(key, result, Duration.ofMinutes(5))
                            .thenReturn(result);
                }
                return Mono.empty();
            })
            .onErrorResume(ex -> {
                // Redis 故障降级：切到 L1-only 模式
                log.warn("L2 Redis 异常, 切到 L1-only 模式, key: {}", key, ex);
                redisDegradationCounter.increment();
                return localCacheService.get(key, clazz); // 兜底读 L1
            });
}
```

### 6.4 雪崩场景演练

提供 `CacheChaosTest` 集成测试，模拟：

- **大批量 key 同时过期**：写入 1000 个相同 TTL 的 key，等待过期后观察回源 QPS 曲线，验证 jitter 分散
- **Redis 实例宕机**：模拟 Redis 超时，验证 L1 继续服务 + 指标记录
- **DB 故障**：模拟 DB 不可用，验证读降级返回过期数据

---

## 七、监控告警体系

### 7.1 指标体系

| 指标名 | 类型 | 标签 | 说明 |
|---|---|---|---|
| `apex_cache_hit_total` | Counter | layer=L1/L2, domain | 缓存命中次数 |
| `apex_cache_miss_total` | Counter | layer=L1/L2, domain | 缓存未命中次数 |
| `apex_cache_hit_ratio` | Gauge | layer, domain | 缓存命中率（计算指标） |
| `apex_cache_reload_total` | Counter | domain, result | 回源次数（成功/失败） |
| `apex_cache_empty_marker_total` | Counter | domain | 空值标记写入次数（穿透防护） |
| `apex_cache_bloom_reject_total` | Counter | domain | 布隆过滤器拦截次数 |
| `apex_cache_bloom_false_positive_total` | Counter | domain | 布隆误判次数 |
| `apex_cache_lock_acquire_total` | Counter | result=success/fail | 分布式锁获取结果 |
| `apex_cache_redis_degradation_total` | Counter | — | Redis 故障降级次数 |
| `apex_cache_read_degradation_total` | Counter | — | DB 故障读降级次数（已有） |
| `apex_cache_prewarm_total` | Counter | domain, result | 预热执行结果 |
| `apex_cache_logical_refresh_total` | Counter | domain | 逻辑过期异步刷新次数 |

### 7.2 指标采集器

```java
// 路径：apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/cache/metrics/CacheMetricsCollector.java
/**
 * 缓存指标采集器
 *
 * <p>统一采集多级缓存的命中、未命中、回源、降级等指标，
 * 通过 Micrometer 暴露给 Prometheus。
 *
 * @author Apex Team
 * @since 1.4.0
 */
@Component
@RequiredArgsConstructor
public class CacheMetricsCollector {

    private final MeterRegistry meterRegistry;

    public Counter hitCounter(String layer, String domain) {
        return Counter.builder("apex_cache_hit_total")
                .tag("layer", layer)
                .tag("domain", domain)
                .register(meterRegistry);
    }

    public Counter missCounter(String layer, String domain) {
        return Counter.builder("apex_cache_miss_total")
                .tag("layer", layer)
                .tag("domain", domain)
                .register(meterRegistry);
    }
    // ... 其他指标
}
```

### 7.3 告警规则

| 告警项 | 触发条件 | 严重级别 | 建议动作 |
|---|---|---|---|
| 缓存命中率下降 | 5 分钟平均命中率 < 80% | WARN | 检查 TTL 配置、预热状态 |
| 回源激增 | 5 分钟回源次数 > 1000 | WARN | 检查热点 key、扩容缓存 |
| 布隆拦截激增 | 5 分钟拦截次数 > 10000 | WARN | 疑似扫描攻击，启动 IP 限流 |
| 分布式锁失败激增 | 5 分钟锁失败 > 500 | WARN | 热点 key 过载，考虑逻辑过期 |
| Redis 降级触发 | 任一次降级触发 | CRITICAL | 立即排查 Redis 集群 |
| 读降级触发 | 任一次读降级触发 | CRITICAL | 立即排查 DB 状态 |

---

## 八、实施步骤

### Phase 1：核心防护修复（必做）

| 步骤 | 文件 | 改动 |
|---|---|---|
| 1.1 | [MultiLevelCacheService.java](../../apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/cache/service/MultiLevelCacheService.java) | `set()` 与 `setEmptyMarker()` 应用 TTL jitter；新增 `jitter()` 工具方法 |
| 1.2 | [CacheProperties.java](../../apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/cache/config/CacheProperties.java) | 新增 `jitter`、`empty`、`lock` 配置组 |
| 1.3 | [application.yml](../../apex-interfaces/src/main/resources/application.yml) | 补充 `apex.cache.jitter.factor`、`apex.cache.empty.ttl`、`apex.cache.empty.jitter-factor` 默认值 |
| 1.4 | [MultiLevelCacheService.java](../../apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/cache/service/MultiLevelCacheService.java) | L2 操作增加 `onErrorResume` 处理 Redis 异常，切到 L1-only 模式 |

### Phase 2：缓存穿透防护

| 步骤 | 文件 | 改动 |
|---|---|---|
| 2.1 | `apex-infrastructure/.../cache/bloom/BloomFilterService.java` | 新建布隆过滤器服务 |
| 2.2 | `apex-infrastructure/.../cache/bloom/BloomFilterInitializer.java` | 新建启动期预热器 |
| 2.3 | `apex-infrastructure/.../cache/bloom/BloomFilterDataProvider.java` | 新建数据提供者接口 |
| 2.4 | [MultiLevelCacheService.java](../../apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/cache/service/MultiLevelCacheService.java) | `getWithLoader()` 前置接入布隆过滤器 |
| 2.5 | [application.yml](../../apex-interfaces/src/main/resources/application.yml) | 新增 `apex.cache.bloom.*` 配置（容量、误判率、各域开关） |

### Phase 3：缓存击穿增强

| 步骤 | 文件 | 改动 |
|---|---|---|
| 3.1 | `apex-infrastructure/.../cache/support/LogicalExpiryWrapper.java` | 新建逻辑过期包装器 |
| 3.2 | [MultiLevelCacheService.java](../../apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/cache/service/MultiLevelCacheService.java) | `getWithLoader()` 叠加分布式锁（多实例互斥） |
| 3.3 | `apex-infrastructure/.../cache/service/HotKeyCacheService.java` | 新建超热点 key 服务（逻辑过期 + 异步刷新） |
| 3.4 | [CacheLockAspect.java](../../apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/cache/aspect/CacheLockAspect.java) | 增加自旋重试工具方法（供 `MultiLevelCacheService` 复用） |

### Phase 4：缓存雪崩防护

| 步骤 | 文件 | 改动 |
|---|---|---|
| 4.1 | `apex-infrastructure/.../cache/prewarmer/CachePrewarmService.java` | 新建预热服务 |
| 4.2 | `apex-infrastructure/.../cache/prewarmer/CachePrewarmRunner.java` | 新建启动后预热触发器 |
| 4.3 | `apex-infrastructure/.../cache/prewarmer/CachePrewarmDataProvider.java` | 新建数据提供者接口 |
| 4.4 | [MultiLevelCacheService.java](../../apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/cache/service/MultiLevelCacheService.java) | Redis 故障降级到 L1-only 模式（已在 Phase 1.4 完成） |
| 4.5 | [application.yml](../../apex-interfaces/src/main/resources/application.yml) | 新增 `apex.cache.prewarm.*` 配置 |

### Phase 5：监控告警

| 步骤 | 文件 | 改动 |
|---|---|---|
| 5.1 | `apex-infrastructure/.../cache/metrics/CacheMetricsCollector.java` | 新建缓存指标采集器 |
| 5.2 | [MultiLevelCacheService.java](../../apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/cache/service/MultiLevelCacheService.java) | 在各关键节点埋点（命中/未命中/回源/降级） |
| 5.3 | [application.yml](../../apex-interfaces/src/main/resources/application.yml) | 告警阈值配置（接入现有 Sentinel/钉钉告警） |

### Phase 6：测试验证

| 步骤 | 文件 | 改动 |
|---|---|---|
| 6.1 | `MultiLevelCacheServiceJitterTest.java` | 验证 TTL jitter 生效 |
| 6.2 | `BloomFilterServiceTest.java` | 布隆过滤器单元测试 |
| 6.3 | `CachePrewarmServiceTest.java` | 预热服务测试 |
| 6.4 | `HotKeyCacheServiceTest.java` | 逻辑过期 + 异步刷新测试 |
| 6.5 | `CacheChaosTest.java` | 雪崩场景集成测试（Redis 故障、批量过期） |
| 6.6 | 扩展 [MultiLevelCacheServiceStampedeTest.java](../../apex-infrastructure/src/test/java/com/apex/scaffold/infrastructure/cache/service/MultiLevelCacheServiceStampedeTest.java) | 覆盖分布式锁 + Single-Flight 组合 |

### Phase 7：文档输出

| 步骤 | 文件 | 改动 |
|---|---|---|
| 7.1 | `docs/cache/CACHE_OPTIMIZATION_SOLUTION.md` | **本方案文档**（已生成） |
| 7.2 | `docs/cache/README.md` | 缓存专题文档索引 |
| 7.3 | 更新 [multi-level-cache-complete-guide.md](../architecture/advanced-features/multi-level-cache-complete-guide.md) | 同步新增能力 |

---

## 九、验证方案

### 9.1 单元测试

| 测试项 | 验证目标 | 通过条件 |
|---|---|---|
| **TTL Jitter** | `set()` 写入 1000 次，TTL 在 ±10% 范围内分布 | 方差 > 0，全部落在 `[0.9, 1.1] * ttl` |
| **空值标记 Jitter** | `setEmptyMarker()` 写入 1000 次，TTL 在 ±20% 范围内 | 全部落在 `[0.8, 1.2] * emptyTtl` |
| **布隆过滤器** | 插入 10 万 key，验证误判率 | 误判率 < 0.1% |
| **Single-Flight + 分布式锁** | 100 并发请求同一 key | DB loader 执行次数 = 1 |
| **逻辑过期** | 构造逻辑过期数据 | 返回旧值 + 触发异步刷新 |
| **预热服务** | mock DB 返回 1 万条 | 全部写入缓存，无失败 |
| **Redis 故障降级** | mock Redis 抛异常 | 切到 L1-only 模式，指标递增 |

### 9.2 集成测试

| 测试场景 | 验证目标 | 通过条件 |
|---|---|---|
| **批量过期雪崩** | 写入 1000 个相同 TTL 的 key，等待过期 | 回源 QPS 曲线分散，无明显尖峰 |
| **Redis 故障降级** | 模拟 Redis 超时 | L1 继续服务，`redis_degradation_total` 递增 |
| **DB 故障读降级** | 模拟 DB 不可用 | 返回过期缓存数据，`read_degradation_total` 递增 |

### 9.3 压测验证

| 场景 | 工具 | 验证目标 |
|---|---|---|
| 热点 key 压测 | JMH / wrk | 对单一热点 key 发起 1 万 QPS，DB 压力接近 0 |
| 冷启动压测 | wrk | 清空缓存后立即加压，预热平滑流量 |

### 9.4 构建与质量检查

```bash
# 加载项目环境
cd /Users/work/apex-scaffold
sdk env

# 编译验证
mvnd -pl apex-infrastructure -am compile -DskipTests

# 单元测试
mvnd -pl apex-infrastructure -am test -Dtest='com.apex.scaffold.infrastructure.cache.**'

# 代码格式
mvnd spotless:check

# 代码质量
mvnd pmd:check

# 架构测试（确保分层依赖、租户隔离、阻塞调度器隔离）
mvnd -pl apex-infrastructure -am test -Dtest='*ArchTest'
```

---

## 十、关键决策与约束

### 10.1 关键决策

| 决策点 | 选择 | 理由 |
|---|---|---|
| 布隆过滤器实现 | Redisson `RBloomFilter` | 跨实例共享，项目已依赖 Redisson；无需引入新组件 |
| 分布式锁实现 | 复用 Redisson `RLock`（直接调用，非 `@CacheLock`） | `@CacheLock` 适用于业务方法级；缓存回源互斥需在 `MultiLevelCacheService` 内部自动控制 |
| 逻辑过期存储 | 包装到 value JSON 中（`LogicalExpiryWrapper`） | 与现有 JSON 序列化方案兼容，无需改 schema |
| 预热触发时机 | `ApplicationRunner` 异步触发 | 不阻塞启动，启动后立即执行 |
| 预热限流 | 复用 `TokenBucket`（项目已有） | 与缓存回源限流统一 |
| 配置前缀 | `apex.cache.*` 统一 | 与现有 `apex.cache.local/remote/read-degradation` 一致 |
| 监控指标命名 | `apex_cache_*` 前缀 | 与现有 `apex_cache_read_degradation_total` 一致 |
| 文档保存位置 | `docs/cache/` 子目录 | 用户确认；建立缓存专题目录 |

### 10.2 约束

1. **Redisson 已在项目中**：布隆过滤器、分布式锁均基于 Redisson，无需引入新依赖
2. **`BlockingIoSchedulers`**：所有 Redis 阻塞操作必须隔离到 `BlockingIoSchedulers.cache()`
3. **租户隔离**：缓存 key 必须包含租户标识（`apex:{tenantId}:...`），使用 `TenantContext.currentTenantId()`
4. **配置化优先**：所有阈值/开关通过 `apex.cache.*` 配置，生产可动态调整
5. **不破坏现有契约**：`CacheService`、`CacheConsistencyPort` 接口签名保持兼容
6. **AGENTS.md 规范**：所有新类需补 JavaDoc + Lombok + 构造器注入 + 中文注释
7. **不修改已执行的 Flyway 脚本**：本次改动不涉及 DB schema 变更
8. **SpringDoc 注解**：新增的 Handler/Router（如有）需补 `@Operation`、`@Schema` 等注解

---

## 十一、风险评估与回滚

### 11.1 风险评估

| 风险 | 影响 | 缓解措施 |
|---|---|---|
| 布隆过滤器误判导致合法 key 被拦截 | 低（误判率 0.1%） | 误判时仍走空值标记 + 回源，不会丢数据 |
| 分布式锁等待导致请求延迟 | 中（waitTime 200ms） | 自旋重试读缓存，避免长时间等待；超时降级直连 DB |
| 缓存预热冲击 DB | 中（启动期 DB 压力） | TokenBucket 限流 100 QPS；延迟 30 秒启动 |
| 逻辑过期返回旧数据 | 低（业务可接受） | 仅用于超热点 key；逻辑 TTL 短（5 分钟） |
| Redis 故障降级导致数据不一致 | 中（L1 数据滞后） | 短 TTL（5 分钟）容忍；恢复后自动切回 |

### 11.2 回滚方案

| 问题 | 回滚动作 |
|---|---|
| 布隆过滤器异常 | 关闭 `apex.cache.bloom.enabled=false`，回到空值标记单道防护 |
| 分布式锁异常 | 关闭 `apex.cache.lock.enabled=false`，回到纯 Single-Flight |
| 预热冲击 DB | 关闭 `apex.cache.prewarm.enabled=false` |
| 逻辑过期返回错误数据 | 关闭 `apex.cache.logical-expiry.enabled=false` |
| TTL jitter 异常 | 关闭 `apex.cache.jitter.factor=0`，回到固定 TTL |

所有新增能力均通过配置开关控制，可独立回滚，不影响现有功能。

---

## 附录 A：配置项汇总

```yaml
apex:
  cache:
    # ---------- 现有配置 ----------
    local:
      max-size: 10000
      expire-after-write: 300
      initial-capacity: 500
    remote:
      default-ttl: 3600
      batch-size: 100
    read-degradation:
      enabled: false
      max-stale-seconds: 3600

    # ---------- 新增配置（Phase 1） ----------
    jitter:
      factor: 0.1  # TTL 抖动因子（±10%）
    empty:
      ttl: 5m  # 空值标记 TTL
      jitter-factor: 0.2  # 空值标记抖动因子（±20%）
    lock:
      enabled: true  # 分布式锁开关
      wait-time-ms: 200  # 锁等待时间
      lease-time-ms: 30000  # 锁租约时间

    # ---------- 新增配置（Phase 2） ----------
    bloom:
      enabled: true  # 布隆过滤器总开关
      default-expected-insertions: 1000000  # 默认容量
      default-false-probability: 0.001  # 默认误判率
      domains:  # 各业务域配置
        apex:bloom:user:
          expected-insertions: 1000000
          false-probability: 0.001
        apex:bloom:menu:
          expected-insertions: 10000
          false-probability: 0.01

    # ---------- 新增配置（Phase 4） ----------
    prewarm:
      enabled: true  # 预热总开关
      initial-delay-seconds: 30  # 启动后延迟预热时间
      qps-limit: 100  # 预热回源 QPS 限流
      incremental-enabled: true  # 定时增量预热
      retry-attempts: 2  # 单 key 预热失败重试
```

## 附录 B：相关文档

- [多级缓存完整指南](../architecture/advanced-features/multi-level-cache-complete-guide.md)
- [多级缓存架构](../architecture/advanced-features/multi-level-cache-architecture.md)
- [读降级指南](../01-guides/read-degradation-guide.md)
- [AGENTS.md 工程规范](../../AGENTS.md)

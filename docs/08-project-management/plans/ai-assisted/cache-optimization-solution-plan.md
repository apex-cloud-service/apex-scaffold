# 缓存穿透 / 击穿 / 雪崩 完整解决方案 — 计划文档

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

> 本计划文档用于规划方案设计，最终方案文档将保存到 `docs/cache/CACHE_OPTIMIZATION_SOLUTION.md`。

## 一、任务目标

针对 Apex Scaffold 项目现有的多级缓存（L1 Caffeine + L2 Redis）架构，补全**缓存穿透 / 缓存击穿 / 缓存雪崩**三大经典问题的完整防护方案，并输出详细的设计文档与实施步骤。方案经用户确认后再执行实施。

## 二、现状分析

### 2.1 现有缓存架构

| 组件 | 路径 | 职责 |
|---|---|---|
| `MultiLevelCacheService` | [apex-infrastructure/.../cache/service/MultiLevelCacheService.java](../../../../apex-infrastructure-cache/src/main/java/com/apex/scaffold/infrastructure/cache/service/MultiLevelCacheService.java) | 多级缓存核心服务：L1+L2 读写、回源、空值标记、Single-Flight |
| `LocalCacheService` | [apex-infrastructure/.../cache/service/LocalCacheService.java](../../../../apex-infrastructure-cache/src/main/java/com/apex/scaffold/infrastructure/cache/service/LocalCacheService.java) | L1 Caffeine 实现，带独立 TTL 跟踪 |
| `CacheServiceImpl` | [apex-infrastructure/.../cache/service/CacheServiceImpl.java](../../../../apex-infrastructure-cache/src/main/java/com/apex/scaffold/infrastructure/cache/service/CacheServiceImpl.java) | L2 Redis 单层缓存（实现 `CacheService` 接口） |
| `CacheConsistencyService` | [apex-infrastructure/.../cache/service/CacheConsistencyService.java](../../../../apex-infrastructure-cache/src/main/java/com/apex/scaffold/infrastructure/cache/service/CacheConsistencyService.java) | 一致性服务，带 TTL jitter、空值标记、Single-Flight |
| `InFlightRequestMerge` | [apex-infrastructure/.../cache/support/InFlightRequestMerge.java](../../../../apex-infrastructure-cache/src/main/java/com/apex/scaffold/infrastructure/cache/support/InFlightRequestMerge.java) | Single-Flight 请求合并器（进程内） |
| `CacheLockAspect` + `@CacheLock` | [apex-infrastructure/.../cache/aspect/CacheLockAspect.java](../../../../apex-infrastructure-cache/src/main/java/com/apex/scaffold/infrastructure/cache/aspect/CacheLockAspect.java) | 基于 Redisson 的分布式锁切面 |
| `MultiLevelCacheConfig` | [apex-infrastructure/.../cache/config/MultiLevelCacheConfig.java](../../../../apex-infrastructure-cache/src/main/java/com/apex/scaffold/infrastructure/cache/config/MultiLevelCacheConfig.java) | Caffeine CacheManager 配置 |
| `CacheProperties` | [apex-infrastructure/.../cache/config/CacheProperties.java](../../../../apex-infrastructure-cache/src/main/java/com/apex/scaffold/infrastructure/cache/config/CacheProperties.java) | 读降级配置（`apex.cache.read-degradation`） |

### 2.2 已有的防护机制

| 防护维度 | 现状 | 评估 |
|---|---|---|
| **缓存穿透** | 部分实现：`MultiLevelCacheService` 与 `CacheConsistencyService` 中均有 `__EMPTY__` 空值标记 + 5 分钟 TTL | **不足**：① 无布隆过滤器前置拦截 ② 空值标记 TTL 固定未加 jitter，可能引发"空值雪崩" ③ `MultiLevelCacheService` 的空值标记未应用 jitter |
| **缓存击穿** | 已实现：`InFlightRequestMerge` 进程内 Single-Flight，并发回源合并为一次；`@CacheLock` 分布式锁可选叠加 | **不足**：① Single-Flight 仅进程内，多实例下每个实例仍各自回源一次 ② 未提供"逻辑过期 + 异步刷新"策略 ③ 未提供永不过期 + 后台刷新机制 |
| **缓存雪崩** | 部分实现：`CacheConsistencyService` 中 L2 TTL 与空值标记 TTL 应用 ±10% jitter | **不足**：① `MultiLevelCacheService.set()` 未应用 jitter（关键缺陷） ② 缺少缓存预热机制 ③ 缺少 Redis 集群故障下的本地缓存降级策略 ④ 缺少批量预热工具 |
| **监控告警** | Caffeine 有指标绑定器（`CaffeineCacheMetricsBinder`）；`MultiLevelCacheService` 有读降级计数器 | **不足**：① 缺少缓存命中率/未命中率/回源次数的统一指标 ② 缺少穿透/击穿/雪崩事件计数器与告警 ③ L2 Redis 命中/未命中无指标 |

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

## 三、解决方案设计

### 3.1 总体架构

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

### 3.2 缓存穿透解决方案

#### 3.2.1 引入 Redisson 布隆过滤器（Bloom Filter）

**目的**：在海量不存在的 key 到达 DB 之前进行前置拦截。

**设计**：
- 使用 `RedissonClient` 的 `RBloomFilter`，存储于 Redis 实现跨实例共享
- 按业务域创建多个布隆过滤器：`apex:bloom:user`、`apex:bloom:menu`、`apex:bloom:dict` 等
- 容量参数：`expectedInsertions = 1_000_000`、`falseProbability = 0.001`（误判率 0.1%）
- 启动时由 `BloomFilterInitializer` 从 DB 加载全量 ID 注册到过滤器
- DB 数据变更时（新增/删除）同步更新布隆过滤器（通过 `@CacheEvict` 或领域事件）

**新增组件**：
```java
// 路径：apex-infrastructure/.../cache/bloom/BloomFilterService.java
public class BloomFilterService {
    /** 注册 key 到布隆过滤器 */
    Mono<Boolean> add(String filterName, String key);
    /** 批量注册 */
    Mono<Boolean> addAll(String filterName, Collection<String> keys);
    /** 检查 key 是否可能存在 */
    Mono<Boolean> mightContain(String filterName, String key);
    /** 重建过滤器（数据迁移/重置场景） */
    Mono<Boolean> rebuild(String filterName, Collection<String> keys);
}
```

#### 3.2.2 空值标记增强

**问题**：现有 `MultiLevelCacheService` 中 `EMPTY_TTL` 固定 5 分钟，且 `setEmptyMarker()` 未应用 jitter，大量 key 同时穿透会写入同 TTL 的空值标记，5 分钟后同时过期再次穿透。

**改进**：
- `MultiLevelCacheService.setEmptyMarker()` 应用 ±20% jitter（空值标记 TTL 短，需要更大抖动比例）
- 抽取 `EMPTY_TTL` 与 `JITTER_FACTOR` 到 `CacheProperties`，支持配置化
- 在布隆过滤器误判（即 DB 中确实不存在）时写入空值标记

#### 3.2.3 防穿透链路集成

```java
// MultiLevelCacheService.getWithLoader 改造
public <T> Mono<T> getWithLoader(String key, Class<T> clazz, Supplier<Mono<T>> loader, Duration ttl) {
    return bloomFilterService.mightContain(filterName(key), key)
        .flatMap(mightExist -> {
            if (!mightExist) {
                // 布隆过滤器判定不存在，直接返回空（穿透防护第一道）
                bloomFilterMissCounter.increment();
                return Mono.empty();
            }
            // 可能存在，继续走多级缓存 + 回源
            return getWithLoaderInternal(key, clazz, loader, ttl);
        });
}
```

### 3.3 缓存击穿解决方案

#### 3.3.1 现有 Single-Flight 保留

保留 `InFlightRequestMerge` 的进程内合并能力，作为本地实例的快速防护。

#### 3.3.2 分布式锁增强（多实例场景）

**问题**：多实例部署下，每个实例的 Single-Flight 各自合并，仍然有 N 个实例同时回源 DB。

**方案**：在 `getWithLoader()` 入口叠加 Redisson 分布式锁，作为多实例互斥兜底：
- 使用 `@CacheLock` 切面或直接调用 `RLock`
- 锁粒度：`cache:lock:reload:{key}`
- waitTime：200ms（短等待，避免阻塞请求线程）
- leaseTime：30s（覆盖 DB 查询最长耗时）
- 获取锁失败：自旋重试读缓存（其他实例回源后会写入），最多重试 3 次，每次间隔 50ms

```java
// Single-Flight + 分布式锁的组合策略
private <T> Mono<T> loadWithDistributedLock(String key, Supplier<Mono<T>> loader, Duration ttl) {
    String lockKey = "cache:lock:reload:" + key;
    return Mono.fromCallable(() -> redissonClient.getLock(lockKey))
        .subscribeOn(BlockingIoSchedulers.cache())
        .flatMap(lock -> tryLockAndLoad(lock, key, loader, ttl));
}
```

#### 3.3.3 逻辑过期 + 异步刷新

**目的**：对超热点 key（如首页推荐、热门商品），物理 TTL 设为较长（如 24 小时），但在 value 中嵌入逻辑过期时间（如 5 分钟）。读取时若逻辑过期，立即返回旧值并触发异步刷新。

**设计**：
```java
// 路径：apex-infrastructure/.../cache/support/LogicalExpiryWrapper.java
public class LogicalExpiryWrapper<T> {
    private T value;
    private long logicalExpireAt;  // 逻辑过期时间戳
    private long physicalExpireAt;  // 物理过期时间戳
    /** 是否逻辑过期 */
    public boolean isLogicalExpired() { ... }
}
```

**读取流程**：
1. 读到缓存值 → 判断 `isLogicalExpired()`
2. 未逻辑过期 → 直接返回
3. 已逻辑过期 → 返回旧值（兜底可用性）+ 异步触发刷新（仅一个请求执行，通过分布式锁保证）

#### 3.3.4 适用场景分流

| 场景 | 策略 |
|---|---|
| 普通热数据（用户、字典） | Single-Flight + 短 waitTime 分布式锁 |
| 超热点 key（首页推荐） | 逻辑过期 + 异步刷新 + 永不物理过期 |
| 低频数据 | 直接回源，无需特殊防护 |

### 3.4 缓存雪崩解决方案

#### 3.4.1 TTL Jitter 全面应用（关键修复）

**问题**：`MultiLevelCacheService.set()` 未应用 jitter，写入相同 TTL 的 key 会同时过期。

**修复**：
- `MultiLevelCacheService.set()` 写入 L2 时应用 ±10% jitter
- `MultiLevelCacheService.setEmptyMarker()` 应用 ±20% jitter（空值标记）
- 抽取 `JitterFactor` 到 `CacheProperties`，按业务场景配置

```java
// MultiLevelCacheService.set 改造
public <T> Mono<Boolean> set(String key, T value, Duration ttl) {
    Duration jitteredTtl = jitter(ttl, cacheProperties.getJitterFactor());
    // ... 写入 L2 使用 jitteredTtl
}
```

#### 3.4.2 缓存预热机制

**目的**：服务启动或缓存清空后，主动加载热点数据到缓存，避免首批请求全部回源。

**设计**：
```java
// 路径：apex-infrastructure/.../cache/prewarmer/CachePrewarmService.java
public class CachePrewarmService {
    /** 按业务域预热 */
    Mono<Long> prewarm(String domain);
    /** 全量预热 */
    Mono<Long> prewarmAll();
    /** 定时增量预热（每小时扫描新增数据） */
    Mono<Long> incrementalPrewarm(String domain);
}
```

**预热策略**：
- 启动后异步预热（`ApplicationRunner` 触发，不阻塞启动）
- 预热域：用户（前 1 万 ID）、菜单（全量）、字典（全量）、权限（全量）
- 限流：预热回源 QPS 受 `TokenBucket` 控制（默认 100 QPS），避免冲击 DB
- 失败重试：单 key 预热失败重试 2 次，超限跳过并记录告警

#### 3.4.3 多级降级策略

**Redis 集群故障场景**：
1. L2 Redis 不可用时，L1 Caffeine 继续服务（短 TTL 容忍数据滞后）
2. L1 未命中 + L2 不可用 → 回源 DB（受 `BlockingIoSchedulers` 限流保护）
3. DB 也不可用 → 读降级返回过期缓存（已由 `tryReadStaleCache` 实现）
4. 全链路不可用 → 返回降级响应（配合 `@Fallback` 注解）

**实现要点**：
- `MultiLevelCacheService` 的 L2 操作增加 `onErrorResume` 处理 Redis 异常
- Redis 故障时切到 "L1 only" 模式，记录 `apex_cache_redis_degradation_total` 指标
- 恢复后自动切回正常模式

#### 3.4.4 雪崩场景演练

提供 `CacheChaosTest` 集成测试，模拟：
- 大批量 key 同时过期 → 验证 jitter 生效
- Redis 实例宕机 → 验证降级链路
- DB 故障 → 验证读降级返回过期数据

### 3.5 监控告警增强

#### 3.5.1 指标体系

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

#### 3.5.2 告警规则

| 告警项 | 触发条件 | 严重级别 |
|---|---|---|
| 缓存命中率下降 | 5 分钟平均命中率 < 80% | WARN |
| 回源激增 | 5 分钟回源次数 > 1000 | WARN |
| 布隆拦截激增 | 5 分钟拦截次数 > 10000 | WARN（疑似扫描攻击） |
| 分布式锁失败激增 | 5 分钟锁失败 > 500 | WARN（疑似热点 key 过载） |
| Redis 降级触发 | 任一次降级触发 | CRITICAL |
| 读降级触发 | 任一次读降级触发 | CRITICAL（DB 故障） |

## 四、实施步骤（待方案确认后执行）

### Phase 1：核心防护修复（必做）

| 步骤 | 文件 | 改动 |
|---|---|---|
| 1.1 | `MultiLevelCacheService.java` | `set()` 与 `setEmptyMarker()` 应用 TTL jitter；抽取 jitter factor 到配置 |
| 1.2 | `CacheProperties.java` | 新增 `jitterFactor`、`emptyTtl`、`emptyJitterFactor` 配置项 |
| 1.3 | `application.yml` | 补充 `apex.cache.jitter.factor`、`apex.cache.empty.ttl` 默认值 |
| 1.4 | `MultiLevelCacheService.java` | L2 操作增加 Redis 异常降级处理，切到 L1-only 模式 |

### Phase 2：缓存穿透防护

| 步骤 | 文件 | 改动 |
|---|---|---|
| 2.1 | `apex-infrastructure/.../cache/bloom/BloomFilterService.java` | 新建布隆过滤器服务 |
| 2.2 | `apex-infrastructure/.../cache/bloom/BloomFilterInitializer.java` | 新建启动期预热器 |
| 2.3 | `MultiLevelCacheService.java` | `getWithLoader()` 前置接入布隆过滤器 |
| 2.4 | `application.yml` | 新增 `apex.cache.bloom.*` 配置（容量、误判率、各域开关） |

### Phase 3：缓存击穿增强

| 步骤 | 文件 | 改动 |
|---|---|---|
| 3.1 | `apex-infrastructure/.../cache/support/LogicalExpiryWrapper.java` | 新建逻辑过期包装器 |
| 3.2 | `MultiLevelCacheService.java` | `getWithLoader()` 叠加分布式锁（多实例互斥） |
| 3.3 | `apex-infrastructure/.../cache/service/HotKeyCacheService.java` | 新建超热点 key 服务（逻辑过期 + 异步刷新） |
| 3.4 | `CacheLockAspect.java` | 优化自旋重试逻辑（获取锁失败时自旋读缓存） |

### Phase 4：缓存雪崩防护

| 步骤 | 文件 | 改动 |
|---|---|---|
| 4.1 | `apex-infrastructure/.../cache/prewarmer/CachePrewarmService.java` | 新建预热服务 |
| 4.2 | `apex-infrastructure/.../cache/prewarmer/CachePrewarmRunner.java` | 新建启动后预热触发器 |
| 4.3 | `MultiLevelCacheService.java` | Redis 故障降级到 L1-only 模式 |
| 4.4 | `application.yml` | 新增 `apex.cache.prewarm.*` 配置 |

### Phase 5：监控告警

| 步骤 | 文件 | 改动 |
|---|---|---|
| 5.1 | `apex-infrastructure/.../cache/metrics/CacheMetricsCollector.java` | 新建缓存指标采集器 |
| 5.2 | `MultiLevelCacheService.java` | 在各关键节点埋点（命中/未命中/回源/降级） |
| 5.3 | `application.yml` | 告警阈值配置（接入现有 Sentinel/钉钉告警） |

### Phase 6：测试验证

| 步骤 | 文件 | 改动 |
|---|---|---|
| 6.1 | `MultiLevelCacheServiceJitterTest.java` | 验证 TTL jitter 生效 |
| 6.2 | `BloomFilterServiceTest.java` | 布隆过滤器单元测试 |
| 6.3 | `CachePrewarmServiceTest.java` | 预热服务测试 |
| 6.4 | `HotKeyCacheServiceTest.java` | 逻辑过期 + 异步刷新测试 |
| 6.5 | `CacheChaosTest.java` | 雪崩场景集成测试（Redis 故障、批量过期） |
| 6.6 | 扩展 `MultiLevelCacheServiceStampedeTest.java` | 覆盖分布式锁 + Single-Flight 组合 |

### Phase 7：文档输出

| 步骤 | 文件 | 改动 |
|---|---|---|
| 7.1 | `docs/cache/CACHE_OPTIMIZATION_SOLUTION.md` | **最终方案文档**（本次任务的核心交付物） |
| 7.2 | `docs/cache/README.md` | 缓存专题文档索引 |
| 7.3 | 更新 `docs/architecture/advanced-features/multi-level-cache-complete-guide.md` | 同步新增能力 |

## 五、关键设计决策

| 决策点 | 选择 | 理由 |
|---|---|---|
| 布隆过滤器实现 | Redisson `RBloomFilter` | 跨实例共享，项目已依赖 Redisson；无需引入新组件 |
| 分布式锁实现 | 复用现有 `@CacheLock` + Redisson | 项目已有成熟切面，无需重复造轮子 |
| 逻辑过期存储 | 包装到 value JSON 中 | 与现有 JSON 序列化方案兼容，无需改 schema |
| 预热触发时机 | `ApplicationRunner` 异步触发 | 不阻塞启动，启动后立即执行 |
| 预热限流 | 复用 `TokenBucket`（项目已有） | 与缓存回源限流统一 |
| 配置前缀 | `apex.cache.*` 统一 | 与现有 `apex.cache.local/remote/read-degradation` 一致 |
| 监控指标命名 | `apex_cache_*` 前缀 | 与现有 `apex_cache_read_degradation_total` 一致 |
| 文档保存位置 | `docs/cache/` 子目录 | 用户确认；建立缓存专题目录 |

## 六、验证方案

### 6.1 单元测试

- **TTL Jitter**：模拟 1000 次 `set()`，验证 TTL 在 ±10% 范围内分布
- **布隆过滤器**：插入 10 万 key，验证误判率 < 0.1%
- **Single-Flight + 分布式锁**：100 并发请求同一 key，验证 DB loader 仅执行一次
- **逻辑过期**：构造逻辑过期数据，验证返回旧值 + 触发异步刷新
- **预热服务**：mock DB 返回 1 万条数据，验证全部写入缓存

### 6.2 集成测试

- **批量过期雪崩**：写入 1000 个相同 TTL 的 key，等待过期后观察回源 QPS 曲线，验证 jitter 分散
- **Redis 故障降级**：模拟 Redis 超时，验证 L1 继续服务 + 指标记录
- **DB 故障读降级**：模拟 DB 不可用，验证返回过期缓存数据

### 6.3 压测验证

- **热点 key 压测**：对单一热点 key 发起 1 万 QPS，观察 DB 压力（应接近 0）
- **冷启动压测**：清空缓存后立即加压，观察预热是否平滑流量

### 6.4 构建与质量检查

```bash
# 编译验证
mvnd -pl apex-infrastructure -am compile -DskipTests

# 单元测试
mvnd -pl apex-infrastructure -am test -Dtest='com.apex.scaffold.infrastructure.cache.**'

# 代码格式
mvnd spotless:check

# 代码质量
mvnd pmd:check
```

## 七、假设与约束

1. **Redisson 已在项目中**：布隆过滤器、分布式锁均基于 Redisson，无需引入新依赖
2. **`BlockingIoSchedulers`**：所有 Redis 阻塞操作必须隔离到 `BlockingIoSchedulers.cache()`
3. **租户隔离**：缓存 key 必须包含租户标识（`apex:{tenantId}:...`）
4. **配置化优先**：所有阈值/开关通过 `apex.cache.*` 配置，生产可动态调整
5. **不破坏现有契约**：`CacheService`、`CacheConsistencyPort` 接口签名保持兼容
6. **AGENTS.md 规范**：所有新类需补 JavaDoc + Lombok + 构造器注入 + 中文注释
7. **不修改已执行的 Flyway 脚本**：本次改动不涉及 DB schema 变更

## 八、最终交付物

| 交付物 | 路径 | 说明 |
|---|---|---|
| 方案文档 | `docs/cache/CACHE_OPTIMIZATION_SOLUTION.md` | 完整方案设计（本计划的最终版） |
| 索引文档 | `docs/cache/README.md` | 缓存专题文档索引 |
| 代码实现 | 见 Phase 1-5 文件列表 | 各类与配置改动 |
| 测试用例 | 见 Phase 6 文件列表 | 单元测试与集成测试 |

# 缓存性能优化与配置调优指南

## 性能调优矩阵

### 根据业务规模选择配置

| 业务规模 | 日活用户 | 布隆过滤器容量 | 误判率 | 预热批次大小 | 锁等待时间 |
|---------|---------|---------------|-------|-------------|-----------|
| 小型 | < 1万 | 100,000 | 0.01 | 50 | 2000ms |
| 中型 | 1-10万 | 1,000,000 | 0.01 | 100 | 3000ms |
| 大型 | 10-100万 | 10,000,000 | 0.01 | 200 | 3000ms |
| 超大型 | > 100万 | 100,000,000 | 0.005 | 500 | 5000ms |

## 布隆过滤器调优

### 容量规划

```java
// 计算公式
// m = -n * ln(p) / (ln(2)^2)
// 其中：
// m = 位数组大小
// n = 预期插入元素数量
// p = 误判率

// 示例计算
long n = 10_000_000;  // 预期1000万用户
double p = 0.01;       // 1%误判率
// m ≈ 95,850,584 bits ≈ 11.5 MB
```

### 配置示例

```yaml
apex:
  cache:
    bloom-filter:
      # 小型业务（10万用户）
      expected-insertions: 100000
      fpp: 0.01
      # 内存占用约：1.2 MB
      
      # 中型业务（100万用户）
      expected-insertions: 1000000
      fpp: 0.01
      # 内存占用约：11.5 MB
      
      # 大型业务（1000万用户）
      expected-insertions: 10000000
      fpp: 0.01
      # 内存占用约：115 MB
      
      # 超大型业务（1亿用户）
      expected-insertions: 100000000
      fpp: 0.005  # 更严格的误判率
      # 内存占用约：1.7 GB
```

### 误判率权衡

| 误判率 | 内存占用 | 适用场景 |
|-------|---------|---------|
| 0.001 | 最大 | 对准确性要求极高 |
| 0.005 | 较大 | 生产环境推荐 |
| 0.01 | 中等 | 一般业务场景 |
| 0.05 | 较小 | 开发测试环境 |

## TTL 策略优化

### 根据数据特征设置 TTL

```yaml
# 热点数据（高频访问）
hot_data_ttl: 5-15分钟
# 示例：用户信息、热门商品

# 普通数据（中频访问）
normal_data_ttl: 30-60分钟
# 示例：商品详情、文章内容

# 冷数据（低频访问）
cold_data_ttl: 2-6小时
# 示例：历史订单、归档数据

# 静态数据（极少变更）
static_data_ttl: 12-24小时
# 示例：地区列表、分类树
```

### TTL Jitter 效果

```
不使用 Jitter：
时间 → 0分  30分  60分  90分
缓存 → ████████████████████████ (同时过期 ❌)

使用 Jitter：
时间 → 0分  30分  60分  90分
缓存 → ██░█░░█░███░█░█░░██░░█ (分散过期 ✅)
```

## 缓存预热优化

### 预热策略选择

#### 1. 全量预热（适用于小规模数据）

```java
@Override
public Mono<Integer> prewarm() {
    return userRepository.findAll()
        .flatMap(user -> cacheService.set(
            "user:" + user.getId(),
            user,
            Duration.ofMinutes(30)
        ))
        .count()
        .map(Long::intValue);
}
```

#### 2. 热点数据预热（推荐）

```java
@Override
public Mono<Integer> prewarm() {
    return userRepository.findTop1000ByOrderByLastLoginTimeDesc()
        .flatMap(user -> cacheService.set(
            "user:" + user.getId(),
            user,
            Duration.ofMinutes(30)
        ))
        .count()
        .map(Long::intValue);
}
```

#### 3. 分批预热（大规模数据）

```java
@Override
public Mono<Integer> prewarm() {
    int batchSize = cacheProperties.getPrewarm().getBatchSize();
    long interval = cacheProperties.getPrewarm().getBatchIntervalMillis();
    
    return userRepository.findHotUserIds()
        .buffer(batchSize)
        .delayElements(Duration.ofMillis(interval))
        .flatMap(batch -> Flux.fromIterable(batch)
            .flatMap(this::loadAndCache))
        .count()
        .map(Long::intValue);
}
```

### 预热时机配置

```yaml
apex:
  cache:
    prewarm:
      # 小型业务：快速启动
      startup-delay-seconds: 30
      batch-size: 50
      batch-interval-millis: 50
      
      # 中型业务：平衡策略
      startup-delay-seconds: 60
      batch-size: 100
      batch-interval-millis: 100
      
      # 大型业务：稳健启动
      startup-delay-seconds: 120
      batch-size: 200
      batch-interval-millis: 200
```

## 分布式锁调优

### 锁参数设置

```yaml
apex:
  cache:
    breakdown:
      # 根据数据库查询时间调整
      # P95响应时间 < lock-wait-time < P99响应时间
      lock-wait-time-millis: 3000
      
      # 应该能够完成：DB查询 + 缓存写入
      # 建议设置为平均DB查询时间的3-5倍
      lock-lease-time-millis: 10000
      
      # 重试策略
      retry-times: 3
      retry-interval-millis: 100
```

### 性能测试数据参考

```
场景：1000 QPS，热点key过期
配置：lock-wait-time=3000ms, retry=3

结果：
- 无锁保护：DB连接数飙升至500+，响应时间2000ms+
- 有锁保护：DB连接数稳定在50以内，响应时间200ms
```

## 逻辑过期策略

### 适用场景判断

| 条件 | 使用逻辑过期 | 使用真实过期 |
|-----|------------|------------|
| 访问频率 | 极高（秒级） | 中低（分钟级） |
| 数据实时性 | 弱一致性可接受 | 要求强一致性 |
| 刷新成本 | 高（复杂查询） | 低（简单查询） |
| 并发压力 | 极大 | 一般 |

### 配置建议

```yaml
apex:
  cache:
    breakdown:
      # 仅对超热点key启用
      logical-expiry-enabled: true
      # 提前5分钟异步刷新
      logical-expiry-refresh-threshold-seconds: 300
```

### 使用示例

```java
// 超热点商品详情缓存
public Mono<Product> getHotProduct(Long productId) {
    String key = "product:hot:" + productId;
    
    return cacheService.get(key, LogicalExpiryWrapper.class)
        .flatMap(wrapper -> {
            if (wrapper == null) {
                return loadAndCacheProduct(productId);
            }
            
            // 未过期，直接返回
            if (!wrapper.isExpired()) {
                return Mono.just((Product) wrapper.getData());
            }
            
            // 已过期，异步刷新，返回旧数据
            asyncRefreshProduct(productId);
            return Mono.just((Product) wrapper.getData());
        });
}
```

## Redis 连接池优化

### Redisson 配置优化

```yaml
# application.yml
spring:
  redis:
    redisson:
      config: |
        singleServerConfig:
          # 连接池大小（根据并发量调整）
          connectionPoolSize: 64
          connectionMinimumIdleSize: 24
          # 超时设置
          timeout: 3000
          connectTimeout: 10000
          # 重试策略
          retryAttempts: 3
          retryInterval: 1500
```

### 连接池大小计算

```
公式：
connectionPoolSize = (期望QPS / 单连接QPS) * 1.5

示例：
- 期望QPS: 10,000
- 单连接QPS: 5,000（Redis单连接约5000 QPS）
- 计算: (10000 / 5000) * 1.5 = 3 * 1.5 ≈ 5

建议最小连接池: 5
建议最大连接池: 10-20（预留缓冲）
```

## 性能基准测试

### 测试脚本示例

```bash
#!/bin/bash
# cache-benchmark.sh

echo "=== 缓存性能基准测试 ==="

# 1. 缓存写入性能
echo "\n1. 测试缓存写入性能（10000次）"
ab -n 10000 -c 100 -p post.json -T application/json \
   http://localhost:8080/api/cache/set

# 2. 缓存读取性能
echo "\n2. 测试缓存读取性能（10000次）"
ab -n 10000 -c 100 \
   http://localhost:8080/api/cache/get?key=test

# 3. 布隆过滤器性能
echo "\n3. 测试布隆过滤器性能（10000次）"
ab -n 10000 -c 100 \
   http://localhost:8080/api/cache/bloom/check?key=test

# 4. 缓存击穿压测
echo "\n4. 测试缓存击穿防护（热点key过期）"
ab -n 5000 -c 500 \
   http://localhost:8080/api/cache/hotkey
```

### 性能指标参考

```
预期性能指标（生产环境）：

L1缓存（Caffeine）：
- QPS: 1,000,000+
- 延迟: < 1ms (P99)

L2缓存（Redis）：
- QPS: 50,000+
- 延迟: < 5ms (P99)

布隆过滤器：
- QPS: 100,000+
- 延迟: < 2ms (P99)

缓存预热：
- 速度: 1000条/秒
- 启动延迟: < 120秒
```

## JVM 调优

### 堆内存配置

```bash
# 小型部署（2GB内存）
-Xms512m -Xmx1g -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m

# 中型部署（4GB内存）
-Xms1g -Xmx2g -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m

# 大型部署（8GB内存）
-Xms2g -Xmx4g -XX:MetaspaceSize=512m -XX:MaxMetaspaceSize=1g

# 超大型部署（16GB内存）
-Xms4g -Xmx8g -XX:MetaspaceSize=512m -XX:MaxMetaspaceSize=1g
```

### GC 调优

```bash
# G1GC（推荐用于大堆）
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:G1HeapRegionSize=16m
-XX:InitiatingHeapOccupancyPercent=45

# ZGC（Java 17+，超低延迟）
-XX:+UseZGC
-XX:ZCollectionInterval=120
```

## 监控和诊断

### JVM 监控指标

```yaml
# 重点关注指标
monitoring:
  jvm:
    - heap_used_percent  # 堆使用率 < 80%
    - gc_pause_time     # GC暂停时间 < 100ms
    - thread_count      # 线程数稳定
    
  cache:
    - caffeine_cache_size      # L1缓存大小
    - caffeine_eviction_count  # 淘汰次数
    - redis_connection_active  # Redis活跃连接
```

### 性能诊断工具

```bash
# 1. JVM 诊断
jstack <pid>  # 线程堆栈
jmap -heap <pid>  # 堆内存分析
jstat -gcutil <pid> 1000  # GC统计

# 2. Redis 诊断
redis-cli --bigkeys  # 大key扫描
redis-cli --latency  # 延迟监控
redis-cli slowlog get 10  # 慢查询

# 3. 缓存统计
curl http://localhost:8080/actuator/metrics/cache.size
curl http://localhost:8080/actuator/metrics/cache.gets
curl http://localhost:8080/actuator/metrics/cache.puts
```

## 压测场景配置

### 场景1：正常流量

```yaml
# JMeter配置
threads: 100
ramp_up: 10
duration: 300
target_qps: 1000
```

### 场景2：流量突增

```yaml
threads: 500
ramp_up: 5
duration: 60
target_qps: 5000
```

### 场景3：缓存雪崩模拟

```bash
# 1. 清空所有缓存
redis-cli FLUSHALL

# 2. 立即发起高并发请求
ab -n 10000 -c 1000 http://localhost:8080/api/products
```

### 场景4：缓存击穿模拟

```bash
# 1. 删除热点key
redis-cli DEL hot:product:12345

# 2. 并发请求该key
ab -n 5000 -c 500 http://localhost:8080/api/product/12345
```

## 调优检查清单

- [ ] 布隆过滤器容量是否匹配业务规模
- [ ] TTL设置是否合理（热点数据短TTL）
- [ ] 缓存预热是否覆盖热点数据
- [ ] 分布式锁超时是否大于DB查询时间
- [ ] Redis连接池大小是否足够
- [ ] JVM堆内存配置是否合适
- [ ] GC暂停时间是否在可接受范围
- [ ] 监控告警规则是否完善
- [ ] 压测是否通过所有场景

## 性能优化案例

### 案例1：命中率提升

**问题**：缓存命中率仅60%

**分析**：
- TTL过短（5分钟）
- 未启用缓存预热

**优化**：
- 调整热点数据TTL至30分钟
- 启用缓存预热，预热Top1000热点数据

**结果**：命中率提升至85%

### 案例2：响应时间优化

**问题**：热点商品详情页P99延迟300ms

**分析**：
- 缓存过期时大量并发查询DB
- 单点Redis压力大

**优化**：
- 启用逻辑过期策略
- Redis分片部署

**结果**：P99延迟降至50ms

### 案例3：稳定性提升

**问题**：缓存雪崩导致服务不可用

**分析**：
- 大量缓存同时过期
- 无降级保护

**优化**：
- 启用TTL jitter
- 启用读降级

**结果**：服务稳定性99.95% → 99.99%

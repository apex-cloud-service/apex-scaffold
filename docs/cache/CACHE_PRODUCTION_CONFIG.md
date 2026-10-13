# 缓存优化生产环境配置示例

## 配置文件：application-prod.yml

```yaml
apex:
  cache:
    # 布隆过滤器配置（缓存穿透防护）
    bloom-filter:
      # 生产环境建议启用
      enabled: true
      # 根据实际业务规模调整预期插入数（用户数、商品数等）
      expected-insertions: 10000000  # 1000万
      # 误判率：0.01 = 1%，越小占用内存越多
      fpp: 0.01
      redis-key-prefix: "apex:cache:bloom:"
    
    # 缓存击穿防护配置
    breakdown:
      # 生产环境强烈建议启用
      enabled: true
      # 分布式锁等待时间（毫秒）
      lock-wait-time-millis: 3000
      # 分布式锁租约时间（毫秒）
      lock-lease-time-millis: 10000
      # 获取锁失败后的重试次数
      retry-times: 3
      # 重试间隔（毫秒）
      retry-interval-millis: 100
      # 是否启用逻辑过期（适用于热点key场景）
      logical-expiry-enabled: false
      # 逻辑过期刷新阈值（秒）
      logical-expiry-refresh-threshold-seconds: 300
    
    # 缓存预热配置
    prewarm:
      # 生产环境根据需要启用
      enabled: true
      # 启动后延迟预热时间（秒），避免启动阶段压力过大
      startup-delay-seconds: 60
      # 批量预热大小
      batch-size: 100
      # 批次间隔（毫秒），控制预热速度
      batch-interval-millis: 100
    
    # 读降级配置（Enterprise 版本特性）
    read-degradation:
      # 是否启用读降级（DB故障时返回过期缓存）
      enabled: true
      # 降级时允许返回的最大过期时间（秒）
      max-stale-seconds: 3600  # 1小时
```

## 环境变量注入（推荐）

生产环境建议通过环境变量注入配置：

```bash
# 布隆过滤器
export APEX_CACHE_BLOOM_FILTER_ENABLED=true
export APEX_CACHE_BLOOM_FILTER_EXPECTED_INSERTIONS=10000000
export APEX_CACHE_BLOOM_FILTER_FPP=0.01

# 缓存击穿防护
export APEX_CACHE_BREAKDOWN_ENABLED=true
export APEX_CACHE_BREAKDOWN_LOCK_WAIT_TIME_MILLIS=3000

# 缓存预热
export APEX_CACHE_PREWARM_ENABLED=true
export APEX_CACHE_PREWARM_STARTUP_DELAY_SECONDS=60

# 读降级
export APEX_CACHE_READ_DEGRADATION_ENABLED=true
export APEX_CACHE_READ_DEGRADATION_MAX_STALE_SECONDS=3600
```

## Kubernetes ConfigMap 示例

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: apex-cache-config
  namespace: production
data:
  application-prod.yml: |
    apex:
      cache:
        bloom-filter:
          enabled: true
          expected-insertions: 10000000
          fpp: 0.01
        breakdown:
          enabled: true
          lock-wait-time-millis: 3000
        prewarm:
          enabled: true
          startup-delay-seconds: 60
```

## Podman Compose 示例

```yaml
version: '3.8'
services:
  apex-app:
    image: apex-scaffold:latest
    environment:
      - APEX_CACHE_BLOOM_FILTER_ENABLED=true
      - APEX_CACHE_BREAKDOWN_ENABLED=true
      - APEX_CACHE_PREWARM_ENABLED=true
    depends_on:
      - redis
      - mysql
```

## 分环境配置建议

### 开发环境（dev）
- 布隆过滤器：关闭或使用小容量
- 缓存击穿防护：启用
- 缓存预热：关闭
- 读降级：关闭

### 测试环境（test）
- 布隆过滤器：启用，中等容量
- 缓存击穿防护：启用
- 缓存预热：启用
- 读降级：关闭

### 预生产环境（staging）
- 布隆过滤器：启用，生产级容量
- 缓存击穿防护：启用
- 缓存预热：启用
- 读降级：启用

### 生产环境（prod）
- 布隆过滤器：启用，按实际规模配置
- 缓存击穿防护：启用
- 缓存预热：启用
- 读降级：启用

## 注意事项

1. **布隆过滤器容量规划**
   - `expected-insertions` 应该设置为预期最大数据量的 1.2-1.5 倍
   - 过小会导致误判率上升，过大会浪费内存
   - 建议按业务增长预留 50% 空间

2. **缓存预热启动延迟**
   - 生产环境建议设置 60-120 秒
   - 避免应用启动阶段压力过大
   - 确保基础设施（Redis、DB）已就绪

3. **读降级使用场景**
   - 适用于可用性优先的场景
   - 不适用于强一致性要求的场景
   - 建议配合监控告警使用

4. **分布式锁超时设置**
   - `lock-wait-time-millis` 应该大于正常DB查询时间
   - `lock-lease-time-millis` 应该足够完成DB查询和缓存写入
   - 建议根据P95/P99响应时间设置

## 配置验证

启动应用后，检查日志确认配置生效：

```bash
# 检查布隆过滤器初始化日志
grep "布隆过滤器初始化" application.log

# 检查缓存预热日志
grep "缓存预热" application.log

# 检查TTL jitter生效
grep "缓存写入" application.log
```

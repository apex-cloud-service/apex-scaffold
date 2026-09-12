# 限流算法集成说明

## 集成范围

项目当前采用三类算法：

- 令牌桶：允许受控突发，适合缓存回源、API 限流和自适应限流。
- 漏桶：以稳定速率输出，适合 MQ 消费和下游保护。
- 分片滑动窗口：按时间子桶统计，适合本地降级、IP 与热点参数限流。

## 当前接入点

### 本地限流降级

`LocalRateLimiter` 默认使用分片滑动窗口，也可传入 `RateLimitAlgorithm.TOKEN_BUCKET` 或 `LEAKY_BUCKET`。

### 用户级限流

Redis 不可用时，`UserRateLimitAspect` 读取 `apex.ratelimit.local-fallback.algorithm` 选择本地算法。

### MQ 消费

`MqConsumerService` 在幂等抢占成功后、业务处理前使用漏桶。桶满时释放当前处理令牌并返回可重试失败，避免业务处理被丢弃。

### 缓存回源

`MultiLevelCacheService.getWithLoader` 在 Single-Flight 回源任务内部使用令牌桶，仅真实 loader 执行消耗令牌，不影响 L1/L2 命中请求，也不会让同 key 并发请求重复消耗令牌。

### 自适应限流

`AdaptiveRateLimiter.tryAcquireAdaptive` 根据当前允许 QPS 创建令牌桶，负载升高时限制准入速率。

## 配置

配置模板位于 `apex-interfaces/src/main/resources/application-rate-limit.yml`。启用时将其内容合并到当前 profile 配置。

重要参数：

- `apex.ratelimit.local-fallback.algorithm`
- `apex.ratelimit.token-bucket.default-capacity`
- `apex.ratelimit.token-bucket.default-refill-rate`
- `apex.ratelimit.leaky-bucket.default-capacity`
- `apex.ratelimit.leaky-bucket.default-leak-rate`
- `apex.ratelimit.sliding-window.default-window-ms`
- `apex.ratelimit.sliding-window.default-bucket-count`

## 测试

`RateLimitAlgorithmTest` 覆盖三种算法的基本准入、拒绝和时间恢复行为。建议执行：

```bash
source ./scripts/build/init-build-env.sh
mvnd -pl apex-common,apex-infrastructure -am test
mvnd spotless:check
```

## 运行注意事项

本地算法是单 JVM 限流器。多实例场景仍应优先使用 Redis 分布式限流；本地算法仅作为 Redis 故障时的安全降级。算法参数应结合容量、平均 QPS、下游处理能力和监控指标逐步调优。

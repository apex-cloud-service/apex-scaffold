# DB 故障读降级使用指南

> **Enterprise License 特性** - 仅 Enterprise 版本可用  
> **配套架构图**: [多级缓存架构](diagrams/10_multi_level_cache_architecture.svg)

## 1. 概述

Apex Scaffold 的多级缓存服务（`MultiLevelCacheService`）支持 **DB 故障读降级**功能，当数据库不可用时，自动从过期缓存中读取旧数据作为兜底，保障系统可用性。

### 1.1 核心特性

- **自动降级**：DB 查询失败时，自动尝试从过期缓存读取

- **时效控制**：可配置最大过期时间（max-stale-seconds），超过限制则拒绝降级

- **降级指标**：记录降级次数（`apex_cache_read_degradation_total`），便于监控告警

- **透明切换**：业务代码无感知，由缓存层自动处理

- **可选启用**：通过配置开关控制，默认关闭，Enterprise 版本建议开启

### 1.2 适用场景

| 场景       | 适用性     | 说明                      |
| -------- | ------- | ----------------------- |
| **商品详情** | ✅ 高度适用  | 价格/库存允许短时间不一致，优先保障页面可访问 |
| **用户资料** | ✅ 高度适用  | 头像/昵称等信息变更频率低，允许返回旧数据   |
| **配置信息** | ✅ 高度适用  | 系统配置、字典数据等低频变更数据        |
| **内容展示** | ✅ 高度适用  | 文章、评论等内容，短时间旧数据不影响用户体验  |
| **订单状态** | ⚠️ 谨慎使用 | 需评估业务容忍度，可能导致用户看到过期状态   |
| **支付金额** | ❌ 不适用   | 强一致性要求，不允许返回过期数据        |
| **库存扣减** | ❌ 不适用   | 库存操作需强一致性，不允许降级         |
| **账户余额** | ❌ 不适用   | 金融数据需强一致性，不允许降级         |

### 1.3 降级策略

```
┌─────────────────────────────────────────────┐
│  1. 尝试读取 L1 缓存（Caffeine 本地缓存）    │
│     ↓ Miss                                  │
│  2. 尝试读取 L2 缓存（Redis 分布式缓存）     │
│     ↓ Miss                                  │
│  3. 回源 DB 查询                            │
│     ↓ 异常（连接超时、SQL 执行失败等）       │
│  4. 检查读降级开关（enabled=true?）          │
│     ↓ Yes                                   │
│  5. 尝试读取过期缓存（Redis stale data）     │
│     ↓                                       │
│  6. 检查过期时间（< max-stale-seconds?）     │
│     ↓ Yes → 返回旧数据 + 记录降级指标        │
│     ↓ No  → 抛出异常                        │
└─────────────────────────────────────────────┘
```

## 2. 配置说明

### 2.1 启用读降级（Enterprise 配置）

```yaml
# application-enterprise.yml
apex:
  cache:
    read-degradation:
      # ✅ 启用读降级（默认 true）
      enabled: true
      # 允许返回的最大过期时间（秒，默认 3600=1 小时）
      max-stale-seconds: 3600
```

### 2.2 配置项说明

| 配置项                 | 类型      | 默认值   | 说明                            |
| ------------------- | ------- | ----- | ----------------------------- |
| `enabled`           | Boolean | false | 是否启用读降级（Enterprise 版本建议 true） |
| `max-stale-seconds` | Long    | 3600  | 允许返回的最大过期时间（秒），超过此时间的旧数据会被拒绝  |

### 2.3 业务级配置（可选）

如果需要为不同业务设置不同的降级策略，可以在调用 `MultiLevelCacheService` 时传递自定义配置：

```java
// 商品详情：允许 2 小时过期数据
cacheService.getWithLoader(
    "product:detail:" + productId,
    () -> productRepository.findById(productId),
    Duration.ofMinutes(30),  // 正常缓存 TTL
    Duration.ofHours(2)      // 降级最大过期时间
);

// 用户资料：允许 24 小时过期数据
cacheService.getWithLoader(
    "user:profile:" + userId,
    () -> userRepository.findById(userId),
    Duration.ofHours(1),     // 正常缓存 TTL
    Duration.ofHours(24)     // 降级最大过期时间
);
```

## 3. 使用示例

### 3.1 基本用法

```java
@Service
public class ProductService {
    
    private final MultiLevelCacheService cacheService;
    private final ProductRepository productRepository;
    
    public Mono<Product> getProductById(Long productId) {
        return cacheService.getWithLoader(
            "product:" + productId,
            () -> productRepository.findById(productId),
            Duration.ofMinutes(30)  // 缓存 30 分钟
        );
    }
}
```

**工作流程**：

1. 正常情况：DB 可用 → 缓存未命中时回源 DB → 写入缓存
2. 降级情况：DB 故障 → 缓存未命中 → **从过期缓存读取旧数据**
3. 无法降级：过期时间超过 max-stale-seconds → 抛出异常

### 3.2 自定义降级时间

```java
@Service
public class ConfigService {
    
    private final MultiLevelCacheService cacheService;
    private final ConfigRepository configRepository;
    
    public Mono<Config> getConfig(String key) {
        // 配置信息允许返回更旧的数据（24 小时）
        return cacheService.getWithLoader(
            "config:" + key,
            () -> configRepository.findByKey(key),
            Duration.ofHours(6),   // 正常缓存 6 小时
            Duration.ofHours(24)   // 降级最大过期时间 24 小时
        );
    }
}
```

### 3.3 禁用降级（强一致性场景）

```java
@Service
public class PaymentService {
    
    private final MultiLevelCacheService cacheService;
    private final PaymentRepository paymentRepository;
    
    public Mono<Payment> getPaymentById(Long paymentId) {
        // 支付数据不允许降级，DB 故障直接抛异常
        return cacheService.getWithLoader(
            "payment:" + paymentId,
            () -> paymentRepository.findById(paymentId),
            Duration.ofMinutes(5),
            Duration.ZERO  // 禁用降级
        );
    }
}
```

## 4. 监控与告警

### 4.1 降级指标

读降级会记录 Prometheus 指标：

```prometheus
# 降级次数（按 cache_key 标签分组）
apex_cache_read_degradation_total{cache_key="product:123"} 5
apex_cache_read_degradation_total{cache_key="user:456"} 2
```

### 4.2 Grafana 面板示例

```yaml
# 降级趋势图
- title: "缓存读降级趋势"
  expr: rate(apex_cache_read_degradation_total[5m])
  legend: "{{cache_key}}"

# 降级占比
- title: "降级请求占比"
  expr: |
    sum(rate(apex_cache_read_degradation_total[5m])) 
    / 
    sum(rate(apex_cache_get_total[5m]))
```

### 4.3 告警规则

```yaml
# 降级次数过高告警
- alert: HighCacheReadDegradation
  expr: rate(apex_cache_read_degradation_total[5m]) > 10
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "缓存读降级频率过高"
    description: "过去 5 分钟内 {{$labels.cache_key}} 降级次数超过 10 次/秒，可能 DB 不可用"

# DB 持续故障告警
- alert: DatabaseUnavailable
  expr: rate(apex_cache_read_degradation_total[5m]) > 0
  for: 10m
  labels:
    severity: critical
  annotations:
    summary: "数据库持续不可用"
    description: "过去 10 分钟持续触发缓存降级，DB 可能宕机"
```

## 5. 日志示例

### 5.1 正常降级日志

```log
2026-09-02 17:30:15.123 WARN  [MultiLevelCacheService] - DB 查询失败，尝试读降级, key: product:123, error: Connection timeout
2026-09-02 17:30:15.125 INFO  [MultiLevelCacheService] - 读降级成功，返回过期数据, key: product:123, stale: 1800s
```

### 5.2 降级失败日志

```log
2026-09-02 17:30:20.456 WARN  [MultiLevelCacheService] - DB 查询失败，尝试读降级, key: product:456, error: SQL execution failed
2026-09-02 17:30:20.458 ERROR [MultiLevelCacheService] - 读降级失败，过期数据超过限制, key: product:456, stale: 7200s, maxStale: 3600s
```

### 5.3 降级关闭日志

```log
2026-09-02 17:30:25.789 WARN  [MultiLevelCacheService] - DB 查询失败, key: payment:789, error: Connection timeout
2026-09-02 17:30:25.790 ERROR [MultiLevelCacheService] - 读降级未启用，直接抛出异常
```

## 6. 最佳实践

### 6.1 业务评估清单

在启用读降级前，务必评估以下问题：

- [ ] 业务能否容忍短时间数据不一致？

- [ ] 旧数据对用户体验的影响程度？

- [ ] 是否涉及金融、库存等强一致性数据？

- [ ] 是否有监控告警及时发现 DB 故障？

- [ ] 是否有应急预案（如手动切流、DB 快速恢复）？

### 6.2 配置建议

| 数据类型  | max-stale-seconds 建议 | 说明           |
| ----- | -------------------- | ------------ |
| 商品详情  | 3600（1 小时）           | 价格/库存变更不频繁   |
| 用户资料  | 86400（24 小时）         | 头像/昵称等低频变更   |
| 系统配置  | 86400（24 小时）         | 配置几乎不变       |
| 内容展示  | 7200（2 小时）           | 文章/评论等内容     |
| 订单状态  | 600（10 分钟）           | 状态变更较频繁，谨慎使用 |
| 支付/库存 | 0（禁用）                | 强一致性要求       |

### 6.3 性能优化

- **合理设置 TTL**：正常缓存 TTL 不宜过短，避免频繁回源

- **预热关键数据**：系统启动时预加载高频数据到缓存

- **批量查询优化**：使用 `mget` 批量读取缓存，减少网络开销

- **异步刷新**：使用 Caffeine 的 `refreshAfterWrite` 异步刷新缓存

### 6.4 故障演练

定期进行 DB 故障演练，验证降级策略有效性：

```bash
# 1. 模拟 DB 故障（暂停 MySQL 容器）
docker pause apex-mysql

# 2. 观察降级指标
curl http://localhost:8080/actuator/prometheus | grep apex_cache_read_degradation_total

# 3. 验证业务可用性
curl http://localhost:8080/api/v1/products/123

# 4. 恢复 DB
docker unpause apex-mysql
```

## 7. 故障排查

### 7.1 降级未生效

**症状**：DB 故障后直接返回 500 错误，未触发降级

**排查步骤**：

1. 检查配置：`apex.cache.read-degradation.enabled=true`
2. 检查 License：确保 Enterprise License 有效
3. 检查缓存：Redis 中是否存在过期数据（`TTL key` 返回 -2 表示已删除）
4. 检查日志：是否有 "读降级未启用" 或 "过期数据超过限制" 日志

### 7.2 降级返回空数据

**症状**：降级成功但返回 null

**原因**：缓存中存储的是空值（cache-aside 模式）

**解决方案**：使用空对象标记代替 null，或在业务层处理空值

### 7.3 降级频率过高

**症状**：`apex_cache_read_degradation_total` 持续增长

**排查步骤**：

1. 检查 DB 状态：连接池是否满、慢查询、死锁
2. 检查网络：DB 与应用之间的网络延迟
3. 检查缓存 TTL：是否设置过短导致频繁回源
4. 检查并发量：是否超过 DB 承载能力

## 8. 与其他特性对比

| 特性       | 读降级                       | 熔断器       | 本地缓存   |
| -------- | ------------------------- | --------- | ------ |
| **目标**   | 保障可用性                     | 快速失败      | 减少网络开销 |
| **触发条件** | DB 故障                     | 错误率/慢调用   | 缓存命中   |
| **返回结果** | 旧数据                       | 错误响应/降级响应 | 最新缓存数据 |
| **适用场景** | 可用性优先                     | 故障隔离      | 高频读取   |
| **联动使用** | ✅ 建议组合使用（熔断 + 读降级 + 本地缓存） | <br />    | <br /> |

## 9. 未来扩展方向

- **自适应降级**：根据 DB 负载自动调整降级策略

- **多版本降级**：支持返回多个历史版本供用户选择

- **降级预警**：DB 响应时间超过阈值时提前启用降级

- **智能刷新**：降级期间异步尝试刷新缓存，减少旧数据时长

***

**License**: Enterprise Only\
**Version**: 1.3.0\
**Support**: [Apex Scaffold 官方文档](https://apex-scaffold.com)

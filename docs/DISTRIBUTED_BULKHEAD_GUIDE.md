# 分布式舱壁隔离使用指南

> **Enterprise License 特性** - 仅 Enterprise 版本可用  
> **配套架构图**: [分布式 Bulkhead 隔离](diagrams/66_distributed_bulkhead_isolation.svg) · [Sentinel 限流熔断降级架构](diagrams/10_rate_limit_circuit_breaker.svg)

## 1. 概述

Apex Scaffold 的 **分布式舱壁隔离**（Distributed Bulkhead）基于 Redisson 分布式信号量实现跨实例的并发控制，防止单个下游服务故障或慢调用拖垮整个集群。

### 1.1 核心特性

- **分布式信号量**：基于 Redis 实现跨实例的许可证（permit）管理，多实例共享并发上限

- **快速失败**：超限时立即拒绝（不排队），避免请求堆积和资源耗尽

- **自动释放**：支持超时自动释放许可证，防止泄漏

- **拒绝指标**：记录拒绝次数（`apex_bulkhead_rejected_total`），便于监控告警

- **熔断联动**：建议与 `CircuitBreakerService` 配合使用，提供多层防护

### 1.2 与本地 Bulkhead 的区别

| 维度        | 本地 Bulkhead（Resilience4j） | 分布式 Bulkhead（Redisson） |
| --------- | ------------------------- | ---------------------- |
| **并发控制**  | 单实例内隔离                    | 跨所有实例隔离                |
| **适用场景**  | 单机部署、内部隔离                 | 多实例集群、下游保护             |
| **许可证存储** | 本地内存                      | Redis 分布式存储            |
| **故障影响**  | 实例间独立                     | 全局共享配额                 |
| **性能开销**  | 极低（纯内存）                   | 较低（Redis 网络调用）         |

### 1.3 适用场景

| 场景             | 适用性     | 说明                              |
| -------------- | ------- | ------------------------------- |
| **第三方 API 调用** | ✅ 高度适用  | 限制对支付网关、短信服务、AI 推理等外部 API 的并发调用 |
| **慢查询保护**      | ✅ 高度适用  | 限制报表生成、全表扫描等耗时操作的并发数            |
| **租户资源隔离**     | ✅ 高度适用  | SaaS 多租户场景，防止单租户占用过多资源          |
| **下游服务保护**     | ✅ 高度适用  | 保护微服务间调用，防止雪崩                   |
| **高频轻量操作**     | ⚠️ 谨慎使用 | Redis 网络开销可能超过业务逻辑本身            |
| **单机部署**       | ❌ 不适用   | 使用 Resilience4j 本地 Bulkhead 即可  |

## 2. 配置说明

### 2.1 启用分布式舱壁（Enterprise 配置）

```yaml
# application-enterprise.yml
apex:
  bulkhead:
    # ✅ 启用分布式舱壁（默认 true）
    enabled: true
    # 最大并发调用数（所有实例共享，默认 25）
    max-concurrent-calls: 25
    # 许可证超时时间（毫秒，默认 60000=1 分钟，防止泄漏）
    permit-timeout-ms: 60000
```

### 2.2 配置项说明

| 配置项                    | 类型      | 默认值   | 说明                              |
| ---------------------- | ------- | ----- | ------------------------------- |
| `enabled`              | Boolean | false | 是否启用分布式舱壁（Enterprise 版本建议 true） |
| `max-concurrent-calls` | Integer | 25    | 最大并发调用数，所有实例共享此上限               |
| `permit-timeout-ms`    | Long    | 60000 | 许可证超时时间（毫秒），防止许可证泄漏             |

### 2.3 按场景调优

| 场景           | max-concurrent-calls 建议 | permit-timeout-ms 建议 |
| ------------ | ----------------------- | -------------------- |
| 第三方 API（如支付） | 10-20                   | 30000（30 秒）          |
| AI 推理服务      | 5-10                    | 60000（1 分钟）          |
| 慢查询报表        | 3-5                     | 300000（5 分钟）         |
| 下游微服务        | 50-100                  | 10000（10 秒）          |
| 租户隔离         | 按租户数调整                  | 根据业务设定               |

## 3. 使用示例

### 3.1 基本用法

```java
@Service
public class PaymentService {
    
    private final DistributedBulkheadService bulkheadService;
    private final PaymentGatewayClient paymentClient;
    
    public Mono<PaymentResult> processPayment(PaymentRequest request) {
        // 限制对支付网关的并发调用，全局最多 10 个并发
        return bulkheadService.execute(
            "payment-gateway",  // 舱壁隔离键
            () -> paymentClient.pay(request)  // 实际操作
        );
    }
}
```

**工作流程**：

1. 尝试获取分布式信号量许可证
2. 成功 → 执行操作 → 自动释放许可证
3. 失败（超限）→ 快速失败，返回 503 错误

### 3.2 与熔断器联动

```java
@Service
public class ThirdPartyApiService {
    
    private final DistributedBulkheadService bulkheadService;
    private final CircuitBreakerService circuitBreakerService;
    private final ThirdPartyClient thirdPartyClient;
    
    public Mono<ApiResponse> callThirdPartyApi(ApiRequest request) {
        // 双层防护：先舱壁限流，再熔断保护
        return bulkheadService.execute(
            "third-party-api",
            () -> circuitBreakerService.execute(
                "third-party-api",
                () -> thirdPartyClient.call(request)
            )
        );
    }
}
```

**防护层级**：

1. **舱壁层**：限制并发数，防止资源耗尽
2. **熔断层**：错误率过高时快速失败，避免雪崩

### 3.3 租户资源隔离

```java
@Service
public class ReportService {
    
    private final DistributedBulkheadService bulkheadService;
    private final ReportGenerator reportGenerator;
    
    public Mono<Report> generateReport(Long tenantId, ReportRequest request) {
        // 按租户隔离，每个租户最多 3 个并发报表任务
        return bulkheadService.execute(
            "report:tenant:" + tenantId,  // 按租户分组
            () -> reportGenerator.generate(request)
        );
    }
}
```

### 3.4 慢查询保护

```java
@Service
public class AnalyticsService {
    
    private final DistributedBulkheadService bulkheadService;
    private final AnalyticsRepository analyticsRepository;
    
    public Mono<AnalyticsResult> runSlowQuery(AnalyticsQuery query) {
        // 限制慢查询并发，防止 DB 被拖垮
        return bulkheadService.execute(
            "slow-query:analytics",
            () -> analyticsRepository.aggregateData(query)
        );
    }
}
```

## 4. 监控与告警

### 4.1 拒绝指标

舱壁超限会记录 Prometheus 指标：

```prometheus
# 拒绝次数（按 bulkhead_key 标签分组）
apex_bulkhead_rejected_total{bulkhead_key="payment-gateway"} 15
apex_bulkhead_rejected_total{bulkhead_key="third-party-api"} 8
```

### 4.2 Grafana 面板示例

```yaml
# 拒绝趋势图
- title: "舱壁拒绝趋势"
  expr: rate(apex_bulkhead_rejected_total[5m])
  legend: "{{bulkhead_key}}"

# 拒绝占比
- title: "拒绝请求占比"
  expr: |
    sum(rate(apex_bulkhead_rejected_total[5m])) 
    / 
    sum(rate(apex_bulkhead_requests_total[5m]))

# 可用许可证数
- title: "可用许可证数"
  expr: apex_bulkhead_available_permits
  legend: "{{bulkhead_key}}"
```

### 4.3 告警规则

```yaml
# 拒绝率过高告警
- alert: HighBulkheadRejectionRate
  expr: |
    rate(apex_bulkhead_rejected_total[5m]) 
    / 
    rate(apex_bulkhead_requests_total[5m]) > 0.2
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "舱壁拒绝率过高"
    description: "{{$labels.bulkhead_key}} 过去 5 分钟拒绝率超过 20%，可能需要扩容或优化"

# 持续拒绝告警
- alert: BulkheadContinuousRejection
  expr: rate(apex_bulkhead_rejected_total[5m]) > 10
  for: 10m
  labels:
    severity: critical
  annotations:
    summary: "舱壁持续拒绝请求"
    description: "{{$labels.bulkhead_key}} 过去 10 分钟持续拒绝请求，下游服务可能不可用"
```

## 5. 日志示例

### 5.1 正常执行日志

```log
2026-09-02 17:40:10.123 DEBUG [DistributedBulkheadService] - 获取分布式信号量成功, key: payment-gateway, permitId: 1a2b3c4d
2026-09-02 17:40:10.456 DEBUG [DistributedBulkheadService] - 释放分布式信号量, key: payment-gateway, permitId: 1a2b3c4d
```

### 5.2 超限拒绝日志

```log
2026-09-02 17:40:15.789 WARN  [DistributedBulkheadService] - 分布式舱壁超限，快速失败, key: payment-gateway, maxConcurrent: 10
```

### 5.3 许可证泄漏日志

```log
2026-09-02 17:40:20.012 ERROR [DistributedBulkheadService] - 释放分布式信号量失败, key: payment-gateway, permitId: 5e6f7g8h, error: Permit not found
```

## 6. 最佳实践

### 6.1 舱壁键设计原则

- **粒度适中**：不宜过粗（全局单键）或过细（每个请求一个键）

- **业务相关**：按下游服务、租户、操作类型等业务维度分组

- **命名规范**：使用 `service:operation:tenant` 格式，便于监控和排查

**示例**：

```java
// ✅ 推荐：按服务 + 操作分组
"payment-gateway:charge"
"payment-gateway:refund"

// ✅ 推荐：按租户隔离
"report:tenant:123"
"report:tenant:456"

// ❌ 不推荐：过于粗粒度
"all-third-party-apis"  // 无法区分不同服务

// ❌ 不推荐：过于细粒度
"payment:" + orderId  // 每个订单一个键，无法复用
```

### 6.2 并发数设置建议

```
max-concurrent-calls = 下游服务容量 × 80% / 上游实例数

示例：
- 支付网关 QPS 上限 100，集群 5 个实例
- max-concurrent-calls = 100 × 0.8 / 5 = 16
```

### 6.3 超时时间设置建议

```
permit-timeout-ms = P99 响应时间 × 2

示例：
- 第三方 API P99 = 5 秒
- permit-timeout-ms = 5000 × 2 = 10000（10 秒）
```

### 6.4 与熔断器配合使用

```java
// 推荐顺序：舱壁 → 熔断 → 重试
bulkheadService.execute("api", () ->
    circuitBreakerService.execute("api", () ->
        retryTemplate.execute(ctx ->
            apiClient.call()
        )
    )
);
```

**防护层级**：

1. **舱壁**：限制并发，防止资源耗尽
2. **熔断**：错误率过高时快速失败
3. **重试**：临时性故障自动恢复

### 6.5 性能优化

- **减少 Redis 调用**：对于高频轻量操作，考虑使用本地 Bulkhead

- **批量操作**：将多个小请求合并为批量请求，减少许可证获取次数

- **异步执行**：使用 Reactor/CompletableFuture 异步执行，提高吞吐量

- **预热许可证**：系统启动时预先初始化信号量，避免首次调用延迟

## 7. 故障排查

### 7.1 拒绝率过高

**症状**：大量请求被拒绝（503 错误）

**排查步骤**：

1. 检查并发数配置：是否设置过低
2. 检查下游服务：是否响应变慢或不可用
3. 检查实例数：是否需要扩容上游实例
4. 检查业务流量：是否突发流量超过预期

**解决方案**：

- 调高 `max-concurrent-calls`

- 优化下游服务性能

- 扩容上游实例

- 增加降级逻辑（如返回缓存数据）

### 7.2 许可证泄漏

**症状**：可用许可证数持续减少，最终归零

**排查步骤**：

1. 检查超时配置：`permit-timeout-ms` 是否设置合理
2. 检查异常处理：业务异常是否导致 `doFinally` 未执行
3. 检查 Redis：信号量 key 的 TTL 是否正常

**解决方案**：

- 调整 `permit-timeout-ms`，自动释放泄漏许可证

- 检查业务代码，确保所有路径都会释放许可证

- 定期清理 Redis 中的僵尸信号量

### 7.3 分布式锁竞争

**症状**：获取许可证耗时长，影响性能

**排查步骤**：

1. 检查 Redis 性能：网络延迟、CPU 使用率
2. 检查并发模式：是否存在大量争抢同一舱壁键的场景
3. 检查 Redisson 配置：连接池、超时设置

**解决方案**：

- 优化 Redis 性能（如使用 Redis Cluster）

- 细化舱壁键粒度，减少竞争

- 调整 Redisson 连接池大小

### 7.4 舱壁未生效

**症状**：超过并发上限但未拒绝请求

**排查步骤**：

1. 检查配置：`apex.bulkhead.enabled=true`
2. 检查 License：确保 Enterprise License 有效
3. 检查 Redisson：Redis 连接是否正常
4. 检查日志：是否有 "获取分布式信号量成功" 日志

## 8. 与其他特性对比

| 特性       | 分布式舱壁                  | 熔断器     | 限流器     |
| -------- | ---------------------- | ------- | ------- |
| **目标**   | 并发控制                   | 故障隔离    | 流量控制    |
| **维度**   | 并发数                    | 错误率/慢调用 | QPS/QPM |
| **作用范围** | 全局（跨实例）                | 单实例     | 单实例/全局  |
| **失败策略** | 快速拒绝                   | 降级响应    | 排队/拒绝   |
| **适用场景** | 下游保护                   | 快速失败    | 流量削峰    |
| **联动使用** | ✅ 建议组合使用（舱壁 + 熔断 + 限流） | <br />  | <br />  |

## 9. 故障演练

定期进行故障演练，验证舱壁策略有效性：

```bash
# 1. 压测场景：模拟并发超限
ab -n 1000 -c 50 http://localhost:8080/api/v1/payment/process

# 2. 观察拒绝指标
curl http://localhost:8080/actuator/prometheus | grep apex_bulkhead_rejected_total

# 3. 查看可用许可证
redis-cli GET bulkhead:semaphore:payment-gateway

# 4. 模拟下游故障
# 人为延长下游响应时间，观察许可证是否被正常释放
```

## 10. 未来扩展方向

- **自适应舱壁**：根据下游负载自动调整并发上限

- **优先级队列**：高优先级请求优先获取许可证

- **智能降级**：舱壁超限时自动切换到降级逻辑

- **分布式限流**：结合 Sentinel 实现 QPS 级别的分布式限流

***

**License**: Enterprise Only\
**Version**: 1.3.0\
**Support**: [Apex Scaffold 官方文档](https://apex-scaffold.com)

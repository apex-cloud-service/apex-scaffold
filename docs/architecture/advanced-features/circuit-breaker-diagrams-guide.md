# 熔断限流图表补充说明文档

**版本**: 1.0  
**更新日期**: 2026-09-12  
**作者**: Apex Team

---

## 图表查看方式

熔断限流完整技术指南中包含3张专业图表,您可以通过以下方式查看:

### 方式1: GitHub/GitLab查看 (推荐)

在GitHub或GitLab代码仓库中直接查看以下Mermaid源文件,会自动渲染为图表:

1. **[Sentinel熔断限流架构图](../../diagrams/sequence/82_sentinel_circuit_breaker_flow.mmd)**
   - 展示Sentinel与Resilience4j双引擎协作流程
   - 包含流控规则、熔断器状态判断、降级处理

2. **[Resilience4j熔断器状态机](../../diagrams/sequence/83_resilience4j_state_machine.mmd)**
   - 展示CLOSED/OPEN/HALF_OPEN三种状态转换
   - 包含每种状态的触发条件和行为说明

3. **[热点参数限流时序图](../../diagrams/sequence/84_rate_limit_hotspot_sequence.mmd)**
   - 展示Sentinel热点参数限流完整流程
   - 包含双重保护机制(Sentinel + UserRateLimit)

### 方式2: VSCode预览

```bash
# 安装Markdown Preview Mermaid Support插件
code --install-extension bierner.markdown-mermaid

# 在VSCode中打开Markdown文件,按Cmd+Shift+V预览
```

### 方式3: 在线Mermaid编辑器

访问 [Mermaid Live Editor](https://mermaid.live/),复制对应的.mmd文件内容粘贴即可查看。

---

## 图表内容说明

### 1. Sentinel熔断限流架构图 (82)

**核心流程**:
```
客户端请求 
→ API网关 
→ Sentinel流量规则检查(QPS/热点/系统)
→ Resilience4j熔断器状态判断
→ [CLOSED]正常调用远程API / [OPEN]快速失败返回降级响应
```

**关键节点**:
- **流控规则**: QPS限流、热点参数限流、系统自适应保护
- **熔断器**: CLOSED/OPEN/HALF_OPEN三种状态
- **降级处理**: 返回默认值或缓存数据

### 2. Resilience4j熔断器状态机 (83)

**状态转换**:
```
[*] → CLOSED (初始状态)
CLOSED → OPEN (失败率>50% 或 慢调用率>50%)
OPEN → HALF_OPEN (等待60秒后自动尝试恢复)
HALF_OPEN → CLOSED (测试调用成功率达标)
HALF_OPEN → OPEN (测试调用失败率仍高)
```

**每种状态行为**:
- **CLOSED**: 正常处理请求,记录失败率,滑动窗口100次调用
- **OPEN**: 快速失败所有请求,返回CircuitBreakerOpenException,触发降级逻辑
- **HALF_OPEN**: 允许5次测试调用,评估系统是否恢复

### 3. 热点参数限流时序图 (84)

**双重保护机制**:

**Sentinel层** (热点参数级别):
```
POST /api/v1/sms/send?phone=13800138000
→ Sentinel热点参数识别 (参数索引0)
→ 检查该手机号是否超限 (10次/分钟)
→ 超限则拒绝,返回429
```

**UserRateLimit层** (用户账号级别):
```
→ @UserRateLimit注解拦截
→ Redis令牌桶算法 (key=user:rate:userId)
→ 尝试获取令牌 (容量20,速率10/s)
→ 无令牌则拒绝,返回429
```

**防护效果**:
- 防止单个手机号被刷 (Sentinel热点)
- 防止单个用户账号刷量 (UserRateLimit)
- 两层保护互不冲突,相互补充

---

## 配置示例

### Sentinel热点参数限流

```java
@PostMapping("/api/v1/sms/send")
@SentinelResource(value = "smsSend", blockHandler = "handleBlock")
public Mono<Result<Void>> sendSms(
    @RequestParam @ParamFlow(index = 0, count = 10, durationInSec = 60) 
    String phone
) {
    return smsService.send(phone)
        .then(Mono.just(Result.success()));
}

public Mono<Result<Void>> handleBlock(String phone, BlockException ex) {
    return Mono.just(Result.error(ResultCode.TOO_MANY_REQUESTS, 
        "该手机号请求过于频繁,请1分钟后重试"));
}
```

### Resilience4j熔断器

```yaml
resilience4j:
  circuitbreaker:
    instances:
      paymentService:
        failure-rate-threshold: 50
        slow-call-rate-threshold: 50
        slow-call-duration-threshold: 3s
        wait-duration-in-open-state: 60s
        sliding-window-size: 100
        minimum-number-of-calls: 10
        permitted-number-of-calls-in-half-open-state: 5
```

### UserRateLimit用户级限流

```java
@PostMapping("/api/v1/orders/create")
@UserRateLimit(capacity = 20, refillTokens = 10)
public Mono<Result<OrderDTO>> createOrder(@RequestBody CreateOrderRequest request) {
    return orderService.create(request)
        .map(Result::success);
}
```

---

## 监控指标

### Prometheus指标示例

```yaml
# Sentinel限流拒绝率
rate(sentinel_block_qps_total[5m]) / rate(sentinel_pass_qps_total[5m])

# 熔断器状态 (1=OPEN, 0=CLOSED)
resilience4j_circuitbreaker_state{name="paymentService",state="open"}

# 熔断器失败率
resilience4j_circuitbreaker_failure_rate{name="paymentService"}

# 慢调用率
resilience4j_circuitbreaker_slow_call_rate{name="paymentService"}
```

---

## 常见问题

### Q1: 为什么需要Sentinel和Resilience4j两个框架?

**A**: 两者互补使用:
- **Sentinel**: 擅长入口流控、热点参数限流、系统自适应保护
- **Resilience4j**: 擅长外部调用熔断、轻量级状态机、Spring Boot无缝集成

### Q2: 热点参数限流和用户级限流有什么区别?

**A**: 
- **热点参数限流**: 针对特定参数值(手机号/IP),防止恶意刷单个资源
- **用户级限流**: 针对用户账号,防止单个用户消耗过多资源

两者可以同时使用,形成多层防护。

### Q3: 熔断器打开后会一直拒绝请求吗?

**A**: 不会。熔断器打开后等待60秒(可配置),自动进入半开状态,允许少量测试调用。如果测试成功,则关闭熔断器恢复正常;如果测试失败,则重新打开。

---

## 相关文档

- [Sentinel熔断限流完整技术指南](circuit-breaker-rate-limit-guide.md)
- [自动重试策略](auto-retry-strategy.md)
- [服务治理方案](../service-governance-solution.md)
- [后端开发规范](../../04-development/development-standards.md)

---

**最后更新**: 2026-09-12  
**维护者**: Apex Team

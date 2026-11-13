> - [数据流一致性架构图](../diagrams/62_data_flow_consistency.svg)
# Apex Scaffold 数据流转架构文档

> 文档版本: v1.0
> 创建时间: 2026-09-04
> 适用版本: apex-scaffold v1.3.1+
> 数据流模式: 同步 + 异步 + 最终一致性

> **配套架构图**:
> - [数据流转架构图](../diagrams/62_data_flow_consistency.svg)
> - [RocketMQ消息队列架构图](../diagrams/61_message_queue_reliability.svg)
> - [业务流程文档](business-flows.md)

---

## 一、数据流概览

Apex Scaffold 系统中存在多种数据流模式，根据业务场景选择合适的数据流方式，平衡一致性、性能和可用性。

当前共享基础设施拆分为 persistence、cache、messaging、remote、integration 五个本地 JAR，旧 `apex-infrastructure` 仅为依赖五个能力 JAR 的兼容坐标；messaging 单向依赖 persistence，infrastructure-integration 单向依赖 remote，业务数据库和 Flyway 归属不变。第一阶段 Maven 结构为根聚合 7 个管理组、42 个后代、43 个 reactor 项目含根（8 个 POM 含根 + 35 个 JAR）；business 组为空，仅作为项目业务库管理入口，通用框架实现在其他组。所有既有叶子目录仍在仓库根，不改变生产依赖或数据流。聚合、parent 与生产 dependencies 是不同关系，分别见[七组聚合图](../diagrams/128_maven_module_groups.svg)、[parent 继承图](../diagrams/129_maven_parent_inheritance.svg)及[生产依赖图](../diagrams/95_module_dependency_graph.svg)。

当前运行拓扑仍为网关 + 六个业务服务 + 只读查询聚合 + 任务代理 / 可选脱敏；aggregation 仅通过 WebClient 调用 workflow 与 office 的内部只读接口，无 DB、迁移或事务，HTTP 调用不等于 Maven 业务库依赖。office 站内信先以同一 JDBC 事务写入消息与收件人，提交后发送本实例 WebSocket 消息 ID 提示；票据、连接与提示不跨实例共享，HTTP/WS 网关地址必须指向同一实例，客户端通过 HTTP 收件箱/未读数补偿，不应将其视为持久化可靠消息投递。实现边界见[拆分方案](../08-project-management/plans/infrastructure-modularization-plan.md)、[系统当前运行拓扑](system-architecture.md#12-当前运行拓扑系统架构图)与[站内信协议](../05-operations/in-app-messaging.md)。62 / 61 为既有专题图，不因 Maven 分组重绘，也不证明额外业务 JAR、服务或数据库已新增。

### 1.1 数据流分类

| 数据流类型 | 模式 | 一致性 | 延迟 | 适用场景 |
|-----------|------|--------|------|---------|
| **用户请求流** | 同步 | 强一致 | < 200ms | 查询操作、实时业务 |
| **写操作流** | 同步事务 | 强一致 | < 500ms | 订单创建、支付扣款 |
| **异步消息流** | 异步 | 最终一致 | < 5s | 邮件发送、数据同步 |
| **文件上传流** | 异步 + 批处理 | 最终一致 | 视文件大小 | 大文件上传、媒体处理 |
| **外部服务调用** | 同步 + 重试 | 弱一致 | < 10s | 第三方API、微信支付 |

---

## 二、Flow 1: 用户请求数据流 (同步)

### 2.1 流程概述

**目标**: 为用户提供实时响应的查询服务

**流程步骤**:
1. 客户端发起HTTP请求
2. 网关层路由+限流
3. 控制器参数验证
4. 服务层业务逻辑
5. 缓存查询 (Cache Hit/Miss)
6. 数据库查询 (Miss时)
7. 响应封装返回

### 2.2 缓存策略 (Cache-Aside Pattern)

**读流程**:
```
1. 应用先查询缓存 (Redis)
2. Cache Hit → 直接返回数据
3. Cache Miss → 查询数据库
4. 将查询结果写入缓存
5. 返回数据给客户端
```

**写流程**:
```
1. 先写数据库 (确保持久化)
2. 数据库写入成功后删除缓存
3. 返回成功响应
4. 下次读取时重新加载缓存 (懒加载)
```

### 2.3 性能指标

| 指标 | 目标值 | 实际值 | 说明 |
|------|-------|-------|------|
| **缓存命中延迟** | P95 < 50ms | P95 = 45ms | 从Redis读取 |
| **缓存未命中延迟** | P95 < 200ms | P95 = 180ms | 从MySQL查询 |
| **缓存命中率** | > 80% | 85% | 热点数据缓存 |
| **并发QPS** | > 5000 | 5200 | 单实例 |

### 2.4 代码示例

```java
@Service
public class UserQueryService {

    @Autowired
    private RedisTemplate<String, User> redisTemplate;

    @Autowired
    private UserRepository userRepository;

    public Mono<User> getUserById(Long userId) {
        String cacheKey = "user:" + userId;

        // 1. 先查缓存
        return Mono.fromCallable(() -> redisTemplate.opsForValue().get(cacheKey))
            .flatMap(cachedUser -> {
                if (cachedUser != null) {
                    // Cache Hit
                    return Mono.just(cachedUser);
                }

                // 2. Cache Miss，查询数据库
                return userRepository.findById(userId)
                    .doOnNext(dbUser -> {
                        // 3. 写入缓存
                        redisTemplate.opsForValue().set(cacheKey, dbUser,
                            Duration.ofHours(1));
                    });
            });
    }
}
```

---

## 三、Flow 2: 写操作数据流 (事务)

### 3.1 流程概述

**目标**: 保证数据的原子性和一致性

**流程步骤**:
1. 接收写请求 (POST/PUT/DELETE)
2. 数据校验 (参数合法性、业务规则)
3. 开启数据库事务 (@Transactional)
4. 写入MySQL主库
5. 删除缓存 (保持一致性)
6. 事务提交
7. 主从复制 (异步)

### 3.2 主从复制机制

**复制模式**: 半同步复制 (Semi-Sync Replication)

**工作流程**:
```
1. Master收到写请求
2. Master写入Binlog
3. Master将Binlog发送给Slave
4. 至少1个Slave确认接收 (ACK)
5. Master返回客户端成功
6. Slave异步应用Binlog
```

**复制延迟**:
- 正常情况: < 1s
- 网络抖动: < 5s
- 大事务回放: < 30s

**读写分离策略**:
```java
// 写操作 → 主库
@Transactional
public Mono<User> createUser(User user) {
    return userRepository.save(user); // 路由到Master
}

// 读操作 → 从库
@Transactional(readOnly = true)
public Mono<User> getUserById(Long userId) {
    return userRepository.findById(userId); // 路由到Slave
}
```

### 3.3 延时双删策略 (防止脏读)

**问题场景**:
```
时间线:
T1: 请求A写数据库
T2: 请求B查缓存 (Miss)
T3: 请求A删缓存
T4: 请求B查数据库 (旧数据)
T5: 请求B写缓存 (脏数据)
```

**解决方案: 延时双删**:
```java
public Mono<Void> updateUser(User user) {
    // 1. 先删缓存（L2→L1）
    return Mono.just(user)
        .then(userRepository.save(user)) // 2. 写数据库
        .then(cacheConsistencyService
            // 3. 延迟双删：立即删除 + 延迟再删（异步，不阻塞响应）
            .evictWithDelayedDoubleDelete("user:id", user.getId().toString()).then());
}
```

> 注: `evictWithDelayedDoubleDelete` 内部执行「立即删除两级缓存 → 延迟 `delayMillis`（可配置，默认 500ms）→ 异步二次删除」,并带失败重试(`retry-times`),失败时记录日志交由补偿机制兜底。

---

## 四、Flow 3: 异步消息数据流

### 4.1 流程概述

**目标**: 解耦业务模块，提升系统吞吐量

**流程步骤**:
1. 生产者发送消息到RocketMQ
2. Broker持久化消息
3. 消费者异步拉取消息
4. 消费者业务处理
5. 持久化到数据库

### 4.2 应用场景

#### 场景1: 用户注册后发送欢迎邮件

**同步方式的问题**:
- 注册接口响应慢 (等待邮件发送)
- 邮件服务故障影响注册

**异步方式**:
```java
// 1. 注册成功后发送消息
@Transactional
public Mono<User> registerUser(UserRegisterDTO dto) {
    return Mono.fromCallable(() -> {
        // 保存用户
        User user = userRepository.save(dto.toEntity()).block();

        // 发送异步消息
        UserRegisterEvent event = new UserRegisterEvent(user.getId(), user.getEmail());
        rocketMQTemplate.asyncSend("user-register-topic", event,
            new SendCallback() {
                @Override
                public void onSuccess(SendResult result) {
                    log.info("消息发送成功: {}", result.getMsgId());
                }

                @Override
                public void onException(Throwable throwable) {
                    log.error("消息发送失败", throwable);
                    // 记录到失败表，定时任务重试
                }
            });

        return user;
    });
}

// 2. 消费者处理
@RocketMQMessageListener(
    topic = "user-register-topic",
    consumerGroup = "email-consumer-group"
)
public class UserRegisterConsumer implements RocketMQListener<UserRegisterEvent> {

    @Override
    public void onMessage(UserRegisterEvent event) {
        try {
            // 发送欢迎邮件
            emailService.sendWelcomeEmail(event.getEmail());
        } catch (Exception e) {
            log.error("发送邮件失败", e);
            // 抛出异常触发重试
            throw new RuntimeException(e);
        }
    }
}
```

#### 场景2: 订单创建后库存扣减

**事务消息保证**:
```java
@Service
public class OrderService {

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    public Mono<Order> createOrder(OrderCreateDTO dto) {
        return Mono.fromCallable(() -> {
            // 发送事务消息
            TransactionSendResult result = rocketMQTemplate.sendMessageInTransaction(
                "order-topic",
                MessageBuilder.withPayload(dto).build(),
                dto // arg参数传递给回调
            );

            if (result.getLocalTransactionState() == LocalTransactionState.COMMIT_MESSAGE) {
                return order;
            } else {
                throw new BusinessException("订单创建失败");
            }
        });
    }

    // 本地事务回调
    @RocketMQTransactionListener
    public class OrderTransactionListener implements RocketMQLocalTransactionListener {

        @Override
        public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
            try {
                OrderCreateDTO dto = (OrderCreateDTO) arg;

                // 执行本地事务：创建订单
                Order order = orderRepository.save(dto.toEntity()).block();

                return RocketMQLocalTransactionState.COMMIT;
            } catch (Exception e) {
                log.error("订单创建失败", e);
                return RocketMQLocalTransactionState.ROLLBACK;
            }
        }

        @Override
        public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
            // 回查本地事务状态
            String orderId = msg.getHeaders().get("orderId", String.class);
            boolean exists = orderRepository.existsById(orderId).block();
            return exists ? RocketMQLocalTransactionState.COMMIT
                          : RocketMQLocalTransactionState.ROLLBACK;
        }
    }
}
```

### 4.3 消息重试与死信队列

**重试策略**:
```
重试次数: 16次
重试间隔:
  1次: 10s
  2次: 30s
  3次: 1min
  4次: 2min
  5次: 3min
  6次: 4min
  7次: 5min
  8次: 6min
  9次: 7min
  10次: 8min
  11次: 9min
  12次: 10min
  13次: 20min
  14次: 30min
  15次: 1h
  16次: 2h
```

**死信队列处理**:
```java
@RocketMQMessageListener(
    topic = "%DLQ%user-register-topic",  // 死信队列
    consumerGroup = "dlq-consumer-group"
)
public class DLQConsumer implements RocketMQListener<UserRegisterEvent> {

    @Override
    public void onMessage(UserRegisterEvent event) {
        // 记录到数据库，人工介入处理
        failedMessageRepository.save(new FailedMessage(
            "user-register-topic",
            JSON.toJSONString(event),
            "重试16次后仍失败"
        ));

        // 发送告警
        alertService.sendAlert("死信队列消息", event);
    }
}
```

---

## 五、Flow 4: 文件上传数据流

### 5.1 大文件分片上传

**流程概述**:
1. 前端计算文件MD5 (秒传检测)
2. 文件分片 (2MB/片)
3. 并发上传分片
4. 服务端合并分片
5. 保存文件元数据

**分片上传实现**:
```javascript
// 前端分片上传
async function uploadLargeFile(file) {
  const chunkSize = 2 * 1024 * 1024; // 2MB
  const chunks = Math.ceil(file.size / chunkSize);
  const fileHash = await calculateMD5(file);

  // 1. 检查是否已上传 (秒传)
  const { data: existFile } = await axios.get(`/api/files/check?hash=${fileHash}`);
  if (existFile) {
    return existFile; // 秒传成功
  }

  // 2. 初始化分片上传
  const { data: uploadId } = await axios.post('/api/files/init', {
    fileName: file.name,
    fileSize: file.size,
    fileHash: fileHash,
    chunks: chunks
  });

  // 3. 并发上传分片
  const uploadPromises = [];
  for (let i = 0; i < chunks; i++) {
    const start = i * chunkSize;
    const end = Math.min(start + chunkSize, file.size);
    const chunk = file.slice(start, end);

    uploadPromises.push(
      axios.post(`/api/files/upload-chunk`, chunk, {
        headers: {
          'Content-Type': 'application/octet-stream',
          'X-Upload-Id': uploadId,
          'X-Chunk-Index': i,
          'X-Total-Chunks': chunks
        }
      })
    );
  }

  await Promise.all(uploadPromises);

  // 4. 通知服务端合并分片
  const { data: result } = await axios.post('/api/files/merge', {
    uploadId: uploadId
  });

  return result;
}
```

**服务端合并分片**:
```java
@Service
public class FileUploadService {

    public Mono<FileInfo> mergeChunks(String uploadId) {
        return Mono.fromCallable(() -> {
            // 1. 获取所有分片路径
            String chunkDir = "/tmp/upload/" + uploadId;
            File[] chunkFiles = new File(chunkDir).listFiles();
            Arrays.sort(chunkFiles, Comparator.comparing(File::getName));

            // 2. 合并分片
            String targetPath = "/upload/files/" + uploadId + ".dat";
            try (FileOutputStream fos = new FileOutputStream(targetPath)) {
                for (File chunkFile : chunkFiles) {
                    Files.copy(chunkFile.toPath(), fos);
                }
            }

            // 3. 上传到对象存储
            String objectKey = minioService.uploadFile(new File(targetPath));

            // 4. 保存元数据
            FileInfo fileInfo = fileInfoRepository.save(new FileInfo(
                uploadId,
                originalName,
                fileSize,
                objectKey
            )).block();

            // 5. 清理临时文件
            FileUtils.deleteDirectory(new File(chunkDir));
            new File(targetPath).delete();

            return fileInfo;
        });
    }
}
```

### 5.2 断点续传

**已上传分片记录**:
```java
// Redis记录已上传分片
public void recordUploadedChunk(String uploadId, int chunkIndex) {
    String key = "upload:chunks:" + uploadId;
    redisTemplate.opsForSet().add(key, String.valueOf(chunkIndex));
    redisTemplate.expire(key, Duration.ofDays(1));
}

// 查询已上传分片
public Set<Integer> getUploadedChunks(String uploadId) {
    String key = "upload:chunks:" + uploadId;
    Set<String> chunks = redisTemplate.opsForSet().members(key);
    return chunks.stream()
        .map(Integer::parseInt)
        .collect(Collectors.toSet());
}
```

**前端续传逻辑**:
```javascript
async function resumeUpload(uploadId, file) {
  // 1. 获取已上传分片
  const { data: uploadedChunks } = await axios.get(`/api/files/uploaded-chunks?uploadId=${uploadId}`);

  // 2. 跳过已上传分片，继续上传剩余分片
  const chunkSize = 2 * 1024 * 1024;
  const chunks = Math.ceil(file.size / chunkSize);

  for (let i = 0; i < chunks; i++) {
    if (uploadedChunks.includes(i)) {
      continue; // 跳过已上传
    }

    const start = i * chunkSize;
    const end = Math.min(start + chunkSize, file.size);
    const chunk = file.slice(start, end);

    await uploadChunk(uploadId, i, chunk);
  }

  // 3. 合并分片
  await axios.post('/api/files/merge', { uploadId });
}
```

---

## 六、Flow 5: 外部服务调用数据流

### 6.1 重试策略

**指数退避重试**:
```java
@Service
public class ExternalApiService {

    @Autowired
    private WebClient webClient;

    public Mono<ExternalResponse> callExternalApi(ExternalRequest request) {
        return webClient.post()
            .uri("/external-api")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(ExternalResponse.class)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                .maxBackoff(Duration.ofSeconds(10))
                .filter(throwable -> throwable instanceof WebClientResponseException)
            )
            .timeout(Duration.ofSeconds(5))
            .onErrorResume(throwable -> {
                log.error("外部API调用失败", throwable);
                // 降级处理：返回兜底数据
                return Mono.just(ExternalResponse.fallback());
            });
    }
}
```

**重试配置**:
```yaml
resilience4j:
  retry:
    instances:
      external-api:
        max-attempts: 3
        wait-duration: 1s
        exponential-backoff-multiplier: 2
        retry-exceptions:
          - java.net.SocketTimeoutException
          - java.net.ConnectException
```

### 6.2 熔断降级

**Sentinel限流 + Resilience4j熔断配置**:
```java
@SentinelResource(
    value = "external-api-call",
    fallback = "callExternalApiFallback",
    blockHandler = "callExternalApiBlocked"
)
public ExternalResponse callExternalApi(ExternalRequest request) {
    // 调用外部API
    return externalApiClient.call(request);
}

// 降级方法 (业务异常)
public ExternalResponse callExternalApiFallback(ExternalRequest request, Throwable ex) {
    log.warn("外部API调用降级: {}", ex.getMessage());
    return ExternalResponse.fallback();
}

// 熔断方法 (限流/熔断)
public ExternalResponse callExternalApiBlocked(ExternalRequest request, BlockException ex) {
    log.warn("外部API被熔断: {}", ex.getClass().getSimpleName());
    return ExternalResponse.blocked();
}
```

**熔断配置**:
```java
// 慢调用比例熔断
FlowRuleManager.loadRules(Arrays.asList(
    new DegradeRule("external-api-call")
        .setGrade(CircuitBreakerStrategy.SLOW_REQUEST_RATIO.getType())
        .setCount(0.5) // 慢调用比例阈值 50%
        .setTimeWindow(10) // 熔断时长 10s
        .setMinRequestAmount(10) // 最小请求数
        .setSlowRatioThreshold(1.0) // RT阈值 1s
));
```

---

## 七、数据一致性保障

### 7.1 强一致性 (同步)

**适用场景**: 订单创建、支付扣款、库存扣减

**实现方式**:
- 数据库事务 (ACID保证)
- 分布式事务 (Seata，支持 AT/TCC/SAGA/XA 多模式，默认 AT。详见 [Seata 接入](../05-operations/seata-integration.md))

**示例**:
```java
@GlobalTransactional(name = "create-order", rollbackFor = Exception.class)
public Order createOrder(OrderDTO dto) {
    // 1. 创建订单
    Order order = orderRepository.save(dto.toEntity()).block();

    // 2. 扣减库存 (跨库操作)
    inventoryService.deductStock(dto.getProductId(), dto.getQuantity());

    // 3. 扣减账户余额 (跨库操作)
    accountService.deductBalance(dto.getUserId(), dto.getAmount());

    return order;
}
```

### 7.2 最终一致性 (异步)

**适用场景**: 邮件发送、数据同步、统计计算

**实现方式**:
- 消息队列 (事务消息)
- 补偿机制 (定时任务兜底)

**示例**:
```java
// 1. 主流程：订单创建 + 发送消息
@Transactional
public Order createOrder(OrderDTO dto) {
    Order order = orderRepository.save(dto.toEntity()).block();

    // 发送异步消息
    OrderCreatedEvent event = new OrderCreatedEvent(order.getId());
    rocketMQTemplate.asyncSend("order-created-topic", event);

    return order;
}

// 2. 消费者：发送通知
@RocketMQMessageListener(topic = "order-created-topic")
public class OrderNotificationConsumer implements RocketMQListener<OrderCreatedEvent> {
    @Override
    public void onMessage(OrderCreatedEvent event) {
        // 发送邮件、短信、推送通知
        notificationService.sendOrderNotification(event.getOrderId());
    }
}

// 3. 补偿任务：兜底重试
@Scheduled(cron = "0 */10 * * * ?") // 每10分钟
public void retryFailedNotifications() {
    List<Order> ordersWithoutNotification = orderRepository
        .findOrdersWithoutNotification()
        .collectList().block();

    for (Order order : ordersWithoutNotification) {
        notificationService.sendOrderNotification(order.getId());
    }
}
```

### 7.3 缓存一致性

**Cache-Aside模式**:
- 先更新数据库，再删除缓存
- 延时双删防止脏读

**订阅Binlog模式** (可选):
```java
// Canal订阅MySQL Binlog
@CanalListener(destination = "example", schema = "apex_db", table = "sys_user")
public class UserBinlogListener implements EntryHandler<User> {

    @Override
    public void insert(User user) {
        // 插入时不处理 (懒加载)
    }

    @Override
    public void update(User before, User after) {
        // 更新时删除缓存
        String cacheKey = "user:" + after.getId();
        redisTemplate.delete(cacheKey);
    }

    @Override
    public void delete(User user) {
        // 删除时删除缓存
        String cacheKey = "user:" + user.getId();
        redisTemplate.delete(cacheKey);
    }
}
```

---

## 八、性能优化建议

### 8.1 数据库优化

- ✅ 索引优化: 为常用查询字段添加索引
- ✅ 读写分离: 写主库，读从库 (注意复制延迟)
- ✅ 连接池配置: Hikari最大连接数=((核心数*2)+磁盘数)
- ✅ 慢查询监控: 记录执行时间>1s的SQL

### 8.2 缓存优化

- ✅ 缓存预热: 系统启动时加载热点数据
- ✅ 缓存穿透: 布隆过滤器 + 空值缓存
- ✅ 缓存击穿: 互斥锁 (setnx) 防止并发查询
- ✅ 缓存雪崩: 随机过期时间 (TTL + 随机值)

### 8.3 消息队列优化

- ✅ 批量发送: 攒批发送，减少网络开销
- ✅ 消息过滤: Tag过滤，减少消费者网络流量
- ✅ 消费幂等: 根据MessageId去重
- ✅ 消费并发: 设置合理的consumeThreadMax

---

## 九、监控与告警

### 9.1 关键指标

| 指标 | 阈值 | 告警级别 |
|------|------|---------|
| API延迟 P95 | > 500ms | P2 |
| API错误率 | > 1% | P1 |
| 缓存命中率 | < 70% | P3 |
| 数据库慢查询 | > 100次/小时 | P2 |
| MQ消息堆积 | > 10万 | P2 |
| MQ消费延迟 | > 1分钟 | P1 |

### 9.2 链路追踪

**TraceID传播**:
- HTTP请求: Header `X-B3-TraceId`
- 消息队列: Message Property `TraceId`
- 数据库: SQL注释 `/* TraceId: xxx */`

---

## 十、参考资料

### 10.1 内部文档

- [RocketMQ消息队列架构文档](mq-architecture.md)
- [业务流程文档](business-flows.md)
- [多级缓存架构文档](advanced-features/multi-level-cache-architecture.md)

### 10.2 外部参考

- [RocketMQ最佳实践](https://rocketmq.apache.org/docs/bestPractice/01bestpractice/)
- [Redis缓存设计模式](https://redis.io/docs/manual/patterns/)
- [分布式事务Seata](https://seata.io/zh-cn/docs/overview/what-is-seata.html)

---

**文档维护者**: 架构团队
**审核状态**: 待人工审核
**下次更新**: 数据流架构变更后同步更新

> - [消息队列可靠性架构图](../diagrams/61_message_queue_reliability.svg)
# Apex Scaffold RocketMQ 消息队列架构文档

> 文档版本: v1.0
> 创建时间: 2026-09-04
> 适用版本: apex-scaffold v2.5.0+
> RocketMQ版本: 5.x

> **配套架构图**:
> - [RocketMQ消息队列架构图](../diagrams/61_message_queue_reliability.svg)
> - [数据流转架构图](../diagrams/62_data_flow_consistency.svg)

---

## 一、RocketMQ 架构概览

### 1.1 核心组件

```
生产者集群 (Producer)
    ↓ 发送消息
NameServer集群 (路由注册中心)
    ↓ 路由信息
Broker集群 (消息存储)
    ├─ Master (读写)
    └─ Slave (只读)
    ↓ 消息推送/拉取
消费者集群 (Consumer)
```

### 1.2 组件说明

| 组件 | 角色 | 部署数量 | 说明 |
|------|------|---------|------|
| **NameServer** | 路由注册中心 | 3节点 | 无状态，节点间不通信 |
| **Broker Master** | 消息存储主节点 | 2主 | 支持读写操作 |
| **Broker Slave** | 消息存储从节点 | 2从 | 仅支持读操作 |
| **Producer** | 消息生产者 | N个实例 | 应用内嵌 |
| **Consumer** | 消息消费者 | N个实例 | 应用内嵌 |

---

## 二、NameServer 集群

### 2.1 功能职责

**核心职责**:
1. 接收Broker的注册请求，维护路由信息
2. 接收Producer/Consumer的路由查询请求
3. 定期检测Broker存活状态 (心跳机制)

**路由信息**:
- Topic → Broker 映射关系
- Broker地址列表
- Queue分配信息

### 2.2 无状态设计

**特点**:
- ✅ NameServer节点之间不通信
- ✅ 每个节点独立维护路由信息
- ✅ 客户端可连接任意NameServer节点

**心跳机制**:
```
Broker → NameServer: 每30s发送心跳
NameServer: 120s未收到心跳则判定Broker下线
Producer/Consumer: 30s从NameServer拉取最新路由
```

### 2.3 部署配置

**application.properties**:
```properties
# NameServer地址 (多个地址用分号分隔)
rocketmq.name-server=192.168.1.10:9876;192.168.1.11:9876;192.168.1.12:9876

# 客户端配置
rocketmq.producer.send-message-timeout=3000
rocketmq.consumer.consume-timeout=15
```

**启动命令**:
```bash
# NameServer-1
nohup sh bin/mqnamesrv &

# NameServer-2
nohup sh bin/mqnamesrv &

# NameServer-3
nohup sh bin/mqnamesrv &
```

---

## 三、Broker 集群

### 3.1 主从架构

**部署拓扑**:
```
Broker-Group-1:
  ├─ broker-a (Master) - 192.168.1.20:10911
  └─ broker-a-s (Slave) - 192.168.1.21:10911

Broker-Group-2:
  ├─ broker-b (Master) - 192.168.1.22:10911
  └─ broker-b-s (Slave) - 192.168.1.23:10911
```

**读写分离**:
- **Master**: 支持读写，负责消息写入和部分读取
- **Slave**: 仅支持读，分担Master读压力

### 3.2 消息存储

**存储结构**:
```
/root/store/
├── commitlog/           # 消息存储文件 (顺序写)
│   ├── 00000000000000000000
│   ├── 00000000001073741824
│   └── ...
├── consumequeue/        # 消息索引 (按Topic)
│   ├── TopicA/
│   │   ├── 0/          # Queue 0
│   │   ├── 1/          # Queue 1
│   │   └── ...
│   └── TopicB/
├── index/              # 消息索引 (按Key)
└── config/             # 配置文件
```

**存储特性**:
- **CommitLog**: 所有消息顺序写入，1GB/文件
- **ConsumeQueue**: 按Topic/Queue组织的索引，8字节offset + 4字节size + 8字节tag
- **零拷贝**: mmap + sendfile，减少内存拷贝
- **页缓存**: 利用OS页缓存，提升读性能

### 3.3 同步复制 vs 异步复制

**同步复制 (SYNC_MASTER)**:
```
1. Master写入CommitLog
2. Master等待Slave ACK
3. Slave写入成功后返回ACK
4. Master返回客户端成功
```
- ✅ 优点: 数据不丢失
- ❌ 缺点: 性能略低 (RT增加10-20ms)

**异步复制 (ASYNC_MASTER)**:
```
1. Master写入CommitLog
2. Master立即返回客户端成功
3. Master异步推送数据给Slave
```
- ✅ 优点: 性能高
- ❌ 缺点: Master宕机可能丢失少量消息

**推荐配置**: 同步复制 (生产环境)

### 3.4 Broker 配置

**broker.conf** (Master):
```properties
# Broker基本信息
brokerClusterName=DefaultCluster
brokerName=broker-a
brokerId=0
namesrvAddr=192.168.1.10:9876;192.168.1.11:9876;192.168.1.12:9876

# 角色配置
brokerRole=SYNC_MASTER
flushDiskType=ASYNC_FLUSH

# 存储配置
storePathRootDir=/root/store
storePathCommitLog=/root/store/commitlog
mapedFileSizeCommitLog=1073741824

# 性能配置
sendMessageThreadPoolNums=16
useReentrantLockWhenPutMessage=true

# 删除文件配置
fileReservedTime=72
deleteWhen=04
```

**broker-slave.conf** (Slave):
```properties
brokerClusterName=DefaultCluster
brokerName=broker-a
brokerId=1
namesrvAddr=192.168.1.10:9876;192.168.1.11:9876;192.168.1.12:9876

brokerRole=SLAVE
flushDiskType=ASYNC_FLUSH
```

**启动命令**:
```bash
# Master
nohup sh bin/mqbroker -c conf/broker.conf &

# Slave
nohup sh bin/mqbroker -c conf/broker-slave.conf &
```

---

## 四、Producer 生产者

### 4.1 发送方式

#### 同步发送 (Sync)
```java
@Service
public class OrderProducerService {

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    public void sendOrderMessage(OrderDTO order) {
        SendResult result = rocketMQTemplate.syncSend(
            "order-topic",
            MessageBuilder.withPayload(order).build()
        );

        if (result.getSendStatus() == SendStatus.SEND_OK) {
            log.info("消息发送成功: MsgId={}", result.getMsgId());
        } else {
            throw new BusinessException("消息发送失败");
        }
    }
}
```
- ✅ 适用场景: 重要消息 (订单、支付)
- ✅ 特点: 可靠性高，等待响应

#### 异步发送 (Async)
```java
public void sendAsyncMessage(UserDTO user) {
    rocketMQTemplate.asyncSend(
        "user-register-topic",
        user,
        new SendCallback() {
            @Override
            public void onSuccess(SendResult result) {
                log.info("消息发送成功: {}", result.getMsgId());
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("消息发送失败", throwable);
                // 记录失败日志，定时重试
            }
        }
    );
}
```
- ✅ 适用场景: 普通消息 (通知、日志)
- ✅ 特点: 高吞吐，不阻塞主流程

#### 单向发送 (Oneway)
```java
public void sendOnewayMessage(LogDTO log) {
    rocketMQTemplate.sendOneWay("log-topic", log);
}
```
- ✅ 适用场景: 日志收集、埋点上报
- ✅ 特点: 最高性能，不关心结果

### 4.2 顺序消息

**全局顺序** (不推荐):
```java
// 所有消息发送到同一个Queue
rocketMQTemplate.syncSendOrderly(
    "order-topic",
    message,
    "global" // hashKey
);
```

**分区顺序** (推荐):
```java
// 按订单ID哈希，同一订单的消息发送到同一Queue
rocketMQTemplate.syncSendOrderly(
    "order-topic",
    message,
    order.getOrderId().toString() // hashKey
);
```

### 4.3 事务消息

**场景**: 本地事务与消息发送保持一致性

**流程**:
```
1. Producer发送半消息 (Half Message)
2. Broker存储半消息，不投递
3. Producer执行本地事务
4. 本地事务成功 → Commit消息
5. 本地事务失败 → Rollback消息
6. Broker回查事务状态 (超时未响应时)
```

**代码实现**:
```java
@Service
public class OrderTransactionService {

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    public void createOrderWithTransaction(OrderDTO dto) {
        TransactionSendResult result = rocketMQTemplate.sendMessageInTransaction(
            "order-topic",
            MessageBuilder.withPayload(dto).build(),
            dto // arg参数传递给回调
        );

        log.info("事务消息发送结果: {}", result.getLocalTransactionState());
    }
}

@RocketMQTransactionListener
@Component
public class OrderTransactionListener implements RocketMQLocalTransactionListener {

    @Autowired
    private OrderService orderService;

    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        try {
            OrderDTO dto = (OrderDTO) arg;

            // 执行本地事务
            Order order = orderService.createOrder(dto);

            log.info("本地事务执行成功: orderId={}", order.getId());
            return RocketMQLocalTransactionState.COMMIT;
        } catch (Exception e) {
            log.error("本地事务执行失败", e);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }

    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        // 回查本地事务状态
        String orderId = msg.getHeaders().get("orderId", String.class);
        boolean exists = orderService.existsById(orderId);

        return exists ? RocketMQLocalTransactionState.COMMIT
                      : RocketMQLocalTransactionState.ROLLBACK;
    }
}
```

### 4.4 延时消息

**延时等级**:
```
Level 1: 1s
Level 2: 5s
Level 3: 10s
Level 4: 30s
Level 5: 1m
Level 6: 2m
Level 7: 3m
Level 8: 4m
Level 9: 5m
Level 10: 6m
Level 11: 7m
Level 12: 8m
Level 13: 9m
Level 14: 10m
Level 15: 20m
Level 16: 30m
Level 17: 1h
Level 18: 2h
```

**使用示例**:
```java
// 延时5分钟后发送
Message<OrderDTO> message = MessageBuilder
    .withPayload(order)
    .setHeader(MessageConst.PROPERTY_DELAY_TIME_LEVEL, 9) // Level 9 = 5分钟
    .build();

rocketMQTemplate.syncSend("order-timeout-topic", message);
```

---

## 五、Consumer 消费者

### 5.1 消费模式

#### 集群消费 (Clustering)
```java
@RocketMQMessageListener(
    topic = "order-topic",
    consumerGroup = "order-consumer-group",
    messageModel = MessageModel.CLUSTERING // 默认
)
public class OrderConsumer implements RocketMQListener<OrderDTO> {

    @Override
    public void onMessage(OrderDTO order) {
        log.info("接收到订单消息: {}", order.getOrderId());
        // 业务处理
    }
}
```
- ✅ 同一ConsumerGroup内的消费者负载均衡消费
- ✅ 每条消息只被一个消费者消费
- ✅ 消费进度存储在Broker

#### 广播消费 (Broadcasting)
```java
@RocketMQMessageListener(
    topic = "config-update-topic",
    consumerGroup = "config-consumer-group",
    messageModel = MessageModel.BROADCASTING
)
public class ConfigUpdateConsumer implements RocketMQListener<ConfigDTO> {

    @Override
    public void onMessage(ConfigDTO config) {
        // 每个消费者都会收到消息
        localCache.refresh(config);
    }
}
```
- ✅ 每个消费者都接收全部消息
- ✅ 消费进度存储在本地

### 5.2 负载均衡策略

**平均分配策略** (默认):
```
假设: 4个Queue, 2个Consumer
结果:
  Consumer-1 消费 Queue 0, 1
  Consumer-2 消费 Queue 2, 3
```

**环形分配策略**:
```
假设: 4个Queue, 3个Consumer
结果:
  Consumer-1 消费 Queue 0, 3
  Consumer-2 消费 Queue 1
  Consumer-3 消费 Queue 2
```

### 5.3 消费模式

#### 顺序消费
```java
@RocketMQMessageListener(
    topic = "order-status-topic",
    consumerGroup = "order-status-group",
    consumeMode = ConsumeMode.ORDERLY
)
public class OrderStatusConsumer implements RocketMQListener<OrderStatusDTO> {

    @Override
    public void onMessage(OrderStatusDTO status) {
        // 保证同一订单的状态变更按顺序处理
        log.info("订单状态变更: {} -> {}", status.getOrderId(), status.getStatus());
    }
}
```

#### 并发消费
```java
@RocketMQMessageListener(
    topic = "email-topic",
    consumerGroup = "email-group",
    consumeMode = ConsumeMode.CONCURRENTLY, // 默认
    consumeThreadMax = 20
)
public class EmailConsumer implements RocketMQListener<EmailDTO> {

    @Override
    public void onMessage(EmailDTO email) {
        // 并发处理，无顺序保证
        emailService.send(email);
    }
}
```

### 5.4 消费重试与死信队列

**重试机制**:
```java
@RocketMQMessageListener(
    topic = "payment-topic",
    consumerGroup = "payment-group",
    maxReconsumeTimes = 16 // 最大重试次数
)
public class PaymentConsumer implements RocketMQListener<PaymentDTO> {

    @Override
    public void onMessage(PaymentDTO payment) {
        try {
            paymentService.process(payment);
        } catch (BusinessException e) {
            // 抛出异常触发重试
            throw e;
        }
    }
}
```

**死信队列处理**:
```java
@RocketMQMessageListener(
    topic = "%DLQ%payment-topic", // 死信队列
    consumerGroup = "dlq-consumer-group"
)
public class PaymentDLQConsumer implements RocketMQListener<PaymentDTO> {

    @Override
    public void onMessage(PaymentDTO payment) {
        // 记录到数据库，人工介入
        failedPaymentRepository.save(payment);

        // 发送告警
        alertService.sendAlert("支付消息进入死信队列", payment);
    }
}
```

### 5.5 消息过滤

**Tag过滤**:
```java
// 生产者设置Tag
Message<OrderDTO> message = MessageBuilder
    .withPayload(order)
    .setHeader(MessageConst.PROPERTY_TAGS, "PAID") // Tag
    .build();

rocketMQTemplate.syncSend("order-topic", message);

// 消费者订阅Tag
@RocketMQMessageListener(
    topic = "order-topic",
    selectorExpression = "PAID || SHIPPED" // 订阅PAID或SHIPPED
)
public class OrderConsumer implements RocketMQListener<OrderDTO> {
    // ...
}
```

**SQL92过滤**:
```java
// 消费者SQL过滤
@RocketMQMessageListener(
    topic = "order-topic",
    selectorType = SelectorType.SQL92,
    selectorExpression = "price > 1000 AND region = 'Beijing'"
)
public class HighValueOrderConsumer implements RocketMQListener<OrderDTO> {
    // ...
}
```

---

## 六、高可用与容灾

### 6.1 Master宕机

**Slave自动接管读流量**:
```
1. Master宕机
2. Producer/Consumer从NameServer获取最新路由
3. 读流量自动切换到Slave
4. 写流量路由到其他Broker Group
```

**DLedger自动主从切换** (推荐):
```properties
# 启用DLedger模式
enableDLegerCommitLog=true
dLegerGroup=broker-a-group
dLegerPeers=n0-192.168.1.20:40911;n1-192.168.1.21:40911;n2-192.168.1.22:40911
dLegerSelfId=n0
```

### 6.2 NameServer宕机

**客户端容错**:
- 客户端本地缓存路由信息
- 可继续向已知Broker发送消息
- 其他NameServer节点正常服务

### 6.3 消费失败处理

**重试策略**:
```
1次: 立即重试
2次: 10s后
3次: 30s后
4次: 1min后
5次: 2min后
...
16次: 2h后
```

**补偿机制**:
```java
// 定时任务扫描死信队列
@Scheduled(cron = "0 */10 * * * ?")
public void processDLQMessages() {
    List<MessageExt> messages = consumer.pullDLQMessages();

    for (MessageExt msg : messages) {
        // 人工审核后重新投递
        if (canRetry(msg)) {
            producer.send(msg);
        }
    }
}
```

---

## 七、性能优化

### 7.1 生产者优化

**批量发送**:
```java
List<Message> messages = orders.stream()
    .map(order -> new Message("order-topic", JSON.toJSONBytes(order)))
    .collect(Collectors.toList());

producer.send(messages); // 批量发送
```

**异步发送**:
```java
// 普通消息使用异步发送
producer.send(msg, new SendCallback() {
    @Override
    public void onSuccess(SendResult result) {
        // 成功回调
    }

    @Override
    public void onException(Throwable e) {
        // 失败回调
    }
});
```

### 7.2 消费者优化

**增加消费线程**:
```java
@RocketMQMessageListener(
    consumeThreadMin = 10,
    consumeThreadMax = 20
)
```

**批量消费**:
```java
@RocketMQMessageListener(
    consumeMessageBatchMaxSize = 10 // 每次最多消费10条
)
public class BatchConsumer implements RocketMQListener<List<OrderDTO>> {

    @Override
    public void onMessage(List<OrderDTO> orders) {
        // 批量处理
        orderService.batchProcess(orders);
    }
}
```

### 7.3 Broker优化

**PageCache预热**:
```properties
# 启动时预加载最近的CommitLog
warmMapedFileEnable=true
```

**异步刷盘**:
```properties
# 性能优先，允许少量消息丢失
flushDiskType=ASYNC_FLUSH
```

**Mmap优化**:
```properties
# 增大单个CommitLog文件大小
mapedFileSizeCommitLog=1073741824
```

---

## 八、监控与运维

### 8.1 关键指标

| 指标 | 说明 | 告警阈值 |
|------|------|---------|
| **消息堆积** | 未消费消息数 | > 10万 |
| **消费延迟** | 消息发送到消费的时间差 | > 1分钟 |
| **TPS** | 每秒处理消息数 | < 预期50% |
| **消费失败率** | 消费失败消息占比 | > 1% |

### 8.2 Dashboard监控

**RocketMQ Console**:
```bash
# 启动Console
podman run -d \
  --name rocketmq-console \
  -e "JAVA_OPTS=-Drocketmq.namesrv.addr=192.168.1.10:9876" \
  -p 8080:8080 \
  styletang/rocketmq-console-ng
```

**Prometheus监控**:
```yaml
# rocketmq-exporter
scrape_configs:
  - job_name: 'rocketmq'
    static_configs:
      - targets: ['192.168.1.20:5557']
```

### 8.3 常用命令

```bash
# 查看集群状态
./mqadmin clusterList -n 192.168.1.10:9876

# 查看Topic信息
./mqadmin topicStatus -n 192.168.1.10:9876 -t order-topic

# 查看消费进度
./mqadmin consumerProgress -n 192.168.1.10:9876 -g order-consumer-group

# 重置消费位点
./mqadmin resetOffsetByTime -n 192.168.1.10:9876 -g order-consumer-group -t order-topic -s 1620000000000
```

---

## 九、最佳实践

### 9.1 Topic设计

- ✅ 按业务域划分Topic (order-topic, user-topic)
- ✅ 避免Topic数量过多 (建议 < 100)
- ✅ Queue数量设置为消费者数量的整数倍
- ✅ 重要业务单独Topic，避免相互影响

### 9.2 消息设计

- ✅ 消息体尽量小 (< 4KB)
- ✅ 使用Tag分类消息
- ✅ 设置唯一的业务Key (去重)
- ✅ 消息体使用JSON格式 (可读性)

### 9.3 消费者设计

- ✅ 消费幂等: 根据MessageId或业务Key去重
- ✅ 消费快速失败: 超时立即返回，触发重试
- ✅ 日志记录: 记录MessageId、消费时间、处理结果
- ✅ 监控告警: 消费延迟、失败率告警

---

## 十、参考资料

### 10.1 内部文档

- [数据流转架构文档](data-flow-architecture.md)
- [业务流程文档](business-flows.md)

### 10.2 外部参考

- [RocketMQ官方文档](https://rocketmq.apache.org/docs/quick-start/)
- [RocketMQ最佳实践](https://rocketmq.apache.org/docs/bestPractice/01bestpractice/)
- [Spring Cloud Alibaba RocketMQ](https://github.com/alibaba/spring-cloud-alibaba/wiki/RocketMQ)

---

**文档维护者**: 架构团队
**审核状态**: 待人工审核
**下次更新**: MQ架构变更后同步更新

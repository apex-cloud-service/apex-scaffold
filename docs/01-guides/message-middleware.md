# 消息中间件使用说明

## 能力范围

基础设施模块提供 RocketMQ 的统一发送和消费封装：

- `MqProducerService#sendSync`：同步消息。
- `MqProducerService#sendAsync`：异步消息。
- `MqProducerService#sendOrderly`：按业务键路由的顺序消息。
- `MqProducerService#sendTransaction`：事务消息。
- `MqConsumerService#processMessage`：Redis 分布式幂等消费。

## 顺序消息

发送顺序消息时，必须为同一业务实体传入稳定且非空的 `hashKey`，例如订单号或用户 ID：

```java
mqProducerService.sendOrderly("order-topic", "status-changed", event, orderId.toString());
```

RocketMQ 会使用 `hashKey` 将消息选择到固定队列。消费端必须声明 `ConsumeMode.ORDERLY`，同一队列内的消息才会按投递顺序串行处理。顺序消息仍然可能重复投递，因此应继续使用 `MqConsumerService` 的幂等处理。

## 事务消息

事务消息需要业务方提供 `MqLocalTransactionHandler` Bean：

```java
@Component
public class OrderTransactionHandler implements MqLocalTransactionHandler {
    public RocketMQLocalTransactionState execute(Message<?> message, Object argument) {
        orderService.create((OrderCommand) argument);
        return RocketMQLocalTransactionState.COMMIT;
    }

    public RocketMQLocalTransactionState check(Message<?> message) {
        return orderService.isCreated(message) ? RocketMQLocalTransactionState.COMMIT
                : RocketMQLocalTransactionState.ROLLBACK;
    }
}
```

发送事务消息：

```java
mqProducerService.sendTransaction("order-topic", "created", event, command);
```

业务本地事务必须具备幂等性，`check` 应能够根据消息 ID 或业务键查询最终状态。未提供处理器时，框架返回 `UNKNOWN`，由 RocketMQ 按配置发起回查，不会默认提交业务事务。

## 配置

```yaml
rocketmq:
  name-server: ${ROCKETMQ_NAMESERVER:127.0.0.1:9876}
  producer:
    group: ${ROCKETMQ_PRODUCER_GROUP:apex-producer-group}
    send-message-timeout: ${ROCKETMQ_SEND_TIMEOUT:30000}
  consumer:
    enabled: ${ROCKETMQ_CONSUMER_ENABLED:true}

mq:
  consumer:
    idempotent-ttl-minutes: ${MQ_IDEMPOTENT_TTL_MINUTES:30}
```

本地使用 Compose 启动 RocketMQ：

```bash
docker compose --profile rocketmq up -d
```

生产环境至少注入 `ROCKETMQ_NAMESERVER`、`ROCKETMQ_PRODUCER_GROUP`，启用 ACL 时同时注入 `ROCKETMQ_ACCESS_KEY` 和 `ROCKETMQ_SECRET_KEY`。消息消费开关为 `APEX_MQ_CONSUMER_ENABLED`。

## 运维脚本

`./scripts/deploy/app.sh` 已支持以上 RocketMQ 环境变量。启动前可使用 `APEX_ENV_FILE` 加载配置：

```bash
APEX_ENV_FILE=.env ./scripts/deploy/app.sh start prod enterprise
```

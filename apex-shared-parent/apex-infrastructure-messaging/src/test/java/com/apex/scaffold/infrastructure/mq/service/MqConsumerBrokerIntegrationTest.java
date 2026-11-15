package com.apex.scaffold.infrastructure.mq.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.context.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.hook.ConsumeMessageContext;
import org.apache.rocketmq.client.hook.ConsumeMessageHook;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 真实 producer → broker → 消费回调 → 生产 MqConsumerService → PostgreSQL 的最小 infra 验收。
 *
 * <p>显式启用 APEX_REAL_MQ_INFRA_IT=true，并提供 APEX_MQ_NAMESRV、APEX_MQ_REDIS、
 * APEX_MQ_JDBC_URL、APEX_MQ_DB_USER；密码通过 APEX_MQ_DB_PASSWORD 提供（本地 trust 可省略）。
 * 数据库账号需 CREATE SCHEMA 权限。
 * 每轮独立 topic/group/schema/tenant，保留数据，不清空共享环境。
 * 不代表 system 业务 E2E，不覆盖 Spring 装配、业务 listener、Flyway、Outbox 或故障恢复。
 */
@EnabledIfEnvironmentVariable(named = "APEX_REAL_MQ_INFRA_IT", matches = "true")
class MqConsumerBrokerIntegrationTest {
    private static final String RUN = UUID.randomUUID().toString().replace("-", "");
    private static final String SCHEMA = "mq_infra_it_" + RUN;
    private static final String TOPIC = "apex_infra_it_" + RUN;
    private static final List<String> GROUPS = List.of("apex_infra_a_" + RUN, "apex_infra_b_" + RUN);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, AtomicInteger> ACKS = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> DUPLICATES = new ConcurrentHashMap<>();
    private static final AtomicReference<Throwable> CONSUMPTION_FAILURE = new AtomicReference<>();
    private static final List<DefaultMQPushConsumer> CONSUMERS = new java.util.ArrayList<>();
    private static HikariDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static RedissonClient redis;
    private static DefaultMQProducer producer;
    private static MqConsumerService service;

    @BeforeAll
    static void start() throws Exception {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(required("APEX_MQ_JDBC_URL"));
        dataSource.setUsername(required("APEX_MQ_DB_USER"));
        dataSource.setPassword(System.getenv().getOrDefault("APEX_MQ_DB_PASSWORD", ""));
        dataSource.setMaximumPoolSize(2);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE SCHEMA " + SCHEMA);
        // 不设 scope 唯一约束，避免数据库约束掩盖重复执行 handler 的缺陷。
        jdbc.execute("CREATE TABLE " + SCHEMA + ".message_effect ("
                + "id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, "
                + "tenant_id VARCHAR(64) NOT NULL, consumer_group VARCHAR(128) NOT NULL, "
                + "message_id VARCHAR(64) NOT NULL, payload TEXT NOT NULL)");
        Config config = new Config();
        config.useSingleServer().setAddress(required("APEX_MQ_REDIS"));
        redis = Redisson.create(config);
        service = new MqConsumerService(redis, 30L);
        for (String group : GROUPS) {
            startConsumer(group);
        }
        producer = new DefaultMQProducer("apex_infra_producer_" + RUN);
        producer.setNamesrvAddr(required("APEX_MQ_NAMESRV"));
        producer.setSendMsgTimeout(15000);
        producer.start();
    }

    private static void startConsumer(String group) throws Exception {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(group);
        CONSUMERS.add(consumer);
        consumer.setNamesrvAddr(required("APEX_MQ_NAMESRV"));
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        consumer.setConsumeMessageBatchMaxSize(1);
        consumer.setConsumeThreadMin(1);
        consumer.setConsumeThreadMax(1);
        consumer.subscribe(TOPIC, "*");
        consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
            try {
                for (MessageExt message : messages) {
                    consume(group, message);
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            } catch (Exception failure) {
                CONSUMPTION_FAILURE.compareAndSet(null, failure);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            } finally {
                TenantContext.clear();
            }
        });
        consumer.getDefaultMQPushConsumerImpl().registerConsumeMessageHook(new ConsumeMessageHook() {
            @Override
            public String hookName() {
                return "infra-jdbc-consumption-evidence";
            }

            @Override
            public void consumeMessageBefore(ConsumeMessageContext context) {}

            @Override
            public void consumeMessageAfter(ConsumeMessageContext context) {
                if (context.isSuccess()) {
                    context.getMsgList().forEach(message -> ACKS.computeIfAbsent(
                                    group + ":" + message.getKeys(), ignored -> new AtomicInteger())
                            .incrementAndGet());
                }
            }
        });
        consumer.start();
    }

    private static void consume(String group, MessageExt message) throws Exception {
        var payload = JSON.readTree(message.getBody());
        String tenant = payload.get("tenantId").asText();
        String messageId = payload.get("messageId").asText();
        TenantContext.setTenantId(tenant);
        var result = service.processMessageWithResult(group, messageId, payload, body -> {
            jdbc.update(
                    "INSERT INTO " + SCHEMA
                            + ".message_effect (tenant_id, consumer_group, message_id, payload) VALUES (?, ?, ?, ?)",
                    TenantContext.currentTenantId().orElseThrow(),
                    group,
                    messageId,
                    body.get("value").asText());
            return true;
        });
        if (!result.isSuccess()) {
            throw new IllegalStateException(result.getErrorMessage());
        }
        if (result.isDuplicate()) {
            DUPLICATES
                    .computeIfAbsent(group + ":" + message.getKeys(), ignored -> new AtomicInteger())
                    .incrementAndGet();
        }
    }

    @Test
    void sameEventHasOneJdbcEffectForEachConsumerGroup() throws Exception {
        String tenant = "normal_" + RUN;
        String messageId = UUID.randomUUID().toString();
        send(tenant, messageId, "normal");
        awaitSuccessfulConsumption(tenant, messageId, 1);
        assertEffect(tenant, messageId, "normal");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM " + SCHEMA + ".message_effect WHERE tenant_id = ? AND message_id = ?",
                        Integer.class,
                        tenant,
                        messageId))
                .isEqualTo(2);
    }

    @Test
    void repeatedMessageInSameTenantDoesNotRepeatJdbcEffect() throws Exception {
        String tenant = "duplicate_" + RUN;
        String messageId = UUID.randomUUID().toString();
        send(tenant, messageId, "first");
        awaitSuccessfulConsumption(tenant, messageId, 1);
        send(tenant, messageId, "must-not-be-written");
        awaitSuccessfulConsumption(tenant, messageId, 2);
        assertEffect(tenant, messageId, "first");
        for (String group : GROUPS) {
            assertThat(DUPLICATES.get(group + ":" + tenant + ":" + messageId).get())
                    .isEqualTo(1);
        }
    }

    @Test
    void sameMessageIdInDifferentTenantsHasIndependentJdbcEffects() throws Exception {
        String messageId = UUID.randomUUID().toString();
        String firstTenant = "tenant_a_" + RUN;
        String secondTenant = "tenant_b_" + RUN;
        send(firstTenant, messageId, "first-tenant");
        awaitSuccessfulConsumption(firstTenant, messageId, 1);
        send(secondTenant, messageId, "second-tenant");
        awaitSuccessfulConsumption(secondTenant, messageId, 1);
        assertEffect(firstTenant, messageId, "first-tenant");
        assertEffect(secondTenant, messageId, "second-tenant");
    }

    private static void send(String tenant, String messageId, String value) throws Exception {
        Message message = new Message(
                TOPIC,
                "jdbc-effect",
                tenant + ":" + messageId,
                JSON.writeValueAsBytes(Map.of("tenantId", tenant, "messageId", messageId, "value", value)));
        assertThat(producer.send(message).getSendStatus()).isEqualTo(SendStatus.SEND_OK);
    }

    private static void awaitSuccessfulConsumption(String tenant, String messageId, int deliveries)
            throws InterruptedException {
        for (String group : GROUPS) {
            String key = group + ":" + tenant + ":" + messageId;
            long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
            while (System.nanoTime() < deadline && CONSUMPTION_FAILURE.get() == null) {
                if (ACKS.getOrDefault(key, new AtomicInteger()).get() >= deliveries) {
                    break;
                }
                Thread.sleep(100);
            }
            assertThat(CONSUMPTION_FAILURE.get()).as("真实消费回调异常").isNull();
            assertThat(ACKS.getOrDefault(key, new AtomicInteger()).get())
                    .as("成功消费次数: %s; schema: %s; topic: %s", key, SCHEMA, TOPIC)
                    .isGreaterThanOrEqualTo(deliveries);
            String redisKey = "mq:idempotent:" + encode(tenant) + ":" + encode(group) + ":" + encode(messageId);
            assertThat(redis.<String>getBucket(redisKey, StringCodec.INSTANCE).get())
                    .isEqualTo("SUCCESS");
        }
    }

    private static void assertEffect(String tenant, String messageId, String value) {
        for (String group : GROUPS) {
            assertThat(jdbc.queryForList(
                            "SELECT payload FROM " + SCHEMA
                                    + ".message_effect WHERE tenant_id = ? AND consumer_group = ? AND message_id = ?",
                            String.class,
                            tenant,
                            group,
                            messageId))
                    .containsExactly(value);
        }
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少真实 MQ infra 验收环境变量: " + name);
        }
        return value;
    }

    @AfterAll
    static void stop() {
        CONSUMERS.forEach(DefaultMQPushConsumer::shutdown);
        if (producer != null) {
            producer.shutdown();
        }
        if (redis != null) {
            redis.shutdown();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }
}

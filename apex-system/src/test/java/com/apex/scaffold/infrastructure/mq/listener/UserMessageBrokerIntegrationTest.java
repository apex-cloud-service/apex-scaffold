package com.apex.scaffold.infrastructure.mq.listener;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.infrastructure.mq.constant.MqConstant;
import com.apex.scaffold.infrastructure.mq.handler.UserMessageDlqHandler;
import com.apex.scaffold.infrastructure.mq.service.MqConsumerService;
import com.apex.scaffold.infrastructure.mq.service.MqProducerService;
import com.apex.scaffold.infrastructure.outbox.mapper.MessageOutboxMapper;
import com.apex.scaffold.infrastructure.outbox.service.impl.MessageOutboxServiceImpl;
import com.apex.scaffold.infrastructure.persistence.config.MyMetaObjectHandler;
import com.apex.scaffold.infrastructure.persistence.converter.OperationLogPoConverter;
import com.apex.scaffold.infrastructure.persistence.converter.SystemMessagePoConverter;
import com.apex.scaffold.infrastructure.persistence.datapermission.DefaultTenantLineHandler;
import com.apex.scaffold.infrastructure.persistence.mapper.OperationLogMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.SystemMessageMapper;
import com.apex.scaffold.infrastructure.persistence.repository.OperationLogRepositoryImpl;
import com.apex.scaffold.infrastructure.persistence.repository.UserRegistrationTransaction;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.rocketmq.client.hook.ConsumeMessageContext;
import org.apache.rocketmq.client.hook.ConsumeMessageHook;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer;
import org.apache.rocketmq.spring.support.RocketMQMessageConverter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mapstruct.factory.Mappers;
import org.mybatis.spring.SqlSessionTemplate;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 真 RocketMQ → Spring 消费容器 → 生产 listener → Redis/生产 MyBatis 仓储验收。
 *
 * <p>启用 APEX_REAL_MQ_IT=true，并提供 APEX_MQ_NAMESRV、APEX_MQ_REDIS、APEX_MQ_JDBC_URL、
 * APEX_MQ_DB_USER、APEX_MQ_DB_PASSWORD；数据库账号需 CREATE SCHEMA 权限。
 * 每轮使用独立 topic/schema/tenant/消费组/DLQ 消费组，不触碰共享 Topic、消费组与数据。
 * 不清空共享数据，失败 schema 保留供排查。
 * 自然死信用例依赖 Broker 真实重试耗尽，不做人工死信投递，也不手工写入 Outbox。
 * 不覆盖完整应用装配、Flyway、网关或 Outbox 调度器重新投递。
 */
@EnabledIfEnvironmentVariable(named = "APEX_REAL_MQ_IT", matches = "true")
class UserMessageBrokerIntegrationTest {
    private static final String RUN = UUID.randomUUID().toString().replace("-", "");
    private static final String SCHEMA = "mq_it_" + RUN;
    private static final String TOPIC = "apex_user_it_" + RUN;
    /** 业务消费组，本轮的 {@code %DLQ%} 死信 Topic 由该组名推导。 */
    private static final String CONSUMER_GROUP = "apex_user_it_group_" + RUN;
    /** 是否由死信处理器声明业务消费组映射，默认按生产装配声明。 */
    private static final boolean REGISTER_CONSUMER_GROUP =
            !"false".equalsIgnoreCase(System.getenv().getOrDefault("APEX_MQ_IT_DLQ_REGISTRATION", "register"));
    /** 死信处理器自身的消费组，独立于业务消费组。 */
    private static final String DLQ_PROCESSOR_GROUP = "apex_dlq_it_processor_" + RUN;
    /** 等待 Broker 顺序重试耗尽并生成自然死信的上限。 */
    private static final Duration DLQ_TIMEOUT = Duration.ofMinutes(10);
    /** 解析 Broker 投递消息体，用于按业务身份统计成功确认次数。 */
    private static final ObjectMapper JSON = new ObjectMapper();
    /** 业务身份（租户 + 事件标识）到成功消费次数的计数。 */
    private static final Map<String, AtomicInteger> ACKS = new ConcurrentHashMap<>();
    /** Broker 实时投递到业务消费端的消息，用于读取生产代理生成的 keys。 */
    private static final Set<MessageExt> DELIVERED = ConcurrentHashMap.newKeySet();

    private static HikariDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static RedissonClient redis;
    private static MessageOutboxMapper outboxMapper;
    private static GenericApplicationContext containerContext;
    private static RocketMQTemplate rocketMQTemplate;
    private static MqProducerService mqProducerService;
    private static DeadLetterQueueProcessor dlqProcessor;
    private static UserMessageDlqHandler dlqHandler;
    private static DefaultRocketMQListenerContainer container;

    @BeforeAll
    static void start() throws Exception {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(required("APEX_MQ_JDBC_URL"));
        dataSource.setUsername(required("APEX_MQ_DB_USER"));
        dataSource.setPassword(System.getenv().getOrDefault("APEX_MQ_DB_PASSWORD", ""));
        try (var connection = DriverManager.getConnection(
                        dataSource.getJdbcUrl(), dataSource.getUsername(), dataSource.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
        }
        dataSource.setSchema(SCHEMA);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE " + SCHEMA
                + ".biz_system_message (id BIGINT PRIMARY KEY, tenant_id VARCHAR(64) NOT NULL, "
                + "user_id BIGINT, msg_type VARCHAR(64), content TEXT, ref_type VARCHAR(64), ref_id BIGINT, read_at TIMESTAMP, "
                + "create_time TIMESTAMP, update_time TIMESTAMP, version INT, deleted INT DEFAULT 0)");
        jdbc.execute(
                "CREATE TABLE " + SCHEMA + ".sys_operation_log (id BIGINT PRIMARY KEY, tenant_id VARCHAR(64) NOT NULL, "
                        + "module VARCHAR(64), operation_type VARCHAR(64), description TEXT, request_method TEXT, request_uri TEXT, "
                        + "request_params TEXT, response_result TEXT, status INT, error_msg TEXT, execution_time BIGINT, ip TEXT, "
                        + "location TEXT, user_id BIGINT, user_name TEXT, operation_time TIMESTAMP, device_type TEXT, browser TEXT, "
                        + "os TEXT, trace_id TEXT, create_time TIMESTAMP, update_time TIMESTAMP, version INT, deleted INT DEFAULT 0)");
        // 列、类型与非空/唯一约束与 PostgreSQL 生产迁移 V45__add_message_outbox.sql 保持一致；
        // 性能索引、注释与 updated_at 触发器不参与本验收，未在临时 schema 中重建。
        jdbc.execute("CREATE TABLE " + SCHEMA + ".sys_message_outbox ("
                + "id BIGSERIAL PRIMARY KEY, "
                + "message_id VARCHAR(64) NOT NULL UNIQUE, "
                + "topic VARCHAR(255) NOT NULL, "
                + "tag VARCHAR(64) NOT NULL, "
                + "payload TEXT NOT NULL, "
                + "message_type VARCHAR(32) NOT NULL DEFAULT 'BUSINESS_MESSAGE', "
                + "status VARCHAR(16) NOT NULL DEFAULT 'PENDING', "
                + "attempts INT NOT NULL DEFAULT 0, "
                + "max_attempts INT NOT NULL DEFAULT 5, "
                + "next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "retry_delay_seconds INT NOT NULL DEFAULT 10, "
                + "locked_by VARCHAR(64) DEFAULT NULL, "
                + "locked_at TIMESTAMP(6) DEFAULT NULL, "
                + "lock_timeout_seconds INT NOT NULL DEFAULT 300, "
                + "last_error VARCHAR(1000) DEFAULT NULL, "
                + "tenant_id VARCHAR(64) NOT NULL, "
                + "created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "delivered_at TIMESTAMP(6) DEFAULT NULL)");
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new DefaultTenantLineHandler()));
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(SystemMessageMapper.class);
        configuration.addMapper(OperationLogMapper.class);
        configuration.addMapper(MessageOutboxMapper.class);
        GlobalConfig global = new GlobalConfig();
        global.setDbConfig(new GlobalConfig.DbConfig());
        global.setMetaObjectHandler(new MyMetaObjectHandler());
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        factory.setGlobalConfig(global);
        factory.setPlugins(interceptor);
        SqlSessionTemplate sessions = new SqlSessionTemplate(factory.getObject());
        outboxMapper = sessions.getMapper(MessageOutboxMapper.class);
        Config config = new Config();
        config.useSingleServer().setAddress(required("APEX_MQ_REDIS"));
        redis = Redisson.create(config);
        // 生产装配中死信处理器先于业务监听器就绪：先创建处理器并完成处理器注册，再构造监听器
        // （其构造期即回调声明消费组映射），最后统一启动，避免登记回调遇到尚未创建的处理器。
        dlqProcessor = new DeadLetterQueueProcessor(null);
        inject(dlqProcessor, "nameServer", required("APEX_MQ_NAMESRV"));
        inject(dlqProcessor, "dlqConsumerGroup", DLQ_PROCESSOR_GROUP);
        dlqHandler = new UserMessageDlqHandler(
                dlqProcessor, new MessageOutboxServiceImpl(outboxMapper), TOPIC, CONSUMER_GROUP);
        dlqHandler.register();
        // 注册事件不访问 UserRepository/CacheService；实际执行的依赖均使用生产实现。
        UserMessageListener listener = new UserMessageListener(
                new MqConsumerService(redis),
                null,
                new UserRegistrationTransaction(
                        sessions.getMapper(SystemMessageMapper.class),
                        Mappers.getMapper(SystemMessagePoConverter.class),
                        sessions.getMapper(OperationLogMapper.class),
                        Mappers.getMapper(OperationLogPoConverter.class),
                        new TransactionTemplate(new DataSourceTransactionManager(dataSource))),
                new OperationLogRepositoryImpl(
                        sessions.getMapper(OperationLogMapper.class), Mappers.getMapper(OperationLogPoConverter.class)),
                null,
                registration -> {
                    if (REGISTER_CONSUMER_GROUP) {
                        dlqProcessor.registerConsumerGroup(registration.originalTopic(), registration.consumerGroup());
                    }
                },
                TOPIC,
                CONSUMER_GROUP);
        // 容器按注解的 ${rocketmq.name-server:} 占位符解析地址，不注入该属性时消费者拿到空地址；
        // 消费回调按 name 从 ApplicationContext 取容器，容器也必须注册为该上下文的单例 bean。
        container = new DefaultRocketMQListenerContainer();
        container.setName("realMqListenerContainer");
        containerContext = applicationContext(Map.of("rocketmq.name-server", required("APEX_MQ_NAMESRV")));
        containerContext.refresh();
        registerContainerBean(containerContext, "realMqListenerContainer", container);
        container.setApplicationContext(containerContext);
        container.setNameServer(required("APEX_MQ_NAMESRV"));
        container.setRocketMQMessageListener(UserMessageListener.class.getAnnotation(RocketMQMessageListener.class));
        container.setConsumerGroup(CONSUMER_GROUP);
        container.setTopic(TOPIC);
        container.setRocketMQListener(listener);
        container.setMessageConverter(new RocketMQMessageConverter().getMessageConverter());
        container.afterPropertiesSet();
        container.getConsumer().getDefaultMQPushConsumerImpl().registerConsumeMessageHook(new ConsumeMessageHook() {
            @Override
            public String hookName() {
                return "real-mq-ack-evidence";
            }

            @Override
            public void consumeMessageBefore(ConsumeMessageContext context) {
                context.getMsgList().forEach(message -> DELIVERED.add(message));
            }

            @Override
            public void consumeMessageAfter(ConsumeMessageContext result) {
                if (result.isSuccess()) {
                    result.getMsgList().forEach(message -> ACKS.computeIfAbsent(
                                    deliveredIdentity(message), key -> new AtomicInteger())
                            .incrementAndGet());
                }
            }
        });
        DefaultMQProducer producer = new DefaultMQProducer("apex_user_it_producer_" + RUN);
        producer.setNamesrvAddr(required("APEX_MQ_NAMESRV"));
        rocketMQTemplate = new RocketMQTemplate();
        rocketMQTemplate.setProducer(producer);
        rocketMQTemplate.setMessageConverter(new RocketMQMessageConverter().getMessageConverter());
        rocketMQTemplate.afterPropertiesSet();
        mqProducerService =
                new MqProducerService(rocketMQTemplate, new ObjectMapper(), new MessageOutboxServiceImpl(outboxMapper));
        // 死信订阅必须先于业务消费容器启动，避免消费起点之后的自然死信无人接收。
        dlqProcessor.start();
        container.start();
    }

    @Test
    void registrationPersistsWelcomeAndAuditBeforeBrokerAcknowledgement() throws Exception {
        String tenant = "a-" + RUN;
        String event = UUID.randomUUID().toString();
        send(tenant, event);
        awaitSuccess(tenant, event, 1);
        assertRows(tenant);
    }

    @Test
    void duplicateEventInSameTenantHasOnlyOneBusinessEffect() throws Exception {
        String tenant = "b-" + RUN;
        String event = UUID.randomUUID().toString();
        send(tenant, event);
        awaitSuccess(tenant, event, 1);
        send(tenant, event);
        awaitSuccess(tenant, event, 2);
        assertRows(tenant);
    }

    @Test
    void sameEventInDifferentTenantsHasIndependentBusinessEffects() throws Exception {
        String tenantA = "c-" + RUN;
        String tenantB = "d-" + RUN;
        String event = UUID.randomUUID().toString();
        send(tenantA, event);
        send(tenantB, event);
        awaitSuccess(tenantA, event, 1);
        awaitSuccess(tenantB, event, 1);
        assertRows(tenantA);
        assertRows(tenantB);
    }

    @Test
    void registrationRollsBackWelcomeWhenAuditInsertFails() throws Exception {
        String tenant = "e-" + RUN;
        String event = UUID.randomUUID().toString();
        jdbc.execute(
                "ALTER TABLE " + SCHEMA
                        + ".sys_operation_log ADD CONSTRAINT registration_failure_injection CHECK (description <> '用户注册: 故障注入')");
        try {
            send(tenant, event, "故障注入");
            awaitState(tenant, event, "FAILED");
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM " + SCHEMA
                                    + ".biz_system_message WHERE tenant_id=? AND user_id=71001 AND msg_type='WELCOME'",
                            Integer.class,
                            tenant))
                    .as("操作日志失败时欢迎消息应随同一数据库事务回滚")
                    .isZero();
        } finally {
            jdbc.execute("ALTER TABLE " + SCHEMA + ".sys_operation_log DROP CONSTRAINT registration_failure_injection");
        }
    }

    /**
     * 自然死信链路：业务消费持续失败 → Broker 顺序重试耗尽 → {@code %DLQ%} → 生产死信处理器 → Outbox。
     *
     * <p>首次投递时让注册事务插入操作日志违反 CHECK 约束制造真实失败，随后立即移除约束；此时幂等状态
     * 已为 FAILED，后续每次重试都被生产幂等逻辑拒绝，直至 Broker 重试耗尽。不做死信投递，也不写 Outbox。
     */
    @Test
    void exhaustedReconsumeProducesNaturalDeadLetterPersistedToOutbox() throws Exception {
        assertThat(REGISTER_CONSUMER_GROUP)
                .as("该用例要求生产装配声明业务消费组映射，供死信反查原始 Topic")
                .isTrue();
        String tenant = "f-" + RUN;
        String event = UUID.randomUUID().toString();
        jdbc.execute("ALTER TABLE " + SCHEMA
                + ".sys_operation_log ADD CONSTRAINT dlq_failure_injection CHECK (description <> '用户注册: 死信注入')");
        String messageId;
        try {
            messageId = send(tenant, event, "死信注入");
            awaitState(tenant, event, "FAILED");
        } finally {
            // 只让首次投递失败：恢复后 Broker 的每次重试仍会执行 listener，
            // 但业务中断由 FAILED 幂等状态保证，最终自然耗尽重试。
            jdbc.execute("ALTER TABLE " + SCHEMA + ".sys_operation_log DROP CONSTRAINT dlq_failure_injection");
        }
        String outboxId = awaitOutbox(messageId);
        Map<String, Object> row =
                jdbc.queryForMap("SELECT * FROM " + SCHEMA + ".sys_message_outbox WHERE message_id=?", outboxId);
        assertThat(outboxId).isEqualTo(messageId + ":dlq-retry");
        assertThat(row.get("topic")).isEqualTo(TOPIC);
        assertThat(row.get("tag")).isEqualTo(MqConstant.TAG_USER_REGISTER);
        assertThat(row.get("tenant_id")).isEqualTo(tenant);
        assertThat(row.get("message_type")).isEqualTo("BUSINESS_MESSAGE");
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("attempts")).isEqualTo(0);
        assertThat(row.get("max_attempts")).isEqualTo(10);
        assertThat(row.get("retry_delay_seconds")).isEqualTo(60);
        assertThat((String) row.get("payload")).contains(event);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM " + SCHEMA + ".sys_message_outbox WHERE tenant_id=?",
                        Integer.class,
                        tenant))
                .as("死信补偿只写入一条 Outbox 记录")
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM " + SCHEMA
                                + ".sys_operation_log WHERE tenant_id=? AND operation_type='USER_REGISTER'",
                        Integer.class,
                        tenant))
                .as("持续失败期间业务副作用不应提交")
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM " + SCHEMA
                                + ".biz_system_message WHERE tenant_id=? AND user_id=71001 AND msg_type='WELCOME'",
                        Integer.class,
                        tenant))
                .as("持续失败期间欢迎消息不应提交")
                .isZero();
    }

    private static void send(String tenant, String event) throws Exception {
        send(tenant, event, "MQ验收");
    }

    /**
     * 通过生产 {@link MqProducerService} 顺序发送用户注册消息，返回消费端观测到的消息标识。
     *
     * <p>生产实现的 keys 恒为新生成 UUID，因此抛开发送入参，以消费到的消息 keys 作为证据。
     */
    private static String send(String tenant, String event, String nickName) throws Exception {
        Map<String, Object> payload = Map.of(
                "tenantId",
                tenant,
                "messageId",
                event,
                "tag",
                MqConstant.TAG_USER_REGISTER,
                "userId",
                71001L,
                "nickName",
                nickName);
        String previousTenantId = TenantContext.setTenantId(tenant);
        try {
            assertThat(mqProducerService
                            .sendOrderly(TOPIC, MqConstant.TAG_USER_REGISTER, payload, tenant + ":" + event)
                            .getSendStatus())
                    .isEqualTo(SendStatus.SEND_OK);
        } finally {
            if (previousTenantId == null) {
                TenantContext.clear();
            } else {
                TenantContext.setTenantId(previousTenantId);
            }
        }
        return awaitDelivered(tenant, event, nickName);
    }

    /**
     * 等待该消息被 Broker 投递到业务消费端，返回真实消息标识。
     *
     * <p>消息标识由生产代理生成，只能从 Broker 投递到消费端的消息上读取；顺序键相同的消息
     * 共享队列，故按租户与昵称匹配本次发送的载荷。
     */
    private static String awaitDelivered(String tenant, String event, String nickName) throws Exception {
        String marker = "\"nickName\":\"" + nickName + "\"";
        long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
        while (System.nanoTime() < deadline) {
            for (MessageExt message : DELIVERED) {
                String body = new String(message.getBody(), StandardCharsets.UTF_8);
                if (tenant.equals(message.getUserProperty("tenantId"))
                        && body.contains(event)
                        && body.contains(marker)) {
                    return message.getKeys();
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("消息未被 Broker 投递到业务消费端, tenant=" + tenant + " event=" + event);
    }

    /**
     * 从 Broker 投递的消息还原业务身份，供成功确认计数使用。
     *
     * @param message Broker 投递到业务消费端的消息
     * @return {@code tenantId:messageId}
     */
    private static String deliveredIdentity(MessageExt message) {
        String tenantId = message.getUserProperty("tenantId");
        try {
            return tenantId + ":"
                    + JSON.readTree(message.getBody()).path("messageId").asText();
        } catch (Exception e) {
            return tenantId + ":" + message.getKeys();
        }
    }

    /**
     * 等待 Broker 顺序重试耗尽后由死信处理器写入的 Outbox 记录。
     *
     * <p>只轮询数据库，不触发投递，也不构造死信消息；超时即视为自然死信链路未完成。
     *
     * @param messageId 业务消费端观测到的消息标识
     * @return 死信处理器写入的 Outbox 消息标识
     */
    private static String awaitOutbox(String messageId) throws Exception {
        String outboxId = messageId + ":dlq-retry";
        long deadline = System.nanoTime() + DLQ_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            Integer rows = jdbc.queryForObject(
                    "SELECT count(*) FROM " + SCHEMA + ".sys_message_outbox WHERE message_id=?",
                    Integer.class,
                    outboxId);
            if (rows != null && rows > 0) {
                return outboxId;
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("Broker 重试耗尽后未生成自然死信或死信未落 Outbox, messageId=" + messageId + "，等待上限 " + DLQ_TIMEOUT);
    }

    private static void awaitSuccess(String tenant, String event, int deliveries) throws Exception {
        String state = null;
        long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
        while (System.nanoTime() < deadline) {
            state = redis.<String>getBucket(idempotentKey(tenant, event), StringCodec.INSTANCE)
                    .get();
            if ("FAILED".equals(state)) {
                break;
            }
            AtomicInteger count = ACKS.get(tenant + ":" + event);
            if ("SUCCESS".equals(state) && count != null && count.get() >= deliveries) {
                return;
            }
            Thread.sleep(100);
        }
        assertThat(state)
                .as("生产消费链 Redis 状态 tenant=%s event=%s；检查消费异常日志", tenant, event)
                .isEqualTo("SUCCESS");
        assertThat(ACKS.getOrDefault(tenant + ":" + event, new AtomicInteger()).get())
                .as("broker 成功消费次数")
                .isGreaterThanOrEqualTo(deliveries);
    }

    private static void awaitState(String tenant, String event, String expected) throws Exception {
        String state = null;
        long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
        while (System.nanoTime() < deadline) {
            state = redis.<String>getBucket(idempotentKey(tenant, event), StringCodec.INSTANCE)
                    .get();
            if (expected.equals(state)) {
                return;
            }
            Thread.sleep(100);
        }
        assertThat(state).as("生产消费链 Redis 状态 tenant=%s event=%s", tenant, event).isEqualTo(expected);
    }

    private static String idempotentKey(String tenant, String event) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return "mq:idempotent:" + encoder.encodeToString(tenant.getBytes(StandardCharsets.UTF_8)) + ":"
                + encoder.encodeToString(CONSUMER_GROUP.getBytes(StandardCharsets.UTF_8)) + ":"
                + encoder.encodeToString(event.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertRows(String tenant) {
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM " + SCHEMA
                                + ".biz_system_message WHERE tenant_id=? AND user_id=71001 AND msg_type='WELCOME'",
                        Integer.class,
                        tenant))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM " + SCHEMA
                                + ".sys_operation_log WHERE tenant_id=? AND user_id=71001 AND operation_type='USER_REGISTER'",
                        Integer.class,
                        tenant))
                .isEqualTo(1);
    }

    private static GenericApplicationContext applicationContext(Map<String, Object> properties) {
        GenericApplicationContext applicationContext = new GenericApplicationContext();
        applicationContext
                .getEnvironment()
                .getPropertySources()
                .addFirst(new MapPropertySource("realMqNameServer", properties));
        return applicationContext;
    }

    /**
     * 消费回调按 name 从 ApplicationContext 取容器 bean。必须先刷新上下文、再注册容器单例：
     * 容器实现了 Lifecycle，若在 refresh 前注册会被上下文提前 start，此时 consumer 尚未初始化。
     */
    private static void registerContainerBean(
            GenericApplicationContext applicationContext, String beanName, Object bean) {
        applicationContext.getBeanFactory().registerSingleton(beanName, bean);
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少真实 MQ 验收环境变量: " + name);
        }
        return value;
    }

    @AfterAll
    static void stop() {
        if (container != null && container.isRunning()) {
            container.destroy();
        }
        if (dlqProcessor != null) {
            dlqProcessor.shutdown();
        }
        if (rocketMQTemplate != null) {
            rocketMQTemplate.destroy();
        }
        if (redis != null) {
            redis.shutdown();
        }
        if (containerContext != null) {
            containerContext.close();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }
}

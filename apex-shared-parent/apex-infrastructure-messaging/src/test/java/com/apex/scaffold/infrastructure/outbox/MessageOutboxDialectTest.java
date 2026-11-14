package com.apex.scaffold.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.infrastructure.outbox.entity.MessageOutboxEntity;
import com.apex.scaffold.infrastructure.outbox.mapper.MessageOutboxMapper;
import com.apex.scaffold.infrastructure.outbox.service.impl.MessageOutboxServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 消息 Outbox（sys_message_outbox）双库方言回归测试。
 *
 * <p>直接执行 apex-system 的 V45 生产迁移脚本建表，再经真实 MyBatis-Plus
 * {@link MessageOutboxMapper} 与 {@link MessageOutboxServiceImpl} 在 MySQL 与
 * PostgreSQL 上验证锁定、过期锁释放、失败登记与待投递查询行为一致。
 * 测试受 {@code OUTBOX_DUAL_DB=true} 显式开启，连接信息来自
 * {@code OUTBOX_<DIALECT>_URL/USER/PASSWORD} 环境变量；未开启时整类跳过。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 */
@EnabledIfEnvironmentVariable(named = "OUTBOX_DUAL_DB", matches = "true")
class MessageOutboxDialectTest {

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private MessageOutboxServiceImpl service;

    private void initialize(String dialect) throws Exception {
        String prefix = "OUTBOX_" + dialect.toUpperCase();
        dataSource = new DriverManagerDataSource(
                System.getenv(prefix + "_URL"), System.getenv(prefix + "_USER"), System.getenv(prefix + "_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        if ("postgresql".equals(dialect)) {
            jdbc.execute("DROP TRIGGER IF EXISTS trigger_sys_message_outbox_updated_at ON sys_message_outbox");
            jdbc.execute("DROP FUNCTION IF EXISTS update_sys_message_outbox_updated_at()");
            jdbc.execute("DROP TABLE IF EXISTS sys_message_outbox");
        } else {
            jdbc.execute("DROP TABLE IF EXISTS sys_message_outbox");
        }
        Path root = Path.of(System.getProperty("maven.multiModuleProjectDirectory"));
        String schema =
                Files.readString(root.resolve("apex-components-parent/apex-system/src/main/resources/db/migration/"
                        + dialect + "/V45__add_message_outbox.sql"));
        for (String statement : splitStatements(schema)) {
            if (!statement.isBlank()) {
                jdbc.execute(statement);
            }
        }

        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setDbConfig(new GlobalConfig.DbConfig());
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        // 模拟生产 VendorDatabaseIdProvider 的解析结果，驱动 _databaseId 方言分支
        configuration.setDatabaseId(dialect);
        configuration.addMapper(MessageOutboxMapper.class);
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        MessageOutboxMapper mapper = new SqlSessionTemplate(sqlSessionFactory).getMapper(MessageOutboxMapper.class);
        service = new MessageOutboxServiceImpl(mapper);
    }

    /** 按分号切分迁移脚本，跳过 PostgreSQL 函数体的 {@code $$ ... $$} 段。 */
    private static List<String> splitStatements(String schema) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inDollarQuote = false;
        for (int index = 0; index < schema.length(); index++) {
            if (schema.startsWith("$$", index)) {
                inDollarQuote = !inDollarQuote;
                current.append("$$");
                index++;
                continue;
            }
            char ch = schema.charAt(index);
            if (ch == ';' && !inDollarQuote) {
                statements.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        if (!current.toString().isBlank()) {
            statements.add(current.toString());
        }
        return statements;
    }

    private void seed(
            String messageId,
            String status,
            int attempts,
            int maxAttempts,
            LocalDateTime nextAttemptAt,
            LocalDateTime lockedAt,
            String lockedBy) {
        jdbc.update(
                "INSERT INTO sys_message_outbox (message_id,topic,tag,payload,message_type,status,attempts,"
                        + "max_attempts,next_attempt_at,retry_delay_seconds,locked_by,locked_at,"
                        + "lock_timeout_seconds,tenant_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                messageId,
                "topic-test",
                "tag-test",
                "{}",
                "BUSINESS_MESSAGE",
                status,
                attempts,
                maxAttempts,
                nextAttemptAt,
                10,
                lockedBy,
                lockedAt,
                300,
                "tenant-test");
    }

    /** 数据库时钟，所有与时间相关的种子数据均以它为基准，避免应用与数据库时区偏差。 */
    private LocalDateTime databaseNow() {
        return jdbc.queryForObject("SELECT CURRENT_TIMESTAMP(6)", java.sql.Timestamp.class)
                .toLocalDateTime();
    }

    private Map<String, Object> row(String messageId) {
        return jdbc.queryForMap(
                "SELECT status,attempts,locked_by,locked_at,next_attempt_at,last_error "
                        + "FROM sys_message_outbox WHERE message_id=?",
                messageId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"mysql", "postgresql"})
    void lockMessageIsPortableAndEnforcesStateAndTiming(String dialect) throws Exception {
        initialize(dialect);
        LocalDateTime now = databaseNow();
        seed("m-due", "PENDING", 0, 5, now.minusSeconds(1), null, null);
        seed("m-future", "PENDING", 0, 5, now.plusHours(24), null, null);
        seed("m-delivered", "DELIVERED", 1, 5, now.minusSeconds(1), null, null);

        assertThat(service.lockMessage("m-due", "inst-a").block()).isTrue();
        Map<String, Object> locked = row("m-due");
        assertThat(locked.get("status")).isEqualTo("PROCESSING");
        assertThat(locked.get("locked_by")).isEqualTo("inst-a");
        assertThat(locked.get("locked_at")).isNotNull();

        assertThat(service.lockMessage("m-due", "inst-b").block()).isFalse();
        assertThat(service.lockMessage("m-future", "inst-b").block()).isFalse();
        assertThat(service.lockMessage("m-delivered", "inst-b").block()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"mysql", "postgresql"})
    void releaseExpiredLocksClearsOnlyExpiredLocks(String dialect) throws Exception {
        initialize(dialect);
        LocalDateTime now = databaseNow();
        seed("m-expired", "PROCESSING", 0, 5, now, now.minusSeconds(400), "crashed-inst");
        seed("m-fresh", "PROCESSING", 0, 5, now, now, "live-inst");

        service.releaseExpiredLocks().block();

        Map<String, Object> expired = row("m-expired");
        assertThat(expired.get("status")).isEqualTo("PROCESSING");
        assertThat(expired.get("locked_by")).isNull();
        assertThat(expired.get("locked_at")).isNull();

        Map<String, Object> fresh = row("m-fresh");
        assertThat(fresh.get("status")).isEqualTo("PROCESSING");
        assertThat(fresh.get("locked_by")).isEqualTo("live-inst");
    }

    @ParameterizedTest
    @ValueSource(strings = {"mysql", "postgresql"})
    void markAsFailedSchedulesRetryBelowMaxAndDeadAtMax(String dialect) throws Exception {
        initialize(dialect);
        LocalDateTime now = databaseNow();
        seed("m-retry", "PROCESSING", 1, 3, now, now, "inst-a");
        seed("m-dead", "PROCESSING", 2, 3, now, now, "inst-a");

        service.markAsFailed("m-retry", "boom").block();
        Map<String, Object> retry = row("m-retry");
        assertThat(retry.get("attempts")).isEqualTo(2);
        assertThat(retry.get("status")).isEqualTo("PENDING");
        assertThat(retry.get("locked_by")).isNull();
        assertThat(retry.get("last_error")).isEqualTo("boom");
        Object rawNextAttempt = retry.get("next_attempt_at");
        LocalDateTime nextAttempt = rawNextAttempt instanceof java.sql.Timestamp timestamp
                ? timestamp.toLocalDateTime()
                : (LocalDateTime) rawNextAttempt;
        assertThat(nextAttempt).isAfter(LocalDateTime.now());

        service.markAsFailed("m-dead", "final").block();
        Map<String, Object> dead = row("m-dead");
        assertThat(dead.get("attempts")).isEqualTo(3);
        assertThat(dead.get("status")).isEqualTo("DEAD");
    }

    @ParameterizedTest
    @ValueSource(strings = {"mysql", "postgresql"})
    void fetchPendingMessagesReturnsOnlyDuePendingOrdered(String dialect) throws Exception {
        initialize(dialect);
        LocalDateTime now = databaseNow();
        seed("m-due", "PENDING", 0, 5, now.minusSeconds(1), null, null);
        seed("m-future", "PENDING", 0, 5, now.plusHours(24), null, null);
        seed("m-processing", "PROCESSING", 0, 5, now.minusSeconds(1), now, "inst-a");

        List<MessageOutboxEntity> pending =
                service.fetchPendingMessages(10).collectList().block();

        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getMessageId()).isEqualTo("m-due");
    }
}

package com.apex.scaffold.interfaces.config;

import java.util.Objects;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 生产数据库持久化参数启动校验器。
 *
 * <p>投票提交包含业务数据、评分和事件的事务写入。生产环境必须在事务提交成功时同步刷新
 * InnoDB redo，并在启用 binlog 时同步刷新 binlog，避免主机故障导致已确认投票丢失。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.2.0
 */
@Configuration
@Profile("prod")
public class ProductionDatabaseSafetyValidator implements ApplicationRunner {

    static final String DURABILITY_SQL = "SELECT @@innodb_flush_log_at_trx_commit, @@sync_binlog, @@log_bin";

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建生产数据库安全校验器。
     *
     * @param jdbcTemplate 数据库访问模板
     */
    public ProductionDatabaseSafetyValidator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 应用就绪前读取 MySQL 全局有效参数并执行安全校验。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        DatabaseDurabilitySnapshot snapshot = jdbcTemplate.queryForObject(
                DURABILITY_SQL,
                (resultSet, rowNumber) -> new DatabaseDurabilitySnapshot(
                        resultSet.getInt(1), resultSet.getInt(2), resultSet.getBoolean(3)));
        validate(Objects.requireNonNull(snapshot, "未读取到 MySQL 持久化参数"));
    }

    /**
     * 校验投票事务所需的持久化级别。
     *
     * @param snapshot 数据库参数快照
     */
    static void validate(DatabaseDurabilitySnapshot snapshot) {
        if (snapshot.innodbFlushLogAtTrxCommit() != 1) {
            throw new IllegalStateException(
                    "生产数据库持久化参数不安全：innodb_flush_log_at_trx_commit 必须为 1，当前为 " + snapshot.innodbFlushLogAtTrxCommit());
        }
        if (snapshot.binaryLogEnabled() && snapshot.syncBinlog() != 1) {
            throw new IllegalStateException("生产数据库持久化参数不安全：启用 binlog 时 sync_binlog 必须为 1，当前为 " + snapshot.syncBinlog());
        }
    }

    /**
     * MySQL 持久化参数快照。
     *
     * @param innodbFlushLogAtTrxCommit InnoDB redo 刷盘策略
     * @param syncBinlog binlog 刷盘间隔
     * @param binaryLogEnabled 是否启用 binlog
     */
    record DatabaseDurabilitySnapshot(int innodbFlushLogAtTrxCommit, int syncBinlog, boolean binaryLogEnabled) {}
}

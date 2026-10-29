package com.apex.scaffold.infrastructure.outbox.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.mapping.MappedStatement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MessageOutboxMapper 方言分支 SQL 解析测试。
 *
 * <p>不依赖真实数据库：通过手工构建 {@link MybatisConfiguration} 并设置不同
 * {@code databaseId}，验证 {@code releaseExpiredLocks} 的 MySQL/PostgreSQL 分支选择与
 * {@code lockMessage} 的两库通用时间函数。PostgreSQL 分支 SQL 同时经 JSqlParser 解析校验，
 * 确保租户/数据权限拦截器的 SQL 改写链路可以处理该方言片段。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 */
@DisplayName("MessageOutboxMapper 方言分支 SQL 解析")
class MessageOutboxMapperDialectSqlTest {

    /** 过期锁释放语句的 MappedStatement 标识。 */
    private static final String RELEASE_STATEMENT =
            "com.apex.scaffold.infrastructure.outbox.mapper.MessageOutboxMapper.releaseExpiredLocks";

    /** 消息锁定语句的 MappedStatement 标识。 */
    private static final String LOCK_STATEMENT =
            "com.apex.scaffold.infrastructure.outbox.mapper.MessageOutboxMapper.lockMessage";

    /**
     * 在指定 {@code databaseId} 下解析 Mapper 语句的最终 SQL。
     *
     * @param databaseId 方言标识，{@code null} 表示未配置 DatabaseIdProvider
     * @param statementId MappedStatement 标识
     * @return 压缩空白后的最终 SQL
     */
    private static String resolvedSql(String databaseId, String statementId) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        if (databaseId != null) {
            configuration.setDatabaseId(databaseId);
        }
        configuration.addMapper(MessageOutboxMapper.class);
        MappedStatement statement = configuration.getMappedStatement(statementId);
        return statement
                .getSqlSource()
                .getBoundSql(null)
                .getSql()
                .replaceAll("\\s+", " ")
                .trim();
    }

    @Test
    @DisplayName("未配置 databaseId 时 releaseExpiredLocks 保持 MySQL 分支（历史行为）")
    void releaseExpiredLocksFallsBackToMysqlBranchWithoutDatabaseId() {
        String sql = resolvedSql(null, RELEASE_STATEMENT);
        assertThat(sql).contains("DATE_SUB(NOW(6), INTERVAL lock_timeout_seconds SECOND)");
        assertThat(sql).doesNotContain("INTERVAL '1 second'");
    }

    @Test
    @DisplayName("databaseId=mysql 时 releaseExpiredLocks 使用 MySQL 分支")
    void releaseExpiredLocksUsesMysqlBranch() {
        String sql = resolvedSql("mysql", RELEASE_STATEMENT);
        assertThat(sql).contains("DATE_SUB(NOW(6), INTERVAL lock_timeout_seconds SECOND)");
        assertThat(sql).doesNotContain("INTERVAL '1 second'");
    }

    @Test
    @DisplayName("databaseId=postgresql 时 releaseExpiredLocks 使用 PostgreSQL 分支且可被 JSqlParser 解析")
    void releaseExpiredLocksUsesPostgresqlBranch() throws Exception {
        String sql = resolvedSql("postgresql", RELEASE_STATEMENT);
        assertThat(sql).doesNotContain("DATE_SUB");
        assertThat(sql).doesNotContain("NOW(");
        assertThat(sql).contains("CURRENT_TIMESTAMP(6) - (lock_timeout_seconds * INTERVAL '1 second')");
        // 租户/数据权限拦截器基于 JSqlParser 改写 SQL，方言分支必须可被解析
        net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql);
    }

    @Test
    @DisplayName("lockMessage 在各方言下均使用两库通用的 CURRENT_TIMESTAMP(6)")
    void lockMessageUsesPortableCurrentTimestamp() {
        String[] databaseIds = {null, "mysql", "postgresql"};
        for (String databaseId : databaseIds) {
            String sql = resolvedSql(databaseId, LOCK_STATEMENT);
            assertThat(sql).contains("CURRENT_TIMESTAMP(6)");
            assertThat(sql).doesNotContain("NOW(");
        }
    }
}

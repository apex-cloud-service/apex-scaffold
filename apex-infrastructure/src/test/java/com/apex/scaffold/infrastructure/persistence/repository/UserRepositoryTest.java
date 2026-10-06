package com.apex.scaffold.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.context.DataScopeConfig;
import com.apex.scaffold.common.context.DataScopeContext;
import com.apex.scaffold.common.context.DataScopeInfo;
import com.apex.scaffold.common.context.DataScopeJdbcBridge;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.infrastructure.persistence.config.MybatisPlusConfig;
import com.apex.scaffold.infrastructure.persistence.converter.UserPoConverter;
import com.apex.scaffold.infrastructure.persistence.datapermission.MdcDataScopeProvider;
import com.apex.scaffold.infrastructure.persistence.mapper.UserMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.Statement;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class UserRepositoryTest {

    private static HikariDataSource dataSource;
    private static SqlSessionFactory sqlSessionFactory;
    private SqlSession sqlSession;
    private UserRepositoryImpl repository;

    @BeforeAll
    static void setUpFactory() throws Exception {
        // 使用 H2 内存库（MySQL 兼容模式）替代外部 MySQL，保证无数据库环境下测试自包含运行：
        // - DB_CLOSE_DELAY=-1：连接池关闭连接后数据库保留，随 JVM 生命周期存续；
        // - DATABASE_TO_LOWER/CASE_INSENSITIVE_IDENTIFIERS：与 MySQL 大小写行为对齐。
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:h2:mem:user_repo_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE"
                + ";CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setDriverClassName("org.h2.Driver");

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS sys_user");
            statement.execute("CREATE TABLE sys_user ("
                    + "id BIGINT NOT NULL PRIMARY KEY, "
                    + "open_id VARCHAR(64), username VARCHAR(64), dept_id BIGINT, tenant_id VARCHAR(64), "
                    + "password VARCHAR(100), union_id VARCHAR(64), access_token VARCHAR(255), "
                    + "refresh_token VARCHAR(255), token_expire_time DATETIME, nick_name VARCHAR(64), "
                    + "avatar_url VARCHAR(255), gender INT, birthday DATE, country VARCHAR(64), "
                    + "province VARCHAR(64), city VARCHAR(64), language VARCHAR(32), phone VARCHAR(32), "
                    + "email VARCHAR(128), keycloak_sub VARCHAR(128), "
                    + "status INT, onboarding_status VARCHAR(64), last_login_time DATETIME, "
                    + "last_login_ip VARCHAR(64), last_heartbeat_time DATETIME, create_time DATETIME NOT NULL, "
                    + "update_time DATETIME NOT NULL, version INT NOT NULL DEFAULT 0, deleted INT NOT NULL DEFAULT 0)");
        }

        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setDbConfig(new GlobalConfig.DbConfig());
        globalConfig.setIdentifierGenerator(new DefaultIdentifierGenerator());
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addInterceptor(
                new MybatisPlusConfig(new com.apex.scaffold.infrastructure.persistence.config.DatabaseProperties())
                        .mybatisPlusInterceptor(new MdcDataScopeProvider()));
        configuration.addMapper(UserMapper.class);
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @BeforeEach
    void setUpRepository() throws Exception {
        sqlSession = sqlSessionFactory.openSession(true);
        UserPoConverter converter = Mappers.getMapper(UserPoConverter.class);
        repository = new UserRepositoryImpl(sqlSession.getMapper(UserMapper.class), converter, null);
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE sys_user");
            statement.execute("INSERT INTO sys_user "
                    + "(id, open_id, nick_name, dept_id, status, create_time, update_time, version, deleted) VALUES "
                    + "(1, 'open-1', 'user-1', 100, 1, '2026-01-01 00:00:00', '2026-01-01 00:00:00', 0, 0),"
                    + "(2, 'open-2', 'user-2', 100, 1, '2026-01-01 00:00:00', '2026-01-01 00:00:00', 0, 0),"
                    + "(3, 'open-3', 'user-3', 200, 1, '2026-01-01 00:00:00', '2026-01-01 00:00:00', 0, 0),"
                    + "(4, 'open-4', 'user-4', 200, 1, '2026-01-01 00:00:00', '2026-01-01 00:00:00', 0, 0),"
                    + "(5, 'open-5', 'user-5', 200, 1, '2026-01-01 00:00:00', '2026-01-01 00:00:00', 0, 0),"
                    + "(6, 'deleted', 'deleted', 100, 1, '2026-01-01 00:00:00', '2026-01-01 00:00:00', 0, 1)");
        }
    }

    @AfterEach
    void closeSession() {
        DataScopeContext.clear();
        if (sqlSession != null) {
            sqlSession.close();
        }
    }

    @AfterAll
    static void tearDownFactory() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void shouldRejectPlaintextBeforeSingleAndBatchPersistence() {
        UserEntity user = new UserEntity();
        user.setPassword("plaintext-secret");
        StepVerifier.create(repository.save(user))
                .expectError(IllegalArgumentException.class)
                .verify();
        StepVerifier.create(repository.saveAll(java.util.List.of(user)))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void shouldPreserveHashAndExcludeCredentialsFromObjectLogs() {
        UserEntity user = repository.findById(1L).block();
        String hash = com.apex.scaffold.common.util.PasswordUtil.encryptPassword("P@ssword123");
        user.setPassword(hash);
        user.setAccessToken("private-access-token");
        user.setRefreshToken("private-refresh-token");
        UserEntity saved = repository.save(user).block();
        assertThat(saved.getPassword()).isEqualTo(hash);
        assertThat(repository.findById(1L).block().getPassword()).isEqualTo(hash);
        assertThat(user.toString()).doesNotContain(hash, "private-access-token", "private-refresh-token");
        assertThat(Mappers.getMapper(UserPoConverter.class).toPo(user).toString())
                .doesNotContain(hash, "private-access-token", "private-refresh-token");
    }

    @Test
    void paginatesWithMetadataStableIdOrderAndSoftDeleteFiltering() {
        PageResponse<UserEntity> first =
                repository.findPage(1, 2, "create_time", false).block();
        PageResponse<UserEntity> second =
                repository.findPage(2, 2, "create_time", false).block();

        assertThat(first).isNotNull();
        assertThat(first.getList()).extracting(UserEntity::getId).containsExactly(5L, 4L);
        assertThat(first.getTotal()).isEqualTo(5L);
        assertThat(first.getPages()).isEqualTo(3);
        assertThat(first.getHasNext()).isTrue();
        assertThat(second).isNotNull();
        assertThat(second.getList()).extracting(UserEntity::getId).containsExactly(3L, 2L);
        assertThat(second.getList()).extracting(UserEntity::getId).doesNotContain(6L);
    }

    @Test
    void protectedAdministrationSearchFailsWithoutScope() {
        StepVerifier.create(repository.searchForAdministration(null, null, 1, 10))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(IllegalStateException.class);
                    assertThat(error).hasMessage("Required data scope is missing from Reactor Context");
                })
                .verify();
    }

    @Test
    void protectedAdministrationSearchBridgesScopeToJdbcSchedulerWithoutLeak() {
        DataScopeContext.Scope scope =
                new DataScopeContext.Scope(DataScopeInfo.ofDept(100L), DataScopeConfig.defaults());

        Mono<PageResponse<UserEntity>> search = repository
                .searchForAdministration(null, null, 1, 10)
                .contextWrite(context -> context.put(DataScopeJdbcBridge.CONTEXT_KEY, scope));

        StepVerifier.create(search)
                .assertNext(result -> {
                    assertThat(result.getList()).extracting(UserEntity::getId).containsExactlyInAnyOrder(1L, 2L);
                    assertThat(result.getTotal()).isEqualTo(2L);
                })
                .verifyComplete();

        assertThat(DataScopeContext.currentScope()).isEmpty();
        Boolean jdbcThreadLeaked = Mono.fromCallable(
                        () -> DataScopeContext.currentScope().isPresent())
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .block();
        assertThat(jdbcThreadLeaked).isFalse();
    }
}

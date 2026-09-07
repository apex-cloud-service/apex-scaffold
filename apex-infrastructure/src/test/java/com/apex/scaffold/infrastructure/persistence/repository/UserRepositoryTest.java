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
        String host = System.getProperty("DB_MASTER_HOST", "127.0.0.1");
        String port = System.getProperty("DB_MASTER_PORT", "3306");
        String user = System.getProperty("DB_MASTER_USERNAME", "root");
        String pass = System.getProperty("DB_MASTER_PASSWORD", "apex-root");
        String dbName = "apex_unit_userrepository";
        String serverUrl = "jdbc:mysql://" + host + ":" + port
                + "/?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false";
        try (Connection admin = java.sql.DriverManager.getConnection(serverUrl, user, pass);
                Statement create = admin.createStatement()) {
            create.execute("CREATE DATABASE IF NOT EXISTS `" + dbName + "`");
        }
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + dbName
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false");
        dataSource.setUsername(user);
        dataSource.setPassword(pass);
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");

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
        configuration.addInterceptor(new MybatisPlusConfig(new com.apex.scaffold.infrastructure.persistence.config.DatabaseProperties())
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

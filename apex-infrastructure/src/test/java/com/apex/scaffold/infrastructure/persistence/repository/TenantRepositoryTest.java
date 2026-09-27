package com.apex.scaffold.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.domain.model.entity.TenantEntity;
import com.apex.scaffold.domain.model.valueobject.TenantQuota;
import com.apex.scaffold.infrastructure.persistence.converter.TenantPoConverter;
import com.apex.scaffold.infrastructure.persistence.datapermission.DefaultTenantLineHandler;
import com.apex.scaffold.infrastructure.persistence.mapper.TenantMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
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
import reactor.test.StepVerifier;

class TenantRepositoryTest {

    private static SqlSessionFactory sqlSessionFactory;
    private static HikariDataSource dataSource;
    private static TenantPoConverter converter;
    private SqlSession sqlSession;
    private TenantRepositoryImpl repository;

    @BeforeAll
    static void setUpFactory() throws Exception {
        // 使用 H2 内存库（MySQL 兼容模式）替代外部 MySQL，保证无数据库环境下测试自包含运行：
        // - DB_CLOSE_DELAY=-1：连接池关闭连接后数据库保留，随 JVM 生命周期存续；
        // - DATABASE_TO_LOWER/CASE_INSENSITIVE_IDENTIFIERS：与 MySQL 大小写行为对齐。
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:h2:mem:tenant_repo_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE"
                + ";CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setDriverClassName("org.h2.Driver");

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS sys_tenant");
            statement.execute("CREATE TABLE sys_tenant ("
                    + "id BIGINT NOT NULL PRIMARY KEY, "
                    + "tenant_code VARCHAR(64) NOT NULL, "
                    + "tenant_name VARCHAR(128) NOT NULL, "
                    + "admin_user_id VARCHAR(64), "
                    + "package_code VARCHAR(64), "
                    + "expire_time DATETIME, "
                    + "status VARCHAR(32) NOT NULL, "
                    + "max_users INT, "
                    + "max_storage_mb INT, "
                    + "remark VARCHAR(500), "
                    + "create_time DATETIME NOT NULL, "
                    + "update_time DATETIME NOT NULL, "
                    + "version INT NOT NULL DEFAULT 0, "
                    + "deleted INT NOT NULL DEFAULT 0, "
                    + "CONSTRAINT uk_sys_tenant_code UNIQUE (tenant_code))");
        }

        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new DefaultTenantLineHandler()));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setDbConfig(new GlobalConfig.DbConfig());
        globalConfig.setIdentifierGenerator(new DefaultIdentifierGenerator());
        globalConfig.setMetaObjectHandler(
                new com.apex.scaffold.infrastructure.persistence.config.MyMetaObjectHandler());
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addInterceptor(interceptor);
        configuration.addMapper(TenantMapper.class);
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        converter = Mappers.getMapper(TenantPoConverter.class);
    }

    @BeforeEach
    void setUpRepository() throws Exception {
        TenantContext.setTenantId("tenant-a");
        sqlSession = sqlSessionFactory.openSession(true);
        repository = new TenantRepositoryImpl(sqlSession.getMapper(TenantMapper.class), converter);
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE sys_tenant");
        }
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
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
    void shouldReturnEmptyWhenTenantCodeDoesNotExist() {
        StepVerifier.create(repository.findByCode("missing")).verifyComplete();
    }

    @Test
    void shouldFindTenantById() {
        TenantEntity entity = TenantEntity.create("by-id", "按 ID 查询", "admin", null);
        repository.insert(entity).block();

        StepVerifier.create(repository.findById(entity.getId()))
                .assertNext(tenant -> assertThat(tenant.getTenantCode()).isEqualTo("by-id"))
                .verifyComplete();
    }

    @Test
    void shouldCheckTenantCodeExistenceAndExcludeCurrentTenant() {
        TenantEntity entity = TenantEntity.create("exists", "存在性", "admin", null);
        repository.insert(entity).block();

        assertThat(repository.existsByCode("exists", null).block()).isTrue();
        assertThat(repository.existsByCode("exists", entity.getId()).block()).isFalse();
        assertThat(repository.existsByCode("missing", null).block()).isFalse();
    }

    @Test
    void shouldFindPlatformTenantRegardlessOfCurrentTenant() {
        TenantEntity entity = TenantEntity.create("platform-a", "平台租户", "admin", TenantQuota.of(10, 100));
        repository.insert(entity).block();

        TenantContext.setTenantId("tenant-b");

        StepVerifier.create(repository.findByCode("platform-a"))
                .assertNext(tenant -> assertThat(tenant.getTenantCode()).isEqualTo("platform-a"))
                .verifyComplete();
    }

    @Test
    void shouldRejectDuplicateTenantCode() {
        TenantEntity first = TenantEntity.create("duplicate", "第一个", "admin", TenantQuota.unlimited());
        TenantEntity second = TenantEntity.create("duplicate", "第二个", "admin", TenantQuota.unlimited());

        assertThat(repository.insert(first).block()).isOne();
        StepVerifier.create(repository.insert(second)).expectError().verify();
    }

    @Test
    void shouldPersistStatusAndIncrementVersionOnUpdate() {
        TenantEntity entity = TenantEntity.create("lifecycle", "生命周期", "admin", TenantQuota.unlimited());
        repository.insert(entity).block();
        Integer initialVersion = entity.getLockVersion();

        entity.disable();
        assertThat(repository.updateById(entity).block()).isOne();

        TenantEntity stored = repository.findByCode("lifecycle").block();
        assertThat(stored.getStatus()).isEqualTo(TenantEntity.TenantStatus.DISABLED);
        assertThat(stored.getLockVersion()).isGreaterThan(initialVersion);
    }

    @Test
    void shouldReturnZeroWhenUpdatingWithStaleVersion() {
        TenantEntity entity = TenantEntity.create("optimistic", "乐观锁", "admin", null);
        repository.insert(entity).block();
        TenantEntity stale = repository.findById(entity.getId()).block();

        entity.disable();
        assertThat(repository.updateById(entity).block()).isOne();

        stale.disable();
        assertThat(repository.updateById(stale).block()).isZero();
    }

    @Test
    void shouldSoftDeleteTenant() throws Exception {
        TenantEntity entity = TenantEntity.create("deleted", "待删除", "admin", TenantQuota.unlimited());
        repository.insert(entity).block();

        assertThat(repository.deleteById(entity.getId()).block()).isOne();
        assertThat(repository.findByCode("deleted").block()).isNull();
        assertThat(rawDeletedValue(entity.getId())).isEqualTo(1);
    }

    @Test
    void shouldReturnZeroWhenDeletingAlreadyDeletedTenant() {
        TenantEntity entity = TenantEntity.create("delete-twice", "重复删除", "admin", null);
        repository.insert(entity).block();

        assertThat(repository.deleteById(entity.getId()).block()).isOne();
        assertThat(repository.deleteById(entity.getId()).block()).isZero();
        StepVerifier.create(repository.findById(entity.getId())).verifyComplete();
    }

    @Test
    void shouldOrderAllTenantsByCode() {
        repository.insert(TenantEntity.create("tenant-z", "Z", "admin", null)).block();
        repository.insert(TenantEntity.create("tenant-a", "A", "admin", null)).block();

        List<TenantEntity> tenants = repository.findAll().block();

        assertThat(tenants).extracting(TenantEntity::getTenantCode).containsExactly("tenant-a", "tenant-z");
    }

    private int rawDeletedValue(Long id) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                var resultSet = statement.executeQuery("SELECT deleted FROM sys_tenant WHERE id = " + id)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt(1);
        }
    }
}

package com.apex.scaffold.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.domain.model.entity.DeptEntity;
import com.apex.scaffold.infrastructure.persistence.config.MyMetaObjectHandler;
import com.apex.scaffold.infrastructure.persistence.datapermission.DefaultTenantLineHandler;
import com.apex.scaffold.infrastructure.persistence.mapper.DeptMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
import org.springframework.dao.OptimisticLockingFailureException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DeptRepositoryTest {

    private static SqlSessionFactory sqlSessionFactory;
    private static HikariDataSource dataSource;
    private SqlSession sqlSession;
    private DeptRepositoryImpl repository;

    @BeforeAll
    static void setUpFactory() throws Exception {
        // 使用 H2 内存库（MySQL 兼容模式）替代外部 MySQL，保证无数据库环境下测试自包含运行：
        // - DB_CLOSE_DELAY=-1：连接池关闭连接后数据库保留，随 JVM 生命周期存续；
        // - DATABASE_TO_LOWER/CASE_INSENSITIVE_IDENTIFIERS：与 MySQL 大小写行为对齐。
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:h2:mem:dept_repo_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE"
                + ";CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setDriverClassName("org.h2.Driver");

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS sys_dept");
            statement.execute("CREATE TABLE sys_dept ("
                    + "id BIGINT PRIMARY KEY, "
                    + "tenant_id VARCHAR(64) NOT NULL, "
                    + "parent_id BIGINT NOT NULL, "
                    + "ancestors VARCHAR(500) NOT NULL, "
                    + "name VARCHAR(64) NOT NULL, "
                    + "order_num INT NOT NULL, "
                    + "leader VARCHAR(64), "
                    + "phone VARCHAR(32), "
                    + "email VARCHAR(128), "
                    + "status INT NOT NULL, "
                    + "create_time TIMESTAMP NOT NULL, "
                    + "update_time TIMESTAMP NOT NULL, "
                    + "version INT NOT NULL DEFAULT 0, "
                    + "deleted INT NOT NULL DEFAULT 0)");
        }

        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new DefaultTenantLineHandler()));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setDbConfig(new GlobalConfig.DbConfig());
        globalConfig.setIdentifierGenerator(new DefaultIdentifierGenerator());
        globalConfig.setMetaObjectHandler(new MyMetaObjectHandler());
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addInterceptor(interceptor);
        configuration.addMapper(DeptMapper.class);
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @BeforeEach
    void setUpRepository() throws Exception {
        sqlSession = sqlSessionFactory.openSession(true);
        repository = new DeptRepositoryImpl(sqlSession.getMapper(DeptMapper.class));
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE sys_dept");
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
    void shouldReturnGeneratedAndFilledFieldsAfterInsert() {
        StepVerifier.create(withTenant(repository.save(newDepartment("研发部")), "tenant-a"))
                .assertNext(saved -> {
                    assertThat(saved.getId()).isNotNull();
                    assertThat(saved.getTenantId()).isEqualTo("tenant-a");
                    assertThat(saved.getCreateTime()).isNotNull();
                    assertThat(saved.getUpdateTime()).isNotNull();
                    assertThat(saved.getLockVersion()).isZero();
                    assertThat(saved.getDeleted()).isZero();
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnIncrementedVersionAfterUpdate() {
        DeptEntity saved =
                withTenant(repository.save(newDepartment("研发部")), "tenant-a").block();
        saved.setName("研发中心");

        StepVerifier.create(withTenant(repository.save(saved), "tenant-a"))
                .assertNext(updated -> {
                    assertThat(updated.getName()).isEqualTo("研发中心");
                    assertThat(updated.getLockVersion()).isOne();
                })
                .verifyComplete();
    }

    @Test
    void shouldTranslateStaleUpdateToOptimisticLockingFailure() {
        DeptEntity saved =
                withTenant(repository.save(newDepartment("研发部")), "tenant-a").block();
        DeptEntity stale =
                withTenant(repository.findById(saved.getId()), "tenant-a").block();
        saved.setName("研发中心");
        withTenant(repository.save(saved), "tenant-a").block();
        stale.setName("陈旧名称");

        StepVerifier.create(withTenant(repository.save(stale), "tenant-a"))
                .expectError(OptimisticLockingFailureException.class)
                .verify();
    }

    @Test
    void shouldRejectCrossTenantUpdateAndDelete() {
        DeptEntity saved =
                withTenant(repository.save(newDepartment("研发部")), "tenant-a").block();
        saved.setName("越权修改");

        StepVerifier.create(withTenant(repository.save(saved), "tenant-b"))
                .expectError(OptimisticLockingFailureException.class)
                .verify();
        StepVerifier.create(withTenant(repository.deleteById(saved.getId()), "tenant-b"))
                .expectNext(false)
                .verifyComplete();
        StepVerifier.create(withTenant(repository.findById(saved.getId()), "tenant-a"))
                .assertNext(stored -> assertThat(stored.getName()).isEqualTo("研发部"))
                .verifyComplete();
    }

    @Test
    void shouldHideLogicallyDeletedDepartment() throws Exception {
        DeptEntity saved =
                withTenant(repository.save(newDepartment("研发部")), "tenant-a").block();

        StepVerifier.create(withTenant(repository.deleteById(saved.getId()), "tenant-a"))
                .expectNext(true)
                .verifyComplete();
        StepVerifier.create(withTenant(repository.findById(saved.getId()), "tenant-a"))
                .verifyComplete();
        assertThat(rawDeletedValue(saved.getId())).isOne();
    }

    private DeptEntity newDepartment(String name) {
        DeptEntity department = new DeptEntity();
        department.setParentId(0L);
        department.setAncestors("0");
        department.setName(name);
        department.setOrderNum(1);
        department.setStatus(1);
        return department;
    }

    private <T> Mono<T> withTenant(Mono<T> publisher, String tenantId) {
        return TenantContext.withTenant(publisher).contextWrite(TenantContext.withTenant(tenantId));
    }

    private int rawDeletedValue(Long id) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT deleted FROM sys_dept WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getInt(1);
            }
        }
    }
}

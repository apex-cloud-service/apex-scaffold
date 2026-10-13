package com.apex.scaffold.interfaces.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.assembler.AdminUserAssembler;
import com.apex.scaffold.application.factory.UserDefaultsFactory;
import com.apex.scaffold.application.service.AdminUserManagementService;
import com.apex.scaffold.application.service.ProfileService;
import com.apex.scaffold.common.context.DataScopeContext;
import com.apex.scaffold.common.context.DataScopeProvider;
import com.apex.scaffold.common.context.ReactiveDataScopeProvider;
import com.apex.scaffold.common.enums.DataScopeType;
import com.apex.scaffold.domain.model.entity.DeptEntity;
import com.apex.scaffold.domain.model.entity.RoleEntity;
import com.apex.scaffold.domain.repository.DeptRepository;
import com.apex.scaffold.domain.repository.RoleRepository;
import com.apex.scaffold.domain.repository.UserComplianceRepository;
import com.apex.scaffold.domain.repository.UserProfileRepository;
import com.apex.scaffold.domain.repository.UserRoleRepository;
import com.apex.scaffold.domain.service.AccountDeletionPort;
import com.apex.scaffold.domain.service.CacheConsistencyPort;
import com.apex.scaffold.domain.service.TokenBlacklistService;
import com.apex.scaffold.domain.service.TokenService;
import com.apex.scaffold.infrastructure.persistence.config.MybatisPlusConfig;
import com.apex.scaffold.infrastructure.persistence.converter.UserPoConverter;
import com.apex.scaffold.infrastructure.persistence.datapermission.MdcDataScopeProvider;
import com.apex.scaffold.infrastructure.persistence.mapper.UserMapper;
import com.apex.scaffold.infrastructure.persistence.repository.UserRepositoryImpl;
import com.apex.scaffold.interfaces.security.AuthenticatedUser;
import com.apex.scaffold.interfaces.security.SecurityContextDataScopeProvider;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
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
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DataScopeServiceTest {

    private static HikariDataSource dataSource;
    private static SqlSessionFactory sqlSessionFactory;

    private AnnotationConfigApplicationContext context;
    private SqlSession sqlSession;
    private AdminUserManagementService service;

    @BeforeAll
    static void setUpFactory() throws Exception {
        // 使用 H2 内存库（MySQL 兼容模式）替代外部 MySQL，保证无数据库环境下测试可运行：
        // - DB_CLOSE_DELAY=-1：连接池关闭连接后数据库保留，随 JVM 生命周期存续；
        // - DATABASE_TO_LOWER/CASE_INSENSITIVE_IDENTIFIERS：与 MySQL 大小写行为对齐。
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:h2:mem:datascope;MODE=MySQL;DATABASE_TO_LOWER=TRUE"
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
                    + "status INT, onboarding_status VARCHAR(64), register_channel VARCHAR(32), last_login_time DATETIME, "
                    + "last_login_ip VARCHAR(64), last_heartbeat_time DATETIME, create_time DATETIME NOT NULL, "
                    + "update_time DATETIME NOT NULL, version INT NOT NULL DEFAULT 0, deleted INT NOT NULL DEFAULT 0)");
        }

        DataScopeProvider dataScopeProvider = new MdcDataScopeProvider();
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setDbConfig(new GlobalConfig.DbConfig());
        globalConfig.setIdentifierGenerator(new DefaultIdentifierGenerator());
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addInterceptor(
                new MybatisPlusConfig(new com.apex.scaffold.infrastructure.persistence.config.DatabaseProperties())
                        .mybatisPlusInterceptor(dataScopeProvider));
        configuration.addMapper(UserMapper.class);
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @BeforeEach
    void setUp() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE sys_user");
            statement.execute("INSERT INTO sys_user "
                    + "(id, open_id, nick_name, dept_id, status, create_time, update_time, version, deleted) VALUES "
                    + "(99, 'operator', 'operator', 100, 1, '2026-01-01 00:00:00', '2026-01-01 00:00:00', 0, 0),"
                    + "(1, 'open-1', 'user-1', 100, 1, '2026-01-01 00:00:00', '2026-01-01 00:00:00', 0, 0),"
                    + "(2, 'open-2', 'user-2', 100, 1, '2026-01-01 00:00:00', '2026-01-01 00:00:00', 0, 0),"
                    + "(3, 'open-3', 'user-3', 200, 1, '2026-01-01 00:00:00', '2026-01-01 00:00:00', 0, 0),"
                    + "(4, 'deleted', 'deleted', 100, 1, '2026-01-01 00:00:00', '2026-01-01 00:00:00', 0, 1)");
        }

        sqlSession = sqlSessionFactory.openSession(true);
        UserRepositoryImpl userRepository = new UserRepositoryImpl(
                sqlSession.getMapper(UserMapper.class), Mappers.getMapper(UserPoConverter.class), null);
        RoleRepository roleRepository = mock(RoleRepository.class);
        DeptRepository deptRepository = mock(DeptRepository.class);
        RoleEntity superAdmin = RoleEntity.create("Super Admin", "SUPER_ADMIN", "operator");
        superAdmin.setDataScope(DataScopeType.DEPT);
        when(roleRepository.findByUserId(99L)).thenReturn(Mono.just(List.of(superAdmin)));
        when(roleRepository.findByUserId(anyLong())).thenReturn(Mono.just(List.of()));
        when(roleRepository.findByUserId(99L)).thenReturn(Mono.just(List.of(superAdmin)));
        DeptEntity department = new DeptEntity();
        department.setId(100L);
        department.setAncestors("0");
        department.setStatus(1);
        when(deptRepository.findById(100L)).thenReturn(Mono.just(department));

        context = new AnnotationConfigApplicationContext();
        context.register(AopConfiguration.class);
        context.registerBean(DataScopeProvider.class, MdcDataScopeProvider::new);
        context.registerBean(RoleRepository.class, () -> roleRepository);
        context.registerBean(DeptRepository.class, () -> deptRepository);
        context.registerBean(
                ReactiveDataScopeProvider.class,
                () -> new SecurityContextDataScopeProvider(roleRepository, deptRepository, null));
        context.registerBean(
                DataScopeAspect.class,
                () -> new DataScopeAspect(
                        context.getBean(DataScopeProvider.class), context.getBean(ReactiveDataScopeProvider.class)));
        context.registerBean(
                AdminUserManagementService.class,
                () -> new AdminUserManagementService(
                        userRepository,
                        roleRepository,
                        mock(UserRoleRepository.class),
                        mock(TokenService.class),
                        mock(TokenBlacklistService.class),
                        mock(ProfileService.class),
                        mock(UserProfileRepository.class),
                        mock(UserComplianceRepository.class),
                        mock(UserDefaultsFactory.class),
                        mock(CacheConsistencyPort.class),
                        mock(AccountDeletionPort.class),
                        mock(AdminUserAssembler.class)));
        context.refresh();
        service = context.getBean(AdminUserManagementService.class);
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
        if (context != null) {
            context.close();
        }
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
    void filtersRealAdministrationQueryUsingScopeResolvedFromSecurityContext() {
        AuthenticatedUser principal = new AuthenticatedUser(99L, "operator", "tenant-a", 100L);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, List.of());

        StepVerifier.create(service.search("operator", null, null, 1, 10)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
                .assertNext(page -> {
                    assertThat(page.getList()).extracting(item -> item.id()).containsExactlyInAnyOrder(99L, 1L, 2L);
                    assertThat(page.getTotal()).isEqualTo(3L);
                })
                .verifyComplete();
    }

    @EnableAspectJAutoProxy
    static class AopConfiguration {}
}

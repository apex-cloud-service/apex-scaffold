package com.apex.example.integration;

import com.apex.example.repository.ExampleUserMapper;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.MySQLContainer;

/**
 * 集成测试基类
 *
 * <p>提供通用的测试配置和WebTestClient实例。
 * 每个测试前自动清理非种子数据，保证测试隔离性。
 *
 * @author Apex Team
 * @version 1.0.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.profiles.active=test")
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@Import(ExampleTestMockConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
public abstract class BaseIntegrationTest {

    // 同一测试 JVM 共享独立容器，由 Testcontainers 在进程退出时回收。
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:9.0")
            .withDatabaseName("apex_example_test")
            .withUsername("example_test")
            .withPassword("example_test")
            .withUrlParam("connectTimeout", "5000")
            .withUrlParam("socketTimeout", "10000")
            .withStartupTimeout(Duration.ofSeconds(90))
            .withConnectTimeoutSeconds(5)
            .withStartupAttempts(1);

    /** 将主从数据源绑定到同一隔离测试库，不读取开发或生产数据库环境变量。 */
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) throws SQLException {
        MYSQL.start();
        // Flyway 读取会话变量需要此单表查询权限，仅授权当前一次性测试容器内的账号。
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("GRANT SELECT ON performance_schema.user_variables_by_thread TO 'example_test'@'%'");
        }
        for (String name : new String[] {"master", "slave"}) {
            String prefix = "spring.datasource.dynamic.datasource." + name;
            registry.add(prefix + ".url", MYSQL::getJdbcUrl);
            registry.add(prefix + ".username", MYSQL::getUsername);
            registry.add(prefix + ".password", MYSQL::getPassword);
        }
    }

    protected final WebTestClient webTestClient;

    private final ExampleUserMapper exampleUserMapper;

    /**
     * 构造器注入共享的测试依赖（Spring Boot 测试框架自动解析构造器参数）。
     *
     * @param webTestClient     响应式 Web 测试客户端
     * @param exampleUserMapper 示例用户 Mapper，用于测试数据清理
     */
    public BaseIntegrationTest(WebTestClient webTestClient, ExampleUserMapper exampleUserMapper) {
        this.webTestClient = webTestClient;
        this.exampleUserMapper = exampleUserMapper;
    }

    /**
     * 每个测试前的清理工作：删除非种子数据，保留 V1 迁移写入的固定 ID 种子用户
     * （id=1,2,3: zhangsan/lisi/wangwu），保证测试可重复运行且互不干扰。
     */
    @BeforeEach
    void setUp() {
        // 清理测试过程中创建的用户（保留种子数据 id <= 3）
        exampleUserMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                        com.apex.example.model.ExampleUser>()
                .gt(com.apex.example.model.ExampleUser::getId, 3L));
    }
}

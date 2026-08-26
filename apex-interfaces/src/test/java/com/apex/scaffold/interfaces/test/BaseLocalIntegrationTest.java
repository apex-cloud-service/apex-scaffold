package com.apex.scaffold.interfaces.test;

import com.apex.scaffold.Application;
import java.time.Duration;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.netty.http.client.HttpClient;

/**
 * 集成测试基类（远程服务模式）
 *
 * <p>使用远程 MySQL + Redis（通过环境变量注入地址），无需 Docker 或嵌入式服务。
 * 该基类只用于显式启用的远程环境联调，默认构建不会连接或修改共享测试环境。
 * 当本地环境中 Docker 不可用且已准备隔离的远程测试库时，集成测试可继承此类。
 *
 * <p>提供与 {@link BaseIntegrationTest} 相同的工具方法：
 * <ul>
 *   <li>{@link TestTokenHelper} — JWT Token 生成</li>
 *   <li>{@link TestDataFactory} — 测试数据常量</li>
 *   <li>{@link TestMockHelper} — 认证辅助和响应解析</li>
 * </ul>
 *
 * @author Apex Team
 */
@SpringBootTest(classes = Application.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it-local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
public abstract class BaseLocalIntegrationTest {

    // ==================== JWT 密钥同步（静态初始化，在 Spring 上下文加载前执行） ====================

    static {
        // 将 TestTokenHelper 的 JWT 密钥同步到 System Property，确保 JwtUtil.getActualSecret()
        // 在 Spring 上下文加载和 JWT 验证时使用与测试 Token 生成一致的密钥
        System.setProperty("jwt.secret", TestTokenHelper.TEST_JWT_SECRET);
    }

    // ==================== 测试数据常量（委托至 TestDataFactory） ====================

    protected static final String TEST_OPEN_ID = TestDataFactory.TEST_OPEN_ID;
    protected static final String TEST_NICK_NAME = TestDataFactory.TEST_NICK_NAME;
    protected static final String TEST_AVATAR_URL = TestDataFactory.TEST_AVATAR_URL;

    // ==================== Spring 注入 ====================

    @LocalServerPort
    private int port;

    protected WebTestClient webTestClient;

    private final DataSource dataSource;

    /**
     * 构造器注入依赖
     *
     * @param dataSource 数据源
     */
    public BaseLocalIntegrationTest(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // ==================== Mock 辅助（实例化委托） ====================

    protected TestMockHelper mockHelper;

    // ==================== 生命周期回调 ====================

    /**
     * 设置{@code upBase}。
     */
    @BeforeAll
    void setUpBase() {
        initSchema();
        // 使用独立 HttpClient（禁用 KeepAlive 避免连接池过期）
        var httpClient = HttpClient.create().keepAlive(false).responseTimeout(Duration.ofSeconds(60));
        webTestClient = WebTestClient.bindToServer()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl("http://localhost:" + port)
                .build();
        mockHelper = new TestMockHelper(webTestClient);
    }

    /**
     * 初始化远程数据库表结构（添加 Flyway 迁移未应用的 DDL）并插入测试数据
     */
    private void initSchema() {
        try (var conn = dataSource.getConnection();
                var stmt = conn.createStatement()) {
            // 1. 添加缺失的列（幂等：检查列是否存在）
            var rs = stmt.executeQuery("SELECT COUNT(*) FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA = (SELECT DATABASE()) AND TABLE_NAME = 'sys_user' "
                    + "AND COLUMN_NAME = 'last_heartbeat_time'");
            rs.next();
            if (rs.getInt(1) == 0) {
                stmt.execute("ALTER TABLE sys_user ADD COLUMN last_heartbeat_time "
                        + "DATETIME DEFAULT NULL COMMENT '最后心跳时间(用于在线判定)' "
                        + "AFTER last_login_ip");
            }

            // 2. 插入测试用户（幂等：检查 open_id 是否已存在）
            var userRs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM sys_user WHERE open_id = '" + TestDataFactory.TEST_OPEN_ID + "'");
            userRs.next();
            if (userRs.getInt(1) == 0) {
                stmt.execute("INSERT INTO sys_user (id, open_id, nick_name, status, create_time, update_time) "
                        + "VALUES (1, '"
                        + TestDataFactory.TEST_OPEN_ID + "', " + "'"
                        + TestDataFactory.TEST_NICK_NAME + "', 1, NOW(), NOW())");
            }

            // 3. 插入测试所需的权限（幂等）
            stmt.execute("INSERT IGNORE INTO sys_permission (id, name, code, description) "
                    + "VALUES (5, '用户查看', 'user:view', '查看用户信息权限')");

            // 4. 为用户分配 ROLE_USER 角色（幂等）
            stmt.execute("INSERT IGNORE INTO sys_user_role (id, user_id, role_id, create_time, update_time) "
                    + "VALUES (1, 1, 1, NOW(), NOW())");

            // 5. 将 user:view 权限授予 ROLE_USER（幂等）
            stmt.execute("INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id) " + "VALUES (6, 1, 5)");
        } catch (Exception e) {
            throw new RuntimeException("数据库 Schema 初始化失败", e);
        }
    }

    // ==================== Token 方法（委托至 TestTokenHelper） ====================

    /**
     * 生成测试用 JWT accessToken
     *
     * @param openId  用户 OpenID
     * @param minutes 过期时间（分钟）
     * @return JWT Token 字符串
     */
    protected String generateAccessToken(String openId, int minutes) {
        return TestTokenHelper.generateAccessToken(openId, minutes);
    }

    /**
     * 生成测试用 JWT refreshToken
     *
     * @param openId  用户 OpenID
     * @param minutes 过期时间（分钟）
     * @return JWT Token 字符串
     */
    protected String generateRefreshToken(String openId, int minutes) {
        return TestTokenHelper.generateRefreshToken(openId, minutes);
    }

    /**
     * 生成已过期的 JWT accessToken
     *
     * @param openId 用户 OpenID
     * @return 已过期的 JWT Token 字符串
     */
    protected String generateExpiredToken(String openId) {
        return TestTokenHelper.generateExpiredToken(openId);
    }

    // ==================== WebTestClient 辅助方法（委托至 TestMockHelper） ====================

    /**
     * 获取带认证头的 WebTestClient 构建器
     *
     * @param token JWT Token
     * @return WebTestClient.RequestHeadersSpec 构建器
     */
    protected WebTestClient.RequestHeadersSpec<?> withAuth(String token) {
        return mockHelper.withAuth(token);
    }

    /**
     * 解析响应体中的 Result 对象
     *
     * @param responseBody 原始响应体字符串
     * @return 解析后的 Result 对象
     */
    protected Map<String, Object> parseResult(String responseBody) {
        return TestMockHelper.parseResult(responseBody);
    }
}

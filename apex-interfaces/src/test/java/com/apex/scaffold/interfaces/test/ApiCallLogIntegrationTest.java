package com.apex.scaffold.interfaces.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.apex.scaffold.domain.model.entity.ApiCallLogEntity;
import com.apex.scaffold.domain.repository.ApiCallLogRepository;
import com.apex.scaffold.infrastructure.remote.filter.ApiCallLogFilter;
import com.apex.scaffold.infrastructure.remote.logging.ApiCallLogProperties;
import com.apex.scaffold.infrastructure.remote.logging.ApiCallLogService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

/**
 * API 调用日志集成测试。
 *
 * <p>验证 API 调用日志功能在 Spring 容器中的端到端行为：
 * 从日志实体构建、脱敏处理到数据库持久化的完整链路。</p>
 *
 * <h2>测试范围</h2>
 * <ul>
 *   <li><b>数据库表存在性</b> — 验证 Flyway 迁移或 SQL 初始化已创建 {@code sys_api_call_log} 表</li>
 *   <li><b>Bean 装配正确性</b> — 验证 {@link ApiCallLogService}、{@link ApiCallLogProperties}、
 *       {@link ApiCallLogRepository} 均已正确注入</li>
 *   <li><b>日志写入链路</b> — 通过 {@link ApiCallLogRepository#save(ApiCallLogEntity)}
 *       直接写入一条日志并回读验证</li>
 *   <li><b>脱敏处理正确性</b> — 验证敏感请求头在写入数据库后被正确脱敏</li>
 * </ul>
 *
 * <h2>运行方式</h2>
 * <p>使用本地 MySQL（profile {@code it}，apex_test 库），默认随单元测试运行。
 * 不需要外部服务或特定系统属性。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see ApiCallLogEntity
 * @see ApiCallLogService
 * @see ApiCallLogRepository
 */
@SpringBootTest(classes = com.apex.scaffold.Application.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Import(ItProfileMockRedisConfiguration.class)
@DisplayName("API调用日志集成测试")
class ApiCallLogIntegrationTest {

    /** 当前测试服务器随机端口。 */
    @LocalServerPort
    private int port;

    /** JDBC 模板，用于直接操作和验证数据库。 */
    private final JdbcTemplate jdbcTemplate;

    /** API 调用日志领域仓储。 */
    private final ApiCallLogRepository apiCallLogRepository;

    /** API 调用日志服务（含脱敏和截断逻辑）。 */
    private final ApiCallLogService apiCallLogService;

    /** API 调用日志配置属性。 */
    private final ApiCallLogProperties apiCallLogProperties;

    /**
     * 构造器注入所有测试依赖。
     *
     * @param jdbcTemplate JDBC 模板
     * @param apiCallLogRepository API 调用日志仓储
     * @param apiCallLogService API 调用日志服务
     * @param apiCallLogProperties API 调用日志配置
     */
    public ApiCallLogIntegrationTest(
            JdbcTemplate jdbcTemplate,
            ApiCallLogRepository apiCallLogRepository,
            ApiCallLogService apiCallLogService,
            ApiCallLogProperties apiCallLogProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.apiCallLogRepository = apiCallLogRepository;
        this.apiCallLogService = apiCallLogService;
        this.apiCallLogProperties = apiCallLogProperties;
    }

    /**
     * 每个测试前清空 API 调用日志表。
     *
     * <p>it 环境使用真实 MySQL（持久库），多次运行/多个嵌套测试
     * 之间 sys_api_call_log 会残留数据，导致固定 ID 插入冲突（DuplicateKey）与
     * 固定条件查询返回多条（expected 1 actual N）。该表为测试专用写入表，
     * 清空不影响种子数据。</p>
     */
    @BeforeEach
    void cleanApiCallLogTable() {
        jdbcTemplate.update("DELETE FROM sys_api_call_log");
    }

    // ==================== 基础设施验证 ====================

    /**
     * 数据库和 Bean 基础设施验证。
     */
    @Nested
    @DisplayName("基础设施验证")
    class InfrastructureTests {

        /**
         * 验证 Flyway 迁移或 SQL 初始化已创建 sys_api_call_log 表。
         *
         * <p>通过尝试写入并查询一条记录来间接验证表的存在性，
         * 而非依赖 INFORMATION_SCHEMA 查询（不同 MySQL 版本元数据可能不一致）。</p>
         *
         */
        @Test
        @DisplayName("sys_api_call_log 表应存在于 MySQL 测试库中")
        void shouldHaveApiCallLogTableInMySqlTestDb() {
            // 通过写入验证表存在
            jdbcTemplate.execute(
                    "INSERT INTO sys_api_call_log (id, service_name, method, path, url, status, create_time, update_time) "
                            + "VALUES (9999999999, 'table-check', 'GET', '/check', 'http://check', 0, NOW(), NOW())");
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_api_call_log WHERE service_name = 'table-check'", Integer.class);

            assertNotNull(count, "查询结果不应为 null");
            assertThat(count).as("sys_api_call_log 表应存在且可写入（表检查记录已插入）").isEqualTo(1);
        }

        /**
         * 验证 sys_api_call_log 表与 sys_user 表同时存在（确保 SQL 初始化完整执行）。
         *
         */
        @Test
        @DisplayName("SQL 初始化应完整执行（sys_api_call_log 和 sys_user 同时存在）")
        void shouldHaveAllCoreTables() {
            // 验证 sys_user 表存在
            Integer userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class);
            assertNotNull(userCount, "sys_user 表应存在");

            // 验证 sys_api_call_log 表存在（通过写入和查询验证）
            Integer logCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_api_call_log", Integer.class);
            assertNotNull(logCount, "sys_api_call_log 表应存在");
        }

        /**
         * 验证 ApiCallLogProperties Bean 已正确加载且默认启用。
         *
         */
        @Test
        @DisplayName("ApiCallLogProperties 应作为 Spring Bean 加载且默认启用")
        void shouldLoadApiCallLogPropertiesBean() {
            assertNotNull(apiCallLogProperties, "ApiCallLogProperties 应已注入");
            assertThat(apiCallLogProperties.isEnabled()).as("API 调用日志默认应启用").isTrue();
            assertThat(apiCallLogProperties.getMaxBodyLength())
                    .as("默认 maxBodyLength 应为 4096")
                    .isEqualTo(4096);
        }

        /**
         * 验证 ApiCallLogService Bean 已正确注入。
         *
         */
        @Test
        @DisplayName("ApiCallLogService 应已注入且可用")
        void shouldInjectApiCallLogService() {
            assertNotNull(apiCallLogService, "ApiCallLogService 应已注入");
        }

        /**
         * 验证 ApiCallLogRepository Bean 已正确注入。
         *
         */
        @Test
        @DisplayName("ApiCallLogRepository 应已注入且可用")
        void shouldInjectApiCallLogRepository() {
            assertNotNull(apiCallLogRepository, "ApiCallLogRepository 应已注入");
        }
    }

    // ==================== 日志写入链路测试 ====================

    /**
     * 日志写入数据库的端到端验证。
     */
    @Nested
    @DisplayName("日志写入链路测试")
    class LogPersistenceTests {

        /**
         * 通过 ApiCallLogService 写入一条日志并验证数据库中存在对应记录。
         *
         */
        @Test
        @DisplayName("ApiCallLogService.log() 应成功将日志写入数据库")
        void shouldPersistLogToDatabase() {
            // 准备测试日志实体
            ApiCallLogEntity entity = new ApiCallLogEntity();
            entity.setServiceName("integration-test-service");
            entity.setMethod("POST");
            entity.setPath("/api/v1/test-endpoint");
            entity.setUrl("https://example.com/api/v1/test-endpoint?param=value");
            entity.setRequestHeaders("{\"content-type\":\"application/json\"}");
            entity.setRequestBody("{\"testKey\":\"testValue\"}");
            entity.setResponseStatus("200");
            entity.setResponseHeaders("{\"content-type\":\"application/json\"}");
            entity.setResponseBody("{\"result\":\"success\"}");
            entity.setExecutionTime(150L);
            entity.setStatus(0);
            entity.setTraceId("trace-integration-test-001");
            entity.setCallTime(LocalDateTime.now());

            // 执行日志写入
            StepVerifier.create(apiCallLogService.log(entity))
                    .expectSubscription()
                    .verifyComplete();

            // 查询数据库验证写入结果
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_api_call_log "
                            + "WHERE service_name = 'integration-test-service' AND trace_id = 'trace-integration-test-001'",
                    Integer.class);

            assertNotNull(count, "查询结果不应为 null");
            assertThat(count)
                    .as("应有一条 service_name='integration-test-service' 的日志记录")
                    .isGreaterThanOrEqualTo(1);
        }
    }

    // ==================== 脱敏集成验证 ====================

    /**
     * 敏感信息脱敏集成验证。
     */
    @Nested
    @DisplayName("脱敏集成验证")
    class SanitizationIntegrationTests {

        /**
         * 验证含敏感头的日志在写入数据库后已被脱敏。
         *
         */
        @Test
        @DisplayName("含敏感请求头的日志写入 DB 后应已脱敏")
        void shouldSanitizeSensitiveHeadersInDatabase() {
            // 构造含敏感请求头的日志实体
            ApiCallLogEntity entity = new ApiCallLogEntity();
            entity.setServiceName("sanitize-test-service");
            entity.setMethod("GET");
            entity.setPath("/api/v1/sanitize-test");
            entity.setUrl("https://example.com/api/v1/sanitize-test");
            entity.setRequestHeaders("{\"authorization\":\"Bearer secret-token-abc123\","
                    + "\"content-type\":\"application/json\","
                    + "\"apikey\":\"my-sensitive-key-xyz\"}");
            entity.setResponseHeaders("{\"secret\":\"response-secret-value\"}");
            entity.setResponseStatus("200");
            entity.setStatus(0);
            entity.setTraceId("trace-sanitize-test-001");
            entity.setCallTime(LocalDateTime.now());

            // 执行日志写入（会触发脱敏处理）
            StepVerifier.create(apiCallLogService.log(entity)).verifyComplete();

            // 从数据库回读请求头验证脱敏结果
            String dbHeaders = jdbcTemplate.queryForObject(
                    "SELECT request_headers FROM sys_api_call_log " + "WHERE trace_id = 'trace-sanitize-test-001'",
                    String.class);

            assertNotNull(dbHeaders, "应从数据库查询到请求头");
            assertThat(dbHeaders).as("请求头中的敏感 token 不应以明文存储").doesNotContain("secret-token-abc123");
            assertThat(dbHeaders).as("请求头中的敏感 key 不应以明文存储").doesNotContain("my-sensitive-key-xyz");
        }
    }

    // ==================== Filter 端到端测试 ====================

    /**
     * WebClient → ApiCallLogFilter → ApiCallLogService → DB 完整链路验证。
     *
     * <p>通过构建含 {@link ApiCallLogFilter} 的真实 {@link WebClient} 实例，
     * 向测试服务器自身的 {@code /actuator/health} 端点发起 HTTP 请求，
     * 验证 Filter 能正确拦截请求并异步写入日志到数据库。</p>
     *
     * <h3>测试流程</h3>
     * <ol>
     *   <li>构建 WebClient，注册 {@code ApiCallLogFilter.create(apiCallLogService)}</li>
     *   <li>设置 {@code ATTRIBUTE_SERVICE_NAME = "end-to-end-test-service"} 启用日志记录</li>
     *   <li>向测试服务器的随机端口发起 GET 请求</li>
     *   <li>等待异步写入完成（通过 CountDownLatch）</li>
     *   <li>查询数据库验证日志记录存在且字段完整</li>
     * </ol>
     *
     * <h3>覆盖的关键场景</h3>
     * <ul>
     *   <li>Filter 正确识别 {@code ATTRIBUTE_SERVICE_NAME} 属性</li>
     *   <li>请求元数据（method / path / url / traceId / callTime）正确提取</li>
     *   <li>响应元数据（responseStatus / status / executionTime）正确记录</li>
     *   <li>Service 脱敏 + truncate 在 Filter 调用链中被正确触发</li>
     *   <li>异步写入完成后数据真实存在数据库（不是 mock 的 repository）</li>
     * </ul>
     *
     * @author Apex Team
     */
    @Nested
    @DisplayName("Filter端到端完整链路测试")
    class FilterEndToEndTests {

        /** 用于跟踪的 traceId，确保只查询本次测试的日志记录。 */
        private static final String E2E_TRACE_ID = "trace-e2e-filter-test";

        /**
         * 构建 WebClient → Filter 拦截 → Service 脱敏 → DB 写入 的完整链路验证。
         *
         * @throws InterruptedException CountDownLatch 等待可能抛出
         */
        @Test
        @DisplayName("WebClient发起请求 → Filter拦截 → 日志写入数据库")
        void shouldLogViaFilterWhenWebClientCallsTestServer() throws InterruptedException {
            // 1. 构建带 Filter 的 WebClient
            WebClient webClient = WebClient.builder()
                    .baseUrl("http://localhost:" + port)
                    .filter(ApiCallLogFilter.create(apiCallLogService))
                    .build();

            // 2. 使用 CountDownLatch 等待异步日志写入完成
            CountDownLatch responseLatch = new CountDownLatch(1);

            // 3. 向测试服务器的公共端点发起请求（toEntity 不抛异常）
            webClient
                    .get()
                    .uri("/api/v1/health")
                    .attribute(ApiCallLogFilter.ATTRIBUTE_SERVICE_NAME, "end-to-end-test-service")
                    .attribute(ApiCallLogFilter.ATTRIBUTE_REQUEST_BODY, "{\"testKey\":\"end-to-end-verification\"}")
                    .retrieve()
                    .toEntity(String.class)
                    .doFinally(signal -> responseLatch.countDown())
                    .block(Duration.ofSeconds(10));

            // 4. 等待异步日志写入完成
            boolean logged = responseLatch.await(3, TimeUnit.SECONDS);
            Thread.sleep(500);

            // 5. 查询数据库验证日志已写入
            String dbServiceName = jdbcTemplate.queryForObject(
                    "SELECT service_name FROM sys_api_call_log "
                            + "WHERE path = '/api/v1/health' "
                            + "AND service_name = 'end-to-end-test-service' "
                            + "ORDER BY create_time DESC LIMIT 1",
                    String.class);

            assertNotNull(dbServiceName, "Filter 应已将日志写入数据库");
            assertThat(dbServiceName).as("日志记录中的 serviceName 应与 Filter 属性一致").isEqualTo("end-to-end-test-service");
        }

        /**
         * 验证不带 ATTRIBUTE_SERVICE_NAME 属性时不写入日志（按需启用机制）。
         *
         * @throws InterruptedException CountDownLatch 等待可能抛出
         */
        @Test
        @DisplayName("不设置serviceName属性时不写入日志（按需启用机制验证）")
        void shouldNotLogWhenServiceNameNotSet() throws InterruptedException {
            Integer before = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_api_call_log WHERE path = '/api/v1/health'", Integer.class);
            WebClient webClient = WebClient.builder()
                    .baseUrl("http://localhost:" + port)
                    .filter(ApiCallLogFilter.create(apiCallLogService))
                    .build();

            CountDownLatch latch = new CountDownLatch(1);

            webClient
                    .get()
                    .uri("/api/v1/health")
                    // 故意不设置 ATTRIBUTE_SERVICE_NAME
                    .retrieve()
                    .toEntity(String.class)
                    .doFinally(signal -> latch.countDown())
                    .block(Duration.ofSeconds(10));

            latch.await(3, TimeUnit.SECONDS);
            Thread.sleep(500);

            Integer after = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_api_call_log WHERE path = '/api/v1/health'", Integer.class);
            assertThat(after).as("不设置serviceName属性的请求不应产生日志").isEqualTo(before);
        }

        /**
         * 验证异常请求的日志记录（调用一个不存在的端点触发 404）。
         *
         * @throws InterruptedException CountDownLatch 等待可能抛出
         */
        @Test
        @DisplayName("HTTP 4xx 响应应正确记录 status=1")
        void shouldLog4xxStatusCorrectly() throws InterruptedException {
            WebClient webClient = WebClient.builder()
                    .baseUrl("http://localhost:" + port)
                    .filter(ApiCallLogFilter.create(apiCallLogService))
                    .build();

            CountDownLatch latch = new CountDownLatch(1);

            try {
                webClient
                        .get()
                        .uri("/api/v1/non-existent-endpoint-" + System.nanoTime())
                        .attribute(ApiCallLogFilter.ATTRIBUTE_SERVICE_NAME, "e2e-4xx-test")
                        .retrieve()
                        .bodyToMono(String.class)
                        .doFinally(signal -> latch.countDown())
                        .block(Duration.ofSeconds(10));
            } catch (Exception e) {
                // 4xx 响应可能抛出 WebClientResponseException，这是预期行为
                latch.countDown();
            }

            latch.await(3, TimeUnit.SECONDS);
            Thread.sleep(500);

            // 验证数据库中的日志记录
            Integer status = jdbcTemplate.queryForObject(
                    "SELECT status FROM sys_api_call_log "
                            + "WHERE service_name = 'e2e-4xx-test' "
                            + "AND path LIKE '/api/v1/non-existent-endpoint%' "
                            + "ORDER BY create_time DESC LIMIT 1",
                    Integer.class);

            if (status != null) {
                assertThat(status).as("4xx 响应应记录为 status=1（客户端错误）").isEqualTo(1);
            }
        }

        /**
         * 验证日志记录中包含请求方法、路径、耗时和 traceId。
         *
         * @throws InterruptedException CountDownLatch 等待可能抛出
         */
        @Test
        @DisplayName("日志记录应包含完整的请求元数据（method/path/executionTime/traceId/callTime）")
        void shouldContainCompleteMetadataInFilterLog() throws InterruptedException {
            WebClient webClient = WebClient.builder()
                    .baseUrl("http://localhost:" + port)
                    .filter(ApiCallLogFilter.create(apiCallLogService))
                    .build();

            CountDownLatch latch = new CountDownLatch(1);

            webClient
                    .get()
                    .uri("/api/v1/health")
                    .attribute(ApiCallLogFilter.ATTRIBUTE_SERVICE_NAME, "e2e-metadata-test")
                    .attribute(ApiCallLogFilter.ATTRIBUTE_REQUEST_BODY, "{\"metadataCheck\":\"yes\"}")
                    .retrieve()
                    .bodyToMono(String.class)
                    .doFinally(signal -> latch.countDown())
                    .block(Duration.ofSeconds(10));

            latch.await(3, TimeUnit.SECONDS);
            Thread.sleep(500);

            // 查询日志记录，验证所有字段
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_api_call_log "
                            + "WHERE service_name = 'e2e-metadata-test' "
                            + "AND method = 'GET' "
                            + "AND path = '/api/v1/health' "
                            + "AND execution_time IS NOT NULL "
                            + "AND trace_id IS NOT NULL "
                            + "AND call_time IS NOT NULL",
                    Integer.class);

            assertNotNull(count, "应能查到匹配的日志记录");
            assertThat(count)
                    .as("日志记录应包含 method='GET', path='/actuator/health', " + "executionTime, traceId, callTime 全部字段")
                    .isGreaterThanOrEqualTo(1);
        }
    }
}

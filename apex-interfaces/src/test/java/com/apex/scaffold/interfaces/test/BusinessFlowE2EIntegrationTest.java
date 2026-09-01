package com.apex.scaffold.interfaces.test;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.constant.SystemConstants;
import com.apex.scaffold.common.util.JsonUtil;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 关键业务流程端到端（E2E）集成测试。
 *
 * <p>基于 {@link BaseLocalIntegrationTest}（远程依赖联调模式），使用 {@link WebTestClient}
 * 覆盖从 Handler → Service → Repository 的全链路集成测试，
 * 验证系统核心业务流程的端到端功能正确性。</p>
 *
 * <h2>测试覆盖的核心业务场景</h2>
 * <ol>
 *   <li><b>认证鉴权</b>：Token 校验、未认证拦截、过期 Token 处理</li>
 *   <li><b>新手引导</b>：获取引导问题、提交引导答案、检查引导完成状态</li>
 *   <li><b>探索投票</b>：获取当前对局、提交投票结果</li>
 *   <li><b>报告查询</b>：获取审美 DNA 报告、获取初印象报告</li>
 *   <li><b>完整业务链路</b>：登录 → 引导 → 投票 → 报告 的全流程串联测试</li>
 * </ol>
 *
 * <h2>测试环境说明</h2>
 * <ul>
 *   <li>数据库：远程 MySQL（通过环境变量注入连接信息）</li>
 *   <li>缓存：远程 Redis（通过环境变量注入连接信息）</li>
 *   <li>安全：防重放切面默认启用（{@code apex.security.anti-replay.enabled} 未配置时为 true），
 *       提交投票等 {@code @AntiReplay} 接口必须携带 {@code X-Timestamp} / {@code X-Nonce} 请求头，
 *       测试通过 {@link #withAntiReplayHeaders} 模拟真实前端行为</li>
 *   <li>认证：使用 {@link TestTokenHelper} 生成测试用 JWT Token 模拟已登录用户</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see BaseIntegrationTest
 * @see TestTokenHelper
 * @see WebTestClient
 */
@DisplayName("关键业务流程 E2E 集成测试")
@EnabledIfSystemProperty(named = "apex.remote-it.enabled", matches = "true")
class BusinessFlowE2ERemoteIT extends BaseLocalIntegrationTest {
    public BusinessFlowE2ERemoteIT(javax.sql.DataSource dataSource) {
        super(dataSource);
        this.dataSource = dataSource;
    }

    /** 测试用认证 Token，有效期 60 分钟 */
    private String accessToken;

    /** 远程共享库数据源，用于测试前置数据清理 */
    private final javax.sql.DataSource dataSource;

    /**
     * 测试用 OpenID，模拟微信小程序用户身份。
     *
     * <p>必须与 {@link BaseLocalIntegrationTest#initSchema()} 在远程共享库中准备的用户
     * （{@link TestDataFactory#TEST_OPEN_ID}，即 {@code test_open_id_001}）保持一致，
     * 否则 {@code JwtAuthenticationFilter} 按 OpenID 查询用户返回空，所有已认证请求被 401 拦截。</p>
     */
    private static final String TEST_USER_OPEN_ID = TEST_OPEN_ID;

    /**
     * 每个测试方法执行前初始化认证凭证。
     *
     * <p>生成一个模拟微信用户的 JWT accessToken，作为后续所有需认证接口的凭证。
     * Token 有效期设置为 60 分钟，覆盖所有测试的执行时间。</p>
     */
    @BeforeEach
    void setUp() {
        accessToken = generateAccessToken(TEST_USER_OPEN_ID, 60);
    }

    /**
     * 为 {@code @AntiReplay} 接口生成防重放请求头。
     *
     * <p>返回当前 Unix 秒级时间戳与全局唯一随机数（Nonce），
     * 模拟真实前端提交请求时携带的 {@code X-Timestamp} / {@code X-Nonce} 请求头。</p>
     *
     * @return 按请求头名称到值的映射
     */
    private Map<String, String> antiReplayHeaders() {
        return Map.of(
                "X-Timestamp", String.valueOf(Instant.now().getEpochSecond()),
                "X-Nonce", UUID.randomUUID().toString().replace("-", ""));
    }

    // ==================== 1. 认证鉴权流程测试 ====================

    /**
     * 认证鉴权流程集成测试。
     *
     * <p>验证基于 JWT 的无状态认证机制在集成环境下的完整行为，
     * 包括正常访问、无 Token 拦截、无效 Token 拦截、过期 Token 拦截等场景。</p>
     *
     * @author Apex Team
     * @since 1.0.0
     */
    @Nested
    @DisplayName("认证鉴权流程")
    class AuthenticationFlow {

        /**
         * 测试：携带有效 Token 访问需认证接口应返回 200。
         *
         * <p>验证 WebFlux Security 过滤器链中 JWT 认证过滤器的正向通过流程，
         * 确保有效 Token 能正确解析并注入到 Reactor Context。</p>
         */
        @Test
        @DisplayName("携带有效Token访问需认证接口应返回200")
        void authenticatedRequest_withValidToken_shouldReturn200() {
            webTestClient
                    .get()
                    .uri("/api/v1/user/info")
                    .header(SystemConstants.TOKEN_HEADER, SystemConstants.TOKEN_PREFIX + accessToken)
                    .exchange()
                    .expectStatus()
                    .isOk();
        }

        /**
         * 测试：无 Token 访问需认证接口应返回 401。
         *
         * <p>验证 SecurityConfig 中 {@code anyExchange().authenticated()} 规则生效，
         * 未携带 Authorization 头的请求被正确拦截。</p>
         */
        @Test
        @DisplayName("无Token访问需认证接口应返回401")
        void unauthenticatedRequest_withoutToken_shouldReturn401() {
            webTestClient
                    .get()
                    .uri("/api/v1/user/info")
                    .exchange()
                    .expectStatus()
                    .isUnauthorized();
        }

        /**
         * 测试：携带无效 Token 访问需认证接口应返回 401。
         *
         * <p>验证 JWT 签名校验机制，篡改或伪造的 Token 无法通过验证。</p>
         */
        @Test
        @DisplayName("携带无效Token访问需认证接口应返回401")
        void authenticatedRequest_withInvalidToken_shouldReturn401() {
            webTestClient
                    .get()
                    .uri("/api/v1/user/info")
                    .header(SystemConstants.TOKEN_HEADER, SystemConstants.TOKEN_PREFIX + "invalid.token.value")
                    .exchange()
                    .expectStatus()
                    .isUnauthorized();
        }

        /**
         * 测试：携带过期 Token 访问需认证接口应返回 401。
         *
         * <p>验证 JWT 过期时间校验机制，超过有效期的 Token 被正确拒绝。
         * 测试使用 {@link TestTokenHelper#generateExpiredToken} 生成已过期 Token。</p>
         */
        @Test
        @DisplayName("携带过期Token访问需认证接口应返回401")
        void authenticatedRequest_withExpiredToken_shouldReturn401() {
            String expiredToken = generateExpiredToken(TEST_USER_OPEN_ID);
            webTestClient
                    .get()
                    .uri("/api/v1/user/info")
                    .header(SystemConstants.TOKEN_HEADER, SystemConstants.TOKEN_PREFIX + expiredToken)
                    .exchange()
                    .expectStatus()
                    .isUnauthorized();
        }

        /**
         * 测试：访问公开接口（无需认证）应直接返回 200。
         *
         * <p>验证 {@link SystemConstants#PUBLIC_PATHS} 中的路径配置生效，
         * 健康检查等公开接口无需携带 Token 即可访问。</p>
         */
        @Test
        @DisplayName("公开接口无需Token可访问")
        void publicEndpoint_withoutToken_shouldReturn200() {
            webTestClient.get().uri("/api/v1/health").exchange().expectStatus().isOk();
        }
    }

    // ==================== 2. 新手引导流程测试 ====================

    /**
     * 新手引导流程集成测试。
     *
     * <p>验证新用户引导的全链路功能，包括获取引导问题列表、
     * 提交引导答案、查询引导完成状态等核心接口。</p>
     *
     * @author Apex Team
     * @since 1.0.0
     */
    @Nested
    @DisplayName("新手引导流程")
    class OnboardingFlow {

        /**
         * 测试：获取引导问题应返回题目和选项列表。
         *
         * <p>验证 {@code GET /api/v1/onboarding/guide/questions} 接口，
         * 返回的题目列表应包含题目 ID、题目文本和可选项列表。
         * 该接口按当前安全配置需要登录后访问，测试需携带 Token。</p>
         */
        @Test
        @DisplayName("获取引导问题应返回题目和选项列表")
        void getGuideQuestions_shouldReturnQuestionsWithOptions() {
            webTestClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/onboarding/guide/questions")
                            .queryParam("scene", "REGISTRATION")
                            .build())
                    .header(SystemConstants.TOKEN_HEADER, SystemConstants.TOKEN_PREFIX + accessToken)
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.code")
                    .isEqualTo(0)
                    .jsonPath("$.data")
                    .isArray();
        }

        /**
         * 测试：提交引导答案应返回成功。
         *
         * <p>验证 {@code POST /api/v1/onboarding/guide/answers} 接口，
         * 提交包含场景标识和答案列表的请求体后应返回成功响应。
         * 该接口需要认证。</p>
         */
        @Test
        @DisplayName("提交引导答案应返回成功")
        void submitGuideAnswers_shouldReturnSuccess() {
            String requestBody =
                    """
                    {
                        "scene": "REGISTRATION",
                        "answers": [
                            { "questionId": "dating_goal", "selectedOptionId": "serious" },
                            { "questionId": "personality_type", "selectedOptionId": "introvert" },
                            { "questionId": "interests", "selectedOptionId": "music" }
                        ]
                    }""";

            webTestClient
                    .post()
                    .uri("/api/v1/onboarding/guide/answers")
                    .header(SystemConstants.TOKEN_HEADER, SystemConstants.TOKEN_PREFIX + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(JsonUtil.fromJson(requestBody, Map.class))
                    .exchange()
                    .expectStatus()
                    .isOk();
        }

        /**
         * 测试：提交空答案列表应返回 400 校验失败。
         *
         * <p>验证 JSR-380 Bean Validation 在 WebFlux 环境下的参数校验机制，
         * 空答案列表触发 {@code @NotEmpty} 校验，返回 400 Bad Request。</p>
         */
        @Test
        @DisplayName("提交空答案列表应返回400")
        void submitEmptyAnswers_shouldReturn400() {
            String requestBody =
                    """
                    {
                        "scene": "REGISTRATION",
                        "answers": []
                    }""";

            webTestClient
                    .post()
                    .uri("/api/v1/onboarding/guide/answers")
                    .header(SystemConstants.TOKEN_HEADER, SystemConstants.TOKEN_PREFIX + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(JsonUtil.fromJson(requestBody, Map.class))
                    .exchange()
                    .expectStatus()
                    .isBadRequest();
        }

        /**
         * 测试：查询引导完成状态应返回引导进度信息。
         *
         * <p>验证 {@code GET /api/v1/onboarding/guide/state} 接口，
         * 获取指定场景的引导完成状态。该接口需要认证。</p>
         */
        @Test
        @DisplayName("查询引导完成状态应返回进度信息")
        void getGuideState_shouldReturnProgress() {
            webTestClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/onboarding/guide/state")
                            .queryParam("scene", "REGISTRATION")
                            .build())
                    .header(SystemConstants.TOKEN_HEADER, SystemConstants.TOKEN_PREFIX + accessToken)
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.code")
                    .isEqualTo(0);
        }

        /**
         * 测试：未认证用户提交引导答案应返回 401。
         */
        @Test
        @DisplayName("未认证用户提交引导答案应返回401")
        void submitGuideAnswers_withoutToken_shouldReturn401() {
            String requestBody =
                    """
                    {
                        "scene": "REGISTRATION",
                        "answers": [
                            { "questionId": "dating_goal", "selectedOptionId": "serious" }
                        ]
                    }""";

            webTestClient
                    .post()
                    .uri("/api/v1/onboarding/guide/answers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(JsonUtil.fromJson(requestBody, Map.class))
                    .exchange()
                    .expectStatus()
                    .isUnauthorized();
        }
    }

    // ==================== 3. 探索投票流程测试 ====================

    /**
     * 探索投票流程集成测试。
     *
     * <p>验证照片对战投票的核心业务流程，包括获取当前对局、
     * 提交投票结果等接口。投票是系统的核心功能，涉及 ELO 评分算法、
     * 金睛反作弊检测等多个领域服务协作。</p>
     *
     * @author Apex Team
     * @since 1.0.0
     */
    @Nested
    @DisplayName("探索投票流程")
    class ExploreVoteFlow {

        /**
         * 测试：获取当前对局应返回对局信息。
         *
         * <p>验证 {@code GET /api/v1/explore/battles/current} 接口，
         * 请求指定对局类型后应返回对应的对局数据（包含照片列表）。
         * 该接口需要认证。</p>
         */
        @Test
        @DisplayName("获取当前对局应返回对局信息")
        void getCurrentBattle_shouldReturnBattleInfo() {
            webTestClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/explore/battles/current")
                            .queryParam("type", "TWO_CHOICE")
                            .build())
                    .header(SystemConstants.TOKEN_HEADER, SystemConstants.TOKEN_PREFIX + accessToken)
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.code")
                    .isEqualTo(0);
        }

        /**
         * 测试：请求不存在的对局类型应返回正常响应。
         *
         * <p>验证系统对未知对局类型的容错处理能力，
         * 不会因非法参数导致服务崩溃。</p>
         */
        @Test
        @DisplayName("未知对局类型应能正常响应")
        void getCurrentBattle_withUnknownType_shouldReturnResponse() {
            webTestClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/explore/battles/current")
                            .queryParam("type", "UNKNOWN_TYPE")
                            .build())
                    .header(SystemConstants.TOKEN_HEADER, SystemConstants.TOKEN_PREFIX + accessToken)
                    .exchange()
                    .expectStatus()
                    .isOk();
        }

        /**
         * 测试：未认证用户获取对局应返回 401。
         */
        @Test
        @DisplayName("未认证用户获取对局应返回401")
        void getCurrentBattle_withoutToken_shouldReturn401() {
            webTestClient
                    .get()
                    .uri("/api/v1/explore/battles/current")
                    .exchange()
                    .expectStatus()
                    .isUnauthorized();
        }

        /**
         * 测试：提交不存在对局的投票应返回 404。
         *
         * <p>这是提交投票路由 P0 缺陷（{@code PathPattern} 拼接时 {@code {battleId}}
         * 变量重复捕获导致 {@code PatternParseException}，所有提交请求被 400 拦截）的
         * 端到端回归断言：请求必须穿过 JWT 认证、防重放切面，到达 Handler 并正确解析
         * 路径变量 {@code battleId}，随后进入服务层对局查询，因对局不存在返回 404。
         * 若路由仍存在 PatternParseException，此处将得到 400 而非 404。</p>
         */
        @Test
        @DisplayName("提交不存在对局的投票应返回404")
        void submitVote_nonexistentBattle_shouldReturn404() {
            String requestBody =
                    """
                    {
                        "selectedPhotoIds": ["photo-001", "photo-002"],
                        "voteTimeMs": 1500,
                        "clientRequestId": "test-e2e-request-001"
                    }""";

            Map<String, String> antiReplay = antiReplayHeaders();
            webTestClient
                    .post()
                    .uri("/api/v1/explore/battles/99999/submit")
                    .header(SystemConstants.TOKEN_HEADER, SystemConstants.TOKEN_PREFIX + accessToken)
                    .header("X-Timestamp", antiReplay.get("X-Timestamp"))
                    .header("X-Nonce", antiReplay.get("X-Nonce"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(JsonUtil.fromJson(requestBody, Map.class))
                    .exchange()
                    .expectStatus()
                    .isNotFound();
        }

        /**
         * 测试：提交空的选中照片列表应返回 400 校验失败。
         *
         * <p>验证 {@code BattleSubmitRequest#selectedPhotoIds} 的 {@code @NotEmpty} 校验生效。</p>
         */
        @Test
        @DisplayName("提交空照片列表应返回400")
        void submitVote_withEmptyPhotoIds_shouldReturn400() {
            String requestBody =
                    """
                    {
                        "selectedPhotoIds": [],
                        "voteTimeMs": 1500
                    }""";

            Map<String, String> antiReplay = antiReplayHeaders();
            webTestClient
                    .post()
                    .uri("/api/v1/explore/battles/99999/submit")
                    .header(SystemConstants.TOKEN_HEADER, SystemConstants.TOKEN_PREFIX + accessToken)
                    .header("X-Timestamp", antiReplay.get("X-Timestamp"))
                    .header("X-Nonce", antiReplay.get("X-Nonce"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(JsonUtil.fromJson(requestBody, Map.class))
                    .exchange()
                    .expectStatus()
                    .isBadRequest();
        }
    }

    // ==================== 4. 报告查询流程测试 ====================

    /**
     * 报告查询流程集成测试。
     *
     * <p>验证多维度报告查询接口的功能正确性，包括审美 DNA 报告、
     * AI 初印象报告、动物人格报告等。报告是系统的核心输出，
     * 基于用户的投票行为数据通过工厂模式生成。</p>
     *
     * @author Apex Team
     * @since 1.0.0
     */
    @Nested
    @DisplayName("报告查询流程")
    class ReportFlow {

        /**
         * 测试：获取审美 DNA 报告应返回报告数据。
         *
         * <p>验证 {@code GET /api/v1/reports/aesthetic-dna/me} 接口，
         * 获取当前登录用户的审美 DNA 分析报告。
         * 该接口需要认证。</p>
         */
        @Test
        @DisplayName("获取审美DNA报告应返回报告数据")
        void getAestheticDnaReport_shouldReturnReportData() {
            webTestClient
                    .get()
                    .uri("/api/v1/reports/aesthetic-dna/me")
                    .header(SystemConstants.TOKEN_HEADER, SystemConstants.TOKEN_PREFIX + accessToken)
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.code")
                    .isEqualTo(0);
        }

        /**
         * 测试：获取动物人格报告应返回报告数据。
         *
         * <p>验证 {@code GET /api/v1/reports/animal/me} 接口，
         * 获取当前登录用户的动物人格分析报告。</p>
         */
        @Test
        @DisplayName("获取动物人格报告应返回报告数据")
        void getAnimalReport_shouldReturnReportData() {
            webTestClient
                    .get()
                    .uri("/api/v1/reports/animal/me")
                    .header(SystemConstants.TOKEN_HEADER, SystemConstants.TOKEN_PREFIX + accessToken)
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.code")
                    .isEqualTo(0);
        }

        /**
         * 测试：按报告类型获取报告应返回对应数据。
         *
         * <p>验证 {@code GET /api/v1/reports/{reportType}/me} 接口的
         * 动态路由能力，支持多种报告类型的统一查询入口。</p>
         */
        @Test
        @DisplayName("按报告类型查询应返回对应数据")
        void getReportByType_shouldReturnCorrespondingData() {
            webTestClient
                    .get()
                    .uri("/api/v1/reports/astrology/me")
                    .header(SystemConstants.TOKEN_HEADER, SystemConstants.TOKEN_PREFIX + accessToken)
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.code")
                    .isEqualTo(0);
        }

        /**
         * 测试：未认证用户获取报告应返回 401。
         */
        @Test
        @DisplayName("未认证用户获取报告应返回401")
        void getReport_withoutToken_shouldReturn401() {
            webTestClient
                    .get()
                    .uri("/api/v1/reports/aesthetic-dna/me")
                    .exchange()
                    .expectStatus()
                    .isUnauthorized();
        }
    }

    // ==================== 5. 完整业务链路串联测试 ====================

    /**
     * 完整业务链路端到端串联测试。
     *
     * <p>按固定的测试顺序串联执行核心业务流程，模拟真实的用户使用场景：
     * <ol>
     *   <li>用户登录认证 → 获取 accessToken</li>
     *   <li>新手引导 → 获取引导题目 → 提交答案</li>
     *   <li>探索投票 → 获取当前对局 → 提交投票结果</li>
     *   <li>报告查询 → 获取审美 DNA → 获取动物人格报告</li>
     * </ol>
     *
     * <p>使用 {@link MethodOrderer.OrderAnnotation} 保证测试按顺序执行，
     * 前一步的输出（如 Token）作为后续步骤的输入。</p>
     *
     * @author Apex Team
     * @since 1.0.0
     */
    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @DisplayName("完整业务链路串联")
    class FullBusinessFlow {

        /** 链路上文中共享的 JWT Token */
        private String flowToken;

        /** 链路上文中共享的对局 ID */
        private Long battleId;

        /** 链路上文中共享的对局照片 ID（取自 step4 真实对局） */
        private final List<String> battlePhotoIds = new ArrayList<>();

        /**
         * 初始化完整链路测试的认证凭证。
         *
         * <p>为整个链路测试生成一个统一的 Token，确保所有步骤使用相同的用户身份。</p>
         */
        @BeforeEach
        void setUpFlow() {
            flowToken = generateAccessToken(TEST_USER_OPEN_ID, 60);
        }

        /**
         * 步骤 1：验证 Token 有效性。
         *
         * <p>在开始完整业务链路之前，首先验证生成的 Token 能够正常通过认证，
         * 确保后续所有步骤的鉴权基础是可靠的。</p>
         */
        @Test
        @Order(1)
        @DisplayName("步骤1 - 验证Token有效性")
        void step1_verifyTokenValidity() {
            webTestClient
                    .get()
                    .uri("/api/v1/user/info")
                    .header(SystemConstants.TOKEN_HEADER, SystemConstants.TOKEN_PREFIX + flowToken)
                    .exchange()
                    .expectStatus()
                    .isOk();
        }

        /**
         * 步骤 2：获取引导题目。
         *
         * <p>模拟新用户进入引导流程，获取注册场景下的引导题目列表，
         * 验证题目包含完整的题目 ID、文本和选项信息。
         * 该接口按当前安全配置需要登录后访问，请求需携带 Token。</p>
         */
        @Test
        @Order(2)
        @DisplayName("步骤2 - 获取引导题目")
        void step2_getGuideQuestions() {
            byte[] responseBytes = webTestClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/onboarding/guide/questions")
                            .queryParam("scene", "REGISTRATION")
                            .build())
                    .header(SystemConstants.TOKEN_HEADER, SystemConstants.TOKEN_PREFIX + flowToken)
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .returnResult()
                    .getResponseBody();

            Map<String, Object> result = JsonUtil.fromJson(new String(responseBytes), Map.class);
            assertThat(result.get("code")).isEqualTo(0);
            assertThat(result.get("data")).isNotNull();
        }

        /**
         * 步骤 3：提交引导答案。
         *
         * <p>模拟用户完成引导问卷，提交包含三种问题类型的答案，
         * 验证系统正确持久化引导答案数据。</p>
         */
        @Test
        @Order(3)
        @DisplayName("步骤3 - 提交引导答案")
        void step3_submitGuideAnswers() {
            String requestBody =
                    """
                    {
                        "scene": "REGISTRATION",
                        "answers": [
                            { "questionId": "dating_goal", "selectedOptionId": "serious" },
                            { "questionId": "personality_type", "selectedOptionId": "ambivert" },
                            { "questionId": "interests", "selectedOptionId": "travel" }
                        ]
                    }""";

            webTestClient
                    .post()
                    .uri("/api/v1/onboarding/guide/answers")
                    .header(SystemConstants.TOKEN_HEADER, SystemConstants.TOKEN_PREFIX + flowToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(JsonUtil.fromJson(requestBody, Map.class))
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.code")
                    .isEqualTo(0);
        }

        /**
         * 步骤 4：获取并验证当前对局。
         *
         * <p>模拟用户进入探索页面，获取当前待投票的对局数据，
         * 包括对局基本信息和对局项（照片）列表。</p>
         */
        @Test
        @Order(4)
        @DisplayName("步骤4 - 获取当前对局")
        void step4_getCurrentBattle() {
            cleanupTestUserBattles();
            byte[] responseBytes = webTestClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/explore/battles/current")
                            .queryParam("type", "TWO_CHOICE")
                            .build())
                    .header(SystemConstants.TOKEN_HEADER, SystemConstants.TOKEN_PREFIX + flowToken)
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .returnResult()
                    .getResponseBody();

            Map<String, Object> result = JsonUtil.fromJson(new String(responseBytes), Map.class);
            assertThat(result.get("code")).isEqualTo(0);

            // 解析对局数据，提取 battleId 供后续步骤使用。
            // 接口响应字段为 battleId（如 CHOICE_2_GLOBAL_CALIBRATION 对局），
            // 兼容历史字段 id，避免字段名不匹配导致 battleId 恒为 null。
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            if (data != null) {
                Object battleIdValue = data.get("battleId");
                if (battleIdValue == null) {
                    battleIdValue = data.get("id");
                }
                if (battleIdValue != null) {
                    battleId = Long.valueOf(battleIdValue.toString());
                }
                // 提取对局项照片 ID，供步骤 5 提交真实对局照片
                Object itemsValue = data.get("items");
                if (itemsValue instanceof List<?> items) {
                    for (Object itemObj : items) {
                        if (itemObj instanceof Map<?, ?> item
                                && item.get("photoId") != null
                                && battlePhotoIds.size() < 2) {
                            battlePhotoIds.add(item.get("photoId").toString());
                        }
                    }
                }
            }
        }

        /**
         * 步骤 5：提交投票结果。
         *
         * <p>模拟用户在对局中选择照片并提交投票，
         * 验证投票数据的持久化和 ELO 评分更新链路。</p>
         */
        @Test
        @Order(5)
        @DisplayName("步骤5 - 提交投票结果")
        void step5_submitVote() {
            // 依赖步骤 4 获取的真实对局；dev 环境无可用对局时跳过而非误报失败
            Assumptions.assumeTrue(battleId != null, "dev 环境当前无可用对局，跳过投票提交步骤");
            Assumptions.assumeTrue(!battlePhotoIds.isEmpty(), "当前对局照片为空，跳过投票提交步骤");

            // 首次提交；若对局已被后台预取/Outbox 调度替换（409），
            // 模拟真实用户重新同步当前对局后重试一次。
            int status = submitVote(battleId);
            if (status == 409) {
                syncCurrentBattle();
                Assumptions.assumeTrue(battleId != null, "对局失效且重新同步后仍无可用对局，跳过投票提交步骤");
                Assumptions.assumeTrue(!battlePhotoIds.isEmpty(), "重新同步后对局照片为空，跳过投票提交步骤");
                status = submitVote(battleId);
            }
            assertThat(status).isEqualTo(200);
        }

        /**
         * 提交一次投票。
         *
         * @param targetBattleId 目标对局 ID
         * @return HTTP 状态码
         */
        private int submitVote(Long targetBattleId) {
            // 二选一（CHOICE_2 系列）对局必须选择 1 张照片
            String requestBody = String.format(
                    """
                    {
                        "selectedPhotoIds": ["%s"],
                        "voteTimeMs": 2500,
                        "clientRequestId": "e2e-fullflow-%d"
                    }""",
                    battlePhotoIds.get(0), System.currentTimeMillis());

            Map<String, String> antiReplay = antiReplayHeaders();
            return webTestClient
                    .post()
                    .uri("/api/v1/explore/battles/" + targetBattleId + "/submit")
                    .header(SystemConstants.TOKEN_HEADER, SystemConstants.TOKEN_PREFIX + flowToken)
                    .header("X-Timestamp", antiReplay.get("X-Timestamp"))
                    .header("X-Nonce", antiReplay.get("X-Nonce"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(JsonUtil.fromJson(requestBody, Map.class))
                    .exchange()
                    .expectBody()
                    .returnResult()
                    .getStatus()
                    .value();
        }

        /**
         * 重新获取当前对局并刷新链路上下文中的对局数据。
         */
        private void syncCurrentBattle() {
            battleId = null;
            battlePhotoIds.clear();
            step4_getCurrentBattle();
        }

        /**
         * 清理测试用户在共享库中的残留对局数据。
         *
         * <p>全量链路测试与其它嵌套用例共享同一测试用户 {@link BusinessFlowE2ERemoteIT#TEST_USER_OPEN_ID}，
         * 其它用例只获取对局未提交时会在 {@code biz_battle} 留下 CREATED 残留记录，
         * 而步骤 5 提交投票时校验的是 CURRENT 槽位对局（与 step4 直接返回的最新活跃对局可能不一致）。
         * 因此 step4 前将测试用户的历史 CREATED 对局归档并清空 CURRENT 槽位，
         * 让服务端走「创建新对局并注册 CURRENT 槽位」路径，保证 step4/step5 对局一致。</p>
         */
        private void cleanupTestUserBattles() {
            try (var conn = dataSource.getConnection();
                    var stmt = conn.createStatement()) {
                // 1. 归档测试用户所有未完成的 CREATED 对局（软删 + 置为 EXPIRED）
                stmt.executeUpdate("UPDATE biz_battle SET deleted = 1, status = 'EXPIRED' "
                        + "WHERE user_id = (SELECT id FROM sys_user WHERE open_id = '"
                        + TEST_USER_OPEN_ID
                        + "') AND status = 'CREATED' AND deleted = 0");
                // 2. 物理删除测试用户的 CURRENT 交付槽位，释放 slot_key 唯一键。
                //    注意：biz_battle_delivery 的 uk_battle_delivery_slot(slot_key) 唯一索引不含
                //    deleted，软删除的记录仍占用 '1:CHOICE_2:CURRENT' 槽位键，会导致后续
                //    insertCurrentIfAbsent 恒为 no-op（ON DUPLICATE KEY UPDATE 不覆盖），
                //    组局请求随之 409「当前局槽位登记失败」，因此这里必须物理删除。
                stmt.executeUpdate("DELETE FROM biz_battle_delivery "
                        + "WHERE user_id = (SELECT id FROM sys_user WHERE open_id = '"
                        + TEST_USER_OPEN_ID + "') AND slot_key LIKE '%:CURRENT'");
            } catch (Exception e) {
                throw new IllegalStateException("清理测试用户对局数据失败", e);
            }
        }

        /**
         * 步骤 6：查询审美 DNA 报告。
         *
         * <p>验证报告生成的完整链路，确认系统能够基于用户的投票数据
         * 生成并返回审美 DNA 分析报告。</p>
         */
        @Test
        @Order(6)
        @DisplayName("步骤6 - 查询审美DNA报告")
        void step6_getAestheticDnaReport() {
            webTestClient
                    .get()
                    .uri("/api/v1/reports/aesthetic-dna/me")
                    .header(SystemConstants.TOKEN_HEADER, SystemConstants.TOKEN_PREFIX + flowToken)
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.code")
                    .isEqualTo(0);
        }

        /**
         * 步骤 7：查询动物人格报告。
         *
         * <p>验证多维度报告系统的完整性，确认动物人格报告生成链路正常。
         * 这是完整业务链路的终点，标志着一个完整的用户交互周期完成。</p>
         */
        @Test
        @Order(7)
        @DisplayName("步骤7 - 查询动物人格报告 - 链路终点")
        void step7_getAnimalReport_endOfFlow() {
            webTestClient
                    .get()
                    .uri("/api/v1/reports/animal/me")
                    .header(SystemConstants.TOKEN_HEADER, SystemConstants.TOKEN_PREFIX + flowToken)
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.code")
                    .isEqualTo(0);
        }
    }
}

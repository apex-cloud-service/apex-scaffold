package com.apex.scaffold.infrastructure.moderation;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.common.util.JsonUtil;
import com.apex.scaffold.domain.gateway.TextModerationPort;
import com.apex.scaffold.domain.model.moderation.ModerationDecision;
import com.apex.scaffold.domain.model.moderation.ModerationFinding;
import com.apex.scaffold.domain.model.moderation.TextModerationResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 百度智能云内容审核平台文本审核适配器。
 *
 * <p>封装百度文本审核 API（{@code /rest/2.0/solution/v1/text_censor/v2/user_defined}）
 * 的鉴权、请求构建和响应解析，实现 {@link TextModerationPort}。</p>
 *
 * <p>请求体仅包含 {@code text}（Content-Type 为 form-urlencoded，上限 20000 字节）；
 * 响应使用 {@code conclusionType}（1 合规 / 2 不合规 / 3 疑似 / 4 审核失败）驱动领域决定。</p>
 *
 * @author weiqiang.ling
 * @version 1.0.0
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(prefix = "text.moderation.baidu", name = "enabled", havingValue = "true")
public class BaiduTextModerationAdapter implements TextModerationPort {

    private static final String PROVIDER = "BAIDU_TEXT_CENSOR";
    private static final int TOKEN_INVALID = 110;
    private static final int TOKEN_EXPIRED = 111;
    /** 百度云 QPS 限流错误码（实测 error_code=18）。 */
    static final int QPS_LIMIT_ERROR = 18;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    /** 百度文本审核请求体长度上限（字节）。 */
    static final int MAX_TEXT_BYTES = 20_000;

    private final String apiKey;
    private final String secretKey;
    private final String tokenEndpoint;
    private final String moderationEndpoint;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final Object tokenLock = new Object();
    private final int maxRetryAttempts;
    private final int retryBaseDelayMillis;

    private volatile CachedToken cachedToken;

    /**
     * 构造方法，通过 Spring {@code @Value} 注入百度文本审核配置。
     *
     * @param apiKey 百度云 API Key
     * @param secretKey 百度云 Secret Key
     * @param tokenEndpoint Token 获取端点
     * @param moderationEndpoint 审核 API 端点
     * @param timeoutSeconds 超时时间（秒）
     * @param maxRetryAttempts 限流/瞬时故障的最大重试次数（总调用次数 = 1 + 重试次数）
     * @param retryBaseDelayMillis 重试退避基准毫秒数（第 n 次重试等待 n × 基准）
     */
    public BaiduTextModerationAdapter(
            @Value("${text.moderation.baidu.api-key:${BAIDU_TEXT_MODERATION_API_KEY:}}") String apiKey,
            @Value("${text.moderation.baidu.secret-key:${BAIDU_TEXT_MODERATION_SECRET_KEY:}}") String secretKey,
            @Value("${text.moderation.baidu.token-endpoint:https://aip.baidubce.com/oauth/2.0/token}")
                    String tokenEndpoint,
            @Value("${text.moderation.baidu.endpoint:"
                            + "https://aip.baidubce.com/rest/2.0/solution/v1/text_censor/v2/user_defined}")
                    String moderationEndpoint,
            @Value("${text.moderation.baidu.timeout-seconds:10}") long timeoutSeconds,
            @Value("${text.moderation.baidu.retry-max-attempts:3}") int maxRetryAttempts,
            @Value("${text.moderation.baidu.retry-base-delay-ms:250}") int retryBaseDelayMillis) {
        if (isBlank(apiKey) || isBlank(secretKey)) {
            throw new IllegalStateException("启用百度云文本审核时必须配置 API Key 和 Secret Key");
        }
        if (timeoutSeconds <= 0) {
            throw new IllegalStateException("百度云文本审核 timeoutSeconds 必须大于 0");
        }
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.tokenEndpoint = tokenEndpoint;
        this.moderationEndpoint = moderationEndpoint;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.maxRetryAttempts = Math.max(0, maxRetryAttempts);
        this.retryBaseDelayMillis = Math.max(0, retryBaseDelayMillis);
    }

    /**
     * 执行 {@code moderate} 逻辑。
     *
     * @param text text 参数
     *
     * @return 执行结果
     */
    @Override
    public Mono<TextModerationResult> moderate(String text) {
        if (isBlank(text)) {
            return Mono.error(new IllegalArgumentException("待审核文本不能为空"));
        }
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_TEXT_BYTES) {
            return Mono.error(new IllegalArgumentException("待审核文本超过 20000 字节上限"));
        }
        return Mono.fromCallable(() -> callBaidu(text))
                .subscribeOn(BlockingIoSchedulers.remote())
                .timeout(timeout.multipliedBy(2).plusSeconds(2));
    }

    /**
     * 执行 {@code callBaidu} 逻辑。
     *
     * @param text text 参数
     *
     * @return 执行结果
     *
     * @throws Exception 执行失败时
     */
    private TextModerationResult callBaidu(String text) throws Exception {
        try {
            return requestWithRetry(text, accessToken());
        } catch (BaiduTokenException ignored) {
            invalidateToken();
            return requestWithRetry(text, accessToken());
        }
    }

    /**
     * 执行 {@code requestWithRetry} 逻辑：对 QPS 限流与可重试 HTTP 状态做线性退避重试。
     *
     * @param text text 参数
     *
     * @param accessToken accessToken 参数
     *
     * @return 执行结果
     *
     * @throws Exception 执行失败时
     */
    private TextModerationResult requestWithRetry(String text, String accessToken) throws Exception {
        int totalAttempts = 1 + maxRetryAttempts;
        for (int attempt = 1; ; attempt++) {
            HttpResponse<String> response = sendRequest(text, accessToken);
            int status = response.statusCode();
            if (isRetryableHttpStatus(status)) {
                if (attempt < totalAttempts) {
                    sleepRetry(attempt);
                    continue;
                }
                throw new IllegalStateException("百度云文本审核 HTTP 调用失败，状态码=" + status);
            }
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("百度云文本审核 HTTP 调用失败，状态码=" + status);
            }
            JsonNode root = JsonUtil.getObjectMapper().readTree(response.body());
            int errorCode = root.path("error_code").asInt(0);
            if (errorCode == TOKEN_INVALID || errorCode == TOKEN_EXPIRED) {
                throw new BaiduTokenException();
            }
            if (isRateLimitedErrorCode(errorCode)) {
                if (attempt < totalAttempts) {
                    sleepRetry(attempt);
                    continue;
                }
                throw new IllegalStateException("百度云文本审核限流，重试 " + maxRetryAttempts + " 次后仍失败，错误码=" + errorCode);
            }
            if (errorCode != 0) {
                throw new IllegalStateException("百度云文本审核失败，错误码=" + errorCode + "，错误信息="
                        + root.path("error_msg").asText("unknown"));
            }
            return mapResponse(root);
        }
    }

    /**
     * 执行 {@code sendRequest} 逻辑。
     *
     * @param text text 参数
     *
     * @param accessToken accessToken 参数
     *
     * @return 执行结果
     *
     * @throws Exception 执行失败时
     */
    private HttpResponse<String> sendRequest(String text, String accessToken) throws Exception {
        String formBody = "text=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
        URI uri = URI.create(
                moderationEndpoint + "?access_token=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 执行 {@code sleepRetry} 逻辑。
     *
     * @param attempt attempt 参数
     *
     * @throws InterruptedException 执行失败时
     */
    private void sleepRetry(int attempt) throws InterruptedException {
        Thread.sleep(retryDelayMillis(attempt, retryBaseDelayMillis));
    }

    /**
     * 百度云 QPS 限流错误码（error_code=18，Open api qps request limit reached）。
     *
     * @param errorCode errorCode 参数
     *
     * @return 是否为限流错误
     */
    static boolean isRateLimitedErrorCode(int errorCode) {
        return errorCode == QPS_LIMIT_ERROR;
    }

    /**
     * HTTP 状态是否可重试（429 限流 / 503 服务不可用）。
     *
     * @param status status 参数
     *
     * @return 是否可重试
     */
    static boolean isRetryableHttpStatus(int status) {
        return status == 429 || status == 503;
    }

    /**
     * 第 {@code attempt} 次调用的退避等待毫秒数：线性退避 attempt × base。
     *
     * @param attempt 当前调用次数（从 1 开始）
     *
     * @param baseMillis 退避基准毫秒数
     *
     * @return 等待毫秒数
     */
    static long retryDelayMillis(int attempt, int baseMillis) {
        return Math.max(0, (long) attempt * baseMillis);
    }

    /**
     * 执行 {@code mapResponse} 逻辑。
     *
     * @param root root 参数
     *
     * @return 执行结果
     */
    TextModerationResult mapResponse(JsonNode root) {
        int conclusionType = root.path("conclusionType").asInt(0);
        if (conclusionType == 4) {
            throw new IllegalStateException("百度云文本审核返回审核失败");
        }
        ModerationDecision overallDecision = mapDecision(conclusionType);
        List<ModerationFinding> findings = new ArrayList<>();
        JsonNode data = root.path("data");
        if (data.isArray()) {
            data.forEach(item -> findings.add(mapFinding(item, overallDecision)));
        }
        if (findings.isEmpty()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("conclusion", root.path("conclusion").asText(""));
            findings.add(new ModerationFinding(
                    "overall",
                    suggestion(overallDecision),
                    root.path("conclusion").asText("合规"),
                    null,
                    details));
        }
        String requestId =
                root.path("log_id").isMissingNode() ? null : root.path("log_id").asText();
        return TextModerationResult.aggregate(PROVIDER, requestId, findings);
    }

    /**
     * 执行 {@code mapFinding} 逻辑。
     *
     * @param item item 参数
     *
     * @param fallbackDecision fallbackDecision 参数
     *
     * @return 执行结果
     */
    private ModerationFinding mapFinding(JsonNode item, ModerationDecision fallbackDecision) {
        int type = item.path("type").asInt(-1);
        int subType = item.path("subType").asInt(-1);
        int itemConclusionType = item.path("conclusionType").asInt(0);
        ModerationDecision decision = itemConclusionType == 0 ? fallbackDecision : mapDecision(itemConclusionType);
        String label = item.path("msg").asText("type=" + type + ",subType=" + subType);
        BigDecimal rate = probability(item);
        Map<String, Object> details = JsonUtil.getObjectMapper().convertValue(item, MAP_TYPE);
        return new ModerationFinding(scene(type, subType), suggestion(decision), label, rate, details);
    }

    /**
     * 执行 {@code probability} 逻辑。
     *
     * @param item item 参数
     *
     * @return 执行结果
     */
    private BigDecimal probability(JsonNode item) {
        BigDecimal highest = null;
        JsonNode hits = item.path("hits");
        if (hits.isArray()) {
            for (JsonNode hit : hits) {
                if (hit.path("probability").isNumber()) {
                    BigDecimal candidate = hit.path("probability")
                            .decimalValue()
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(4, RoundingMode.HALF_UP);
                    if (highest == null || candidate.compareTo(highest) > 0) {
                        highest = candidate;
                    }
                }
            }
        }
        if (highest == null && item.path("probability").isNumber()) {
            highest = item.path("probability")
                    .decimalValue()
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(4, RoundingMode.HALF_UP);
        }
        return highest;
    }

    /**
     * 执行 {@code accessToken} 逻辑。
     *
     * @return 执行结果
     *
     * @throws Exception 执行失败时
     */
    private String accessToken() throws Exception {
        CachedToken current = cachedToken;
        if (current != null && current.usable()) {
            return current.value();
        }
        synchronized (tokenLock) {
            current = cachedToken;
            if (current != null && current.usable()) {
                return current.value();
            }
            cachedToken = requestAccessToken();
            return cachedToken.value();
        }
    }

    /**
     * 执行 {@code requestAccessToken} 逻辑。
     *
     * @return 执行结果
     *
     * @throws Exception 执行失败时
     */
    private CachedToken requestAccessToken() throws Exception {
        String query = "grant_type=client_credentials&client_id="
                + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(secretKey, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenEndpoint + "?" + query))
                .timeout(timeout)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("百度云 Access Token 获取失败，状态码=" + response.statusCode());
        }
        JsonNode root = JsonUtil.getObjectMapper().readTree(response.body());
        String token = root.path("access_token").asText("");
        long expiresIn = root.path("expires_in").asLong(0);
        if (isBlank(token) || expiresIn <= 0) {
            throw new IllegalStateException("百度云 Access Token 获取失败，错误码="
                    + root.path("error").asText(root.path("error_code").asText("unknown")));
        }
        long usableSeconds = Math.max(1, expiresIn - Math.min(300, Math.max(30, expiresIn / 10)));
        return new CachedToken(token, Instant.now().plusSeconds(usableSeconds));
    }

    /**
     * 执行 {@code invalidateToken} 逻辑。
     */
    private void invalidateToken() {
        synchronized (tokenLock) {
            cachedToken = null;
        }
    }

    /**
     * 执行 {@code mapDecision} 逻辑。
     *
     * @param conclusionType conclusionType 参数
     *
     * @return 执行结果
     */
    private ModerationDecision mapDecision(int conclusionType) {
        return switch (conclusionType) {
            case 1 -> ModerationDecision.PASS;
            case 2 -> ModerationDecision.BLOCK;
            case 3 -> ModerationDecision.REVIEW;
            default -> throw new IllegalStateException("未知的百度云审核结论类型=" + conclusionType);
        };
    }

    /**
     * 执行 {@code suggestion} 逻辑。
     *
     * @param decision decision 参数
     *
     * @return 执行结果
     */
    private String suggestion(ModerationDecision decision) {
        return decision.name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * 百度云文本审核主类型/子类型到场景编码的映射。
     *
     * <p>type=11 官方违禁词库；type=12 文本反作弊（subType：0 低质灌水、1 违禁违规、
     * 2 文本色情、3 敏感信息、4 恶意推广、5 低俗辱骂、6 恶意推广-联系方式、
     * 7 恶意推广-软文推广）；type=13 自定义文本黑名单；type=14 自定义文本白名单。</p>
     *
     * @param type type 参数
     *
     * @param subType subType 参数
     *
     * @return 场景编码
     */
    private String scene(int type, int subType) {
        return switch (type) {
            case 11 -> "political";
            case 12 -> switch (subType) {
                case 0 -> "spam";
                case 1 -> "prohibited";
                case 2 -> "porn";
                case 3 -> "sensitive";
                case 4 -> "ad";
                case 5 -> "abuse";
                case 6 -> "contact";
                case 7 -> "ad";
                default -> "text_type_12_" + subType;
            };
            case 13 -> "custom_blacklist";
            case 14 -> "custom_whitelist";
                // 实测命中：33 不良性邀约/色情资源名称、34 人身攻击、36 微信号等联系方式引流。
            case 33 -> "dating_invitation";
            case 34 -> "attack";
            case 36 -> "contact";
            default -> "type_" + type;
        };
    }

    /**
     * 执行 {@code isBlank} 逻辑。
     *
     * @param value value 参数
     *
     * @return 执行结果
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record CachedToken(String value, Instant expiresAt) {
        /**
         * 执行 {@code usable} 逻辑。
         *
         * @return 执行结果
         */
        boolean usable() {
            return Instant.now().isBefore(expiresAt);
        }
    }

    private static final class BaiduTokenException extends RuntimeException {}
}

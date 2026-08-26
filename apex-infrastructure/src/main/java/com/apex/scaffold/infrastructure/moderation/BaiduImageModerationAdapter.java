package com.apex.scaffold.infrastructure.moderation;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.common.util.JsonUtil;
import com.apex.scaffold.domain.gateway.ImageModerationPort;
import com.apex.scaffold.domain.model.moderation.ImageModerationResult;
import com.apex.scaffold.domain.model.moderation.ModerationDecision;
import com.apex.scaffold.domain.model.moderation.ModerationFinding;
import com.apex.scaffold.domain.model.moderation.ModerationScene;
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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 百度智能云内容审核平台图像审核适配器。
 *
 * <p>封装百度图像审核 API 的鉴权、请求构建和响应解析，
 * 实现 {@link ImageModerationPort} 对接百度内容审核服务。</p>
 *
 * @author weiqiang.ling
 * @version 1.0.0
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(prefix = "image.moderation.baidu", name = "enabled", havingValue = "true")
public class BaiduImageModerationAdapter implements ImageModerationPort {

    private static final String PROVIDER = "BAIDU_IMAGE_CENSOR";
    private static final int TOKEN_INVALID = 110;
    private static final int TOKEN_EXPIRED = 111;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final String apiKey;
    private final String secretKey;
    private final String tokenEndpoint;
    private final String moderationEndpoint;
    private final int strategyId;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final BaiduImagePreprocessor imagePreprocessor;
    private final Object tokenLock = new Object();

    private volatile CachedToken cachedToken;

    /**
     * 构造方法，通过 Spring {@code @Value} 注入百度图像审核配置。
     *
     * @param apiKey 百度云 API Key
     * @param secretKey 百度云 Secret Key
     * @param tokenEndpoint Token 获取端点
     * @param moderationEndpoint 审核 API 端点
     * @param strategyId 审核策略 ID
     * @param timeoutSeconds 超时时间（秒）
     * @param maxBase64Bytes Base64 编码最大字节数
     * @param maxDimension 图片最大边长
     * @param jpegQuality JPEG 压缩质量
     */
    public BaiduImageModerationAdapter(
            @Value("${image.moderation.baidu.api-key:${BAIDU_IMAGE_MODERATION_API_KEY:}}") String apiKey,
            @Value("${image.moderation.baidu.secret-key:${BAIDU_IMAGE_MODERATION_SECRET_KEY:}}") String secretKey,
            @Value("${image.moderation.baidu.token-endpoint:https://aip.baidubce.com/oauth/2.0/token}")
                    String tokenEndpoint,
            @Value("${image.moderation.baidu.endpoint:"
                            + "https://aip.baidubce.com/rest/2.0/solution/v1/img_censor/v2/user_defined}")
                    String moderationEndpoint,
            @Value("${image.moderation.baidu.strategy-id:1}") int strategyId,
            @Value("${image.moderation.baidu.timeout-seconds:10}") long timeoutSeconds,
            @Value("${image.moderation.baidu.max-base64-bytes:9500000}") int maxBase64Bytes,
            @Value("${image.moderation.baidu.max-dimension:4096}") int maxDimension,
            @Value("${image.moderation.baidu.jpeg-quality:0.88}") float jpegQuality) {
        if (isBlank(apiKey) || isBlank(secretKey)) {
            throw new IllegalStateException("启用百度云图片审核时必须配置 API Key 和 Secret Key");
        }
        if (strategyId <= 0 || timeoutSeconds <= 0) {
            throw new IllegalStateException("百度云图片审核 strategyId 和 timeoutSeconds 必须大于 0");
        }
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.tokenEndpoint = tokenEndpoint;
        this.moderationEndpoint = moderationEndpoint;
        this.strategyId = strategyId;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.imagePreprocessor = new BaiduImagePreprocessor(maxBase64Bytes, maxDimension, jpegQuality);
    }

    /**
     * 执行 {@code moderate} 逻辑。
     *
     * @param image image 参数
     *
     * @param scenes scenes 参数
     *
     * @return 执行结果
     */
    @Override
    /**
     * moderate
     *
     * @param image 参数
     *
     * @param scenes 参数
     *
     * @return 结果
     */
    public Mono<ImageModerationResult> moderate(Resource image, List<ModerationScene> scenes) {
        if (image == null || scenes == null || scenes.isEmpty()) {
            return Mono.error(new IllegalArgumentException("图片与审核场景不能为空"));
        }
        return Mono.fromCallable(() -> callBaidu(image))
                .subscribeOn(BlockingIoSchedulers.remote())
                .timeout(timeout.multipliedBy(2).plusSeconds(2));
    }

    /**
     * 执行 {@code callBaidu} 逻辑。
     *
     * @param image image 参数
     *
     * @return 执行结果
     *
     * @throws Exception 执行失败时
     */
    private ImageModerationResult callBaidu(Resource image) throws Exception {
        byte[] preparedImage = imagePreprocessor.prepare(image);
        try {
            return requestModeration(preparedImage, accessToken());
        } catch (BaiduTokenException ignored) {
            invalidateToken();
            return requestModeration(preparedImage, accessToken());
        }
    }

    /**
     * 执行 {@code requestModeration} 逻辑。
     *
     * @param image image 参数
     *
     * @param accessToken accessToken 参数
     *
     * @return 执行结果
     *
     * @throws Exception 执行失败时
     */
    private ImageModerationResult requestModeration(byte[] image, String accessToken) throws Exception {
        String formBody = "image="
                + URLEncoder.encode(Base64.getEncoder().encodeToString(image), StandardCharsets.UTF_8)
                + "&strategyId=" + strategyId;
        URI uri = URI.create(
                moderationEndpoint + "?access_token=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("百度云图片审核 HTTP 调用失败，状态码=" + response.statusCode());
        }
        JsonNode root = JsonUtil.getObjectMapper().readTree(response.body());
        int errorCode = root.path("error_code").asInt(0);
        if (errorCode == TOKEN_INVALID || errorCode == TOKEN_EXPIRED) {
            throw new BaiduTokenException();
        }
        if (errorCode != 0) {
            throw new IllegalStateException("百度云图片审核失败，错误码=" + errorCode + "，错误信息="
                    + root.path("error_msg").asText("unknown"));
        }
        return mapResponse(root);
    }

    /**
     * 执行 {@code mapResponse} 逻辑。
     *
     * @param root root 参数
     *
     * @return 执行结果
     */
    ImageModerationResult mapResponse(JsonNode root) {
        int conclusionType = root.path("conclusionType").asInt(0);
        if (conclusionType == 4) {
            throw new IllegalStateException("百度云图片审核返回审核失败");
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
            details.put("isHitMd5", root.path("isHitMd5").asBoolean(false));
            findings.add(new ModerationFinding(
                    "overall",
                    suggestion(overallDecision),
                    root.path("conclusion").asText("合规"),
                    null,
                    details));
        }
        String requestId =
                root.path("log_id").isMissingNode() ? null : root.path("log_id").asText();
        return new ImageModerationResult(overallDecision, PROVIDER, requestId, null, findings);
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
        return new ModerationFinding(scene(type), suggestion(decision), label, rate, details);
    }

    /**
     * 执行 {@code probability} 逻辑。
     *
     * @param item item 参数
     *
     * @return 执行结果
     */
    private BigDecimal probability(JsonNode item) {
        if (item.has("probability") && item.path("probability").isNumber()) {
            return percentage(item.path("probability").decimalValue());
        }
        BigDecimal highest = null;
        JsonNode stars = item.path("stars");
        if (stars.isArray()) {
            for (JsonNode star : stars) {
                if (star.path("probability").isNumber()) {
                    BigDecimal candidate = percentage(star.path("probability").decimalValue());
                    if (highest == null || candidate.compareTo(highest) > 0) {
                        highest = candidate;
                    }
                }
            }
        }
        return highest;
    }

    /**
     * 执行 {@code percentage} 逻辑。
     *
     * @param probability probability 参数
     *
     * @return 执行结果
     */
    private BigDecimal percentage(BigDecimal probability) {
        return probability.multiply(BigDecimal.valueOf(100)).setScale(4, RoundingMode.HALF_UP);
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
     * 百度云审核类型号到场景名称的静态映射表。
     *
     * @since 1.0.0
     */
    private static final Map<Integer, String> SCENE_MAP = Map.ofEntries(
            Map.entry(0, "forbidden_image"),
            Map.entry(1, "porn"),
            Map.entry(43, "porn"),
            Map.entry(2, "terrorism"),
            Map.entry(44, "terrorism"),
            Map.entry(3, "disgusting"),
            Map.entry(4, "ad"),
            Map.entry(47, "ad"),
            Map.entry(5, "political"),
            Map.entry(16, "political"),
            Map.entry(38, "political"),
            Map.entry(42, "political"),
            Map.entry(6, "quality"),
            Map.entry(7, "custom_blacklist"),
            Map.entry(8, "custom_whitelist"),
            Map.entry(10, "avatar"),
            Map.entry(11, "text"),
            Map.entry(12, "text"),
            Map.entry(13, "text"),
            Map.entry(14, "text"),
            Map.entry(1230, "text"),
            Map.entry(1231, "text"),
            Map.entry(1233, "text"),
            Map.entry(1234, "text"),
            Map.entry(1235, "text"),
            Map.entry(1236, "text"),
            Map.entry(1237, "text"),
            Map.entry(1238, "text"),
            Map.entry(1239, "text"),
            Map.entry(15, "custom_model"),
            Map.entry(21, "prohibited"),
            Map.entry(45, "prohibited"),
            Map.entry(24, "live"),
            Map.entry(39, "public_figure"),
            Map.entry(40, "official_scene"),
            Map.entry(41, "custom_scene"));

    /**
     * 执行 {@code scene} 逻辑。
     *
     * @param type type 参数
     *
     * @return 执行结果
     */
    private String scene(int type) {
        return SCENE_MAP.getOrDefault(type, "type_" + type);
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

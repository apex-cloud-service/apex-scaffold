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
 *
 *<p>封装百度图像审核 API 的鉴权、请求构建和响应解析，
 *实现 {@link ImageModerationPort} 对接百度内容审核服务。</p>
 *
 * @author Apex Team
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
     * 审核图片内容。
     *
     * <p>对指定图片按给定场景列表执行百度云内容审核，在远程弹性调度器上执行，
     * 并设置整体超时时间（接口超时的两倍加两秒）。</p>
     *
     * @param image  待审核的图片资源
     * @param scenes 审核场景列表，不能为空
     * @return 审核结果，包含总体结论与逐项发现
     * @throws IllegalArgumentException 图片为空或审核场景为空时抛出
     */
    @Override
    public Mono<ImageModerationResult> moderate(Resource image, List<ModerationScene> scenes) {
        if (image == null || scenes == null || scenes.isEmpty()) {
            return Mono.error(new IllegalArgumentException("图片与审核场景不能为空"));
        }
        return Mono.fromCallable(() -> callBaidu(image))
                .subscribeOn(BlockingIoSchedulers.remote())
                .timeout(timeout.multipliedBy(2).plusSeconds(2));
    }

    /**
     * 调用百度云图片审核 API。
     *
     * <p>先将图片交给 {@link BaiduImagePreprocessor} 预处理，再携带有效 Access Token
     * 发起审核请求；若首次请求因 Token 失效失败，则作废缓存 Token 并重试一次。</p>
     *
     * @param image 待审核的图片资源
     * @return 审核结果
     * @throws Exception 预处理或调用审核 API 失败时抛出
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
     * 向百度云发起图片审核 HTTP 请求。
     *
     * <p>将图片 Base64 编码后与策略 ID 组成表单请求体，携带 Access Token 调用审核端点；
     * 解析响应并判断返回的错误码，Token 失效或过期时抛出 {@link BaiduTokenException}。</p>
     *
     * @param image       已预处理的图片字节数组
     * @param accessToken 百度云 Access Token
     * @return 审核结果
     * @throws Exception HTTP 调用失败、审核失败或返回 Token 失效时抛出
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
     * 将百度云审核响应解析为领域审核结果。
     *
     * <p>根据响应中的 {@code conclusionType} 计算总体决策，遍历 {@code data} 数组
     * 生成逐项发现；无明细时以总体结论兜底构造单条发现，并提取 {@code log_id} 作为请求 ID。</p>
     *
     * @param root 百度云审核响应 JSON 根节点
     * @return 审核结果
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
     * 将单条审核明细映射为发现项。
     *
     * <p>解析明细中的类型、子类型与结论类型；结论类型缺失时沿用总体决策兜底，
     * 计算命中概率并附带完整明细数据。</p>
     *
     * @param item             百度云审核明细 JSON 节点
     * @param fallbackDecision 明细未给出结论类型时的兜底决策
     * @return 单条审核发现
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
     * 计算审核明细的命中概率。
     *
     * <p>优先取明细自身的 {@code probability}；否则遍历 {@code stars} 数组中的
     * 概率值并返回最大值。</p>
     *
     * @param item 百度云审核明细 JSON 节点
     * @return 命中概率（百分比），无法计算时返回 {@code null}
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
     * 将 0~1 区间的概率换算为百分比并保留四位小数。
     *
     * @param probability 原始概率（0~1）
     * @return 百分比数值（0~100，四位小数）
     */
    private BigDecimal percentage(BigDecimal probability) {
        return probability.multiply(BigDecimal.valueOf(100)).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 获取有效的百度云 Access Token。
     *
     * <p>优先返回缓存中未过期的 Token；否则在锁内再次校验后重新获取，避免并发重复请求。</p>
     *
     * @return 有效的 Access Token
     * @throws Exception 获取 Token 失败时抛出
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
     * 向百度云 OAuth 端点请求新的 Access Token。
     *
     * <p>使用 API Key 与 Secret Key 按客户端凭证模式获取 Token，按过期时间提前
     * 留出安全余量计算可用截止时间。</p>
     *
     * @return 带过期时间的缓存 Token
     * @throws Exception HTTP 调用失败或响应中缺少有效 Token 时抛出
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
     * 作废缓存的 Access Token。
     *
     * <p>在锁内将缓存 Token 置空，触发下次请求时重新获取。</p>
     */
    private void invalidateToken() {
        synchronized (tokenLock) {
            cachedToken = null;
        }
    }

    /**
     * 将百度云审核结论类型映射为领域决策。
     *
     * <p>1 对应通过、2 对应拦截、3 对应人工审核，其余类型视为未知并抛异常。</p>
     *
     * @param conclusionType 百度云审核结论类型
     * @return 对应的领域审核决策
     * @throws IllegalStateException 结论类型未知时抛出
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
     * 生成决策对应的建议动作标识。
     *
     * @param decision 领域审核决策
     * @return 决策名的小写形式（如 {@code pass}、{@code block}）
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
     * 将审核场景类型映射为百度云场景编号。
     *
     * @param type 审核场景类型编号
     * @return 对应的百度云场景编号，未知类型时返回 {@code type_<编号>}
     */
    private String scene(int type) {
        return SCENE_MAP.getOrDefault(type, "type_" + type);
    }

    /**
     * 判断给定文本是否为空或仅含空白字符。
     *
     * @param value 待判断的文本
     * @return 文本为 {@code null} 或空白时返回 {@code true}
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record CachedToken(String value, Instant expiresAt) {
        /**
         * 判断缓存的 Token 是否仍可用。
         *
         * @return 未过有效期时返回 {@code true}
         */
        boolean usable() {
            return Instant.now().isBefore(expiresAt);
        }
    }

    private static final class BaiduTokenException extends RuntimeException {}
}

package com.apex.scaffold.interfaces.util;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Sentinel 资源名称归一化工具。
 *
 * <p>作为 Sentinel 规则资源名与运行时请求 URL 之间对齐的单一事实来源，
 * 供 {@code SentinelConfig}（URL Cleaner 注册）与降级快照保存
 * （{@code DegradeSnapshotRecorder}）共用，避免两套归一化逻辑漂移。</p>
 *
 * <h2>设计说明</h2>
 * <p>Sentinel WebFlux 适配器以请求的原始路径作为资源名，而注册的限流/熔断规则
 * 使用 {@code /api/v1/user/{userId}} 这类含占位符的模板名。
 * 若不做归一化，运行时资源名（如 {@code /api/v1/user/10086}）
 * 与规则名永不相等，导致对应规则失效；降级快照（以归一化资源名为 key）也将
 * 与熔断降级时读取的资源名不一致，造成快照永不命中。</p>
 *
 * <h2>使用示例</h2>
 * <ul>
 *   <li>新增含路径变量的限流/熔断规则时，必须同步在
 *       {@link #PATH_VARIABLE_TEMPLATES} 登记对应模板，否则规则不会生效；</li>
 *   <li>快照保存与降级读取必须使用同一归一化方法，保证资源名严格对齐。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public final class SentinelResourceUtil {

    /** 健康检查探针路径，Sentinel 流量治理应排除。 */
    private static final String ACTUATOR_HEALTH_PATH = "/actuator/health";

    /** 健康检查精确路径集合，Sentinel 流量治理应排除。 */
    private static final Set<String> HEALTH_EXACT_PATHS = Set.of(ACTUATOR_HEALTH_PATH, "/api/v1/health", "/health");

    /** 健康检查路径前缀（含子路径），如 {@code /actuator/health/liveness}。 */
    private static final List<String> HEALTH_PREFIXES = List.of(ACTUATOR_HEALTH_PATH + "/");

    /**
     * 含路径变量占位符的 Sentinel 规则资源模板。
     *
     * <p>规则注册与 URL 归一化均以此列表为基准：URL Cleaner 将运行时 URL
     * 与模板逐段匹配，命中后返回模板名，使运行时资源与规则资源对齐。</p>
     */
    public static final List<String> PATH_VARIABLE_TEMPLATES = List.of("/api/v1/user/{userId}");

    /**
     * 私有构造器，防止实例化工具类。
     */
    private SentinelResourceUtil() {
        // 工具类不实例化
    }

    /**
     * 清理 Sentinel 资源名称。
     *
     * <p>对健康检查相关路径（{@code /actuator/health}、{@code /api/v1/health} 等）
     * 返回 {@code null}，使其不受 Sentinel 流控规则约束；其余路径先通过
     * {@link #normalizePathVariables(String)} 归一化含路径变量的资源名，再返回。</p>
     *
     * @param originalUrl 原始请求 URL
     * @return 清理后的资源名称，健康检查路径返回 {@code null}
     */
    public static String cleanSentinelResource(String originalUrl) {
        if (originalUrl == null || isHealthCheckPath(originalUrl)) {
            return null;
        }
        return normalizePathVariables(originalUrl);
    }

    /**
     * 判断 URL 是否为健康检查探针路径。
     *
     * <p>精确命中 {@link #HEALTH_EXACT_PATHS} 或命中 {@link #HEALTH_PREFIXES} 中的前缀
     * （如 {@code /actuator/health/liveness}）即视为健康检查路径，返回 {@code true}。</p>
     *
     * @param url 请求 URL
     * @return 是健康检查路径返回 {@code true}，否则返回 {@code false}
     */
    private static boolean isHealthCheckPath(String url) {
        return HEALTH_EXACT_PATHS.contains(url) || HEALTH_PREFIXES.stream().anyMatch(url::startsWith);
    }

    /**
     * 将含路径变量的运行时 URL 归一化为 Sentinel 规则模板名。
     *
     * <p>按 {@link #PATH_VARIABLE_TEMPLATES} 中的模板逐段匹配，命中后返回模板名；
     * 未命中任何模板时返回原 URL（如 {@code /api/v1/auth/login} 等无路径变量的静态路径）。</p>
     *
     * @param rawUrl 请求原始路径
     * @return 归一化后的规则资源名；{@code rawUrl} 为 {@code null} 时返回 {@code null}
     */
    public static String normalizePathVariables(String rawUrl) {
        if (rawUrl == null) {
            return null;
        }
        return PATH_VARIABLE_TEMPLATES.stream()
                .filter(template -> matchesTemplate(template, rawUrl))
                .findFirst()
                .orElse(rawUrl);
    }

    /**
     * 逐段比较模板与 URL 是否匹配。
     *
     * <p>按 {@code /} 切分后逐段比较：模板段为 {@code {xxx}} 占位符时匹配任意非空段，
     * 否则要求字面值完全相等；两段数量必须一致。</p>
     *
     * @param template 规则资源模板（含占位符）
     * @param rawUrl   请求原始路径
     * @return 匹配返回 {@code true}，否则返回 {@code false}
     */
    private static boolean matchesTemplate(String template, String rawUrl) {
        String[] templateSegments = template.split("/");
        String[] urlSegments = rawUrl.split("/");
        if (templateSegments.length != urlSegments.length) {
            return false;
        }
        return IntStream.range(0, templateSegments.length).allMatch(i -> {
            String templateSegment = templateSegments[i];
            String urlSegment = urlSegments[i];
            return templateSegment.startsWith("{") && templateSegment.endsWith("}")
                    ? !urlSegment.isEmpty()
                    : templateSegment.equals(urlSegment);
        });
    }
}

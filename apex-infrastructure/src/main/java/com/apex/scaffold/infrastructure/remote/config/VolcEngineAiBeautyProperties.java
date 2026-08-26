package com.apex.scaffold.infrastructure.remote.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 火山传统修图 V2.0（Custom AI Beauty）配置。
 *
 * <p>对象存储 TOS 与修图 OpenAPI 使用不同凭证，因此在已有
 * {@code storage.volcengine} 配置下增加独立的 {@code ai-beauty} 命名空间。
 * API Key 只用于请求头，不会写入任务审计或日志。</p>
 * @author weiqiang.ling
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "storage.volcengine.ai-beauty")
public class VolcEngineAiBeautyProperties {
    public static final String DEFAULT_BASE_URL = "https://kickart.volces.com";
    public static final String DEFAULT_SUBMIT_PATH = "/openapi/ai_effect/submit";
    public static final String DEFAULT_QUERY_PATH = "/openapi/ai_effect/query";
    /** Full URL aliases matching the Python adapter's environment names. */
    public static final String DEFAULT_SUBMIT_URL = DEFAULT_BASE_URL + DEFAULT_SUBMIT_PATH;

    public static final String DEFAULT_QUERY_URL = DEFAULT_BASE_URL + DEFAULT_QUERY_PATH;

    /** 是否启用直调链路；关闭时由应用层选择其它供应商或返回配置错误。 */
    private boolean enabled;
    /** 火山 AI Beauty API Key。 */
    private String apiKey = "";
    /** API 基础地址，测试环境可指向 WireMock。 */
    private String baseUrl = DEFAULT_BASE_URL;
    /** 提交相对路径。 */
    private String submitPath = DEFAULT_SUBMIT_PATH;
    /** 查询相对路径。 */
    private String queryPath = DEFAULT_QUERY_PATH;
    /** RemoteApiClient 服务名。 */
    private String serviceName = "volc-ai-beauty";
    /** 建连超时（毫秒），由 RemoteApiClient 服务配置最终执行。 */
    private int connectTimeoutMillis = 5_000;
    /** 读超时（毫秒），由 RemoteApiClient 服务配置最终执行。 */
    private int readTimeoutMillis = 30_000;

    /** 返回完整提交 URL，便于日志与测试展示（不包含 API Key）。 */
    public String getSubmitUrl() {
        return join(baseUrl, submitPath);
    }

    /** 返回完整查询 URL，便于日志与测试展示（不包含 API Key）。 */
    public String getQueryUrl() {
        return join(baseUrl, queryPath);
    }

    /**
     * 执行对应的业务处理；该操作由 {@code join} 定义。
     *
     * @param base 方法输入 {@code base}，类型为 {@code String}
     * @param path 方法输入 {@code path}，类型为 {@code String}
     * @return 操作结果，类型为 {@code String}
     */
    private String join(String base, String path) {
        if (base == null || path == null) {
            return null;
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) + path : base + path;
    }

    /**
     * 校验能否向供应商发起请求。
     *
     * @throws IllegalStateException 配置启用但缺少 API Key 或地址非法
     */
    public void validateForRequest() {
        if (!enabled) {
            throw new IllegalStateException("火山 AI Beauty 直调链路未启用");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("火山 AI Beauty API Key 未配置");
        }
        if (baseUrl == null
                || baseUrl.isBlank()
                || !(baseUrl.startsWith("https://") || baseUrl.startsWith("http://"))) {
            throw new IllegalStateException("火山 AI Beauty baseUrl 必须为 HTTP(S) 地址");
        }
        if (submitPath == null || !submitPath.startsWith("/")) {
            throw new IllegalStateException("火山 AI Beauty submitPath 必须以 / 开头");
        }
        if (queryPath == null || !queryPath.startsWith("/")) {
            throw new IllegalStateException("火山 AI Beauty queryPath 必须以 / 开头");
        }
    }
}

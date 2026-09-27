package com.apex.scaffold.application.support.sso;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * CAS 协议适配器。
 *
 * <p>实现 CAS(Central Authentication Service)单点登录协议的适配器,
 * 支持 CAS 2.0/3.0 协议的认证、登出和连接测试。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CasProtocolAdapter implements SsoProtocolAdapter {

    /** CAS 认证成功 XML 标签。 */
    private static final String CAS_AUTH_SUCCESS_TAG = "<cas:authenticationSuccess>";

    /** CAS 认证失败 XML 标签。 */
    private static final String CAS_AUTH_FAILURE_TAG = "<cas:authenticationFailure>";

    /** Map 初始容量:用于响应结果(预期 2-5 个键值对)。 */
    private static final int RESULT_MAP_CAPACITY = 8;

    /** Map 初始容量:用于用户属性(预期 2-10 个属性)。 */
    private static final int ATTRIBUTES_MAP_CAPACITY = 16;

    private final WebClient.Builder webClientBuilder;

    @Override
    public Mono<Map<String, Object>> initiate(Map<String, Object> config, String callbackUrl) {
        return Mono.fromCallable(() -> {
            Map<String, Object> result = new HashMap<>(RESULT_MAP_CAPACITY);
            String authEndpoint = (String) config.get("authEndpoint");
            String encodedService = URLEncoder.encode(callbackUrl, StandardCharsets.UTF_8);
            result.put("redirectUrl", authEndpoint + "/login?service=" + encodedService);
            return result;
        });
    }

    @Override
    public Mono<Map<String, Object>> authenticate(Map<String, Object> config, Map<String, String> callbackParams) {
        String ticket = callbackParams.get("ticket");
        if (ticket == null || ticket.isBlank()) {
            return Mono.error(new IllegalArgumentException("CAS ticket 参数缺失"));
        }

        String serviceValidateUrl = (String) config.get("serviceValidateUrl");
        String service = callbackParams.get("service");

        return webClientBuilder
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path(serviceValidateUrl)
                        .queryParam("ticket", ticket)
                        .queryParam("service", service)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .flatMap(xmlResponse -> parseCasResponse(xmlResponse))
                .doOnError(e ->
                        log.error("CAS ticket 验证失败: ticket={}, service={}, error={}", ticket, service, e.getMessage()));
    }

    @Override
    public Mono<Map<String, Object>> logout(Map<String, Object> config, String sessionToken) {
        return Mono.fromCallable(() -> {
            Map<String, Object> result = new HashMap<>(RESULT_MAP_CAPACITY);
            String logoutEndpoint = (String) config.get("logoutEndpoint");
            if (logoutEndpoint != null && !logoutEndpoint.isBlank()) {
                result.put("logoutUrl", logoutEndpoint + "/logout");
            }
            result.put("success", true);
            return result;
        });
    }

    @Override
    public Mono<Map<String, Object>> getMetadata(Map<String, Object> config) {
        return Mono.fromCallable(() -> {
            Map<String, Object> metadata = new HashMap<>(RESULT_MAP_CAPACITY);
            metadata.put("protocol", "CAS");
            metadata.put("version", config.getOrDefault("version", "3.0"));
            metadata.put("authEndpoint", config.get("authEndpoint"));
            metadata.put("serviceValidateUrl", config.get("serviceValidateUrl"));
            metadata.put("logoutEndpoint", config.get("logoutEndpoint"));
            return metadata;
        });
    }

    @Override
    public Mono<Map<String, Object>> testConnection(Map<String, Object> config) {
        String authEndpoint = (String) config.get("authEndpoint");
        if (authEndpoint == null || authEndpoint.isBlank()) {
            return Mono.fromCallable(() -> {
                Map<String, Object> result = new HashMap<>(RESULT_MAP_CAPACITY);
                result.put("success", false);
                result.put("message", "CAS authEndpoint 配置缺失");
                return result;
            });
        }

        return webClientBuilder
                .build()
                .get()
                .uri(authEndpoint + "/login")
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(5))
                .map(response -> {
                    Map<String, Object> result = new HashMap<>(RESULT_MAP_CAPACITY);
                    result.put(
                            "success",
                            response.getStatusCode().is2xxSuccessful()
                                    || response.getStatusCode().is3xxRedirection());
                    result.put("message", "CAS 服务端点可访问");
                    result.put("statusCode", response.getStatusCode().value());
                    return result;
                })
                .onErrorResume(e -> {
                    Map<String, Object> result = new HashMap<>(RESULT_MAP_CAPACITY);
                    result.put("success", false);
                    result.put("message", "CAS 连接失败: " + e.getMessage());
                    return Mono.just(result);
                });
    }

    private Mono<Map<String, Object>> parseCasResponse(String xmlResponse) {
        return Mono.fromCallable(() -> {
            Map<String, Object> userInfo = new HashMap<>(RESULT_MAP_CAPACITY);

            if (xmlResponse.contains(CAS_AUTH_SUCCESS_TAG)) {
                String username = extractXmlTag(xmlResponse, "cas:user");
                userInfo.put("userId", username);
                userInfo.put("username", username);

                Map<String, String> attributes = new HashMap<>(ATTRIBUTES_MAP_CAPACITY);
                String email = extractXmlTag(xmlResponse, "cas:email");
                if (email != null) {
                    attributes.put("email", email);
                }
                String displayName = extractXmlTag(xmlResponse, "cas:displayName");
                if (displayName != null) {
                    attributes.put("displayName", displayName);
                }
                userInfo.put("attributes", attributes);
            } else if (xmlResponse.contains(CAS_AUTH_FAILURE_TAG)) {
                String errorMsg = extractXmlTag(xmlResponse, "cas:authenticationFailure");
                throw new RuntimeException("CAS 认证失败: " + errorMsg);
            } else {
                throw new RuntimeException("无法解析 CAS 响应");
            }

            return userInfo;
        });
    }

    private String extractXmlTag(String xml, String tagName) {
        String openTag = "<" + tagName + ">";
        String closeTag = "</" + tagName + ">";
        int start = xml.indexOf(openTag);
        int end = xml.indexOf(closeTag);
        if (start != -1 && end != -1 && end > start) {
            return xml.substring(start + openTag.length(), end).trim();
        }
        return null;
    }
}

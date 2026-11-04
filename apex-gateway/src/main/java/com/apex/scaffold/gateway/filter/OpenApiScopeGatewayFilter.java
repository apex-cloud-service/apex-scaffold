package com.apex.scaffold.gateway.filter;

import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.gateway.capability.TenantCapabilityClient;
import com.apex.scaffold.gateway.support.GatewayWebUtil;
import io.jsonwebtoken.Claims;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 开放接口 scope 断言与能力门禁过滤器（D2/D4，全局）.
 *
 * <p>仅对 {@code /open-api/**} 生效，执行三道断言后放行：
 *
 * <ol>
 *   <li><b>令牌来源</b>：拒绝用户个人令牌（含 {@code userId} claim）与非开放授予类型
 *       （{@code grant_type} 限 client_credentials / authorization_code，遗留无 claim 令牌
 *       按 client_id + scope 放行）；</li>
 *   <li><b>scope 断言</b>：从路由元数据 {@code metadata.open-api} 读取
 *       {@code read-scopes}/{@code write-scopes}（GET/HEAD 用 read，其余方法用 write，
 *       any-of 满足），令牌 scope 不满足返回 403 + {@link ResultCode#OPEN_SCOPE_INSUFFICIENT}；
 *       随后将令牌 scope 注入可信头 {@code X-Client-Scopes} 供下游复验（外部伪造值由
 *       {@link JwtSignatureFilter} 全局剥离）；</li>
 *   <li><b>能力门禁</b>：经 {@link TenantCapabilityClient} 查询租户订阅（共享
 *       {@code CapabilityGate} 判定），未订阅返回 403 + {@link ResultCode#CAPABILITY_NOT_SUBSCRIBED}，
 *       内部端点不可达 fail-closed 返回 503。</li>
 * </ol>
 *
 * @author Apex Team
 * @since 1.3.1
 * @see JwtSignatureFilter
 * @see TenantCapabilityClient
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenApiScopeGatewayFilter implements GlobalFilter, Ordered {

    /** 过滤器顺序：JWT 验签/上下文中继之后、限流（30）之前 */
    public static final int ORDER = 25;

    /** 注入下游的可信 scope 头（令牌 scope 原文，空格分隔） */
    public static final String SCOPE_HEADER = "X-Client-Scopes";

    /** 注入下游的可信客户端 ID 头（开放应用 client_id，供分档限流/服务侧审计消费） */
    public static final String CLIENT_ID_HEADER = "X-Client-Id";

    /** 注入下游的能力标记头（服务侧据此做能力订阅二次校验，D4 纵深防御） */
    public static final String CAPABILITY_HEADER = "X-Open-Api-Capability";

    /** 路由元数据键（{@code spring.cloud.gateway.routes[].metadata.open-api}） */
    public static final String METADATA_KEY = "open-api";

    private static final String OPEN_API_PREFIX = "/open-api/";
    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_CLIENT_ID = "client_id";
    private static final String CLAIM_GRANT_TYPE = "grant_type";
    private static final String CLAIM_SCOPE = "scope";
    private static final String CLAIM_TENANT_ID = "tenant_id";

    /** 开放接口接受的授权类型；password 等面向个人用户的授予类型一律拒绝 */
    private static final Set<String> ALLOWED_GRANT_TYPES = Set.of("client_credentials", "authorization_code");

    /** 读方法族使用 read-scopes 断言，其余方法族使用 write-scopes */
    private static final Set<HttpMethod> READ_METHODS = Set.of(HttpMethod.GET, HttpMethod.HEAD);

    private final TenantCapabilityClient capabilityClient;

    /**
     * 对 {@code /open-api/**} 执行令牌来源、scope 与租户能力三道断言.
     *
     * @param exchange 请求交换对象
     * @param chain 网关后续过滤链
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!path.startsWith(OPEN_API_PREFIX)) {
            return chain.filter(exchange);
        }
        Claims claims = exchange.getAttribute(JwtSignatureFilter.ATTR_CLAIMS);
        if (claims == null) {
            // 防御：正常链路下 JwtSignatureFilter（order 10）未验签通过不会到达此处
            return GatewayWebUtil.writeError(exchange, HttpStatus.UNAUTHORIZED, ResultCode.UNAUTHORIZED);
        }
        ResultCode denial = deny(claims);
        if (denial != null) {
            log.debug("开放接口令牌来源断言拒绝: path={}, reason={}", path, denial.getCode());
            return GatewayWebUtil.writeError(exchange, HttpStatus.FORBIDDEN, denial);
        }
        Set<String> tokenScopes = scopes(claims.get(CLAIM_SCOPE));
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        OpenApiMetadata metadata = OpenApiMetadata.from(route);
        Set<String> required = metadata == null
                ? Set.of()
                : metadata.requiredFor(exchange.getRequest().getMethod());
        boolean scopeSatisfied =
                metadata != null && !required.isEmpty() && required.stream().anyMatch(tokenScopes::contains);
        if (!scopeSatisfied) {
            log.debug("开放接口 scope 断言失败: path={}, tokenScopes={}, required={}", path, tokenScopes, required);
            return GatewayWebUtil.writeError(exchange, HttpStatus.FORBIDDEN, ResultCode.OPEN_SCOPE_INSUFFICIENT);
        }
        String tenant = str(claims.get(CLAIM_TENANT_ID));
        return capabilityClient
                .subscribed(tenant, metadata.capability())
                .flatMap(subscribed -> {
                    if (!Boolean.TRUE.equals(subscribed)) {
                        log.info(
                                "租户能力未订阅，拒绝开放接口访问: tenant={}, capability={}, path={}",
                                tenant,
                                metadata.capability(),
                                path);
                        return GatewayWebUtil.writeError(
                                exchange, HttpStatus.FORBIDDEN, ResultCode.CAPABILITY_NOT_SUBSCRIBED);
                    }
                    ServerHttpRequest request = exchange.getRequest()
                            .mutate()
                            .headers(headers -> {
                                headers.set(SCOPE_HEADER, String.join(" ", tokenScopes));
                                headers.set(CLIENT_ID_HEADER, str(claims.get(CLAIM_CLIENT_ID)));
                                headers.set(CAPABILITY_HEADER, metadata.capability());
                            })
                            .build();
                    return chain.filter(exchange.mutate().request(request).build());
                })
                .onErrorResume(error -> {
                    log.warn("租户能力订阅查询失败，开放接口降级拒绝: tenant={}, path={}, reason={}", tenant, path, error.getMessage());
                    return GatewayWebUtil.writeError(
                            exchange, HttpStatus.SERVICE_UNAVAILABLE, ResultCode.SERVICE_UNAVAILABLE);
                });
    }

    /**
     * 令牌来源断言：命中个人令牌/非开放令牌特征时返回拒绝码，放行返回 {@code null}.
     *
     * @param claims 已验签令牌声明
     * @return 拒绝码；放行为 {@code null}
     */
    private ResultCode deny(Claims claims) {
        // 个人登录令牌（TokenServiceImpl 签发）携带 userId，开放接口仅接受应用令牌
        if (claims.get(CLAIM_USER_ID) != null) {
            return ResultCode.OPEN_PERSONAL_TOKEN_REJECTED;
        }
        String grant = str(claims.get(CLAIM_GRANT_TYPE));
        if (!grant.isEmpty() && !ALLOWED_GRANT_TYPES.contains(grant)) {
            return ResultCode.OPEN_PERSONAL_TOKEN_REJECTED;
        }
        if (str(claims.get(CLAIM_CLIENT_ID)).isEmpty()) {
            return ResultCode.OPEN_PERSONAL_TOKEN_REJECTED;
        }
        if (str(claims.get(CLAIM_SCOPE)).isEmpty()) {
            return ResultCode.OPEN_SCOPE_INSUFFICIENT;
        }
        return null;
    }

    private static Set<String> scopes(Object raw) {
        if (raw == null) {
            return Set.of();
        }
        return new LinkedHashSet<>(Arrays.asList(raw.toString().trim().split("\\s+")));
    }

    private static String str(Object raw) {
        return raw == null ? "" : raw.toString().trim();
    }

    /** 返回全局过滤器执行顺序：验证签（10）/上下文中继（20）之后、限流（30）之前。 */
    @Override
    public int getOrder() {
        return ORDER;
    }

    /**
     * 路由元数据 {@code open-api} 节解析.
     *
     * @param capability 租户订阅能力编码（D4）
     * @param readScopes GET/HEAD 方法族要求的 scope（any-of）
     * @param writeScopes 其余方法族要求的 scope（any-of）
     */
    record OpenApiMetadata(String capability, Set<String> readScopes, Set<String> writeScopes) {

        static OpenApiMetadata from(Route route) {
            if (route == null || !(route.getMetadata().get(METADATA_KEY) instanceof Map<?, ?> map)) {
                return null;
            }
            String capability = str(map.get("capability"));
            return new OpenApiMetadata(
                    capability.isEmpty() ? null : capability,
                    scopeList(map.get("read-scopes")),
                    scopeList(map.get("write-scopes")));
        }

        Set<String> requiredFor(HttpMethod method) {
            return READ_METHODS.contains(method) ? readScopes : writeScopes;
        }

        private static Set<String> scopeList(Object raw) {
            if (raw instanceof Iterable<?> iterable) {
                return collect(iterable);
            }
            if (raw instanceof Map<?, ?> map) {
                // Spring Boot Binder 绑定 Object 值时会把 YAML 列表退化为 {0=v, 1=v} 形式的 Map
                return collect(map.values());
            }
            if (raw instanceof String value && !value.isBlank()) {
                return Set.of(value.trim());
            }
            return Set.of();
        }

        private static Set<String> collect(Iterable<?> items) {
            Set<String> result = new LinkedHashSet<>();
            for (Object item : items) {
                if (item != null && !item.toString().isBlank()) {
                    result.add(item.toString());
                }
            }
            return result;
        }
    }
}

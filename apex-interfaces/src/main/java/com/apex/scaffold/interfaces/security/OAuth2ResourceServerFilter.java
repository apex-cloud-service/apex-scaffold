package com.apex.scaffold.interfaces.security;

import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.service.OAuth2AuthorizationService;
import com.apex.scaffold.interfaces.util.MessageUtils;
import com.apex.scaffold.interfaces.util.ResponseUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * OAuth2资源服务器过滤器
 *
 * <p>拦截携带OAuth2 Token的请求，验证Token有效性并设置Security Context。
 * 该过滤器仅处理以{@code /oauth2/api/}开头的资源端点,其他端点由JWT过滤器处理。
 *
 * <h2>Token验证流程</h2>
 * <ol>
 *   <li>从Authorization请求头提取Bearer Token</li>
 *   <li>调用OAuth2AuthorizationService验证Token</li>
 *   <li>从Token中提取scope并转换为Spring Security权限(SCOPE_前缀)</li>
 *   <li>创建Authentication对象并设置到SecurityContext</li>
 * </ol>
 *
 * <h2>权限格式</h2>
 * <p>OAuth2 scope会被转换为{@code SCOPE_}前缀的权限,例如:
 * <ul>
 *   <li>scope: "read" → authority: "SCOPE_READ"</li>
 *   <li>scope: "user:profile" → authority: "SCOPE_USER_PROFILE"</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2.0.0
 */
@Slf4j
@Component
@Profile("!it")
@RequiredArgsConstructor
public class OAuth2ResourceServerFilter implements WebFilter {
    
    private final OAuth2AuthorizationService authorizationService;
    private final MessageUtils messageUtils;
    
    private static final String TOKEN_PREFIX = "Bearer ";
    private static final String OAUTH2_API_PREFIX = "/oauth2/api/";
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        
        if (!path.startsWith(OAUTH2_API_PREFIX)) {
            return chain.filter(exchange);
        }
        
        String token = extractToken(exchange);
        
        if (token == null) {
            return handleError(exchange, ResultCode.UNAUTHORIZED);
        }
        
        return authorizationService.validateToken(token)
                .flatMap(tokenEntity -> {
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    
                    if (tokenEntity.getScope() != null && !tokenEntity.getScope().isEmpty()) {
                        String[] scopes = tokenEntity.getScope().split(" ");
                        Arrays.stream(scopes)
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .forEach(scope -> {
                                    String normalizedScope = scope.toUpperCase().replace(":", "_");
                                    authorities.add(new SimpleGrantedAuthority("SCOPE_" + normalizedScope));
                                });
                    }
                    
                    String principal = tokenEntity.getUserId() != null 
                            ? tokenEntity.getUserId() 
                            : tokenEntity.getClientId();
                    
                    Authentication authentication = new UsernamePasswordAuthenticationToken(
                            principal, null, authorities);
                    SecurityContext securityContext = new SecurityContextImpl(authentication);
                    
                    exchange.getAttributes().put("oauth2.clientId", tokenEntity.getClientId());
                    if (tokenEntity.getUserId() != null) {
                        exchange.getAttributes().put("oauth2.userId", tokenEntity.getUserId());
                    }
                    
                    return chain.filter(exchange)
                            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));
                })
                .switchIfEmpty(handleError(exchange, ResultCode.UNAUTHORIZED));
    }
    
    private String extractToken(ServerWebExchange exchange) {
        String bearerToken = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (bearerToken != null && bearerToken.startsWith(TOKEN_PREFIX)) {
            return bearerToken.substring(TOKEN_PREFIX.length());
        }
        return null;
    }
    
    private Mono<Void> handleError(ServerWebExchange exchange, ResultCode resultCode) {
        return Mono.deferContextual(ctx -> {
            Locale locale = ctx.getOrDefault(Locale.class, Locale.SIMPLIFIED_CHINESE);
            String msg = messageUtils.get(locale, resultCode.getMessageKey());
            return ResponseUtil.unauthorized(exchange, msg);
        });
    }
}

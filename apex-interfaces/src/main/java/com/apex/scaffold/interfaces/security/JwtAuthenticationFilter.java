package com.apex.scaffold.interfaces.security;

import com.apex.scaffold.common.constant.SystemConstants;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.JwtUtil;
import com.apex.scaffold.domain.model.entity.RoleEntity;
import com.apex.scaffold.domain.repository.RoleRepository;
import com.apex.scaffold.domain.repository.UserRepository;
import com.apex.scaffold.domain.service.TokenBlacklistService;
import com.apex.scaffold.domain.service.TokenService;
import com.apex.scaffold.interfaces.util.MessageUtils;
import com.apex.scaffold.interfaces.util.ResponseUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * JWT认证过滤器
 *
 * <p>拦截请求，从请求头中提取JWT Token并进行认证，是Spring Security WebFlux安全体系的核心组件。
 * 同时集成了国际化支持，认证失败时的错误消息会根据客户端语言偏好自动切换。
 *
 * <h2>设计模式</h2>
 * <ul>
 *   <li><strong>责任链模式</strong>：实现 {@link WebFilter} 接口，在过滤器链中完成JWT认证逻辑</li>
 *   <li><strong>策略模式</strong>：根据请求路径判断是否为公开路径，应用不同的认证策略</li>
 *   <li><strong>模板方法模式</strong>：通过 {@code filter()} 方法定义认证流程骨架，
 *       具体步骤由 {@code authenticateToken()}、{@code getUserAuthorities()} 等方法实现</li>
 * </ul>
 *
 * <h2>认证流程</h2>
 * <ol>
 *   <li>{@link #filter(ServerWebExchange, WebFilterChain)}：入口方法，判断公开路径、提取Token、检查黑名单</li>
 *   <li>{@link #authenticateToken(ServerWebExchange, WebFilterChain, String)}：解析Token，处理异常</li>
 *   <li>{@link #getUserAuthorities(String, boolean)}：根据OpenID获取用户权限（含敏感路径强制刷新）</li>
 *   <li>{@link #fetchAndCacheUserRoles(Long)}：缓存未命中时，从数据库查询角色并缓存</li>
 *   <li>{@link #buildAuthoritiesFromRoles(List)}：将角色列表转换为Spring Security权限对象</li>
 *   <li>{@link #setAuthenticationContext(ServerWebExchange, WebFilterChain, String, List)}：创建认证信息并存入SecurityContext</li>
 * </ol>
 *
 * <h2>国际化支持</h2>
 * <p>
 * 通过 {@link MessageUtils} 从 Reactor Context 中读取由
 * {@link com.apex.scaffold.interfaces.filter.LocaleWebFilter} 写入的 Locale，
 * 将认证错误消息翻译为客户端偏好的语言。
 * </p>
 *
 * <h2>性能优化</h2>
 * <p>
 * 使用 Caffeine 本地缓存存储用户角色权限，TTL 60秒，避免频繁数据库查询。
 * 缓存键为 userId，值为 {@link List}&lt;{@link SimpleGrantedAuthority}&gt;。
 * </p>
 *
 * @author Apex Team
 * @version 3.1.0
 * @since 1.0.0
 * @see com.apex.scaffold.interfaces.util.MessageUtils
 * @see com.apex.scaffold.interfaces.filter.LocaleWebFilter
 */
@Slf4j
@Component
@Profile("!it")
public class JwtAuthenticationFilter implements WebFilter {

    private static final String ACCESS_TOKEN_TYPE = "access";

    /** Token 前缀标识 */
    private static final String TOKEN_PREFIX = "Bearer ";

    /** Ant风格的路径匹配器，用于公开路径匹配 */
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /** HTTP状态码常量 */
    private static final int HTTP_STATUS_UNAUTHORIZED = 401;

    private static final int HTTP_STATUS_FORBIDDEN = 403;
    private static final int HTTP_STATUS_NOT_FOUND = 404;

    /**
     * 响应头名称：当 AccessToken 近过期（在 renewalThreshold 内即将到期）时，
     * 过滤器自动生成新 Token 并通过此响应头下发给客户端。
     *
     * <p>客户端应始终检查此响应头，若存在则替换本地 AccessToken。</p>
     */
    static final String NEW_ACCESS_TOKEN_HEADER = "X-New-Access-Token";

    /** AccessToken 默认过期时间（毫秒），默认 2 小时 */
    private static final long DEFAULT_ACCESS_TOKEN_EXPIRE_MS = 7200000L;

    /** Token 续期窗口默认值（毫秒），默认 30 分钟——即剩余有效期小于此值自动续期 */
    private static final long DEFAULT_RENEWAL_THRESHOLD_MS = 1800000L;

    /** Token 黑名单服务 */
    private final TokenBlacklistService tokenBlacklistService;

    /** Token 服务，用于生成和存储新 AccessToken */
    private final TokenService tokenService;

    /** 国际化消息工具，用于解析多语言错误消息 */
    private final MessageUtils messageUtils;

    /** 用户仓储，用于根据 OpenID 查询用户信息 */
    private final UserRepository userRepository;

    /** 角色仓储，用于根据用户ID查询用户角色列表 */
    private final RoleRepository roleRepository;

    /** AccessToken 过期时间（毫秒） */
    @Value("${jwt.access-token-expire:" + DEFAULT_ACCESS_TOKEN_EXPIRE_MS + "}")
    private long accessTokenExpireMs;

    /**
     * AccessToken 续期阈值（毫秒）：当 Token 剩余有效期小于此值时自动生成新 Token。
     *
     * <p>默认值为 {@value #DEFAULT_RENEWAL_THRESHOLD_MS}（30 分钟），
     * 即可配置为 {@code 0} 完全禁用滑动刷新。</p>
     */
    @Value("${jwt.access-token-renew-threshold:" + DEFAULT_RENEWAL_THRESHOLD_MS + "}")
    private long renewalThresholdMs;

    /** 用户角色本地缓存，键为 userId，值为角色权限列表，TTL 60秒避免频繁查库 */
    private final Cache<Long, List<SimpleGrantedAuthority>> userRolesCache = Caffeine.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .maximumSize(10000)
            .build();

    /**
     * 构造方法注入依赖
     *
     * @param tokenBlacklistService Token 黑名单服务，用于验证 Token 是否已注销
     * @param tokenService          Token 服务，用于生成和存储新 AccessToken（滑动刷新）
     * @param messageUtils          国际化消息工具，用于解析多语言错误消息
     * @param userRepository        用户仓储，用于根据 OpenID 查询用户信息
     * @param roleRepository        角色仓储，用于根据用户ID查询用户角色列表
     */
    public JwtAuthenticationFilter(
            TokenBlacklistService tokenBlacklistService,
            TokenService tokenService,
            MessageUtils messageUtils,
            UserRepository userRepository,
            RoleRepository roleRepository) {
        this.tokenBlacklistService = tokenBlacklistService;
        this.tokenService = tokenService;
        this.messageUtils = messageUtils;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    /**
     * 过滤请求并进行 JWT 认证
     *
     * <p>认证流程：
     *
     * <ol>
     *   <li>判断是否为精确配置的公开路径；公开请求直接放行且不解析 Token</li>
     *   <li>从请求头中提取 Bearer Token</li>
     *   <li>检查 Token 是否在黑名单中（已注销）</li>
     *   <li>验证 Token 签名和有效期</li>
     *   <li>通过 OpenID 查询用户真实角色并设置 authorities</li>
     *   <li>创建 {@link Authentication} 并写入 SecurityContext</li>
     * </ol>
     *
     * @param exchange ServerWebExchange 交换对象
     * @param chain    WebFilterChain 过滤器链
     * @return 空响应 Mono，表示过滤完成
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String token = extractToken(request);

        if (token == null) {
            return handleError(exchange, ResultCode.UNAUTHORIZED);
        }

        return tokenBlacklistService
                .isBlacklisted(token)
                .flatMap(inBlacklist -> inBlacklist
                        ? handleError(exchange, ResultCode.UNAUTHORIZED)
                        : authenticateToken(exchange, chain, token));
    }

    /**
     * 验证 Token 并完成认证流程
     *
     * <p>解析 Token 获取 OpenID，查询用户权限，设置认证上下文。
     * 处理 Token 过期、无效等异常情况。
     *
     * @param exchange ServerWebExchange 交换对象
     * @param chain    WebFilterChain 过滤器链
     * @param token    JWT Token 字符串
     * @return 认证成功返回过滤链继续处理，失败返回错误响应
     */
    private Mono<Void> authenticateToken(ServerWebExchange exchange, WebFilterChain chain, String token) {
        try {
            Claims claims = JwtUtil.parseToken(token);
            String tokenType = claims.get("type", String.class);
            if (!ACCESS_TOKEN_TYPE.equals(tokenType)) {
                log.warn("拒绝非AccessToken访问业务接口, tokenType: {}", tokenType);
                return handleError(exchange, ResultCode.UNAUTHORIZED);
            }
            String openId = claims.getSubject();

            // 账号注销后，即使手机号/微信标识被新账号复用，旧令牌也不能穿透到新账号。
            Mono<Boolean> subjectRevoked = tokenBlacklistService.isSubjectRevoked(openId, claims.getIssuedAt());
            Mono<Boolean> safeSubjectRevoked = subjectRevoked == null ? Mono.empty() : subjectRevoked;
            return safeSubjectRevoked.defaultIfEmpty(false).flatMap(revoked -> {
                if (Boolean.TRUE.equals(revoked)) {
                    log.warn("拒绝已注销身份的旧Token, openId: {}", openId);
                    return handleError(exchange, ResultCode.UNAUTHORIZED);
                }

                /*
                 * 滑动刷新：当 AccessToken 剩余有效期小于 renewalThresholdMs 时，
                 * 自动生成新 Token 并通过 X-New-Access-Token 响应头下发。
                 */
                final Mono<Void> renewal = tryRenewToken(exchange, openId, claims);

                boolean forceFreshRoles =
                        exchange.getRequest().getPath().value().startsWith("/internal/v1/interests/");
                return getUserAuthorities(openId, forceFreshRoles)
                        .map(Optional::of)
                        .defaultIfEmpty(Optional.empty())
                        // 认证成功后，将 userId（即 openId）写入 exchange attributes，
                        // 供下游 LoggingFilter 写入 MDC，确保 REQUEST_FILE 日志携带用户标识。
                        .doOnNext(opt ->
                                opt.ifPresent(a -> exchange.getAttributes().put("userId", openId)))
                        .onErrorResume(e -> {
                            log.warn("加载用户认证信息失败, openId: {}, error: {}", openId, e.getMessage());
                            return Mono.error(new AuthenticationInfrastructureException(e));
                        })
                        .flatMap(authorities -> authorities
                                .map(value -> renewal.then(setAuthenticationContext(exchange, chain, openId, value)))
                                .orElseGet(() -> handleError(exchange, ResultCode.UNAUTHORIZED)))
                        .onErrorResume(
                                AuthenticationInfrastructureException.class,
                                error -> ResponseUtil.error(
                                        exchange,
                                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                                        ResultCode.SYSTEM_BUSY.getCode(),
                                        ResultCode.SYSTEM_BUSY.getMsg()));
            });
        } catch (ExpiredJwtException e) {
            log.warn("Token已过期: {}", e.getMessage());
            return handleError(exchange, ResultCode.TOKEN_EXPIRED);
        } catch (JwtException e) {
            log.warn("无效的Token: {}", e.getMessage());
            return handleError(exchange, ResultCode.UNAUTHORIZED);
        } catch (Exception e) {
            log.error("JWT认证失败: {}", e.getMessage(), e);
            return handleError(exchange, ResultCode.ERROR);
        }
    }

    /** 区分认证凭据无效与认证依赖设施暂时不可用，避免有效 Token 被误报为未授权。 */
    private static final class AuthenticationInfrastructureException extends RuntimeException {
        /**
         * 创建 {@link AuthenticationInfrastructureException} 实例。
         *
         * @param cause cause 参数
         */
        private AuthenticationInfrastructureException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * 判断 AccessToken 是否已进入续期窗口（剩余有效期小于 {@link #renewalThresholdMs}）。
     *
     * <p>当返回 {@code true} 时，过滤器自动生成新 Token 并通过
     * {@value #NEW_ACCESS_TOKEN_HEADER} 响应头下发给客户端，
     * 实现无感知的滑动刷新。</p>
     *
     * @param claims 已解析的 JWT Claims
     * @return true 表示需要自动续期，false 表示 Token 有效期充足或续期功能已禁用
     */
    private boolean isNearExpiry(Claims claims) {
        Date expiration = claims.getExpiration();
        if (expiration == null) {
            return false;
        }
        long remainingMs = expiration.getTime() - System.currentTimeMillis();
        return remainingMs < renewalThresholdMs;
    }

    /**
     * 尝试为即将过期的 AccessToken 执行滑动续期。
     *
     * <p>当 Token 进入续期窗口时，生成新的 AccessToken 并写入 Redis，
     * 同时通过 {@value #NEW_ACCESS_TOKEN_HEADER} 响应头下发给客户端。</p>
     *
     * @param exchange 当前请求交换上下文
     * @param openId   用户 OpenID
     * @param claims   已解析的 JWT Claims
     * @return 续期存储操作的 Mono（已完成或空操作）
     */
    private Mono<Void> tryRenewToken(ServerWebExchange exchange, String openId, Claims claims) {
        if (renewalThresholdMs <= 0 || !isNearExpiry(claims)) {
            return Mono.empty();
        }
        String deviceId = claims.get("deviceId", String.class);
        String newToken = tokenService.generateAccessToken(openId, deviceId);
        exchange.getResponse().getHeaders().set(NEW_ACCESS_TOKEN_HEADER, newToken);
        int ttlSeconds = (int) (accessTokenExpireMs / 1000);
        log.debug("AccessToken 滑动刷新: openId={}, 剩余有效期进入续期窗口", openId);
        return tokenService.storeAccessToken(openId, newToken, ttlSeconds);
    }

    /**
     * 根据 OpenID 获取用户权限列表
     *
     * <p>优先从 Caffeine 本地缓存获取权限，缓存未命中时从数据库查询并更新缓存。
     * 用户不存在或已禁用时返回空 Publisher，由认证流程拒绝访问。
     *
     * @param openId 微信 OpenID
     * @return 用户权限列表的 Mono 包装
     */
    private Mono<List<SimpleGrantedAuthority>> getUserAuthorities(String openId, boolean forceFreshRoles) {
        return userRepository
                .findByOpenId(openId)
                .filter(user -> {
                    if (!user.isEnabled()) {
                        log.warn("拒绝已禁用用户访问, userId: {}", user.getId());
                        return false;
                    }
                    return true;
                })
                .flatMap(user -> {
                    List<SimpleGrantedAuthority> cached =
                            forceFreshRoles ? null : userRolesCache.getIfPresent(user.getId());
                    if (cached != null) {
                        return Mono.just(cached);
                    }
                    return fetchAndCacheUserRoles(user.getId());
                });
    }

    /**
     * 从数据库查询用户角色并缓存权限列表
     *
     * <p>通过 RoleRepository 查询用户角色，转换为 Spring Security 权限对象，
     * 并将结果存入 Caffeine 本地缓存（TTL 60秒）。
     *
     * @param userId 用户ID
     * @return 用户权限列表的 Mono 包装
     */
    private Mono<List<SimpleGrantedAuthority>> fetchAndCacheUserRoles(Long userId) {
        return roleRepository.findByUserId(userId).map(roles -> {
            if (roles == null || roles.isEmpty()) {
                return Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
            }
            List<SimpleGrantedAuthority> authorities = buildAuthoritiesFromRoles(roles);
            userRolesCache.put(userId, new java.util.ArrayList<>(authorities));
            return authorities;
        });
    }

    /**
     * 将角色列表转换为 Spring Security 权限列表
     *
     * <p>遍历角色列表，将每个角色的 code 转换为 {@code ROLE_{code}} 格式的权限。
     * 特殊处理管理员角色：如果角色 code 或名称包含 "ADMIN"，确保添加 ROLE_ADMIN 权限。
     *
     * @param roles 角色实体列表
     * @return Spring Security 权限列表
     */
    private List<SimpleGrantedAuthority> buildAuthoritiesFromRoles(List<RoleEntity> roles) {
        List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>(roles.stream()
                .filter(role -> {
                    if (role.getCode() == null) {
                        log.warn("角色code为空，跳过该角色: roleId={}", role.getId());
                        return false;
                    }
                    return true;
                })
                .map(role -> new SimpleGrantedAuthority(normalizeRoleAuthority(role.getCode())))
                .collect(Collectors.toList()));

        boolean isAdmin = roles.stream()
                .anyMatch(role -> "ADMIN".equalsIgnoreCase(stripRolePrefix(role.getCode()))
                        || (role.getName() != null
                                && role.getName().toLowerCase().contains("admin")));
        if (isAdmin && authorities.stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return authorities;
    }

    /** Normalizes persisted role codes without ever producing ROLE_ROLE_* authorities. */
    private static String normalizeRoleAuthority(String roleCode) {
        String normalized = roleCode.trim().toUpperCase(java.util.Locale.ROOT);
        return normalized.startsWith("ROLE_") ? normalized : "ROLE_" + normalized;
    }

    /**
     * 去除角色编码的 {@code ROLE_} 前缀并统一为大写。
     *
     * <p>仅用于 {@code ADMIN} 等特殊角色的比较判断；{@code null} 输入返回空字符串，
     * 保证比较逻辑不会因空值抛出异常。</p>
     *
     * @param roleCode 持久化的角色编码，可为 {@code null}
     * @return 大写且去除 {@code ROLE_} 前缀后的角色编码；输入为 {@code null} 时返回空字符串
     */
    private static String stripRolePrefix(String roleCode) {
        if (roleCode == null) {
            return "";
        }
        String normalized = roleCode.trim().toUpperCase(java.util.Locale.ROOT);
        return normalized.startsWith("ROLE_") ? normalized.substring("ROLE_".length()) : normalized;
    }

    /**
     * 设置认证上下文到 SecurityContext
     *
     * <p>创建 {@link UsernamePasswordAuthenticationToken} 并写入
     * {@link SecurityContext}，通过 {@link ReactiveSecurityContextHolder}
     * 将认证信息传递给后续过滤器和控制器。
     *
     * @param exchange    ServerWebExchange 交换对象
     * @param chain       WebFilterChain 过滤器链
     * @param openId      微信 OpenID（作为认证主体）
     * @param authorities 用户权限列表
     * @return 携带安全上下文的过滤链处理结果
     */
    private Mono<Void> setAuthenticationContext(
            ServerWebExchange exchange, WebFilterChain chain, String openId, List<SimpleGrantedAuthority> authorities) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(openId, null, authorities);
        SecurityContext securityContext = new SecurityContextImpl(authentication);
        return chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));
    }

    /**
     * 判断请求路径是否为公开路径
     *
     * <p>遍历 {@link SystemConstants#PUBLIC_PATHS} 列表，
     * 使用 Ant 风格的路径匹配器进行匹配。业务接口仅允许精确声明的登录前入口跳过认证，
     * 避免新增接口因命中宽泛通配符而意外绕过 JWT。
     *
     * @param path 请求路径
     * @return 如果路径匹配任一公开路径则返回 true
     */
    private boolean isPublicPath(String path) {
        for (String publicPath : SystemConstants.PUBLIC_PATHS) {
            if (PATH_MATCHER.match(publicPath, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从请求头中提取 JWT Token
     *
     * <p>期望格式：{@code Bearer {token}}。
     * 从 {@code Authorization} 请求头中提取 Token 部分。
     *
     * @param request ServerHttpRequest 请求对象
     * @return Token 字符串，未找到或格式不正确时返回 null
     */
    private String extractToken(ServerHttpRequest request) {
        String bearerToken = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (bearerToken != null && bearerToken.startsWith(TOKEN_PREFIX)) {
            return bearerToken.substring(TOKEN_PREFIX.length());
        }
        return null;
    }

    /**
     * 将 Token 加入黑名单（用于退出登录场景）
     *
     * @param token      Token 字符串
     * @param expireTime Token 过期时间（毫秒），黑名单记录将在此时间后自动清除
     * @return 处理结果的 Mono
     */
    public Mono<Void> addTokenToBlacklist(String token, long expireTime) {
        return tokenBlacklistService.add(token, expireTime);
    }

    /**
     * 处理认证错误，返回国际化的 JSON 错误响应
     *
     * <p>从 Reactor Context 中获取当前请求的 Locale，通过
     * {@link MessageUtils} 将错误消息键翻译为客户端语言的文本，
     * 并写入 HTTP 响应体。
     *
     * @param exchange   当前 HTTP 请求-响应交换对象
     * @param resultCode 错误码枚举，包含 code 和 messageKey
     * @return 表示响应写入完成的 Mono
     */
    private Mono<Void> handleError(ServerWebExchange exchange, ResultCode resultCode) {
        return Mono.deferContextual(ctx -> {
            Locale locale = ctx.getOrDefault(Locale.class, Locale.SIMPLIFIED_CHINESE);
            String msg = messageUtils.get(locale, resultCode.getMessageKey());
            return com.apex.scaffold.interfaces.util.ResponseUtil.error(
                    exchange, statusFor(resultCode), resultCode.getCode(), msg);
        });
    }

    /**
     * 根据 ResultCode 映射对应的 HTTP 状态码
     *
     * @param resultCode 错误码枚举
     * @return 对应的 HTTP 状态码
     */
    private static HttpStatus statusFor(ResultCode resultCode) {
        Integer code = resultCode.getCode();
        if (code != null) {
            if (code == HTTP_STATUS_UNAUTHORIZED) {
                return HttpStatus.UNAUTHORIZED;
            }
            if (code == HTTP_STATUS_FORBIDDEN) {
                return HttpStatus.FORBIDDEN;
            }
            if (code == HTTP_STATUS_NOT_FOUND) {
                return HttpStatus.NOT_FOUND;
            }
            if (code == SystemConstants.ERROR_CODE_RATE_LIMIT) {
                return HttpStatus.TOO_MANY_REQUESTS;
            }
            if (code == SystemConstants.ERROR_CODE_SERVICE_UNAVAILABLE || code == SystemConstants.ERROR_CODE_DEGRADE) {
                return HttpStatus.SERVICE_UNAVAILABLE;
            }
        }
        return HttpStatus.UNAUTHORIZED;
    }
}

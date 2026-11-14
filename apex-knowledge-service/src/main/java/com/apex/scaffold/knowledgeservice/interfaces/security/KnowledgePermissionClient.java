package com.apex.scaffold.knowledgeservice.interfaces.security;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 使用服务配置的同租户 reader 凭据查询受保护授权端点，不转发客户端身份头或令牌。
 *
 * <p>仅信任与已认证用户的 userId、tenantId 完全匹配的成功快照，不根据角色名称推断权限。
 * 未确认共享身份命名空间、配置无效、远程失败或快照不完整时均拒绝访问，不缓存或降级放行。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Component
public class KnowledgePermissionClient {
    private static final int MAX_SNAPSHOT_BYTES = 256 * 1024;
    private static final Duration MAX_TIMEOUT = Duration.ofSeconds(30);
    private final KnowledgePermissionProperties properties;
    private final WebClient client;

    /**
     * 使用 Boot 管理的客户端构建器及连接资源，限制授权快照的内存解码大小。
     *
     * @param properties 本地授权配置及按租户配置的 reader 凭据
     * @param builder Boot 管理的客户端构建器
     */
    public KnowledgePermissionClient(KnowledgePermissionProperties properties, WebClient.Builder builder) {
        this.properties = properties;
        this.client = builder.clone()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_SNAPSHOT_BYTES))
                .build();
    }

    /**
     * 在订阅时查询并核对用户、租户和业务响应码，返回不可修改的精确权限码列表。
     *
     * @param user 从已验证认证上下文取得的用户
     * @return 成功时发出权限列表；任何查询或校验失败均发出 {@link AccessDeniedException}
     */
    public Mono<List<String>> permissions(AuthenticatedUser user) {
        return Mono.defer(() -> {
                    URI base = URI.create(properties.getBaseUrl() == null ? "" : properties.getBaseUrl());
                    String token = properties.getReaderTokens().get(user.tenantId());
                    Duration timeout = properties.getTimeout();
                    if (!properties.isSharedUserNamespace()
                            || !"https".equalsIgnoreCase(base.getScheme())
                            || base.getHost() == null
                            || base.getUserInfo() != null
                            || base.getQuery() != null
                            || base.getFragment() != null
                            || user.userId() == null
                            || user.userId() <= 0
                            || user.tenantId() == null
                            || user.tenantId().isBlank()
                            || token == null
                            || token.isBlank()
                            || timeout == null
                            || timeout.isNegative()
                            || timeout.isZero()
                            || timeout.compareTo(MAX_TIMEOUT) > 0) {
                        return Mono.<List<String>>error(denied());
                    }
                    return client.get()
                            .uri(base.toString().replaceAll("/+$", "") + "/auth/v1/authorization/users/"
                                    + user.userId())
                            .headers(headers -> headers.setBearerAuth(token))
                            .retrieve()
                            .bodyToMono(new ParameterizedTypeReference<Result<Snapshot>>() {})
                            .filter(result -> Integer.valueOf(ResultCode.SUCCESS.getCode())
                                    .equals(result.getCode()))
                            .mapNotNull(Result::getData)
                            .filter(data -> user.userId().equals(data.userId())
                                    && user.tenantId().equals(data.tenantId())
                                    && data.permissions() != null
                                    && data.permissions().stream().allMatch(code -> code != null && !code.isBlank()))
                            .map(data -> List.copyOf(data.permissions()))
                            .switchIfEmpty(Mono.error(denied()))
                            .timeout(timeout);
                })
                .onErrorMap(error -> denied());
    }

    /**
     * 创建不包含远程响应、身份标识或凭据的统一拒绝异常。
     *
     * @return 由响应式错误边界处理的访问拒绝异常
     */
    private static AccessDeniedException denied() {
        return new AccessDeniedException("Forbidden");
    }

    /**
     * 授权中心返回的最小权限快照，仅在身份和全部权限码校验通过后用于授权。
     *
     * @param tenantId 快照所属租户，必须与已认证用户的租户完全一致
     * @param userId 快照所属用户，必须与已认证用户的正数用户 ID 完全一致
     * @param permissions 精确权限码列表，允许空列表，不允许空列表引用、空元素或空白权限码
     * @author Apex Team
     * @since 1.3.2
     */
    public record Snapshot(String tenantId, Long userId, List<String> permissions) {}
}

package com.apex.scaffold.officeservice.infrastructure.org;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.office.application.port.OfficeOrgUserQueryPort;
import com.apex.scaffold.office.application.port.OfficeOrgUserRef;
import com.apex.scaffold.office.application.port.OfficeOrgUserSnapshot;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 通过系统服务内部接口解析真实系统用户的出站适配器。
 *
 * <p>调用 {@code apex-system-service} 的 {@code POST /internal/v1/org/users/resolve}，
 * 用 {@code X-Tenant-Id} 声明目标租户。该接口只服务集群内直连，不经网关，也不携带用户令牌。</p>
 *
 * <p>降级语义：基址未配置、响应码非成功、超时或远程异常时返回空列表并记录告警，
 * 组织同步据此保留「已绑定用户」为空，而不是让整批同步失败。解析不可用不影响
 * 部门与员工的镜像写入，仅影响成员到系统用户的绑定。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.6.0
 */
@Slf4j
@Component
public class SystemOrgUserQueryAdapter implements OfficeOrgUserQueryPort {

    /** 单次解析请求的内存解码上限，与 system 侧 200 条批量上限匹配。 */
    private static final int MAX_RESPONSE_BYTES = 512 * 1024;

    /** 单次解析允许的最大条目数，与 system 侧请求 DTO 上限一致。 */
    private static final int MAX_BATCH_SIZE = 200;

    /** 内部组织接口路径。 */
    private static final String RESOLVE_PATH = "/internal/v1/org/users/resolve";

    /** 内部调用声明目标租户的请求头。 */
    private static final String TENANT_HEADER = "X-Tenant-Id";

    /** 解析配置。 */
    private final SystemOrgUserProperties properties;

    /** 内部调用客户端。 */
    private final WebClient client;

    /**
     * 使用 Boot 管理的客户端构建器，限制解析响应的内存解码大小。
     *
     * @param properties 系统服务内部接口配置
     * @param builder    Boot 管理的客户端构建器
     */
    public SystemOrgUserQueryAdapter(SystemOrgUserProperties properties, WebClient.Builder builder) {
        this.properties = properties;
        this.client = builder.clone()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES))
                .build();
    }

    /**
     * 批量解析真实系统用户。
     *
     * @param tenantId 目标租户标识
     * @param refs     待解析的外部通讯录身份
     * @return 命中的用户快照；配置缺失、未命中或远程失败时为空列表
     */
    @Override
    public Mono<List<OfficeOrgUserSnapshot>> resolveUsers(String tenantId, List<OfficeOrgUserRef> refs) {
        if (refs == null || refs.isEmpty()) {
            return Mono.just(List.of());
        }
        return Mono.<List<OfficeOrgUserSnapshot>>defer(() -> {
                    URI base = baseUri();
                    if (tenantId == null || tenantId.isBlank() || base == null) {
                        log.warn("组织用户解析跳过：租户或系统服务基址缺失，tenantId={}", tenantId);
                        return Mono.just(List.of());
                    }
                    List<ResolveRequest.UserRef> users = refs.stream()
                            .filter(ref -> ref != null && ref.externalUserId() != null)
                            .limit(MAX_BATCH_SIZE)
                            .map(ref -> new ResolveRequest.UserRef(
                                    ref.externalUserId(), ref.mobile(), ref.email(), ref.username(), ref.userId()))
                            .toList();
                    if (users.isEmpty()) {
                        return Mono.just(List.of());
                    }
                    Duration timeout = properties.getTimeout();
                    ParameterizedTypeReference<Result<ResolveResponse>> typeRef = new ParameterizedTypeReference<>() {};
                    Mono<List<OfficeOrgUserSnapshot>> decoded = client.post()
                            .uri(base.toString().replaceAll("/+$", "") + RESOLVE_PATH)
                            .header(TENANT_HEADER, tenantId)
                            .bodyValue(new ResolveRequest(users))
                            .retrieve()
                            .bodyToMono(typeRef)
                            .filter(result -> Integer.valueOf(ResultCode.SUCCESS.getCode())
                                    .equals(result.getCode()))
                            .map(Result::getData)
                            .filter(Objects::nonNull)
                            .map(SystemOrgUserQueryAdapter::toSnapshots);
                    return decoded.defaultIfEmpty(List.of()).timeout(timeout);
                })
                .onErrorResume(error -> {
                    log.warn("组织用户解析失败，按未绑定处理，tenantId={}，原因：{}", tenantId, error.toString());
                    return Mono.<List<OfficeOrgUserSnapshot>>just(List.of());
                });
    }

    /**
     * 构造并校验系统服务基址。
     *
     * <p>只接受 http/https、带主机且不含用户信息、查询参数或片段的基址，
     * 避免配置错误时把租户头发往非预期目标。
     *
     * @return 合法基址；配置非法时返回 null
     */
    private URI baseUri() {
        String raw = properties.getSystemBaseUrl();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        URI base = URI.create(raw);
        boolean valid = base.getHost() != null
                && base.getUserInfo() == null
                && base.getQuery() == null
                && base.getFragment() == null
                && ("http".equalsIgnoreCase(base.getScheme()) || "https".equalsIgnoreCase(base.getScheme()));
        return valid ? base : null;
    }

    private static List<OfficeOrgUserSnapshot> toSnapshots(ResolveResponse response) {
        return response.resolved().stream()
                .filter(user -> user != null && user.userId() != null)
                .map(user -> new OfficeOrgUserSnapshot(
                        user.externalUserId(), user.userId(), user.deptId(), user.matchedBy()))
                .toList();
    }

    /**
     * 批量解析请求体。
     *
     * @param users 待解析用户列表
     */
    record ResolveRequest(List<UserRef> users) {
        /**
         * 单个外部用户引用。
         *
         * @param externalUserId 外部系统用户标识
         * @param mobile         手机号
         * @param email          邮箱
         * @param username       登录名
         * @param userId         已绑定的系统用户 ID
         */
        record UserRef(String externalUserId, String mobile, String email, String username, Long userId) {}
    }

    /**
     * 批量解析响应体。
     *
     * @param resolved 命中的用户列表
     */
    record ResolveResponse(List<ResolvedUser> resolved) {
        /**
         * 命中的系统用户。
         *
         * @param externalUserId 外部系统用户标识
         * @param userId         系统用户 ID
         * @param deptId         所属系统部门 ID
         * @param matchedBy      命中字段
         */
        record ResolvedUser(String externalUserId, Long userId, Long deptId, String matchedBy) {}
    }
}

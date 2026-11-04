package com.apex.scaffold.authservice.interfaces.handler;

import com.apex.scaffold.auth.application.service.AuthOpenAppRegistryService;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.internal.InternalServiceIdentity;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.contract.dto.internal.OpenAppImportRequest;
import com.apex.scaffold.contract.dto.internal.OpenAppRegistrationRequest;
import com.apex.scaffold.contract.dto.internal.OpenAppStatusRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 开放应用注册内部契约：只接受已验签的 system 管理服务调用。
 *
 * <p>签名身份与租户来自 {@link InternalServiceIdentity}（过滤器写入的 exchange attribute），
 * 请求体中的租户不被信任；主体账号预验证标记也仅在调用方为 system 时才可使用。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Component
@RequiredArgsConstructor
public class InternalOpenAppRegistryHandler {

    private static final String SYSTEM = "apex-system-service";

    private final AuthOpenAppRegistryService registry;

    /**
     * 创建开放应用。
     *
     * @param request 服务器请求，请求体为 {@link OpenAppRegistrationRequest}
     * @return 统一响应，{@code data} 为注册结果
     */
    public Mono<ServerResponse> create(ServerRequest request) {
        return trusted(request, identity -> request.bodyToMono(OpenAppRegistrationRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体缺失")))
                .flatMap(body -> registry.create(body))
                .flatMap(this::ok));
    }

    /**
     * 更新开放应用。
     *
     * @param request 服务器请求，路径参数 {@code clientId}
     * @return 统一响应，{@code data} 为更新后的注册信息
     */
    public Mono<ServerResponse> update(ServerRequest request) {
        return trusted(request, identity -> request.bodyToMono(OpenAppRegistrationRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体缺失")))
                .flatMap(body -> registry.update(request.pathVariable("clientId"), body))
                .flatMap(this::ok));
    }

    /**
     * 查询单个开放应用。
     *
     * @param request 服务器请求，路径参数 {@code clientId}
     * @return 统一响应，{@code data} 为注册信息
     */
    public Mono<ServerResponse> get(ServerRequest request) {
        return trusted(request, identity -> registry.get(request.pathVariable("clientId"))
                .flatMap(this::ok));
    }

    /**
     * 分页查询开放应用。
     *
     * @param request 服务器请求（query 参数 page/size）
     * @return 统一响应，{@code data} 为应用列表
     */
    public Mono<ServerResponse> list(ServerRequest request) {
        return trusted(request, identity -> {
            int page = parse(request.queryParam("page").orElse("0"));
            int size = parse(request.queryParam("size").orElse("20"));
            return registry.list(page, size).collectList().flatMap(this::ok);
        });
    }

    /**
     * 启用或停用开放应用。
     *
     * @param request 服务器请求，请求体为 {@link OpenAppStatusRequest}
     * @return 统一响应，{@code data} 为更新后的注册信息
     */
    public Mono<ServerResponse> status(ServerRequest request) {
        return trusted(request, identity -> request.bodyToMono(OpenAppStatusRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体缺失")))
                .flatMap(body -> registry.status(request.pathVariable("clientId"), body.getEnabled()))
                .flatMap(this::ok));
    }

    /**
     * 轮换开放应用密钥，新密钥只在本次响应中出现。
     *
     * @param request 服务器请求，路径参数 {@code clientId}
     * @return 统一响应，{@code data.clientSecret} 为新明文密钥
     */
    public Mono<ServerResponse> rotateSecret(ServerRequest request) {
        return trusted(request, identity -> registry.rotateSecret(request.pathVariable("clientId"))
                .flatMap(this::ok));
    }

    /**
     * 删除开放应用。
     *
     * @param request 服务器请求，路径参数 {@code clientId}
     * @return 统一响应，{@code data} 为空
     */
    public Mono<ServerResponse> delete(ServerRequest request) {
        return trusted(request, identity -> registry.delete(request.pathVariable("clientId"))
                .then(ok(Result.success())));
    }

    /**
     * 导入一条旧注册源开放应用。
     *
     * @param request 服务器请求，请求体为 {@link OpenAppImportRequest}
     * @return 统一响应，{@code data} 为注册信息
     */
    public Mono<ServerResponse> importApp(ServerRequest request) {
        return trusted(request, identity -> request.bodyToMono(OpenAppImportRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体缺失")))
                .flatMap(body -> registry.importApp(body))
                .flatMap(this::ok));
    }

    private <T> Mono<ServerResponse> trusted(
            ServerRequest request, java.util.function.Function<InternalServiceIdentity, Mono<ServerResponse>> action) {
        return Mono.defer(() -> {
            Object raw = request.attributes().get(InternalServiceIdentity.ATTR_IDENTITY);
            if (!(raw instanceof InternalServiceIdentity identity)
                    || !SYSTEM.equals(identity.serviceName())
                    || identity.tenantId() == null
                    || identity.tenantId().isBlank()) {
                return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "内部注册仅允许已签名 system 调用"));
            }
            return action.apply(identity);
        });
    }

    private Mono<ServerResponse> ok(Object data) {
        return ServerResponse.ok().bodyValue(Result.success(data));
    }

    private int parse(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException error) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "分页参数非法");
        }
    }
}

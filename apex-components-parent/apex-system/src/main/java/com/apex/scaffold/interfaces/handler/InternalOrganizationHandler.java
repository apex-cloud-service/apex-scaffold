package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.interfaces.dto.internal.OrgUserResolveRequest;
import com.apex.scaffold.interfaces.dto.internal.OrgUserResolveResponse;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 组织目录内部接口处理器（集群内直连，不经网关）。
 *
 * <p>供其他服务按手机号、邮箱或登录名把外部通讯录身份解析为本租户真实的
 * {@code sys_user.id}。调用方不携带 JWT，必须通过 {@code X-Tenant-Id} 声明目标租户；
 * {@code SecurityConfig} 中的租户桥接过滤器已把该租户写入 Reactor Context，
 * 本处理器再用 {@link TenantContext#withTenant(Mono)} 安装到执行线程，
 * 保证租户拦截器按租户过滤。</p>
 *
 * <p>请求头缺失时返回参数错误，而不是回退到默认租户，避免跨租户解析。</p>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>POST /internal/v1/org/users/resolve - 批量解析真实系统用户</li>
 * </ul>
 *
 * @author Apex Team
 * @since 2.6.0
 */
@Component
@Tag(name = "内部-组织目录", description = "集群内组织身份解析，不经网关")
public class InternalOrganizationHandler extends BaseHandler {

    /** 内部调用声明目标租户的请求头。 */
    private static final String TENANT_HEADER = "X-Tenant-Id";

    /** 组织目录请求/响应适配器。 */
    private final OrgUserResolveAdapter resolveAdapter;

    /**
     * 创建组织目录内部接口处理器。
     *
     * @param resolveAdapter 组织目录请求/响应适配器
     * @param messageUtils   国际化消息工具
     * @param validator      Jakarta Bean Validation 校验器
     */
    public InternalOrganizationHandler(
            OrgUserResolveAdapter resolveAdapter, MessageUtils messageUtils, Validator validator) {
        super(messageUtils, validator);
        this.resolveAdapter = resolveAdapter;
    }

    /**
     * 批量解析真实系统用户
     *
     * <p>先读取请求头 {@code X-Tenant-Id}，缺失或空白时抛参数错误；解析请求体后交由解析适配器按手机号、
     * 邮箱或登录名匹配本租户真实用户，并在租户上下文中执行，最后统一包装为成功响应。
     *
     * @param request HTTP 请求，请求头必须包含 {@code X-Tenant-Id}，请求体为待解析列表
     * @return 命中用户的统一成功响应
     * @throws BusinessException 当租户头缺失或请求体校验失败时抛出
     */
    @Operation(
            operationId = "internalResolveOrgUsers",
            summary = "批量解析真实系统用户",
            description = "按手机号、邮箱或登录名把外部通讯录身份解析为本租户真实系统用户；" + "集群内服务直连，不经网关，调用方不携带 JWT，须通过 X-Tenant-Id 声明目标租户")
    @Parameter(name = TENANT_HEADER, description = "目标租户 ID", required = true, in = ParameterIn.HEADER)
    @RequestBody(
            description = "批量解析请求",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OrgUserResolveRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "解析完成,data 为命中的系统用户列表",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = OrgUserResolveResponse.class))),
        @ApiResponse(responseCode = "400", description = "租户头缺失或请求体非法")
    })
    public Mono<ServerResponse> resolveUsers(ServerRequest request) {
        String tenantId = request.headers().firstHeader(TENANT_HEADER);
        if (tenantId == null || tenantId.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "缺少租户标识");
        }
        return parseBody(request, OrgUserResolveRequest.class)
                .flatMap(resolveAdapter::resolve)
                .transform(TenantContext::withTenant)
                .flatMap(this::buildSuccess);
    }
}

package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.admin.SsoProviderRequest;
import com.apex.scaffold.application.service.SysSsoProviderService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
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
 * SSO 提供商管理处理器(函数式端点)
 *
 * <p>提供 SSO 单点登录提供商的配置管理 API,包括提供商的增删改查、连接测试和元数据获取功能。
 * 支持 SAML、CAS、LDAP、OIDC 等多种单点登录协议的统一管理。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>提供商配置管理:创建、更新、删除、查询 SSO 提供商配置</li>
 *   <li>连接测试:验证 SSO 提供商配置是否正确,端点是否可访问</li>
 *   <li>元数据获取:获取 SSO 协议相关的元数据信息,用于客户端集成</li>
 * </ul>
 *
 * <h2>接口清单</h2>
 * <ul>
 *   <li>GET /admin-api/v1/sso/providers - 查询提供商列表(分页)</li>
 *   <li>POST /admin-api/v1/sso/providers - 创建提供商</li>
 *   <li>PUT /admin-api/v1/sso/providers/{id} - 更新提供商</li>
 *   <li>DELETE /admin-api/v1/sso/providers/{id} - 删除提供商</li>
 *   <li>GET /admin-api/v1/sso/providers/{id} - 查询提供商详情</li>
 *   <li>POST /admin-api/v1/sso/providers/{id}/test - 测试提供商连接</li>
 *   <li>GET /admin-api/v1/sso/providers/{id}/metadata - 获取提供商元数据</li>
 * </ul>
 *
 * @author Apex Scaffold Generator
 * @since 2026-09-19
 */
@Component
@Tag(name = "后台-SSO 提供商管理", description = "SSO 单点登录提供商配置管理")
public class SsoProviderHandler extends BaseHandler {

    private final SysSsoProviderService sysSsoProviderService;

    /**
     * 创建 SSO 提供商管理处理器。
     *
     * @param sysSsoProviderService SSO 提供商服务
     * @param messageUtils 国际化消息工具
     * @param validator Jakarta Bean Validation 校验器
     */
    public SsoProviderHandler(
            SysSsoProviderService sysSsoProviderService, MessageUtils messageUtils, Validator validator) {
        super(messageUtils, validator);
        this.sysSsoProviderService = sysSsoProviderService;
    }

    /**
     * 查询 SSO 提供商列表(分页)
     *
     * <p>分页查询当前租户下的 SSO 提供商配置列表,支持按提供商名称和协议类型筛选。
     * 返回提供商的基本信息、协议类型、启用状态等。
     *
     * @param request HTTP 请求,支持分页参数 page、size 和筛选参数 providerName、protocolType
     * @return 带 traceId 的统一成功响应,data 为 SSO 提供商分页列表
     */
    @RequirePermission("system:sso:provider:list")
    @Operation(
            operationId = "adminListSsoProviders",
            summary = "查询 SSO 提供商列表",
            description = "分页查询当前租户下的 SSO 提供商配置列表,支持按提供商名称和协议类型筛选")
    @Parameter(
            name = "page",
            description = "页码(从1开始)",
            in = ParameterIn.QUERY,
            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "integer", example = "1"))
    @Parameter(
            name = "size",
            description = "每页数量",
            in = ParameterIn.QUERY,
            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "integer", example = "20"))
    @Parameter(
            name = "providerName",
            description = "提供商名称(模糊查询)",
            in = ParameterIn.QUERY,
            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string"))
    @Parameter(
            name = "protocolType",
            description = "协议类型(SAML/CAS/LDAP/OIDC)",
            in = ParameterIn.QUERY,
            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "401", description = "未授权,需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足,需要 system:sso:provider:list 权限")
    })
    public Mono<ServerResponse> list(ServerRequest request) {
        int page = safeParseInt(request.queryParam("page").orElse("1"), 1);
        int size = safeParseInt(request.queryParam("size").orElse("20"), 20);
        String providerName = request.queryParam("providerName").orElse(null);

        return sysSsoProviderService.list(providerName, page, size).flatMap(this::buildSuccess);
    }

    /**
     * 创建 SSO 提供商
     *
     * <p>创建新的 SSO 提供商配置,包括协议类型、认证端点、证书等信息。
     * 创建后可用于 SSO 单点登录认证流程。
     *
     * @param request HTTP 请求,请求体包含 SSO 提供商配置信息
     * @return 带 traceId 的统一成功响应,data 为已创建的 SSO 提供商实体
     */
    @RequirePermission("system:sso:provider:add")
    @Operation(
            operationId = "adminCreateSsoProvider",
            summary = "创建 SSO 提供商",
            description = "创建新的 SSO 提供商配置,支持 SAML、CAS、LDAP、OIDC 等协议")
    @RequestBody(description = "SSO 提供商配置信息", required = true, content = @Content(mediaType = "application/json"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "创建成功"),
        @ApiResponse(responseCode = "400", description = "参数校验失败"),
        @ApiResponse(responseCode = "401", description = "未授权,需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足,需要 system:sso:provider:add 权限")
    })
    @OperationLog(module = "SSO 提供商管理", type = OperationLog.OperationType.INSERT, description = "创建 SSO 提供商")
    public Mono<ServerResponse> create(ServerRequest request) {
        return parseBody(request, SsoProviderRequest.class)
                .flatMap(sysSsoProviderService::create)
                .flatMap(this::buildSuccess);
    }

    /**
     * 更新 SSO 提供商
     *
     * <p>更新指定 SSO 提供商的配置信息,包括认证端点、证书、启用状态等。
     * 更新后立即生效,影响后续的 SSO 认证流程。
     *
     * @param request HTTP 请求,路径参数 id 必须为正整数,请求体包含更新信息
     * @return 带 traceId 的统一成功响应,data 为已更新的 SSO 提供商实体
     */
    @RequirePermission("system:sso:provider:edit")
    @Operation(
            operationId = "adminUpdateSsoProvider",
            summary = "更新 SSO 提供商",
            description = "更新指定 SSO 提供商的配置信息,更新后立即生效")
    @Parameter(
            name = "id",
            description = "提供商 ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "integer", format = "int64"))
    @RequestBody(description = "SSO 提供商更新信息", required = true, content = @Content(mediaType = "application/json"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "更新成功"),
        @ApiResponse(responseCode = "400", description = "参数校验失败"),
        @ApiResponse(responseCode = "401", description = "未授权,需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足,需要 system:sso:provider:edit 权限"),
        @ApiResponse(responseCode = "404", description = "SSO 提供商不存在")
    })
    @OperationLog(module = "SSO 提供商管理", type = OperationLog.OperationType.UPDATE, description = "更新 SSO 提供商")
    public Mono<ServerResponse> update(ServerRequest request) {
        Long id = requirePositiveId(request);
        return parseBody(request, SsoProviderRequest.class)
                .flatMap(body -> sysSsoProviderService.update(id, body))
                .flatMap(this::buildSuccess);
    }

    /**
     * 删除 SSO 提供商
     *
     * <p>删除指定的 SSO 提供商配置。删除后该提供商将无法用于 SSO 认证。
     * 若该提供商存在活跃会话,建议先撤销会话再删除。
     *
     * @param request HTTP 请求,路径参数 id 必须为正整数
     * @return 带 traceId 的统一成功响应,data 为 {"deleted": 是否删除成功}
     */
    @RequirePermission("system:sso:provider:remove")
    @Operation(
            operationId = "adminDeleteSsoProvider",
            summary = "删除 SSO 提供商",
            description = "删除指定的 SSO 提供商配置,删除后将无法用于 SSO 认证")
    @Parameter(
            name = "id",
            description = "提供商 ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "400", description = "参数错误,ID 必须为正整数"),
        @ApiResponse(responseCode = "401", description = "未授权,需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足,需要 system:sso:provider:remove 权限"),
        @ApiResponse(responseCode = "404", description = "SSO 提供商不存在")
    })
    @OperationLog(module = "SSO 提供商管理", type = OperationLog.OperationType.DELETE, description = "删除 SSO 提供商")
    public Mono<ServerResponse> delete(ServerRequest request) {
        return sysSsoProviderService
                .delete(requirePositiveId(request))
                .flatMap(deleted -> buildSuccess(java.util.Map.of("deleted", deleted)));
    }

    /**
     * 查询 SSO 提供商详情
     *
     * <p>查询指定 SSO 提供商的完整配置信息,包括协议类型、端点地址、证书、扩展配置等。
     * 用于提供商配置详情展示和编辑回显。
     *
     * @param request HTTP 请求,路径参数 id 必须为正整数
     * @return 带 traceId 的统一成功响应,data 为 SSO 提供商详情
     */
    @RequirePermission("system:sso:provider:query")
    @Operation(operationId = "adminGetSsoProvider", summary = "查询 SSO 提供商详情", description = "查询指定 SSO 提供商的完整配置信息")
    @Parameter(
            name = "id",
            description = "提供商 ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "400", description = "参数错误,ID 必须为正整数"),
        @ApiResponse(responseCode = "401", description = "未授权,需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足,需要 system:sso:provider:query 权限"),
        @ApiResponse(responseCode = "404", description = "SSO 提供商不存在")
    })
    public Mono<ServerResponse> get(ServerRequest request) {
        return sysSsoProviderService.getById(requirePositiveId(request)).flatMap(this::buildSuccess);
    }

    /**
     * 测试 SSO 提供商连接
     *
     * <p>测试指定 SSO 提供商的连接是否正常,验证端点是否可访问、证书是否有效等。
     * 返回测试结果和详细信息,用于配置验证和故障排查。
     *
     * @param request HTTP 请求,路径参数 id 必须为正整数
     * @return 带 traceId 的统一成功响应,data 为连接测试结果
     */
    @RequirePermission("system:sso:provider:test")
    @Operation(
            operationId = "adminTestSsoProviderConnection",
            summary = "测试 SSO 提供商连接",
            description = "测试指定 SSO 提供商的连接是否正常,返回测试结果和详细信息")
    @Parameter(
            name = "id",
            description = "提供商 ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "测试完成(包含连接成功或失败结果)"),
        @ApiResponse(responseCode = "400", description = "参数错误,ID 必须为正整数"),
        @ApiResponse(responseCode = "401", description = "未授权,需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足,需要 system:sso:provider:test 权限"),
        @ApiResponse(responseCode = "404", description = "SSO 提供商不存在")
    })
    public Mono<ServerResponse> testConnection(ServerRequest request) {
        return sysSsoProviderService.testConnection(requirePositiveId(request)).flatMap(this::buildSuccess);
    }

    /**
     * 获取 SSO 提供商元数据
     *
     * <p>获取指定 SSO 提供商的协议元数据信息,用于客户端集成和配置验证。
     * 不同协议返回不同格式的元数据(如 SAML 返回 SP 元数据 XML)。
     *
     * @param request HTTP 请求,路径参数 id 必须为正整数
     * @return 带 traceId 的统一成功响应,data 为元数据信息
     */
    @RequirePermission("system:sso:provider:metadata")
    @Operation(
            operationId = "adminGetSsoProviderMetadata",
            summary = "获取 SSO 提供商元数据",
            description = "获取指定 SSO 提供商的协议元数据信息,用于客户端集成")
    @Parameter(
            name = "id",
            description = "提供商 ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "400", description = "参数错误,ID 必须为正整数"),
        @ApiResponse(responseCode = "401", description = "未授权,需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足,需要 system:sso:provider:metadata 权限"),
        @ApiResponse(responseCode = "404", description = "SSO 提供商不存在")
    })
    public Mono<ServerResponse> getMetadata(ServerRequest request) {
        return sysSsoProviderService.getMetadata(requirePositiveId(request)).flatMap(this::buildSuccess);
    }
}

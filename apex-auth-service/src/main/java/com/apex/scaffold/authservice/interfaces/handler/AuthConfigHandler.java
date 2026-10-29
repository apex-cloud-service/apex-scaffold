package com.apex.scaffold.authservice.interfaces.handler;

import com.apex.scaffold.auth.application.assembler.AuthConfigAssembler;
import com.apex.scaffold.auth.application.dto.AuthConfigDTO;
import com.apex.scaffold.auth.application.dto.CreateAuthConfigRequest;
import com.apex.scaffold.auth.application.dto.UpdateAuthConfigRequest;
import com.apex.scaffold.auth.application.service.AuthConfigService;
import com.apex.scaffold.auth.domain.model.entity.AuthConfigEntity;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.OperationLog.OperationType;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.LogSafetyUtil;
import com.apex.scaffold.common.util.TraceIdUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 认证配置Handler
 *
 * <p>提供系统认证配置的全生命周期管理，支持多租户隔离的灵活配置体系。
 *
 * <p><b>核心功能：</b>
 * <ul>
 *   <li><b>配置管理</b>：创建、更新、删除、查询认证配置</li>
 *   <li><b>分类管理</b>：按配置类型组织和查询配置项</li>
 *   <li><b>有效配置</b>：获取当前生效的配置值</li>
 *   <li><b>多租户支持</b>：每个租户独立的配置空间</li>
 * </ul>
 *
 * <p><b>配置类型（configType）：</b>
 * <ul>
 *   <li><b>PASSWORD_POLICY</b>：密码策略（长度、复杂度、有效期、历史限制）</li>
 *   <li><b>LOGIN_POLICY</b>：登录策略（失败锁定、会话超时、并发限制）</li>
 *   <li><b>MFA_POLICY</b>：多因素认证策略（启用状态、支持类型、强制范围）</li>
 *   <li><b>SESSION_POLICY</b>：会话策略（超时时间、续期规则、单点登录）</li>
 *   <li><b>TOKEN_POLICY</b>：令牌策略（JWT有效期、刷新策略、签名算法）</li>
 *   <li><b>OAUTH2_CLIENT</b>：OAuth2客户端配置（client_id、secret、回调地址）</li>
 *   <li><b>IP_WHITELIST</b>：IP白名单配置</li>
 *   <li><b>AUDIT_POLICY</b>：审计策略（日志级别、保留期限、敏感字段）</li>
 * </ul>
 *
 * <p><b>配置键（configKey）示例：</b>
 * <ul>
 *   <li><b>password.min_length</b>：密码最小长度（如8）</li>
 *   <li><b>password.require_uppercase</b>：是否要求大写字母（true/false）</li>
 *   <li><b>login.max_failures</b>：最大失败次数（如5）</li>
 *   <li><b>login.lockout_duration</b>：锁定时长（如300秒）</li>
 *   <li><b>session.timeout</b>：会话超时时间（如1800秒）</li>
 *   <li><b>token.access_token_ttl</b>：访问令牌有效期（如3600秒）</li>
 * </ul>
 *
 * <p><b>配置值格式（configValue）：</b>
 * <ul>
 *   <li><b>简单值</b>：字符串、数字、布尔值（如"8"、"true"）</li>
 *   <li><b>JSON对象</b>：复杂配置结构（如完整的密码策略JSON）</li>
 *   <li><b>枚举值</b>：预定义的选项（如"TOTP"、"SMS"）</li>
 * </ul>
 *
 * <p><b>配置优先级：</b>
 * <ol>
 *   <li>租户自定义配置（enabled=true）</li>
 *   <li>租户默认配置（isDefault=true）</li>
 *   <li>系统全局默认配置</li>
 * </ol>
 *
 * <p><b>多租户隔离：</b>
 * <p>所有配置按租户严格隔离，租户只能访问和修改自己的配置，
 * 不同租户可以有完全不同的认证策略。
 *
 * <p><b>版本控制：</b>
 * <p>每次更新配置时版本号递增，支持乐观锁并发控制。
 *
 * @author Apex Scaffold Team
 * @version 1.0.0
 * @since 1.3.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "认证配置管理", description = "认证配置相关接口")
public class AuthConfigHandler {

    private final AuthConfigService authConfigService;
    private final AuthConfigAssembler authConfigAssembler;

    /**
     * 创建认证配置
     *
     * <p>创建新的认证配置项。
     *
     * <p><b>请求体参数：</b>
     * <ul>
     *   <li><b>configType</b>：配置类型（必填，如PASSWORD_POLICY、LOGIN_POLICY）</li>
     *   <li><b>configKey</b>：配置键（必填，如password.min_length）</li>
     *   <li><b>configValue</b>：配置值（必填，支持字符串、数字、JSON对象）</li>
     *   <li><b>description</b>：配置描述（选填）</li>
     *   <li><b>enabled</b>：是否启用（必填，默认true）</li>
     *   <li><b>tenantId</b>：租户ID（必填）</li>
     * </ul>
     *
     * <p><b>返回数据：</b>
     * <p>返回创建的配置对象，包含自动生成的ID、版本号、创建时间等字段。
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>初始化租户 - 为新租户创建默认认证策略</li>
     *   <li>自定义策略 - 租户定制化认证规则</li>
     *   <li>功能开关 - 控制特定认证功能的启用状态</li>
     * </ul>
     *
     * <p><b>注意事项：</b>
     * <ul>
     *   <li>同一租户下configType+configKey组合必须唯一</li>
     *   <li>isDefault默认为false，不能直接创建为默认配置</li>
     *   <li>version初始值为1，后续更新时自动递增</li>
     *   <li>配置值格式需符合对应配置类型的schema要求</li>
     * </ul>
     *
     * <p><b>示例请求：</b>
     * <pre>
     * {
     *   "configType": "PASSWORD_POLICY",
     *   "configKey": "password.min_length",
     *   "configValue": "8",
     *   "description": "密码最小长度要求",
     *   "enabled": true,
     *   "tenantId": "default"
     * }
     * </pre>
     *
     * @param request HTTP请求
     * @return HTTP响应
     */
    @OperationLog(
            module = "认证配置",
            type = OperationType.INSERT,
            description = "创建认证配置",
            saveRequest = false,
            saveResponse = false)
    @Operation(operationId = "createAuthConfig", summary = "创建认证配置", description = "创建新的认证配置项")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "认证配置创建请求",
            required = true,
            content = @Content(schema = @Schema(implementation = CreateAuthConfigRequest.class)))
    @ApiResponse(
            responseCode = "200",
            description = "创建成功",
            content = @Content(schema = @Schema(implementation = AuthConfigDTO.class)))
    @ApiResponse(
            responseCode = "400",
            description = "参数校验失败",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "409",
            description = "配置已存在（configType+configKey冲突）",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> createConfig(ServerRequest request) {
        log.info("Creating auth config");

        return request.bodyToMono(CreateAuthConfigRequest.class)
                .flatMap(req -> {
                    AuthConfigEntity entity = authConfigAssembler.toEntity(req);
                    entity.setIsDefault(false);
                    entity.setVersion(1);
                    entity.setCreatedAt(LocalDateTime.now());
                    return authConfigService.createConfig(entity);
                })
                .map(authConfigAssembler::toDto)
                .flatMap(dto -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(dto)))
                .onErrorResume(e -> {
                    log.error("Failed to create auth config", e);
                    return buildError(ResultCode.ERROR, TraceIdUtil.getTraceId());
                });
    }

    /**
     * 更新认证配置
     *
     * <p>根据ID更新现有的认证配置。
     *
     * <p><b>路径参数：</b>
     * <ul>
     *   <li><b>id</b>：配置ID（必填）</li>
     * </ul>
     *
     * <p><b>查询参数：</b>
     * <ul>
     *   <li><b>tenantId</b>：租户ID（必填）</li>
     * </ul>
     *
     * <p><b>请求体参数：</b>
     * <ul>
     *   <li><b>configValue</b>：配置值（选填）</li>
     *   <li><b>description</b>：配置描述（选填）</li>
     *   <li><b>enabled</b>：是否启用（选填）</li>
     * </ul>
     *
     * <p><b>返回数据：</b>
     * <p>返回更新后的配置对象，版本号自动递增。
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>调整策略 - 修改认证策略参数（如密码长度、超时时间）</li>
     *   <li>启用/禁用 - 临时禁用或启用某个配置</li>
     *   <li>更新描述 - 补充或修正配置说明</li>
     * </ul>
     *
     * <p><b>注意事项：</b>
     * <ul>
     *   <li>不能修改configType和configKey，如需修改请删除后重建</li>
     *   <li>使用乐观锁机制，版本号不匹配时更新失败</li>
     *   <li>更新后立即生效，影响后续认证流程</li>
     *   <li>只能更新指定租户下的配置</li>
     * </ul>
     *
     * <p><b>示例请求：</b>
     * <pre>
     * {
     *   "configValue": "10",
     *   "description": "密码最小长度要求（已从8位提升至10位）",
     *   "enabled": true
     * }
     * </pre>
     *
     * @param request HTTP请求
     * @return HTTP响应
     */
    @OperationLog(
            module = "认证配置",
            type = OperationType.UPDATE,
            description = "更新认证配置",
            saveRequest = false,
            saveResponse = false)
    @Operation(operationId = "updateAuthConfig", summary = "更新认证配置", description = "根据ID更新认证配置")
    @io.swagger.v3.oas.annotations.Parameter(name = "id", description = "配置ID", required = true, example = "1001")
    @io.swagger.v3.oas.annotations.Parameter(
            name = "tenantId",
            description = "租户ID",
            required = true,
            example = "default")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "认证配置更新请求",
            required = true,
            content = @Content(schema = @Schema(implementation = UpdateAuthConfigRequest.class)))
    @ApiResponse(
            responseCode = "200",
            description = "更新成功",
            content = @Content(schema = @Schema(implementation = AuthConfigDTO.class)))
    @ApiResponse(
            responseCode = "400",
            description = "参数校验失败",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "404",
            description = "配置不存在",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "409",
            description = "版本冲突（乐观锁）",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> updateConfig(ServerRequest request) {
        Long id = Long.valueOf(request.pathVariable("id"));
        String tenantId =
                request.queryParam("tenantId").orElseThrow(() -> new IllegalArgumentException("tenantId is required"));
        log.info("Updating auth config: id={}, tenantId={}", id, LogSafetyUtil.text(tenantId, 64));

        return request.bodyToMono(UpdateAuthConfigRequest.class)
                .flatMap(req -> authConfigService.updateConfig(
                        id,
                        AuthConfigEntity.builder()
                                .configValue(req.getConfigValue())
                                .description(req.getDescription())
                                .enabled(req.getEnabled())
                                .build(),
                        tenantId))
                .map(authConfigAssembler::toDto)
                .flatMap(dto -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(dto)))
                .onErrorResume(e -> {
                    log.error("Failed to update auth config: id={}", id, e);
                    return buildError(ResultCode.ERROR, TraceIdUtil.getTraceId());
                });
    }

    /**
     * 删除认证配置
     *
     * <p>根据ID删除指定的认证配置。
     *
     * <p><b>路径参数：</b>
     * <ul>
     *   <li><b>id</b>：配置ID（必填）</li>
     * </ul>
     *
     * <p><b>查询参数：</b>
     * <ul>
     *   <li><b>tenantId</b>：租户ID（必填）</li>
     * </ul>
     *
     * <p><b>返回数据：</b>
     * <p>删除成功返回null。
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>移除过时配置 - 删除不再使用的认证策略</li>
     *   <li>清理租户 - 租户注销时清理其所有配置</li>
     *   <li>重置策略 - 删除自定义配置以恢复默认值</li>
     * </ul>
     *
     * <p><b>安全警告：</b>
     * <ul>
     *   <li>此操作不可逆，删除后无法恢复</li>
     *   <li>删除配置后系统将使用默认配置或上层配置</li>
     *   <li>建议先禁用配置观察影响，确认无误后再删除</li>
     *   <li>不能删除isDefault=true的默认配置</li>
     * </ul>
     *
     * <p><b>影响范围：</b>
     * <ul>
     *   <li>删除后立即生效，影响后续认证流程</li>
     *   <li>不影响已建立的会话和令牌</li>
     *   <li>删除操作会记录审计日志</li>
     * </ul>
     *
     * @param request HTTP请求
     * @return HTTP响应
     */
    @OperationLog(
            module = "认证配置",
            type = OperationType.DELETE,
            description = "删除认证配置",
            saveRequest = false,
            saveResponse = false)
    @Operation(operationId = "deleteAuthConfig", summary = "删除认证配置", description = "根据ID删除认证配置")
    @io.swagger.v3.oas.annotations.Parameter(name = "id", description = "配置ID", required = true, example = "1001")
    @io.swagger.v3.oas.annotations.Parameter(
            name = "tenantId",
            description = "租户ID",
            required = true,
            example = "default")
    @ApiResponse(responseCode = "200", description = "删除成功")
    @ApiResponse(
            responseCode = "400",
            description = "参数错误",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "404",
            description = "配置不存在",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "403",
            description = "不能删除默认配置",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> deleteConfig(ServerRequest request) {
        Long id = Long.valueOf(request.pathVariable("id"));
        String tenantId =
                request.queryParam("tenantId").orElseThrow(() -> new IllegalArgumentException("tenantId is required"));
        log.info("Deleting auth config: id={}, tenantId={}", id, LogSafetyUtil.text(tenantId, 64));

        return authConfigService
                .deleteConfig(id, tenantId)
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(null)))
                .onErrorResume(e -> {
                    log.error(
                            "Failed to delete auth config: id={}, tenantId={}",
                            id,
                            LogSafetyUtil.text(tenantId, 64),
                            e);
                    return buildError(ResultCode.ERROR, TraceIdUtil.getTraceId());
                });
    }

    /**
     * 获取认证配置详情
     *
     * <p>根据ID获取认证配置的完整详细信息。
     *
     * <p><b>路径参数：</b>
     * <ul>
     *   <li><b>id</b>：配置ID（必填）</li>
     * </ul>
     *
     * <p><b>查询参数：</b>
     * <ul>
     *   <li><b>tenantId</b>：租户ID（必填）</li>
     * </ul>
     *
     * <p><b>返回数据：</b>
     * <ul>
     *   <li><b>id</b>：配置ID</li>
     *   <li><b>configType</b>：配置类型</li>
     *   <li><b>configKey</b>：配置键</li>
     *   <li><b>configValue</b>：配置值</li>
     *   <li><b>description</b>：配置描述</li>
     *   <li><b>enabled</b>：是否启用</li>
     *   <li><b>isDefault</b>：是否为默认配置</li>
     *   <li><b>tenantId</b>：租户ID</li>
     *   <li><b>version</b>：版本号</li>
     *   <li><b>createdAt</b>：创建时间</li>
     *   <li><b>updatedAt</b>：更新时间</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>查看配置 - 查看某个配置项的详细信息</li>
     *   <li>编辑前查询 - 获取当前配置值用于编辑表单</li>
     *   <li>版本查看 - 查看配置的版本号和修改历史</li>
     * </ul>
     *
     * @param request HTTP请求
     * @return HTTP响应
     */
    @OperationLog(
            module = "认证配置",
            type = OperationType.SELECT,
            description = "获取认证配置详情",
            saveRequest = false,
            saveResponse = false)
    @Operation(operationId = "getAuthConfig", summary = "获取认证配置详情", description = "根据ID获取认证配置详情")
    @io.swagger.v3.oas.annotations.Parameter(name = "id", description = "配置ID", required = true, example = "1001")
    @io.swagger.v3.oas.annotations.Parameter(
            name = "tenantId",
            description = "租户ID",
            required = true,
            example = "default")
    @ApiResponse(
            responseCode = "200",
            description = "获取成功",
            content = @Content(schema = @Schema(implementation = AuthConfigDTO.class)))
    @ApiResponse(
            responseCode = "404",
            description = "配置不存在",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> getConfig(ServerRequest request) {
        Long id = Long.valueOf(request.pathVariable("id"));
        String tenantId =
                request.queryParam("tenantId").orElseThrow(() -> new IllegalArgumentException("tenantId is required"));
        log.debug("Getting auth config: id={}, tenantId={}", id, LogSafetyUtil.text(tenantId, 64));

        return authConfigService
                .getConfigById(id, tenantId)
                .map(authConfigAssembler::toDto)
                .flatMap(dto -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(dto)))
                .switchIfEmpty(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.error(ResultCode.NOT_FOUND)))
                .onErrorResume(e -> {
                    log.error("Failed to get auth config: id={}, tenantId={}", id, LogSafetyUtil.text(tenantId, 64), e);
                    return buildError(ResultCode.ERROR, TraceIdUtil.getTraceId());
                });
    }

    /**
     * 获取认证配置列表
     *
     * <p>获取指定租户的所有认证配置列表。
     *
     * <p><b>查询参数：</b>
     * <ul>
     *   <li><b>tenantId</b>：租户ID（必填）</li>
     * </ul>
     *
     * <p><b>返回数据：</b>
     * <p>返回该租户的所有配置列表（数组），包括启用和禁用的配置。
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>配置管理 - 在管理后台展示所有配置项</li>
     *   <li>配置导出 - 导出租户的完整配置用于备份</li>
     *   <li>配置对比 - 对比不同租户的配置差异</li>
     * </ul>
     *
     * <p><b>返回内容：</b>
     * <ul>
     *   <li>包含所有configType的配置</li>
     *   <li>包含启用（enabled=true）和禁用（enabled=false）的配置</li>
     *   <li>按configType和configKey排序</li>
     * </ul>
     *
     * @param request HTTP请求
     * @return HTTP响应
     */
    @OperationLog(
            module = "认证配置",
            type = OperationType.SELECT,
            description = "获取认证配置列表",
            saveRequest = false,
            saveResponse = false)
    @Operation(operationId = "listAuthConfigs", summary = "获取认证配置列表", description = "获取指定租户的认证配置列表")
    @io.swagger.v3.oas.annotations.Parameter(
            name = "tenantId",
            description = "租户ID",
            required = true,
            example = "default")
    @ApiResponse(responseCode = "200", description = "获取成功")
    @ApiResponse(
            responseCode = "400",
            description = "参数错误",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> listConfigs(ServerRequest request) {
        String tenantId =
                request.queryParam("tenantId").orElseThrow(() -> new IllegalArgumentException("tenantId is required"));
        log.debug("Listing auth configs: tenantId={}", LogSafetyUtil.text(tenantId, 64));

        return authConfigService
                .listAllConfigs(tenantId)
                .map(authConfigAssembler::toDto)
                .collectList()
                .flatMap(dtos -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(dtos)))
                .onErrorResume(e -> {
                    log.error("Failed to list auth configs: tenantId={}", LogSafetyUtil.text(tenantId, 64), e);
                    return buildError(ResultCode.ERROR, TraceIdUtil.getTraceId());
                });
    }

    /**
     * 根据类型获取配置
     *
     * <p>根据配置类型获取指定租户下该类型的所有配置列表。
     *
     * <p><b>路径参数：</b>
     * <ul>
     *   <li><b>type</b>：配置类型（必填，如PASSWORD_POLICY、LOGIN_POLICY）</li>
     * </ul>
     *
     * <p><b>查询参数：</b>
     * <ul>
     *   <li><b>tenantId</b>：租户ID（必填）</li>
     * </ul>
     *
     * <p><b>返回数据：</b>
     * <p>返回该类型下的所有配置列表（数组）。
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>按类别管理 - 集中管理某一类配置（如密码策略的所有配置项）</li>
     *   <li>配置页面 - 在特定配置页面加载相关配置（如密码策略配置页）</li>
     *   <li>策略检查 - 验证某类策略的完整性</li>
     * </ul>
     *
     * <p><b>常用配置类型：</b>
     * <ul>
     *   <li><b>PASSWORD_POLICY</b>：密码策略配置</li>
     *   <li><b>LOGIN_POLICY</b>：登录策略配置</li>
     *   <li><b>MFA_POLICY</b>：多因素认证策略</li>
     *   <li><b>SESSION_POLICY</b>：会话策略</li>
     *   <li><b>TOKEN_POLICY</b>：令牌策略</li>
     * </ul>
     *
     * @param request HTTP请求
     * @return HTTP响应
     */
    @OperationLog(
            module = "认证配置",
            type = OperationType.SELECT,
            description = "根据类型获取配置",
            saveRequest = false,
            saveResponse = false)
    @Operation(operationId = "getAuthConfigsByType", summary = "根据类型获取配置", description = "根据配置类型获取指定租户的配置列表")
    @io.swagger.v3.oas.annotations.Parameter(
            name = "type",
            description = "配置类型（如PASSWORD_POLICY、LOGIN_POLICY）",
            required = true,
            example = "PASSWORD_POLICY")
    @io.swagger.v3.oas.annotations.Parameter(
            name = "tenantId",
            description = "租户ID",
            required = true,
            example = "default")
    @ApiResponse(responseCode = "200", description = "获取成功")
    @ApiResponse(
            responseCode = "400",
            description = "参数错误",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> getConfigsByType(ServerRequest request) {
        String configType = request.pathVariable("type");
        String tenantId =
                request.queryParam("tenantId").orElseThrow(() -> new IllegalArgumentException("tenantId is required"));
        log.debug(
                "Getting configs by type: type={}, tenantId={}",
                LogSafetyUtil.text(configType, 64),
                LogSafetyUtil.text(tenantId, 64));

        return authConfigService
                .listConfigsByType(configType, tenantId)
                .map(authConfigAssembler::toDto)
                .collectList()
                .flatMap(dtos -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(dtos)))
                .onErrorResume(e -> {
                    log.error(
                            "Failed to get configs by type: type={}, tenantId={}",
                            LogSafetyUtil.text(configType, 64),
                            LogSafetyUtil.text(tenantId, 64),
                            e);
                    return buildError(ResultCode.ERROR, TraceIdUtil.getTraceId());
                });
    }

    /**
     * 获取有效配置
     *
     * <p>获取指定类型和键的当前生效的认证配置值。
     *
     * <p><b>查询参数：</b>
     * <ul>
     *   <li><b>configType</b>：配置类型（必填，如PASSWORD_POLICY）</li>
     *   <li><b>configKey</b>：配置键（必填，如password.min_length）</li>
     *   <li><b>tenantId</b>：租户ID（必填）</li>
     * </ul>
     *
     * <p><b>返回数据：</b>
     * <p>返回当前生效的配置对象。
     *
     * <p><b>配置优先级逻辑：</b>
     * <ol>
     *   <li>优先返回租户自定义的启用配置（enabled=true）</li>
     *   <li>其次返回租户的默认配置（isDefault=true）</li>
     *   <li>最后返回系统全局默认配置</li>
     *   <li>如果都不存在则返回404</li>
     * </ol>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>认证校验 - 在登录、密码验证等流程中获取实际生效的策略配置</li>
     *   <li>前端展示 - 在用户界面展示当前的策略要求（如"密码最小8位"）</li>
     *   <li>动态策略 - 根据配置动态调整认证行为</li>
     *   <li>策略查询 - 查询某个具体配置项的当前值</li>
     * </ul>
     *
     * <p><b>与其他查询接口的区别：</b>
     * <ul>
     *   <li><b>getConfig</b>：根据ID查询，返回指定的配置记录</li>
     *   <li><b>listConfigs</b>：返回租户的所有配置列表</li>
     *   <li><b>getConfigsByType</b>：返回某类型的所有配置</li>
     *   <li><b>getEffectiveConfig</b>：按优先级返回实际生效的配置（本接口）</li>
     * </ul>
     *
     * <p><b>示例请求：</b>
     * <pre>
     * GET /api/auth-configs/effective?configType=PASSWORD_POLICY&configKey=password.min_length&tenantId=default
     * </pre>
     *
     * @param request HTTP请求
     * @return HTTP响应
     */
    @OperationLog(
            module = "认证配置",
            type = OperationType.SELECT,
            description = "获取有效配置",
            saveRequest = false,
            saveResponse = false)
    @Operation(operationId = "getEffectiveAuthConfig", summary = "获取有效配置", description = "获取指定类型和键的有效认证配置")
    @io.swagger.v3.oas.annotations.Parameter(
            name = "configType",
            description = "配置类型（如PASSWORD_POLICY、LOGIN_POLICY）",
            required = true,
            example = "PASSWORD_POLICY")
    @io.swagger.v3.oas.annotations.Parameter(
            name = "configKey",
            description = "配置键（如password.min_length、login.max_failures）",
            required = true,
            example = "password.min_length")
    @io.swagger.v3.oas.annotations.Parameter(
            name = "tenantId",
            description = "租户ID",
            required = true,
            example = "default")
    @ApiResponse(
            responseCode = "200",
            description = "查询成功",
            content = @Content(schema = @Schema(implementation = AuthConfigDTO.class)))
    @ApiResponse(
            responseCode = "400",
            description = "参数错误",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "404",
            description = "配置不存在",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> getEffectiveConfig(ServerRequest request) {
        String configType = request.queryParam("configType")
                .orElseThrow(() -> new IllegalArgumentException("configType is required"));
        String configKey = request.queryParam("configKey")
                .orElseThrow(() -> new IllegalArgumentException("configKey is required"));
        String tenantId =
                request.queryParam("tenantId").orElseThrow(() -> new IllegalArgumentException("tenantId is required"));
        log.debug(
                "Getting effective config: type={}, key={}, tenantId={}",
                LogSafetyUtil.text(configType, 64),
                LogSafetyUtil.text(configKey, 64),
                LogSafetyUtil.text(tenantId, 64));

        return authConfigService
                .getConfigByTypeAndKey(configType, configKey, tenantId)
                .map(authConfigAssembler::toDto)
                .flatMap(dto -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(dto)))
                .switchIfEmpty(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.error(ResultCode.NOT_FOUND)))
                .onErrorResume(e -> {
                    log.error(
                            "Failed to get effective config: configType={}, configKey={}, tenantId={}",
                            LogSafetyUtil.text(configType, 64),
                            LogSafetyUtil.text(configKey, 64),
                            LogSafetyUtil.text(tenantId, 64),
                            e);
                    return buildError(ResultCode.ERROR, TraceIdUtil.getTraceId());
                });
    }

    /**
     * 构建错误响应
     *
     * @param code    错误码
     * @param traceId 追踪ID
     * @return HTTP响应
     */
    private Mono<ServerResponse> buildError(ResultCode code, String traceId) {
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(Result.error(code, traceId));
    }
}

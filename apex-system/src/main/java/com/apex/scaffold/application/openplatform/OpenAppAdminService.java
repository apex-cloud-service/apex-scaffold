package com.apex.scaffold.application.openplatform;

import com.apex.scaffold.application.dto.OAuth2ClientRequest;
import com.apex.scaffold.application.dto.OpenAppRequest;
import com.apex.scaffold.application.dto.OpenAppResponse;
import com.apex.scaffold.application.service.OAuth2ClientManagementService;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.entity.OAuth2RegisteredClientEntity;
import com.apex.scaffold.domain.repository.OAuth2RegisteredClientRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 开放应用管理服务（D2）。
 *
 * <p>开放应用是 OAUTH2_CLIENT 注册表中以 {@code client_credentials} 为主的第三方应用。
 * 本服务复用 {@link OAuth2ClientManagementService} 完成客户端基础生命周期
 * （唯一性校验、密钥加密、设置重建），并将开放元数据（名称/描述/限流档/联系人/状态）
 * 合并写入 {@code clientSettings} JSON 的 {@code openPlatform.*} 键——不新增表结构，
 * 回调与 scope 直接复用客户端既有 {@code redirectUris/scopes} 字段。
 *
 * <p>scope 申请必须全部命中 {@link OpenPlatformScopeRegistry}（配置可扩展）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
public class OpenAppAdminService {

    private static final String META_PREFIX = "openPlatform.";
    private static final String KEY_APP_NAME = META_PREFIX + "appName";
    private static final String KEY_DESCRIPTION = META_PREFIX + "description";
    private static final String KEY_CONTACT = META_PREFIX + "contact";
    private static final String KEY_RATE_LIMIT_TIER = META_PREFIX + "rateLimitTier";
    private static final String KEY_STATUS = META_PREFIX + "status";
    private static final String KEY_CREATED_AT = META_PREFIX + "createdAt";

    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_DISABLED = "DISABLED";

    private final OAuth2ClientManagementService clientManagementService;
    private final OAuth2RegisteredClientRepository clientRepository;
    private final OpenPlatformScopeRegistry scopeRegistry;
    private final ObjectMapper objectMapper;

    /**
     * 创建开放应用.
     *
     * <p>校验 scope 合法性与必填项后，经既有客户端管理服务注册 OAuth2 客户端
     * （认证方式 client_secret_basic、授权类型 client_credentials），再合并开放元数据。
     * 密钥未提供时自动生成，明文仅本次响应可见。
     *
     * @param request 开放应用注册请求
     * @return 创建后的应用（含一次性明文密钥）
     */
    public Mono<OpenAppResponse> createApp(OpenAppRequest request) {
        return validate(request, true).then(Mono.defer(() -> {
            String secret = resolveSecret(request.getClientSecret());
            OAuth2ClientRequest base = baseRequest(request);
            base.setClientSecret(secret);
            return clientManagementService
                    .createClient(base)
                    .then(mergeMetadata(request.getClientId(), request))
                    .map(entity -> toResponse(entity, secret));
        }));
    }

    /**
     * 更新开放应用（回调、scope 与开放元数据；密钥不变）.
     *
     * @param clientId 客户端 ID
     * @param request 更新请求（字段缺省保持原值）
     * @return 更新后的应用
     */
    public Mono<OpenAppResponse> updateApp(String clientId, OpenAppRequest request) {
        if (clientId == null || clientId.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "clientId 不能为空"));
        }
        return clientRepository
                .findByClientId(clientId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "客户端不存在")))
                .then(validate(request, false))
                .then(clientManagementService.updateClient(clientId, baseRequest(request)))
                .then(mergeMetadata(clientId, request))
                .map(entity -> toResponse(entity, null));
    }

    /**
     * 查询开放应用详情.
     *
     * @param clientId 客户端 ID
     * @return 应用信息（不含密钥）
     */
    public Mono<OpenAppResponse> getApp(String clientId) {
        return clientRepository
                .findByClientId(clientId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "客户端不存在")))
                .map(entity -> toResponse(entity, null));
    }

    /**
     * 分页查询客户端注册表（含未开通开放元数据的普通客户端）.
     *
     * @param page 页码（从 0 开始）
     * @param size 每页条数（1~100）
     * @return 应用信息流
     */
    public Flux<OpenAppResponse> listApps(int page, int size) {
        return clientRepository
                .findAll(Math.max(0, page), Math.min(Math.max(1, size), 100))
                .map(entity -> toResponse(entity, null));
    }

    /**
     * 重置开放应用密钥.
     *
     * @param clientId 客户端 ID
     * @return 应用信息（{@code clientSecret} 为一次性明文）
     */
    public Mono<OpenAppResponse> resetSecret(String clientId) {
        return clientRepository
                .findByClientId(clientId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "客户端不存在")))
                .then(clientManagementService.resetClientSecret(clientId))
                .flatMap(secret -> clientRepository.findByClientId(clientId).map(entity -> toResponse(entity, secret)));
    }

    private Mono<Void> validate(OpenAppRequest request, boolean create) {
        return Mono.defer(() -> {
            if (create
                    && (request.getClientId() == null || request.getClientId().isBlank())) {
                return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "clientId 不能为空"));
            }
            if (create && (request.getAppName() == null || request.getAppName().isBlank())) {
                return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "appName 不能为空"));
            }
            if (request.getStatus() != null && !request.getStatus().isBlank()) {
                String status = request.getStatus().trim().toUpperCase(Locale.ROOT);
                if (!STATUS_ENABLED.equals(status) && !STATUS_DISABLED.equals(status)) {
                    return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "status 仅支持 ENABLED/DISABLED"));
                }
            }
            List<String> invalid = scopeRegistry.invalidScopes(request.getScopes());
            if (!invalid.isEmpty()) {
                return Mono.error(
                        new BusinessException(ResultCode.PARAM_ERROR, "非法 scope: " + String.join(", ", invalid)));
            }
            return Mono.empty();
        });
    }

    /** 组装既有客户端管理服务的注册/更新请求（开放应用固定 client_credentials + basic 认证）。 */
    private OAuth2ClientRequest baseRequest(OpenAppRequest request) {
        OAuth2ClientRequest base = new OAuth2ClientRequest();
        base.setClientId(request.getClientId());
        base.setClientName(request.getAppName());
        base.setClientAuthenticationMethods(Set.of("client_secret_basic"));
        base.setAuthorizationGrantTypes(Set.of("client_credentials"));
        base.setRedirectUris(request.getRedirectUris());
        base.setScopes(request.getScopes());
        return base;
    }

    private static String resolveSecret(String provided) {
        return provided == null || provided.isBlank()
                ? UUID.randomUUID().toString().replace("-", "")
                : provided;
    }

    /** 将开放元数据合并进 clientSettings（{@code openPlatform.*} 平铺键，幂等覆盖）。 */
    private Mono<OAuth2RegisteredClientEntity> mergeMetadata(String clientId, OpenAppRequest request) {
        return clientRepository
                .findByClientId(clientId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "客户端不存在")))
                .flatMap(entity -> {
                    ObjectNode settings = parseSettings(entity.getClientSettings());
                    settings.put(
                            KEY_APP_NAME,
                            request.getAppName() == null || request.getAppName().isBlank()
                                    ? entity.getClientName()
                                    : request.getAppName());
                    if (request.getDescription() != null) {
                        settings.put(KEY_DESCRIPTION, request.getDescription());
                    }
                    if (request.getContact() != null) {
                        settings.put(KEY_CONTACT, request.getContact());
                    }
                    if (request.getRateLimitTier() != null) {
                        settings.put(KEY_RATE_LIMIT_TIER, request.getRateLimitTier());
                    }
                    String previousStatus = settings.path(KEY_STATUS).asText(STATUS_ENABLED);
                    String status =
                            request.getStatus() == null || request.getStatus().isBlank()
                                    ? previousStatus
                                    : request.getStatus().trim().toUpperCase(Locale.ROOT);
                    settings.put(KEY_STATUS, status);
                    if (settings.path(KEY_CREATED_AT).isMissingNode()) {
                        settings.put(KEY_CREATED_AT, LocalDateTime.now().toString());
                    }
                    entity.setClientSettings(settings.toString());
                    return clientRepository.save(entity);
                });
    }

    private ObjectNode parseSettings(String json) {
        try {
            JsonNode node = objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
            if (node instanceof ObjectNode object) {
                return object;
            }
            throw new BusinessException(ResultCode.PARAM_ERROR, "客户端设置不是 JSON 对象");
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "客户端设置解析失败");
        }
    }

    private OpenAppResponse toResponse(OAuth2RegisteredClientEntity entity, String secret) {
        ObjectNode settings = parseSettings(entity.getClientSettings());
        boolean openApp = settings.hasNonNull(KEY_APP_NAME);
        return OpenAppResponse.builder()
                .clientId(entity.getClientId())
                .appName(settings.path(KEY_APP_NAME).asText(entity.getClientName()))
                .description(openApp ? textOrNull(settings, KEY_DESCRIPTION) : null)
                .redirectUris(entity.getRedirectUris())
                .scopes(entity.getScopes())
                .rateLimitTier(openApp ? textOrNull(settings, KEY_RATE_LIMIT_TIER) : null)
                .contact(openApp ? textOrNull(settings, KEY_CONTACT) : null)
                .status(openApp ? settings.path(KEY_STATUS).asText(STATUS_ENABLED) : null)
                .createdAt(openApp ? textOrNull(settings, KEY_CREATED_AT) : null)
                .clientSecret(secret)
                .build();
    }

    private static String textOrNull(ObjectNode settings, String key) {
        JsonNode node = settings.path(key);
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }
}

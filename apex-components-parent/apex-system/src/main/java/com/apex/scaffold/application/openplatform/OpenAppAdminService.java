package com.apex.scaffold.application.openplatform;

import com.apex.scaffold.application.dto.OAuth2ClientRequest;
import com.apex.scaffold.application.dto.OpenAppRequest;
import com.apex.scaffold.application.dto.OpenAppResponse;
import com.apex.scaffold.application.service.OAuth2ClientManagementService;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.contract.dto.internal.OpenAppRegistrationRequest;
import com.apex.scaffold.contract.dto.internal.OpenAppRegistrationResponse;
import com.apex.scaffold.domain.gateway.OpenAppRegistryGateway;
import com.apex.scaffold.domain.model.entity.OAuth2RegisteredClientEntity;
import com.apex.scaffold.domain.repository.OAuth2RegisteredClientRepository;
import com.apex.scaffold.domain.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
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
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
public class OpenAppAdminService {

    private static final String REGISTRY_AUTH = "auth";
    private static final String REGISTRY_LOCAL = "local";

    private static final String META_PREFIX = "openPlatform.";
    private static final String KEY_APP_NAME = META_PREFIX + "appName";
    private static final String KEY_DESCRIPTION = META_PREFIX + "description";
    private static final String KEY_CONTACT = META_PREFIX + "contact";
    private static final String KEY_RATE_LIMIT_TIER = META_PREFIX + "rateLimitTier";
    private static final String KEY_STATUS = META_PREFIX + "status";
    private static final String KEY_CREATED_AT = META_PREFIX + "createdAt";

    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_DISABLED = "DISABLED";

    /** 默认限流档编码（未声明档位/未知客户端的 fail-open 兜底） */
    private static final String DEFAULT_RATE_LIMIT_TIER = "default";

    private final OAuth2ClientManagementService clientManagementService;
    private final OAuth2RegisteredClientRepository clientRepository;
    private final OpenPlatformScopeRegistry scopeRegistry;
    private final ObjectMapper objectMapper;
    private final OpenAppRegistryGateway authRegistry;
    private final UserRepository userRepository;
    private final boolean localRegistry;

    /**
     * Spring 主构造器（唯一 public 构造器，Spring 按唯一构造器注入，无需 {@code @Autowired}）。
     *
     * <p>本类不使用 {@code @RequiredArgsConstructor}：Lombok 会再生成一个构造器，
     * 导致 Spring 无法按唯一构造器注入；构造器在此显式声明以便校验
     * {@code apex.open-app.registry} 配置值。
     *
     * @param clientManagementService OAuth2 客户端管理服务
     * @param clientRepository 已注册客户端仓储
     * @param scopeRegistry 开放平台 scope 注册表
     * @param objectMapper JSON 序列化器
     * @param authRegistry 认证服务开放应用注册网关
     * @param userRepository 用户仓储
     * @param registry 注册表实现选择（{@code auth} 或 {@code local}）
     */
    public OpenAppAdminService(
            OAuth2ClientManagementService clientManagementService,
            OAuth2RegisteredClientRepository clientRepository,
            OpenPlatformScopeRegistry scopeRegistry,
            ObjectMapper objectMapper,
            OpenAppRegistryGateway authRegistry,
            UserRepository userRepository,
            @org.springframework.beans.factory.annotation.Value("${apex.open-app.registry:auth}") String registry) {
        if (!REGISTRY_AUTH.equals(registry) && !REGISTRY_LOCAL.equals(registry)) {
            throw new IllegalArgumentException("apex.open-app.registry must be auth or local");
        }
        this.clientManagementService = clientManagementService;
        this.clientRepository = clientRepository;
        this.scopeRegistry = scopeRegistry;
        this.objectMapper = objectMapper;
        this.authRegistry = authRegistry;
        this.userRepository = userRepository;
        this.localRegistry = REGISTRY_LOCAL.equals(registry);
    }

    /**
     * 测试与程序化装配入口：仅使用本地注册表、无认证网关与用户仓储的过渡形态。
     *
     * @param management OAuth2 客户端管理服务
     * @param repository 已注册客户端仓储
     * @param scopes 开放平台 scope 注册表
     * @param mapper JSON 序列化器
     * @return 构建完成的服务实例
     */
    public static OpenAppAdminService forTesting(
            OAuth2ClientManagementService management,
            OAuth2RegisteredClientRepository repository,
            OpenPlatformScopeRegistry scopes,
            ObjectMapper mapper) {
        return new OpenAppAdminService(management, repository, scopes, mapper, null, null, "local");
    }

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
        if (!localRegistry) {
            return validate(request, true)
                    .then(validateSubject(request))
                    .then(Mono.defer(() -> authRegistry.create(registration(request))))
                    .map(this::fromRegistration);
        }
        return validate(request, true).then(validateSubject(request)).then(Mono.defer(() -> {
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
        if (!localRegistry) {
            return validate(request, false)
                    .then(validateSubject(request))
                    .then(Mono.defer(() -> authRegistry.update(clientId, registration(request))))
                    .map(this::fromRegistration);
        }
        if (clientId == null || clientId.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "clientId 不能为空"));
        }
        return clientRepository
                .findByClientId(clientId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "客户端不存在")))
                .then(validate(request, false))
                .then(validateSubject(request))
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
        if (!localRegistry) {
            return Mono.defer(() -> authRegistry.get(clientId)).map(this::fromRegistration);
        }
        return clientRepository
                .findByClientId(clientId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "客户端不存在")))
                .map(entity -> toResponse(entity, null));
    }

    /**
     * 导入一条旧源开放应用（D25 迁移窗口）。
     *
     * <p>仅在 auth 单源模式下可用；本地回退模式拒绝，避免把导入写回已弃用的旧注册表。
     *
     * @param request 导入请求（含 bcrypt 哈希与幂等指纹）
     * @return 导入后的应用（不含明文密钥）
     */
    public Mono<OpenAppResponse> importApp(com.apex.scaffold.contract.dto.internal.OpenAppImportRequest request) {
        if (localRegistry) {
            return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "迁移导入仅在 auth 单源模式可用"));
        }
        return Mono.defer(() -> authRegistry.importApp(request)).map(this::fromRegistration);
    }

    /**
     * 查询开放应用限流档（D2 {@code rateLimitTier}，网关内部消费）。
     *
     * <p>读取开放应用元数据 {@code openPlatform.rateLimitTier}。非开放应用、未声明档位
     * 或客户端不存在时返回 {@code default}——限流判定的 fail-open 口径：网关对未知/异常
     * 客户端一律按默认档阈值放行判定，不因档位查询失败阻断开放调用。
     *
     * @param clientId 客户端 ID（空视为未声明）
     * @return 限流档编码（如 {@code default}/{@code standard}/{@code premium}）
     */
    public Mono<String> getRateLimitTier(String clientId) {
        if (!localRegistry) {
            return getApp(clientId)
                    .map(app -> app.getRateLimitTier() == null
                                    || app.getRateLimitTier().isBlank()
                            ? DEFAULT_RATE_LIMIT_TIER
                            : app.getRateLimitTier());
        }
        if (clientId == null || clientId.isBlank()) {
            return Mono.just(DEFAULT_RATE_LIMIT_TIER);
        }
        return clientRepository
                .findByClientId(clientId)
                .map(entity -> {
                    String tier = textOrNull(parseSettings(entity.getClientSettings()), KEY_RATE_LIMIT_TIER);
                    return tier == null || tier.isBlank() ? DEFAULT_RATE_LIMIT_TIER : tier;
                })
                // 元数据损坏等解析异常不阻断限流判定，回退默认档
                .onErrorResume(error -> Mono.just(DEFAULT_RATE_LIMIT_TIER))
                .defaultIfEmpty(DEFAULT_RATE_LIMIT_TIER);
    }

    /**
     * 分页查询客户端注册表（含未开通开放元数据的普通客户端）.
     *
     * @param page 页码（从 0 开始）
     * @param size 每页条数（1~100）
     * @return 应用信息流
     */
    public Flux<OpenAppResponse> listApps(int page, int size) {
        if (!localRegistry) {
            return Flux.defer(() -> authRegistry.list(Math.max(0, page), Math.min(Math.max(1, size), 100)))
                    .map(this::fromRegistration);
        }
        return clientRepository
                .findAll(Math.max(0, page), Math.min(Math.max(1, size), 100))
                .map(entity -> toResponse(entity, null));
    }

    /**
     * 平台 scope 目录（与创建/更新的合法性校验同源注册表）。
     *
     * @return scope 定义列表（内置 + 配置扩展）
     */
    public List<OpenPlatformScopeRegistry.ScopeDefinition> listScopes() {
        return scopeRegistry.list();
    }

    /**
     * 重置开放应用密钥.
     *
     * @param clientId 客户端 ID
     * @return 应用信息（{@code clientSecret} 为一次性明文）
     */
    public Mono<OpenAppResponse> resetSecret(String clientId) {
        if (!localRegistry) {
            return Mono.defer(() -> authRegistry.rotateSecret(clientId)).map(this::fromRegistration);
        }
        return clientRepository
                .findByClientId(clientId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "客户端不存在")))
                .then(clientManagementService.resetClientSecret(clientId))
                .flatMap(secret -> clientRepository.findByClientId(clientId).map(entity -> toResponse(entity, secret)));
    }

    private Mono<Void> validateSubject(OpenAppRequest request) {
        if (request == null || request.getSubjectUserId() == null) {
            return Mono.empty();
        }
        return Mono.deferContextual(context -> {
            String tenant = context.getOrDefault(TenantContext.REACTOR_CONTEXT_KEY, null);
            if (tenant == null || tenant.isBlank() || userRepository == null || request.getSubjectUserId() <= 0) {
                return Mono.error(new BusinessException(ResultCode.FORBIDDEN));
            }
            return TenantContext.withTenant(userRepository.findById(request.getSubjectUserId()))
                    .filter(user -> tenant.equals(user.getTenantId()) && user.isEnabled() && !user.isDeleted())
                    .switchIfEmpty(Mono.error(new BusinessException(ResultCode.FORBIDDEN)))
                    .then();
        });
    }

    private OpenAppRegistrationRequest registration(OpenAppRequest request) {
        return OpenAppRegistrationRequest.builder()
                .clientId(request.getClientId())
                .clientSecret(request.getClientSecret())
                .appName(request.getAppName())
                .description(request.getDescription())
                .redirectUris(request.getRedirectUris())
                .scopes(request.getScopes())
                .rateLimitTier(request.getRateLimitTier())
                .contact(request.getContact())
                .enabled(
                        request.getStatus() == null || request.getStatus().isBlank()
                                ? null
                                : STATUS_ENABLED.equals(
                                        request.getStatus().trim().toUpperCase(Locale.ROOT)))
                .subjectUserId(request.getSubjectUserId())
                .subjectValidated(request.getSubjectUserId() != null)
                .build();
    }

    private OpenAppResponse fromRegistration(OpenAppRegistrationResponse app) {
        return OpenAppResponse.builder()
                .clientId(app.getClientId())
                .appName(app.getAppName())
                .description(app.getDescription())
                .redirectUris(app.getRedirectUris())
                .scopes(app.getScopes())
                .rateLimitTier(app.getRateLimitTier())
                .contact(app.getContact())
                .status(Boolean.TRUE.equals(app.getEnabled()) ? STATUS_ENABLED : STATUS_DISABLED)
                .createdAt(app.getCreatedAt())
                .clientSecret(app.getClientSecret())
                .subjectUserId(app.getSubjectUserId())
                .build();
    }

    private Mono<Void> validate(OpenAppRequest request, boolean create) {
        return Mono.defer(() -> {
            if (request == null) {
                return Mono.error(new BusinessException(ResultCode.PARAM_ERROR));
            }
            if (create) {
                if (request.getClientId() == null || request.getClientId().isBlank()) {
                    return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "clientId 不能为空"));
                }
                if (request.getAppName() == null || request.getAppName().isBlank()) {
                    return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "appName 不能为空"));
                }
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

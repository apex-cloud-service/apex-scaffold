package com.apex.scaffold.auth.application.service;

import com.apex.scaffold.auth.domain.model.entity.AuthConfigEntity;
import com.apex.scaffold.auth.domain.repository.AuthConfigRepository;
import com.apex.scaffold.auth.openplatform.OpenAppScopes;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.contract.dto.internal.OpenAppImportRequest;
import com.apex.scaffold.contract.dto.internal.OpenAppRegistrationRequest;
import com.apex.scaffold.contract.dto.internal.OpenAppRegistrationResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 开放应用注册与签发服务。
 *
 * <p>{@code auth_config} 是开放应用的唯一注册与签发源，本服务不访问 system 旧注册表；
 * 所有读写都在调用方租户作用域内完成。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Service
@RequiredArgsConstructor
public class AuthOpenAppRegistryService {
    private static final String TYPE = "OAUTH2_CLIENT";
    private static final String BCRYPT = "\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}";
    private final AuthConfigRepository repository;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /**
     * 创建一个开放应用，未提供密钥时生成随机密钥并只返回一次。
     *
     * @param request 注册请求；{@code clientSecret} 为空时由服务端生成
     * @return 注册结果，含本次明文密钥
     * @throws BusinessException 请求非法或 clientId 已存在时抛出
     */
    public Mono<OpenAppRegistrationResponse> create(OpenAppRegistrationRequest request) {
        return tenant(tenant -> Mono.defer(() -> {
            validate(request, true);
            validateWriteScopes(request.getScopes(), request.getSubjectUserId());
            String secret = request.getClientSecret() == null
                            || request.getClientSecret().isBlank()
                    ? randomSecret()
                    : request.getClientSecret();
            if (secret.getBytes(StandardCharsets.UTF_8).length > 72
                    || secret.matches(BCRYPT)
                    || secret.startsWith("{bcrypt}")) {
                throw invalid();
            }
            return repository
                    .findByTypeAndKey(TYPE, request.getClientId(), tenant)
                    .hasElement()
                    .flatMap(exists -> exists
                            ? Mono.error(conflict())
                            : hash(secret)
                                    .flatMap(hash -> insert(tenant, request, hash, null))
                                    .map(entity -> response(entity, secret)));
        }));
    }

    /**
     * 更新开放应用的非敏感字段；不允许借更新接口修改 clientId 或密钥。
     *
     * @param clientId 应用标识
     * @param request 更新请求
     * @return 更新后的注册信息（不含密钥）
     * @throws BusinessException 请求非法或应用不存在时抛出
     */
    public Mono<OpenAppRegistrationResponse> update(String clientId, OpenAppRegistrationRequest request) {
        return tenant(tenant -> Mono.defer(() -> {
            validate(request, false);
            if (request.getClientId() != null && !clientId.equals(request.getClientId())
                    || request.getClientSecret() != null) {
                throw invalid();
            }
            return required(clientId, tenant).flatMap(entity -> {
                ObjectNode value = parse(entity);
                merge(value, request);
                validateBinding(value);
                if (request.getEnabled() != null) {
                    entity.setEnabled(request.getEnabled());
                }
                return update(entity, value).map(saved -> response(saved, null));
            });
        }));
    }

    /**
     * 查询单个开放应用。
     *
     * @param clientId 应用标识
     * @return 注册信息（不含密钥）
     * @throws BusinessException 应用不存在时抛出
     */
    public Mono<OpenAppRegistrationResponse> get(String clientId) {
        return tenant(tenant -> required(clientId, tenant).map(entity -> response(entity, null)));
    }

    /**
     * 分页查询当前租户的开放应用。
     *
     * @param page 页码，从 0 开始
     * @param size 每页条数，取值 1~100
     * @return 当前页应用列表（不含密钥）
     * @throws BusinessException 分页参数越界时抛出
     */
    public Flux<OpenAppRegistrationResponse> list(int page, int size) {
        return tenant(tenant -> {
                    if (page < 0 || size < 1 || size > 100) {
                        return Mono.error(invalid());
                    }
                    return repository
                            .findOAuth2Clients(tenant, page, size)
                            .map(entity -> response(entity, null))
                            .collectList();
                })
                .flatMapMany(Flux::fromIterable);
    }

    /**
     * 启用或停用开放应用。
     *
     * @param clientId 应用标识
     * @param enabled true=启用；false=停用
     * @return 更新后的注册信息（不含密钥）
     * @throws BusinessException 应用不存在时抛出
     */
    public Mono<OpenAppRegistrationResponse> status(String clientId, boolean enabled) {
        return tenant(tenant -> required(clientId, tenant).flatMap(entity -> {
            ObjectNode value = parse(entity);
            entity.setEnabled(enabled);
            return update(entity, value).map(saved -> response(saved, null));
        }));
    }

    /**
     * 轮换客户端密钥，新密钥只在本方法返回值中出现一次。
     *
     * @param clientId 应用标识
     * @return 注册信息，{@code clientSecret} 为本次新生成的明文密钥
     * @throws BusinessException 应用不存在时抛出
     */
    public Mono<OpenAppRegistrationResponse> rotateSecret(String clientId) {
        return tenant(tenant -> required(clientId, tenant).flatMap(entity -> {
            String secret = randomSecret();
            return hash(secret).flatMap(hash -> {
                ObjectNode value = parse(entity);
                value.put("clientSecret", hash);
                return update(entity, value).map(saved -> response(saved, secret));
            });
        }));
    }

    /**
     * 删除开放应用。
     *
     * @param clientId 应用标识
     * @throws BusinessException 应用不存在时抛出
     */
    public Mono<Void> delete(String clientId) {
        return tenant(
                tenant -> required(clientId, tenant).flatMap(entity -> repository.deleteById(entity.getId(), tenant)));
    }

    /** 相同源行和内容可重入；不覆盖已被管理面修改的目标。 */
    public Mono<OpenAppRegistrationResponse> importApp(OpenAppImportRequest request) {
        return tenant(tenant -> Mono.defer(() -> {
            if (request == null || !validator.validate(request).isEmpty()) {
                throw invalid();
            }
            OpenAppRegistrationRequest registration = request.getRegistration();
            validate(registration, true);
            if (registration.getClientSecret() != null || !"BCRYPT".equals(request.getHashAlgorithm())) {
                throw invalid();
            }
            String hash = request.getSecretHash();
            if (hash.startsWith("{bcrypt}")) {
                hash = hash.substring(8);
            }
            if (!hash.matches(BCRYPT)) {
                throw invalid();
            }
            ObjectNode importMetadata = objectMapper.createObjectNode();
            importMetadata.put("id", request.getImportId());
            importMetadata.put("fingerprint", request.getSourceFingerprint().toLowerCase(java.util.Locale.ROOT));
            importMetadata.put("payloadDigest", digest(request));
            String importedHash = hash;
            return repository
                    .findByTypeAndKey(TYPE, registration.getClientId(), tenant)
                    .flatMap(existing -> {
                        JsonNode previous = parse(existing).path("registryImport");
                        if (!previous.equals(importMetadata)
                                || !Integer.valueOf(1).equals(existing.getVersion())) {
                            return Mono.error(conflict());
                        }
                        return Mono.just(response(existing, null));
                    })
                    .switchIfEmpty(Mono.defer(() -> insert(tenant, registration, importedHash, importMetadata)
                            .map(saved -> response(saved, null))));
        }));
    }

    private Mono<AuthConfigEntity> insert(
            String tenant, OpenAppRegistrationRequest request, String secretHash, ObjectNode importMetadata) {
        ObjectNode value = objectMapper.createObjectNode();
        value.put("clientId", request.getClientId());
        value.put("clientSecret", secretHash);
        value.putArray("authorizationGrantTypes").add("client_credentials");
        value.putArray("clientAuthenticationMethods").add("client_secret_basic");
        merge(value, request);
        value.withObject("/openPlatform").put("createdAt", LocalDateTime.now().toString());
        value.put("enabled", !Boolean.FALSE.equals(request.getEnabled()));
        validateBinding(value);
        if (importMetadata != null) {
            value.set("registryImport", importMetadata);
        }
        LocalDateTime now = LocalDateTime.now();
        AuthConfigEntity entity = AuthConfigEntity.builder()
                .tenantId(tenant)
                .configType(TYPE)
                .configKey(request.getClientId())
                .configValue(value.toString())
                .description(request.getDescription())
                .enabled(!Boolean.FALSE.equals(request.getEnabled()))
                .isDefault(false)
                .version(1)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return repository.save(entity).onErrorMap(DuplicateKeyException.class, error -> conflict());
    }

    private Mono<AuthConfigEntity> update(AuthConfigEntity entity, ObjectNode value) {
        int expectedVersion = entity.getVersion() == null ? 0 : entity.getVersion();
        value.put("enabled", entity.isEnabled());
        // 旧平铺status只用于导入归一；签发以后统一看enabled与auth_config.enabled。
        value.remove("openPlatform.status");
        value.withObject("/openPlatform").remove("status");
        entity.setConfigValue(value.toString());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setVersion(expectedVersion + 1);
        return repository.updateOpenApp(entity, expectedVersion);
    }

    private void merge(ObjectNode value, OpenAppRegistrationRequest request) {
        ObjectNode metadata = value.withObject("/openPlatform");
        put(metadata, "appName", request.getAppName());
        put(metadata, "description", request.getDescription());
        put(metadata, "rateLimitTier", request.getRateLimitTier());
        put(metadata, "contact", request.getContact());
        if (request.getRedirectUris() != null) {
            value.set("redirectUris", objectMapper.valueToTree(request.getRedirectUris()));
        }
        if (request.getScopes() != null) {
            value.set("scopes", objectMapper.valueToTree(request.getScopes()));
        }
        if (request.getSubjectUserId() != null) {
            value.put("subjectUserId", request.getSubjectUserId());
            metadata.remove("subjectUserId");
        }
    }

    private void validate(OpenAppRegistrationRequest request, boolean create) {
        if (request == null
                || !validator.validate(request).isEmpty()
                || create
                        && (request.getClientId() == null
                                || request.getClientId().isBlank()
                                || request.getAppName() == null
                                || request.getAppName().isBlank())
                || request.getClientId() != null && !request.getClientId().matches("[A-Za-z0-9._-]{1,100}")
                || request.getAppName() != null && request.getAppName().isBlank()
                || !OpenAppScopes.invalidScopes(request.getScopes()).isEmpty()
                || request.getSubjectUserId() != null && !Boolean.TRUE.equals(request.getSubjectValidated())) {
            throw invalid();
        }
        if (request.getRedirectUris() != null) {
            for (String redirect : request.getRedirectUris()) {
                try {
                    java.net.URI uri = java.net.URI.create(redirect);
                    if (uri.getHost() == null
                            || uri.getUserInfo() != null
                            || uri.getFragment() != null
                            || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) {
                        throw invalid();
                    }
                } catch (IllegalArgumentException error) {
                    throw invalid();
                }
            }
        }
    }

    /** 写 scope 必须绑定主体账号，否则会产生无属主写操作。 */
    private void validateWriteScopes(Set<String> scopes, Long subjectUserId) {
        if (scopes != null
                && (scopes.contains(OpenAppScopes.FILE_WRITE) || scopes.contains(OpenAppScopes.WORKFLOW_WRITE))
                && (subjectUserId == null || subjectUserId <= 0)) {
            throw invalid();
        }
    }

    private void validateBinding(ObjectNode value) {
        Set<String> scopes = strings(value.path("scopes"));
        long subject = value.path("subjectUserId")
                .asLong(value.path("openPlatform").path("subjectUserId").asLong(0));
        if ((scopes.contains(OpenAppScopes.FILE_WRITE) || scopes.contains(OpenAppScopes.WORKFLOW_WRITE))
                && subject <= 0) {
            throw invalid();
        }
    }

    private Mono<AuthConfigEntity> required(String clientId, String tenant) {
        if (clientId == null || !clientId.matches("[A-Za-z0-9._-]{1,100}")) {
            return Mono.error(invalid());
        }
        return repository
                .findByTypeAndKey(TYPE, clientId, tenant)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND)));
    }

    private ObjectNode parse(AuthConfigEntity entity) {
        try {
            JsonNode node = objectMapper.readTree(entity.getConfigValue());
            if (node instanceof ObjectNode object) {
                return object;
            }
        } catch (Exception error) {
            throw invalid();
        }
        throw invalid();
    }

    private OpenAppRegistrationResponse response(AuthConfigEntity entity, String secret) {
        ObjectNode value = parse(entity);
        JsonNode metadata = value.path("openPlatform");
        JsonNode subject =
                value.hasNonNull("subjectUserId") ? value.path("subjectUserId") : metadata.path("subjectUserId");
        return OpenAppRegistrationResponse.builder()
                .clientId(entity.getConfigKey())
                .appName(
                        metadata.path("appName").asText(value.path("clientName").asText(entity.getConfigKey())))
                .description(text(metadata, "description"))
                .contact(text(metadata, "contact"))
                .rateLimitTier(text(metadata, "rateLimitTier"))
                .redirectUris(strings(value.path("redirectUris")))
                .scopes(strings(value.path("scopes")))
                .enabled(entity.isEnabled() && value.path("enabled").asBoolean(true))
                .createdAt(metadata.path("createdAt")
                        .asText(
                                entity.getCreatedAt() == null
                                        ? null
                                        : entity.getCreatedAt().toString()))
                .subjectUserId(subject.isMissingNode() || subject.isNull() ? null : subject.asLong())
                .clientSecret(secret)
                .build();
    }

    private <T> Mono<T> tenant(Function<String, Mono<T>> action) {
        return Mono.deferContextual(context -> {
            String tenant = context.getOrDefault(TenantContext.REACTOR_CONTEXT_KEY, null);
            if (tenant == null || tenant.isBlank()) {
                return Mono.error(new BusinessException(ResultCode.FORBIDDEN));
            }
            return action.apply(tenant);
        });
    }

    private Mono<String> hash(String secret) {
        return Mono.fromCallable(() -> encoder.encode(secret)).subscribeOn(BlockingIoSchedulers.general());
    }

    private static String randomSecret() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    private String digest(OpenAppImportRequest request) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(objectMapper.writeValueAsBytes(request)));
        } catch (Exception error) {
            throw invalid();
        }
    }

    private static void put(ObjectNode value, String key, String text) {
        if (text != null) {
            value.put(key, text);
        }
    }

    private static Set<String> strings(JsonNode array) {
        Set<String> result = new HashSet<>();
        if (array.isArray()) {
            array.forEach(item -> result.add(item.asText()));
        }
        return result;
    }

    private static String text(JsonNode value, String key) {
        return value.hasNonNull(key) ? value.path(key).asText() : null;
    }

    private static BusinessException invalid() {
        return new BusinessException(ResultCode.PARAM_ERROR);
    }

    private static BusinessException conflict() {
        return new BusinessException(409, "Open application registration conflict");
    }
}

package com.apex.scaffold.authcenter.application.service.impl;

import com.apex.scaffold.authcenter.application.service.AuthConfigService;
import com.apex.scaffold.authcenter.domain.model.entity.AuthConfigEntity;
import com.apex.scaffold.authcenter.domain.repository.AuthConfigRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 认证配置服务实现
 *
 * <p>实现认证配置的业务逻辑，包括配置管理、配置缓存和热更新支持。
 * 使用Caffeine作为本地缓存，提升配置查询性能。
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthConfigServiceImpl implements AuthConfigService {

    private final AuthConfigRepository authConfigRepository;

    /**
     * 本地缓存，缓存配置数据
     * key格式: {tenantId}:{configType}:{configKey}
     */
    private final Cache<String, AuthConfigEntity> configCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

    @Override
    public Mono<AuthConfigEntity> createConfig(AuthConfigEntity config) {
        log.debug(
                "Creating auth config: type={}, key={}, tenantId={}",
                config.getConfigType(),
                config.getConfigKey(),
                config.getTenantId());

        // 初始化审计字段：创建/更新时间与版本号
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        config.setVersion(1);

        // 检查配置键是否已存在
        return authConfigRepository
                .existsByTypeAndKey(config.getConfigType(), config.getConfigKey(), config.getTenantId())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalArgumentException(
                                "Config already exists: " + config.getConfigType() + ":" + config.getConfigKey()));
                    }
                    return authConfigRepository.save(config);
                })
                .doOnSuccess(saved -> {
                    // 清除缓存
                    String cacheKey = buildCacheKey(saved.getTenantId(), saved.getConfigType(), saved.getConfigKey());
                    configCache.invalidate(cacheKey);
                    log.info(
                            "Auth config created successfully: id={}, type={}, key={}",
                            saved.getId(),
                            saved.getConfigType(),
                            saved.getConfigKey());
                });
    }

    @Override
    public Mono<AuthConfigEntity> updateConfig(Long id, AuthConfigEntity config, String tenantId) {
        log.debug("Updating auth config: id={}, tenantId={}", id, tenantId);

        return authConfigRepository
                .findById(id, tenantId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Config not found: " + id)))
                .flatMap(existing -> {
                    // 仅覆盖请求中非空的字段（部分更新语义）
                    if (config.getConfigValue() != null) {
                        existing.setConfigValue(config.getConfigValue());
                    }
                    if (config.getDescription() != null) {
                        existing.setDescription(config.getDescription());
                    }
                    if (config.getEnabled() != null) {
                        existing.setEnabled(config.getEnabled());
                    }

                    existing.setUpdatedAt(LocalDateTime.now());
                    existing.incrementVersion();

                    return authConfigRepository.update(existing);
                })
                .doOnSuccess(updated -> {
                    // 清除缓存
                    String cacheKey =
                            buildCacheKey(updated.getTenantId(), updated.getConfigType(), updated.getConfigKey());
                    configCache.invalidate(cacheKey);
                    log.info(
                            "Auth config updated successfully: id={}, version={}",
                            updated.getId(),
                            updated.getVersion());
                });
    }

    @Override
    public Mono<Void> deleteConfig(Long id, String tenantId) {
        log.debug("Deleting auth config: id={}, tenantId={}", id, tenantId);

        return authConfigRepository
                .findById(id, tenantId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Config not found: " + id)))
                .flatMap(config -> {
                    // 不允许删除系统默认配置
                    if (config.isDefaultConfig()) {
                        return Mono.error(new IllegalArgumentException("Cannot delete default config: " + id));
                    }

                    // 清除缓存
                    String cacheKey =
                            buildCacheKey(config.getTenantId(), config.getConfigType(), config.getConfigKey());
                    configCache.invalidate(cacheKey);

                    return authConfigRepository.deleteById(id, tenantId);
                })
                .doOnSuccess(v -> log.info("Auth config deleted successfully: id={}", id));
    }

    @Override
    public Mono<AuthConfigEntity> getConfigById(Long id, String tenantId) {
        log.debug("Getting auth config by id: id={}, tenantId={}", id, tenantId);
        return authConfigRepository.findById(id, tenantId);
    }

    @Override
    public Mono<AuthConfigEntity> getConfigByTypeAndKey(String configType, String configKey, String tenantId) {
        log.debug("Getting auth config: type={}, key={}, tenantId={}", configType, configKey, tenantId);

        String cacheKey = buildCacheKey(tenantId, configType, configKey);

        // 先从缓存获取
        AuthConfigEntity cached = configCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("Config found in cache: {}", cacheKey);
            return Mono.just(cached);
        }

        // 缓存未命中，从数据库查询
        return authConfigRepository
                .findByTypeAndKey(configType, configKey, tenantId)
                .doOnSuccess(config -> {
                    if (config != null) {
                        configCache.put(cacheKey, config);
                        log.debug("Config cached: {}", cacheKey);
                    }
                });
    }

    @Override
    public Flux<AuthConfigEntity> listConfigsByType(String configType, String tenantId) {
        log.debug("Listing configs by type: type={}, tenantId={}", configType, tenantId);
        return authConfigRepository.findByType(configType, tenantId);
    }

    @Override
    public Flux<AuthConfigEntity> listAllConfigs(String tenantId) {
        log.debug("Listing all configs: tenantId={}", tenantId);
        return authConfigRepository.findAll(tenantId);
    }

    @Override
    public Flux<AuthConfigEntity> listEnabledConfigs(String tenantId) {
        log.debug("Listing enabled configs: tenantId={}", tenantId);
        return authConfigRepository.findAllEnabled(tenantId);
    }

    @Override
    public Mono<AuthConfigEntity> enableConfig(Long id, String tenantId) {
        log.debug("Enabling config: id={}, tenantId={}", id, tenantId);

        return authConfigRepository
                .findById(id, tenantId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Config not found: " + id)))
                .flatMap(config -> {
                    config.enable();
                    config.setUpdatedAt(LocalDateTime.now());
                    config.incrementVersion();
                    return authConfigRepository.update(config);
                })
                .doOnSuccess(updated -> {
                    String cacheKey =
                            buildCacheKey(updated.getTenantId(), updated.getConfigType(), updated.getConfigKey());
                    configCache.invalidate(cacheKey);
                    log.info("Config enabled: id={}", id);
                });
    }

    @Override
    public Mono<AuthConfigEntity> disableConfig(Long id, String tenantId) {
        log.debug("Disabling config: id={}, tenantId={}", id, tenantId);

        return authConfigRepository
                .findById(id, tenantId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Config not found: " + id)))
                .flatMap(config -> {
                    config.disable();
                    config.setUpdatedAt(LocalDateTime.now());
                    config.incrementVersion();
                    return authConfigRepository.update(config);
                })
                .doOnSuccess(updated -> {
                    String cacheKey =
                            buildCacheKey(updated.getTenantId(), updated.getConfigType(), updated.getConfigKey());
                    configCache.invalidate(cacheKey);
                    log.info("Config disabled: id={}", id);
                });
    }

    @Override
    public Mono<AuthConfigEntity> getPasswordPolicy(String tenantId) {
        log.debug("Getting password policy: tenantId={}", tenantId);
        return getConfigByTypeAndKey("PASSWORD_POLICY", "default", tenantId).switchIfEmpty(getDefaultPasswordPolicy());
    }

    @Override
    public Mono<AuthConfigEntity> getSessionPolicy(String tenantId) {
        log.debug("Getting session policy: tenantId={}", tenantId);
        return getConfigByTypeAndKey("SESSION_POLICY", "default", tenantId).switchIfEmpty(getDefaultSessionPolicy());
    }

    @Override
    public Mono<AuthConfigEntity> getSecurityPolicy(String tenantId) {
        log.debug("Getting security policy: tenantId={}", tenantId);
        return getConfigByTypeAndKey("SECURITY_POLICY", "default", tenantId).switchIfEmpty(getDefaultSecurityPolicy());
    }

    @Override
    public Mono<AuthConfigEntity> getOauth2ClientConfig(String clientId, String tenantId) {
        log.debug("Getting OAuth2 client config: clientId={}, tenantId={}", clientId, tenantId);
        return getConfigByTypeAndKey("OAUTH2_CLIENT", clientId, tenantId);
    }

    @Override
    public Mono<Long> batchUpdateConfigs(Flux<AuthConfigEntity> configs, String tenantId) {
        log.debug("Batch updating configs: tenantId={}", tenantId);

        return configs.flatMap(config -> updateConfig(config.getId(), config, tenantId))
                .count()
                .doOnSuccess(count -> log.info("Batch update completed: count={}", count));
    }

    @Override
    public Mono<AuthConfigEntity> resetToDefault(String configType, String tenantId) {
        log.debug("Resetting config to default: type={}, tenantId={}", configType, tenantId);

        return authConfigRepository
                .findByType(configType, tenantId)
                .flatMap(config -> authConfigRepository.deleteById(config.getId(), tenantId))
                .then(Mono.defer(() -> {
                    // 清除缓存
                    configCache.invalidateAll();
                    // 返回内置默认配置（重置后的生效值）
                    return getDefaultConfigByType(configType);
                }))
                .doOnSuccess(v -> log.info("Config reset to default: type={}", configType));
    }

    /**
     * 构建缓存键
     *
     * @param tenantId 租户ID
     * @param configType 配置类型
     * @param configKey 配置键
     * @return 缓存键
     */
    private String buildCacheKey(String tenantId, String configType, String configKey) {
        return tenantId + ":" + configType + ":" + configKey;
    }

    /**
     * 获取默认密码策略
     *
     * @return 默认密码策略配置
     */
    private Mono<AuthConfigEntity> getDefaultPasswordPolicy() {
        return Mono.just(AuthConfigEntity.builder()
                .configType("PASSWORD_POLICY")
                .configKey("default")
                .configValue("{\"minLength\":8,\"requireUppercase\":true,\"requireLowercase\":true,"
                        + "\"requireDigit\":true,\"requireSpecialChar\":true,\"maxAge\":90,"
                        + "\"historyCount\":5,\"lockoutThreshold\":5,\"lockoutDuration\":1800}")
                .description("Default password policy")
                .enabled(true)
                .isDefault(true)
                .version(1)
                .build());
    }

    /**
     * 获取默认会话策略
     *
     * @return 默认会话策略配置
     */
    private Mono<AuthConfigEntity> getDefaultSessionPolicy() {
        return Mono.just(AuthConfigEntity.builder()
                .configType("SESSION_POLICY")
                .configKey("default")
                .configValue("{\"timeout\":3600,\"maxConcurrent\":3,\"singleDevice\":false,"
                        + "\"refreshEnabled\":true,\"refreshThreshold\":300}")
                .description("Default session policy")
                .enabled(true)
                .isDefault(true)
                .version(1)
                .build());
    }

    /**
     * 获取默认安全策略
     *
     * @return 默认安全策略配置
     */
    private Mono<AuthConfigEntity> getDefaultSecurityPolicy() {
        return Mono.just(AuthConfigEntity.builder()
                .configType("SECURITY_POLICY")
                .configKey("default")
                .configValue("{\"mfaEnabled\":false,\"ipWhitelistEnabled\":false,"
                        + "\"bruteForceProtection\":true,\"maxLoginAttempts\":5,"
                        + "\"loginAttemptWindow\":300,\"accountLockoutDuration\":1800}")
                .description("Default security policy")
                .enabled(true)
                .isDefault(true)
                .version(1)
                .build());
    }

    /**
     * 根据类型获取默认配置
     *
     * @param configType 配置类型
     * @return 默认配置
     */
    private Mono<AuthConfigEntity> getDefaultConfigByType(String configType) {
        return switch (configType) {
            case "PASSWORD_POLICY" -> getDefaultPasswordPolicy();
            case "SESSION_POLICY" -> getDefaultSessionPolicy();
            case "SECURITY_POLICY" -> getDefaultSecurityPolicy();
            default -> Mono.empty();
        };
    }
}

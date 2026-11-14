package com.apex.scaffold.application.service;

import com.apex.scaffold.application.assembler.ConfigAssembler;
import com.apex.scaffold.application.dto.admin.ConfigDTO;
import com.apex.scaffold.application.dto.admin.ConfigRequest;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.entity.ConfigEntity;
import com.apex.scaffold.domain.repository.ConfigRepository;
import com.apex.scaffold.domain.repository.TenantConfigRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 参数配置应用服务。
 *
 * <p>提供系统参数配置的查询、创建、更新、删除与启停管理，
 * 是管理系统运行时参数（系统名称、默认分页、上传限制等）的基础支撑。
 * 参数采用键值对模型，内置参数禁止删除以保护系统功能。</p>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>参数键名全局唯一，创建与更新时校验（{@code CONFIG_KEY_EXISTS}）</li>
 *   <li>内置参数（{@code configType=0}）禁止删除，防止系统功能失效</li>
 *   <li>参数键名创建后不允许变更（键名是代码读取的依赖）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigService {

    /**
     * 参数配置仓储。
     */
    private final ConfigRepository configRepository;

    /**
     * 参数配置对象转换器。
     */
    private final ConfigAssembler configAssembler;

    /**
     * 租户写链路守卫。
     */
    private final TenantGuard tenantGuard;

    private final TenantConfigRepository tenantConfigRepository;

    /**
     * 查询全部参数配置。
     *
     * @return 参数配置 DTO 列表
     */
    public Mono<List<ConfigDTO>> listConfigs() {
        return configRepository.findAll().map(configAssembler::toDTOList);
    }

    /**
     * 根据键名查询参数值。
     *
     * <p>供业务代码读取配置值，未找到时返回 {@code null}。</p>
     *
     * @param configKey 参数键名
     * @return 参数键值，未找到时为 {@code null}
     */
    public Mono<String> getValueByKey(String configKey) {
        // B6：读取优先级 租户配置 → 全局配置（平台租户的租户配置即全局语义，直接走全局）
        String tenant =
                com.apex.scaffold.common.context.TenantContext.currentTenantId().orElse(null);
        Mono<String> tenantValue = (tenant == null
                        || tenant.isBlank()
                        || com.apex.scaffold.domain.model.entity.TenantPackageEntity.PLATFORM_TENANT_CODE.equals(
                                tenant))
                ? Mono.empty()
                : tenantConfigRepository
                        .findByTenantAndKey(tenant, configKey)
                        .filter(e -> e.getDeleted() == null || e.getDeleted() == 0)
                        .map(com.apex.scaffold.domain.model.entity.TenantConfigEntity::getConfigValue)
                        .onErrorResume(com.apex.scaffold.common.exception.BusinessException.class, e -> Mono.empty());
        return tenantValue.switchIfEmpty(configRepository.findByKey(configKey).map(ConfigEntity::getConfigValue));
    }

    /**
     * 查询参数配置详情。
     *
     * @param id 参数配置 ID
     * @return 参数配置实体
     * @throws BusinessException 当参数不存在时抛出 {@code CONFIG_NOT_FOUND}
     */
    public Mono<ConfigEntity> getConfig(Long id) {
        return configRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.CONFIG_NOT_FOUND)));
    }

    /**
     * 创建参数配置。
     *
     * <p>校验键名唯一性后持久化。</p>
     *
     * @param request 参数配置请求体
     * @return 已创建的参数配置实体
     * @throws BusinessException 当键名已存在时抛出 {@code CONFIG_KEY_EXISTS}
     */
    public Mono<ConfigEntity> createConfig(ConfigRequest request) {
        return tenantGuard.assertActive().then(Mono.defer(() -> configRepository
                .existsByKey(request.getConfigKey(), null)
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new BusinessException(ResultCode.CONFIG_KEY_EXISTS));
                    }
                    ConfigEntity entity = ConfigEntity.create(
                            request.getConfigName(), request.getConfigKey(), request.getConfigValue());
                    entity.setRemark(request.getRemark());
                    return configRepository.insert(entity).map(rows -> {
                        log.info("创建参数配置成功，key: {}", entity.getConfigKey());
                        return entity;
                    });
                })));
    }

    /**
     * 更新参数配置。
     *
     * <p>键名不允许变更，仅更新名称、值与备注。</p>
     *
     * @param id 参数配置 ID
     * @param request 参数配置请求体
     * @return 已更新的参数配置实体
     * @throws BusinessException 当参数不存在时抛出
     */
    public Mono<ConfigEntity> updateConfig(Long id, ConfigRequest request) {
        return getConfig(id)
                .flatMap(existing -> {
                    existing.updateInfo(request.getConfigName(), request.getConfigValue(), request.getRemark());
                    return configRepository.updateById(existing).map(rows -> existing);
                })
                .doOnSuccess(config -> log.info("更新参数配置成功，id: {}", id));
    }

    /**
     * 删除参数配置。
     *
     * <p>内置参数禁止删除，防止系统功能失效。</p>
     *
     * @param id 参数配置 ID
     * @return 删除结果，true-删除成功，false-未删除任何记录
     * @throws BusinessException 当参数不存在或为内置参数时抛出
     */
    public Mono<Boolean> deleteConfig(Long id) {
        return getConfig(id)
                .flatMap(config -> {
                    if (config.isBuiltin()) {
                        return Mono.error(new BusinessException(ResultCode.CONFIG_NOT_FOUND, "内置参数禁止删除"));
                    }
                    return configRepository.deleteById(id).map(rows -> rows > 0);
                })
                .doOnSuccess(deleted -> log.info("删除参数配置完成，id: {}, deleted: {}", id, deleted));
    }

    /**
     * 启用或停用参数配置。
     *
     * @param id 参数配置 ID
     * @param enabled true-启用，false-停用
     * @return 更新后的参数配置实体
     * @throws BusinessException 当参数不存在时抛出
     */
    public Mono<ConfigEntity> updateConfigStatus(Long id, boolean enabled) {
        return getConfig(id).flatMap(config -> {
            if (enabled) {
                config.enable();
            } else {
                config.disable();
            }
            return configRepository.updateById(config).map(rows -> config);
        });
    }
}

package com.apex.scaffold.application.service;

import com.apex.scaffold.application.assembler.SysSsoProviderAssembler;
import com.apex.scaffold.application.dto.admin.SsoProviderDTO;
import com.apex.scaffold.application.dto.admin.SsoProviderRequest;
import com.apex.scaffold.application.support.sso.SsoProtocolAdapterFactory;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.entity.SysSsoProviderEntity;
import com.apex.scaffold.domain.repository.SysSsoProviderRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * SSO 提供商管理服务
 *
 * <p>提供 SSO 单点登录提供商的管理功能,包括提供商的创建、更新、删除、查询、连接测试和元数据获取。
 * 支持 SAML 2.0、CAS、LDAP 和 OIDC 等多种协议类型。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>创建提供商:校验名称唯一性后持久化,自动关联租户</li>
 *   <li>更新提供商:支持更新配置信息,租户隔离校验</li>
 *   <li>删除提供商:租户权限校验,物理删除</li>
 *   <li>查询提供商:支持单个查询和分页列表查询</li>
 *   <li>测试连接:通过协议适配器测试 SSO 提供商连接</li>
 *   <li>获取元数据:获取协议特定的元数据信息(SAML metadata/OIDC discovery 等)</li>
 * </ul>
 *
 * <h2>租户隔离</h2>
 * <ul>
 *   <li>所有操作自动关联当前租户上下文</li>
 *   <li>租户间数据完全隔离,无法跨租户访问</li>
 *   <li>提供商名称在租户内唯一</li>
 * </ul>
 *
 * <h2>协议适配器</h2>
 * <p>通过 {@link SsoProtocolAdapterFactory} 获取对应协议的适配器,
 * 支持 SAML、CAS、LDAP、OIDC 等协议的统一抽象和管理。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysSsoProviderService {

    private final SysSsoProviderRepository ssoProviderRepository;
    private final SysSsoProviderAssembler ssoProviderAssembler;
    private final SsoProtocolAdapterFactory ssoProtocolAdapterFactory;
    private final ObjectMapper objectMapper;

    /**
     * 创建 SSO 提供商
     *
     * <p>校验提供商名称在租户内的唯一性,验证通过后持久化配置。
     * 提供商自动关联当前租户上下文。
     *
     * @param request SSO 提供商配置请求
     * @return 创建成功的提供商 DTO
     * @throws BusinessException 当租户上下文未初始化或提供商名称已存在时
     */
    public Mono<SsoProviderDTO> create(SsoProviderRequest request) {
        String tenantId = TenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException(ResultCode.FORBIDDEN, "租户上下文未初始化"));
        log.info(
                "创建 SSO 提供商: name={}, protocolType={}, tenantId={}",
                request.getProviderName(),
                request.getProtocolType(),
                tenantId);

        return ssoProviderRepository
                .findByNameAndTenantId(request.getProviderName(), tenantId)
                .hasElement()
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "SSO 提供商名称已存在"));
                    }
                    SysSsoProviderEntity entity = ssoProviderAssembler.toEntity(request);
                    entity.setTenantId(tenantId);
                    return ssoProviderRepository
                            .insert(entity)
                            .thenReturn(entity)
                            .map(ssoProviderAssembler::toDTO);
                });
    }

    /**
     * 更新 SSO 提供商
     *
     * <p>更新提供商配置信息,校验租户权限和名称唯一性。
     * 只能更新当前租户下的提供商。
     *
     * @param id 提供商 ID
     * @param request SSO 提供商配置请求
     * @return 更新后的提供商 DTO
     * @throws BusinessException 当提供商不存在、无权操作或名称冲突时
     */
    public Mono<SsoProviderDTO> update(Long id, SsoProviderRequest request) {
        String tenantId = TenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException(ResultCode.FORBIDDEN, "租户上下文未初始化"));
        log.info("更新 SSO 提供商: id={}, name={}, tenantId={}", id, request.getProviderName(), tenantId);

        return ssoProviderRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "SSO 提供商不存在")))
                .flatMap(existingEntity -> {
                    if (!tenantId.equals(existingEntity.getTenantId())) {
                        return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "无权操作其他租户的 SSO 提供商"));
                    }

                    return ssoProviderRepository
                            .findByNameAndTenantId(request.getProviderName(), tenantId)
                            .flatMap(duplicateEntity -> {
                                if (!duplicateEntity.getId().equals(id)) {
                                    return Mono.<SysSsoProviderEntity>error(
                                            new BusinessException(ResultCode.BUSINESS_ERROR, "SSO 提供商名称已存在"));
                                }
                                return Mono.just(existingEntity);
                            })
                            .switchIfEmpty(Mono.just(existingEntity));
                })
                .flatMap(existingEntity -> {
                    SysSsoProviderEntity updatedEntity = ssoProviderAssembler.toEntity(request);
                    updatedEntity.setId(existingEntity.getId());
                    updatedEntity.setTenantId(existingEntity.getTenantId());
                    return ssoProviderRepository.updateById(updatedEntity).thenReturn(updatedEntity);
                })
                .map(ssoProviderAssembler::toDTO);
    }

    /**
     * 删除 SSO 提供商
     *
     * <p>删除指定的 SSO 提供商配置,校验租户权限。
     * 只能删除当前租户下的提供商。
     *
     * @param id 提供商 ID
     * @return 删除是否成功
     * @throws BusinessException 当提供商不存在或无权操作时
     */
    public Mono<Boolean> delete(Long id) {
        String tenantId = TenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException(ResultCode.FORBIDDEN, "租户上下文未初始化"));
        log.info("删除 SSO 提供商: id={}, tenantId={}", id, tenantId);

        return ssoProviderRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "SSO 提供商不存在")))
                .flatMap(entity -> {
                    if (!tenantId.equals(entity.getTenantId())) {
                        return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "无权操作其他租户的 SSO 提供商"));
                    }
                    return ssoProviderRepository.deleteById(id).thenReturn(true);
                });
    }

    /**
     * 根据 ID 查询 SSO 提供商详情
     *
     * <p>查询指定 ID 的 SSO 提供商详细配置,校验租户权限。
     * 只能查询当前租户下的提供商。
     *
     * @param id 提供商 ID
     * @return 提供商 DTO
     * @throws BusinessException 当提供商不存在或无权查看时
     */
    public Mono<SsoProviderDTO> getById(Long id) {
        String tenantId = TenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException(ResultCode.FORBIDDEN, "租户上下文未初始化"));
        log.debug("查询 SSO 提供商详情: id={}, tenantId={}", id, tenantId);

        return ssoProviderRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "SSO 提供商不存在")))
                .flatMap(entity -> {
                    if (!tenantId.equals(entity.getTenantId())) {
                        return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "无权查看其他租户的 SSO 提供商"));
                    }
                    return Mono.just(ssoProviderAssembler.toDTO(entity));
                });
    }

    /**
     * 分页查询 SSO 提供商列表
     *
     * <p>查询当前租户下的 SSO 提供商列表,支持按名称模糊查询和分页。
     * 结果自动按租户隔离,只返回当前租户的提供商。
     *
     * @param name 提供商名称(模糊查询,可选)
     * @param page 页码(从 0 开始)
     * @param size 每页大小
     * @return 提供商 DTO 列表
     * @throws BusinessException 当租户上下文未初始化时
     */
    public Mono<List<SsoProviderDTO>> list(String name, int page, int size) {
        String tenantId = TenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException(ResultCode.FORBIDDEN, "租户上下文未初始化"));
        log.debug("分页查询 SSO 提供商列表: name={}, page={}, size={}, tenantId={}", name, page, size, tenantId);

        return ssoProviderRepository
                .findByTenantId(tenantId)
                .collectList()
                .map(entities -> {
                    if (name != null && !name.trim().isEmpty()) {
                        String lowerName = name.toLowerCase();
                        entities = entities.stream()
                                .filter(e -> e.getProviderName().toLowerCase().contains(lowerName))
                                .toList();
                    }
                    int start = Math.min(page * size, entities.size());
                    int end = Math.min(start + size, entities.size());
                    return entities.subList(start, end);
                })
                .map(ssoProviderAssembler::toDTOList);
    }

    /**
     * 测试 SSO 提供商连接
     *
     * <p>通过协议适配器测试 SSO 提供商的连接可用性,验证配置是否正确、端点是否可访问、证书是否有效等。
     * 返回测试结果包含 success(是否成功)、message(结果消息)和 details(详细信息)。
     *
     * @param id 提供商 ID
     * @return 测试结果 Map
     * @throws BusinessException 当提供商不存在或无权测试时
     */
    public Mono<Map<String, Object>> testConnection(Long id) {
        String tenantId = TenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException(ResultCode.FORBIDDEN, "租户上下文未初始化"));
        log.info("测试 SSO 提供商连接: id={}, tenantId={}", id, tenantId);

        return ssoProviderRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "SSO 提供商不存在")))
                .flatMap(entity -> {
                    if (!tenantId.equals(entity.getTenantId())) {
                        return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "无权测试其他租户的 SSO 提供商"));
                    }

                    try {
                        Map<String, Object> config = parseConfig(entity.getConfig());
                        String protocolType = entity.getProviderType().name();
                        return ssoProtocolAdapterFactory
                                .getAdapter(protocolType)
                                .testConnection(config);
                    } catch (Exception e) {
                        log.error("解析 SSO 提供商配置失败: id={}", id, e);
                        Map<String, Object> errorResult = new HashMap<>(16);
                        errorResult.put("success", false);
                        errorResult.put("message", "配置解析失败: " + e.getMessage());
                        return Mono.just(errorResult);
                    }
                });
    }

    /**
     * 获取 SSO 提供商元数据
     *
     * <p>获取协议特定的元数据信息,用于配置验证或客户端集成。
     * 不同协议返回不同内容:SAML 返回 SP 元数据 XML,OIDC 返回 Discovery 端点信息,CAS 返回服务配置信息。
     *
     * @param id 提供商 ID
     * @return 元数据 Map
     * @throws BusinessException 当提供商不存在、无权获取或配置解析失败时
     */
    public Mono<Map<String, Object>> getMetadata(Long id) {
        String tenantId = TenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException(ResultCode.FORBIDDEN, "租户上下文未初始化"));
        log.info("获取 SSO 提供商元数据: id={}, tenantId={}", id, tenantId);

        return ssoProviderRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "SSO 提供商不存在")))
                .flatMap(entity -> {
                    if (!tenantId.equals(entity.getTenantId())) {
                        return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "无权获取其他租户的 SSO 提供商元数据"));
                    }

                    try {
                        Map<String, Object> config = parseConfig(entity.getConfig());
                        String protocolType = entity.getProviderType().name();
                        return ssoProtocolAdapterFactory
                                .getAdapter(protocolType)
                                .getMetadata(config);
                    } catch (Exception e) {
                        log.error("解析 SSO 提供商配置失败: id={}", id, e);
                        return Mono.error(new BusinessException(ResultCode.ERROR, "配置解析失败: " + e.getMessage()));
                    }
                });
    }

    /**
     * 构建 SSO 提供商配置
     *
     * <p>将 SSO 提供商 DTO 转换为协议适配器需要的配置 Map。
     * 用于 SSO 认证流程中传递配置给协议适配器。
     *
     * @param providerDTO SSO 提供商 DTO
     * @return 配置 Map
     * @throws BusinessException 当配置解析失败时
     */
    public Map<String, Object> buildProviderConfig(SsoProviderDTO providerDTO) {
        try {
            Map<String, Object> config = parseConfig(providerDTO.getConfigJson());
            config.put("providerId", providerDTO.getId());
            config.put("providerName", providerDTO.getProviderName());
            config.put("protocolType", providerDTO.getProtocolType());
            config.put("enabled", providerDTO.getEnabled());
            return config;
        } catch (Exception e) {
            log.error("构建 SSO 提供商配置失败: providerId={}", providerDTO.getId(), e);
            throw new BusinessException(ResultCode.ERROR, "构建 SSO 配置失败: " + e.getMessage());
        }
    }

    /**
     * 解析 JSON 配置字符串为 Map
     *
     * @param configJson JSON 配置字符串
     * @return 配置 Map
     * @throws BusinessException 当 JSON 格式错误时
     */
    private Map<String, Object> parseConfig(String configJson) {
        try {
            if (configJson == null || configJson.trim().isEmpty()) {
                return new HashMap<>(16);
            }
            return objectMapper.readValue(configJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("解析 JSON 配置失败: config={}", configJson, e);
            throw new BusinessException(ResultCode.PARAM_ERROR, "配置格式错误,请检查 JSON 格式");
        }
    }
}

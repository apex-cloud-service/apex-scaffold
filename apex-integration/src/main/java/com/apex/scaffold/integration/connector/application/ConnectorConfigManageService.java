package com.apex.scaffold.integration.connector.application;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.integration.connector.domain.ConnectorDefinition;
import com.apex.scaffold.integration.connector.domain.repository.ConnectorConfigRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 连接器配置管理用例（方案 M5 / 批次 E2，C7 标准管理接口）。
 *
 * <p>CRUD 编排：JSON 配置合法性校验 → 仓储读写（租户限定 + 乐观锁 + 逻辑删除）。
 * {@code configValue} 中的敏感值以凭据引用（{@code credentialRef}）表达，
 * 本服务不接触明文凭据。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectorConfigManageService {

    private final ConnectorConfigRepository configRepository;

    private final ObjectMapper objectMapper;

    /**
     * 分页查询租户连接器配置。
     *
     * @param tenantId      租户编码
     * @param connectorType 连接器类型（可空）
     * @param pageNum       页码
     * @param pageSize      页大小
     * @return 分页结果
     */
    public Mono<PageResponse<ConnectorDefinition>> page(
            String tenantId, String connectorType, int pageNum, int pageSize) {
        return configRepository.page(tenantId, connectorType, pageNum, pageSize);
    }

    /**
     * 按 ID 查询配置。
     *
     * @param tenantId 租户编码
     * @param id       主键
     * @return 配置；跨租户访问视为不存在
     */
    public Mono<ConnectorDefinition> get(String tenantId, Long id) {
        return configRepository
                .findById(tenantId, id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "连接器配置不存在")));
    }

    /**
     * 新增连接器配置。
     *
     * @param tenantId      租户编码
     * @param connectorType 连接器类型
     * @param configKey     配置标识（租户内唯一）
     * @param configValue   配置 JSON
     * @param remark        备注（可空）
     * @return 落库后的配置（status=1 启用）
     */
    public Mono<ConnectorDefinition> create(
            String tenantId, String connectorType, String configKey, String configValue, String remark) {
        if (connectorType == null || connectorType.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "连接器类型不能为空"));
        }
        if (configKey == null || configKey.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "配置标识不能为空"));
        }
        return validateJson(configValue)
                .map(value -> {
                    ConnectorDefinition definition = new ConnectorDefinition();
                    definition.setTenantId(tenantId);
                    definition.setConnectorType(connectorType.trim());
                    definition.setConfigKey(configKey.trim());
                    definition.setConfigValue(value);
                    definition.setStatus(1);
                    definition.setRemark(remark);
                    return definition;
                })
                .flatMap(configRepository::insert);
    }

    /**
     * 更新连接器配置（乐观锁）。
     *
     * @param tenantId    租户编码
     * @param id          主键
     * @param configValue 新配置 JSON（null 表示不修改）
     * @param remark      备注（null 表示不修改）
     * @param version     乐观锁版本
     * @return 更新后的配置
     */
    public Mono<ConnectorDefinition> update(
            String tenantId, Long id, String configValue, String remark, Integer version) {
        return configRepository
                .findById(tenantId, id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "连接器配置不存在")))
                .flatMap(existing -> (configValue == null
                                ? Mono.just(existing.getConfigValue())
                                : validateJson(configValue))
                        .map(value -> {
                            if (configValue != null) {
                                existing.setConfigValue(value);
                            }
                            if (remark != null) {
                                existing.setRemark(remark);
                            }
                            existing.setVersion(version);
                            return existing;
                        }))
                .flatMap(configRepository::update);
    }

    /**
     * 逻辑删除连接器配置。
     *
     * @param tenantId 租户编码
     * @param id       主键
     * @return 完成信号
     */
    public Mono<Void> delete(String tenantId, Long id) {
        return configRepository.delete(tenantId, id);
    }

    private Mono<String> validateJson(String configValue) {
        if (configValue == null || configValue.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "配置 JSON 不能为空"));
        }
        try {
            objectMapper.readValue(configValue, new TypeReference<Object>() {});
            return Mono.just(configValue);
        } catch (Exception e) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "配置 JSON 非法"));
        }
    }
}

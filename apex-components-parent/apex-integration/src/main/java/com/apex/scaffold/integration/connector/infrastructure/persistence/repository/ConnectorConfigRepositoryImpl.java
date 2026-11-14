package com.apex.scaffold.integration.connector.infrastructure.persistence.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.integration.connector.domain.ConnectorDefinition;
import com.apex.scaffold.integration.connector.domain.repository.ConnectorConfigRepository;
import com.apex.scaffold.integration.connector.infrastructure.persistence.mapper.ConnectorConfigMapper;
import com.apex.scaffold.integration.connector.infrastructure.persistence.po.ConnectorConfigPO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link ConnectorConfigRepository} 的 MyBatis-Plus 实现（方案 M5 / 批次 E2）。
 *
 * <p>全部查询显式携带 {@code tenant_id} 条件，叠加租户行级拦截器（开启时条件冗余但
 * 语义一致：上下文租户与显式租户不一致时结果为空，保持 fail-closed）。
 * 阻塞 MyBatis 访问统一调度到 jdbc 池。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Repository
@RequiredArgsConstructor
public class ConnectorConfigRepositoryImpl implements ConnectorConfigRepository {

    private final ConnectorConfigMapper mapper;

    @Override
    public Mono<ConnectorDefinition> findActive(String tenantId, String configKey) {
        return Mono.fromCallable(() -> mapper.selectOne(ConditionUtil.lambda(ConnectorConfigPO.class)
                        .eq(ConnectorConfigPO::getTenantId, tenantId)
                        .eq(ConnectorConfigPO::getConfigKey, configKey)
                        .eq(ConnectorConfigPO::getStatus, 1)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(this::toEntity);
    }

    @Override
    public Mono<ConnectorDefinition> findById(String tenantId, Long id) {
        return Mono.fromCallable(() -> mapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .filter(po -> po != null && tenantId != null && tenantId.equals(po.getTenantId()))
                .map(this::toEntity);
    }

    @Override
    public Mono<PageResponse<ConnectorDefinition>> page(
            String tenantId, String connectorType, int pageNum, int pageSize) {
        return Mono.fromCallable(() -> {
                    Page<ConnectorConfigPO> page = new Page<>(pageNum, pageSize);
                    mapper.selectPage(
                            page,
                            ConditionUtil.lambda(ConnectorConfigPO.class)
                                    .eqIfPresent(ConnectorConfigPO::getConnectorType, connectorType)
                                    .eq(ConnectorConfigPO::getTenantId, tenantId)
                                    .orderByDesc(ConnectorConfigPO::getCreateTime));
                    return PageResponse.of(
                            page.getRecords().stream().map(this::toEntity).toList(),
                            page.getTotal(),
                            pageNum,
                            pageSize);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<ConnectorDefinition> insert(ConnectorDefinition definition) {
        return Mono.fromCallable(() -> {
                    ConnectorConfigPO po = toPo(definition);
                    if (po.getStatus() == null) {
                        po.setStatus(1);
                    }
                    if (po.getVersion() == null) {
                        po.setVersion(0);
                    }
                    if (po.getDeleted() == null) {
                        po.setDeleted(0);
                    }
                    try {
                        mapper.insert(po);
                    } catch (DuplicateKeyException e) {
                        throw new BusinessException(ResultCode.BUSINESS_ERROR, "配置标识已存在: " + po.getConfigKey());
                    }
                    return po;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(this::toEntity);
    }

    @Override
    public Mono<ConnectorDefinition> update(ConnectorDefinition definition) {
        return Mono.fromCallable(() -> {
                    ConnectorConfigPO po = toPo(definition);
                    int rows = mapper.updateById(po);
                    if (rows == 0) {
                        throw new BusinessException(ResultCode.BUSINESS_ERROR, "配置不存在或版本冲突，请刷新后重试");
                    }
                    return mapper.selectById(po.getId());
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(this::toEntity);
    }

    @Override
    public Mono<Void> delete(String tenantId, Long id) {
        return Mono.fromCallable(() -> mapper.delete(ConditionUtil.lambda(ConnectorConfigPO.class)
                        .eq(ConnectorConfigPO::getTenantId, tenantId)
                        .eq(ConnectorConfigPO::getId, id)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(rows ->
                        rows > 0 ? Mono.empty() : Mono.error(new BusinessException(ResultCode.NOT_FOUND, "配置不存在")));
    }

    private ConnectorDefinition toEntity(ConnectorConfigPO po) {
        ConnectorDefinition definition = new ConnectorDefinition();
        definition.setId(po.getId());
        definition.setTenantId(po.getTenantId());
        definition.setConnectorType(po.getConnectorType());
        definition.setConfigKey(po.getConfigKey());
        definition.setConfigValue(po.getConfigValue());
        definition.setStatus(po.getStatus());
        definition.setRemark(po.getRemark());
        definition.setCreateTime(po.getCreateTime());
        definition.setUpdateTime(po.getUpdateTime());
        definition.setVersion(po.getVersion());
        definition.setDeleted(po.getDeleted());
        return definition;
    }

    private ConnectorConfigPO toPo(ConnectorDefinition definition) {
        ConnectorConfigPO po = new ConnectorConfigPO();
        po.setId(definition.getId());
        po.setTenantId(definition.getTenantId());
        po.setConnectorType(definition.getConnectorType());
        po.setConfigKey(definition.getConfigKey());
        po.setConfigValue(definition.getConfigValue());
        po.setStatus(definition.getStatus());
        po.setRemark(definition.getRemark());
        po.setCreateTime(definition.getCreateTime());
        po.setUpdateTime(definition.getUpdateTime());
        po.setVersion(definition.getVersion());
        po.setDeleted(definition.getDeleted());
        return po;
    }
}

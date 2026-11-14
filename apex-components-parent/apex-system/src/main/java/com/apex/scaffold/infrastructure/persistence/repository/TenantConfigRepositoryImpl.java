package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.TenantConfigEntity;
import com.apex.scaffold.domain.repository.TenantConfigRepository;
import com.apex.scaffold.infrastructure.persistence.mapper.TenantConfigMapper;
import com.apex.scaffold.infrastructure.persistence.po.TenantConfigPO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 租户级参数配置仓储实现（方案批次 B6）。
 *
 * <p>租户条件为<b>实现侧显式校验</b>：入参租户必须与可信上下文一致（防跨租户键注入），
 * 上下文缺失时 fail-closed（FORBIDDEN），与 {@code JdbcTenantScope} 语义一致；
 * 查询同时携带显式 {@code tenant_id} 条件，配额/守卫不依赖拦截器开关。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Repository
@RequiredArgsConstructor
public class TenantConfigRepositoryImpl implements TenantConfigRepository {

    private final TenantConfigMapper mapper;

    /** 校验入参租户与可信上下文一致，返回上下文租户。 */
    private String requireTrustedTenant(String requested) {
        String tenant = TenantContext.currentTenantId().orElse(null);
        if (tenant == null || tenant.isBlank()) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        if (requested != null && !requested.isBlank() && !requested.equals(tenant)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return tenant;
    }

    @Override
    public Mono<TenantConfigEntity> findByTenantAndKey(String tenantId, String configKey) {
        return Mono.fromCallable(() -> {
                    String tenant = requireTrustedTenant(tenantId);
                    return converter(mapper.selectOne(new QueryWrapper<TenantConfigPO>()
                            .eq("tenant_id", tenant)
                            .eq("config_key", configKey)
                            .eq("deleted", 0)));
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Flux<TenantConfigEntity> findAllByTenant(String tenantId) {
        return Mono.fromCallable(() -> {
                    String tenant = requireTrustedTenant(tenantId);
                    return mapper.selectList(new QueryWrapper<TenantConfigPO>()
                            .eq("tenant_id", tenant)
                            .eq("deleted", 0)
                            .orderByAsc("config_key"));
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable)
                .map(this::converter);
    }

    @Override
    public Mono<TenantConfigEntity> save(TenantConfigEntity entity) {
        return Mono.fromCallable(() -> {
                    String tenant = requireTrustedTenant(entity.getTenantId());
                    TenantConfigPO po = toPo(entity, tenant);
                    if (po.getId() == null) {
                        TenantConfigPO existing = mapper.selectOne(new QueryWrapper<TenantConfigPO>()
                                .eq("tenant_id", tenant)
                                .eq("config_key", po.getConfigKey())
                                .eq("deleted", 0));
                        if (existing != null) {
                            po.setId(existing.getId());
                            po.setVersion(existing.getVersion());
                            po.setUpdateTime(LocalDateTime.now());
                            mapper.updateById(po);
                        } else {
                            po.setCreateTime(LocalDateTime.now());
                            po.setUpdateTime(LocalDateTime.now());
                            mapper.insert(po);
                        }
                    } else {
                        po.setUpdateTime(LocalDateTime.now());
                        mapper.updateById(po);
                    }
                    return converter(mapper.selectById(po.getId()));
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Boolean> deleteByTenantAndKey(String tenantId, String configKey) {
        return Mono.fromCallable(() -> {
                    String tenant = requireTrustedTenant(tenantId);
                    return mapper.update(
                                    null,
                                    new UpdateWrapper<TenantConfigPO>()
                                            .eq("tenant_id", tenant)
                                            .eq("config_key", configKey)
                                            .eq("deleted", 0)
                                            .set("deleted", 1)
                                            .set("update_time", LocalDateTime.now()))
                            > 0;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    private TenantConfigPO toPo(TenantConfigEntity entity, String tenant) {
        TenantConfigPO po = new TenantConfigPO();
        po.setId(entity.getId());
        po.setTenantId(tenant);
        po.setConfigKey(entity.getConfigKey());
        po.setConfigValue(entity.getConfigValue());
        po.setRemark(entity.getRemark());
        po.setVersion(entity.getVersion() == null ? 0 : entity.getVersion());
        po.setDeleted(entity.getDeleted() == null ? 0 : entity.getDeleted());
        return po;
    }

    private TenantConfigEntity converter(TenantConfigPO po) {
        if (po == null) {
            return null;
        }
        return TenantConfigEntity.builder()
                .id(po.getId())
                .tenantId(po.getTenantId())
                .configKey(po.getConfigKey())
                .configValue(po.getConfigValue())
                .remark(po.getRemark())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .version(po.getVersion())
                .deleted(po.getDeleted())
                .build();
    }
}

package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.common.util.JsonUtil;
import com.apex.scaffold.domain.model.entity.TenantPackageEntity;
import com.apex.scaffold.domain.repository.TenantPackageRepository;
import com.apex.scaffold.infrastructure.persistence.mapper.TenantPackageMapper;
import com.apex.scaffold.infrastructure.persistence.po.TenantPackagePO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 租户套餐仓储实现（平台级定义表，方案批次 B3）。
 *
 * <p>sys_tenant_package 在租户拦截器白名单中（平台级定义，先于租户上下文查询），
 * 读写均为平台维度，与 {@code JdbcTenantScope} 业务路径分离。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class TenantPackageRepositoryImpl implements TenantPackageRepository {

    private final TenantPackageMapper mapper;

    @Override
    public Mono<TenantPackageEntity> findByCode(String packageCode) {
        return Mono.fromCallable(() -> converter(mapper.selectOne(new QueryWrapper<TenantPackagePO>()
                        .eq("package_code", packageCode)
                        .eq("deleted", 0))))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Flux<TenantPackageEntity> findAll() {
        return Mono.fromCallable(() -> mapper.selectList(
                        new QueryWrapper<TenantPackagePO>().eq("deleted", 0).orderByAsc("id")))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable)
                .map(this::converter);
    }

    @Override
    public Mono<TenantPackageEntity> save(TenantPackageEntity entity) {
        return Mono.fromCallable(() -> {
                    TenantPackagePO po = toPo(entity);
                    if (entity.getId() == null) {
                        po.setCreateTime(LocalDateTime.now());
                        po.setUpdateTime(LocalDateTime.now());
                        mapper.insert(po);
                    } else {
                        po.setUpdateTime(LocalDateTime.now());
                        mapper.updateById(po);
                    }
                    return converter(po);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /** 实体 → PO（集合序列化为 JSON 数组文本）。 */
    private TenantPackagePO toPo(TenantPackageEntity entity) {
        TenantPackagePO po = new TenantPackagePO();
        po.setId(entity.getId());
        po.setPackageCode(entity.getPackageCode());
        po.setPackageName(entity.getPackageName());
        po.setMenuIds(JsonUtil.toJson(entity.getMenuIds() == null ? List.of() : entity.getMenuIds()));
        po.setPermissionCodes(
                JsonUtil.toJson(entity.getPermissionCodes() == null ? List.of() : entity.getPermissionCodes()));
        po.setFeatures(JsonUtil.toJson(entity.getFeatures() == null ? List.of() : entity.getFeatures()));
        po.setStatus(entity.getStatus());
        po.setRemark(entity.getRemark());
        po.setVersion(entity.getVersion() == null ? 0 : entity.getVersion());
        po.setDeleted(entity.getDeleted() == null ? 0 : entity.getDeleted());
        return po;
    }

    /** PO → 实体（JSON 数组文本反序列化；解析失败按空集合兜底并告警）。 */
    private TenantPackageEntity converter(TenantPackagePO po) {
        if (po == null) {
            return null;
        }
        return TenantPackageEntity.builder()
                .id(po.getId())
                .packageCode(po.getPackageCode())
                .packageName(po.getPackageName())
                .menuIds(new HashSet<>(parseLongs(po.getMenuIds())))
                .permissionCodes(new HashSet<>(parseStrings(po.getPermissionCodes())))
                .features(new HashSet<>(parseStrings(po.getFeatures())))
                .status(po.getStatus())
                .remark(po.getRemark())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .version(po.getVersion())
                .deleted(po.getDeleted())
                .build();
    }

    private List<Long> parseLongs(String json) {
        try {
            return JsonUtil.fromJson(json, new com.fasterxml.jackson.core.type.TypeReference<List<Long>>() {});
        } catch (RuntimeException e) {
            log.warn("套餐 menu_ids 解析失败，按全量兜底: reason={}", e.getMessage());
            return List.of();
        }
    }

    private List<String> parseStrings(String json) {
        try {
            return JsonUtil.fromJson(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (RuntimeException e) {
            log.warn("套餐 permission_codes/features 解析失败，按全量兜底: reason={}", e.getMessage());
            return List.of();
        }
    }
}

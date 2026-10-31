package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.TenantPackageEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 租户套餐仓储端口（平台级定义表，方案批次 B3）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public interface TenantPackageRepository {

    /**
     * 按套餐编码查询有效套餐。
     *
     * @param packageCode 套餐编码
     * @return 套餐实体；不存在时为空
     */
    Mono<TenantPackageEntity> findByCode(String packageCode);

    /**
     * 查询全部有效套餐。
     *
     * @return 套餐列表
     */
    Flux<TenantPackageEntity> findAll();

    /**
     * 保存套餐（id 为空插入，否则更新）。
     *
     * @param entity 套餐实体
     * @return 已保存实体
     */
    Mono<TenantPackageEntity> save(TenantPackageEntity entity);
}

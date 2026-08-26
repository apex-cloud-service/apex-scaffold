package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.SubscriptionEntity;
import com.apex.scaffold.infrastructure.persistence.po.SubscriptionPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@link SubscriptionEntity} 与 {@link SubscriptionPO} 之间的 MapStruct 双向转换器。
 *
 * <p>使用 MapStruct（componentModel = "spring"）完成领域实体与持久化对象
 * 之间的双向映射，忽略未匹配目标属性，供 Repository 实现层使用。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SubscriptionPoConverter {

    /**
     * 将订阅实体转换为PO
     *
     * @param entity 订阅实体
     * @return 订阅PO
     */
    SubscriptionPO toPo(SubscriptionEntity entity);

    /**
     * 将订阅PO转换为实体
     *
     * @param po 订阅PO
     * @return 订阅实体
     */
    SubscriptionEntity toEntity(SubscriptionPO po);

    /**
     * 将订阅实体列表转换为PO列表
     *
     * @param entities 订阅实体列表
     * @return 订阅PO列表
     */
    List<SubscriptionPO> toPoList(List<SubscriptionEntity> entities);

    /**
     * 将订阅PO列表转换为实体列表
     *
     * @param pos 订阅PO列表
     * @return 订阅实体列表
     */
    List<SubscriptionEntity> toEntityList(List<SubscriptionPO> pos);
}

package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.PolicyVersionEntity;
import com.apex.scaffold.infrastructure.persistence.po.PolicyVersionPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * {@link PolicyVersionEntity} 与 {@link PolicyVersionPO} 之间的 MapStruct 双向转换器。
 *
 * <p>使用 MapStruct（componentModel = "spring"）完成领域实体与持久化对象
 * 之间的双向映射，忽略未匹配目标属性，供 Repository 实现层使用。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PolicyVersionPoConverter {

    /**
     * 将政策版本实体转换为PO
     *
     * @param entity 政策版本实体
     * @return 政策版本PO
     */
    @Mapping(source = "version", target = "policyVersion")
    PolicyVersionPO toPo(PolicyVersionEntity entity);

    /**
     * 将政策版本PO转换为实体
     *
     * @param po 政策版本PO
     * @return 政策版本实体
     */
    @Mapping(source = "policyVersion", target = "version")
    PolicyVersionEntity toEntity(PolicyVersionPO po);

    /**
     * 将政策版本实体列表转换为PO列表
     *
     * @param entities 政策版本实体列表
     * @return 政策版本PO列表
     */
    List<PolicyVersionPO> toPoList(List<PolicyVersionEntity> entities);

    /**
     * 将政策版本PO列表转换为实体列表
     *
     * @param pos 政策版本PO列表
     * @return 政策版本实体列表
     */
    List<PolicyVersionEntity> toEntityList(List<PolicyVersionPO> pos);
}

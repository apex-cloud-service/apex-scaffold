package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.SampleEntity;
import com.apex.scaffold.infrastructure.persistence.po.SamplePO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * {@link SampleEntity} 与 {@link SamplePO} 之间的 MapStruct 双向转换器。
 *
 * <p>使用 MapStruct（componentModel = "spring"）完成领域实体与持久化对象
 * 之间的双向映射，忽略未匹配目标属性，供 Repository 实现层使用。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SamplePoConverter {

    /**
     * 将样本实体转换为PO
     *
     * @param entity 样本实体
     * @return 样本PO
     */
    SamplePO toPo(SampleEntity entity);

    /**
     * 将样本PO转换为实体
     *
     * @param po 样本PO
     * @return 样本实体
     */
    @Mapping(target = "faceTypeValue", ignore = true)
    SampleEntity toEntity(SamplePO po);

    /**
     * 将样本实体列表转换为PO列表
     *
     * @param entities 样本实体列表
     * @return 样本PO列表
     */
    List<SamplePO> toPoList(List<SampleEntity> entities);

    /**
     * 将样本PO列表转换为实体列表
     *
     * @param pos 样本PO列表
     * @return 样本实体列表
     */
    List<SampleEntity> toEntityList(List<SamplePO> pos);
}

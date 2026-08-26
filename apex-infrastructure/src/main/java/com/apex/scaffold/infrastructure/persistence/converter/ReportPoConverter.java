package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.ReportEntity;
import com.apex.scaffold.infrastructure.persistence.po.ReportPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@link ReportEntity} 与 {@link ReportPO} 之间的 MapStruct 双向转换器。
 *
 * <p>使用 MapStruct（componentModel = "spring"）完成领域实体与持久化对象
 * 之间的双向映射，忽略未匹配目标属性，供 Repository 实现层使用。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ReportPoConverter {

    /**
     * 将举报实体转换为PO
     *
     * @param entity 举报实体
     * @return 举报PO
     */
    ReportPO toPo(ReportEntity entity);

    /**
     * 将举报PO转换为实体
     *
     * @param po 举报PO
     * @return 举报实体
     */
    ReportEntity toEntity(ReportPO po);

    /**
     * 将举报实体列表转换为PO列表
     *
     * @param entities 举报实体列表
     * @return 举报PO列表
     */
    List<ReportPO> toPoList(List<ReportEntity> entities);

    /**
     * 将举报PO列表转换为实体列表
     *
     * @param pos 举报PO列表
     * @return 举报实体列表
     */
    List<ReportEntity> toEntityList(List<ReportPO> pos);
}

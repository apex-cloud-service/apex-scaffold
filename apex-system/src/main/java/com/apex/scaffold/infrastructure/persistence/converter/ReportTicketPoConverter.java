package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.ReportTicketEntity;
import com.apex.scaffold.infrastructure.persistence.po.ReportTicketPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@link ReportTicketEntity} 与 {@link ReportTicketPO} 之间的 MapStruct 双向转换器。
 *
 * <p>使用 MapStruct（componentModel = "spring"）完成领域实体与持久化对象
 * 之间的双向映射，忽略未匹配目标属性，供 Repository 实现层使用。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ReportTicketPoConverter {

    /**
     * 将举报工单实体转换为PO
     *
     * @param entity 举报工单实体
     * @return 举报工单PO
     */
    ReportTicketPO toPo(ReportTicketEntity entity);

    /**
     * 将举报工单PO转换为实体
     *
     * @param po 举报工单PO
     * @return 举报工单实体
     */
    ReportTicketEntity toEntity(ReportTicketPO po);

    /**
     * 将举报工单实体列表转换为PO列表
     *
     * @param entities 举报工单实体列表
     * @return 举报工单PO列表
     */
    List<ReportTicketPO> toPoList(List<ReportTicketEntity> entities);

    /**
     * 将举报工单PO列表转换为实体列表
     *
     * @param pos 举报工单PO列表
     * @return 举报工单实体列表
     */
    List<ReportTicketEntity> toEntityList(List<ReportTicketPO> pos);
}

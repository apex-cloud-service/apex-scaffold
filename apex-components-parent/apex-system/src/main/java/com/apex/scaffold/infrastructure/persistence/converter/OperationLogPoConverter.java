package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.OperationLogEntity;
import com.apex.scaffold.infrastructure.persistence.po.OperationLogPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@link OperationLogEntity} 与 {@link OperationLogPO} 之间的 MapStruct 双向转换器。
 *
 * <p>使用 MapStruct（componentModel = "spring"）完成领域实体与持久化对象
 * 之间的双向映射，忽略未匹配目标属性，供 Repository 实现层使用。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OperationLogPoConverter {

    /**
     * 将操作日志实体转换为PO
     *
     * @param entity 操作日志实体
     * @return 操作日志PO
     */
    OperationLogPO toPo(OperationLogEntity entity);

    /**
     * 将操作日志PO转换为实体
     *
     * @param po 操作日志PO
     * @return 操作日志实体
     */
    OperationLogEntity toEntity(OperationLogPO po);

    /**
     * 将操作日志实体列表转换为PO列表
     *
     * @param entities 操作日志实体列表
     * @return 操作日志PO列表
     */
    List<OperationLogPO> toPoList(List<OperationLogEntity> entities);

    /**
     * 将操作日志PO列表转换为实体列表
     *
     * @param pos 操作日志PO列表
     * @return 操作日志实体列表
     */
    List<OperationLogEntity> toEntityList(List<OperationLogPO> pos);
}

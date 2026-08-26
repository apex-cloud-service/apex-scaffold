package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.SystemMessageEntity;
import com.apex.scaffold.infrastructure.persistence.po.SystemMessagePO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@link SystemMessageEntity} 与 {@link SystemMessagePO} 之间的 MapStruct 双向转换器。
 *
 * <p>使用 MapStruct（componentModel = "spring"）完成领域实体与持久化对象
 * 之间的双向映射，忽略未匹配目标属性，供 Repository 实现层使用。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SystemMessagePoConverter {
    /**
     * 将系统消息实体转换为PO
     *
     * @param entity 系统消息实体
     * @return 系统消息PO
     */
    SystemMessagePO toPo(SystemMessageEntity entity);

    /**
     * 将系统消息PO转换为实体
     *
     * @param po 系统消息PO
     * @return 系统消息实体
     */
    SystemMessageEntity toEntity(SystemMessagePO po);

    /**
     * 将系统消息PO列表转换为实体列表
     *
     * @param pos 系统消息PO列表
     * @return 系统消息实体列表
     */
    List<SystemMessageEntity> toEntityList(List<SystemMessagePO> pos);
}

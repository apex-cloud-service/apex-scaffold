package com.apex.scaffold.office.off.infrastructure.persistence.converter;

import com.apex.scaffold.office.off.domain.entity.OffMessageEntity;
import com.apex.scaffold.office.off.infrastructure.persistence.po.OffMessagePO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 站内消息实体与持久化对象转换器
 *
 * @author Apex Scaffold
 * @since 1.2.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OffMessagePoConverter {

    /**
     * 对象转换方法
     *
     * @param entity 领域实体
     * @return 目标对象
     */
    OffMessagePO toPo(OffMessageEntity entity);

    /**
     * 对象转换方法
     *
     * @param po 持久化对象
     * @return 目标对象
     */
    OffMessageEntity toEntity(OffMessagePO po);

    /**
     * 将站内消息PO列表转换为实体列表
     *
     * @param pos 站内消息PO列表
     * @return 站内消息实体列表
     */
    List<OffMessageEntity> toEntityList(List<OffMessagePO> pos);
}

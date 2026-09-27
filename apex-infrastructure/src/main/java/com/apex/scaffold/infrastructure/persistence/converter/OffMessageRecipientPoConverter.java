package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.OffMessageRecipientEntity;
import com.apex.scaffold.infrastructure.persistence.po.OffMessageRecipientPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 消息接收者实体与持久化对象转换器
 *
 * @author Apex Scaffold
 * @since 1.2.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OffMessageRecipientPoConverter {

    /**
     * 对象转换方法
     *
     * @param entity 领域实体
     * @return 目标对象
     */
    OffMessageRecipientPO toPo(OffMessageRecipientEntity entity);

    /**
     * 对象转换方法
     *
     * @param po 持久化对象
     * @return 目标对象
     */
    OffMessageRecipientEntity toEntity(OffMessageRecipientPO po);

    /**
     * 将消息接收者PO列表转换为实体列表
     *
     * @param pos 消息接收者PO列表
     * @return 消息接收者实体列表
     */
    List<OffMessageRecipientEntity> toEntityList(List<OffMessageRecipientPO> pos);
}

package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.OffMessageTemplateEntity;
import com.apex.scaffold.infrastructure.persistence.po.OffMessageTemplatePO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 消息模板实体与持久化对象转换器
 *
 * @author Apex Scaffold
 * @since 1.2.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OffMessageTemplatePoConverter {

    /**
     * 对象转换方法
     *
     * @param entity 领域实体
     * @return 目标对象
     */
    OffMessageTemplatePO toPo(OffMessageTemplateEntity entity);

    /**
     * 对象转换方法
     *
     * @param po 持久化对象
     * @return 目标对象
     */
    OffMessageTemplateEntity toEntity(OffMessageTemplatePO po);

    /**
     * 将消息模板PO列表转换为实体列表
     *
     * @param pos 消息模板PO列表
     * @return 消息模板实体列表
     */
    List<OffMessageTemplateEntity> toEntityList(List<OffMessageTemplatePO> pos);
}

package com.apex.scaffold.office.notify.infrastructure.persistence.converter;

import com.apex.scaffold.office.notify.domain.NotifyTemplate;
import com.apex.scaffold.office.notify.infrastructure.persistence.po.NotifyTemplatePO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 统一通知模板实体与持久化对象转换器。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface NotifyTemplatePoConverter {

    /**
     * 对象转换方法。
     *
     * @param entity 领域实体
     * @return 目标对象
     */
    NotifyTemplatePO toPo(NotifyTemplate entity);

    /**
     * 对象转换方法。
     *
     * @param po 持久化对象
     * @return 目标对象
     */
    NotifyTemplate toEntity(NotifyTemplatePO po);
}

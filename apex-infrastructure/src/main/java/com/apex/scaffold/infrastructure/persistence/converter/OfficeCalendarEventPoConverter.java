package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.OfficeCalendarEventEntity;
import com.apex.scaffold.infrastructure.persistence.po.OfficeCalendarEventPO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 日历事件实体与持久化对象转换器
 *
 * @author Apex Scaffold
 * @since 1.2.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OfficeCalendarEventPoConverter {

    /**
     * 对象转换方法
     *
     * @param po 持久化对象
     * @return 目标对象
     */
    OfficeCalendarEventEntity toDomain(OfficeCalendarEventPO po);

    /**
     * 对象转换方法
     *
     * @param entity 领域实体
     * @return 目标对象
     */
    OfficeCalendarEventPO toPo(OfficeCalendarEventEntity entity);
}

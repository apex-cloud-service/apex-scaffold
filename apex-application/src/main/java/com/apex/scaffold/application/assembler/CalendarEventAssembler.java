package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.CalendarEventDTO;
import com.apex.scaffold.domain.model.entity.OfficeCalendarEventEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 日历事件装配器。
 *
 * <p>基于 MapStruct 实现 {@code OfficeCalendarEventEntity} 与 {@code CalendarEventDTO}
 * 之间的双向转换，供 {@code OfficeIntegrationService} 在日历事件管理用例编排中完成对象映射。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        implementationName = "CalendarEventDtoAssemblerImpl")
public interface CalendarEventAssembler {

    /**
     *
     *
     * 将日历事件实体转换为DTO。
     *
     *
     *
     *
     *
     * @param entity 日历事件实体
     *
     *
     * @return 日历事件DTO
     *
     *
     */
    CalendarEventDTO toDto(OfficeCalendarEventEntity entity);

    /**
     *
     *
     * 将日历事件DTO转换为实体。
     *
     *
     *
     *
     *
     * @param dto 日历事件DTO
     *
     *
     * @return 日历事件实体
     *
     *
     */
    OfficeCalendarEventEntity toEntity(CalendarEventDTO dto);
}

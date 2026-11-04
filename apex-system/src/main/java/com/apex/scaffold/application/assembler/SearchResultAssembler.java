package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.search.SearchResultDTO;
import com.apex.scaffold.domain.search.SearchResultEntity;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * 搜索结果转换器。
 *
 * @author Apex Team
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SearchResultAssembler {

    DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 将搜索结果实体转换为 DTO。
     *
     * @param entity 搜索结果实体
     * @return 搜索结果 DTO
     */
    @Mapping(target = "createdAt", expression = "java(formatDateTime(entity.getCreatedAt()))")
    @Mapping(target = "updatedAt", expression = "java(formatDateTime(entity.getUpdatedAt()))")
    SearchResultDTO toDTO(SearchResultEntity entity);

    /**
     * 格式化日期时间。
     *
     * @param dateTime 日期时间
     * @return 格式化后的日期时间字符串
     */
    default String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(FORMATTER) : null;
    }
}

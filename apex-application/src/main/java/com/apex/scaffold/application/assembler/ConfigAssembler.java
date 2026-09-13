package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.admin.ConfigDTO;
import com.apex.scaffold.domain.model.entity.ConfigEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 系统配置对象转换器
 *
 * <p>用于在系统配置相关的 DTO 与领域实体之间进行转换。
 * 基于 MapStruct 生成实现类。
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ConfigAssembler {
    /**
     * 将 ConfigEntity 转换为 ConfigDTO
     *
     * @param entity 配置实体
     * @return 配置 DTO
     */
    ConfigDTO toDTO(ConfigEntity entity);

    /**
     * 将 ConfigEntity 列表转换为 ConfigDTO 列表
     *
     * @param entities 配置实体列表
     * @return 配置 DTO 列表
     */
    List<ConfigDTO> toDTOList(List<ConfigEntity> entities);
}

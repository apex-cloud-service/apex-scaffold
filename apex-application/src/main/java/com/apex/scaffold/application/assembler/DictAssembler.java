package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.admin.DictDataDTO;
import com.apex.scaffold.application.dto.admin.DictDataRequest;
import com.apex.scaffold.application.dto.admin.DictTypeDTO;
import com.apex.scaffold.application.dto.admin.DictTypeRequest;
import com.apex.scaffold.domain.model.entity.DictDataEntity;
import com.apex.scaffold.domain.model.entity.DictTypeEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 字典对象转换器
 *
 * <p>用于在字典相关的 DTO/Request 与领域实体之间进行转换。
 * 基于 MapStruct 生成实现类。
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DictAssembler {
    /**
     * 将 DictTypeEntity 转换为 DictTypeDTO
     *
     * @param entity 字典类型实体
     * @return 字典类型 DTO
     */
    DictTypeDTO toTypeDTO(DictTypeEntity entity);

    /**
     * 将 DictTypeRequest 转换为 DictTypeEntity
     *
     * @param request 字典类型请求
     * @return 字典类型实体
     */
    DictTypeEntity fromTypeRequest(DictTypeRequest request);

    /**
     * 将 DictTypeEntity 列表转换为 DictTypeDTO 列表
     *
     * @param entities 字典类型实体列表
     * @return 字典类型 DTO 列表
     */
    List<DictTypeDTO> toTypeDTOList(List<DictTypeEntity> entities);

    /**
     * 将 DictDataEntity 转换为 DictDataDTO
     *
     * @param entity 字典数据实体
     * @return 字典数据 DTO
     */
    DictDataDTO toDataDTO(DictDataEntity entity);

    /**
     * 将 DictDataRequest 转换为 DictDataEntity
     *
     * @param request 字典数据请求
     * @return 字典数据实体
     */
    DictDataEntity fromDataRequest(DictDataRequest request);

    /**
     * 将 DictDataEntity 列表转换为 DictDataDTO 列表
     *
     * @param entities 字典数据实体列表
     * @return 字典数据 DTO 列表
     */
    List<DictDataDTO> toDataDTOList(List<DictDataEntity> entities);
}

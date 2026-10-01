package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.admin.SsoSessionDTO;
import com.apex.scaffold.domain.model.entity.SysSsoSessionEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * SSO 会话装配器。
 *
 * <p>基于 MapStruct 实现 {@code SysSsoSessionEntity} 到 {@code SsoSessionDTO}
 * 的单向转换（含列表批量转换），供 {@code SysSsoSessionService}
 * 在 SSO 会话查询用例中完成对象映射。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SysSsoSessionAssembler {
    /**
     *
     * 将SSO会话实体转换为DTO。
     *
     *
     *
     * @param entity SSO会话实体
     *
     * @return SSO会话DTO
     *
     */
    SsoSessionDTO toDTO(SysSsoSessionEntity entity);

    /**
     *
     *
     * 将SSO会话实体列表转换为DTO列表。
     *
     *
     *
     *
     *
     * @param entities SSO会话实体列表
     *
     *
     * @return SSO会话DTO列表
     *
     *
     */
    List<SsoSessionDTO> toDTOList(List<SysSsoSessionEntity> entities);
}

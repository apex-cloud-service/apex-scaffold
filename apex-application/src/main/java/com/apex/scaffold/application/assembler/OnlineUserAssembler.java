package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.admin.OnlineUserDTO;
import com.apex.scaffold.domain.model.entity.UserEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 在线用户对象转换器
 *
 * <p>用于将领域实体转换为在线用户 DTO。
 * 基于 MapStruct 生成实现类。
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OnlineUserAssembler {
    /**
     * 将 UserEntity 转换为 OnlineUserDTO
     *
     * @param entity 用户实体
     * @return 在线用户 DTO
     */
    OnlineUserDTO toDTO(UserEntity entity);

    /**
     * 将 UserEntity 列表转换为 OnlineUserDTO 列表
     *
     * @param entities 用户实体列表
     * @return 在线用户 DTO 列表
     */
    List<OnlineUserDTO> toDTOList(List<UserEntity> entities);
}

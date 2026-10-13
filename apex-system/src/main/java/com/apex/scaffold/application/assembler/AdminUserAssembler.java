package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.admin.AdminRoleResponse;
import com.apex.scaffold.application.dto.admin.AdminUserProfileResponse;
import com.apex.scaffold.domain.model.entity.RoleEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * 管理后台用户对象转换器
 *
 * <p>用于将领域实体转换为管理后台专用的响应对象。
 * 基于 MapStruct 生成实现类。
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AdminUserAssembler {

    /**
     * 将 UserProfileEntity 转换为 AdminUserProfileResponse
     *
     * @param entity 用户画像实体
     * @return 管理后台用户档案响应
     */
    @Mapping(target = "socialUser", expression = "java(entity.getSocialUser() != null && entity.getSocialUser() == 1)")
    AdminUserProfileResponse toProfileResponse(com.apex.scaffold.domain.model.entity.UserProfileEntity entity);

    /**
     * 将 RoleEntity 转换为 AdminRoleResponse
     *
     * @param entity 角色实体
     * @return 管理后台角色响应
     */
    AdminRoleResponse toRoleResponse(RoleEntity entity);

    /**
     * 将 RoleEntity 列表转换为 AdminRoleResponse 列表
     *
     * @param entities 角色实体列表
     * @return 管理后台角色响应列表
     */
    List<AdminRoleResponse> toRoleResponseList(List<RoleEntity> entities);
}

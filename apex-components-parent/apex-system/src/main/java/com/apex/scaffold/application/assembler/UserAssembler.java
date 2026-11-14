package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.LoginResponse;
import com.apex.scaffold.application.dto.UserDTO;
import com.apex.scaffold.domain.model.entity.UserEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * 用户对象转换器
 *
 * <p>
 * 使用 MapStruct 自动生成 DTO 与 Entity 之间的转换逻辑。
 * 提供实体转换、部分更新合并等功能。
 * </p>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>Entity → DTO 转换</li>
 *   <li>DTO → Entity 转换</li>
 *   <li>部分更新合并（仅更新非空字段）</li>
 *   <li>列表批量转换</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>装配器模式：封装对象转换逻辑</li>
 *   <li>部分更新模式：使用 {@code @MappingTarget} 实现字段合并</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserAssembler {

    /**
     * 将 UserEntity 转换为 UserDTO
     *
     * <p>
     * 将领域实体转换为应用层DTO，用于API响应。
     * </p>
     *
     * @param user 用户实体
     * @return 用户 DTO
     */
    UserDTO toDTO(UserEntity user);

    /**
     * 将 UserEntity 列表转换为 UserDTO 列表
     *
     * <p>
     * 批量转换实体列表为DTO列表。
     * </p>
     *
     * @param users 用户实体列表
     * @return 用户 DTO 列表
     */
    List<UserDTO> toDTOList(List<UserEntity> users);

    /**
     * 将 UserEntity 转换为 LoginResponse.UserInfo
     *
     * <p>
     * 用于登录响应中的用户信息部分。
     * </p>
     *
     * @param user 用户实体
     * @return 用户信息 DTO
     */
    LoginResponse.UserInfo toUserInfo(UserEntity user);

    /**
     * 将 UserEntity 转换为 LoginResponse
     *
     * <p>
     * 将用户实体转换为登录响应对象，忽略Token相关字段。
     * </p>
     *
     * @param user 用户实体
     * @return 登录响应 DTO
     */
    @Mapping(target = "userInfo", source = "user")
    @Mapping(target = "accessToken", ignore = true)
    @Mapping(target = "refreshToken", ignore = true)
    @Mapping(target = "tokenType", ignore = true)
    @Mapping(target = "expiresIn", ignore = true)
    @Mapping(target = "isNewUser", ignore = true)
    @Mapping(target = "openId", source = "openId")
    @Mapping(target = "unionId", source = "unionId")
    LoginResponse toLoginResponse(UserEntity user);

    /**
     * 部分更新合并：将源实体的非空字段合并到目标实体
     *
     * <p>
     * 使用 MapStruct 的 {@code @MappingTarget} 注解实现部分更新。
     * 仅更新源实体中非 null 的字段，保留目标实体中已有值。
     * </p>
     *
     * <p><strong>合并规则</strong></p>
     * <ul>
     *   <li>源字段为 null 时，不更新目标字段（保留原值）</li>
     *   <li>源字段为空字符串时，不更新目标字段（保留原值）</li>
     *   <li>源字段非空时，覆盖目标字段</li>
     *   <li>createTime、id 等字段不参与合并</li>
     * </ul>
     *
     * <p><strong>业务约束</strong></p>
     * <p>
     * 注意：性别字段的合并需要额外校验（性别不可修改），
     * 该校验逻辑在 {@link com.apex.scaffold.application.service.UserService#mergeFields} 中实现。
     * openId / unionId 允许更新以支持手机号注册后绑定微信的场景，
     * 当 source 中这些字段为 null 时（受 NullValuePropertyMappingStrategy.IGNORE 保护）不覆盖。
     * </p>
     *
     * @param target 目标实体（数据库中已有）
     * @param source 源实体（待更新的数据）
     * @return 合并后的实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "accessToken", ignore = true)
    @Mapping(target = "refreshToken", ignore = true)
    @Mapping(target = "tokenExpireTime", ignore = true)
    @org.mapstruct.BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    UserEntity mergeUpdate(@MappingTarget UserEntity target, UserEntity source);
}

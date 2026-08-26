package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.UserProfileEntity;
import com.apex.scaffold.infrastructure.persistence.po.UserProfilePO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@link UserProfileEntity} 与 {@link UserProfilePO} 之间的 MapStruct 双向转换器。
 *
 * <p>使用 MapStruct（componentModel = "spring"）完成领域实体与持久化对象
 * 之间的双向映射，忽略未匹配目标属性，供 Repository 实现层使用。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserProfilePoConverter {

    /**
     * 将用户档案实体转换为PO
     *
     * @param entity 用户档案实体
     * @return 用户档案PO
     */
    UserProfilePO toPo(UserProfileEntity entity);

    /**
     * 将用户档案PO转换为实体
     *
     * @param po 用户档案PO
     * @return 用户档案实体
     */
    UserProfileEntity toEntity(UserProfilePO po);

    /**
     * 将用户档案实体列表转换为PO列表
     *
     * @param entities 用户档案实体列表
     * @return 用户档案PO列表
     */
    List<UserProfilePO> toPoList(List<UserProfileEntity> entities);

    /**
     * 将用户档案PO列表转换为实体列表
     *
     * @param pos 用户档案PO列表
     * @return 用户档案实体列表
     */
    List<UserProfileEntity> toEntityList(List<UserProfilePO> pos);
}

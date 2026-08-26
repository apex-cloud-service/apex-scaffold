package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.infrastructure.persistence.po.UserPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@link UserEntity} 与 {@link UserPO} 之间的 MapStruct 双向转换器。
 *
 * <p>使用 MapStruct（componentModel = "spring"）完成领域实体与持久化对象
 * 之间的双向映射，忽略未匹配目标属性，供 Repository 实现层使用。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserPoConverter {

    /**
     * 将用户实体转换为PO
     *
     * @param entity 用户实体
     * @return 用户PO
     */
    UserPO toPo(UserEntity entity);

    /**
     * 将用户PO转换为实体
     *
     * @param po 用户PO
     * @return 用户实体
     */
    UserEntity toEntity(UserPO po);

    /**
     * 将用户实体列表转换为PO列表
     *
     * @param entities 用户实体列表
     * @return 用户PO列表
     */
    List<UserPO> toPoList(List<UserEntity> entities);

    /**
     * 将用户PO列表转换为实体列表
     *
     * @param pos 用户PO列表
     * @return 用户实体列表
     */
    List<UserEntity> toEntityList(List<UserPO> pos);
}

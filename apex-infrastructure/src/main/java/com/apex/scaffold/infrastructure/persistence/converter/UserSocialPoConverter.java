package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.UserSocialEntity;
import com.apex.scaffold.infrastructure.persistence.po.UserSocialPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@link UserSocialEntity} 与 {@link UserSocialPO} 之间的 MapStruct 双向转换器。
 *
 * <p>使用 MapStruct（componentModel = "spring"）完成领域实体与持久化对象
 * 之间的双向映射，忽略未匹配目标属性，供 Repository 实现层使用。
 * 渠道枚举 {@code SocialChannel} 与字符串列之间由 MapStruct 隐式转换。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserSocialPoConverter {

    /**
     * 将社交身份绑定实体转换为持久化对象。
     *
     * @param entity 社交身份绑定实体
     * @return 社交身份绑定持久化对象
     */
    UserSocialPO toPo(UserSocialEntity entity);

    /**
     * 将社交身份绑定持久化对象转换为实体。
     *
     * @param po 社交身份绑定持久化对象
     * @return 社交身份绑定实体
     */
    UserSocialEntity toEntity(UserSocialPO po);

    /**
     * 将社交身份绑定实体列表转换为持久化对象列表。
     *
     * @param entities 社交身份绑定实体列表
     * @return 社交身份绑定持久化对象列表
     */
    List<UserSocialPO> toPoList(List<UserSocialEntity> entities);

    /**
     * 将社交身份绑定持久化对象列表转换为实体列表。
     *
     * @param pos 社交身份绑定持久化对象列表
     * @return 社交身份绑定实体列表
     */
    List<UserSocialEntity> toEntityList(List<UserSocialPO> pos);
}

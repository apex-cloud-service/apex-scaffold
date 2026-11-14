package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.UserConsentEntity;
import com.apex.scaffold.infrastructure.persistence.po.UserConsentPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * {@link UserConsentEntity} 与 {@link UserConsentPO} 之间的 MapStruct 双向转换器。
 *
 * <p>使用 MapStruct（componentModel = "spring"）完成领域实体与持久化对象
 * 之间的双向映射，忽略未匹配目标属性，供 Repository 实现层使用。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserConsentPoConverter {

    /**
     * 将用户同意实体转换为PO
     *
     * @param entity 用户同意实体
     * @return 用户同意PO
     */
    @Mapping(source = "version", target = "consentVersion")
    UserConsentPO toPo(UserConsentEntity entity);

    /**
     * 将用户同意PO转换为实体
     *
     * @param po 用户同意PO
     * @return 用户同意实体
     */
    @Mapping(source = "consentVersion", target = "version")
    UserConsentEntity toEntity(UserConsentPO po);

    /**
     * 将用户同意实体列表转换为PO列表
     *
     * @param entities 用户同意实体列表
     * @return 用户同意PO列表
     */
    List<UserConsentPO> toPoList(List<UserConsentEntity> entities);

    /**
     * 将用户同意PO列表转换为实体列表
     *
     * @param pos 用户同意PO列表
     * @return 用户同意实体列表
     */
    List<UserConsentEntity> toEntityList(List<UserConsentPO> pos);
}

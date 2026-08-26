package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.UserComplianceEntity;
import com.apex.scaffold.infrastructure.persistence.po.UserCompliancePO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@link UserComplianceEntity} 与 {@link UserCompliancePO} 之间的 MapStruct 双向转换器。
 *
 * <p>使用 MapStruct（componentModel = "spring"）完成领域实体与持久化对象
 * 之间的双向映射，忽略未匹配目标属性，供 Repository 实现层使用。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserCompliancePoConverter {

    /**
     * 将用户合规实体转换为PO
     *
     * @param entity 用户合规实体
     * @return 用户合规PO
     */
    UserCompliancePO toPo(UserComplianceEntity entity);

    /**
     * 将用户合规PO转换为实体
     *
     * @param po 用户合规PO
     * @return 用户合规实体
     */
    UserComplianceEntity toEntity(UserCompliancePO po);

    /**
     * 将用户合规实体列表转换为PO列表
     *
     * @param entities 用户合规实体列表
     * @return 用户合规PO列表
     */
    List<UserCompliancePO> toPoList(List<UserComplianceEntity> entities);

    /**
     * 将用户合规PO列表转换为实体列表
     *
     * @param pos 用户合规PO列表
     * @return 用户合规实体列表
     */
    List<UserComplianceEntity> toEntityList(List<UserCompliancePO> pos);
}

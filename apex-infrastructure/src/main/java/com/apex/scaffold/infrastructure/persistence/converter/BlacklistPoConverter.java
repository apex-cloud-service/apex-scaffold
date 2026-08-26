package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.BlacklistEntity;
import com.apex.scaffold.infrastructure.persistence.po.BlacklistPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * {@link BlacklistEntity} 与 {@link BlacklistPO} 之间的 MapStruct 双向转换器。
 *
 * <p>使用 MapStruct（componentModel = "spring"）完成领域实体与持久化对象
 * 之间的双向映射与内联更新（{@code @MappingTarget}），忽略未匹配目标属性，
 * 供 Repository 实现层使用。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BlacklistPoConverter {

    /**
     * 将黑名单实体转换为PO
     *
     * @param entity 黑名单实体
     * @return 黑名单PO
     */
    BlacklistPO toPo(BlacklistEntity entity);

    /**
     * 更新黑名单PO
     *
     * @param entity 黑名单实体
     * @param po 黑名单PO
     */
    void updatePo(BlacklistEntity entity, @MappingTarget BlacklistPO po);

    /**
     * 将黑名单PO转换为实体
     *
     * @param po 黑名单PO
     * @return 黑名单实体
     */
    BlacklistEntity toEntity(BlacklistPO po);

    /**
     * 将黑名单实体列表转换为PO列表
     *
     * @param entities 黑名单实体列表
     * @return 黑名单PO列表
     */
    List<BlacklistPO> toPoList(List<BlacklistEntity> entities);

    /**
     * 将黑名单PO列表转换为实体列表
     *
     * @param pos 黑名单PO列表
     * @return 黑名单实体列表
     */
    List<BlacklistEntity> toEntityList(List<BlacklistPO> pos);
}

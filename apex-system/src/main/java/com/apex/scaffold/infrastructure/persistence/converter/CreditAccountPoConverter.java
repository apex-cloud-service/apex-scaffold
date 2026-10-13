package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.CreditAccountEntity;
import com.apex.scaffold.infrastructure.persistence.po.CreditAccountPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@link CreditAccountEntity} 与 {@link CreditAccountPO} 之间的 MapStruct 双向转换器。
 *
 * <p>使用 MapStruct（componentModel = "spring"）完成领域实体与持久化对象
 * 之间的双向映射，忽略未匹配目标属性，供 Repository 实现层使用。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CreditAccountPoConverter {

    /**
     * 将信用账户实体转换为PO
     *
     * @param entity 信用账户实体
     * @return 信用账户PO
     */
    CreditAccountPO toPo(CreditAccountEntity entity);

    /**
     * 将信用账户PO转换为实体
     *
     * @param po 信用账户PO
     * @return 信用账户实体
     */
    CreditAccountEntity toEntity(CreditAccountPO po);

    /**
     * 将信用账户实体列表转换为PO列表
     *
     * @param entities 信用账户实体列表
     * @return 信用账户PO列表
     */
    List<CreditAccountPO> toPoList(List<CreditAccountEntity> entities);

    /**
     * 将信用账户PO列表转换为实体列表
     *
     * @param pos 信用账户PO列表
     * @return 信用账户实体列表
     */
    List<CreditAccountEntity> toEntityList(List<CreditAccountPO> pos);
}

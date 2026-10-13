package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.CreditTxEntity;
import com.apex.scaffold.infrastructure.persistence.po.CreditTxPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@link CreditTxEntity} 与 {@link CreditTxPO} 之间的 MapStruct 双向转换器。
 *
 * <p>使用 MapStruct（componentModel = "spring"）完成领域实体与持久化对象
 * 之间的双向映射，忽略未匹配目标属性，供 Repository 实现层使用。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CreditTxPoConverter {

    /**
     * 将信用交易实体转换为PO
     *
     * @param entity 信用交易实体
     * @return 信用交易PO
     */
    CreditTxPO toPo(CreditTxEntity entity);

    /**
     * 将信用交易PO转换为实体
     *
     * @param po 信用交易PO
     * @return 信用交易实体
     */
    CreditTxEntity toEntity(CreditTxPO po);

    /**
     * 将信用交易实体列表转换为PO列表
     *
     * @param entities 信用交易实体列表
     * @return 信用交易PO列表
     */
    List<CreditTxPO> toPoList(List<CreditTxEntity> entities);

    /**
     * 将信用交易PO列表转换为实体列表
     *
     * @param pos 信用交易PO列表
     * @return 信用交易实体列表
     */
    List<CreditTxEntity> toEntityList(List<CreditTxPO> pos);
}

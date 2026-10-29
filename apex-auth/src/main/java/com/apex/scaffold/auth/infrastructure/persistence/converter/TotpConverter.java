package com.apex.scaffold.auth.infrastructure.persistence.converter;

import com.apex.scaffold.auth.domain.model.entity.TotpEntity;
import com.apex.scaffold.auth.infrastructure.persistence.po.TotpPO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * TOTP 实体与持久化对象转换器
 *
 * <p>负责 TotpEntity 与 TotpPO 之间的双向转换，使用 MapStruct 自动生成实现代码。
 *
 * @author Apex Scaffold Team
 * @since 1.3.1
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, uses = NumericFlagConverter.class)
public interface TotpConverter {

    /**
     * 实体转持久化对象
     *
     * @param entity TOTP 实体
     * @return TOTP 持久化对象
     */
    TotpPO toPo(TotpEntity entity);

    /**
     * 持久化对象转实体
     *
     * @param po TOTP 持久化对象
     * @return TOTP 实体
     */
    TotpEntity toEntity(TotpPO po);
}

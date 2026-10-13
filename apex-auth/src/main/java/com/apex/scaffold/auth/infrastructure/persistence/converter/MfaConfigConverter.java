package com.apex.scaffold.auth.infrastructure.persistence.converter;

import com.apex.scaffold.auth.domain.model.entity.MfaConfigEntity;
import com.apex.scaffold.auth.infrastructure.persistence.po.MfaConfigPO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * MFA 配置实体与持久化对象转换器
 *
 * <p>负责 MfaConfigEntity 与 MfaConfigPO 之间的双向转换，使用 MapStruct 自动生成实现代码。
 *
 * @author Apex Scaffold Team
 * @since 2026-09-21
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, uses = NumericFlagConverter.class)
public interface MfaConfigConverter {

    /**
     * 实体转持久化对象
     *
     * @param entity MFA 配置实体
     * @return MFA 配置持久化对象
     */
    MfaConfigPO toPo(MfaConfigEntity entity);

    /**
     * 持久化对象转实体
     *
     * @param po MFA 配置持久化对象
     * @return MFA 配置实体
     */
    MfaConfigEntity toEntity(MfaConfigPO po);
}

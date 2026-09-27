package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.SysSsoProviderEntity;
import com.apex.scaffold.domain.model.sso.SsoProtocolType;
import com.apex.scaffold.infrastructure.persistence.po.SysSsoProviderPO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * SSO 提供商 PO 转换器。
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SysSsoProviderPoConverter {

    /**
     * 将实体转换为持久化对象
     *
     * @param entity 实体对象
     * @return 持久化对象
     */
    SysSsoProviderPO toPo(SysSsoProviderEntity entity);

    /**
     * 将持久化对象转换为实体
     *
     * @param po 持久化对象
     * @return 实体对象
     */
    SysSsoProviderEntity toEntity(SysSsoProviderPO po);

    /**
     * 将协议类型枚举转换为字符串
     *
     * @param value 协议类型枚举
     * @return 字符串表示
     */
    default String map(SsoProtocolType value) {
        return value == null ? null : value.name();
    }

    /**
     * 将字符串转换为协议类型枚举
     *
     * @param value 字符串值
     * @return 协议类型枚举
     */
    default SsoProtocolType map(String value) {
        return value == null ? null : SsoProtocolType.valueOf(value);
    }
}

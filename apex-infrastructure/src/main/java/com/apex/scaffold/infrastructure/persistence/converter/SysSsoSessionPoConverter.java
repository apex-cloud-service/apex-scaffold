package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.SysSsoSessionEntity;
import com.apex.scaffold.domain.model.sso.SsoSessionStatus;
import com.apex.scaffold.infrastructure.persistence.po.SysSsoSessionPO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * SSO 会话 PO 转换器。
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SysSsoSessionPoConverter {

    /**
     * 将实体转换为持久化对象。
     *
     * @param entity 实体对象
     * @return 持久化对象
     */
    SysSsoSessionPO toPo(SysSsoSessionEntity entity);

    /**
     * 将持久化对象转换为实体。
     *
     * @param po 持久化对象
     * @return 实体对象
     */
    SysSsoSessionEntity toEntity(SysSsoSessionPO po);

    /**
     * 将会话状态枚举转换为字符串。
     *
     * @param value 会话状态枚举
     * @return 字符串值
     */
    default String map(SsoSessionStatus value) {
        return value == null ? null : value.name();
    }

    /**
     * 将字符串转换为会话状态枚举。
     *
     * @param value 字符串值
     * @return 会话状态枚举
     */
    default SsoSessionStatus map(String value) {
        return value == null ? null : SsoSessionStatus.valueOf(value);
    }
}

package com.apex.scaffold.authcenter.infrastructure.persistence.converter;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 在领域布尔标志与数据库数值标志之间进行转换，保留空值语义。
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface NumericFlagConverter {
    /**
     * 将布尔标志转换为数据库数值标志。
     *
     * @param value 布尔标志，可为 null
     * @return true 对应 1，false 对应 0，null 保持为 null
     */
    static Integer toNumber(Boolean value) {
        return value == null ? null : value ? 1 : 0;
    }

    /**
     * 将数据库数值标志转换为布尔标志。
     *
     * @param value 数值标志，可为 null
     * @return 0 对应 false，非零值对应 true，null 保持为 null
     */
    static Boolean toBoolean(Integer value) {
        return value == null ? null : value != 0;
    }
}

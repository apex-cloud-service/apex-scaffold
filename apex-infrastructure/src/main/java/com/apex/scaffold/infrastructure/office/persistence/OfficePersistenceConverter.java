package com.apex.scaffold.infrastructure.office.persistence;

import com.apex.scaffold.domain.office.OfficeIdentityBinding;
import com.apex.scaffold.domain.office.OfficePlatformApp;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 办公平台持久化对象转换器。
 *
 * <p>基于 MapStruct 实现办公模块 PO 与领域实体间的转换：
 * {@code OfficeAppPO} 与 {@code OfficePlatformApp} 双向映射，
 * {@code OfficeBindingPO} 到 {@code OfficeIdentityBinding} 的单向映射，
 * 供 {@code OfficeRepositoryImpl} 在领域仓储实现中完成对象模型转换。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OfficePersistenceConverter {
    OfficePlatformApp toApp(OfficeAppPO po);

    OfficeAppPO toPo(OfficePlatformApp app);

    OfficeIdentityBinding toBinding(OfficeBindingPO po);
}

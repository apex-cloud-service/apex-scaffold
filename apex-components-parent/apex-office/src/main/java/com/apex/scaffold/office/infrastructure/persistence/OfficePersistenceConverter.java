package com.apex.scaffold.office.infrastructure.persistence;

import com.apex.scaffold.office.domain.OfficeIdentityBinding;
import com.apex.scaffold.office.domain.OfficeOrganizationMapping;
import com.apex.scaffold.office.domain.OfficePlatformApp;
import com.apex.scaffold.office.domain.OfficeSyncTask;
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
    /**
     * 将应用配置持久化对象转换为领域实体。
     *
     * @param po 应用配置持久化对象
     * @return 应用配置领域实体
     */
    OfficePlatformApp toApp(OfficeAppPO po);

    /**
     * 将应用配置领域实体转换为持久化对象。
     *
     * @param app 应用配置领域实体
     * @return 应用配置持久化对象
     */
    OfficeAppPO toPo(OfficePlatformApp app);

    /**
     * 将身份绑定持久化对象转换为领域实体。
     *
     * @param po 身份绑定持久化对象
     * @return 身份绑定领域实体
     */
    OfficeIdentityBinding toBinding(OfficeBindingPO po);

    /**
     * 将组织映射持久化对象转换为领域实体。
     *
     * @param po 组织映射持久化对象
     * @return 组织映射领域实体
     */
    OfficeOrganizationMapping toOrganizationMapping(OfficeOrganizationMappingPO po);

    /**
     * 将组织映射领域实体转换为持久化对象。
     *
     * @param mapping 组织映射领域实体
     * @return 组织映射持久化对象
     */
    OfficeOrganizationMappingPO toOrganizationMappingPo(OfficeOrganizationMapping mapping);

    /**
     * 将同步任务持久化对象转换为领域实体。
     *
     * @param po 同步任务持久化对象
     * @return 同步任务领域实体
     */
    OfficeSyncTask toSyncTask(OfficeSyncTaskPO po);

    /**
     * 将同步任务领域实体转换为持久化对象。
     *
     * @param task 同步任务领域实体
     * @return 同步任务持久化对象
     */
    OfficeSyncTaskPO toSyncTaskPo(OfficeSyncTask task);
}

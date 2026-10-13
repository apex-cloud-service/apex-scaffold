package com.apex.scaffold.office.application;

import com.apex.scaffold.office.domain.OfficePlatform;
import com.apex.scaffold.office.domain.OfficePlatformApp;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * 办公平台管理 MapStruct 转换器
 *
 * <p>负责应用层命令对象与领域实体之间的转换。
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OfficeAdminAssembler {
    /**
     * 将命令对象转换为领域实体
     *
     * @param command 应用配置命令
     * @param tenantId 租户标识
     * @param platform 平台类型
     * @param id 主键 ID（新增时为 null）
     * @param version 乐观锁版本号
     * @return 平台应用配置实体
     */
    @Mapping(target = "deleted", constant = "0")
    @Mapping(target = "version", source = "version")
    OfficePlatformApp toApp(
            OfficeAppCommand command, String tenantId, OfficePlatform platform, Long id, Integer version);
}

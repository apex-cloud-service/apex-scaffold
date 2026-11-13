package com.apex.scaffold.file.domain.service;

/**
 * 租户可用状态端口。
 *
 * <p>文件域不拥有租户数据，通过本端口判断分享兑换时租户是否仍可用。默认实现基于启动配置，
 * 由部署侧维护停用租户清单；若后续接入租户中心，只需替换该端口的适配器。</p>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
public interface TenantStatusPort {

    /**
     * 判断租户当前是否可用。
     *
     * @param tenantId 可信租户标识
     * @return 租户未停用时返回 true
     */
    boolean isActive(String tenantId);
}

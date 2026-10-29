package com.apex.scaffold.file.infrastructure.tenant;

import com.apex.scaffold.file.domain.service.TenantStatusPort;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 基于启动配置的租户可用状态适配器。
 *
 * <p>文件域不持有租户数据，分享兑换时的租户停用判定由部署侧通过
 * {@code file.share.suspended-tenants} 维护停用清单；该策略是显式的降级实现，
 * 不构成对租户中心实时状态的替代。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.2
 * @see TenantStatusPort
 */
@Component
public class ConfiguredTenantStatusAdapter implements TenantStatusPort {

    private final Set<String> suspendedTenants;

    /**
     * 构造适配器并冻结启动时的停用租户清单。
     *
     * @param suspendedTenants 启动配置的停用租户标识
     */
    public ConfiguredTenantStatusAdapter(@Value("${file.share.suspended-tenants:}") List<String> suspendedTenants) {
        this.suspendedTenants = new HashSet<>(suspendedTenants);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isActive(String tenantId) {
        return tenantId != null && !tenantId.isBlank() && !suspendedTenants.contains(tenantId);
    }
}

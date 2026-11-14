package com.apex.scaffold.common.capability;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 租户能力快照的进程内 TTL 缓存。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public final class TenantCapabilitySnapshotCache {

    private final ConcurrentHashMap<String, TenantCapabilitySnapshot> snapshots = new ConcurrentHashMap<>();

    /**
     * 获取未过期快照。
     *
     * @param tenantCode 租户编码
     * @return 未过期快照，找不到或已过期时返回 null
     */
    public TenantCapabilitySnapshot getFresh(String tenantCode) {
        TenantCapabilitySnapshot snapshot = snapshots.get(tenantCode);
        if (snapshot == null || !snapshot.fresh()) {
            return null;
        }
        return snapshot;
    }

    /**
     * 保存已成功解析的快照。
     *
     * @param tenantCode 租户编码
     * @param snapshot 能力快照
     */
    public void put(String tenantCode, TenantCapabilitySnapshot snapshot) {
        snapshots.put(tenantCode, snapshot);
    }
}

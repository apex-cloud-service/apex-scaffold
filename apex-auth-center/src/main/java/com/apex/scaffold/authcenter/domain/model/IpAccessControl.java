package com.apex.scaffold.authcenter.domain.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * IP访问控制领域模型
 * @author Apex Team
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IpAccessControl {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * IP地址或CIDR
     */
    private String ipAddress;

    /**
     * 控制类型：WHITELIST-白名单, BLACKLIST-黑名单
     */
    private AccessControlType type;

    /**
     * 规则描述
     */
    private String description;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 优先级（数字越小优先级越高）
     */
    private Integer priority;

    /**
     * 过期时间（黑名单可设置自动解禁）
     */
    private LocalDateTime expiresAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 创建人
     */
    private String createdBy;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 更新人
     */
    private String updatedBy;

    /**
     * 访问控制类型枚举
     */
    public enum AccessControlType {
        /**
         * 白名单 - 只允许列表中的IP访问
         */
        WHITELIST,

        /**
         * 黑名单 - 禁止列表中的IP访问
         */
        BLACKLIST
    }

    /**
     * 判断是否已过期
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * 判断是否有效（启用且未过期）
     */
    public boolean isActive() {
        return Boolean.TRUE.equals(enabled) && !isExpired();
    }

    /**
     * 判断IP是否匹配（支持CIDR格式）
     */
    public boolean matches(String targetIp) {
        if (ipAddress == null || targetIp == null) {
            return false;
        }

        // 精确匹配
        if (ipAddress.equals(targetIp)) {
            return true;
        }

        // CIDR匹配
        if (ipAddress.contains("/")) {
            return matchesCidr(targetIp, ipAddress);
        }

        // 通配符匹配（如 192.168.1.*）
        if (ipAddress.contains("*")) {
            return matchesWildcard(targetIp, ipAddress);
        }

        return false;
    }

    /**
     * CIDR匹配
     */
    private boolean matchesCidr(String targetIp, String cidr) {
        try {
            String[] parts = cidr.split("/");
            String network = parts[0];
            int prefixLength = Integer.parseInt(parts[1]);

            long targetIpLong = ipToLong(targetIp);
            long networkIpLong = ipToLong(network);
            long mask = (0xFFFFFFFFL << (32 - prefixLength)) & 0xFFFFFFFFL;

            return (targetIpLong & mask) == (networkIpLong & mask);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 通配符匹配
     */
    private boolean matchesWildcard(String targetIp, String pattern) {
        String[] targetParts = targetIp.split("\\.");
        String[] patternParts = pattern.split("\\.");

        if (targetParts.length != 4 || patternParts.length != 4) {
            return false;
        }

        for (int i = 0; i < 4; i++) {
            if (!"*".equals(patternParts[i]) && !targetParts[i].equals(patternParts[i])) {
                return false;
            }
        }

        return true;
    }

    /**
     * IP地址转长整型
     */
    private long ipToLong(String ipAddress) {
        String[] octets = ipAddress.split("\\.");
        long result = 0;
        for (int i = 0; i < 4; i++) {
            result |= (Long.parseLong(octets[i]) << (24 - (8 * i)));
        }
        return result;
    }
}

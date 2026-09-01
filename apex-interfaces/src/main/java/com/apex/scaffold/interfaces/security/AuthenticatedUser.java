package com.apex.scaffold.interfaces.security;

import java.security.Principal;

public record AuthenticatedUser(Long userId, String openId, String tenantId, Long deptId) implements Principal {

    public AuthenticatedUser(Long userId, String openId, String tenantId) {
        this(userId, openId, tenantId, null);
    }

    @Override
    public String getName() {
        return openId;
    }

    @Override
    public String toString() {
        return "AuthenticatedUser[userId=" + userId + ", openId=" + openId + ", tenantId=" + tenantId + "]";
    }
}

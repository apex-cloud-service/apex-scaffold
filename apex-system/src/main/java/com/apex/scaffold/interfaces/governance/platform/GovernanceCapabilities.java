package com.apex.scaffold.interfaces.governance.platform;

/**
 * 部署平台治理能力。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public record GovernanceCapabilities(
        boolean readiness, boolean registration, boolean externalScaling, boolean healthCheck) {}

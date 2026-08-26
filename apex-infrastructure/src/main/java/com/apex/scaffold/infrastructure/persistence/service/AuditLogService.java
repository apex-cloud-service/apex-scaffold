package com.apex.scaffold.infrastructure.persistence.service;

import com.apex.scaffold.infrastructure.persistence.po.AuditLogPO;

/**
 * 审计日志服务接口
 *
 * @author Apex Team
 * @version 1.0.0
 */
public interface AuditLogService {

    /**
     * 保存审计日志
     *
     * @param auditLog 审计日志对象
     */
    void saveAuditLog(AuditLogPO auditLog);
}

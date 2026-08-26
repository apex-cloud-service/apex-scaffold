package com.apex.scaffold.infrastructure.persistence.service;

import com.apex.scaffold.infrastructure.persistence.mapper.AuditLogMapper;
import com.apex.scaffold.infrastructure.persistence.po.AuditLogPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 审计日志服务实现
 *
 * @author Apex Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogMapper auditLogMapper;

    @Override
    public void saveAuditLog(AuditLogPO auditLog) {
        try {
            auditLogMapper.insert(auditLog);
        } catch (Exception e) {
            // 审计日志保存失败不应影响主业务流程,仅记录错误
            log.error("保存审计日志失败: {}", e.getMessage(), e);
        }
    }
}

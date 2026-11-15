package com.apex.scaffold.knowledge.domain.model;

import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 数据库认领凭证。owner 与 generation 构成 fencing token，租约到期后的旧持有者不得继续写库。
 *
 * @param tenantId 从可信上下文或专属任务投影取得的租户
 * @param documentId 文档唯一标识，上传预登记时即分配
 * @param owner 本次认领随机标识
 * @param generation 单调递增认领代次
 * @param kind UPLOAD、INGEST 或 CLEANUP
 * @param leaseUntil 数据库中认领到期时间
 * @author Apex Team
 * @since 1.3.2
 */
@Builder
public record KnowledgeDocumentLease(
        String tenantId, Long documentId, String owner, long generation, String kind, LocalDateTime leaseUntil) {}

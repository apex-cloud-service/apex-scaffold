package com.apex.scaffold.knowledge.application.dto;

import com.apex.scaffold.knowledge.domain.model.KnowledgeDocumentCleanup;
import java.time.LocalDateTime;

/**
 * 管理端文档清理进度，不暴露租户、对象定位、认领owner或密钥。
 *
 * @param documentId 文档ID，上传失败同样拥有预分配ID
 * @param title 文档标题
 * @param status PENDING/PROCESSING/COMPLETED/FAILED
 * @param errorMessage 安全错误摘要，可空
 * @param attempts 累计清理尝试次数
 * @param updatedAt 最近变更时间，沿用业务LocalDateTime序列化
 * @param nextRetryAt 下次自动重试；完成或达上限为空，tombstone仍按内部复核周期定向清理
 * @author Apex Team
 * @since 1.3.2
 */
public record KnowledgeDocumentCleanupDTO(
        Long documentId,
        String title,
        String status,
        String errorMessage,
        int attempts,
        LocalDateTime updatedAt,
        LocalDateTime nextRetryAt) {

    /**
     * 将领域清理凭证映射为管理端展示 DTO。
     *
     * @param cleanup 清理凭证；调用方须保证非空
     * @return 仅含可展示字段的清理进度
     */
    public static KnowledgeDocumentCleanupDTO from(KnowledgeDocumentCleanup cleanup) {
        return new KnowledgeDocumentCleanupDTO(
                cleanup.documentId(),
                cleanup.title(),
                cleanup.status().name(),
                cleanup.errorMessage(),
                cleanup.attempts(),
                cleanup.updatedAt(),
                cleanup.nextRetryAt());
    }
}

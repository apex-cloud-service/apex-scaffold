package com.apex.scaffold.knowledge.application.dto;

import com.apex.scaffold.knowledge.domain.model.KnowledgeDocumentCleanup;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(name = "KbDocumentCleanup", description = "管理端文档清理进度；不暴露租户、对象定位、认领 owner 或密钥")
public record KnowledgeDocumentCleanupDTO(
        @Schema(
                        description = "文档 ID；上传失败同样拥有预分配 ID",
                        example = "1",
                        type = "integer",
                        format = "int64",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Long documentId,
        @Schema(description = "文档标题", example = "产品需求说明书.docx") String title,
        @Schema(
                        description = "清理状态",
                        example = "PROCESSING",
                        allowableValues = {"PENDING", "PROCESSING", "COMPLETED", "FAILED"},
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String status,
        @Schema(description = "安全错误摘要；无错误时为 null", example = "对象定位快照缺失") String errorMessage,
        @Schema(
                        description = "累计清理尝试次数",
                        example = "2",
                        type = "integer",
                        format = "int32",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                int attempts,
        @Schema(description = "最近变更时间", example = "2026-11-22T10:15:30", type = "string", format = "date-time")
                LocalDateTime updatedAt,
        @Schema(
                        description = "下次自动重试时间；清理完成或达到重试上限时为 null，tombstone 仍按内部复核周期定向清理",
                        example = "2026-11-22T11:15:30",
                        type = "string",
                        format = "date-time")
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

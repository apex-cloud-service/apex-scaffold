package com.apex.scaffold.knowledge.domain.model;

import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 文档删除/上传补偿的持久凭证，独立于主档可见性保存，不包含文件内容或供应商密钥。
 * REGISTERED 上传凭证不参加清理；DELETE tombstone 长期保留并定向复核。
 *
 * @param documentId 文档 ID；上传失败时使用预留的预分配 ID
 * @param tenantId 可信租户
 * @param title 文档标题，用于管理端展示
 * @param mode 凭证生命周期类型
 * @param status 清理进度状态
 * @param provider 对象存储提供商快照，历史记录可为空
 * @param bucket 对象桶快照，历史记录可为空
 * @param objectKey 独占对象键快照，历史记录可为空
 * @param objectRequired 是否存在必须确认的对象清理
 * @param indexEndpoint 索引端点快照
 * @param indexName 索引名快照
 * @param indexRequired 是否存在必须确认的索引清理
 * @param indexConfirmed 索引阶段是否已确认
 * @param objectRemoved 对象是否已删除或明确不存在
 * @param errorMessage 安全错误摘要，可为空
 * @param attempts 累计清理尝试次数
 * @param updatedAt 最近变更时间
 * @param nextRetryAt 下次自动重试时间，可为空
 * @param nextReviewAt 下次 tombstone 定向复核时间，可为空
 * @author Apex Team
 * @since 1.3.2
 */
@Builder
public record KnowledgeDocumentCleanup(
        Long documentId,
        String tenantId,
        String title,
        Mode mode,
        Status status,
        String provider,
        String bucket,
        String objectKey,
        boolean objectRequired,
        String indexEndpoint,
        String indexName,
        boolean indexRequired,
        boolean indexConfirmed,
        boolean objectRemoved,
        String errorMessage,
        int attempts,
        LocalDateTime updatedAt,
        LocalDateTime nextRetryAt,
        LocalDateTime nextReviewAt) {

    /** 上传预留、成功登记、明确删除、历史仅索引清理四种互斥生命周期。 */
    public enum Mode {
        /** 上传已预留但尚未确认登记，仅异常补偿后可清理。 */
        UPLOAD_STAGING,
        /** 上传已成功登记，不参加清理 worker 扫描。 */
        REGISTERED,
        /** 用户已接受删除，后台按定位快照清理外部资源。 */
        DELETE,
        /** 历史软删除主档仅补索引清理，不推断或删除对象存储。 */
        LEGACY_INDEX
    }

    /** 前端只消费这四种状态；COMPLETED 不代表迟到任务无法产生短暂残留。 */
    public enum Status {
        /** 已持久化意图，等待后台认领。 */
        PENDING,
        /** 已被工作租约认领，正在清理。 */
        PROCESSING,
        /** 最近一轮清理已确认；仍保留凭证做定向复核。 */
        COMPLETED,
        /** 清理失败或结果未知，保留安全摘要与重试信息。 */
        FAILED
    }
}

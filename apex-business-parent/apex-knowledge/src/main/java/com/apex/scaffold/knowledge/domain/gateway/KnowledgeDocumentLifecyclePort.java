package com.apex.scaffold.knowledge.domain.gateway;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.knowledge.domain.model.KnowledgeChunk;
import com.apex.scaffold.knowledge.domain.model.KnowledgeDocument;
import com.apex.scaffold.knowledge.domain.model.KnowledgeDocumentCleanup;
import com.apex.scaffold.knowledge.domain.model.KnowledgeDocumentLease;
import java.time.LocalDateTime;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 知识文档生命周期持久化边界。多步变更在短 JDBC 事务内完成，远程操作必须在事务外。
 * 所有业务访问验证可信租户，任务扫描仅从专属投影获取租户；不绕过共享租户拦截器。
 *
 * @author Apex Team
 * @since 1.3.2
 */
public interface KnowledgeDocumentLifecyclePort {

    /**
     * 上传前预留 documentId、定位快照与租约，保证进程中断时有补偿凭证。
     *
     * @param title 文档标题
     * @param provider 对象存储提供商标识
     * @param bucket 对象桶名
     * @param objectKey 预留的独占对象键
     * @param indexEndpoint 索引端点快照
     * @param indexName 索引名快照
     * @return 上传工作租约，含租户、文档 ID、owner 与 generation
     */
    Mono<KnowledgeDocumentLease> reserveUpload(
            String title, String provider, String bucket, String objectKey, String indexEndpoint, String indexName);

    /**
     * 保存供应商实际返回键；只有与预留独占键一致的对象才允许自动清理。
     *
     * @param lease 上传工作租约
     * @param actualKey 供应商实际返回的对象键
     * @return 绑定完成信号；键不一致时发出错误
     */
    Mono<Void> bindUploadedKey(KnowledgeDocumentLease lease, String actualKey);

    /**
     * 原子登记主档并关闭 staging；不得将成功上传当作清理 tombstone。
     *
     * @param lease 上传工作租约
     * @param document 待登记的主档
     * @return 登记后的主档
     */
    Mono<KnowledgeDocument> registerUpload(KnowledgeDocumentLease lease, KnowledgeDocument document);

    /**
     * 先锁定并 fence 上传 owner 再允许补偿；正常 REGISTERED 或已被新 owner 认领返回 false。
     *
     * @param lease 上传工作租约
     * @return true 表示该租约仍可执行对象补偿，false 表示不得删除对象
     */
    Mono<Boolean> abortUpload(KnowledgeDocumentLease lease);

    /**
     * 持久记录上传失败；释放上传 lease 后清理 worker 才能推进。
     *
     * @param lease 上传工作租约
     * @param errorMessage 安全错误摘要
     * @param objectRemoved 对象是否已删除或明确不存在
     * @return 失败状态持久化完成信号
     */
    Mono<Void> failUpload(KnowledgeDocumentLease lease, String errorMessage, boolean objectRemoved);

    /**
     * 原子记录删除意图并 soft-delete 隐藏主档；true 仅表示接受，重复请求幂等。
     *
     * @param documentId 文档 ID
     * @param indexEndpoint 当前配置的索引端点，仅在主档无快照时使用
     * @param indexName 当前配置的索引名，仅在主档无快照时使用
     * @return true 表示删除意图已接受；不存在或非本租户时发出错误
     */
    Mono<Boolean> acceptDelete(Long documentId, String indexEndpoint, String indexName);

    /**
     * 无活跃工作认领时重置摄取状态；删除或活跃认领冲突明确拒绝。
     *
     * @param documentId 文档 ID
     * @return 重置状态后的主档
     */
    Mono<KnowledgeDocument> requestReindex(Long documentId);

    /**
     * 数据库排他认领摄取，删除意图存在、活跃租约或主档缺失时返回空。
     *
     * @param documentId 文档 ID
     * @return 工作租约；无法认领时为空完成
     */
    Mono<KnowledgeDocumentLease> claimIngestion(Long documentId);

    /**
     * 校验 fencing token、租约、未删除主档后写状态；所有更新检查受影响行数。
     *
     * @param lease 当前工作租约
     * @param document 待持久化的主档状态
     * @return 更新后的主档
     */
    Mono<KnowledgeDocument> persistDocument(KnowledgeDocumentLease lease, KnowledgeDocument document);

    /**
     * 锁定工作行与未删除主档，原子替换分块并回填 chunk ID。
     *
     * @param lease 当前工作租约
     * @param chunks 新分块列表
     * @return 实际写入的分块数量
     */
    Mono<Integer> replaceChunks(KnowledgeDocumentLease lease, List<KnowledgeChunk> chunks);

    /**
     * 索引写之前保存实际 endpoint/index 快照，防止超时任务失去定位凭证。
     *
     * @param lease 当前工作租约
     * @param endpoint 实际索引端点
     * @param indexName 实际索引名
     * @return 快照持久化完成信号
     */
    Mono<Void> recordIndexTarget(KnowledgeDocumentLease lease, String endpoint, String indexName);

    /**
     * 续租或释放必须匹配 owner/generation；过期 owner 不得恢复租约。
     *
     * @param lease 当前工作租约
     * @return true 表示续租成功，false 表示租约已失效
     */
    Mono<Boolean> renew(KnowledgeDocumentLease lease);

    /**
     * 释放当前持有者租约；不匹配 owner/generation 时不得影响新持有者。
     *
     * @param lease 当前工作租约
     * @return 释放完成信号
     */
    Mono<Void> release(KnowledgeDocumentLease lease);

    /**
     * 从专属 cleanup/work 表读取有限、去重租户投影，不扫描共享业务表差集。
     *
     * @param limit 单次读取上限
     * @return 去重后的租户标识列表
     */
    Mono<List<String>> scanTaskTenants(int limit);

    /**
     * 扫描指定租户超过阈值仍滞留的摄取候选。
     *
     * @param tenantId 可信租户
     * @param threshold 更新时间上限，早于该时间的记录才进入候选
     * @param limit 单轮上限
     * @return 文档 ID 列表，不跨租户
     */
    Mono<List<Long>> scanIngestion(String tenantId, LocalDateTime threshold, int limit);

    /**
     * 扫描指定租户到期待处理或需定向复核的清理凭证。
     *
     * @param tenantId 可信租户
     * @param now 当前时间，用于比较重试与复核时间
     * @param limit 单轮上限
     * @return 文档 ID 列表，不跨租户
     */
    Mono<List<Long>> scanCleanups(String tenantId, LocalDateTime now, int limit);

    /**
     * 无活跃 ingest/upload 租约时原子认领清理，completed 定向复核也适用。
     *
     * @param tenantId 可信租户
     * @param documentId 文档 ID
     * @return 清理工作租约；无法认领时为空完成
     */
    Mono<KnowledgeDocumentLease> claimCleanup(String tenantId, Long documentId);

    /**
     * 查询当前租户文档的清理凭证。
     *
     * @param documentId 文档 ID
     * @return 清理凭证；不存在时为空完成
     */
    Mono<KnowledgeDocumentCleanup> findCleanup(Long documentId);

    /**
     * 分页查询当前租户清理凭证。
     *
     * @param page 页码，从 1 开始
     * @param size 页大小
     * @return 清理凭证分页，list/total 口径
     */
    Mono<PageResponse<KnowledgeDocumentCleanup>> cleanupPage(int page, int size);

    /**
     * 接受人工重试，活跃认领或已完成复核时按状态拒绝。
     *
     * @param documentId 文档 ID
     * @return true 表示重试已接受，不代表远程资源已完成清理
     */
    Mono<Boolean> retryCleanup(Long documentId);

    /**
     * 仅清除该租户该文档的派生分块，主档 tombstone 不物理删除。
     *
     * @param lease 当前清理工作租约
     * @return 分块删除完成信号
     */
    Mono<Void> deleteChunks(KnowledgeDocumentLease lease);

    /**
     * 确认索引清理成功；失败时保留摘要、次数与下次重试，tombstone 永不删除。
     *
     * @param lease 当前清理工作租约
     * @return 索引阶段确认完成信号
     */
    Mono<Void> confirmIndex(KnowledgeDocumentLease lease);

    /**
     * 成功删除或明确对象不存在后单独持久确认，索引失败不回滚此阶段。
     *
     * @param lease 当前清理工作租约
     * @return 对象阶段确认完成信号
     */
    Mono<Void> confirmObject(KnowledgeDocumentLease lease);

    /**
     * 记录本轮清理结果并释放租约。
     *
     * @param lease 当前工作租约
     * @param errorMessage 安全错误摘要；为空表示本轮全部确认成功
     * @return 结果与租约释放持久化完成信号
     */
    Mono<Void> finishCleanup(KnowledgeDocumentLease lease, String errorMessage);

    /**
     * 默认关闭的历史 softdeleted 定向索引清理；不推断 provider/bucket，不删历史对象。
     *
     * @param tenantId 显式配置的历史租户
     * @param endpoint 目标索引端点
     * @param indexName 目标索引名
     * @param limit 单轮导入上限
     * @return 实际导入的清理凭证数量
     */
    Mono<Integer> importLegacyDeleted(String tenantId, String endpoint, String indexName, int limit);
}

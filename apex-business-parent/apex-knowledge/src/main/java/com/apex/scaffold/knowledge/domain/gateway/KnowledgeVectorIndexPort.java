package com.apex.scaffold.knowledge.domain.gateway;

import com.apex.scaffold.knowledge.domain.model.IndexedChunk;
import com.apex.scaffold.knowledge.domain.model.VectorSearchQuery;
import com.apex.scaffold.knowledge.domain.model.VectorSearchResult;
import java.time.LocalDateTime;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 知识分块索引端口，提供带租户字段的索引写入、删除与混合检索。
 *
 * <p>当前 Meilisearch 实现写入租户字段，并在检索和删除请求中附加租户过滤；
 * 调用方须提供可信的租户值。创建、设置、写入和删除均等待异步 task 的 succeeded；
 * failed、canceled、未知状态、轮询超限或超时拒绝成功，只有明确幂等错误码允许忽略。
 *
 * <p>缺少索引实现时检索返回空，摄取按配置只入库，必需索引清理必须显式失败。
 * 异步取消/超时无法证明服务端不会迟到，删除方须长期保留 tombstone 并定向复核。
 *
 * @author Apex Team
 * @since 1.3.2
 */
public interface KnowledgeVectorIndexPort {

    /**
     * 提交索引创建及设置请求，包括过滤字段、可搜索字段与向量 embedder 配置。
     *
     * <p>进程内就绪标记只在创建及设置任务确认成功后写入；失败保持可重试。
     * index_already_exists 为创建幂等例外，鉴权、网络、通用错误不得当作已存在。
     *
     * @param embeddingEnabled 是否配置用户提供的向量；关闭时提交空 embedder 配置
     * @param dimensions 启用向量时提交的维度，调用方应提供与向量一致的有效值
     * @return 创建和设置任务确认成功后完成；索引能力禁用由调用方明确降级，不等于实际写成功
     */
    Mono<Void> ensureIndex(boolean embeddingEnabled, int dimensions);

    /**
     * 分批提交分块索引文档，写入租户字段；无向量的分块仅含词法检索内容。
     *
     * @param chunks 待写入分块；当前实现对 {@code null} 或空列表不发起写入
     * @return 每批任务确认成功后完成；提交失败、未知状态与有界轮询超时发出错误信号
     */
    Mono<Void> upsertChunks(List<IndexedChunk> chunks);

    /**
     * 提交指定租户、指定文档的全部分块索引删除请求。
     *
     * @param tenantId 参与删除过滤的可信租户 ID
     * @param documentId 参与删除过滤的文档 ID
     * @return task成功确认或明确index_not_found后完成；索引禁用、任务失败/未知或超时发出错误
     */
    Mono<Void> deleteDocumentChunks(String tenantId, Long documentId);

    /**
     * 按持久化目标快照定向清理；目标无法安全访问或索引能力关闭时必须失败。
     *
     * @param endpoint 原索引端点快照
     * @param indexName 原索引名快照
     * @param tenantId 可信租户 ID
     * @param documentId 文档 ID
     * @return task 成功确认完成；端点不匹配、能力关闭或任务失败/未知时发出错误
     */
    default Mono<Void> deleteDocumentChunks(String endpoint, String indexName, String tenantId, Long documentId) {
        return Mono.error(new IllegalStateException("索引目标清理未启用"));
    }

    /**
     * 执行带租户过滤的检索，有查询向量时尝试 hybrid，否则使用词法检索。
     *
     * <p>当前实现对带向量查询的失败再尝试一次词法检索；
     * 返回结果的 {@code hybridUsed} 表示最终请求是否携带向量参数，
     * 不单独验证服务端实际使用的检索策略。
     *
     * @param query 检索条件，包含可信租户、可选空间过滤及分页参数
     * @return 检索结果及最终请求的 hybrid 标记；降级实现返回空结果，
     *     最终检索失败时发出错误信号
     */
    Mono<VectorSearchResult> search(VectorSearchQuery query);

    /**
     * 根据调用方提供的分块 ID 加载索引文档，再分批提交覆盖写入。
     *
     * <p>此方法不按更新时间筛选、不推进校准游标，也不清理旧索引条目。
     * 当前 Meilisearch 实现在方法调用时执行加载函数以取得 {@link Mono}；
     * 加载函数直接抛出的异常不会自动转换为此方法返回值的错误信号。
     *
     * @param ids 待重写的分块 ID 列表；{@code null} 或空列表不调用加载函数
     * @param chunkLoader 按给定 ID 加载 {@link IndexedChunk} 列表的函数
     * @return 任务成功确认后返回实际加载的分块数；加载/提交/任务确认失败发出错误信号；
     *     无分块或能力未启用返回零，不清理其他文档
     */
    Mono<Long> rebuild(List<Long> ids, java.util.function.Function<List<Long>, Mono<List<IndexedChunk>>> chunkLoader);

    /**
     * 获取索引侧同步标记；默认实现不维护同步状态。
     *
     * @return 默认及当前索引实现均返回 {@code null}，不能据此推断最近同步时间
     */
    default LocalDateTime lastSyncMarker() {
        return null;
    }
}

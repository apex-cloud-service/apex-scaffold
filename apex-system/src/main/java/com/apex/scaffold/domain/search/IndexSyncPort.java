package com.apex.scaffold.domain.search;

import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 搜索索引同步端口接口（Domain 层端口定义）
 *
 * <p>本接口定义搜索索引的增量同步与全量重建契约，供业务代码在数据变更时触发索引更新。
 * Domain 层与 Application 层仅依赖本接口，具体索引操作由 Infrastructure 层适配器实现。
 *
 * <p><strong>设计理念（Event-Driven Indexing）</strong></p>
 * <ul>
 *   <li><b>事件驱动索引</b>：业务操作（创建用户、修改角色等）成功提交后，发布领域事件，
 *       由事件监听器异步调用本接口更新索引，避免阻塞主流程</li>
 *   <li><b>失败降级</b>：索引更新失败不影响业务操作，仅记录日志并触发失败重试或定时校准补偿</li>
 *   <li><b>最终一致性</b>：索引与数据库短暂不一致可接受，定时校准任务确保最终一致</li>
 *   <li><b>响应式设计</b>：返回 {@link Mono}，适配项目 WebFlux 响应式技术栈</li>
 * </ul>
 *
 * <p><strong>核心职责</strong></p>
 * <ol>
 *   <li><b>单个索引</b>：业务操作后立即索引单个资源（如新建用户 → 索引该用户）</li>
 *   <li><b>批量索引</b>：批量操作后批量索引多个资源（如批量导入用户 → 批量索引）</li>
 *   <li><b>删除索引</b>：资源删除后移除索引（如删除用户 → 删除索引）</li>
 *   <li><b>全量重建</b>：索引损坏或首次部署时，从数据库全量重建索引</li>
 *   <li><b>增量校准</b>：定时任务检查数据库与索引差异，增量同步变更数据</li>
 * </ol>
 *
 * <p><strong>使用场景</strong></p>
 *
 * <p><strong>场景1：业务操作触发索引（推荐）</strong></p>
 * <pre>{@code
 * // 在 Application Service 中，业务操作成功后发布领域事件
 * @Transactional
 * public Mono<UserDTO> createUser(CreateUserRequest request) {
 *     return userRepository.save(user)
 *         .flatMap(savedUser -> {
 *             // 发布领域事件：用户已创建
 *             applicationEventPublisher.publishEvent(
 *                 new ResourceIndexEvent(Action.CREATE, "user", savedUser.getId().toString())
 *             );
 *             return Mono.just(toDTO(savedUser));
 *         });
 * }
 *
 * // 事件监听器异步调用索引同步
 * @EventListener
 * @Async
 * public void onResourceIndexEvent(ResourceIndexEvent event) {
 *     if (event.getAction() == Action.CREATE || event.getAction() == Action.UPDATE) {
 *         indexSyncPort.indexResource(event.getResourceType(), event.getResourceId())
 *             .subscribe(
 *                 success -> log.info("Index synced: type={}, id={}", event.getResourceType(), event.getResourceId()),
 *                 error -> log.error("Index sync failed", error)
 *             );
 *     } else if (event.getAction() == Action.DELETE) {
 *         indexSyncPort.removeIndex(event.getResourceType(), event.getResourceId())
 *             .subscribe();
 *     }
 * }
 * }</pre>
 *
 * <p><strong>场景2：定时校准任务</strong></p>
 * <pre>{@code
 * // 定时任务（每天凌晨2点）：增量校准最近24小时的变更
 * @Scheduled(cron = "0 0 2 * * ?")
 * public void reconcileIndex() {
 *     long since = System.currentTimeMillis() - 24 * 3600 * 1000; // 24小时前
 *     String[] types = {"user", "role", "menu", "dept", "dict", "config"};
 *
 *     for (String type : types) {
 *         indexSyncPort.syncIncremental(type, since)
 *             .doOnSuccess(count -> log.info("Incremental sync: type={}, count={}", type, count))
 *             .subscribe();
 *     }
 * }
 * }</pre>
 *
 * <p><strong>场景3：首次部署全量重建</strong></p>
 * <pre>{@code
 * // 管理后台触发全量重建（需 SUPER_ADMIN 权限）
 * @PostMapping("/admin-api/v1/search/rebuild")
 * @RequirePermission("system:search:rebuild")
 * public Mono<ResponseResult<Long>> rebuildIndex(@RequestParam(required = false) String resourceType) {
 *     return indexSyncPort.rebuildIndex(resourceType)
 *         .map(count -> ResponseResult.success(count));
 * }
 * }</pre>
 *
 * <p><strong>实现注意事项</strong></p>
 * <ul>
 *   <li><b>异步非阻塞</b>：索引操作应在独立线程池（{@code Schedulers.boundedElastic()}）执行，
 *       避免阻塞 Reactor 事件循环</li>
 *   <li><b>失败重试</b>：网络抖动或搜索引擎暂时不可用时，应重试 2-3 次（指数退避）</li>
 *   <li><b>批量优化</b>：批量索引时，使用搜索引擎的批量 API（如 Meilisearch addDocuments），
 *       避免逐个调用</li>
 *   <li><b>数据权限</b>：从数据库读取资源时，需应用数据权限过滤（用户仅索引当前租户数据）</li>
 *   <li><b>敏感字段脱敏</b>：索引文档不应包含密码、密钥等敏感字段</li>
 *   <li><b>增量窗口</b>：增量同步基于 {@code updated_at} 字段，需确保所有资源表有此字段</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.0
 * @see SearchRepository 搜索查询接口
 * @see com.apex.scaffold.application.event.ResourceIndexEvent 资源索引事件
 */
public interface IndexSyncPort {

    /**
     * 索引单个资源
     *
     * <p>业务操作（创建、修改）成功后，调用本方法立即索引该资源。
     *
     * <p><strong>执行流程</strong></p>
     * <ol>
     *   <li>根据 {@code resourceType} 和 {@code resourceId} 从数据库查询资源详情</li>
     *   <li>将资源字段映射为搜索引擎文档格式（参考 {@link SearchResultEntity}）</li>
     *   <li>调用搜索引擎 API 添加或更新文档（Meilisearch addDocuments）</li>
     *   <li>返回是否成功（{@code true} 成功，{@code false} 失败）</li>
     * </ol>
     *
     * <p><strong>错误处理</strong></p>
     * <ul>
     *   <li><b>资源不存在</b>：数据库查询为空，返回 {@code false}，记录 WARN 日志</li>
     *   <li><b>搜索引擎不可用</b>：网络超时或服务不可用，返回 {@code false}，记录 ERROR 日志</li>
     *   <li><b>索引失败</b>：搜索引擎返回错误（如字段类型不匹配），返回 {@code false}，记录 ERROR 日志</li>
     * </ul>
     *
     * <p><strong>性能优化</strong></p>
     * <ul>
     *   <li><b>异步执行</b>：本方法应异步执行，不阻塞业务主流程</li>
     *   <li><b>响应时间</b>：单个索引操作应在 100ms 内完成（P95）</li>
     *   <li><b>失败降级</b>：索引失败不影响业务，仅记录日志，依赖定时校准补偿</li>
     * </ul>
     *
     * @param resourceType 资源类型，枚举值：user/role/menu/dept/dict/config
     * @param resourceId 资源唯一标识（数据库主键），示例："1001"
     * @return 是否成功：{@code true} 索引成功，{@code false} 索引失败（资源不存在或搜索引擎不可用）
     */
    Mono<Boolean> indexResource(String resourceType, String resourceId);

    /**
     * 批量索引资源
     *
     * <p>批量业务操作（批量导入、批量修改）成功后，调用本方法批量索引资源。
     * 相比逐个调用 {@link #indexResource}，批量操作可显著提升性能。
     *
     * <p><strong>执行流程</strong></p>
     * <ol>
     *   <li>根据 {@code resourceIds} 批量从数据库查询资源详情（{@code IN} 查询）</li>
     *   <li>批量转换为搜索引擎文档格式</li>
     *   <li>调用搜索引擎批量 API 添加或更新文档（Meilisearch addDocuments 支持数组）</li>
     *   <li>返回成功索引的数量</li>
     * </ol>
     *
     * <p><strong>批量大小限制</strong></p>
     * <ul>
     *   <li><b>单批最大数量</b>：建议 100-200 条（配置项 {@code meilisearch.batch-size}）</li>
     *   <li><b>超出自动分片</b>：若 {@code resourceIds.size() > batchSize}，自动分片为多批次</li>
     * </ul>
     *
     * <p><strong>错误处理</strong></p>
     * <ul>
     *   <li><b>部分资源不存在</b>：仅索引存在的资源，不存在的跳过，记录 WARN 日志</li>
     *   <li><b>搜索引擎不可用</b>：返回 {@code 0}，记录 ERROR 日志</li>
     *   <li><b>部分失败</b>：搜索引擎批量 API 可能部分成功，返回实际成功数量</li>
     * </ul>
     *
     * @param resourceType 资源类型，枚举值：user/role/menu/dept/dict/config
     * @param resourceIds 资源ID列表，示例：["1001", "1002", "1003"]
     * @return 成功索引的数量，范围：0 ~ resourceIds.size()
     */
    Mono<Integer> indexResourceBatch(String resourceType, List<String> resourceIds);

    /**
     * 删除索引
     *
     * <p>业务删除操作成功后，调用本方法移除搜索索引，避免已删除资源仍出现在搜索结果中。
     *
     * <p><strong>执行流程</strong></p>
     * <ol>
     *   <li>构建索引文档ID（格式：{@code resourceType_resourceId}，如 "user_1001"）</li>
     *   <li>调用搜索引擎删除 API（Meilisearch deleteDocument）</li>
     *   <li>返回是否成功（{@code true} 成功，{@code false} 失败）</li>
     * </ol>
     *
     * <p><strong>幂等性</strong></p>
     * <p>重复删除同一索引视为成功（搜索引擎返回"文档不存在"也返回 {@code true}）。
     *
     * <p><strong>错误处理</strong></p>
     * <ul>
     *   <li><b>索引不存在</b>：视为成功，返回 {@code true}</li>
     *   <li><b>搜索引擎不可用</b>：返回 {@code false}，记录 ERROR 日志</li>
     *   <li><b>删除失败</b>：搜索引擎返回错误，返回 {@code false}，记录 ERROR 日志</li>
     * </ul>
     *
     * @param resourceType 资源类型，枚举值：user/role/menu/dept/dict/config
     * @param resourceId 资源唯一标识（数据库主键），示例："1001"
     * @return 是否成功：{@code true} 删除成功或索引不存在，{@code false} 搜索引擎不可用
     */
    Mono<Boolean> removeIndex(String resourceType, String resourceId);

    /**
     * 全量重建索引
     *
     * <p>索引损坏、首次部署或数据迁移后，调用本方法从数据库全量重建索引。
     * <b>⚠️ 高开销操作</b>：需遍历所有资源表，适合低峰期执行，避免影响业务。
     *
     * <p><strong>执行流程</strong></p>
     * <ol>
     *   <li>清空现有索引（Meilisearch deleteAllDocuments 或删除索引后重建）</li>
     *   <li>根据 {@code resourceType} 确定需重建的资源类型（null 表示全部）</li>
     *   <li>分批从数据库查询资源（每批 100 条，避免一次加载过多数据）</li>
     *   <li>批量索引（调用 {@link #indexResourceBatch}）</li>
     *   <li>返回索引总数</li>
     * </ol>
     *
     * <p><strong>全量重建策略</strong></p>
     * <ul>
     *   <li><b>单类型重建</b>：{@code resourceType="user"}，仅重建用户索引</li>
     *   <li><b>全类型重建</b>：{@code resourceType=null}，重建所有类型（user/role/menu/dept/dict/config）</li>
     * </ul>
     *
     * <p><strong>性能优化</strong></p>
     * <ul>
     *   <li><b>批量查询</b>：每次从数据库查询 100 条资源，避免 OOM</li>
     *   <li><b>批量索引</b>：调用搜索引擎批量 API，减少网络往返</li>
     *   <li><b>进度日志</b>：每批次完成后记录 INFO 日志，便于监控进度</li>
     *   <li><b>响应时间</b>：全量重建耗时取决于数据量，1万条资源约 10-30 秒</li>
     * </ul>
     *
     * <p><strong>错误处理</strong></p>
     * <ul>
     *   <li><b>清空索引失败</b>：记录 ERROR 日志，终止重建，返回 {@code 0}</li>
     *   <li><b>批量索引失败</b>：跳过失败批次，继续后续批次，记录 ERROR 日志</li>
     *   <li><b>搜索引擎不可用</b>：返回 {@code 0}，记录 ERROR 日志</li>
     * </ul>
     *
     * <p><strong>使用场景</strong></p>
     * <ul>
     *   <li><b>首次部署</b>：应用启动后，检测索引为空，自动触发全量重建</li>
     *   <li><b>索引损坏</b>：搜索结果异常，管理员手动触发重建</li>
     *   <li><b>数据迁移</b>：从旧系统迁移数据后，触发全量重建</li>
     *   <li><b>Schema 变更</b>：索引字段变更（如新增字段），触发全量重建</li>
     * </ul>
     *
     * @param resourceType 资源类型，可选值：user/role/menu/dept/dict/config；
     *                     {@code null} 表示重建所有类型
     * @return 索引总数，返回成功索引的资源数量
     */
    Mono<Long> rebuildIndex(String resourceType);

    /**
     * 增量校准索引（定时任务补偿）
     *
     * <p>定时任务（每天凌晨2点）调用本方法，检查数据库与索引的差异，增量同步变更数据。
     * 补偿机制：确保因异步失败、网络抖动、应用重启等原因未及时索引的资源最终一致。
     *
     * <p><strong>执行流程</strong></p>
     * <ol>
     *   <li>查询 {@code updated_at > sinceTimestamp} 的资源（数据库增量查询）</li>
     *   <li>批量索引这些资源（调用 {@link #indexResourceBatch}）</li>
     *   <li>返回同步数量</li>
     * </ol>
     *
     * <p><strong>时间窗口策略</strong></p>
     * <ul>
     *   <li><b>常规校准</b>：{@code sinceTimestamp = 当前时间 - 24小时}，同步最近24小时变更</li>
     *   <li><b>首次校准</b>：{@code sinceTimestamp = 0}，同步所有历史数据</li>
     *   <li><b>故障恢复</b>：{@code sinceTimestamp = 最后成功时间戳}，同步故障期间的变更</li>
     * </ul>
     *
     * <p><strong>增量查询示例</strong></p>
     * <pre>{@code
     * // SQL 查询：查询最近 24 小时更新的用户
     * SELECT id FROM sys_user
     * WHERE updated_at > ?
     *   AND tenant_id = ?
     *   AND status = 1
     * ORDER BY updated_at ASC
     * LIMIT 10000;  // 限制单次同步数量，防止一次加载过多
     * }</pre>
     *
     * <p><strong>性能优化</strong></p>
     * <ul>
     *   <li><b>索引优化</b>：确保资源表 {@code updated_at} 字段有索引，加速查询</li>
     *   <li><b>分批处理</b>：查询结果分批索引，每批 100 条</li>
     *   <li><b>限制数量</b>：单次校准最多处理 1 万条，超出分多次执行</li>
     *   <li><b>响应时间</b>：1000 条增量同步约 5-10 秒</li>
     * </ul>
     *
     * <p><strong>错误处理</strong></p>
     * <ul>
     *   <li><b>数据库查询失败</b>：返回 {@code 0}，记录 ERROR 日志</li>
     *   <li><b>批量索引失败</b>：跳过失败批次，继续后续批次，记录 ERROR 日志</li>
     *   <li><b>搜索引擎不可用</b>：返回 {@code 0}，记录 ERROR 日志，下次定时任务重试</li>
     * </ul>
     *
     * @param resourceType 资源类型，枚举值：user/role/menu/dept/dict/config
     * @param sinceTimestamp 起始时间戳（毫秒），仅同步此时间后的变更；
     *                       {@code 0} 表示同步所有历史数据
     * @return 同步数量，返回成功同步的资源数量
     */
    Mono<Long> syncIncremental(String resourceType, Long sinceTimestamp);
}

package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.admin.ReviewReason;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 审核原因配置仓储。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface ReviewReasonRepository {
    /**
     * 按审核类型查询原因列表。
     *
     * @param reviewType 审核类型标识
     * @param enabledOnly 仅返回启用状态的原因时为 {@code true}
     * @return 匹配的原因列表
     */
    Mono<List<ReviewReason>> findByType(String reviewType, boolean enabledOnly);

    /**
     * 查询指定类型下启用且编码匹配的单个原因。
     *
     * @param reviewType 审核类型标识
     * @param code 原因编码
     * @return 匹配的启用原因；不存在时为空 Mono
     */
    Mono<ReviewReason> findEnabled(String reviewType, String code);

    /**
     * 以乐观锁版本校验更新原因配置。
     *
     * @param id 原因 ID
     * @param label 原因展示名称
     * @param description 原因描述
     * @param enabled 是否启用
     * @param requireComment 是否强制填写补充说明
     * @param sortOrder 排序值
     * @param expectedVersion 期望版本号，用于并发冲突检测
     * @return 更新后的原因记录
     */
    Mono<ReviewReason> update(
            Long id,
            String label,
            String description,
            boolean enabled,
            boolean requireComment,
            int sortOrder,
            int expectedVersion);
}

package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.OnboardingGuideAnswerEntity;
import reactor.core.publisher.Mono;

/**
 * 引导问答仓储接口
 *
 * <p>定义引导问答相关的持久化操作方法
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface OnboardingGuideAnswerRepository {
    /**
     * 插入或更新引导问答
     *
     * @param entity 引导问答实体
     * @return 保存后的引导问答实体
     */
    Mono<OnboardingGuideAnswerEntity> upsert(OnboardingGuideAnswerEntity entity);

    /**
     * 根据用户ID和场景统计引导问答数量
     *
     * @param userId 用户ID
     * @param scene 场景
     * @return 引导问答数量
     */
    Mono<Long> countByUserIdAndScene(Long userId, String scene);
}

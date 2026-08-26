package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.QuizQuestionEntity;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 答题题目仓储接口
 *
 * <p>定义答题题目相关的持久化操作方法。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface QuizQuestionRepository {

    /**
     * 随机抽取指定数量的题目
     *
     * @param limit 抽取数量
     * @return 题目列表
     */
    Mono<List<QuizQuestionEntity>> findRandomQuestions(int limit);

    /**
     * 根据题目唯一标识查询题目
     *
     * @param questionId 题目唯一标识
     * @return 题目实体
     */
    Mono<QuizQuestionEntity> findByQuestionId(String questionId);

    /**
     * 保存题目
     *
     * @param entity 题目实体
     * @return 保存后的题目实体
     */
    Mono<QuizQuestionEntity> save(QuizQuestionEntity entity);
}

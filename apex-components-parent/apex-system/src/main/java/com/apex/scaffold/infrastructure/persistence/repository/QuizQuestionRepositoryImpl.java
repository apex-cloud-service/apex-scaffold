package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.QuizQuestionEntity;
import com.apex.scaffold.domain.repository.QuizQuestionRepository;
import com.apex.scaffold.infrastructure.persistence.converter.QuizQuestionPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.QuizQuestionMapper;
import com.apex.scaffold.infrastructure.persistence.po.QuizQuestionPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link QuizQuestionRepository} 的 MyBatis-Plus 实现（基础设施层）。
 *
 * <p>负责 {@link QuizQuestionEntity} 的持久化读写与随机题目查询，
 * 通过 {@link QuizQuestionPoConverter} 在领域实体与 {@link QuizQuestionPO} 之间转换，
 * 阻塞操作调度到 JDBC 专用线程池。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class QuizQuestionRepositoryImpl implements QuizQuestionRepository {

    private final QuizQuestionMapper mapper;
    private final QuizQuestionPoConverter quizQuestionPoConverter;

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<List<QuizQuestionEntity>> findRandomQuestions(int limit) {
        return Mono.fromCallable(() -> mapper.selectList(new LambdaQueryWrapper<QuizQuestionPO>()
                        .eq(QuizQuestionPO::getEnabled, 1)
                        .apply("order by RAND() limit {0}", limit)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(quizQuestionPoConverter::toEntityList);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<QuizQuestionEntity> findByQuestionId(String questionId) {
        return Mono.fromCallable(() -> mapper.selectOne(new LambdaQueryWrapper<QuizQuestionPO>()
                        .eq(QuizQuestionPO::getQuestionId, questionId)
                        .last("limit 1")))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(quizQuestionPoConverter::toEntity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<QuizQuestionEntity> save(QuizQuestionEntity entity) {
        return Mono.fromCallable(() -> {
                    QuizQuestionPO po = quizQuestionPoConverter.toPo(entity);
                    if (po.getId() == null) {
                        mapper.insert(po);
                    } else {
                        mapper.updateById(po);
                    }
                    return mapper.selectById(po.getId());
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(quizQuestionPoConverter::toEntity);
    }
}

package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.QuizQuestionEntity;
import com.apex.scaffold.infrastructure.persistence.po.QuizQuestionPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 测验题目实体与持久化对象转换器
 *
 * <p>使用 MapStruct 实现 {@link QuizQuestionEntity} 与 {@link QuizQuestionPO} 之间的双向转换。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface QuizQuestionPoConverter {

    /**
     * 将实体转换为持久化对象
     *
     * @param entity 测验题目实体
     * @return 持久化对象
     */
    QuizQuestionPO toPo(QuizQuestionEntity entity);

    /**
     * 将持久化对象转换为实体
     *
     * @param po 测验题目持久化对象
     * @return 实体
     */
    QuizQuestionEntity toEntity(QuizQuestionPO po);

    /**
     * 将实体列表转换为持久化对象列表
     *
     * @param entities 测验题目实体列表
     * @return 持久化对象列表
     */
    List<QuizQuestionPO> toPoList(List<QuizQuestionEntity> entities);

    /**
     * 将持久化对象列表转换为实体列表
     *
     * @param pos 测验题目持久化对象列表
     * @return 实体列表
     */
    List<QuizQuestionEntity> toEntityList(List<QuizQuestionPO> pos);
}

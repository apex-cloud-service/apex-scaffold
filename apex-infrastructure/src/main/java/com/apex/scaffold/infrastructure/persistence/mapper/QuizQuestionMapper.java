package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.QuizQuestionPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 题目 Mapper 接口
 *
 * <p>提供题目表的 MyBatis-Plus CRUD 操作能力。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper
public interface QuizQuestionMapper extends BaseMapper<QuizQuestionPO> {}

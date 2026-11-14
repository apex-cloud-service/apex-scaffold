package com.apex.scaffold.knowledge.infrastructure.persistence.mapper;

import com.apex.scaffold.knowledge.infrastructure.persistence.po.KnowledgeChunkPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识分块 Mapper。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Mapper
public interface KnowledgeChunkMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<KnowledgeChunkPO> {}

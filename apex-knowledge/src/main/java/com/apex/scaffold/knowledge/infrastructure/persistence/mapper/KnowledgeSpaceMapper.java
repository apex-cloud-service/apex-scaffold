package com.apex.scaffold.knowledge.infrastructure.persistence.mapper;

import com.apex.scaffold.knowledge.infrastructure.persistence.po.KnowledgeSpacePO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识空间 Mapper。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Mapper
public interface KnowledgeSpaceMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<KnowledgeSpacePO> {}

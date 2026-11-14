package com.apex.scaffold.knowledge.infrastructure.persistence.mapper;

import com.apex.scaffold.knowledge.infrastructure.persistence.po.KnowledgeDocumentPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识文档 Mapper。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Mapper
public interface KnowledgeDocumentMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<KnowledgeDocumentPO> {}

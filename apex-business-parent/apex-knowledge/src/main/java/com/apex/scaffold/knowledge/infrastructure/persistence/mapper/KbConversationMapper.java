package com.apex.scaffold.knowledge.infrastructure.persistence.mapper;

import com.apex.scaffold.knowledge.infrastructure.persistence.po.KbConversationPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识问答会话 Mapper。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Mapper
public interface KbConversationMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<KbConversationPO> {}

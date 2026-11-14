package com.apex.scaffold.knowledge.infrastructure.persistence.mapper;

import com.apex.scaffold.knowledge.infrastructure.persistence.po.KbMessagePO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识问答消息 Mapper。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Mapper
public interface KbMessageMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<KbMessagePO> {}

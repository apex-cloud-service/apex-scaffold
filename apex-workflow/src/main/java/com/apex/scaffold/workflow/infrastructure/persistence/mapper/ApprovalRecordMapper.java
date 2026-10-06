package com.apex.scaffold.workflow.infrastructure.persistence.mapper;

import com.apex.scaffold.workflow.infrastructure.persistence.po.ApprovalRecordPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批记录 MyBatis Mapper 接口。
 *
 * <p>基于 MyBatis-Plus {@link BaseMapper}，提供审批记录持久化对象
 * {@link ApprovalRecordPO} 的基础 CRUD 操作。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper
public interface ApprovalRecordMapper extends BaseMapper<ApprovalRecordPO> {}

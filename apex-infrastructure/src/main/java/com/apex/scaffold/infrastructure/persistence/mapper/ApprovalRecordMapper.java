package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.ApprovalRecordPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * ApprovalRecord MyBatis Mapper 接口。
 *
 * <p>基于 MyBatis-Plus BaseMapper，提供 ApprovalRecord 的基础 CRUD 操作。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper
public interface ApprovalRecordMapper extends BaseMapper<ApprovalRecordPO> {}

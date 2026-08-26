package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.AuditLogPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审计日志Mapper
 *
 * @author Apex Team
 * @version 1.0.0
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogPO> {}

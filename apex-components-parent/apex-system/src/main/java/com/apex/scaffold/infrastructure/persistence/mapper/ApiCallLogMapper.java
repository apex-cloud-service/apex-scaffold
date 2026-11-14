package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.ApiCallLogPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * API 调用日志 MyBatis-Plus Mapper。
 *
 * <p>用于 {@link ApiCallLogPO} 与表 {@code sys_api_call_log} 之间的持久化映射。
 * 继承 {@link BaseMapper} 获得 insert / selectById / selectList 等基础 CRUD 能力。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see ApiCallLogPO
 */
@Mapper
public interface ApiCallLogMapper extends BaseMapper<ApiCallLogPO> {}

package com.apex.scaffold.office.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 办公平台同步任务数据访问接口。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
@Mapper
public interface OfficeSyncTaskMapper extends BaseMapper<OfficeSyncTaskPO> {}

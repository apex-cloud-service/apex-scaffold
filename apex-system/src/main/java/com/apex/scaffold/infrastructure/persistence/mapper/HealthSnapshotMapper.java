package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.HealthSnapshotPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 运维体检历史快照 Mapper。
 *
 * <p>继承 {@link BaseMapper} 获得通用增删改查能力；租户过滤由持久化拦截器追加，
 * 逻辑删除由 {@code deleted} 字段处理。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see HealthSnapshotPO
 */
@Mapper
public interface HealthSnapshotMapper extends BaseMapper<HealthSnapshotPO> {}

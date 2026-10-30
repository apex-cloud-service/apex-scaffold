package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.HealthSnapshotPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 运维体检历史快照 Mapper。
 *
 * <p>继承 {@link BaseMapper} 获得通用增删改查能力；租户过滤由持久化拦截器追加，
 * 逻辑删除由 {@code deleted} 字段处理。保留期清理需要真实回收容量，
 * 因此额外提供绕过逻辑删除的物理删除语句。
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.0.0
 * @see HealthSnapshotPO
 */
@Mapper
public interface HealthSnapshotMapper extends BaseMapper<HealthSnapshotPO> {

    /**
     * 按主键物理删除快照（保留期清理专用）。
     *
     * <p>不使用 {@code deleteById}：{@code @TableLogic} 会把它改写成逻辑删除，
     * 数据仍占用存储，与「容量回收」目标相反。</p>
     *
     * @param id 快照主键
     * @return 删除行数
     */
    @Delete("DELETE FROM ops_health_snapshot WHERE id = #{id}")
    int deleteByIdPhysically(@Param("id") Long id);
}

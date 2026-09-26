package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.ChartPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 图表 Mapper。
 *
 * <p>继承 MyBatis-Plus {@code BaseMapper}，提供 {@code doc_chart} 表（文档图表配置）
 * 的基础 CRUD 与条件查询能力，由 {@code ChartRepositoryImpl} 配合
 * {@code ChartPoConverter} 完成图表领域仓储实现。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Mapper
public interface ChartMapper extends BaseMapper<ChartPO> {}

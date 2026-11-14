package com.apex.scaffold.workflow.infrastructure.persistence.mapper;

import com.apex.scaffold.workflow.infrastructure.persistence.po.WorkflowDefinitionPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作流定义 MyBatis Mapper 接口。
 *
 * <p>基于 MyBatis-Plus {@link BaseMapper}，提供工作流定义持久化对象
 * {@link WorkflowDefinitionPO} 的基础 CRUD 操作。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper
public interface WorkflowDefinitionMapper extends BaseMapper<WorkflowDefinitionPO> {}

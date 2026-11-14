package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.GenArtifactPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 代码生成产物 Mapper 接口。
 *
 * <p>继承 {@link BaseMapper} 获得标准增删改查能力；租户过滤由持久化拦截器统一注入。
 *
 * <p><strong>使用约束</strong></p>
 * <ul>
 *   <li>所有调用必须在 {@code BlockingIoSchedulers.jdbc()} 调度器上执行</li>
 *   <li>逻辑删除由 MyBatis-Plus 自动附加条件</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see GenArtifactPO
 */
@Mapper
public interface GenArtifactMapper extends BaseMapper<GenArtifactPO> {}

package com.apex.scaffold.file.infrastructure.persistence.mapper;

import com.apex.scaffold.file.infrastructure.persistence.po.DocumentPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档数据访问接口。
 *
 * <p>基于 MyBatis-Plus {@link BaseMapper} 提供文档表的 CRUD 操作。
 * 继承 BaseMapper 后自动获得增删改查、批量操作、分页查询等基础能力，
 * 无需手写 SQL 即可完成大部分数据库操作。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>单表 CRUD：insert、updateById、deleteById、selectById、selectList 等</li>
 *   <li>条件构造器：支持 LambdaQueryWrapper、LambdaUpdateWrapper 复杂查询</li>
 *   <li>分页查询：集成 MyBatis-Plus 分页插件</li>
 *   <li>乐观锁：自动处理 version 字段</li>
 *   <li>逻辑删除：自动过滤 deleted 字段</li>
 *   <li>多租户：集成租户拦截器自动注入 tenantId</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>复杂查询可在接口中定义方法，配合 {@code @Select} 注解或 XML 映射文件实现。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see DocumentPO
 * @see BaseMapper
 */
@Mapper
public interface DocumentMapper extends BaseMapper<DocumentPO> {}

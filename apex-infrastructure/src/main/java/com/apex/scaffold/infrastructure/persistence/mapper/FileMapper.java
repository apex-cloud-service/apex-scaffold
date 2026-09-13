package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.FilePO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 文件记录 MyBatis Mapper 接口。
 *
 *
 *<p>负责 {@code sys_file} 表的持久化操作，管理上传文件的元数据和存储状态。
 *继承 MyBatis-Plus {@link BaseMapper} 获得通用单表 CRUD 操作，
 *并通过自定义 SQL 支持文件存储状态的原子转换。</p>
 *
 *<h2>核心能力</h2>
 *<ul>
 *  <li>标记文件存储状态为已删除/已批准</li>
 *</ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper
/**n * FileMappern *n * <p>TODO: 补充 Mapper 的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public interface FileMapper extends BaseMapper<FilePO> {
    /**
     * 标记文件存储状态为已删除。
     *
     * @param fileId 文件 ID
     * @return 受影响行数
     */
    @Update("UPDATE sys_file SET storage_status='DELETED',update_time=NOW() WHERE id=#{fileId} AND deleted=0")
    int markStorageDeleted(@Param("fileId") Long fileId);

    /**
     * 人工审核通过后解除文件的逻辑隔离；对象仍可保留在审核桶中。
     *
     * @param fileId 文件 ID
     * @return 受影响行数
     */
    @Update("UPDATE sys_file SET storage_status='STORED',update_time=NOW() "
            + "WHERE id=#{fileId} AND storage_status='QUARANTINED' AND deleted=0")
    int markStorageApproved(@Param("fileId") Long fileId);
}

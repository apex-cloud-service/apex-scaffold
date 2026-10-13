package com.apex.scaffold.file.infrastructure.persistence.mapper;

import com.apex.scaffold.file.infrastructure.persistence.po.FileChunkPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 文件分片 MyBatis Mapper 接口。
 *
 * <p>负责 {@code file_chunk} 表的持久化操作，管理大文件分片上传的每个分片元数据。
 * 继承 MyBatis-Plus {@link BaseMapper} 获得通用单表 CRUD 操作，
 * 并通过自定义 SQL 支持分片存储状态的原子转换。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper
public interface FileChunkMapper extends BaseMapper<FileChunkPO> {

    /**
     * 按文件 ID 逻辑删除全部分片。
     *
     * <p>合并完成或上传取消后清理分片元数据，仅做逻辑删除，物理对象由存储侧回收。</p>
     *
     * @param fileId 文件 ID
     * @return 受影响行数
     */
    @Update("UPDATE file_chunk SET deleted=1,update_time=CURRENT_TIMESTAMP WHERE file_id=#{fileId} AND deleted=0")
    int markDeletedByFileId(@Param("fileId") Long fileId);
}

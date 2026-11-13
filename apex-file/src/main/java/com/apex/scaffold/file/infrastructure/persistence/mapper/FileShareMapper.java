package com.apex.scaffold.file.infrastructure.persistence.mapper;

import com.apex.scaffold.file.infrastructure.persistence.po.FileSharePO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件分享记录 Mapper。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Mapper
public interface FileShareMapper extends BaseMapper<FileSharePO> {}

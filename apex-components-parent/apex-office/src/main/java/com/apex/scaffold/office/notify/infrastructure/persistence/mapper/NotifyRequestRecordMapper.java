package com.apex.scaffold.office.notify.infrastructure.persistence.mapper;

import com.apex.scaffold.office.notify.infrastructure.persistence.po.NotifyRequestRecordPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 统一通知投递请求留痕 MyBatis Mapper 接口。
 *
 * <p>负责 {@code off_notify_request} 表的持久化操作，继承 MyBatis-Plus
 * {@link BaseMapper} 获得通用单表 CRUD 能力。</p>
 *
 * @author Apex Team
 * @since 1.3.2
 * @see NotifyRequestRecordPO
 */
@Mapper
public interface NotifyRequestRecordMapper extends BaseMapper<NotifyRequestRecordPO> {}

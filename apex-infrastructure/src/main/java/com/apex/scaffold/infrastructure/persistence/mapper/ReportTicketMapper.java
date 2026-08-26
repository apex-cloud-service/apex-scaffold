package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.ReportTicketPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 举报工单 MyBatis Mapper 接口。
 *
 * <p>负责 {@code biz_report_ticket} 表的持久化操作，管理用户举报照片的工单记录。
 * 继承 MyBatis-Plus {@link BaseMapper} 获得通用单表 CRUD 操作。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper
public interface ReportTicketMapper extends BaseMapper<ReportTicketPO> {}

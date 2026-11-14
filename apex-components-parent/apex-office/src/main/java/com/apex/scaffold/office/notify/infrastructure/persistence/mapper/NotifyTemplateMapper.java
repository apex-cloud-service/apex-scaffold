package com.apex.scaffold.office.notify.infrastructure.persistence.mapper;

import com.apex.scaffold.office.notify.infrastructure.persistence.po.NotifyTemplatePO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 统一通知模板 MyBatis Mapper 接口。
 *
 * <p>负责 {@code off_notify_template} 表的持久化操作，继承 MyBatis-Plus
 * {@link BaseMapper} 获得通用单表 CRUD 能力。</p>
 *
 * @author Apex Team
 * @since 1.3.2
 * @see NotifyTemplatePO
 */
@Mapper
public interface NotifyTemplateMapper extends BaseMapper<NotifyTemplatePO> {}

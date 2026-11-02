package com.apex.scaffold.integration.connector.infrastructure.persistence.mapper;

import com.apex.scaffold.integration.connector.infrastructure.persistence.po.WebhookDedupPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 入站 webhook 幂等去重 Mapper（表 {@code int_webhook_dedup}）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Mapper
public interface WebhookDedupMapper extends BaseMapper<WebhookDedupPO> {}

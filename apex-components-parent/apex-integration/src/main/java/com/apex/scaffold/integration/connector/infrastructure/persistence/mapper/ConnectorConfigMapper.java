package com.apex.scaffold.integration.connector.infrastructure.persistence.mapper;

import com.apex.scaffold.integration.connector.infrastructure.persistence.po.ConnectorConfigPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 连接器配置 Mapper（表 {@code int_connector_config}）。
 *
 * <p>租户条件由仓储层显式携带并叠加行级拦截器；乐观锁与逻辑删除由
 * MyBatis-Plus 拦截器按 PO 注解改写。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Mapper
public interface ConnectorConfigMapper extends BaseMapper<ConnectorConfigPO> {}

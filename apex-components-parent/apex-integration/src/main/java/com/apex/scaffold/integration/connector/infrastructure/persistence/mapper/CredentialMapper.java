package com.apex.scaffold.integration.connector.infrastructure.persistence.mapper;

import com.apex.scaffold.integration.connector.infrastructure.persistence.po.CredentialPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户级外部凭据 Mapper（表 {@code int_credential}）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Mapper
public interface CredentialMapper extends BaseMapper<CredentialPO> {}

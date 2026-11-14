package com.apex.scaffold.auth.infrastructure.persistence.mapper;

import com.apex.scaffold.auth.infrastructure.persistence.po.PasswordRecoveryPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 自助密码找回恢复单 Mapper。
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
@Mapper
public interface PasswordRecoveryMapper extends BaseMapper<PasswordRecoveryPO> {}

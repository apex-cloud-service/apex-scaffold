package com.apex.scaffold.authcenter.infrastructure.persistence.mapper;

import com.apex.scaffold.authcenter.infrastructure.persistence.po.TotpPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * TOTP Mapper
 *
 * <p>TOTP 数据访问接口
 *
 * @author Apex Scaffold Team
 * @since 2026-09-21
 */
@Mapper
public interface TotpMapper extends BaseMapper<TotpPO> {

    /**
     * 根据用户 ID 和租户 ID 查询 TOTP 配置
     *
     * @param userId 用户 ID
     * @param tenantId 租户 ID
     * @return TOTP 持久化对象
     */
    TotpPO selectByUserIdAndTenantId(@Param("userId") Long userId, @Param("tenantId") String tenantId);

    /**
     * 根据用户 ID 和租户 ID 删除 TOTP 配置
     *
     * @param userId 用户 ID
     * @param tenantId 租户 ID
     * @return 影响行数
     */
    int deleteByUserIdAndTenantId(@Param("userId") Long userId, @Param("tenantId") String tenantId);

    /**
     * 检查用户是否已配置 TOTP
     *
     * @param userId 用户 ID
     * @param tenantId 租户 ID
     * @return 存在返回 1，否则返回 0
     */
    int existsByUserIdAndTenantId(@Param("userId") Long userId, @Param("tenantId") String tenantId);
}

package com.apex.scaffold.auth.infrastructure.persistence.mapper;

import com.apex.scaffold.auth.infrastructure.persistence.po.MfaConfigPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * MFA 配置 Mapper
 *
 * <p>MFA 配置数据访问接口
 *
 * @author Apex Scaffold Team
 * @since 1.3.1
 */
@Mapper
public interface MfaConfigMapper extends BaseMapper<MfaConfigPO> {

    /**
     * 根据用户 ID 和租户 ID 查询所有 MFA 配置
     *
     * @param userId 用户 ID
     * @param tenantId 租户 ID
     * @return MFA 配置列表
     */
    List<MfaConfigPO> selectByUserIdAndTenantId(@Param("userId") Long userId, @Param("tenantId") String tenantId);

    /**
     * 根据用户 ID、MFA 类型和租户 ID 查询配置
     *
     * @param userId 用户 ID
     * @param mfaType MFA 类型
     * @param tenantId 租户 ID
     * @return MFA 配置
     */
    MfaConfigPO selectByUserIdAndTypeAndTenantId(
            @Param("userId") Long userId, @Param("mfaType") String mfaType, @Param("tenantId") String tenantId);

    /**
     * 查询用户已启用的 MFA 配置
     *
     * @param userId 用户 ID
     * @param tenantId 租户 ID
     * @return 已启用的 MFA 配置列表
     */
    List<MfaConfigPO> selectEnabledByUserIdAndTenantId(
            @Param("userId") Long userId, @Param("tenantId") String tenantId);

    /**
     * 查询用户必需的 MFA 配置
     *
     * @param userId 用户 ID
     * @param tenantId 租户 ID
     * @return 必需的 MFA 配置列表
     */
    List<MfaConfigPO> selectRequiredByUserIdAndTenantId(
            @Param("userId") Long userId, @Param("tenantId") String tenantId);

    /**
     * 根据用户 ID 和租户 ID 删除所有 MFA 配置
     *
     * @param userId 用户 ID
     * @param tenantId 租户 ID
     * @return 影响行数
     */
    int deleteByUserIdAndTenantId(@Param("userId") Long userId, @Param("tenantId") String tenantId);

    /**
     * 检查用户是否已配置指定类型的 MFA
     *
     * @param userId 用户 ID
     * @param mfaType MFA 类型
     * @param tenantId 租户 ID
     * @return 存在返回 1，否则返回 0
     */
    int existsByUserIdAndTypeAndTenantId(
            @Param("userId") Long userId, @Param("mfaType") String mfaType, @Param("tenantId") String tenantId);
}

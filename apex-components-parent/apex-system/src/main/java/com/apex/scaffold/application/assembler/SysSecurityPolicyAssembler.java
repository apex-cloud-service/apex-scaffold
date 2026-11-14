package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.admin.SecurityPolicyDTO;
import com.apex.scaffold.application.dto.admin.SecurityPolicyRequest;
import com.apex.scaffold.domain.model.entity.SysSecurityPolicyEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * 安全策略配置对象转换器
 *
 * <p>负责安全策略配置领域实体与应用层 DTO 之间的双向转换，使用 MapStruct 自动生成转换代码。
 * 支持请求对象到实体的转换、实体到响应 DTO 的转换。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>Request → Entity 转换：将请求对象转换为领域实体</li>
 *   <li>Entity → DTO 转换：将领域实体转换为应用层 DTO</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>passwordRequireUppercase → passwordRequireUpper：密码大写字母要求（Boolean → Integer 0/1）</li>
 *   <li>passwordRequireLowercase → passwordRequireLower：密码小写字母要求（Boolean → Integer 0/1）</li>
 *   <li>passwordRequireDigit：密码数字要求（Boolean → Integer 0/1）</li>
 *   <li>passwordRequireSpecialChar → passwordRequireSpecial：密码特殊字符要求（Boolean → Integer 0/1）</li>
 *   <li>passwordExpirationDays → passwordExpiryDays：密码过期天数</li>
 *   <li>loginFailureLockThreshold → maxLoginAttempts：登录失败锁定阈值</li>
 *   <li>accountLockDuration → lockoutDuration：账户锁定时长</li>
 *   <li>ipWhitelist：IP 白名单（JSON 字符串）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SysSecurityPolicyAssembler {

    /**
     * 将请求对象转换为领域实体
     *
     * <p>用于更新安全策略配置时，将前端传入的请求参数转换为领域实体。
     * 租户 ID、审计字段等由服务层单独设置。
     *
     * @param request 安全策略配置请求对象
     * @return 安全策略配置领域实体
     */
    @Mapping(
            target = "passwordRequireUpper",
            expression =
                    "java(request.getPasswordRequireUppercase() != null ? (request.getPasswordRequireUppercase() ? 1 : 0) : null)")
    @Mapping(
            target = "passwordRequireLower",
            expression =
                    "java(request.getPasswordRequireLowercase() != null ? (request.getPasswordRequireLowercase() ? 1 : 0) : null)")
    @Mapping(
            target = "passwordRequireDigit",
            expression =
                    "java(request.getPasswordRequireDigit() != null ? (request.getPasswordRequireDigit() ? 1 : 0) : null)")
    @Mapping(
            target = "passwordRequireSpecial",
            expression =
                    "java(request.getPasswordRequireSpecialChar() != null ? (request.getPasswordRequireSpecialChar() ? 1 : 0) : null)")
    @Mapping(target = "passwordExpiryDays", source = "passwordExpirationDays")
    @Mapping(target = "maxLoginAttempts", source = "loginFailureLockThreshold")
    @Mapping(target = "lockoutDuration", source = "accountLockDuration")
    @Mapping(target = "enabled", expression = "java(1)")
    SysSecurityPolicyEntity toEntity(SecurityPolicyRequest request);

    /**
     * 将领域实体转换为响应 DTO
     *
     * <p>用于查询安全策略配置时，将领域实体转换为前端可用的响应对象。
     *
     * @param entity 安全策略配置领域实体
     * @return 安全策略配置响应 DTO
     */
    @Mapping(
            target = "passwordRequireUppercase",
            expression = "java(entity.getPasswordRequireUpper() != null && entity.getPasswordRequireUpper() == 1)")
    @Mapping(
            target = "passwordRequireLowercase",
            expression = "java(entity.getPasswordRequireLower() != null && entity.getPasswordRequireLower() == 1)")
    @Mapping(
            target = "passwordRequireDigit",
            expression = "java(entity.getPasswordRequireDigit() != null && entity.getPasswordRequireDigit() == 1)")
    @Mapping(
            target = "passwordRequireSpecialChar",
            expression = "java(entity.getPasswordRequireSpecial() != null && entity.getPasswordRequireSpecial() == 1)")
    @Mapping(target = "passwordExpirationDays", source = "passwordExpiryDays")
    @Mapping(target = "loginFailureLockThreshold", source = "maxLoginAttempts")
    @Mapping(target = "accountLockDuration", source = "lockoutDuration")
    SecurityPolicyDTO toDTO(SysSecurityPolicyEntity entity);
}

package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.admin.JwtConfigDTO;
import com.apex.scaffold.application.dto.admin.JwtConfigRequest;
import com.apex.scaffold.domain.model.entity.SysJwtConfigEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * JWT 配置对象转换器
 *
 * <p>负责 JWT 配置领域实体与应用层 DTO 之间的双向转换，使用 MapStruct 自动生成转换代码。
 * 支持请求对象到实体的转换、实体到响应 DTO 的转换。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>Request → Entity 转换：将请求对象转换为领域实体</li>
 *   <li>Entity → DTO 转换：将领域实体转换为应用层 DTO，密钥字段自动掩码为 "******"</li>
 * </ul>
 *
 * <h2>特殊处理规则</h2>
 * <ul>
 *   <li><strong>密钥掩码</strong>：Entity → DTO 时，secretKey/privateKey 字段固定返回 "******"，不暴露真实密钥</li>
 *   <li><strong>字段映射</strong>：secret → secretKey，accessTokenExpiration → accessTokenTtl，refreshTokenExpiration → refreshTokenTtl</li>
 *   <li><strong>忽略字段</strong>：Request → Entity 时，id、tenantId、createTime、updateTime、deleted、lockVersion 等字段由系统自动管理</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SysJwtConfigAssembler {

    /**
     * 将请求对象转换为领域实体
     *
     * <p>用于更新 JWT 配置时，将前端传入的请求参数转换为领域实体。
     * 租户 ID、审计字段等由服务层单独设置。
     *
     * @param request JWT 配置请求对象
     * @return JWT 配置领域实体
     */
    @Mapping(target = "secretKey", source = "secret")
    @Mapping(target = "accessTokenTtl", source = "accessTokenExpiration")
    @Mapping(target = "refreshTokenTtl", source = "refreshTokenExpiration")
    SysJwtConfigEntity toEntity(JwtConfigRequest request);

    /**
     * 将领域实体转换为响应 DTO
     *
     * <p>用于查询 JWT 配置时，将领域实体转换为前端可用的响应对象。
     * secretKey 和 privateKey 字段自动掩码为 "******"，不暴露真实密钥。
     *
     * @param entity JWT 配置领域实体
     * @return JWT 配置响应 DTO，密钥字段为掩码
     */
    @Mapping(target = "secret", expression = "java(entity.getSecretKey() != null ? \"******\" : null)")
    @Mapping(target = "accessTokenExpiration", source = "accessTokenTtl")
    @Mapping(target = "refreshTokenExpiration", source = "refreshTokenTtl")
    JwtConfigDTO toDTO(SysJwtConfigEntity entity);
}

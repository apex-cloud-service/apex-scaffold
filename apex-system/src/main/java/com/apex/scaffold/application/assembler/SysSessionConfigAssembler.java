package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.admin.SessionConfigDTO;
import com.apex.scaffold.application.dto.admin.SessionConfigRequest;
import com.apex.scaffold.domain.model.entity.SysSessionConfigEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * 会话配置对象转换器
 *
 * <p>负责会话配置领域实体与应用层 DTO 之间的双向转换，使用 MapStruct 自动生成转换代码。
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
 *   <li>maxConcurrentSessions → maxSessions：最大并发会话数</li>
 *   <li>preventLogin → concurrentControl：并发控制策略（preventLogin=true 对应 concurrentControl=0，false 对应 1）</li>
 *   <li>enableRememberMe → rememberMeEnabled：记住我功能开关（Boolean → Integer 0/1）</li>
 *   <li>rememberMeDuration → rememberMeTtl：记住我有效期</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SysSessionConfigAssembler {

    /**
     * 将请求对象转换为领域实体
     *
     * <p>用于更新会话配置时，将前端传入的请求参数转换为领域实体。
     * 租户 ID、审计字段等由服务层单独设置。
     *
     * @param request 会话配置请求对象
     * @return 会话配置领域实体
     */
    @Mapping(target = "maxSessions", source = "maxConcurrentSessions")
    @Mapping(
            target = "concurrentControl",
            expression = "java(request.getPreventLogin() != null ? (request.getPreventLogin() ? 0 : 1) : null)")
    @Mapping(
            target = "rememberMeEnabled",
            expression = "java(request.getEnableRememberMe() != null ? (request.getEnableRememberMe() ? 1 : 0) : null)")
    @Mapping(target = "rememberMeTtl", source = "rememberMeDuration")
    @Mapping(target = "enabled", expression = "java(1)")
    SysSessionConfigEntity toEntity(SessionConfigRequest request);

    /**
     * 将领域实体转换为响应 DTO
     *
     * <p>用于查询会话配置时，将领域实体转换为前端可用的响应对象。
     *
     * @param entity 会话配置领域实体
     * @return 会话配置响应 DTO
     */
    @Mapping(target = "maxConcurrentSessions", source = "maxSessions")
    @Mapping(
            target = "preventLogin",
            expression = "java(entity.getConcurrentControl() != null && entity.getConcurrentControl() == 0)")
    @Mapping(
            target = "enableRememberMe",
            expression = "java(entity.getRememberMeEnabled() != null && entity.getRememberMeEnabled() == 1)")
    @Mapping(target = "rememberMeDuration", source = "rememberMeTtl")
    SessionConfigDTO toDTO(SysSessionConfigEntity entity);
}

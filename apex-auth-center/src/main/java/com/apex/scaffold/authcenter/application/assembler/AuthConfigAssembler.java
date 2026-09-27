package com.apex.scaffold.authcenter.application.assembler;

import com.apex.scaffold.authcenter.application.dto.AuthConfigDTO;
import com.apex.scaffold.authcenter.application.dto.CreateAuthConfigRequest;
import com.apex.scaffold.authcenter.application.dto.UpdateAuthConfigRequest;
import com.apex.scaffold.authcenter.domain.model.entity.AuthConfigEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import reactor.core.publisher.Mono;

/**
 * 认证配置Assembler
 *
 * <p>使用MapStruct实现AuthConfigEntity与DTO之间的转换。
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AuthConfigAssembler {

    /**
     * 实体转DTO
     *
     * @param entity 认证配置实体
     * @return 认证配置DTO
     */
    AuthConfigDTO toDto(AuthConfigEntity entity);

    /**
     * DTO转实体
     *
     * @param dto 认证配置DTO
     * @return 认证配置实体
     */
    AuthConfigEntity toEntity(AuthConfigDTO dto);

    /**
     * 创建请求转实体
     *
     * @param request 创建请求
     * @return 认证配置实体
     */
    AuthConfigEntity toEntity(CreateAuthConfigRequest request);

    /**
     * 更新请求转实体
     *
     * @param request 更新请求
     * @return 认证配置实体
     */
    AuthConfigEntity toEntity(UpdateAuthConfigRequest request);

    /**
     * 实体转DTO（响应式）
     *
     * @param entity 认证配置实体
     * @return 认证配置DTO的Mono包装
     */
    default Mono<AuthConfigDTO> toDto(Mono<AuthConfigEntity> entity) {
        return entity.map(this::toDto);
    }
}

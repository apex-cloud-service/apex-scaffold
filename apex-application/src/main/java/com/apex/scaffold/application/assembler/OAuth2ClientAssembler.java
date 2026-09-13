package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.OAuth2ClientResponse;
import com.apex.scaffold.domain.model.entity.OAuth2RegisteredClientEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * OAuth2 客户端对象转换器。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OAuth2ClientAssembler {

    /**
     * 实体转换为响应 DTO。
     *
     * @param entity OAuth2 客户端实体
     * @return OAuth2 客户端响应 DTO
     */
    @Mapping(target = "isPublicClient", source = "publicClient")
    OAuth2ClientResponse toResponse(OAuth2RegisteredClientEntity entity);

    /**
     * 实体列表转换为响应 DTO 列表。
     *
     * @param entities OAuth2 客户端实体列表
     * @return OAuth2 客户端响应 DTO 列表
     */
    List<OAuth2ClientResponse> toResponseList(List<OAuth2RegisteredClientEntity> entities);
}

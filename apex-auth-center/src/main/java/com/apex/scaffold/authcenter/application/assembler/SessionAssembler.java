package com.apex.scaffold.authcenter.application.assembler;

import com.apex.scaffold.authcenter.application.dto.SessionDTO;
import com.apex.scaffold.authcenter.domain.model.entity.SessionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 会话实体与DTO转换器
 *
 * <p>使用MapStruct自动生成实体与DTO之间的转换代码，简化手动转换的重复劳动。
 *
 * <p>配置说明：
 * <ul>
 *   <li>componentModel = "spring"：注册为Spring Bean，支持依赖注入</li>
 *   <li>unmappedTargetPolicy = IGNORE：忽略未映射的目标属性，避免编译警告</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SessionAssembler {

    /**
     * 将会话实体转换为DTO
     *
     * @param entity 会话实体
     * @return 会话DTO
     */
    SessionDTO toDto(SessionEntity entity);

    /**
     * 将会话DTO转换为实体
     *
     * @param dto 会话DTO
     * @return 会话实体
     */
    SessionEntity toEntity(SessionDTO dto);
}

package com.apex.scaffold.authcenter.application.assembler;

import com.apex.scaffold.authcenter.application.dto.PermissionDTO;
import com.apex.scaffold.authcenter.domain.model.entity.PermissionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 权限实体与DTO转换器
 *
 * <p>使用MapStruct自动生成实体与DTO之间的转换代码，简化手动转换的重复劳动。
 *
 * <p>配置说明：
 * <ul>
 *   <li>componentModel = "spring"：注册为Spring Bean，支持依赖注入</li>
 *   <li>unmappedTargetPolicy = IGNORE：忽略未映射的目标属性，避免编译警告</li>
 * </ul>
 *
 * <p>说明（2026-09-30）：本接口不再显式指定 {@code implementationName}。原因与
 * {@code AuditLogAssembler} 一致：{@code apex-interfaces} 主应用已通过
 * {@code @ComponentScan} 排除 {@code authcenter} 包，两应用生成类互不干扰，
 * 此前为规避潜在同名冲突添加的过渡性 {@code implementationName} 属性已回退。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PermissionAssembler {

    /**
     * 将权限实体转换为DTO
     *
     * @param entity 权限实体
     * @return 权限DTO
     */
    PermissionDTO toDto(PermissionEntity entity);

    /**
     * 将权限DTO转换为实体
     *
     * @param dto 权限DTO
     * @return 权限实体
     */
    PermissionEntity toEntity(PermissionDTO dto);

    /**
     * Integer转Boolean映射方法
     *
     * @param value Integer值（0或1）
     * @return Boolean值
     */
    default Boolean map(Integer value) {
        return value != null && value == 1;
    }

    /**
     * Boolean转Integer映射方法
     *
     * @param value Boolean值
     * @return Integer值（0或1）
     */
    default Integer map(Boolean value) {
        return value != null && value ? 1 : 0;
    }
}

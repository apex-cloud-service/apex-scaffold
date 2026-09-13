package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.admin.LoginLogDTO;
import com.apex.scaffold.domain.model.entity.LoginLogEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 登录日志对象转换器
 *
 * <p>用于在登录日志相关的 DTO 与领域实体之间进行转换。
 * 基于 MapStruct 生成实现类。
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface LoginLogAssembler {
    /**
     * 将 LoginLogEntity 转换为 LoginLogDTO
     *
     * @param entity 登录日志实体
     * @return 登录日志 DTO
     */
    LoginLogDTO toDTO(LoginLogEntity entity);

    /**
     * 将 LoginLogEntity 列表转换为 LoginLogDTO 列表
     *
     * @param entities 登录日志实体列表
     * @return 登录日志 DTO 列表
     */
    List<LoginLogDTO> toDTOList(List<LoginLogEntity> entities);
}

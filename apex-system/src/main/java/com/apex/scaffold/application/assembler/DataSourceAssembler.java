package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.admin.DataSourceResponse;
import com.apex.scaffold.domain.model.system.SysDatasource;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * 系统数据源实体与响应 DTO 之间的 MapStruct 转换器。
 *
 * <p>负责"数据库配置"模块的数据源领域实体到响应 DTO 的映射。审计时间字段
 * 命名不一致（实体 {@code createTime}/{@code updateTime}，DTO {@code createdAt}/
 * {@code updatedAt}），通过 {@code @Mapping} 显式指定。
 *
 * <h2>设计说明</h2>
 * <p>本转换器只做字段搬运，<strong>不负责脱敏</strong>。URL 与用户名的脱敏、
 * 连接池运行时状态（poolSize/activeConnections/status）由应用服务在转换后补充，
 * 确保密码字段永远不会进入响应 DTO。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DataSourceAssembler {

    /**
     * 将系统数据源实体转换为响应 DTO。
     *
     * @param entity 系统数据源实体
     * @return 数据源响应 DTO（未脱敏、未含运行时状态）
     */
    @Mapping(source = "createTime", target = "createdAt")
    @Mapping(source = "updateTime", target = "updatedAt")
    DataSourceResponse toResponse(SysDatasource entity);

    /**
     * 将系统数据源实体列表转换为响应 DTO 列表。
     *
     * @param entities 系统数据源实体列表
     * @return 数据源响应 DTO 列表
     */
    List<DataSourceResponse> toResponseList(List<SysDatasource> entities);
}

package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.admin.GenDatasourceDTO;
import com.apex.scaffold.application.dto.admin.GenDatasourceRequest;
import com.apex.scaffold.domain.model.entity.GenDatasourceEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * 代码生成数据源对象转换器
 *
 * <p>使用 MapStruct 自动生成代码生成器数据源相关的 DTO 与领域实体之间的转换逻辑。
 * 提供实体与 DTO 的双向转换、批量转换等功能。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>Entity → DTO 转换：将领域实体转换为应用层 DTO，密码字段自动掩码为 "******"</li>
 *   <li>Request → Entity 转换：将请求对象转换为领域实体</li>
 *   <li>批量转换：支持实体列表到 DTO 列表的批量转换</li>
 * </ul>
 *
 * <h2>特殊处理规则</h2>
 * <ul>
 *   <li><strong>密码掩码</strong>：Entity → DTO 时，password 字段固定返回 "******"，不暴露真实密码</li>
 *   <li><strong>忽略字段</strong>：Request → Entity 时，id、tenantId、createTime、updateTime、deleted、lockVersion 等字段由系统自动管理，不从请求中映射</li>
 * </ul>
 *
 * <h2>设计模式</h2>
 * <ul>
 *   <li>装配器模式（Assembler）：封装对象转换逻辑，隔离应用层与领域层的对象依赖</li>
 *   <li>MapStruct 编译期代码生成：避免运行时反射，提升性能</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see GenDatasourceDTO
 * @see GenDatasourceRequest
 * @see GenDatasourceEntity
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface GenDatasourceAssembler {

    /**
     * 将 GenDatasourceEntity 转换为 GenDatasourceDTO
     *
     * <p>将代码生成器数据源领域实体转换为应用层 DTO，用于 API 响应。
     * 转换时自动对密码字段进行掩码处理，固定返回 "******"，防止敏感信息泄露。
     *
     * <h3>字段映射规则</h3>
     * <ul>
     *   <li>id → id：主键 ID</li>
     *   <li>tenantId → tenantId：租户 ID</li>
     *   <li>name → name：数据源名称</li>
     *   <li>dbType → dbType：数据库类型</li>
     *   <li>jdbcUrl → jdbcUrl：JDBC 连接 URL</li>
     *   <li>username → username：数据库用户名</li>
     *   <li>password → "******"：密码字段固定返回掩码</li>
     *   <li>remark → remark：备注说明</li>
     *   <li>createTime → createTime：创建时间</li>
     *   <li>updateTime → updateTime：更新时间</li>
     * </ul>
     *
     * @param entity 代码生成器数据源领域实体
     * @return 代码生成器数据源 DTO，密码字段为掩码
     */
    @Mapping(target = "password", expression = "java(\"******\")")
    GenDatasourceDTO toDTO(GenDatasourceEntity entity);

    /**
     * 将 GenDatasourceRequest 转换为 GenDatasourceEntity
     *
     * <p>将创建或更新数据源的请求对象转换为领域实体。
     * 请求中的密码字段为明文，将在持久化时由基础设施层加密。
     *
     * <h3>字段映射规则</h3>
     * <ul>
     *   <li>name → name：数据源名称</li>
     *   <li>dbType → dbType：数据库类型（mysql/postgresql 等）</li>
     *   <li>jdbcUrl → jdbcUrl：JDBC 连接 URL</li>
     *   <li>username → username：数据库用户名</li>
     *   <li>password → password：数据库密码（明文，持久化时加密）</li>
     *   <li>remark → remark：备注说明</li>
     * </ul>
     *
     * <h3>忽略字段</h3>
     * <p>以下字段不从请求中映射，由系统自动管理：
     * <ul>
     *   <li>id：主键 ID，由数据库自动生成</li>
     *   <li>tenantId：租户 ID，从上下文中获取</li>
     *   <li>createTime：创建时间，由持久层自动填充</li>
     *   <li>updateTime：更新时间，由持久层自动填充</li>
     *   <li>deleted：逻辑删除标记，默认为 0</li>
     *   <li>lockVersion：乐观锁版本号，由持久层管理</li>
     * </ul>
     *
     * @param request 代码生成器数据源请求对象
     * @return 代码生成器数据源领域实体
     */
    GenDatasourceEntity toEntity(GenDatasourceRequest request);

    /**
     * 将 GenDatasourceEntity 列表批量转换为 GenDatasourceDTO 列表
     *
     * <p>批量将代码生成器数据源领域实体列表转换为 DTO 列表，用于列表查询接口的响应。
     * 每个实体的密码字段都会被掩码为 "******"。
     *
     * <h3>使用场景</h3>
     * <ul>
     *   <li>数据源列表查询</li>
     *   <li>分页查询结果转换</li>
     *   <li>批量数据导出</li>
     * </ul>
     *
     * @param entities 代码生成器数据源领域实体列表
     * @return 代码生成器数据源 DTO 列表，所有密码字段为掩码
     */
    List<GenDatasourceDTO> toDTOList(List<GenDatasourceEntity> entities);
}

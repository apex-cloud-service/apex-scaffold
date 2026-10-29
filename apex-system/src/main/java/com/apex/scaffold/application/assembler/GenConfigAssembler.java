package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.admin.GenConfigDTO;
import com.apex.scaffold.application.dto.admin.GenConfigRequest;
import com.apex.scaffold.domain.model.entity.GenConfigEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 代码生成配置对象转换器
 *
 * <p>使用 MapStruct 自动生成代码生成器配置相关的 DTO 与领域实体之间的转换逻辑。
 * 提供实体与 DTO 的双向转换、批量转换等功能。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>Entity → DTO 转换：将领域实体转换为应用层 DTO，用于 API 响应</li>
 *   <li>Request → Entity 转换：将请求对象转换为领域实体</li>
 *   <li>批量转换：支持实体列表到 DTO 列表的批量转换</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li><strong>关联数据源名称</strong>：DTO 中包含 datasourceName 字段，用于前端展示，需要在 Service 层通过 datasourceId 查询并设置</li>
 *   <li><strong>忽略字段</strong>：Request → Entity 时，id、tenantId、datasourceName、createTime、updateTime、deleted、lockVersion 等字段由系统自动管理，不从请求中映射</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>装配器模式（Assembler）：封装对象转换逻辑，隔离应用层与领域层的对象依赖</li>
 *   <li>MapStruct 编译期代码生成：避免运行时反射，提升性能</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see GenConfigDTO
 * @see GenConfigRequest
 * @see GenConfigEntity
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface GenConfigAssembler {

    /**
     * 将 GenConfigEntity 转换为 GenConfigDTO
     *
     * <p>将代码生成器配置领域实体转换为应用层 DTO，用于 API 响应。
     *
     * <p><strong>字段映射规则</strong></p>
     * <ul>
     *   <li>id → id：主键 ID</li>
     *   <li>tenantId → tenantId：租户 ID</li>
     *   <li>datasourceId → datasourceId：数据源 ID</li>
     *   <li>datasourceName → datasourceName：数据源名称（需要在 Service 层额外填充）</li>
     *   <li>tableName → tableName：表名</li>
     *   <li>entityName → entityName：实体类名</li>
     *   <li>packageName → packageName：包路径</li>
     *   <li>moduleName → moduleName：模块名</li>
     *   <li>author → author：作者</li>
     *   <li>tablePrefix → tablePrefix：表前缀（生成时移除）</li>
     *   <li>outputPath → outputPath：输出路径</li>
     *   <li>remark → remark：备注说明</li>
     *   <li>createTime → createTime：创建时间</li>
     *   <li>updateTime → updateTime：更新时间</li>
     * </ul>
     *
     * <p><strong>关联数据处理</strong></p>
     * <p>datasourceName 字段不在实体中存储，需要在 Service 层通过 datasourceId 查询数据源表获取名称后设置。
     *
     * @param entity 代码生成器配置领域实体
     * @return 代码生成器配置 DTO
     */
    GenConfigDTO toDTO(GenConfigEntity entity);

    /**
     * 将 GenConfigRequest 转换为 GenConfigEntity
     *
     * <p>将创建或更新配置的请求对象转换为领域实体。
     *
     * <p><strong>字段映射规则</strong></p>
     * <ul>
     *   <li>datasourceId → datasourceId：数据源 ID</li>
     *   <li>tableName → tableName：表名</li>
     *   <li>entityName → entityName：实体类名</li>
     *   <li>packageName → packageName：包路径</li>
     *   <li>moduleName → moduleName：模块名</li>
     *   <li>author → author：作者</li>
     *   <li>tablePrefix → tablePrefix：表前缀（生成时移除）</li>
     *   <li>outputPath → outputPath：输出路径</li>
     *   <li>remark → remark：备注说明</li>
     * </ul>
     *
     * <p><strong>忽略字段</strong></p>
     * <p>以下字段不从请求中映射，由系统自动管理：
     * <ul>
     *   <li>id：主键 ID，由数据库自动生成</li>
     *   <li>tenantId：租户 ID，从上下文中获取</li>
     *   <li>datasourceName：数据源名称，仅用于 DTO 展示，不持久化</li>
     *   <li>createTime：创建时间，由持久层自动填充</li>
     *   <li>updateTime：更新时间，由持久层自动填充</li>
     *   <li>deleted：逻辑删除标记，默认为 0</li>
     *   <li>lockVersion：乐观锁版本号，由持久层管理</li>
     * </ul>
     *
     * @param request 代码生成器配置请求对象
     * @return 代码生成器配置领域实体
     */
    GenConfigEntity toEntity(GenConfigRequest request);

    /**
     * 将 GenConfigEntity 列表批量转换为 GenConfigDTO 列表
     *
     * <p>批量将代码生成器配置领域实体列表转换为 DTO 列表，用于列表查询接口的响应。
     *
     * <p><strong>使用场景</strong></p>
     * <ul>
     *   <li>配置列表查询</li>
     *   <li>分页查询结果转换</li>
     *   <li>批量数据导出</li>
     * </ul>
     *
     * <p><strong>关联数据处理</strong></p>
     * <p>如果需要填充 datasourceName 字段，应在 Service 层批量查询数据源信息后设置。
     *
     * @param entities 代码生成器配置领域实体列表
     * @return 代码生成器配置 DTO 列表
     */
    List<GenConfigDTO> toDTOList(List<GenConfigEntity> entities);
}

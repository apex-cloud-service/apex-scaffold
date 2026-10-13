package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.admin.GenTemplateDTO;
import com.apex.scaffold.application.dto.admin.GenTemplateRequest;
import com.apex.scaffold.domain.model.entity.GenTemplateEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 代码生成模板对象转换器
 *
 * <p>使用 MapStruct 自动生成代码生成器模板相关的 DTO 与领域实体之间的转换逻辑。
 * 提供实体与 DTO 的双向转换、批量转换等功能。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>Entity → DTO 转换：将领域实体转换为应用层 DTO，用于 API 响应</li>
 *   <li>Request → Entity 转换：将请求对象转换为领域实体</li>
 *   <li>批量转换：支持实体列表到 DTO 列表的批量转换</li>
 * </ul>
 *
 * <h2>特殊处理规则</h2>
 * <ul>
 *   <li><strong>模板内容</strong>：支持 FreeMarker 语法的模板内容，转换时原样保留</li>
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
 * @see GenTemplateDTO
 * @see GenTemplateRequest
 * @see GenTemplateEntity
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface GenTemplateAssembler {

    /**
     * 将 GenTemplateEntity 转换为 GenTemplateDTO
     *
     * <p>将代码生成器模板领域实体转换为应用层 DTO，用于 API 响应。
     *
     * <h3>字段映射规则</h3>
     * <ul>
     *   <li>id → id：主键 ID</li>
     *   <li>tenantId → tenantId：租户 ID</li>
     *   <li>templateName → templateName：模板名称</li>
     *   <li>templateType → templateType：模板类型（entity/mapper/service 等）</li>
     *   <li>templateContent → templateContent：模板内容（FreeMarker 语法）</li>
     *   <li>outputFileName → outputFileName：输出文件名模板</li>
     *   <li>outputSubPath → outputSubPath：输出子路径</li>
     *   <li>remark → remark：备注说明</li>
     *   <li>createTime → createTime：创建时间</li>
     *   <li>updateTime → updateTime：更新时间</li>
     * </ul>
     *
     * <h3>使用场景</h3>
     * <ul>
     *   <li>模板详情查询</li>
     *   <li>模板编辑回显</li>
     *   <li>模板列表展示</li>
     * </ul>
     *
     * @param entity 代码生成器模板领域实体
     * @return 代码生成器模板 DTO
     */
    GenTemplateDTO toDTO(GenTemplateEntity entity);

    /**
     * 将 GenTemplateRequest 转换为 GenTemplateEntity
     *
     * <p>将创建或更新模板的请求对象转换为领域实体。
     *
     * <h3>字段映射规则</h3>
     * <ul>
     *   <li>templateName → templateName：模板名称</li>
     *   <li>templateType → templateType：模板类型（entity/mapper/service/controller 等）</li>
     *   <li>templateContent → templateContent：模板内容（FreeMarker 语法）</li>
     *   <li>outputFileName → outputFileName：输出文件名模板（如 ${entityName}Entity.java）</li>
     *   <li>outputSubPath → outputSubPath：输出子路径（如 domain/model/entity）</li>
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
     * <h3>模板内容格式</h3>
     * <p>templateContent 字段支持 FreeMarker 语法，可以使用以下变量：
     * <ul>
     *   <li>${entityName}：实体类名</li>
     *   <li>${packageName}：包路径</li>
     *   <li>${tableName}：表名</li>
     *   <li>${columns}：列信息列表</li>
     *   <li>${author}：作者</li>
     * </ul>
     *
     * @param request 代码生成器模板请求对象
     * @return 代码生成器模板领域实体
     */
    GenTemplateEntity toEntity(GenTemplateRequest request);

    /**
     * 将 GenTemplateEntity 列表批量转换为 GenTemplateDTO 列表
     *
     * <p>批量将代码生成器模板领域实体列表转换为 DTO 列表，用于列表查询接口的响应。
     *
     * <h3>使用场景</h3>
     * <ul>
     *   <li>模板列表查询</li>
     *   <li>分页查询结果转换</li>
     *   <li>批量数据导出</li>
     *   <li>模板选择下拉框</li>
     * </ul>
     *
     * @param entities 代码生成器模板领域实体列表
     * @return 代码生成器模板 DTO 列表
     */
    List<GenTemplateDTO> toDTOList(List<GenTemplateEntity> entities);
}

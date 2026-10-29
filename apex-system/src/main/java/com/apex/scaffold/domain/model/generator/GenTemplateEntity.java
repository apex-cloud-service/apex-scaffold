package com.apex.scaffold.domain.model.generator;

import com.apex.scaffold.domain.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 代码模板实体（代码生成器领域模型）
 *
 * <p>定义代码生成的模板内容和模板元数据。模板使用 FreeMarker 语法，
 * 通过变量替换生成目标代码文件（Entity、Mapper、Service、Controller 等）。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>存储模板内容：FreeMarker 模板代码</li>
 *   <li>标识模板类型：entity、mapper、service、controller、xml 等</li>
 *   <li>定义文件后缀：生成文件的扩展名，如 .java、.xml、.sql</li>
 *   <li>支持内置和自定义模板：内置模板由系统提供，自定义模板由用户创建</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>常见模板类型及其用途：
 * <ul>
 *   <li><strong>entity</strong>：生成领域实体类（Entity），对应数据库表</li>
 *   <li><strong>po</strong>：生成持久化对象（PO），MyBatis-Plus 映射对象</li>
 *   <li><strong>mapper</strong>：生成 MyBatis Mapper 接口</li>
 *   <li><strong>mapper-xml</strong>：生成 MyBatis Mapper XML 文件</li>
 *   <li><strong>repository</strong>：生成仓储接口（Repository）</li>
 *   <li><strong>repository-impl</strong>：生成仓储实现类（RepositoryImpl）</li>
 *   <li><strong>service</strong>：生成应用服务类（Service）</li>
 *   <li><strong>controller</strong>：生成控制器或 Handler</li>
 *   <li><strong>dto</strong>：生成数据传输对象（DTO）</li>
 *   <li><strong>converter</strong>：生成对象转换器（Converter）</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>模板中可使用的常见变量（由代码生成器注入）：
 * <ul>
 *   <li><code>${packageName}</code>：包名</li>
 *   <li><code>${moduleName}</code>：模块名</li>
 *   <li><code>${className}</code>：类名（驼峰命名）</li>
 *   <li><code>${tableName}</code>：表名（数据库物理表名）</li>
 *   <li><code>${tableComment}</code>：表注释</li>
 *   <li><code>${author}</code>：作者名</li>
 *   <li><code>${fields}</code>：字段列表（List&lt;ColumnType&gt;）</li>
 *   <li><code>${primaryKey}</code>：主键字段信息</li>
 *   <li><code>${datetime}</code>：生成时间</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li><strong>内置模板（isBuiltin=1）</strong>：系统预置的标准模板，不可修改和删除，
 *       升级时可能被覆盖，适用于标准化代码生成</li>
 *   <li><strong>自定义模板（isBuiltin=0）</strong>：用户创建的模板，可自由修改和删除，
 *       用于定制化代码生成场景</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ol>
 *   <li>创建：管理员或用户创建模板，编写 FreeMarker 内容</li>
 *   <li>使用：在生成配置中引用模板ID，执行代码生成</li>
 *   <li>修改：调整模板内容以适应新的生成需求（仅自定义模板）</li>
 *   <li>删除：逻辑删除（deleted=1），历史生成记录中仍保留引用</li>
 * </ol>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>多对多：多个生成配置（{@code GenConfigEntity}）可使用同一个模板</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see GenConfigEntity
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class GenTemplateEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    public GenTemplateEntity() {
        super();
    }

    private String templateName;

    private String templateType;

    private String fileSuffix;

    private String templatePath;

    private String templateContent;

    private String description;

    private Integer isBuiltin;

    private String tenantId;
}

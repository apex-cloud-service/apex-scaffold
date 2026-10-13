package com.apex.scaffold.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 代码生成器模板持久化对象。
 *
 * <p>对应数据库表 {@code gen_template},存储代码生成使用的 FreeMarker 模板内容。
 * 模板定义了生成代码的结构和格式,支持内置模板和用户自定义模板。
 *
 * <h3>主要字段</h3>
 * <ul>
 *   <li>{@link #templateName} - 模板名称,便于识别和管理</li>
 *   <li>{@link #templateType} - 模板类型(entity/mapper/service/controller/vo/dto等)</li>
 *   <li>{@link #fileSuffix} - 生成文件后缀(如 .java、.xml、.ts)</li>
 *   <li>{@link #templatePath} - 模板文件相对路径(用于输出目录结构)</li>
 *   <li>{@link #templateContent} - 模板内容(FreeMarker 语法)</li>
 *   <li>{@link #description} - 模板描述</li>
 *   <li>{@link #isBuiltin} - 是否内置模板(0-自定义,1-系统内置)</li>
 *   <li>{@link #tenantId} - 租户标识</li>
 * </ul>
 *
 * <h3>模板类型枚举</h3>
 * <ul>
 *   <li>{@code entity} - 领域实体类</li>
 *   <li>{@code po} - 持久化对象</li>
 *   <li>{@code mapper} - MyBatis Mapper 接口</li>
 *   <li>{@code mapper_xml} - MyBatis XML 映射文件</li>
 *   <li>{@code repository} - 仓储接口</li>
 *   <li>{@code repository_impl} - 仓储实现</li>
 *   <li>{@code service} - 应用服务</li>
 *   <li>{@code controller} - 控制器/Handler</li>
 *   <li>{@code vo} - 视图对象</li>
 *   <li>{@code dto} - 数据传输对象</li>
 *   <li>{@code request} - 请求参数对象</li>
 *   <li>{@code converter} - MapStruct 转换器</li>
 *   <li>{@code assembler} - 组装器</li>
 * </ul>
 *
 * <h3>模板变量</h3>
 * <p>FreeMarker 模板可使用以下变量:
 * <ul>
 *   <li>{@code ${packageName}} - 包名</li>
 *   <li>{@code ${moduleName}} - 模块名</li>
 *   <li>{@code ${className}} - 类名(首字母大写)</li>
 *   <li>{@code ${classNameLower}} - 类名(首字母小写)</li>
 *   <li>{@code ${tableName}} - 表名</li>
 *   <li>{@code ${tableComment}} - 表注释</li>
 *   <li>{@code ${author}} - 作者</li>
 *   <li>{@code ${date}} - 当前日期</li>
 *   <li>{@code ${columns}} - 列信息列表</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see com.apex.scaffold.domain.model.generator.GenTemplateEntity
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("gen_template")
public class GenTemplatePO extends BasePO {

    /**
     * 模板名称。
     *
     * <p>用于标识和显示的模板名称,建议使用清晰的命名,如:
     * <ul>
     *   <li>"DDD 领域实体模板"</li>
     *   <li>"MyBatis-Plus Mapper 模板"</li>
     *   <li>"Spring WebFlux Handler 模板"</li>
     * </ul>
     *
     * <p>长度限制 100 字符。
     */
    private String templateName;

    /**
     * 模板类型。
     *
     * <p>标识该模板用于生成哪种类型的代码文件,用于分类和筛选。
     * 常见类型包括:entity、mapper、service、controller、vo、dto、request 等。
     *
     * <p>模板类型与文件输出路径相关,例如:
     * <ul>
     *   <li>{@code entity} → domain/model/xxx</li>
     *   <li>{@code mapper} → infrastructure/persistence/mapper</li>
     *   <li>{@code service} → application/service</li>
     * </ul>
     *
     * <p>长度限制 50 字符。
     */
    private String templateType;

    /**
     * 生成文件后缀。
     *
     * <p>指定生成文件的扩展名,如:
     * <ul>
     *   <li>{@code .java} - Java 源文件</li>
     *   <li>{@code .xml} - MyBatis XML 映射文件</li>
     *   <li>{@code .ts} - TypeScript 文件</li>
     *   <li>{@code .vue} - Vue 组件文件</li>
     * </ul>
     *
     * <p>生成器会将类名与此后缀拼接,形成完整文件名(如 UserEntity.java)。
     *
     * <p>长度限制 20 字符。
     */
    private String fileSuffix;

    /**
     * 模板文件相对路径。
     *
     * <p>指定生成文件在输出目录下的子目录结构,相对于 {@code outputPath}。
     *
     * <p>路径示例:
     * <ul>
     *   <li>{@code domain/model/entity} - 领域实体目录</li>
     *   <li>{@code infrastructure/persistence/mapper} - Mapper 接口目录</li>
     *   <li>{@code application/service} - 应用服务目录</li>
     * </ul>
     *
     * <p>生成器会自动创建不存在的中间目录。
     *
     * <p>长度限制 200 字符。
     */
    private String templatePath;

    /**
     * 模板内容(FreeMarker 语法)。
     *
     * <p>完整的 FreeMarker 模板代码,使用 FreeMarker 语法编写,支持:
     * <ul>
     *   <li>变量插值: {@code ${variableName}}</li>
     *   <li>条件判断: {@code <#if condition>...</#if>}</li>
     *   <li>循环遍历: {@code <#list items as item>...</#list>}</li>
     *   <li>宏定义: {@code <#macro name>...</#macro>}</li>
     * </ul>
     *
     * <p>模板示例:
     * <pre>{@code
     * package ${packageName}.domain.model.${moduleName};
     *
     * import lombok.Data;
     *
     * /**
     *  * ${tableComment}实体
     *  *
     *  * @author ${author}
     *  * @since ${date}
     *  {@literal *}/
     * @Data
     * public class ${className}Entity {
     *
     *     <#list columns as column>
     *     /**
     *      * ${column.comment}
     *      {@literal *}/
     *     private ${column.javaType} ${column.javaField};
     *
     *     </#list>
     * }
     * }</pre>
     *
     * <p>类型为 LONGTEXT,支持存储大型复杂模板。
     */
    private String templateContent;

    /**
     * 模板描述。
     *
     * <p>详细说明模板的用途、适用场景和特点,便于用户选择合适的模板。
     *
     * <p>描述示例:
     * <ul>
     *   <li>"基于 DDD 领域驱动设计的实体模板,使用 Lombok 简化代码,包含审计字段"</li>
     *   <li>"MyBatis-Plus 增强 Mapper 模板,集成分页和条件构造器"</li>
     *   <li>"Spring WebFlux 响应式 Handler 模板,使用 Mono/Flux 返回值"</li>
     * </ul>
     *
     * <p>长度限制 500 字符。
     */
    private String description;

    /**
     * 是否内置模板。
     *
     * <p>区分系统内置模板和用户自定义模板:
     * <ul>
     *   <li>0 - 自定义模板:由用户创建和维护,可随意编辑和删除</li>
     *   <li>1 - 内置模板:系统提供的标准模板,建议设为只读或限制删除</li>
     * </ul>
     *
     * <p>内置模板通常在系统初始化时通过 Flyway 脚本或初始化程序预置,
     * 提供开箱即用的代码生成能力。
     */
    private Integer isBuiltin;

    /**
     * 租户标识。
     *
     * <p>实现多租户数据隔离:
     * <ul>
     *   <li>内置模板的 {@code tenantId} 通常为系统级标识(如 "system")</li>
     *   <li>自定义模板的 {@code tenantId} 为创建该模板的租户ID</li>
     * </ul>
     *
     * <p>查询模板时应同时返回系统内置模板和当前租户的自定义模板。
     *
     * <p>长度限制 64 字符。
     */
    private String tenantId;
}

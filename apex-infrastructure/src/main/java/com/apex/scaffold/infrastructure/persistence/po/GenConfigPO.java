package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 代码生成器生成配置持久化对象。
 *
 * <p>对应数据库表 {@code gen_config},存储代码生成的配置参数,包含目标表清单、
 * 包名结构、模块划分、模板选择和输出路径等信息。
 *
 * <h3>主要字段</h3>
 * <ul>
 *   <li>{@link #configName} - 配置名称,便于识别和管理</li>
 *   <li>{@link #datasourceId} - 关联的数据源ID,指定从哪个数据库读取表结构</li>
 *   <li>{@link #tableNames} - 待生成的表名列表,JSON 数组格式</li>
 *   <li>{@link #packageName} - Java 包名根路径(如 com.apex.scaffold)</li>
 *   <li>{@link #moduleName} - 模块名称(如 user、order),用于分层目录</li>
 *   <li>{@link #author} - 作者名,写入生成代码的 @author 标签</li>
 *   <li>{@link #tablePrefix} - 表前缀(如 sys_),生成类名时移除</li>
 *   <li>{@link #outputPath} - 代码输出路径(绝对路径)</li>
 *   <li>{@link #templateIds} - 使用的模板ID列表,JSON 数组</li>
 *   <li>{@link #extraConfig} - 扩展配置,JSON 格式,灵活存储其他参数</li>
 *   <li>{@link #tenantId} - 租户标识</li>
 * </ul>
 *
 * <h3>安全约束</h3>
 * <ul>
 *   <li>{@link #outputPath} 必须经过路径穿越校验,禁止包含 {@code ..} 或写入系统敏感目录</li>
 *   <li>支持的白名单路径前缀应配置在应用层 Service 中</li>
 * </ul>
 *
 * <h3>JSON 字段格式</h3>
 * <ul>
 *   <li>{@code tableNames}: ["sys_user", "sys_role"]</li>
 *   <li>{@code templateIds}: [1, 2, 3, 5]</li>
 *   <li>{@code extraConfig}: {"lombokBuilder": true, "swagger": true}</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see com.apex.scaffold.domain.model.generator.GenConfigEntity
 * @see GenDatasourcePO
 * @see GenTemplatePO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("gen_config")
public class GenConfigPO extends BasePO {

    /**
     * 配置名称。
     *
     * <p>用于标识该生成配置的唯一名称,建议使用有意义的名称如 "用户模块代码生成"、"订单管理-完整CRUD"。
     * 方便在历史记录中追溯和管理。
     *
     * <p>长度限制 100 字符。
     */
    private String configName;

    /**
     * 数据源ID。
     *
     * <p>外键关联 {@code gen_datasource.id},指定从哪个数据源读取表结构元数据。
     * 代码生成时会使用该数据源的连接信息建立临时连接,读取表和列的定义。
     *
     * <p>如果数据源被删除,相关的生成配置应级联处理(删除或禁用)。
     */
    private Long datasourceId;

    /**
     * 待生成的表名列表(JSON 数组格式)。
     *
     * <p>存储需要生成代码的数据库表名,支持多表批量生成。
     *
     * <p>JSON 格式示例:
     * <pre>{@code
     * ["sys_user", "sys_role", "sys_permission"]
     * }</pre>
     *
     * <p>生成器会遍历该数组,为每张表生成对应的 Entity、Mapper、Service、Controller 等文件。
     */
    private String tableNames;

    /**
     * Java 包名根路径。
     *
     * <p>生成代码的基础包名,遵循 Java 包命名规范(小写字母和点分隔),如:
     * <ul>
     *   <li>{@code com.apex.scaffold}</li>
     *   <li>{@code com.example.project}</li>
     * </ul>
     *
     * <p>生成的文件会在此基础上按分层结构创建子包:
     * <ul>
     *   <li>{@code <packageName>.domain.model.<moduleName>} - 实体类</li>
     *   <li>{@code <packageName>.infrastructure.persistence.mapper} - Mapper 接口</li>
     *   <li>{@code <packageName>.application.service} - Service 类</li>
     *   <li>{@code <packageName>.interfaces.controller} - Controller 类</li>
     * </ul>
     *
     * <p>长度限制 200 字符。
     */
    private String packageName;

    /**
     * 模块名称。
     *
     * <p>用于按业务领域划分子包和目录,建议使用小写英文单词,如:
     * <ul>
     *   <li>{@code user} - 用户管理模块</li>
     *   <li>{@code order} - 订单管理模块</li>
     *   <li>{@code product} - 商品管理模块</li>
     * </ul>
     *
     * <p>模块名会拼接到包名中,形成完整的类路径。
     *
     * <p>长度限制 100 字符。
     */
    private String moduleName;

    /**
     * 作者名。
     *
     * <p>生成代码的 JavaDoc {@code @author} 标签中使用,标识代码作者。
     * 默认值为 "Apex Team",可自定义为团队名称或个人姓名。
     *
     * <p>长度限制 50 字符。
     */
    private String author;

    /**
     * 表前缀(生成类名时移除)。
     *
     * <p>数据库表名通常带有前缀(如 {@code sys_}、{@code biz_}),生成 Java 类名时会移除该前缀:
     * <ul>
     *   <li>表名 {@code sys_user} + 前缀 {@code sys_} → 类名 {@code User}</li>
     *   <li>表名 {@code biz_order} + 前缀 {@code biz_} → 类名 {@code Order}</li>
     * </ul>
     *
     * <p>如果不需要移除前缀,此字段可为 {@code null}。
     *
     * <p>长度限制 50 字符。
     */
    private String tablePrefix;

    /**
     * 代码输出路径(绝对路径)。
     *
     * <p>生成的代码文件将写入该目录下,必须使用绝对路径,如:
     * <ul>
     *   <li>{@code /Users/work/apex-scaffold/output}</li>
     *   <li>{@code /tmp/apex-gen}</li>
     *   <li>{@code D:\\projects\\output}</li>
     * </ul>
     *
     * <p><strong>安全约束:</strong>
     * <ul>
     *   <li>禁止包含路径遍历符号 {@code ..},防止写入系统敏感目录</li>
     *   <li>应用层 Service 应维护路径白名单,仅允许写入特定目录</li>
     *   <li>生成前应校验目标目录的写权限</li>
     * </ul>
     *
     * <p>长度限制 500 字符。
     */
    private String outputPath;

    /**
     * 使用的模板ID列表(JSON 数组格式)。
     *
     * <p>指定代码生成时使用哪些模板,模板ID 关联 {@code gen_template.id}。
     * 支持选择性生成,如只生成 Entity 和 Mapper,不生成 Service 和 Controller。
     *
     * <p>JSON 格式示例:
     * <pre>{@code
     * [1, 2, 3, 5]
     * }</pre>
     *
     * <p>如果为 {@code null} 或空数组,则使用默认模板集合。
     */
    private String templateIds;

    /**
     * 扩展配置(JSON 格式)。
     *
     * <p>灵活存储其他配置参数,支持未来扩展而不修改表结构。
     *
     * <p>JSON 格式示例:
     * <pre>{@code
     * {
     *   "lombokBuilder": true,
     *   "swagger": true,
     *   "restful": true,
     *   "commentStyle": "javadoc"
     * }
     * }</pre>
     *
     * <p>应用层可根据需要解析这些参数,影响代码生成的细节。
     */
    private String extraConfig;

    /**
     * 租户标识。
     *
     * <p>实现多租户数据隔离,确保不同租户的生成配置相互独立。
     *
     * <p>长度限制 64 字符。
     */
    private String tenantId;
}

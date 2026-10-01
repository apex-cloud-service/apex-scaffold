package com.apex.scaffold.domain.model.generator;

import com.apex.scaffold.domain.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 生成配置实体（代码生成器领域模型）
 *
 * <p>封装代码生成的配置参数，包括目标数据源、表选择、包名、模块名、作者等信息。
 * 一个生成配置对应一次代码生成任务的全部参数。
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li>关联数据源：指定从哪个数据源读取表结构</li>
 *   <li>指定生成目标：选择要生成代码的表、包名、模块名</li>
 *   <li>定制生成规则：表前缀移除、作者名、模板选择</li>
 *   <li>配置输出路径：指定生成代码的文件系统路径</li>
 * </ul>
 *
 * <h2>配置项说明</h2>
 * <ul>
 *   <li><strong>datasourceId</strong>：关联的数据源ID，必填</li>
 *   <li><strong>tableNames</strong>：要生成代码的表名列表，JSON数组格式，如 ["user", "role"]</li>
 *   <li><strong>packageName</strong>：生成代码的基础包名，如 com.apex.scaffold</li>
 *   <li><strong>moduleName</strong>：模块名，用于子包划分，如 user、order</li>
 *   <li><strong>author</strong>：代码作者名，会出现在类注释中</li>
 *   <li><strong>tablePrefix</strong>：表前缀，生成类名时会移除，如 sys_user → User</li>
 *   <li><strong>outputPath</strong>：输出路径，生成的代码文件保存位置</li>
 *   <li><strong>templateIds</strong>：使用的模板ID列表，JSON数组格式</li>
 *   <li><strong>extraConfig</strong>：扩展配置，JSON格式，用于自定义生成规则</li>
 * </ul>
 *
 * <h2>安全约束</h2>
 * <ul>
 *   <li><strong>路径安全</strong>：{@link #outputPath} 必须进行路径穿越校验，
 *       禁止写入系统目录（如 /etc、/usr）或敏感路径，建议限制在项目目录内</li>
 *   <li><strong>租户隔离</strong>：配置只能访问同租户下的数据源</li>
 *   <li><strong>权限校验</strong>：执行代码生成前需要校验用户是否有对应权限</li>
 * </ul>
 *
 * <h2>生命周期</h2>
 * <ol>
 *   <li>创建：管理员配置生成参数</li>
 *   <li>执行：触发代码生成任务，生成历史记录到 {@code GenHistoryEntity}</li>
 *   <li>修改：调整配置参数后可重新执行生成</li>
 *   <li>删除：逻辑删除（deleted=1），关联的生成历史保留</li>
 * </ol>
 *
 * <h2>关联关系</h2>
 * <ul>
 *   <li>多对一：多个配置可引用同一个数据源（{@code GenDatasourceEntity}）</li>
 *   <li>一对多：一个配置可产生多次生成历史（{@code GenHistoryEntity}）</li>
 *   <li>多对多：一个配置可使用多个模板（{@code GenTemplateEntity}）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see GenDatasourceEntity
 * @see GenTemplateEntity
 * @see GenHistoryEntity
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class GenConfigEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    public GenConfigEntity() {
        super();
    }

    private String configName;

    private Long datasourceId;

    private String tableNames;

    private String packageName;

    private String moduleName;

    private String author;

    private String tablePrefix;

    private String outputPath;

    private String templateIds;

    private String extraConfig;

    private String tenantId;
}

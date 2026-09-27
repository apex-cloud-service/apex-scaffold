package com.apex.scaffold.domain.model.entity;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 代码生成器配置实体
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class GenConfigEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private String tenantId;
    private Long datasourceId;
    private String datasourceName;
    private String tableName;
    private String entityName;
    private String packageName;
    private String moduleName;
    private String author;
    private String tablePrefix;
    private String outputPath;
    private String remark;

    public GenConfigEntity() {
        super();
    }

    /**
     * 设置租户标识，赋值后刷新更新时间。
     *
     * @param tenantId 租户标识，用于多租户数据隔离
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
        touch();
    }

    /**
     * 设置数据源 ID，赋值后刷新更新时间。
     *
     * @param datasourceId 代码生成所使用的数据源 ID
     */
    public void setDatasourceId(Long datasourceId) {
        this.datasourceId = datasourceId;
        touch();
    }

    /**
     * 设置数据源名称，赋值后刷新更新时间。
     *
     * @param datasourceName 数据源显示名称
     */
    public void setDatasourceName(String datasourceName) {
        this.datasourceName = datasourceName;
        touch();
    }

    /**
     * 设置生成代码的目标表名，赋值后刷新更新时间。
     *
     * @param tableName 数据库表名
     */
    public void setTableName(String tableName) {
        this.tableName = tableName;
        touch();
    }

    /**
     * 设置生成的实体类名，赋值后刷新更新时间。
     *
     * @param entityName 实体类名称（不含包名）
     */
    public void setEntityName(String entityName) {
        this.entityName = entityName;
        touch();
    }

    /**
     * 设置生成代码的包名，赋值后刷新更新时间。
     *
     * @param packageName 生成代码所属的 Java 包名
     */
    public void setPackageName(String packageName) {
        this.packageName = packageName;
        touch();
    }

    /**
     * 设置生成代码的模块名，赋值后刷新更新时间。
     *
     * @param moduleName 所属业务模块名称
     */
    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
        touch();
    }

    /**
     * 设置生成代码的作者标注，赋值后刷新更新时间。
     *
     * @param author 写入代码注释中的作者名
     */
    public void setAuthor(String author) {
        this.author = author;
        touch();
    }

    /**
     * 设置表名前缀处理规则，赋值后刷新更新时间。
     *
     * @param tablePrefix 生成类名时需要移除的表名前缀
     */
    public void setTablePrefix(String tablePrefix) {
        this.tablePrefix = tablePrefix;
        touch();
    }

    /**
     * 设置代码输出路径，赋值后刷新更新时间。
     *
     * @param outputPath 生成文件的输出目录路径
     */
    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
        touch();
    }

    /**
     * 设置备注说明，赋值后刷新更新时间。
     *
     * @param remark 配置备注信息
     */
    public void setRemark(String remark) {
        this.remark = remark;
        touch();
    }
}

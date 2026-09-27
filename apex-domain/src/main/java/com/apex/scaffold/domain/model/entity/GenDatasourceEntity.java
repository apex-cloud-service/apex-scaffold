package com.apex.scaffold.domain.model.entity;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 代码生成器数据源实体
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class GenDatasourceEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private String tenantId;
    private String name;
    private String dbType;
    private String jdbcUrl;
    private String username;
    private String password;
    private String remark;

    public GenDatasourceEntity() {
        super();
    }

    /**
     * 设置租户 ID，并刷新实体更新时间。
     *
     * @param tenantId 数据源所属租户的唯一标识
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
        touch();
    }

    /**
     * 设置数据源名称，并刷新实体更新时间。
     *
     * @param name 数据源的业务名称，用于在代码生成器中展示和选择
     */
    public void setName(String name) {
        this.name = name;
        touch();
    }

    /**
     * 设置数据库类型，并刷新实体更新时间。
     *
     * @param dbType 数据库类型标识（如 MySQL、PostgreSQL）
     */
    public void setDbType(String dbType) {
        this.dbType = dbType;
        touch();
    }

    /**
     * 设置 JDBC 连接地址，并刷新实体更新时间。
     *
     * @param jdbcUrl 数据源的 JDBC 连接 URL，包含主机、端口和数据库名
     */
    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
        touch();
    }

    /**
     * 设置数据库用户名，并刷新实体更新时间。
     *
     * @param username 连接数据库使用的账号
     */
    public void setUsername(String username) {
        this.username = username;
        touch();
    }

    /**
     * 设置数据库密码，并刷新实体更新时间。
     *
     * @param password 连接数据库使用的密码，调用方需保证其脱敏存储与传输
     */
    public void setPassword(String password) {
        this.password = password;
        touch();
    }

    /**
     * 设置数据源备注信息，并刷新实体更新时间。
     *
     * @param remark 对该数据源的补充说明，可为空
     */
    public void setRemark(String remark) {
        this.remark = remark;
        touch();
    }
}

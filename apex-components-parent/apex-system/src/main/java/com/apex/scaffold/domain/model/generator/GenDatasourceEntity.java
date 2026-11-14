package com.apex.scaffold.domain.model.generator;

import com.apex.scaffold.domain.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 数据源配置实体（代码生成器领域模型）
 *
 * <p>表示代码生成器需要连接的目标数据库配置信息。数据源是代码生成的起点，
 * 生成器通过数据源配置连接数据库并读取表结构元数据。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>封装数据库连接参数：主机、端口、数据库名、认证信息</li>
 *   <li>标识数据库类型，用于选择正确的元数据查询策略</li>
 *   <li>支持多租户隔离，每个租户可配置独立的数据源</li>
 *   <li>提供数据源状态管理，支持启用/禁用</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li><strong>密码加密存储</strong>：{@link #password} 字段必须使用 AES 加密后存储，
 *       加密密钥从环境变量或密钥管理服务获取，禁止明文存储</li>
 *   <li><strong>连接测试</strong>：在保存数据源前应先测试连接有效性，避免保存无效配置</li>
 *   <li><strong>租户隔离</strong>：查询和操作数据源时必须校验 tenantId，防止跨租户访问</li>
 *   <li><strong>敏感信息脱敏</strong>：日志和接口响应中应脱敏显示密码（如 ******）</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ol>
 *   <li>创建：管理员配置数据源连接信息</li>
 *   <li>测试连接：验证配置的正确性</li>
 *   <li>启用：状态设为 1，可用于代码生成</li>
 *   <li>禁用：状态设为 0，暂时不可用但保留配置</li>
 *   <li>删除：逻辑删除（deleted=1），关联的生成配置一并失效</li>
 * </ol>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>一对多：一个数据源可被多个生成配置（{@code GenConfigEntity}）引用</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see GenConfigEntity
 * @see DbType
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class GenDatasourceEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    public GenDatasourceEntity() {
        super();
    }

    private String name;

    private DbType dbType;

    private String host;

    private Integer port;

    private String databaseName;

    private String username;

    private String password;

    private String schemaName;

    private String params;

    private Integer status;

    private String tenantId;
}

package com.apex.scaffold.generator;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 表结构定义（代码生成器输入模型）。
 *
 * <p>描述一张业务表的元信息，由 JSON 定义文件反序列化而来。
 * 生成器依据该定义输出 DDD 五层代码（entity/repository/PO/Mapper/
 * Converter/RepositoryImpl/Service/DTO/Handler/路由/迁移/测试）。</p>
 *
 * <pre>{@code
 * {
 *   "module": "demo",
 *   "entityName": "Product",
 *   "tableName": "biz_product",
 *   "tableComment": "商品",
 *   "fields": [
 *     { "name": "productName", "type": "String", "comment": "商品名称", "required": true }
 *   ]
 * }
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see FieldDefinition
 * @see CodeGenerator
 */
@Data
public class TableDefinition {

    /**
     * 构造表结构定义。
     */
    public TableDefinition() {}

    /**
     * 业务模块名。
     *
     * <p>用于生成 DTO 子包（{@code application/dto/{module}}）与
     * Handler 子包（{@code interfaces/handler/{module}}），
     * 建议小写，例如 {@code demo}。</p>
     */
    private String module = "demo";

    /**
     * 实体名称。
     *
     * <p>大驼峰命名，例如 {@code Product}。生成的所有类名均以此为基础。</p>
     */
    private String entityName;

    /**
     * 数据库表名。
     *
     * <p>例如 {@code biz_product}。</p>
     */
    private String tableName;

    /**
     * 表注释（业务描述）。
     */
    private String tableComment = "";

    private boolean tenantAware;

    private StateMachineDefinition stateMachine;

    /**
     * 字段定义列表。
     */
    @JsonProperty("fields")
    private List<FieldDefinition> fields = new ArrayList<>();
}

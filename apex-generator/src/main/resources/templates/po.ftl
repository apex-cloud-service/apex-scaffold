package ${infraPackage}.persistence.po;

<#if tenantAware>import com.baomidou.mybatisplus.annotation.TableField;</#if>
import com.baomidou.mybatisplus.annotation.TableName;
<#if hasBigDecimal>import java.math.BigDecimal;</#if>
<#if hasLocalDateTime>import java.time.LocalDateTime;</#if>
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * ${tableComment}持久化对象。
 *
 * <p>对应数据库中的 {@code ${tableName}} 表，存储${tableComment}数据。</p>
 *
 * @author ${author}
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("${tableName}")
public class ${entityName}PO extends BasePO {

    /**
     * 构造${tableComment}持久化对象。
     */
    public ${entityName}PO() {}

<#if tenantAware>
    @TableField("tenant_id")
    private String tenantId;

</#if>
<#list fields as field>
    /**
     * ${field.comment}。
     */
    private ${field.type} ${field.name};

</#list>
}

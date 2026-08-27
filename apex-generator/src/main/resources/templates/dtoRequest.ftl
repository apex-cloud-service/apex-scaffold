package ${appPackage}.dto.${module};

import io.swagger.v3.oas.annotations.media.Schema;
<#if hasLocalDateTime>import java.time.LocalDateTime;</#if>
<#if hasBigDecimal>import java.math.BigDecimal;</#if>
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * ${tableComment}创建/更新请求体。
 *
 * <p>${tableComment}的创建与编辑统一请求 DTO，字段约束遵循业务校验规则。</p>
 *
 * @author ${author}
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Schema(description = "${tableComment}创建/更新请求")
public class ${entityName}Request {

    /**
     * 构造${tableComment}请求体。
     */
    public ${entityName}Request() {}

<#list fields as field>
    /**
     * ${field.comment}。
     */
    @Schema(description = "${field.comment}")
<#if field.required>
    @NotBlank(message = "{param.not_null}")
</#if>
    private ${field.type} ${field.name};

</#list>
}

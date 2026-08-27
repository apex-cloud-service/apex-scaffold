package ${appPackage}.dto.${module};

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * ${tableComment}响应 DTO。
 *
 * <p>${tableComment}的响应对象，供接口层返回业务数据使用。</p>
 *
 * @author ${author}
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Schema(description = "${tableComment}响应")
public class ${entityName}DTO {

    /**
     * 构造${tableComment}响应。
     */
    public ${entityName}DTO() {}

    /**
     * ${tableComment} ID。
     */
    @Schema(description = "${tableComment}ID")
    private Long id;

<#list fields as field>
    /**
     * ${field.comment}。
     */
    @Schema(description = "${field.comment}")
    private ${field.type} ${field.name};

</#list>
    /**
     * 创建时间。
     */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}

package com.apex.scaffold.knowledge.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 知识空间持久化对象，对应表 {@code kb_space}。
 *
 * <p>继承 {@link BasePO}（雪花 ID、审计字段、乐观锁、逻辑删除）；
 * 租户列由多租户拦截器自动注入与过滤，仅作展示读取。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("kb_space")
public class KnowledgeSpacePO extends BasePO {

    private static final long serialVersionUID = 1L;

    /** 空间名称（租户内唯一）。 */
    private String name;

    /** 空间描述。 */
    private String description;

    /** 状态：0=停用，1=正常。 */
    private Integer status;

    /** 租户 ID。 */
    private String tenantId;
}

package com.apex.scaffold.knowledge.domain.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识空间领域模型。
 *
 * <p>租户内的知识组织单元；检索与文档管理均以空间为最小可见范围。
 * 租户隔离由持久化层租户拦截器保证，本模型不承载跨租户语义。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSpace {

    /** 空间 ID（雪花 ID）。 */
    private Long id;

    /** 空间名称（租户内唯一）。 */
    private String name;

    /** 空间描述。 */
    private String description;

    /** 状态：0=停用，1=正常。 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

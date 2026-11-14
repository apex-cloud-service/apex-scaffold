package com.apex.scaffold.contract.dto.internal;

import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字典项（内部 API 契约）.
 *
 * <p>由 system-service 的 {@code GET /internal/v1/dict/{type}} 提供，
 * 供全部业务服务消费字典只读副本。消费侧建议本地缓存并配合 stale-while-revalidate 策略。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictItemDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 字典类型编码（与查询路径中的 {@code type} 一致） */
    private String dictType;

    /** 字典项标签（展示文案） */
    private String label;

    /** 字典项值（业务存储值，字符串泛化） */
    private String value;

    /** 排序序号 */
    private Integer sortOrder;

    /** 状态（启用/禁用，1=启用 0=禁用，沿用 common 约定） */
    private Integer status;

    /** 扩展样式或颜色（前端渲染用，可选） */
    private String cssClass;
}

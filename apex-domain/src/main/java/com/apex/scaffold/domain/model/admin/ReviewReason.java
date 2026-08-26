package com.apex.scaffold.domain.model.admin;

/**
 * 人工审核可选择的标准原因。
 *
 * <p>按审核类型组织，可通过开关控制启用状态，并可选要求审核时必须填写
 * 补充说明。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public record ReviewReason(
        Long id,
        String reviewType,
        String code,
        String label,
        String description,
        boolean enabled,
        boolean requireComment,
        int sortOrder,
        int version) {}

package com.apex.scaffold.domain.model.admin;

/**
 * 人工审核可选择的标准原因。
 *
 * <p>按审核类型组织，可通过开关控制启用状态，并可选要求审核时必须填写
 * 补充说明。</p>
 *
 * @param id 原因ID
 * @param reviewType 审核类型
 * @param code 原因编码，创建后不可修改
 * @param label 原因展示文案
 * @param description 原因补充说明
 * @param enabled 是否启用（停用后审核时不可再选择）
 * @param requireComment 选择该原因时是否强制填写备注
 * @param sortOrder 展示排序值（越小越靠前）
 * @param version 乐观锁版本号
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

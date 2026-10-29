package com.apex.scaffold.office.application.port;

import lombok.Builder;

/**
 * 组织同步解析出的真实系统用户。
 *
 * @param externalUserId 外部平台用户标识，与请求原样对应
 * @param userId         本租户真实系统用户 ID（{@code sys_user.id}）
 * @param deptId         该用户在主数据中挂载的系统部门 ID，可为空
 * @param matchedBy      命中字段：{@code USER_ID}、{@code MOBILE}、{@code EMAIL} 或 {@code USERNAME}
 * @author Apex Team
 * @version 1.0.0
 * @since 2.6.0
 */
@Builder
public record OfficeOrgUserSnapshot(String externalUserId, Long userId, Long deptId, String matchedBy) {}

package com.apex.scaffold.interfaces.dto.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 组织目录内部接口：批量解析真实系统用户响应。
 *
 * @param resolved 命中的用户列表，未命中的条目不出现在结果中
 * @author Apex Team
 * @since 2.6.0
 */
@Schema(description = "组织目录批量解析系统用户结果")
public record OrgUserResolveResponse(@Schema(description = "命中的用户列表") List<ResolvedUser> resolved) {

    /**
     * 命中的用户。
     *
     * @param externalUserId 外部系统用户标识，与请求原样对应
     * @param userId         本租户真实系统用户 ID（{@code sys_user.id}）
     * @param openId         系统用户 openId
     * @param username       登录名
     * @param mobile         手机号
     * @param email          邮箱
     * @param deptId         所属系统部门 ID，可为空
     * @param matchedBy      命中字段：{@code MOBILE}、{@code EMAIL} 或 {@code USERNAME}
     */
    @Schema(description = "命中的系统用户")
    public record ResolvedUser(
            @Schema(description = "外部系统用户标识") String externalUserId,
            @Schema(description = "系统用户 ID") Long userId,
            @Schema(description = "系统用户 openId") String openId,
            @Schema(description = "登录名") String username,
            @Schema(description = "手机号") String mobile,
            @Schema(description = "邮箱") String email,
            @Schema(description = "所属系统部门 ID") Long deptId,
            @Schema(description = "命中字段：MOBILE / EMAIL / USERNAME") String matchedBy) {}
}

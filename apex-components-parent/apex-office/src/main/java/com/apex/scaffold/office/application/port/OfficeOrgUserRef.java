package com.apex.scaffold.office.application.port;

import lombok.Builder;

/**
 * 组织同步待解析的外部通讯录身份。
 *
 * <p>由组织同步用例在写入成员映射前构造，交给 {@link OfficeOrgUserQueryPort}
 * 解析为本租户真实的系统用户 ID。所有字段都可为空，但至少应提供一个可匹配字段，
 * 否则解析方直接忽略该条而不做猜测。</p>
 *
 * @param externalUserId 外部平台用户标识，原样回传给调用方用于对账
 * @param userId         已绑定的系统用户 ID；提供时优先按主键精确匹配
 * @param mobile         手机号
 * @param email          邮箱
 * @param username       登录名
 * @author Apex Team
 * @version 1.0.0
 * @since 2.6.0
 */
@Builder
public record OfficeOrgUserRef(String externalUserId, Long userId, String mobile, String email, String username) {}

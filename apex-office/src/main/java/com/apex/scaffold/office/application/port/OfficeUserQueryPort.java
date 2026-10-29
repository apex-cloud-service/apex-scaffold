package com.apex.scaffold.office.application.port;

import reactor.core.publisher.Mono;

/**
 * 办公模块用户查询出站端口
 *
 * <p>办公应用服务对 Apex 用户能力的依赖在此抽象为出站端口，用于解耦单体应用层。
 * 由基础设施适配层实现，例如通过系统服务内部 API（{@code /internal/v1}）查询用户快照，
 * 并映射为办公模块内自有的 {@link OfficeUserSnapshot}。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>{@link #getById(Long)}：按用户 ID 查询用户快照，不存在时返回空 Mono</li>
 *   <li>{@link #updateLoginInfo(Long, String)}：记录用户最后登录 IP</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.3.0
 */
public interface OfficeUserQueryPort {

    /**
     * 按用户 ID 查询用户快照
     *
     * @param userId 用户 ID
     * @return 用户快照；不存在时返回空 Mono
     */
    Mono<OfficeUserSnapshot> getById(Long userId);

    /**
     * 更新用户最后登录信息
     *
     * @param userId  用户 ID
     * @param loginIp 客户端登录 IP
     * @return true-更新成功，false-用户不存在
     */
    Mono<Boolean> updateLoginInfo(Long userId, String loginIp);
}

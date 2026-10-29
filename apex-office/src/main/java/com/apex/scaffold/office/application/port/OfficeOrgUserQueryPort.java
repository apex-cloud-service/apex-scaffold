package com.apex.scaffold.office.application.port;

import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 办公模块系统用户解析出站端口。
 *
 * <p>组织同步需要把外部通讯录成员绑定到本租户真实的 {@code sys_user.id}，
 * 而用户主数据属于系统域。本端口把该依赖抽象为出站契约，由基础设施适配层
 * 通过 {@code apex-system-service} 的内部接口 {@code POST /internal/v1/org/users/resolve}
 * 实现，避免 office 业务库直接依赖 system 的实体或持久化实现。</p>
 *
 * <h2>契约说明</h2>
 * <ul>
 *   <li>只读：解析过程不创建、不修改系统用户</li>
 *   <li>未命中即丢弃：返回结果只包含命中的条目，未命中的外部标识不出现在结果中</li>
 *   <li>降级：远程不可用或契约配置缺失时返回空列表，由调用方决定如何处理未绑定的成员</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.6.0
 */
public interface OfficeOrgUserQueryPort {

    /**
     * 批量解析真实系统用户。
     *
     * @param tenantId 目标租户标识，解析方据此限定租户范围，不得由外部覆盖
     * @param refs     待解析的外部通讯录身份，空列表直接返回空结果
     * @return 命中的用户快照列表；未命中或解析不可用时为空列表
     */
    Mono<List<OfficeOrgUserSnapshot>> resolveUsers(String tenantId, List<OfficeOrgUserRef> refs);
}

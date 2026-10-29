package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.SysSsoAuditLogPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统 SSO 审计日志 MyBatis Mapper 接口
 *
 * <p>负责 {@code sys_sso_audit_log} 表的持久化操作，记录所有 SSO 相关的操作日志和安全事件。
 * 继承 MyBatis-Plus {@link BaseMapper} 获得通用单表 CRUD 操作。
 *
 * <h2>设计说明</h2>
 * <p>表名：{@code sys_sso_audit_log}<br>
 * 实体：{@link SysSsoAuditLogPO}<br>
 * 主键：{@code id}（Long 类型，雪花算法）
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>{@code user_id}：操作用户 ID</li>
 *   <li>{@code username}：用户名</li>
 *   <li>{@code provider_id}：SSO 提供商 ID</li>
 *   <li>{@code action}：操作类型（LOGIN、LOGOUT、AUTH_SUCCESS、AUTH_FAILURE）</li>
 *   <li>{@code ip_address}：客户端 IP 地址</li>
 *   <li>{@code user_agent}：用户代理字符串</li>
 *   <li>{@code result}：操作结果（SUCCESS、FAILURE）</li>
 *   <li>{@code error_message}：失败原因描述</li>
 *   <li>{@code session_id}：关联的会话 ID</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>LOGIN：用户通过 SSO 登录</li>
 *   <li>LOGOUT：用户单点登出</li>
 *   <li>AUTH_SUCCESS：SSO 认证成功</li>
 *   <li>AUTH_FAILURE：SSO 认证失败</li>
 *   <li>TOKEN_REFRESH：令牌刷新</li>
 *   <li>SESSION_EXPIRE：会话过期</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>安全合规：满足等保、ISO 27001 等安全合规要求</li>
 *   <li>异常检测：识别可疑的登录行为和攻击模式</li>
 *   <li>故障排查：追踪 SSO 认证失败的根本原因</li>
 *   <li>操作溯源：提供完整的用户操作审计链</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <p>继承自 {@link BaseMapper} 的常用方法：
 * <ul>
 *   <li>{@code insert(entity)} - 插入审计日志</li>
 *   <li>{@code selectById(id)} - 根据 ID 查询日志</li>
 *   <li>{@code selectList(wrapper)} - 条件查询日志列表</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see SysSsoAuditLogPO
 * @see BaseMapper
 */
@Mapper
public interface SysSsoAuditLogMapper extends BaseMapper<SysSsoAuditLogPO> {}

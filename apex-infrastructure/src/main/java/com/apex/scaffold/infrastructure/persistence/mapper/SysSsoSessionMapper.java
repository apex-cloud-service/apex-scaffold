package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.SysSsoSessionPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统 SSO 会话 MyBatis Mapper 接口
 *
 * <p>负责 {@code sys_sso_session} 表的持久化操作，管理单点登录会话的生命周期。
 * 继承 MyBatis-Plus {@link BaseMapper} 获得通用单表 CRUD 操作。
 *
 * <h2>表映射</h2>
 * <p>表名：{@code sys_sso_session}<br>
 * 实体：{@link SysSsoSessionPO}<br>
 * 主键：{@code id}（Long 类型，雪花算法）
 *
 * <h2>会话字段</h2>
 * <ul>
 *   <li>{@code session_id}：会话唯一标识（UUID）</li>
 *   <li>{@code user_id}：关联用户 ID</li>
 *   <li>{@code provider_id}：SSO 提供商 ID</li>
 *   <li>{@code saml_session_index}：SAML 协议的会话索引</li>
 *   <li>{@code access_token}：访问令牌</li>
 *   <li>{@code refresh_token}：刷新令牌</li>
 *   <li>{@code expires_at}：会话过期时间</li>
 *   <li>{@code status}：会话状态（ACTIVE、EXPIRED、REVOKED）</li>
 *   <li>{@code ip_address}：客户端 IP 地址</li>
 * </ul>
 *
 * <h2>会话生命周期</h2>
 * <ul>
 *   <li>创建：用户通过 SSO 认证成功后创建会话</li>
 *   <li>活跃：会话在有效期内可用于身份验证</li>
 *   <li>刷新：通过 refresh_token 延长会话有效期</li>
 *   <li>过期：会话超过 expires_at 时间自动失效</li>
 *   <li>撤销：用户主动登出或管理员强制下线</li>
 * </ul>
 *
 * <h2>单点登出</h2>
 * <p>支持 SAML SLO（Single Logout）和 OAuth2 Token Revocation：
 * <ul>
 *   <li>通过 {@code saml_session_index} 关联 SAML 会话</li>
 *   <li>撤销会话时同步通知 SSO 提供商</li>
 *   <li>支持前端渠道和后端渠道的单点登出</li>
 * </ul>
 *
 * <h2>通用方法</h2>
 * <p>继承自 {@link BaseMapper} 的常用方法：
 * <ul>
 *   <li>{@code insert(entity)} - 插入会话记录</li>
 *   <li>{@code updateById(entity)} - 更新会话信息</li>
 *   <li>{@code selectById(id)} - 根据 ID 查询会话</li>
 *   <li>{@code selectList(wrapper)} - 条件查询会话列表</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see SysSsoSessionPO
 * @see BaseMapper
 */
@Mapper
public interface SysSsoSessionMapper extends BaseMapper<SysSsoSessionPO> {}

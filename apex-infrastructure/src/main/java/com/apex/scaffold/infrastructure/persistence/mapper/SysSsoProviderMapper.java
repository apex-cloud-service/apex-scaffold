package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.SysSsoProviderPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统 SSO 提供商 MyBatis Mapper 接口
 *
 * <p>负责 {@code sys_sso_provider} 表的持久化操作，管理单点登录提供商配置信息。
 * 继承 MyBatis-Plus {@link BaseMapper} 获得通用单表 CRUD 操作。
 *
 * <h2>表映射</h2>
 * <p>表名：{@code sys_sso_provider}<br>
 * 实体：{@link SysSsoProviderPO}<br>
 * 主键：{@code id}（Long 类型，雪花算法）
 *
 * <h2>提供商配置字段</h2>
 * <ul>
 *   <li>{@code provider_name}：提供商名称（如 "Keycloak"、"Azure AD"）</li>
 *   <li>{@code provider_type}：提供商类型（SAML、OAuth2、LDAP、CAS）</li>
 *   <li>{@code entity_id}：SAML Entity ID 或 OAuth2 Client ID</li>
 *   <li>{@code metadata_url}：SAML 元数据 URL</li>
 *   <li>{@code sso_login_url}：单点登录入口 URL</li>
 *   <li>{@code sso_logout_url}：单点登出 URL</li>
 *   <li>{@code certificate}：签名验证证书（SAML）</li>
 *   <li>{@code enabled}：启用状态</li>
 * </ul>
 *
 * <h2>支持的 SSO 协议</h2>
 * <ul>
 *   <li>SAML 2.0：企业级单点登录标准协议</li>
 *   <li>OAuth 2.0 / OIDC：现代授权与身份认证协议</li>
 *   <li>LDAP：轻量级目录访问协议</li>
 *   <li>CAS：中央认证服务</li>
 * </ul>
 *
 * <h2>租户隔离</h2>
 * <p>SSO 提供商配置支持租户级隔离，每个租户可配置独立的 SSO 提供商。
 *
 * <h2>通用方法</h2>
 * <p>继承自 {@link BaseMapper} 的常用方法：
 * <ul>
 *   <li>{@code insert(entity)} - 插入提供商配置</li>
 *   <li>{@code updateById(entity)} - 更新提供商配置</li>
 *   <li>{@code selectById(id)} - 根据 ID 查询配置</li>
 *   <li>{@code selectList(wrapper)} - 条件查询提供商列表</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see SysSsoProviderPO
 * @see BaseMapper
 */
@Mapper
public interface SysSsoProviderMapper extends BaseMapper<SysSsoProviderPO> {}

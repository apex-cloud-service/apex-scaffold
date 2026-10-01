package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.OffMessageRecipientPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 办公消息接收人 MyBatis Mapper 接口
 *
 * <p>负责 {@code off_message_recipient} 表的持久化操作，管理消息与接收人之间的关联关系。
 * 继承 MyBatis-Plus {@link BaseMapper} 获得通用单表 CRUD 操作。
 *
 * <h2>表映射</h2>
 * <p>表名：{@code off_message_recipient}<br>
 * 实体：{@link OffMessageRecipientPO}<br>
 * 主键：{@code id}（Long 类型，雪花算法）
 *
 * <h2>关联字段</h2>
 * <ul>
 *   <li>{@code message_id}：消息 ID（关联 {@code off_message}）</li>
 *   <li>{@code recipient_id}：接收人用户 ID</li>
 *   <li>{@code read_status}：已读状态（0=未读，1=已读）</li>
 *   <li>{@code read_time}：已读时间</li>
 *   <li>{@code deleted}：删除标记（0=正常，1=已删除）</li>
 *   <li>{@code deleted_time}：删除时间</li>
 * </ul>
 *
 * <h2>业务场景</h2>
 * <ul>
 *   <li>消息分发：一条消息可以有多个接收人</li>
 *   <li>收件箱：查询用户收到的所有消息</li>
 *   <li>已读状态：标记消息为已读/未读</li>
 *   <li>未读统计：统计用户的未读消息数量</li>
 *   <li>消息删除：逻辑删除用户的消息（不影响其他接收人）</li>
 * </ul>
 *
 * <h2>数据隔离</h2>
 * <p>每个接收人对同一条消息拥有独立的状态：
 * <ul>
 *   <li>已读状态互不影响：A 已读不影响 B 的未读状态</li>
 *   <li>删除互不影响：A 删除不影响 B 继续接收</li>
 * </ul>
 *
 * <h2>通用方法</h2>
 * <p>继承自 {@link BaseMapper} 的常用方法：
 * <ul>
 *   <li>{@code insert(entity)} - 插入接收人记录</li>
 *   <li>{@code updateById(entity)} - 更新已读状态</li>
 *   <li>{@code selectList(wrapper)} - 查询接收人列表</li>
 *   <li>{@code delete(wrapper)} - 逻辑删除消息</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 * @see OffMessageRecipientPO
 * @see BaseMapper
 */
@Mapper
public interface OffMessageRecipientMapper extends BaseMapper<OffMessageRecipientPO> {}

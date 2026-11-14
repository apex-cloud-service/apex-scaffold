package com.apex.scaffold.office.off.infrastructure.persistence.mapper;

import com.apex.scaffold.office.off.infrastructure.persistence.po.OffMessagePO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 办公消息 MyBatis Mapper 接口
 *
 * <p>负责 {@code off_message} 表的持久化操作，管理站内消息的存储与查询。
 * 继承 MyBatis-Plus {@link BaseMapper} 获得通用单表 CRUD 操作。
 *
 * <h2>设计说明</h2>
 * <p>表名：{@code off_message}<br>
 * 实体：{@link OffMessagePO}<br>
 * 主键：{@code id}（Long 类型，雪花算法）
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>{@code title}：消息标题</li>
 *   <li>{@code content}：消息内容（支持富文本）</li>
 *   <li>{@code sender_id}：发送者用户 ID</li>
 *   <li>{@code message_type}：消息类型（系统通知、用户消息、审批消息等）</li>
 *   <li>{@code priority}：优先级（普通、重要、紧急）</li>
 *   <li>{@code template_id}：消息模板 ID（可选）</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>站内消息发送：用户之间的消息通信</li>
 *   <li>系统通知：系统自动推送的通知消息</li>
 *   <li>审批提醒：审批流程相关的消息</li>
 *   <li>模板消息：基于预定义模板的批量消息</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <p>继承自 {@link BaseMapper} 的常用方法：
 * <ul>
 *   <li>{@code insert(entity)} - 插入消息记录</li>
 *   <li>{@code selectById(id)} - 根据 ID 查询消息</li>
 *   <li>{@code selectList(wrapper)} - 条件查询消息列表</li>
 *   <li>{@code deleteById(id)} - 根据 ID 删除消息</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 * @see OffMessagePO
 * @see BaseMapper
 */
@Mapper
public interface OffMessageMapper extends BaseMapper<OffMessagePO> {}

package com.apex.scaffold.office.off.infrastructure.persistence.mapper;

import com.apex.scaffold.office.off.infrastructure.persistence.po.OffMessageTemplatePO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 办公消息模板 MyBatis Mapper 接口
 *
 * <p>负责 {@code off_message_template} 表的持久化操作，管理消息模板的定义和使用。
 * 继承 MyBatis-Plus {@link BaseMapper} 获得通用单表 CRUD 操作。
 *
 * <h2>表映射</h2>
 * <p>表名：{@code off_message_template}<br>
 * 实体：{@link OffMessageTemplatePO}<br>
 * 主键：{@code id}（Long 类型，雪花算法）
 *
 * <h2>模板字段</h2>
 * <ul>
 *   <li>{@code template_name}：模板名称</li>
 *   <li>{@code template_code}：模板编码（唯一标识）</li>
 *   <li>{@code template_type}：模板类型（SYSTEM_NOTIFY、USER_MESSAGE、APPROVAL_NOTIFY）</li>
 *   <li>{@code subject}：消息主题（支持变量占位符）</li>
 *   <li>{@code content}：消息内容（支持变量占位符和富文本）</li>
 *   <li>{@code variables}：变量定义（JSON 数组，如 ["userName", "taskName"]）</li>
 *   <li>{@code enabled}：启用状态（0=禁用，1=启用）</li>
 *   <li>{@code priority}：优先级（1=低，2=中，3=高，4=紧急）</li>
 * </ul>
 *
 * <h2>变量占位符</h2>
 * <p>模板支持使用变量占位符，在发送时动态替换：
 * <ul>
 *   <li>语法：{@code ${variableName}} 或 {@code {{variableName}}}</li>
 *   <li>示例：{@code "您好，${userName}，您有一个待审批的${taskName}"}</li>
 *   <li>变量列表在 {@code variables} 字段中定义</li>
 * </ul>
 *
 * <h2>模板类型</h2>
 * <ul>
 *   <li>SYSTEM_NOTIFY：系统通知模板（如密码重置、账号变更）</li>
 *   <li>USER_MESSAGE：用户消息模板（如好友申请、消息提醒）</li>
 *   <li>APPROVAL_NOTIFY：审批通知模板（如审批提交、审批结果）</li>
 *   <li>TASK_NOTIFY：任务通知模板（如任务分配、任务到期）</li>
 * </ul>
 *
 * <h2>业务场景</h2>
 * <ul>
 *   <li>批量消息：基于模板批量发送格式统一的消息</li>
 *   <li>定时任务：定时任务使用模板发送通知</li>
 *   <li>业务触发：业务事件触发时自动使用模板发送消息</li>
 *   <li>模板管理：统一管理和维护各类消息模板</li>
 * </ul>
 *
 * <h2>通用方法</h2>
 * <p>继承自 {@link BaseMapper} 的常用方法：
 * <ul>
 *   <li>{@code insert(entity)} - 插入消息模板</li>
 *   <li>{@code updateById(entity)} - 更新模板内容</li>
 *   <li>{@code selectById(id)} - 根据 ID 查询模板</li>
 *   <li>{@code selectList(wrapper)} - 条件查询模板列表</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 * @see OffMessageTemplatePO
 * @see BaseMapper
 */
@Mapper
public interface OffMessageTemplateMapper extends BaseMapper<OffMessageTemplatePO> {}

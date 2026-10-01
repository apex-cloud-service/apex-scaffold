package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.OfficeApprovalProcessPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 办公审批流程 MyBatis Mapper 接口
 *
 * <p>负责 {@code office_approval_process} 表的持久化操作，管理从第三方办公平台同步的审批流程信息。
 * 继承 MyBatis-Plus {@link BaseMapper} 获得通用单表 CRUD 操作。
 *
 * <h2>表映射</h2>
 * <p>表名：{@code office_approval_process}<br>
 * 实体：{@link OfficeApprovalProcessPO}<br>
 * 主键：{@code id}（Long 类型，雪花算法）
 *
 * <h2>审批流程字段</h2>
 * <ul>
 *   <li>{@code process_name}：流程名称</li>
 *   <li>{@code process_code}：流程编码（唯一标识）</li>
 *   <li>{@code initiator_id}：发起人用户 ID</li>
 *   <li>{@code approver_ids}：审批人 ID 列表（JSON 数组）</li>
 *   <li>{@code cc_user_ids}：抄送人 ID 列表（JSON 数组）</li>
 *   <li>{@code status}：流程状态（PENDING、APPROVED、REJECTED、CANCELLED）</li>
 *   <li>{@code external_process_id}：外部平台流程 ID</li>
 *   <li>{@code platform}：来源平台（FEISHU、DINGTALK、WECOM）</li>
 *   <li>{@code form_data}：表单数据（JSON 格式）</li>
 *   <li>{@code approval_time}：审批完成时间</li>
 * </ul>
 *
 * <h2>审批状态流转</h2>
 * <ul>
 *   <li>PENDING：审批中，等待审批人处理</li>
 *   <li>APPROVED：已通过，所有审批人同意</li>
 *   <li>REJECTED：已拒绝，任一审批人拒绝</li>
 *   <li>CANCELLED：已撤销，发起人主动撤销</li>
 * </ul>
 *
 * <h2>同步来源</h2>
 * <ul>
 *   <li>飞书（Lark）：从飞书审批应用同步审批流程</li>
 *   <li>钉钉（DingTalk）：从钉钉审批应用同步</li>
 *   <li>企业微信（WeChat Work）：从企业微信审批应用同步</li>
 * </ul>
 *
 * <h2>业务场景</h2>
 * <ul>
 *   <li>待办查询：查询用户的待审批事项</li>
 *   <li>已办查询：查询用户的已审批事项</li>
 *   <li>流程跟踪：跟踪审批流程的进度和状态</li>
 *   <li>数据同步：从第三方平台同步审批流程变更</li>
 * </ul>
 *
 * <h2>通用方法</h2>
 * <p>继承自 {@link BaseMapper} 的常用方法：
 * <ul>
 *   <li>{@code insert(entity)} - 插入审批流程记录</li>
 *   <li>{@code updateById(entity)} - 更新流程状态</li>
 *   <li>{@code selectById(id)} - 根据 ID 查询流程</li>
 *   <li>{@code selectList(wrapper)} - 条件查询流程列表</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 * @see OfficeApprovalProcessPO
 * @see BaseMapper
 */
@Mapper
public interface OfficeApprovalProcessMapper extends BaseMapper<OfficeApprovalProcessPO> {}

package com.apex.scaffold.office.off.infrastructure.persistence.mapper;

import com.apex.scaffold.office.off.infrastructure.persistence.po.OffSyncHistoryPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 办公数据同步历史 MyBatis Mapper 接口
 *
 * <p>负责 {@code off_sync_history} 表的持久化操作，记录从第三方办公平台同步数据的历史记录和状态。
 * 继承 MyBatis-Plus {@link BaseMapper} 获得通用单表 CRUD 操作。
 *
 * <h2>表映射</h2>
 * <p>表名：{@code off_sync_history}<br>
 * 实体：{@link OffSyncHistoryPO}<br>
 * 主键：{@code id}（Long 类型，雪花算法）
 *
 * <h2>同步历史字段</h2>
 * <ul>
 *   <li>{@code sync_type}：同步类型（EMPLOYEE、DEPARTMENT、CALENDAR、APPROVAL）</li>
 *   <li>{@code platform}：来源平台（FEISHU、DINGTALK、WECOM）</li>
 *   <li>{@code sync_status}：同步状态（SUCCESS、FAILED、IN_PROGRESS）</li>
 *   <li>{@code start_time}：同步开始时间</li>
 *   <li>{@code end_time}：同步结束时间</li>
 *   <li>{@code total_count}：总记录数</li>
 *   <li>{@code success_count}：成功记录数</li>
 *   <li>{@code failed_count}：失败记录数</li>
 *   <li>{@code error_message}：错误信息（同步失败时记录）</li>
 *   <li>{@code operator_id}：操作人用户 ID（手动触发时记录）</li>
 * </ul>
 *
 * <h2>同步类型</h2>
 * <ul>
 *   <li>EMPLOYEE：员工信息同步</li>
 *   <li>DEPARTMENT：部门组织架构同步</li>
 *   <li>CALENDAR：日历事件同步</li>
 *   <li>APPROVAL：审批流程同步</li>
 *   <li>MESSAGE：消息通知同步</li>
 * </ul>
 *
 * <h2>同步状态</h2>
 * <ul>
 *   <li>IN_PROGRESS：同步进行中</li>
 *   <li>SUCCESS：同步成功完成</li>
 *   <li>FAILED：同步失败</li>
 *   <li>PARTIAL_SUCCESS：部分成功（有失败记录但整体完成）</li>
 * </ul>
 *
 * <h2>业务场景</h2>
 * <ul>
 *   <li>同步记录：记录每次数据同步的执行情况</li>
 *   <li>失败追溯：查询同步失败的历史记录和原因</li>
 *   <li>性能监控：统计同步耗时和成功率</li>
 *   <li>数据对账：通过同步记录进行数据一致性检查</li>
 * </ul>
 *
 * <h2>通用方法</h2>
 * <p>继承自 {@link BaseMapper} 的常用方法：
 * <ul>
 *   <li>{@code insert(entity)} - 插入同步历史记录</li>
 *   <li>{@code updateById(entity)} - 更新同步状态</li>
 *   <li>{@code selectById(id)} - 根据 ID 查询记录</li>
 *   <li>{@code selectList(wrapper)} - 条件查询历史列表</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 * @see OffSyncHistoryPO
 * @see BaseMapper
 */
@Mapper
public interface OffSyncHistoryMapper extends BaseMapper<OffSyncHistoryPO> {}

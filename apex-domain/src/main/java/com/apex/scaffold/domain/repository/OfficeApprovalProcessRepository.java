package com.apex.scaffold.domain.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.domain.model.entity.OfficeApprovalProcessEntity;
import java.time.LocalDateTime;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 办公审批流程仓储接口
 *
 * <p>定义办公平台审批流程的持久化操作契约，支持多平台（飞书、钉钉、企业微信）的审批流程管理。
 * 提供审批流程的增删改查、状态跟踪、待办查询、历史记录等功能。
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li>审批流程的基础 CRUD 操作</li>
 *   <li>按发起人查询审批记录</li>
 *   <li>按状态查询审批流程（待审批、已通过、已拒绝）</li>
 *   <li>查询用户的待办审批任务</li>
 *   <li>分页查询和条件过滤</li>
 *   <li>多平台审批实例ID映射</li>
 * </ul>
 *
 * <h2>查询场景</h2>
 * <ul>
 *   <li>我的审批：查询用户发起的审批流程</li>
 *   <li>待办中心：查询用户的待审批任务</li>
 *   <li>审批管理：分页查询、状态筛选、时间范围筛选</li>
 *   <li>审批详情：通过平台实例ID查询审批详情</li>
 * </ul>
 *
 * <h2>技术特性</h2>
 * <ul>
 *   <li>响应式编程：所有方法返回 Mono/Flux</li>
 *   <li>多平台支持：通过 platform + platformInstanceId 实现平台映射</li>
 *   <li>状态管理：支持审批状态的完整生命周期跟踪</li>
 *   <li>时间范围查询：支持按时间段筛选审批记录</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 * @see OfficeApprovalProcessEntity
 */
public interface OfficeApprovalProcessRepository {

    /**
     * 根据审批流程ID查询审批流程信息
     *
     * @param id 审批流程ID
     * @return 审批流程实体的Mono包装，如果不存在则返回空Mono
     */
    Mono<OfficeApprovalProcessEntity> findById(Long id);

    /**
     * 根据平台实例ID查询审批流程
     *
     * @param platform 平台标识（如feishu、dingtalk、wechat）
     * @param platformInstanceId 平台审批实例ID
     * @return 审批流程实体的Mono包装，如果不存在则返回空Mono
     */
    Mono<OfficeApprovalProcessEntity> findByPlatformInstanceId(String platform, String platformInstanceId);

    /**
     * 查询发起人的所有审批流程
     *
     * @param initiatorId 发起人ID
     * @return 审批流程列表的Flux流
     */
    Flux<OfficeApprovalProcessEntity> findByInitiatorId(String initiatorId);

    /**
     * 根据审批状态查询审批流程
     *
     * @param status 审批状态（如待审批、已通过、已拒绝）
     * @return 审批流程列表的Flux流
     */
    Flux<OfficeApprovalProcessEntity> findByStatus(String status);

    /**
     * 查询发起人在指定状态下的审批流程
     *
     * @param initiatorId 发起人ID
     * @param status 审批状态
     * @return 审批流程列表的Flux流
     */
    Flux<OfficeApprovalProcessEntity> findByInitiatorAndStatus(String initiatorId, String status);

    /**
     * 查询审批人的待办审批任务
     *
     * @param approverId 审批人ID
     * @return 待办审批任务列表的Flux流
     */
    Flux<OfficeApprovalProcessEntity> findPendingApprovals(String approverId);

    /**
     * 插入新审批流程记录
     *
     * @param entity 审批流程实体
     * @return 受影响的行数
     */
    Mono<Integer> insert(OfficeApprovalProcessEntity entity);

    /**
     * 更新审批流程信息
     *
     * @param entity 审批流程实体，必须包含ID
     * @return 受影响的行数
     */
    Mono<Integer> update(OfficeApprovalProcessEntity entity);

    /**
     * 根据ID删除审批流程（逻辑删除）
     *
     * @param id 审批流程ID
     * @return 删除操作的Mono包装
     */
    Mono<Void> deleteById(Long id);

    /**
     * 分页查询审批流程列表
     *
     * @param pageNum 页码，从1开始
     * @param pageSize 每页大小
     * @param initiatorId 发起人ID，可为null表示查询所有发起人
     * @param status 审批状态，可为null表示查询所有状态
     * @param startTime 开始时间，可为null表示不限制
     * @param endTime 结束时间，可为null表示不限制
     * @return 分页结果
     */
    Mono<PageResponse<OfficeApprovalProcessEntity>> findPage(
            Integer pageNum,
            Integer pageSize,
            String initiatorId,
            String status,
            LocalDateTime startTime,
            LocalDateTime endTime);
}

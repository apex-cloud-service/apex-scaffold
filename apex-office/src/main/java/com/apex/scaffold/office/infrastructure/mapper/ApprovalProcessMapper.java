package com.apex.scaffold.office.infrastructure.mapper;

import com.apex.scaffold.office.infrastructure.po.ApprovalProcessPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 审批流程Mapper接口
 *
 * <p>提供审批流程的数据库访问操作，继承MyBatis-Plus的BaseMapper获得基础CRUD能力。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Mapper
public interface ApprovalProcessMapper extends BaseMapper<ApprovalProcessPO> {

    /**
     * 根据平台实例ID查询
     *
     * @param platformInstanceId 平台实例ID
     * @param tenantId 租户ID
     * @return 审批流程PO
     */
    @Select(
            "SELECT * FROM office_approval_process WHERE platform_instance_id = #{platformInstanceId} AND tenant_id = #{tenantId} AND deleted = 0")
    ApprovalProcessPO selectByPlatformInstanceId(
            @Param("platformInstanceId") String platformInstanceId, @Param("tenantId") Long tenantId);

    /**
     * 根据流程实例ID查询
     *
     * @param processInstanceId 流程实例ID
     * @param tenantId 租户ID
     * @return 审批流程PO
     */
    @Select(
            "SELECT * FROM office_approval_process WHERE process_instance_id = #{processInstanceId} AND tenant_id = #{tenantId} AND deleted = 0")
    ApprovalProcessPO selectByProcessInstanceId(
            @Param("processInstanceId") String processInstanceId, @Param("tenantId") Long tenantId);

    /**
     * 查询租户下全部未删除审批流程。
     *
     * <p>已办筛选依赖 records JSON，统一在仓储层解析，避免不同数据库 JSON 查询方言差异。
     *
     * @param tenantId 租户ID
     * @return 审批流程列表
     */
    @Select(
            "SELECT * FROM office_approval_process WHERE tenant_id = #{tenantId} AND deleted = 0 ORDER BY created_at DESC")
    List<ApprovalProcessPO> selectAllByTenant(@Param("tenantId") Long tenantId);

    /**
     * 查询用户发起的审批流程列表
     *
     * @param initiatorId 发起人ID
     * @param tenantId 租户ID
     * @return 审批流程列表
     */
    @Select(
            "SELECT * FROM office_approval_process WHERE initiator_id = #{initiatorId} AND tenant_id = #{tenantId} AND deleted = 0 ORDER BY created_at DESC")
    List<ApprovalProcessPO> selectByInitiator(
            @Param("initiatorId") String initiatorId, @Param("tenantId") Long tenantId);

    /**
     * 查询待审批列表
     *
     * <p>查询当前审批人列表中包含指定审批人的待审批流程。
     *
     * @param approverId 审批人ID
     * @param tenantId 租户ID
     * @return 待审批流程列表
     */
    @Select(
            "SELECT * FROM office_approval_process WHERE JSON_CONTAINS(current_approver_ids, JSON_QUOTE(#{approverId})) "
                    + "AND status IN ('PENDING', 'IN_PROGRESS') AND tenant_id = #{tenantId} AND deleted = 0 ORDER BY created_at DESC")
    List<ApprovalProcessPO> selectPendingByApprover(
            @Param("approverId") String approverId, @Param("tenantId") Long tenantId);
}

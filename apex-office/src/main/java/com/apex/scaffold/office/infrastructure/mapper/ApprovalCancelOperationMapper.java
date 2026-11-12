package com.apex.scaffold.office.infrastructure.mapper;

import com.apex.scaffold.office.infrastructure.po.ApprovalCancelOperationPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 审批撤销操作编排状态 Mapper
 *
 * <p>显式声明租户条件，不使用数据库方言函数，保证 MySQL 与 PostgreSQL 语义一致。
 *
 * @author Apex Team
 * @since 1.4.0
 */
@Mapper
public interface ApprovalCancelOperationMapper extends BaseMapper<ApprovalCancelOperationPO> {

    /**
     * 按租户与幂等键查询撤销操作记录。
     *
     * @param tenantId 租户ID
     * @param idempotencyKey 幂等键
     * @return 撤销操作记录，不存在时返回 null
     */
    @Select("SELECT * FROM off_approval_cancel_operation"
            + " WHERE tenant_id = #{tenantId}"
            + " AND idempotency_key = #{idempotencyKey}"
            + " AND deleted = 0")
    ApprovalCancelOperationPO selectByTenantAndKey(
            @Param("tenantId") Long tenantId, @Param("idempotencyKey") String idempotencyKey);
}

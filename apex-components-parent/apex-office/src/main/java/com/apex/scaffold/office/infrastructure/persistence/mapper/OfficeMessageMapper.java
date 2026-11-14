package com.apex.scaffold.office.infrastructure.persistence.mapper;

import com.apex.scaffold.office.infrastructure.persistence.po.OfficeMessagePO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 办公消息 Mapper 接口
 *
 * <p>提供办公消息的数据库访问操作。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Mapper
public interface OfficeMessageMapper extends BaseMapper<OfficeMessagePO> {

    /**
     * 根据平台消息ID查询
     *
     * @param platformMessageId 平台消息ID
     * @param tenantId 租户ID
     * @return 消息PO
     */
    @Select("SELECT * FROM office_message WHERE platform_message_id = #{platformMessageId} "
            + "AND tenant_id = #{tenantId} AND deleted = 0 LIMIT 1")
    OfficeMessagePO findByPlatformMessageId(
            @Param("platformMessageId") String platformMessageId, @Param("tenantId") Long tenantId);

    /**
     * 查询失败的消息
     *
     * @param tenantId 租户ID
     * @param limit 最大数量
     * @return 消息列表
     */
    @Select("SELECT * FROM office_message WHERE tenant_id = #{tenantId} "
            + "AND send_status = 'FAILED' AND retry_count < max_retry_count "
            + "AND deleted = 0 ORDER BY created_at ASC LIMIT #{limit}")
    List<OfficeMessagePO> findFailedMessages(@Param("tenantId") Long tenantId, @Param("limit") int limit);
}

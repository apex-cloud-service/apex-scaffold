package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.AccountDeletionFilePO;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 账号注销异步清理 SQL。
 *
 * <p>此 Mapper 仅清理仍存在的通用业务表；已删除的业务表（对战/照片/兴趣/共鸣等）不再
 * 包含清理语句。举报工单/证据、订阅历史等引用留待其他模块处理。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper
public interface AccountDeletionCleanupMapper {

    /** 删除账号权限关联。 */
    @Delete("DELETE FROM sys_user_role WHERE user_id=#{userId}")
    int deleteUserRoles(@Param("userId") Long userId);

    /** 删除用户个人资料。 */
    @Delete("DELETE FROM biz_user_profile WHERE user_id=#{userId}")
    int deleteUserProfile(@Param("userId") Long userId);

    /** 删除用户合规记录（合规状态投影）。 */
    @Delete("DELETE FROM biz_user_compliance WHERE user_id=#{userId}")
    int deleteUserCompliance(@Param("userId") Long userId);

    /** 删除用户同意记录（隐私授权投影）。 */
    @Delete("DELETE FROM biz_user_consent WHERE user_id=#{userId}")
    int deleteUserConsent(@Param("userId") Long userId);

    /** 删除账号相关的黑名单关系（双向）。 */
    @Delete("DELETE FROM biz_blacklist WHERE user_id=#{userId} OR target_user_id=#{userId}")
    int deleteBlacklist(@Param("userId") Long userId);

    /** 删除只属于该用户的算力流水。 */
    @Delete("DELETE FROM biz_credit_tx WHERE user_id=#{userId}")
    int deleteCreditTransactions(@Param("userId") Long userId);

    /** 删除账号的算力账户余额。 */
    @Delete("DELETE FROM biz_credit_account WHERE user_id=#{userId}")
    int deleteCreditAccount(@Param("userId") Long userId);

    /** 删除发给用户的系统消息。 */
    @Delete("DELETE FROM biz_system_message WHERE user_id=#{userId}")
    int deleteSystemMessages(@Param("userId") Long userId);

    /** 删除用户产生的采样记录。 */
    @Delete("DELETE FROM biz_sample WHERE user_id=#{userId}")
    int deleteSamples(@Param("userId") Long userId);

    /**
     * 删除账号自己的文件元数据；被举报工单 evidence JSON 引用的文件保留。
     */
    @Delete("DELETE f FROM sys_file f WHERE f.user_id=#{userId} AND f.deleted=0 AND NOT EXISTS "
            + "(SELECT 1 FROM biz_report_ticket r WHERE r.evidence IS NOT NULL "
            + "AND (r.evidence LIKE CONCAT('%',f.file_url,'%') "
            + "OR r.evidence LIKE CONCAT('%',f.file_name,'%')))")
    int deleteFilesExceptReportEvidence(@Param("userId") Long userId);

    /**
     * 查询需要异步物理删除的文件对象。
     *
     * <p>举报证据引用的对象不出现在结果中。</p>
     */
    @Select("SELECT f.bucket_name, f.file_name FROM sys_file f "
            + "WHERE f.user_id=#{userId} AND f.deleted=0 "
            + "AND NOT EXISTS (SELECT 1 FROM biz_report_ticket r WHERE r.evidence IS NOT NULL "
            + "AND (r.evidence LIKE CONCAT('%',f.file_url,'%') "
            + "OR r.evidence LIKE CONCAT('%',f.file_name,'%')))")
    List<AccountDeletionFilePO> selectNonPhotoFilesExceptReportEvidence(@Param("userId") Long userId);
}

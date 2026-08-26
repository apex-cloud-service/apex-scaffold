package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.AccountDeletionTaskPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 账号注销清理任务 Mapper。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper
public interface AccountDeletionTaskMapper extends BaseMapper<AccountDeletionTaskPO> {

    /** 找到一条待处理或锁超时任务。 */
    @Select("SELECT * FROM sys_account_deletion_task "
            + "WHERE deleted=0 AND ((status IN ('PENDING','FAILED') AND attempts < 10) "
            + "OR (status='CLEANING' AND locked_at < #{staleBefore})) "
            + "ORDER BY id LIMIT 1")
    AccountDeletionTaskPO findNextCandidate(@Param("staleBefore") LocalDateTime staleBefore);

    /** 竞争领取任务。 */
    @Update("UPDATE sys_account_deletion_task SET status='CLEANING', attempts=attempts+1, "
            + "locked_at=#{now}, locked_by=#{workerId}, update_time=NOW() "
            + "WHERE id=#{id} AND deleted=0 AND (status IN ('PENDING','FAILED') "
            + "OR (status='CLEANING' AND locked_at < #{staleBefore}))")
    int claim(
            @Param("id") Long id,
            @Param("now") LocalDateTime now,
            @Param("workerId") String workerId,
            @Param("staleBefore") LocalDateTime staleBefore);

    /** 标记清理完成。 */
    @Update("UPDATE sys_account_deletion_task SET status='COMPLETED', completed_at=#{now}, "
            + "locked_at=NULL, locked_by=NULL, update_time=NOW() WHERE id=#{id} AND deleted=0")
    int markCompleted(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** 标记清理失败并允许后续重试。 */
    @Update("UPDATE sys_account_deletion_task SET status='FAILED', last_error=#{error}, "
            + "locked_at=NULL, locked_by=NULL, update_time=NOW() WHERE id=#{id} AND deleted=0")
    int markFailed(@Param("id") Long id, @Param("error") String error);
}
